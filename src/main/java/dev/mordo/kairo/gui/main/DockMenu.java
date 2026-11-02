package dev.mordo.kairo.gui.main;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * "Dock" main menu: calm and centred. A big clock with the date and a greeting, and the entries as icons in a glass
 * dock at the bottom that magnify under the mouse (with a name bubble over the one you point at).
 */
final class DockMenu {
	private static final float BASE = 60;
	private static final float GAP = 14;
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH);

	private float[] scales = new float[0];
	private float[][] rects = new float[0][];
	private int keyboard = -1;
	private int lastHover = -1;

	void reset() {
		keyboard = -1;
	}

	/** Bottom of the dock area (chips go above the clock block, the footer below the dock). */
	float dockCenterY(float viewH) {
		return viewH - 118;
	}

	void drawBackground(GuiGraphicsExtractor g) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, Colors.argb(0.22f, 0x05070B));
		Shapes.gradientRect(g, 0, 0, w, h * 0.5f, 0, Colors.argb(0.45f, 0x05070B), Colors.argb(0, 0x05070B));
		Shapes.gradientRect(g, 0, h * 0.6f, w, h * 0.4f, 0, Colors.argb(0, 0x05070B), Colors.argb(0.55f, 0x05070B));
	}

	/** Clock, date and greeting; returns the y just under them. */
	float drawClock(GuiGraphicsExtractor g, float viewW, float viewH, float a) {
		float cx = viewW / 2;
		float rise = Animation.motion((1 - a) * 14, 0);
		LocalDateTime now = LocalDateTime.now();
		int hour = now.getHour();
		String part = hour < 5 ? "GOOD NIGHT" : hour < 12 ? "GOOD MORNING" : hour < 18 ? "GOOD AFTERNOON" : "GOOD EVENING";
		String name = Minecraft.getInstance().getUser().getName().toUpperCase(Locale.ROOT);
		float top = viewH * 0.2f + rise;
		Fonts.draw(g, Typeface.MONO_BOLD, part + ", " + name, cx, top, 9.5f, Colors.multiplyAlpha(Theme.accent(), a), Fonts.Align.CENTER, 2.6f, false);
		String time = now.format(TIME);
		// Blinking colon.
		boolean colon = (System.currentTimeMillis() / 1000) % 2 == 0;
		float size = 112;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, time.substring(0, 2), cx - 22, top + 74, size, Theme.text(1, a), Fonts.Align.RIGHT, 0, false);
		// The colon as two dots, so it sits in its own gap whatever the font does.
		int dotColor = Theme.text(colon ? 0.9f : 0.3f, a);
		Shapes.circle(g, cx, top + 56, 7, dotColor);
		Shapes.circle(g, cx, top + 92, 7, dotColor);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, time.substring(3), cx + 22, top + 74, size, Theme.text(1, a), Fonts.Align.LEFT, 0, false);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, now.format(DATE), cx, top + 148, 16, Theme.text(0.7f, a), Fonts.Align.CENTER, 0, false);
		Fonts.draw(g, Typeface.MONO_MEDIUM, ClientInfo.NAME.toUpperCase(Locale.ROOT) + "  ·  " + ClientInfo.MC_VERSION, cx, top + 174, 9,
			Theme.text(0.4f, a), Fonts.Align.CENTER, 2, false);
		return top + 196;
	}

	void drawDock(GuiGraphicsExtractor g, List<MenuRow> rows, float viewW, float viewH, float mx, float my, float a, boolean live) {
		int n = rows.size();
		if (scales.length != n) {
			scales = new float[n];
			rects = new float[n][];
			java.util.Arrays.fill(scales, 1);
		}
		float cy = dockCenterY(viewH);
		float baseTotal = n * BASE + (n - 1) * GAP;
		float baseLeft = (viewW - baseTotal) / 2;
		boolean near = live && my > cy - BASE * 1.6f && my < cy + BASE * 0.9f && mx > baseLeft - 80 && mx < baseLeft + baseTotal + 80;
		// Magnify by distance from the mouse (or around the keyboard pick).
		float focusX = near ? mx : keyboard >= 0 ? baseLeft + keyboard * (BASE + GAP) + BASE / 2 : Float.NaN;
		for (int i = 0; i < n; i++) {
			float center = baseLeft + i * (BASE + GAP) + BASE / 2;
			float targetScale = 1;
			if (!Float.isNaN(focusX)) {
				float d = (center - focusX) / 82;
				targetScale = 1 + 0.55f * (float) Math.exp(-d * d);
			}
			scales[i] += (targetScale - scales[i]) * (Animation.motion(1, 0) == 0 ? 1 : 0.25f);
		}
		float total = GAP * (n - 1);
		for (float s : scales) {
			total += BASE * s;
		}
		float left = (viewW - total) / 2;
		float rise = Animation.motion((1 - a) * 30, 0);

		// The dock itself.
		float dockH = BASE + 24;
		float dockW = baseTotal + 32 + (total - baseTotal);
		Shapes.shadow(g, (viewW - dockW) / 2, cy - dockH / 2 + 14 + rise, dockW, dockH, 26, 26, Colors.argb(0.35f * a, 0));
		Shapes.glassRect(g, (viewW - dockW) / 2, cy - dockH / 2 + rise, dockW, dockH, 26, Theme.glassTint(), 0x33FFFFFF, 1.25f, a);

		int hoveredIndex = -1;
		float x = left;
		for (int i = 0; i < n; i++) {
			MenuRow row = rows.get(i);
			float size = BASE * scales[i];
			float bottom = cy + BASE / 2 + rise;
			float top = bottom - size - (scales[i] - 1) * 14;
			float ix = x;
			rects[i] = new float[]{ix, top, size, size};
			boolean over = live && mx >= ix && mx <= ix + size && my >= top && my <= top + size;
			if (over) {
				hoveredIndex = i;
			}
			boolean pick = over || i == keyboard;
			int accent = row.accentColor();
			float alpha = a * (row.isEnabled() ? 1 : 0.45f);
			int tint = pick ? Colors.withAlpha(Colors.lerp(Theme.glassTint() | 0xFF000000, accent, 0.28f), 0.7f) : Theme.glassTint();
			Shapes.glassRect(g, ix, top, size, size, size * 0.28f, tint, pick ? Colors.withAlpha(accent, 0.8f) : 0x2EFFFFFF, 1.25f, alpha);
			IconRenderer.draw(g, row.icon(), ix + size / 2, top + size / 2, size * 0.42f, Colors.multiplyAlpha(pick ? accent : Theme.text(0.92f), alpha), 1.9f);
			if (pick) {
				// Name bubble above, and a little dot under (like a running app).
				String title = row.title();
				float tw = Fonts.width(Typeface.OUTFIT_SEMIBOLD, title, 12.5f) + 22;
				float by = top - 36;
				Shapes.glassRect(g, ix + size / 2 - tw / 2, by, tw, 26, 13, Theme.glassTint(), 0x33FFFFFF, 1, alpha);
				Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, title, ix + size / 2, by + 13, 12.5f, Theme.text(1, alpha), Fonts.Align.CENTER, 0, false);
				Shapes.circle(g, ix + size / 2, bottom + 8, 2.2f, Colors.multiplyAlpha(accent, alpha));
			}
			x += size + GAP;
		}
		if (hoveredIndex != lastHover && hoveredIndex >= 0) {
			UiSounds.hover();
		}
		lastHover = hoveredIndex;
	}

	/** Returns the row clicked, or -1. */
	int click(float x, float y) {
		for (int i = 0; i < rects.length; i++) {
			float[] r = rects[i];
			if (r != null && x >= r[0] && x <= r[0] + r[2] && y >= r[1] && y <= r[1] + r[3]) {
				return i;
			}
		}
		return -1;
	}

	/** ←/→ pick, Enter opens. Returns the row to open, -1 handled, -2 not handled. */
	int key(KeyEvent event, int count) {
		if (event.isLeft() || event.isRight()) {
			keyboard = keyboard < 0 ? 0 : Math.floorMod(keyboard + (event.isRight() ? 1 : -1), count);
			UiSounds.hover();
			return -1;
		}
		if (event.isConfirmation() && keyboard >= 0) {
			return keyboard;
		}
		return -2;
	}
}
