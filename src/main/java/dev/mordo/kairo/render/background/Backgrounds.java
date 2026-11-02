package dev.mordo.kairo.render.background;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.gui.FilePicker;
import dev.mordo.kairo.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * The background choices (Minecraft's panorama, your images and GIFs, your panoramas in
 * config/kairo/backgrounds), small thumbnails for the picker, and importing new ones.
 */
public final class Backgrounds {
	public enum Kind {
		PANORAMA, IMAGE, GIF, CUBE
	}

	public record Entry(String id, String label, Kind kind, @Nullable Path path) {
	}

	private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "jpeg", "bmp", "gif");
	private static final int THUMB_W = 192;
	private static final int THUMB_H = 108;

	private static List<Entry> cached = List.of();
	private static long lastScan;
	private static final Map<String, @Nullable DynamicTexture> THUMBS = new HashMap<>();
	private static final Map<String, Boolean> THUMB_LOADING = new HashMap<>();

	private Backgrounds() {
	}

	/** Minecraft's panorama first, then files and panorama folders (rescanned every 2 s at most). */
	public static List<Entry> list() {
		long now = System.currentTimeMillis();
		if (now - lastScan < 2000 && !cached.isEmpty()) {
			return cached;
		}
		lastScan = now;
		List<Entry> list = new ArrayList<>();
		list.add(new Entry(MainMenuSettings.VANILLA_BACKGROUND, dev.mordo.kairo.core.Edition.isVoid() ? "Void scene" : "Minecraft panorama", Kind.PANORAMA, null));
		Path dir = MenuBackground.folder();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				for (Path p : files.sorted().toList()) {
					String name = p.getFileName().toString();
					if (Files.isDirectory(p)) {
						if (PanoramaFolders.isPanorama(p)) {
							list.add(new Entry("cube:" + name, name, Kind.CUBE, p));
						}
					} else if (IMAGE_TYPES.contains(extension(name))) {
						list.add(new Entry("image:" + name, stripExtension(name), extension(name).equals("gif") ? Kind.GIF : Kind.IMAGE, p));
					}
				}
			} catch (IOException e) {
				Kairo.LOGGER.warn("Couldn't list {}", dir, e);
			}
		}
		cached = list;
		return list;
	}

	public static void rescanSoon() {
		lastScan = 0;
	}

	private static String extension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	private static String stripExtension(String name) {
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}

	// ---- importing ----

	/** System file dialog for an image or GIF; selects it once copied in. */
	public static void importImage() {
		FilePicker.importInto(MenuBackground.folder(), "Images and GIFs", "png;jpg;jpeg;bmp;gif", file -> {
			rescanSoon();
			MainMenuSettings.INSTANCE.background.set("image:" + file.getFileName());
		});
	}

	/** System folder dialog for a panorama folder (panorama_0 … panorama_5); copies it in and selects it. */
	public static void importPanorama() {
		FilePicker.pickFolder(folder -> {
			if (!PanoramaFolders.isPanorama(folder)) {
				Toasts.show("Pick a folder with panorama_0 to panorama_5", 0xFFFF7A7A);
				return;
			}
			Path target = MenuBackground.folder().resolve(folder.getFileName().toString());
			try {
				if (!target.toAbsolutePath().normalize().equals(folder.toAbsolutePath().normalize())) {
					Files.createDirectories(target);
					for (int i = 0; i < 6; i++) {
						Path face = PanoramaFolders.face(folder, i);
						Files.copy(face, target.resolve(face.getFileName().toString()), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
					}
				}
				rescanSoon();
				MainMenuSettings.INSTANCE.background.set("cube:" + target.getFileName());
			} catch (IOException e) {
				Kairo.LOGGER.warn("Couldn't import panorama {}", folder, e);
				Toasts.show("Couldn't import that panorama", 0xFFFF7A7A);
			}
		});
	}

	// ---- thumbnails ----

	/** A small cover-cropped preview texture, loading in the background (null until ready). */
	public static @Nullable Identifier thumbnail(Entry entry) {
		String key = entry.id();
		if (THUMBS.containsKey(key)) {
			return THUMBS.get(key) == null ? null : thumbId(key);
		}
		if (THUMB_LOADING.putIfAbsent(key, Boolean.TRUE) != null) {
			return null;
		}
		Minecraft mc = Minecraft.getInstance();
		Util.ioPool().execute(() -> {
			BufferedImage image = null;
			try {
				image = switch (entry.kind()) {
					case PANORAMA -> {
						// Through the resource manager (the jar only has a placeholder), skipping the client's own pack.
						var stack = mc.getResourceManager().getResourceStack(
							Identifier.withDefaultNamespace("textures/gui/title/background/panorama_0.png"));
						BufferedImage found = null;
						for (int i = stack.size() - 1; i >= 0 && found == null; i--) {
							var resource = stack.get(i);
							if (!resource.sourcePackId().equals(dev.mordo.kairo.mods.totem.TotemPack.ID)) {
								try (InputStream in = resource.open()) {
									found = ImageIO.read(in);
								}
							}
						}
						yield found;
					}
					case CUBE -> ImageIO.read(PanoramaFolders.face(entry.path(), 0).toFile());
					default -> ImageIO.read(entry.path().toFile()); // GIF: first frame
				};
			} catch (Exception e) {
				Kairo.LOGGER.debug("No thumbnail for {}", key, e);
			}
			int[] pixels = image == null ? null : cover(image);
			mc.execute(() -> {
				DynamicTexture texture = null;
				if (pixels != null) {
					com.mojang.blaze3d.platform.NativeImage img = new com.mojang.blaze3d.platform.NativeImage(THUMB_W, THUMB_H, false);
					org.lwjgl.system.MemoryUtil.memIntBuffer(img.getPointer(), THUMB_W * THUMB_H).put(pixels);
					texture = new DynamicTexture(() -> "Kairo background thumbnail", img);
					mc.getTextureManager().register(thumbId(key), texture);
				}
				THUMBS.put(key, texture);
			});
		});
		return null;
	}

	private static Identifier thumbId(String key) {
		return dev.mordo.kairo.core.ClientInfo.id("bg_thumb/" + Integer.toHexString(key.hashCode()));
	}

	/** Cover-crops to the thumbnail size and returns ABGR (RGBA byte) pixels. */
	private static int[] cover(BufferedImage image) {
		float scale = Math.max(THUMB_W / (float) image.getWidth(), THUMB_H / (float) image.getHeight());
		int w = Math.round(image.getWidth() * scale);
		int h = Math.round(image.getHeight() * scale);
		BufferedImage out = new BufferedImage(THUMB_W, THUMB_H, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, (THUMB_W - w) / 2, (THUMB_H - h) / 2, w, h, null);
		g.dispose();
		int[] px = out.getRGB(0, 0, THUMB_W, THUMB_H, null, 0, THUMB_W);
		for (int i = 0; i < px.length; i++) {
			int c = px[i];
			px[i] = 0xFF000000 | ((c & 0xFF) << 16) | (c & 0xFF00) | ((c >> 16) & 0xFF);
		}
		return px;
	}

	/** Forget thumbnails (files changed). */
	public static void clearThumbnails() {
		Minecraft mc = Minecraft.getInstance();
		for (String key : THUMBS.keySet()) {
			if (THUMBS.get(key) != null) {
				mc.getTextureManager().release(thumbId(key));
			}
		}
		THUMBS.clear();
		THUMB_LOADING.clear();
	}
}
