package dev.mordo.kairo.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.gui.FilePicker;
import dev.mordo.kairo.gui.Toasts;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Kairo Void "Custom sky": your own picture, loaded once in the background and kept as a texture the sky pass
 * samples by view direction (a wide 2:1 panorama wraps all the way round). Pictures live in config/kairo-void/skies.
 */
public final class SkyImage {
	private static final int MAX_W = 4096;
	private static final int MAX_H = 2048;

	private static String loaded = "";
	private static @Nullable DynamicTexture texture;
	private static @Nullable DynamicTexture blank;
	private static volatile int @Nullable [] pending;
	private static volatile int pendingW;
	private static volatile int pendingH;
	private static volatile int generation;

	private SkyImage() {
	}

	public static Path folder() {
		return ClientInfo.configDir("skies");
	}

	/** The pictures in the folder, by file name. */
	public static List<Path> list() {
		List<Path> out = new ArrayList<>();
		Path dir = folder();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.sorted().toList()) {
					String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
					if (n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".bmp")) {
						out.add(p);
					}
				}
			} catch (IOException e) {
				Kairo.LOGGER.warn("Couldn't list {}", dir, e);
			}
		}
		return out;
	}

	/** System file dialog; the picture is copied in and chosen. */
	public static void importImage() {
		FilePicker.importInto(folder(), "Images", "png;jpg;jpeg;bmp", file -> {
			VoidSettings.INSTANCE.skyImage.set("image:" + file.getFileName());
			VoidSettings.INSTANCE.customSky.set(true);
		});
	}

	/** True once the chosen picture is ready to draw. */
	public static boolean ready() {
		sync();
		return texture != null;
	}

	/** The picture to sample (a black pixel when there is none, so the pass always has something bound). */
	public static GpuTextureView view() {
		sync();
		if (texture != null) {
			return texture.getTextureView();
		}
		if (blank == null) {
			NativeImage px = new NativeImage(1, 1, false);
			px.setPixel(0, 0, 0xFF000000);
			blank = new DynamicTexture(() -> "Kairo sky blank", px);
		}
		return blank.getTextureView();
	}

	public static GpuSampler sampler() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
	}

	/** Render thread: follows the chosen file, uploads a finished load. */
	private static void sync() {
		String want = VoidSettings.INSTANCE.customSky.isOn() ? VoidSettings.INSTANCE.skyImage.get() : "";
		if (!want.equals(loaded)) {
			loaded = want;
			int gen = ++generation;
			close();
			if (want.startsWith("image:")) {
				load(folder().resolve(want.substring(6)), gen);
			}
		}
		int[] px = pending;
		if (px != null) {
			pending = null;
			close();
			NativeImage img = new NativeImage(pendingW, pendingH, false);
			MemoryUtil.memIntBuffer(img.getPointer(), pendingW * pendingH).put(px);
			texture = new DynamicTexture(() -> "Kairo custom sky", img);
		}
	}

	private static void close() {
		if (texture != null) {
			texture.close();
			texture = null;
		}
	}

	private static void load(Path file, int gen) {
		Util.ioPool().execute(() -> {
			try {
				BufferedImage image = ImageIO.read(file.toFile());
				if (image == null) {
					throw new IOException("not an image");
				}
				float fit = Math.min(1, Math.min(MAX_W / (float) image.getWidth(), MAX_H / (float) image.getHeight()));
				int w = Math.max(1, Math.round(image.getWidth() * fit));
				int h = Math.max(1, Math.round(image.getHeight() * fit));
				BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				Graphics2D g = scaled.createGraphics();
				g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
				g.drawImage(image, 0, 0, w, h, null);
				g.dispose();
				int[] px = scaled.getRGB(0, 0, w, h, null, 0, w);
				for (int i = 0; i < px.length; i++) {
					int c = px[i];
					px[i] = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
				}
				if (gen == generation) {
					pendingW = w;
					pendingH = h;
					pending = px;
				}
			} catch (Exception e) {
				Kairo.LOGGER.warn("Couldn't load sky picture {}", file, e);
				net.minecraft.client.Minecraft.getInstance().execute(() -> Toasts.show("Couldn't load that sky picture", 0xFFFF7A7A));
			}
		});
	}
}
