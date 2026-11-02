package dev.mordo.kairo.mods.totem;

import dev.mordo.kairo.render.Colors;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.ArrayDeque;

/**
 * Builds the totem texture: any image (PNG, JPEG, BMP, GIF) cropped to its content, made square and scaled to
 * a size the item renderer handles well; or the vanilla totem recoloured.
 */
public final class TotemImages {
	/** Item textures this size still look sharp in hand and keep the 3D item model cheap. */
	private static final int MAX_SIZE = 64;

	private TotemImages() {
	}

	/** Built-in recolours: id -> {hue shift in degrees (NaN = grey), saturation factor, brightness factor}. */
	public record Tint(String id, String label, float hueShift, float saturation, float brightness) {
	}

	public static final Tint[] TINTS = {
		new Tint("frost", "Frost", 150, 0.9f, 1.05f),
		new Tint("ember", "Ember", -38, 1.15f, 1.0f),
		new Tint("amethyst", "Amethyst", 228, 0.85f, 1.0f),
		new Tint("emerald", "Emerald", 78, 1.0f, 0.95f),
		new Tint("rose", "Rose", 290, 0.75f, 1.08f),
		new Tint("shadow", "Shadow", Float.NaN, 0.0f, 0.55f),
	};

	public static @Nullable Tint tint(String id) {
		for (Tint t : TINTS) {
			if (t.id().equals(id)) {
				return t;
			}
		}
		return null;
	}

	public static BufferedImage vanilla() throws IOException {
		try (InputStream in = Minecraft.class.getResourceAsStream("/assets/minecraft/textures/item/totem_of_undying.png")) {
			if (in == null) {
				throw new IOException("Vanilla totem texture not found");
			}
			return argb(ImageIO.read(in));
		}
	}

	public static BufferedImage recolor(BufferedImage source, Tint tint) {
		BufferedImage out = argb(source);
		for (int y = 0; y < out.getHeight(); y++) {
			for (int x = 0; x < out.getWidth(); x++) {
				int argb = out.getRGB(x, y);
				int a = argb >>> 24;
				if (a == 0) {
					continue;
				}
				float[] hsv = Colors.toHsv(argb);
				float h = Float.isNaN(tint.hueShift()) ? hsv[0] : (hsv[0] + tint.hueShift() / 360f + 1) % 1f;
				float s = Math.clamp(hsv[1] * tint.saturation(), 0, 1);
				float v = Math.clamp(hsv[2] * tint.brightness(), 0, 1);
				out.setRGB(x, y, Colors.withAlpha(Colors.hsv(h, s, v), a / 255f));
			}
		}
		return out;
	}

	/** Reads an image file and turns it into a square item texture. */
	public static BufferedImage fromFile(Path file, boolean removeBackground) throws IOException {
		BufferedImage read = ImageIO.read(file.toFile());
		if (read == null) {
			throw new IOException("Not an image (PNG, JPEG, BMP or GIF)");
		}
		BufferedImage image = argb(read);
		if (removeBackground) {
			removeBackground(image);
		}
		image = cropToContent(image);
		return squareAndScale(image);
	}

	// ---- processing ----

	private static BufferedImage argb(BufferedImage source) {
		if (source.getType() == BufferedImage.TYPE_INT_ARGB) {
			return source;
		}
		BufferedImage out = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.drawImage(source, 0, 0, null);
		g.dispose();
		return out;
	}

	/**
	 * Clears the background: flood-fills from every edge pixel through colours close to the edge colour.
	 * Good enough for photos and logos on a plain background (the usual JPEG case).
	 */
	private static void removeBackground(BufferedImage image) {
		int w = image.getWidth();
		int h = image.getHeight();
		int reference = image.getRGB(0, 0);
		boolean[] seen = new boolean[w * h];
		ArrayDeque<int[]> queue = new ArrayDeque<>();
		for (int x = 0; x < w; x++) {
			queue.add(new int[]{x, 0});
			queue.add(new int[]{x, h - 1});
		}
		for (int y = 0; y < h; y++) {
			queue.add(new int[]{0, y});
			queue.add(new int[]{w - 1, y});
		}
		while (!queue.isEmpty()) {
			int[] p = queue.poll();
			int x = p[0];
			int y = p[1];
			if (x < 0 || y < 0 || x >= w || y >= h || seen[y * w + x]) {
				continue;
			}
			seen[y * w + x] = true;
			int c = image.getRGB(x, y);
			if ((c >>> 24) < 16 || distance(c, reference) < 48) {
				image.setRGB(x, y, 0);
				queue.add(new int[]{x + 1, y});
				queue.add(new int[]{x - 1, y});
				queue.add(new int[]{x, y + 1});
				queue.add(new int[]{x, y - 1});
			}
		}
	}

	private static float distance(int a, int b) {
		int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
		int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
		int db = (a & 0xFF) - (b & 0xFF);
		return (float) Math.sqrt(dr * dr + dg * dg + db * db);
	}

	/** Trims fully transparent borders. */
	private static BufferedImage cropToContent(BufferedImage image) {
		int minX = image.getWidth();
		int minY = image.getHeight();
		int maxX = -1;
		int maxY = -1;
		for (int y = 0; y < image.getHeight(); y++) {
			for (int x = 0; x < image.getWidth(); x++) {
				if ((image.getRGB(x, y) >>> 24) > 8) {
					minX = Math.min(minX, x);
					minY = Math.min(minY, y);
					maxX = Math.max(maxX, x);
					maxY = Math.max(maxY, y);
				}
			}
		}
		if (maxX < 0) {
			return image; // all transparent: leave it
		}
		return image.getSubimage(minX, minY, maxX - minX + 1, maxY - minY + 1);
	}

	/** Centres the image on a transparent square and scales it to a power of two between 16 and 64. */
	private static BufferedImage squareAndScale(BufferedImage image) {
		int side = Math.max(image.getWidth(), image.getHeight());
		int target = 16;
		while (target < side && target < MAX_SIZE) {
			target *= 2;
		}
		BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = square.createGraphics();
		g.drawImage(image, (side - image.getWidth()) / 2, (side - image.getHeight()) / 2, null);
		g.dispose();
		if (side == target) {
			return square;
		}
		if (side < target) {
			// Small pixel art: scale up with hard edges.
			return scale(square, target, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
		}
		// Big images: halve with bilinear filtering until close, then one last step (keeps detail without aliasing).
		BufferedImage current = square;
		int size = side;
		while (size / 2 >= target) {
			size /= 2;
			current = scale(current, size, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		}
		return size == target ? current : scale(current, target, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
	}

	private static BufferedImage scale(BufferedImage source, int size, Object interpolation) {
		BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = out.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, interpolation);
		g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
		g.drawImage(source, 0, 0, size, size, null);
		g.dispose();
		return out;
	}
}
