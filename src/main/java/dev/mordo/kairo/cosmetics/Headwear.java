package dev.mordo.kairo.cosmetics;

import dev.mordo.kairo.cosmetics.Cosmetics.BandanaStyle;
import dev.mordo.kairo.cosmetics.Cosmetics.Pattern;
import dev.mordo.kairo.render.Colors;

/**
 * Hats and bandanas, built in the head's space (pixels; the head is the box x -4..4, y -8..0, z -4..4, the face is
 * -z) or, for scarves, the body's (x -4..4, y 0..12, z -2..2).
 */
final class Headwear {
	private Headwear() {
	}

	/** One cat ear (side -1 or 1), stacked boxes narrowing upwards with a pink inside. Posed by {@link CatEars}. */
	static void catEar(Geo g, Cosmetics c, int s) {
		int main = c.hatColor.argb() | 0xFF000000;
		float x = s * 2.6f;
		for (int i = 0; i < 4; i++) {
			float half = 1.6f - i * 0.4f;
			g.box(x - half, -8.8f - i * 0.8f, -2.6f, x + half, -8 - i * 0.8f, -1.4f, main, false);
		}
		g.box(x - 0.7f, -9.6f, -2.75f, x + 0.7f, -8.2f, -2.6f, 0xFFFF9EB5, false);
		g.box(x - 0.35f, -10.4f, -2.72f, x + 0.35f, -9.6f, -2.6f, 0xFFFF9EB5, false);
	}

	static void hat(Geo g, Cosmetics c, float time) {
		int main = c.hatColor.argb() | 0xFF000000;
		int detail = c.hatColor2.argb() | 0xFF000000;
		switch (c.hat.get()) {
			case CROWN -> {
				int gold = detail;
				// A band that sits on the head with points around it, and gems at the front.
				ring(g, 4.5f, -10, -8, 0.6f, gold, false);
				for (int i = 0; i < 4; i++) {
					float x = -3.4f + i * 2.27f;
					g.box(x - 0.6f, -12, -4.5f, x + 0.6f, -10, -3.9f, gold, false);
					g.box(x - 0.6f, -12, 3.9f, x + 0.6f, -10, 4.5f, gold, false);
					g.box(-4.5f, -12, x - 0.6f, -3.9f, -10, x + 0.6f, gold, false);
					g.box(3.9f, -12, x - 0.6f, 4.5f, -10, x + 0.6f, gold, false);
				}
				g.cube(0, -9, -4.6f, 1.4f, 1.4f, 0.5f, main == 0xFF1A1D24 ? 0xFFD7263D : main, true);
				g.cube(-2.4f, -9, -4.6f, 1, 1, 0.5f, 0xFF4AA3FF, true);
				g.cube(2.4f, -9, -4.6f, 1, 1, 0.5f, 0xFF39D98A, true);
			}
			case HALO -> {
				float bob = (float) Math.sin(time * 2) * 0.4f;
				int glow = detail;
				for (int i = 0; i < 24; i++) {
					double a = i * Math.PI * 2 / 24;
					float x = (float) Math.cos(a) * 4.6f;
					float z = (float) Math.sin(a) * 4.6f;
					g.cube(x, -12.5f + bob, z, 1.4f, 0.6f, 1.4f, glow, true);
				}
			}
			case TOP_HAT -> {
				g.box(-6, -8.6f, -6, 6, -8, 6, main, false);
				g.box(-4, -15.5f, -4, 4, -8.6f, 4, main, false);
				g.box(-4.1f, -10.2f, -4.1f, 4.1f, -8.6f, 4.1f, detail, false);
			}
			case PARTY_HAT -> {
				for (int i = 0; i < 8; i++) {
					float half = 3.6f - i * 0.42f;
					float y = -8 - i * 1.1f;
					g.box(-half, y - 1.1f, -half, half, y, half, i % 2 == 0 ? main : detail, false);
				}
				g.cube(0, -17.6f, 0, 1.6f, 1.6f, 1.6f, 0xFFFFFFFF, false);
			}
			case WITCH_HAT -> {
				g.box(-7.5f, -8.6f, -7.5f, 7.5f, -8, 7.5f, main, false);
				for (int i = 0; i < 10; i++) {
					float half = 4.2f - i * 0.38f;
					float bend = i > 5 ? (i - 5) * 0.7f : 0;
					float y = -8.6f - i * 1.1f;
					g.box(-half, y - 1.1f, -half + bend, half, y, half + bend, main, false);
				}
				g.box(-4.3f, -10, -4.3f, 4.3f, -8.6f, 4.3f, detail, false);
			}
			case CAT_EARS -> {
				for (int s = -1; s <= 1; s += 2) {
					catEar(g, c, s);
				}
			}
			case BUNNY_EARS -> {
				float sway = (float) Math.sin(time * 1.5f) * 0.6f;
				for (int s = -1; s <= 1; s += 2) {
					float[] base = {s * 1.8f, -8, -0.5f};
					float[] tip = {s * (2.6f + sway * s), -16, -0.2f};
					g.stick(base, tip, 1.8f, 0.8f, main, false);
					g.stick(new float[]{base[0], base[1] - 1, base[2] - 0.45f}, new float[]{tip[0], tip[1] + 1.5f, tip[2] - 0.45f}, 0.9f, 0.2f, 0xFFFF9EB5, false);
				}
			}
			case HORNS -> {
				for (int s = -1; s <= 1; s += 2) {
					float[] prev = {s * 3f, -8, -1.5f};
					for (int i = 1; i <= 5; i++) {
						float[] next = {s * (3f + i * 0.6f), -8 - i * 1.1f, -1.5f + i * 0.7f};
						g.stick(prev, next, 1.6f - i * 0.22f, 1.6f - i * 0.22f, i == 5 ? detail : main, false);
						prev = next;
					}
				}
			}
			case HEADPHONES -> {
				float[] prev = null;
				for (int i = 0; i <= 10; i++) {
					double a = Math.PI * i / 10;
					float[] pt = {(float) -Math.cos(a) * 4.7f, -4 - (float) Math.sin(a) * 5f, 0};
					if (prev != null) {
						g.stick(prev, pt, 1.2f, 0.9f, main, false);
					}
					prev = pt;
				}
				for (int s = -1; s <= 1; s += 2) {
					g.box(s > 0 ? 4 : -5.4f, -5.5f, -1.8f, s > 0 ? 5.4f : -4, -1.5f, 1.8f, main, false);
					g.box(s > 0 ? 5.4f : -5.6f, -4.5f, -1, s > 0 ? 5.6f : -5.4f, -2.5f, 1, detail, true);
				}
			}
			case SUNGLASSES -> {
				int lens = 0xF0101418;
				g.box(-3.7f, -5.2f, -4.6f, -0.6f, -3.2f, -4.2f, lens, false);
				g.box(0.6f, -5.2f, -4.6f, 3.7f, -3.2f, -4.2f, lens, false);
				g.box(-0.6f, -5, -4.55f, 0.6f, -4.5f, -4.25f, main, false);
				g.box(-4.3f, -5, -4.4f, -4.1f, -4.5f, 0, main, false);
				g.box(4.1f, -5, -4.4f, 4.3f, -4.5f, 0, main, false);
				g.box(-3.2f, -5, -4.65f, -2.4f, -4.6f, -4.6f, 0xCCFFFFFF, false);
				g.box(1.1f, -5, -4.65f, 1.9f, -4.6f, -4.6f, 0xCCFFFFFF, false);
			}
			case COWBOY -> {
				g.box(-7.5f, -8.6f, -6.5f, 7.5f, -8, 6.5f, main, false);
				g.box(-7.5f, -10, -6.5f, -6.5f, -8.6f, 6.5f, main, false);
				g.box(6.5f, -10, -6.5f, 7.5f, -8.6f, 6.5f, main, false);
				g.box(-4, -12.5f, -3.6f, 4, -8.6f, 3.6f, main, false);
				g.box(-1, -12.8f, -3.6f, 1, -12.4f, 3.6f, Colors.lerp(main, 0xFF000000, 0.3f), false);
				g.box(-4.1f, -9.8f, -3.7f, 4.1f, -8.6f, 3.7f, detail, false);
			}
			case BEANIE -> {
				g.box(-4.45f, -10, -4.45f, 4.45f, -6.5f, 4.45f, main, false);
				g.box(-4.55f, -7, -4.55f, 4.55f, -5.6f, 4.55f, detail, false);
				g.cube(0, -10.9f, 0, 1.8f, 1.8f, 1.8f, detail, false);
			}
			case NONE -> {
			}
		}
	}

	/** Bandanas worn on the head. */
	static void headBandana(Geo g, Cosmetics c, float time, float moving) {
		int main = c.bandanaColor.argb() | 0xFF000000;
		int second = c.bandanaColor2.argb() | 0xFF000000;
		Pattern pattern = c.bandanaPattern.get();
		BandanaStyle style = c.bandana.get();
		switch (style) {
			case HEADBAND, NINJA -> {
				band(g, -6.6f, -5, main, second, pattern);
				tails(g, -5.8f, style == BandanaStyle.NINJA ? 7 : 3.5f, main, time, moving);
			}
			case PIRATE -> {
				g.box(-4.35f, -8.35f, -4.35f, 4.35f, -5.2f, 4.35f, main, false);
				patternFront(g, -8.2f, -5.4f, second, pattern);
				tails(g, -6, 3, main, time, moving);
			}
			case FACE_MASK -> {
				g.box(-4.35f, -3.6f, -4.4f, 4.35f, 0.2f, -4.05f, main, false);
				g.box(-4.4f, -3.6f, -4.35f, -4.05f, 0.2f, 1.5f, main, false);
				g.box(4.05f, -3.6f, -4.35f, 4.4f, 0.2f, 1.5f, main, false);
				g.tri2(new float[]{-3, 0.2f, -4.4f}, new float[]{3, 0.2f, -4.4f}, new float[]{0, 2.8f, -4.6f}, main, main, main, false);
				patternFront(g, -3.4f, 0, second, pattern);
			}
			default -> {
			}
		}
	}

	/** Bandanas worn on the body (scarf, neckerchief). */
	static void bodyBandana(Geo g, Cosmetics c, float time, float moving) {
		int main = c.bandanaColor.argb() | 0xFF000000;
		int second = c.bandanaColor2.argb() | 0xFF000000;
		switch (c.bandana.get()) {
			case SCARF -> {
				g.box(-4.4f, -0.4f, -2.45f, 4.4f, 1.8f, 2.45f, main, false);
				if (c.bandanaPattern.get() == Pattern.STRIPES) {
					g.box(-4.45f, 0.4f, -2.5f, 4.45f, 0.9f, 2.5f, second, false);
				}
				float sway = (float) Math.sin(time * 2) * 0.3f + moving * 0.8f;
				g.box(1.2f, 1.8f, -2.65f - sway * 0.2f, 3.2f, 8, -2.35f, main, false);
				g.box(1.2f, 7.4f, -2.7f, 3.2f, 8, -2.6f, second, false);
			}
			case NECKERCHIEF -> {
				g.tri2(new float[]{-4.2f, 0.2f, -2.35f}, new float[]{4.2f, 0.2f, -2.35f}, new float[]{0, 5.5f, -2.45f}, main, main, main, false);
				g.cube(0, 0.6f, -2.5f, 1.6f, 1.2f, 0.4f, second, false);
			}
			default -> {
			}
		}
	}

	/** A band all the way round the head between y0 and y1, with the pattern on it. */
	private static void band(Geo g, float y0, float y1, int main, int second, Pattern pattern) {
		g.box(-4.35f, y0, -4.35f, 4.35f, y1, -4.05f, main, false);
		g.box(-4.35f, y0, 4.05f, 4.35f, y1, 4.35f, main, false);
		g.box(-4.35f, y0, -4.35f, -4.05f, y1, 4.35f, main, false);
		g.box(4.05f, y0, -4.35f, 4.35f, y1, 4.35f, main, false);
		patternFront(g, y0, y1, second, pattern);
	}

	/** Dots or stripes on the front of a band from y0 to y1. */
	private static void patternFront(Geo g, float y0, float y1, int second, Pattern pattern) {
		if (pattern == Pattern.DOTS) {
			for (float x = -3.4f; x <= 3.5f; x += 1.7f) {
				g.cube(x, (y0 + y1) / 2, -4.4f, 0.5f, 0.5f, 0.1f, second, false);
			}
		} else if (pattern == Pattern.STRIPES) {
			float mid = (y0 + y1) / 2;
			g.box(-4.36f, mid - 0.2f, -4.42f, 4.36f, mid + 0.2f, -4.36f, second, false);
		}
	}

	/** Knot at the back of the head with two tails that sway (more when moving). */
	private static void tails(Geo g, float y, float length, int color, float time, float moving) {
		g.cube(0, y, 4.6f, 1.8f, 1.6f, 0.8f, color, false);
		float sway = (float) Math.sin(time * 3) * 0.6f + moving * 1.5f;
		g.stick(new float[]{-0.4f, y, 4.8f}, new float[]{-1.4f, y + length, 5.4f + sway}, 1.2f, 0.3f, color, false);
		g.stick(new float[]{0.4f, y, 4.8f}, new float[]{1.6f, y + length * 0.85f, 5.2f + sway * 0.8f}, 1.2f, 0.3f, color, false);
	}

	/** A ring of four bars round the head (crowns). */
	private static void ring(Geo g, float r, float y0, float y1, float t, int color, boolean glow) {
		g.box(-r, y0, -r, r, y1, -r + t, color, glow);
		g.box(-r, y0, r - t, r, y1, r, color, glow);
		g.box(-r, y0, -r, -r + t, y1, r, color, glow);
		g.box(r - t, y0, -r, r, y1, r, color, glow);
	}

}
