package dev.mordo.kairo.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.render.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Random;

/**
 * Cape textures, painted in code at 4x Minecraft's resolution (256x128, so a cape face is 40x64 pixels), or made
 * from your own image. The current one is cached and only repainted when the choice or its colours change.
 */
public final class Capes {
	public enum Design {
		KAIRO("Kairo", false), GRADIENT("Gradient", false), SUNSET("Sunset", false), OCEAN("Ocean", true), GALAXY("Galaxy", true),
		AURORA("Aurora", true), FLAMES("Flames", true), FROST("Frost", false), RAINBOW("Rainbow", true), STRIPES("Stripes", false),
		CHECKER("Checker", false), CARBON("Carbon", false), NEON("Neon grid", true), CHERRY("Cherry blossom", false), LAVA("Lava", true),
		MIDNIGHT("Midnight", false);

		private final String label;
		private final boolean animated;

		Design(String label, boolean animated) {
			this.label = label;
			this.animated = animated;
		}

		public String label() {
			return label;
		}

		/** Moving designs (repainted about 20 times a second while worn). */
		public boolean animated() {
			return animated;
		}
	}

	/** Every cape design (both editions). */
	public static Design[] designs() {
		return Design.values();
	}

	private static final int SCALE = 4;
	private static final int W = 64 * SCALE;
	private static final int H = 32 * SCALE;
	/** The cape model's UV area: 22x17 pixels at Minecraft scale (front and back faces plus edges). */
	private static final int AREA_W = 22 * SCALE;
	private static final int AREA_H = 17 * SCALE;

	private static @Nullable String currentKey;
	private static @Nullable Identifier current;
	private static @Nullable DynamicTexture currentTexture;
	private static @Nullable Design currentDesign;
	private static int currentC1;
	private static int currentC2;
	private static long lastFrame;
	private static final long START = System.nanoTime();
	private static int generation;

	private Capes() {
	}

	/** Swaps your skin's cape for the chosen one (only on your own player). */
	public static void apply(Cosmetics cosmetics, AvatarRenderState state) {
		String choice = cosmetics.cape.get();
		if (choice.equals("none")) {
			return;
		}
		Identifier texture = texture(choice, cosmetics.capeColor.argb(), cosmetics.capeColor2.argb());
		if (texture == null) {
			return;
		}
		PlayerSkin skin = state.skin;
		ClientAsset.ResourceTexture asset = new ClientAsset.ResourceTexture(texture, texture);
		state.skin = new PlayerSkin(skin.body(), asset, skin.elytra(), skin.model(), skin.secure());
		state.showCape = true;
	}

	/** The texture for a choice ("design:galaxy" or "file:my.png"), painted on first use. */
	public static @Nullable Identifier texture(String choice, int color1, int color2) {
		String key = choice + "|" + Integer.toHexString(color1) + "|" + Integer.toHexString(color2);
		if (key.equals(currentKey)) {
			animate();
			return current;
		}
		NativeImage image = paint(choice, color1, color2, 0);
		if (image == null) {
			currentKey = key;
			current = null;
			return null;
		}
		Identifier id = ClientInfo.id("cosmetics/cape_" + (++generation));
		Minecraft mc = Minecraft.getInstance();
		if (current != null) {
			mc.getTextureManager().release(current);
		}
		DynamicTexture texture = new DynamicTexture(() -> "kairo cape", image);
		mc.getTextureManager().register(id, texture);
		currentKey = key;
		current = id;
		currentTexture = texture;
		currentDesign = design(choice);
		currentC1 = color1;
		currentC2 = color2;
		return id;
	}

	/** Repaints a moving design in place, at most about 20 times a second. */
	private static void animate() {
		if (currentTexture == null || currentDesign == null || !currentDesign.animated()) {
			return;
		}
		long now = System.nanoTime();
		if (now - lastFrame < 50_000_000L) {
			return;
		}
		lastFrame = now;
		NativeImage pixels = currentTexture.getPixels();
		if (pixels == null) {
			return;
		}
		paintDesign(pixels, currentDesign, currentC1, currentC2, (now - START) / 1e9f);
		currentTexture.upload();
	}

	private static @Nullable Design design(String choice) {
		if (!choice.startsWith("design:")) {
			return null;
		}
		try {
			return Design.valueOf(choice.substring(7).toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static final java.util.Map<String, Identifier> PREVIEWS = new java.util.HashMap<>();
	private static final java.util.Map<String, DynamicTexture> PREVIEW_TEXTURES = new java.util.HashMap<>();
	private static final java.util.Map<String, Long> PREVIEW_FRAMES = new java.util.HashMap<>();

	/** A separate texture per choice for the wardrobe tiles (kept while the colours stay the same). */
	public static @Nullable Identifier preview(String choice, int color1, int color2) {
		String key = choice + "|" + Integer.toHexString(color1) + "|" + Integer.toHexString(color2);
		if (PREVIEWS.containsKey(key)) {
			Identifier cached = PREVIEWS.get(key);
			DynamicTexture live = PREVIEW_TEXTURES.get(key);
			Design design = design(choice);
			if (live != null && design != null && design.animated()) {
				long now = System.nanoTime();
				Long last = PREVIEW_FRAMES.get(key);
				if (last == null || now - last > 80_000_000L) {
					PREVIEW_FRAMES.put(key, now);
					if (live.getPixels() != null) {
						paintDesign(live.getPixels(), design, color1, color2, (now - START) / 1e9f);
						live.upload();
					}
				}
			}
			return cached;
		}
		if (PREVIEWS.size() > 64) {
			for (Identifier old : PREVIEWS.values()) {
				if (old != null) {
					Minecraft.getInstance().getTextureManager().release(old);
				}
			}
			PREVIEWS.clear();
			PREVIEW_TEXTURES.clear();
			PREVIEW_FRAMES.clear();
		}
		NativeImage image = paint(choice, color1, color2, 0);
		Identifier id = null;
		if (image != null) {
			id = ClientInfo.id("cosmetics/cape_preview_" + (++generation));
			DynamicTexture texture = new DynamicTexture(() -> "kairo cape preview", image);
			Minecraft.getInstance().getTextureManager().register(id, texture);
			PREVIEW_TEXTURES.put(key, texture);
		}
		PREVIEWS.put(key, id);
		return id;
	}

	/** UV rectangle of the cape's back face (what others see), as {u0, u1, v0, v1}. */
	public static float[] faceUv() {
		return new float[]{12f / 64, 22f / 64, 1f / 32, 17f / 32};
	}

	private static @Nullable NativeImage paint(String choice, int c1, int c2, float time) {
		NativeImage image = new NativeImage(W, H, true);
		if (choice.startsWith("file:")) {
			return fromFile(Cosmetics.capeFolder().resolve(choice.substring(5)), image) ? image : null;
		}
		Design design = design(choice);
		if (design == null) {
			return null;
		}
		paintDesign(image, design, c1, c2, time);
		return image;
	}

	private static void paintDesign(NativeImage image, Design design, int c1, int c2, float time) {
		Random random = new Random(design.ordinal() * 7919L);
		for (int y = 0; y < AREA_H; y++) {
			for (int x = 0; x < AREA_W; x++) {
				// Coordinates on one face: u across (0..1), v down (0..1). Both faces get the same picture.
				float u = faceU(x);
				float v = (y - SCALE) / (16f * SCALE);
				image.setPixel(x, y, color(design, u, Math.clamp(v, 0, 1), c1, c2, random, x, y, time));
			}
		}
	}

	/** Position across a face: the two 10-pixel faces sit at x 1..11 and 12..22 (Minecraft scale). */
	private static float faceU(int x) {
		float px = x / (float) SCALE;
		if (px >= 12) {
			return Math.clamp((px - 12) / 10f, 0, 1);
		}
		return Math.clamp((px - 1) / 10f, 0, 1);
	}

	private static int color(Design design, float u, float v, int c1, int c2, Random random, int x, int y, float t) {
		switch (design) {
			case KAIRO -> {
				int bg = Colors.lerp(0xFF101826, 0xFF080B11, v);
				// The ring mark in the upper middle.
				float dx = (u - 0.5f) * 1.0f;
				float dy = (v - 0.32f) * 1.6f;
				float r = (float) Math.sqrt(dx * dx + dy * dy);
				double angle = Math.toDegrees(Math.atan2(dy, dx));
				boolean top = angle < -60 && angle > -120;
				if (r > 0.20f && r < 0.27f) {
					return top ? 0xFFFF9A52 : (angle > -135 && angle < -45 ? bg : 0xFF7FD8FF);
				}
				if (r < 0.09f) {
					return 0xFF7FD8FF;
				}
				if (v > 0.9f) {
					return Colors.lerp(bg, 0xFF7FD8FF, (v - 0.9f) * 4);
				}
				return bg;
			}
			case GRADIENT -> {
				return Colors.lerp(c1 | 0xFF000000, c2 | 0xFF000000, v);
			}
			case SUNSET -> {
				int top = Colors.lerp(0xFF2B1055, 0xFFD7263D, Math.min(1, v * 1.6f));
				int col = Colors.lerp(top, 0xFFFFB347, Math.max(0, (v - 0.55f) * 2.2f));
				float sun = (float) Math.hypot(u - 0.5f, (v - 0.62f) * 1.4f);
				return sun < 0.18f && (int) (v * 40) % 4 != 0 ? Colors.lerp(0xFFFFE29A, col, sun / 0.18f * 0.4f) : col;
			}
			case OCEAN -> {
				// Rolling waves: the crests drift down the cape and sway sideways.
				float wave = (float) Math.sin(u * 12 + v * 6 - t * 2.4f) * 0.04f + (float) Math.sin(u * 5 - t * 1.3f) * 0.02f;
				int deep = Colors.lerp(0xFF7FD8FF, 0xFF06203A, Math.clamp(v + wave, 0, 1));
				float foam = (v + wave - t * 0.06f) * 30;
				int band = Math.floorMod((int) Math.floor(foam), 6);
				int col = band == 0 ? Colors.lerp(deep, 0xFFFFFFFF, 0.3f) : deep;
				float glint = noise(u * 9 + t * 0.7f, v * 9 - t * 0.4f);
				return glint > 0.82f ? Colors.lerp(col, 0xFFE8FBFF, (glint - 0.82f) * 3) : col;
			}
			case GALAXY -> {
				float n = noise(u * 5 + t * 0.05f, v * 7);
				int base = Colors.lerp(0xFF05030F, 0xFF2A0E4A, n);
				base = Colors.lerp(base, 0xFF1E5AA8, Math.max(0, noise(u * 3 + 9 - t * 0.04f, v * 4) - 0.55f) * 1.6f);
				// Twinkling stars.
				float twinkle = 0.55f + 0.45f * (float) Math.sin(t * 3 + hash(x, y) * 40);
				return random.nextInt(140) == 0 ? Colors.lerp(base, 0xFFFFFFFF, twinkle)
					: random.nextInt(400) == 0 ? Colors.lerp(base, 0xFF9FE3FF, twinkle) : base;
			}
			case AURORA -> {
				// Curtains of light that ripple sideways and slowly change colour.
				float band = (float) Math.sin(u * 7 + noise(u * 3 + t * 0.35f, v * 2) * 4 + t * 0.9f) * 0.12f + 0.35f;
				float band2 = (float) Math.sin(u * 5 - t * 0.6f + 2) * 0.1f + 0.58f;
				float d = Math.abs(v - band);
				float d2 = Math.abs(v - band2);
				int sky = Colors.lerp(0xFF04101C, 0xFF0B1E2E, v);
				float shift = 0.5f + 0.5f * (float) Math.sin(t * 0.4f);
				int glow = Colors.lerp(Colors.lerp(0xFF39FF9E, 0xFF5CE1FF, shift), 0xFFB06BFF, u);
				float shimmer = 0.75f + 0.25f * (float) Math.sin(t * 2.2f + u * 14);
				int col = Colors.lerp(sky, glow, Math.max(0, 0.5f - d * 3) * shimmer);
				col = Colors.lerp(col, 0xFFFF6BD6, Math.max(0, 0.25f - d2 * 3) * shimmer);
				return random.nextInt(260) == 0 ? 0xFFFFFFFF : col | 0xFF000000;
			}
			case FLAMES -> {
				// Flames licking upwards.
				float n = noise(u * 6 + (float) Math.sin(t * 1.3f + v * 4) * 0.25f, v * 5 + 3 + t * 2.2f);
				float n2 = noise(u * 11, v * 9 + t * 3.5f);
				float heat = Math.clamp(1 - v + (n - 0.5f) * 0.7f + (n2 - 0.5f) * 0.25f, 0, 1);
				int col = heat > 0.66f ? Colors.lerp(0xFFFFB000, 0xFFFFF2B0, (heat - 0.66f) * 3) : heat > 0.33f
					? Colors.lerp(0xFFD7263D, 0xFFFFB000, (heat - 0.33f) * 3) : Colors.lerp(0xFF1A0505, 0xFFD7263D, heat * 3);
				return col;
			}
			case FROST -> {
				float n = noise(u * 8, v * 8);
				int col = Colors.lerp(0xFFE6F6FF, 0xFF5AB8F0, v * 0.8f + n * 0.2f);
				boolean crack = Math.abs(noise(u * 4 + 5, v * 4) - 0.5f) < 0.02f;
				return crack ? 0xFFFFFFFF : col;
			}
			case RAINBOW -> {
				return Colors.hsv(v * 0.85f - t * 0.15f, 0.75f, 1f);
			}
			case STRIPES -> {
				return ((int) ((u + v) * 9)) % 2 == 0 ? c1 | 0xFF000000 : c2 | 0xFF000000;
			}
			case CHECKER -> {
				return (((int) (u * 5) + (int) (v * 8)) % 2 == 0) ? c1 | 0xFF000000 : c2 | 0xFF000000;
			}
			case CARBON -> {
				int cell = ((x / 3) + (y / 3)) % 2;
				int base = cell == 0 ? 0xFF1A1C20 : 0xFF2A2D33;
				return (x + y) % 6 == 0 ? Colors.lerp(base, 0xFF4A4F57, 0.6f) : base;
			}
			case NEON -> {
				int bg = Colors.lerp(0xFF0B0418, 0xFF14092A, v);
				boolean line = (x % (2 * SCALE) == 0) || (y % (2 * SCALE) == 0);
				// A pulse of light running down the grid.
				float pulse = Math.max(0, 1 - Math.abs(v - ((t * 0.35f) % 1.3f)) * 6);
				return line ? Colors.lerp(Colors.lerp(c1 | 0xFF000000, bg, v * 0.6f), 0xFFFFFFFF, pulse * 0.6f) : bg;
			}
			case CHERRY -> {
				int bg = Colors.lerp(0xFFFFE4EC, 0xFFF7A8C4, v);
				return random.nextInt(18) == 0 ? (random.nextBoolean() ? 0xFFFF7EB0 : 0xFFFFFFFF) : bg;
			}
			case LAVA -> {
				float n = noise(u * 5 + t * 0.12f, v * 6 - t * 0.3f);
				return n > 0.62f ? Colors.lerp(0xFFFF6A00, 0xFFFFD25A, (n - 0.62f) * 2.5f) : Colors.lerp(0xFF1C0F0C, 0xFF3A1A10, n);
			}
			case MIDNIGHT -> {
				int bg = Colors.lerp(0xFF0A0F1F, 0xFF02030A, v);
				float moon = (float) Math.hypot(u - 0.68f, (v - 0.22f) * 1.5f);
				if (moon < 0.13f && Math.hypot(u - 0.74f, (v - 0.19f) * 1.5f) > 0.11f) {
					return 0xFFF2F0D8;
				}
				return random.nextInt(200) == 0 ? 0xFFFFFFFF : bg;
			}
		}
		return 0xFF000000;
	}

	/** Cheap smooth value noise, 0..1. */
	private static float noise(float x, float y) {
		int xi = (int) Math.floor(x);
		int yi = (int) Math.floor(y);
		float xf = x - xi;
		float yf = y - yi;
		float a = hash(xi, yi);
		float b = hash(xi + 1, yi);
		float c = hash(xi, yi + 1);
		float d = hash(xi + 1, yi + 1);
		float sx = xf * xf * (3 - 2 * xf);
		float sy = yf * yf * (3 - 2 * yf);
		return a + (b - a) * sx + (c - a) * sy + (a - b - c + d) * sx * sy;
	}

	private static float hash(int x, int y) {
		int h = x * 374761393 + y * 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}


	/** Your image, cropped to the cape's shape and scaled onto both faces. */
	private static boolean fromFile(Path file, NativeImage out) {
		if (!Files.isRegularFile(file)) {
			return false;
		}
		try {
			BufferedImage src = ImageIO.read(file.toFile());
			if (src == null) {
				return false;
			}
			// Crop to the face's 10:16 shape (centred), then sample.
			float want = 10f / 16f;
			int sw = src.getWidth();
			int sh = src.getHeight();
			int cw = sw;
			int ch = sh;
			if ((float) sw / sh > want) {
				cw = Math.round(sh * want);
			} else {
				ch = Math.round(sw / want);
			}
			int ox = (sw - cw) / 2;
			int oy = (sh - ch) / 2;
			for (int y = 0; y < AREA_H; y++) {
				for (int x = 0; x < AREA_W; x++) {
					float u = faceU(x);
					float v = Math.clamp((y - SCALE) / (16f * SCALE), 0, 1);
					int sx = ox + Math.min(cw - 1, (int) (u * cw));
					int sy = oy + Math.min(ch - 1, (int) (v * ch));
					out.setPixel(x, y, src.getRGB(sx, sy) | 0xFF000000);
				}
			}
			return true;
		} catch (Exception e) {
			Kairo.LOGGER.warn("Couldn't use {} as a cape", file, e);
			return false;
		}
	}
}
