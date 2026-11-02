package dev.mordo.kairo.render.background;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.mods.totem.TotemPack;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.KairoPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;

/**
 * The menu background when there's no world: Minecraft's panorama (vanilla), a still image, an animated GIF,
 * or the player's own 6-image panorama.
 *
 * <p>Images and GIFs are drawn into the frame by a small full-screen pass right before the GUI (and before the
 * menu blur, so glass panels blur them like they blur the panorama). Custom panoramas replace the vanilla
 * panorama textures in the client's resource pack, so Minecraft's own spinning cube shows them.
 */
public final class MenuBackground {
	private static final int MAX_IMAGE = 2560;

	private static String loaded = "";
	private static @Nullable DynamicTexture texture;
	private static int texW;
	private static int texH;
	private static @Nullable GifPlayer gif;
	private static long uploadedVersion = -1;
	private static boolean requested;
	private static @Nullable MappableRingBuffer config;
	private static long startNanos = System.nanoTime();
	/** Bumped when a load starts, so a slow load that finishes after another choice doesn't win. */
	private static volatile int generation;
	/** An image or GIF is chosen (while it loads the screen stays dark instead of flashing the panorama). */
	private static boolean custom;
	/** Decoded still image waiting for the render thread to upload it. */
	private static volatile int @Nullable [] pendingStill;
	private static volatile int pendingW;
	private static volatile int pendingH;
	private static volatile int pendingGen;
	private static volatile boolean stillFailed;
	/** When the image first appeared (for the fade-in), 0 = not yet. */
	private static long shownAt;
	private static final float FADE_SECONDS = 0.45f;

	private MenuBackground() {
	}

	public static Path folder() {
		return ClientInfo.configDir("backgrounds");
	}

	private static String choice() {
		return MainMenuSettings.INSTANCE.background.get();
	}

	/** True if a custom image/GIF replaces the panorama right now (loading it if the choice changed). */
	public static boolean replacesPanorama() {
		sync();
		if (stillFailed) {
			custom = false; // fall back to the panorama
			stillFailed = false;
		}
		return custom;
	}

	/** Asks for the image to be drawn this frame (instead of the panorama). */
	public static void request() {
		requested = true;
		if (gif != null) {
			gif.wanted();
		}
	}

	// ---- loading ----

	private static void sync() {
		String want = choice();
		if (want.equals(loaded)) {
			return;
		}
		loaded = want;
		int gen = ++generation;
		clearImage();
		custom = want.startsWith("image:");
		stillFailed = false;
		pendingStill = null;
		Path cubeTarget = null;
		if (want.startsWith("image:")) {
			File file = folder().resolve(want.substring(6)).toFile();
			if (file.getName().toLowerCase(Locale.ROOT).endsWith(".gif")) {
				gif = new GifPlayer(file, 1920, 1080);
			} else {
				loadStill(file, gen);
			}
		} else if (want.startsWith("cube:")) {
			cubeTarget = folder().resolve(want.substring(5));
		}
		applyPanorama(cubeTarget);
	}

	private static void clearImage() {
		if (gif != null) {
			gif.stop();
			gif = null;
		}
		if (texture != null) {
			texture.close();
			texture = null;
		}
		uploadedVersion = -1;
		shownAt = 0;
	}

	private static void loadStill(File file, int gen) {
		Minecraft mc = Minecraft.getInstance();
		Util.ioPool().execute(() -> {
			try {
				BufferedImage image = ImageIO.read(file);
				if (image == null) {
					throw new IOException("not an image");
				}
				float fit = Math.min(1, MAX_IMAGE / (float) Math.max(image.getWidth(), image.getHeight()));
				int w = Math.max(1, Math.round(image.getWidth() * fit));
				int h = Math.max(1, Math.round(image.getHeight() * fit));
				BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = scaled.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				g.drawImage(image, 0, 0, w, h, null);
				g.dispose();
				int[] pixels = scaled.getRGB(0, 0, w, h, null, 0, w);
				for (int i = 0; i < pixels.length; i++) {
					int c = pixels[i];
					pixels[i] = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
				}
				// The render thread picks it up on the next menu frame (works even before the game has finished loading).
				pendingW = w;
				pendingH = h;
				pendingGen = gen;
				pendingStill = pixels;
			} catch (Exception e) {
				Kairo.LOGGER.warn("Couldn't load menu background {}", file, e);
				if (gen == generation) {
					stillFailed = true;
					mc.execute(() -> Toasts.show("Couldn't load that background", 0xFFFF7A7A));
				}
			}
		});
	}

	/** Creates or refreshes the texture from ABGR pixels. Render thread. */
	private static void upload(int[] abgr, int w, int h) {
		if (texture == null || texW != w || texH != h) {
			if (texture != null) {
				texture.close();
			}
			texture = new DynamicTexture(() -> "Kairo menu background", new NativeImage(w, h, false));
			texW = w;
			texH = h;
		}
		NativeImage pixels = texture.getPixels();
		if (pixels == null) {
			return;
		}
		MemoryUtil.memIntBuffer(pixels.getPointer(), w * h).put(abgr, 0, w * h);
		texture.upload();
	}

	/**
	 * At start-up (before the first resource load): put the chosen custom panorama in place, so it shows
	 * without an extra reload.
	 */
	public static void prepareAtStartup() {
		String want = choice();
		try {
			writePanorama(want.startsWith("cube:") ? folder().resolve(want.substring(5)) : null);
		} catch (IOException e) {
			Kairo.LOGGER.warn("Couldn't prepare the custom panorama", e);
		}
		// Start decoding a chosen image or GIF right away, so it's ready before the title screen appears.
		sync();
	}

	/**
	 * Custom panorama: copy its six faces into the client pack (or remove them for vanilla) and reload
	 * resources if anything changed.
	 */
	private static void applyPanorama(@Nullable Path source) {
		Minecraft mc = Minecraft.getInstance();
		Util.ioPool().execute(() -> {
			boolean changed;
			try {
				changed = writePanorama(source);
			} catch (IOException e) {
				Kairo.LOGGER.warn("Couldn't set up the custom panorama {}", source, e);
				mc.execute(() -> Toasts.show("That panorama needs panorama_0 to panorama_5", 0xFFFF7A7A));
				return;
			}
			if (changed && mc.isGameLoadFinished()) {
				mc.execute(() -> {
					Toasts.show("Applying panorama…", Theme.accent());
					mc.reloadResourcePacks();
				});
			}
		});
	}

	/** Returns true if the files in the pack changed. */
	private static synchronized boolean writePanorama(@Nullable Path source) throws IOException {
		if (source != null && !PanoramaFolders.isPanorama(source)) {
			throw new IOException("not a complete panorama: " + source);
		}
		boolean changed = false;
		for (int i = 0; i < 6; i++) {
			Path target = TotemPack.panoramaFace(i);
			if (source == null) {
				changed |= Files.deleteIfExists(target);
				continue;
			}
			byte[] png = PanoramaFolders.facePng(PanoramaFolders.face(source, i));
			if (!Files.isRegularFile(target) || !java.util.Arrays.equals(Files.readAllBytes(target), png)) {
				Files.createDirectories(target.getParent());
				Files.write(target, png);
				changed = true;
			}
		}
		return changed;
	}

	// ---- drawing (render thread, right before the GUI) ----

	/** Draws the image into the main frame if a screen asked for it this frame. */
	public static void renderIfRequested() {
		if (!requested) {
			return;
		}
		requested = false;
		int[] still = pendingStill;
		if (still != null && pendingGen == generation) {
			pendingStill = null;
			upload(still, pendingW, pendingH);
		}
		if (gif != null) {
			if (gif.failed()) {
				gif.stop();
				gif = null;
				custom = false;
				Toasts.show("Couldn't play that GIF", 0xFFFF7A7A);
				return;
			}
			int[] frame = gif.latest();
			if (frame != null && gif.version() != uploadedVersion) {
				uploadedVersion = gif.version();
				upload(frame, gif.width(), gif.height());
			}
		}
		if (texture != null && shownAt == 0) {
			shownAt = System.nanoTime();
		}
		if (texture == null) {
			return;
		}
		try {
			draw();
		} catch (RuntimeException e) {
			Kairo.LOGGER.error("Menu background failed", e);
			clearImage();
		}
	}

	/** The chosen darkening, faded in from black when the image first appears. */
	private static float darken(float dim) {
		float t = Math.clamp((System.nanoTime() - shownAt) / 1e9f / FADE_SECONDS, 0f, 1f);
		float fade = t * t * (3 - 2 * t);
		return 1 - (1 - dim) * fade;
	}

	private static void draw() {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTextureView target = main.getColorTextureView();
		if (target == null) {
			return;
		}
		// Cover fit: fill the screen, crop the overflowing side.
		float screenAspect = main.width / (float) main.height;
		float imageAspect = texW / (float) texH;
		float du = imageAspect > screenAspect ? screenAspect / imageAspect : 1;
		float dv = imageAspect > screenAspect ? 1 : imageAspect / screenAspect;
		float cx = 0.5f;
		float cy = 0.5f;
		MainMenuSettings s = MainMenuSettings.INSTANCE;
		if (s.backgroundMotion.isOn() && !Animation.reduceMotion()) {
			// Slow drift and breathing zoom; small enough to never show an edge.
			float t = (System.nanoTime() - startNanos) / 1e9f;
			float zoom = 1.08f + 0.03f * (float) Math.sin(t * 0.05);
			du /= zoom;
			dv /= zoom;
			cx += (1 - du) * 0.5f * 0.8f * (float) Math.sin(t * 0.031);
			cy += (1 - dv) * 0.5f * 0.8f * (float) Math.sin(t * 0.023 + 1.3);
		}
		float u0 = Math.clamp(cx - du / 2, 0, 1 - du);
		float v0 = Math.clamp(cy - dv / 2, 0, 1 - dv);

		if (config == null) {
			config = new MappableRingBuffer(() -> "Kairo background config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 32);
		}
		GpuBuffer buffer = config.currentBuffer();
		try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
			Std140Builder.intoBuffer(view.data())
				.putVec4(u0, v0, du, dv)
				.putVec4(darken(s.backgroundDim.floatValue()), 0, 0, 0);
		}
		var encoder = RenderSystem.getDevice().createCommandEncoder();
		try (RenderPass pass = encoder.createRenderPass(() -> "Kairo menu background", target, Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(KairoPipelines.BACKGROUND));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("InSampler", texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
			pass.setUniform("BackgroundConfig", buffer);
			pass.draw(3, 1, 0, 0);
		}
		config.rotate();
	}
}
