package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.VoidPresets;
import dev.mordo.kairo.core.VoidSettings.Atmosphere;
import dev.mordo.kairo.core.VoidSettings.CrystalStyle;
import dev.mordo.kairo.core.VoidSettings.HudStyle;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Atmospheres;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Little animated pictures for the Visuals cards: a scene for each atmosphere (its sky, hills and weather), a crystal
 * for each crystal style, and a preset's colours over its atmosphere. All drawn with plain shapes, every frame.
 */
public final class VisualPreviews {
	private VisualPreviews() {
	}

	private static int rgb(float[] c, float alpha) {
		return Colors.rgba(Math.round(c[0] * 255), Math.round(c[1] * 255), Math.round(c[2] * 255), alpha);
	}

	private static float hash(float n) {
		double s = Math.sin(n * 12.9898) * 43758.5453;
		return (float) (s - Math.floor(s));
	}

	private static double time() {
		return System.nanoTime() / 1e9;
	}

	// ---- atmospheres ----

	public static void atmosphere(GuiGraphicsExtractor g, Atmosphere a, float x, float y, float w, float h, float alpha) {
		double t = time();
		Atmospheres.Look look = Atmospheres.look(a);
		int high;
		int low;
		int fog;
		if (a == Atmosphere.NONE) {
			high = Colors.argb(alpha, 0x3E6FC4);
			low = Colors.argb(alpha, 0xA8C9F2);
			fog = Colors.argb(alpha, 0x6E8F6A);
		} else {
			high = rgb(look.skyHigh(), alpha);
			low = rgb(look.skyLow(), alpha);
			fog = rgb(look.fog(), alpha);
		}
		int corner = Colors.lerp(fog, Colors.argb(alpha, 0x000000), a == Atmosphere.BLIZZARD ? 0.05f : 0.7f);
		Shapes.gradientRect(g, x, y, w, h, dev.mordo.kairo.gui.widgets.VisualCard.ART_RADIUS, high, corner);
		dev.mordo.kairo.gui.widgets.VisualCard.clip(g, x, y, w, h, dev.mordo.kairo.gui.widgets.VisualCard.ART_RADIUS, () -> {
			Shapes.gradientRect(g, x, y, w, h, 0, high, low);
			// Sky objects first.
			switch (a) {
				case GALAXY -> {
					stars(g, x, y, w, h * 0.7f, 26, alpha, t);
					Shapes.shadow(g, x + w * 0.3f, y + h * 0.15f, w * 0.35f, h * 0.3f, h * 0.15f, h * 0.18f, Colors.argb(0.35f * alpha, 0xC04CFF));
					Shapes.shadow(g, x + w * 0.55f, y + h * 0.25f, w * 0.3f, h * 0.2f, h * 0.1f, h * 0.15f, Colors.argb(0.3f * alpha, 0x3FA9FF));
					float px = x + w * 0.78f;
					float py = y + h * 0.28f;
					Shapes.circle(g, px, py, h * 0.11f, Colors.argb(alpha, 0xB59BD6));
					g.pose().pushMatrix();
					g.pose().translate(px, py);
					g.pose().rotate(-0.35f);
					g.pose().scale(1, 0.28f);
					Shapes.ring(g, 0, 0, h * 0.2f, 2.4f, Colors.argb(0.8f * alpha, 0xE6D7B8));
					g.pose().popMatrix();
				}
				case BLOOD_MOON -> {
					stars(g, x, y, w, h * 0.6f, 12, alpha * 0.6f, t);
					float mx = x + w * 0.7f;
					float my = y + h * 0.3f;
					Shapes.shadow(g, mx - h * 0.2f, my - h * 0.2f, h * 0.4f, h * 0.4f, h * 0.2f, h * 0.18f, Colors.argb(0.6f * alpha, 0xFF2010));
					Shapes.circle(g, mx, my, h * 0.17f, Colors.argb(alpha, 0xD8261C));
					Shapes.circle(g, mx - h * 0.05f, my + h * 0.03f, h * 0.05f, Colors.argb(0.5f * alpha, 0x7A0A0A));
					Shapes.circle(g, mx + h * 0.06f, my - h * 0.05f, h * 0.035f, Colors.argb(0.5f * alpha, 0x7A0A0A));
				}
				case SYNTHWAVE -> {
					float sx = x + w * 0.5f;
					float sy = y + h * 0.58f;
					float r = h * 0.3f;
					Shapes.shadow(g, sx - r, sy - r, r * 2, r * 2, r, r * 0.6f, Colors.argb(0.5f * alpha, 0xFF3D9A));
					Shapes.gradientRect(g, sx - r, sy - r, r * 2, r * 2, r, Colors.argb(alpha, 0xFFE45C), Colors.argb(alpha, 0xFF2E8A));
					for (int i = 0; i < 4; i++) {
						float by = sy - r * 0.05f + i * r * 0.22f;
						Shapes.rect(g, sx - r, by, r * 2, 1.2f + i * 1.1f, 0, Colors.argb(alpha, 0x2A0B4A));
					}
				}
				case STORM -> {
					for (int i = 0; i < 5; i++) {
						float cx = x + w * (0.1f + 0.22f * i) + (float) Math.sin(t * 0.3 + i) * 6;
						Shapes.shadow(g, cx - w * 0.15f, y + h * 0.05f, w * 0.3f, h * 0.22f, h * 0.11f, 10, Colors.argb(0.6f * alpha, 0x1C1F26));
					}
					float cycle = (float) (t % 3.2);
					if (cycle < 0.18f) {
						float bx = x + w * 0.62f;
						float k = 1 - cycle / 0.18f;
						Shapes.rect(g, x, y, w, h, 0, Colors.argb(0.35f * k * alpha, 0xDDE6FF));
						Shapes.line(g, bx, y + h * 0.2f, bx - 8, y + h * 0.45f, 2, Colors.argb(k * alpha, 0xFFFFFF));
						Shapes.line(g, bx - 8, y + h * 0.45f, bx + 4, y + h * 0.55f, 2, Colors.argb(k * alpha, 0xFFFFFF));
						Shapes.line(g, bx + 4, y + h * 0.55f, bx - 6, y + h * 0.8f, 2, Colors.argb(k * alpha, 0xFFFFFF));
					}
				}
				case INFERNO -> Shapes.shadow(g, x - w * 0.1f, y + h * 0.5f, w * 1.2f, h * 0.4f, h * 0.2f, h * 0.25f, Colors.argb(0.5f * alpha, 0xFF7A1A));
				case NONE -> Shapes.circle(g, x + w * 0.8f, y + h * 0.25f, h * 0.09f, Colors.argb(alpha, 0xFFF6CF));
				default -> {
				}
			}
			// Hills.
			int far = Colors.lerp(fog, Colors.argb(alpha, 0x000000), a == Atmosphere.BLIZZARD ? 0.15f : 0.45f);
			int near = Colors.lerp(fog, Colors.argb(alpha, 0x000000), a == Atmosphere.BLIZZARD ? 0.05f : 0.7f);
			Shapes.circle(g, x + w * 0.22f, y + h * 1.18f, h * 0.55f, far);
			Shapes.circle(g, x + w * 0.62f, y + h * 1.3f, h * 0.6f, far);
			Shapes.circle(g, x + w * 0.95f, y + h * 1.22f, h * 0.5f, near);
			Shapes.circle(g, x + w * 0.45f, y + h * 1.45f, h * 0.65f, near);
			if (a == Atmosphere.BLIZZARD) {
				Shapes.circle(g, x + w * 0.22f, y + h * 1.18f, h * 0.55f, 0, Colors.argb(0.9f * alpha, 0xFFFFFF), 3);
				Shapes.circle(g, x + w * 0.45f, y + h * 1.45f, h * 0.65f, 0, Colors.argb(0.9f * alpha, 0xFFFFFF), 3);
			}
			if (a == Atmosphere.SYNTHWAVE) {
				// A neon grid running towards the horizon.
				float gy = y + h * 0.72f;
				Shapes.rect(g, x, gy, w, h - (gy - y), 0, Colors.argb(alpha, 0x1A0533));
				for (int i = -6; i <= 6; i++) {
					Shapes.line(g, x + w * 0.5f + i * w * 0.04f, gy, x + w * 0.5f + i * w * 0.22f, y + h, 1, Colors.argb(0.8f * alpha, 0xFF3DC8));
				}
				float scroll = (float) (t * 0.6 % 1.0);
				for (int i = 0; i < 5; i++) {
					float k = (i + scroll) / 5f;
					float ly = gy + (y + h - gy) * k * k;
					Shapes.rect(g, x, ly, w, 1, 0, Colors.argb(0.8f * alpha * k, 0xFF3DC8));
				}
			}
			if (a == Atmosphere.ABYSS) {
				float gy = y + h * 0.75f;
				for (int i = 0; i < 9; i++) {
					float ox = (float) ((t * 14 + i * 37) % (w + 30)) - 15;
					Shapes.arc(g, x + ox, gy + (i % 3) * 7 + 6, 10 + (i % 4) * 4, 1.4f, 3.4f, 1.6f, Colors.argb(0.75f * alpha, 0x6FE6FF));
				}
			}
			// Weather.
			switch (a) {
				case BLIZZARD -> drift(g, x, y, w, h, 60, alpha, t, 0xFFFFFF, 1.5f, 0.55f, 0.35f, 1);
				case INFERNO -> {
					drift(g, x, y, w, h, 26, alpha, t, 0x8C8C8C, 1.2f, 0.08f, 0.08f, 1);
					drift(g, x, y, w, h, 18, alpha, t, 0xFFB03A, 1.4f, -0.25f, 0.03f, -1);
				}
				case SAKURA -> drift(g, x, y, w, h, 20, alpha, t, 0xFFB7D9, 2.2f, 0.12f, 0.15f, 1);
				case TOXIC -> drift(g, x, y, w, h, 24, alpha, t, 0x9CFF4A, 1.4f, -0.08f, 0.04f, -1);
				case GALAXY -> drift(g, x, y, w, h, 10, alpha, t, 0xD9B8FF, 1.1f, -0.04f, 0.02f, -1);
				case ABYSS -> drift(g, x, y, w, h, 22, alpha, t, 0xBFEFFF, 0.9f, -0.05f, 0.02f, -1);
				case STORM -> {
					for (int i = 0; i < 34; i++) {
						float k = (float) ((t * 1.6 + hash(i)) % 1.0);
						float rx = x + hash(i * 3.3f) * (w + 20) - 10 - k * 8;
						float ry = y + k * h;
						Shapes.line(g, rx, ry, rx - 3, ry + 8, 1, Colors.argb(0.5f * alpha, 0xC8D2E6));
					}
				}
				case BLOOD_MOON -> drift(g, x, y, w, h, 14, alpha, t, 0xFF3A2A, 1.3f, -0.05f, 0.05f, -1);
				default -> {
				}
			}
		});
	}

	private static void stars(GuiGraphicsExtractor g, float x, float y, float w, float h, int count, float alpha, double t) {
		for (int i = 0; i < count; i++) {
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (1 + hash(i) * 3) + i);
			Shapes.circle(g, x + hash(i * 1.7f) * w, y + hash(i * 5.1f) * h, 0.6f + hash(i * 2.9f) * 0.9f, Colors.argb(tw * alpha, 0xFFFFFF));
		}
	}

	/** Particles drifting down ({@code dir} 1) or up (-1), sideways by {@code wind}, wrapping round the picture. */
	private static void drift(GuiGraphicsExtractor g, float x, float y, float w, float h, int count, float alpha, double t, int rgb, float size,
							  float wind, float speed, int dir) {
		for (int i = 0; i < count; i++) {
			float s = speed * (0.6f + hash(i * 2.1f) * 0.8f);
			float k = (float) ((t * s + hash(i)) % 1.0);
			float py = dir > 0 ? y + k * (h + 6) - 3 : y + h + 3 - k * (h + 6);
			float px = x + ((hash(i * 4.7f) * w + k * wind * w * 2 + (float) Math.sin(t + i) * 3) % w + w) % w;
			Shapes.circle(g, px, py, size * (0.6f + hash(i * 8.3f) * 0.8f), Colors.argb(alpha * (0.6f + 0.4f * hash(i * 6.1f)), rgb));
		}
	}

	// ---- crystals ----

	public static void crystal(GuiGraphicsExtractor g, CrystalStyle style, float x, float y, float w, float h, float alpha) {
		double t = time();
		int accent = Theme.accent() | 0xFF000000;
		Shapes.gradientRect(g, x, y, w, h, dev.mordo.kairo.gui.widgets.VisualCard.ART_RADIUS, Colors.argb(alpha, 0x0B0B12), Colors.argb(alpha, 0x17131F));
		dev.mordo.kairo.gui.widgets.VisualCard.clip(g, x, y, w, h, dev.mordo.kairo.gui.widgets.VisualCard.ART_RADIUS, () -> {
			Shapes.gradientRect(g, x, y, w, h, 0, Colors.argb(alpha, 0x0B0B12), Colors.argb(alpha, 0x17131F));
			float cx = x + w / 2;
			float cy = y + h * 0.48f + (float) Math.sin(t * 1.6) * 3;
			float s = h * 0.22f;
			// Base block.
			Shapes.rect(g, cx - s * 0.9f, y + h * 0.82f, s * 1.8f, h * 0.12f, 2, Colors.argb(alpha, 0x2A2433));
			int body;
			switch (style) {
				case VANILLA -> {
					body = Colors.argb(0.85f * alpha, 0xE8B6F2);
					Shapes.shadow(g, cx - s, cy - s, s * 2, s * 2, s, s * 0.5f, Colors.argb(0.25f * alpha, 0xC77BFF));
				}
				case PRISM -> {
					body = Colors.multiplyAlpha(Colors.lerp(0xFFFFFFFF, accent, 0.45f), alpha);
					Shapes.shadow(g, cx - s * 1.6f, cy - s * 1.6f, s * 3.2f, s * 3.2f, s * 1.6f, s, Colors.withAlpha(accent, 0.45f * alpha));
					tiltedRing(g, cx, cy, s * 1.6f, 0.35f, (float) t * 0.8f, Colors.withAlpha(accent, 0.9f * alpha));
					tiltedRing(g, cx, cy, s * 1.85f, -0.4f, (float) -t * 0.6f, Colors.withAlpha(Colors.lerp(accent, 0xFFFFFFFF, 0.5f), 0.7f * alpha));
				}
				case VOID -> {
					body = Colors.argb(0.95f * alpha, 0x0B0610);
					Shapes.shadow(g, cx - s * 1.8f, cy - s * 1.8f, s * 3.6f, s * 3.6f, s * 1.8f, s * 0.8f, Colors.withAlpha(accent, 0.35f * alpha));
					Shapes.ring(g, cx, cy, s * 1.35f, 3, Colors.withAlpha(accent, alpha));
					Shapes.circle(g, cx, cy, s * 1.25f, Colors.argb(0.8f * alpha, 0x000000));
				}
				case PLASMA -> {
					float pulse = 0.85f + 0.15f * (float) Math.sin(t * 5);
					body = Colors.multiplyAlpha(Colors.lerp(accent, 0xFFFFFFFF, 0.6f), alpha);
					Shapes.shadow(g, cx - s * 2 * pulse, cy - s * 2 * pulse, s * 4 * pulse, s * 4 * pulse, s * 2 * pulse, s, Colors.withAlpha(accent, 0.55f * alpha));
					long seed = (long) (t * 12);
					for (int i = 0; i < 4; i++) {
						float ang = hash(seed + i * 7.1f) * 6.28f;
						float px = cx;
						float py = cy;
						for (int k = 1; k <= 4; k++) {
							float nx = cx + (float) Math.cos(ang) * s * 0.55f * k + (hash(seed + i * 3 + k) - 0.5f) * 8;
							float ny = cy + (float) Math.sin(ang) * s * 0.55f * k + (hash(seed + i * 5 + k) - 0.5f) * 8;
							Shapes.line(g, px, py, nx, ny, 1.4f, Colors.withAlpha(Colors.lerp(accent, 0xFFFFFFFF, 0.6f), alpha * (1 - k / 5f)));
							px = nx;
							py = ny;
						}
					}
				}
				case HOLO -> {
					boolean off = Math.sin(t * 7.1) * Math.sin(t * 3.3 + 1.2) > 0.85;
					body = Colors.withAlpha(Colors.lerp(accent, 0xFF8FFBFF, 0.5f), (off ? 0.15f : 0.5f) * alpha);
					Shapes.shadow(g, cx - s * 1.4f, cy - s * 1.4f, s * 2.8f, s * 2.8f, s * 1.4f, s * 0.6f, Colors.withAlpha(accent, 0.3f * alpha));
					float k = (float) (t * 0.6 % 1.0);
					Shapes.rect(g, cx - s * 1.3f, cy - s * 1.3f + k * s * 2.6f, s * 2.6f, 1.5f, 0, Colors.withAlpha(0xFF8FFBFF, 0.9f * alpha));
				}
				default -> body = 0;
			}
			// The crystal: a turning diamond with an inner one.
			g.pose().pushMatrix();
			g.pose().translate(cx, cy);
			g.pose().rotate((float) Math.PI / 4 + (float) Math.sin(t * 0.9) * 0.25f);
			Shapes.rect(g, -s * 0.7f, -s * 0.7f, s * 1.4f, s * 1.4f, 2, body, Colors.multiplyAlpha(0xA0FFFFFF, alpha), 1);
			Shapes.rect(g, -s * 0.38f, -s * 0.38f, s * 0.76f, s * 0.76f, 1.5f, Colors.multiplyAlpha(0x66FFFFFF, alpha));
			g.pose().popMatrix();
		});
	}

	private static void tiltedRing(GuiGraphicsExtractor g, float cx, float cy, float r, float tilt, float spin, int color) {
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(tilt);
		g.pose().scale(1, 0.3f);
		Shapes.ring(g, 0, 0, r, 2.4f, Colors.multiplyAlpha(color, 0.55f));
		float px = (float) Math.cos(spin) * r;
		float py = (float) Math.sin(spin) * r;
		Shapes.circle(g, px, py, 3.5f, color);
		g.pose().popMatrix();
	}

	// ---- presets ----

	public static void preset(GuiGraphicsExtractor g, VoidPresets.Preset p, float x, float y, float w, float h, float alpha) {
		atmosphere(g, p.atmosphere(), x, y, w, h, alpha);
		double t = time();
		int accent = p.accent() | 0xFF000000;
		float r = p.radius();
		// A little HUD in the preset's style: two keys and a box.
		float kx = x + 10;
		float ky = y + 10;
		float k = 16;
		for (int i = 0; i < 3; i++) {
			float bx = kx + i * (k + 3);
			boolean pressed = ((int) (t * 1.5) % 3) == i;
			hudBox(g, bx, ky + k + 3, k, k, r * 0.6f, p.hud(), accent, pressed, alpha);
		}
		hudBox(g, kx + k + 3, ky, k, k, r * 0.6f, p.hud(), accent, false, alpha);
		hudBox(g, x + w - 66, y + 10, 56, 16, r * 0.6f, p.hud(), accent, false, alpha);
		Fonts.draw(g, Typeface.MONO_BOLD, "120 FPS", x + w - 38, y + 18, 7.5f, Colors.multiplyAlpha(accent, alpha), Fonts.Align.CENTER, 0.6f, false);
		// The accent, big.
		Shapes.shadow(g, x + w - 30, y + h - 30, 20, 20, 10, 8, Colors.withAlpha(accent, 0.6f * alpha));
		Shapes.circle(g, x + w - 20, y + h - 20, 9, Colors.multiplyAlpha(accent, alpha), Colors.multiplyAlpha(0xCCFFFFFF, alpha), 1.5f);
	}

	private static void hudBox(GuiGraphicsExtractor g, float x, float y, float w, float h, float r, HudStyle style, int accent, boolean pressed,
							   float alpha) {
		if (style != HudStyle.CLASSIC) {
			Shapes.shadow(g, x, y, w, h, r, 5, Colors.withAlpha(accent, (style == HudStyle.NEON ? 0.55f : 0.35f) * alpha));
		}
		int fill = pressed ? Colors.withAlpha(accent, 0.85f * alpha) : Colors.argb(0.55f * alpha, 0x000000);
		int stroke = style == HudStyle.CLASSIC ? 0 : Colors.withAlpha(accent, (style == HudStyle.NEON ? 0.95f : 0.4f) * alpha);
		Shapes.rect(g, x, y, w, h, r, fill, stroke, style == HudStyle.CLASSIC ? 0 : 1);
	}
}
