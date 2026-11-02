package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.HudStyle;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Kairo Void's HUD looks: boxes that glow in your colour (Glow), bright neon edges (Neon), or a light running round
 * each box (Pulse). Kairo and the Classic style draw plain boxes.
 */
public final class HudFx {
	private HudFx() {
	}

	public static HudStyle style() {
		return dev.mordo.kairo.render.Visuals.on() ? VoidSettings.INSTANCE.hudStyle.get() : HudStyle.CLASSIC;
	}

	public static boolean styled() {
		return style() != HudStyle.CLASSIC;
	}

	private static double time() {
		return System.nanoTime() / 1e9;
	}

	/** Everything behind a box: its glow. {@code seed} keeps neighbouring boxes from pulsing in step. */
	public static void under(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float seed, float boost) {
		HudStyle style = style();
		if (style == HudStyle.CLASSIC) {
			return;
		}
		int accent = Theme.accent();
		float breathe = 0.75f + 0.25f * (float) Math.sin(time() * 1.8 + seed * 2.1);
		float strength = switch (style) {
			case GLOW -> 0.32f;
			case NEON -> 0.5f;
			case PULSE -> 0.22f;
			case CLASSIC -> 0;
		};
		float soft = Math.max(4, Math.min(w, h) * 0.35f);
		Shapes.shadow(g, x, y, w, h, radius, soft, Colors.withAlpha(accent, Math.min(1, strength * breathe + boost * 0.5f)));
	}

	/** Everything on top of a box: its edge and, for Pulse, the running light. */
	public static void over(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float seed, float boost) {
		HudStyle style = style();
		if (style == HudStyle.CLASSIC) {
			return;
		}
		int accent = Theme.accent();
		float edge = switch (style) {
			case GLOW -> 0.35f;
			case NEON -> 0.95f;
			case PULSE -> 0.25f;
			case CLASSIC -> 0;
		};
		Shapes.rect(g, x, y, w, h, radius, 0, Colors.withAlpha(accent, Math.min(1, edge + boost * 0.5f)), style == HudStyle.NEON ? 1.2f : 0.8f);
		if (style == HudStyle.PULSE) {
			// A bright point running round the box.
			float perimeter = 2 * (w + h);
			float d = (float) (((time() * 60 + seed * 97) % perimeter + perimeter) % perimeter);
			float px;
			float py;
			if (d < w) {
				px = x + d;
				py = y;
			} else if (d < w + h) {
				px = x + w;
				py = y + (d - w);
			} else if (d < 2 * w + h) {
				px = x + w - (d - w - h);
				py = y + h;
			} else {
				px = x;
				py = y + h - (d - 2 * w - h);
			}
			Shapes.shadow(g, px - 1.5f, py - 1.5f, 3, 3, 1.5f, 5, Colors.withAlpha(accent, 0.9f));
			Shapes.circle(g, px, py, 1.1f, Colors.withAlpha(Colors.lerp(accent | 0xFF000000, 0xFFFFFFFF, 0.6f), 1));
		}
	}
}
