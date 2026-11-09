package dev.mordo.kairo.render;

import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.keybinds.InputTracker;
import dev.mordo.kairo.gui.RefSpace;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Kairo Void's start-up intro: over a black screen "Kairo" rises into place, then "Void" pops in next to it with a
 * ring of light, and the whole thing fades away to show whatever menu opened first. Once per launch (never when you
 * leave a world), skipped with a click. Reduce motion: fades only. Fewer flashes: no ring, a soft glow.
 */
public final class VoidIntro {
	private static final boolean GAME_TEST = System.getProperty("fabric.client.gametest") != null;
	private static final float VOID_AT = 1.05f;
	private static final float EXIT_AT = 2.15f;
	private static final float EXIT_LENGTH = 0.55f;

	private static long start;
	private static boolean done;
	private static float skippedAt = -1;
	/** The visual tour plays it on purpose (it never starts by itself in game tests). */
	public static boolean forceForTest;

	private VoidIntro() {
	}

	/** Restarts it (the visual tour uses this for screenshots). */
	public static void replay() {
		start = 0;
		done = false;
		skippedAt = -1;
		forceForTest = true;
	}

	/** True while it covers the screen. */
	public static boolean playing() {
		return start != 0 && !done;
	}

	/** Called at the end of every screen's frame, so it sits on top of any menu. */
	public static void draw(GuiGraphicsExtractor g) {
		if (done) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (start == 0) {
			if (!Edition.isVoid() || !VoidSettings.INSTANCE.intro.isOn() || mc.level != null || (GAME_TEST && !forceForTest)) {
				done = true;
				return;
			}
			if (mc.gui.overlay() != null) {
				return; // still loading: start with the first real menu frame
			}
			start = System.nanoTime();
		}
		float t = (System.nanoTime() - start) / 1e9f;
		if (skippedAt < 0 && t > 0.15f && (InputTracker.isMouseDown(0) || InputTracker.isMouseDown(1))) {
			skippedAt = Math.min(t, EXIT_AT);
		}
		float exitStart = skippedAt >= 0 ? skippedAt : EXIT_AT;
		float exitLength = skippedAt >= 0 ? 0.3f : EXIT_LENGTH;
		float out = Math.clamp((t - exitStart) / exitLength, 0f, 1f);
		if (out >= 1) {
			done = true;
			return;
		}
		boolean calm = WorldFx.calm();
		boolean still = Animation.reduceMotion();
		float fade = 1 - smooth(out);

		RefSpace.push(g);
		try {
			float w = g.guiWidth() / RefSpace.unit(g.guiWidth(), g.guiHeight());
			float h = RefSpace.REF_HEIGHT;
			float cx = w / 2;
			float cy = h / 2;
			int accent = Theme.accent() | 0xFF000000;
			g.fill(0, 0, (int) Math.ceil(w), (int) Math.ceil(h), Colors.argb(fade, 0x000000));
			// A faint glow of your colour slowly gathering behind the words.
			float gather = smooth(Math.clamp(t / 1.6f, 0f, 1f));
			Shapes.shadow(g, cx - 260, cy - 130, 520, 260, 130, 160, Colors.withAlpha(accent, 0.10f * gather * fade));

			float size = 64;
			float gap = 18;
			float kairoW = Fonts.width(Typeface.OUTFIT_BOLD, "Kairo", size);
			float voidW = Fonts.width(Typeface.OUTFIT_BOLD, "Void", size);
			float total = kairoW + gap + voidW;
			float finalKairoX = cx - total / 2;

			// "Kairo": rises and tightens into place.
			float k = smooth(Math.clamp((t - 0.2f) / 0.65f, 0f, 1f));
			// Kairo makes room first, so it is in place by the time Void pops in.
			float make = smooth(Math.clamp((t - VOID_AT + 0.34f) / 0.3f, 0f, 1f));
			float kairoX = still ? finalKairoX : Animation.lerp(cx - kairoW / 2, finalKairoX, make);
			float rise = still ? 0 : (1 - k) * 22;
			float tracking = still ? 0 : (1 - k) * 0.35f * size * 0.1f;
			float exitGrow = still ? 1 : 1 + out * 0.06f;
			drawWord(g, "Kairo", kairoX, cy + rise, size * exitGrow, Colors.withAlpha(Theme.TEXT, k * fade), tracking);

			// "Void": pops in with a little overshoot, in your colour.
			float v = Math.clamp((t - VOID_AT) / 0.38f, 0f, 1f);
			if (v > 0) {
				float pop = still ? 1 : overshoot(v);
				float voidX = finalKairoX + kairoW + gap;
				float vcx = voidX + voidW / 2;
				float alpha = Math.min(1, v * 3) * fade;
				float glow = calm ? 0.25f : 0.35f + 0.45f * (float) Math.exp(-(t - VOID_AT) * 4);
				Shapes.shadow(g, vcx - voidW * 0.8f, cy - size * 0.75f, voidW * 1.6f, size * 1.5f, size * 0.75f, 40,
					Colors.withAlpha(accent, glow * alpha));
				g.pose().pushMatrix();
				g.pose().translate(vcx, cy);
				g.pose().scale(pop * exitGrow, pop * exitGrow);
				drawWord(g, "Void", -voidW / 2, 0, size, Colors.withAlpha(accent, alpha), 0);
				g.pose().popMatrix();
				// A ring racing out from it (not with Fewer flashes or Reduce motion).
				float r = (t - VOID_AT) / 0.7f;
				if (!calm && !still && r < 1) {
					float radius = 30 + r * 260;
					Shapes.ring(g, vcx, cy, radius, 2.5f * (1 - r) + 0.5f, Colors.withAlpha(accent, (1 - r) * (1 - r) * 0.8f * fade));
				}
			}

			// A thin line of light drawn under the name.
			float line = smooth(Math.clamp((t - 0.55f) / 0.8f, 0f, 1f));
			if (line > 0) {
				float lw = total * line;
				float ly = cy + size * 0.62f;
				Shapes.gradientRectH(g, cx - lw / 2, ly, lw / 2, 1.5f, 0, Colors.withAlpha(accent, 0), Colors.withAlpha(accent, 0.9f * fade));
				Shapes.gradientRectH(g, cx, ly, lw / 2, 1.5f, 0, Colors.withAlpha(accent, 0.9f * fade), Colors.withAlpha(accent, 0));
			}
			if (t > 0.6f && skippedAt < 0) {
				Fonts.draw(g, Typeface.MONO_BOLD, "CLICK TO SKIP", cx, h - 40, 8, Theme.text(0.25f * fade * Math.min(1, (t - 0.6f) * 2)),
					Fonts.Align.CENTER, 2f, false);
			}
		} finally {
			RefSpace.pop(g);
		}
	}

	private static void drawWord(GuiGraphicsExtractor g, String word, float x, float cy, float size, int color, float tracking) {
		Fonts.draw(g, Typeface.OUTFIT_BOLD, word, x, cy, size, color, Fonts.Align.LEFT, tracking, false);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	/** 0 to 1 with a small bounce past the end (an "out back" curve). */
	private static float overshoot(float t) {
		float c = 1.9f;
		float u = t - 1;
		return 1 + (c + 1) * u * u * u + c * u * u;
	}
}
