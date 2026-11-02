package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.ColorSetting.Mode;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.List;

/**
 * Colour picker: Static / Chroma / Gradient tabs, an HSV square, hue bar, optional alpha bar and a hex field. In
 * Gradient mode the swatches above the square are the gradient's colours (click one to edit it, + adds one, - removes
 * the selected one); Chroma and Gradient also get a speed bar. Edits apply live.
 */
final class ColorPicker extends Popup {
	private static final float PAD = 14;
	private static final float TABS_H = 26;
	private static final float SV_H = 120;
	private static final float BAR_H = 12;
	private static final float STOPS_H = 24;
	private static final int[] HUE_STOPS = {0xFFFF0000, 0xFFFFFF00, 0xFF00FF00, 0xFF00FFFF, 0xFF0000FF, 0xFFFF00FF, 0xFFFF0000};

	private enum Part {
		NONE, SV, HUE, ALPHA, SPEED
	}

	private final Binding<Integer> value;
	private final boolean hasAlpha;
	private final ColorSetting meta;
	private final TextField hex;
	private float hue;
	private float sat;
	private float val;
	private float alpha;
	private Part dragging = Part.NONE;
	/** The gradient colour being edited (Gradient mode), else -1 for the main colour. */
	private int stop = -1;

	ColorPicker(Binding<Integer> value, boolean hasAlpha, ColorSetting meta) {
		this.value = value;
		this.hasAlpha = hasAlpha;
		this.meta = meta;
		this.w = 256;
		this.hex = new TextField("#RRGGBB", null).mono(11).maxLength(9)
			.allow(c -> c == '#' || Character.digit(c, 16) >= 0)
			.onChange(this::applyHex);
		if (meta.mode() == Mode.GRADIENT) {
			stop = 0;
		}
		loadEdited();
		layoutHeight();
	}

	private int edited() {
		if (stop >= 0) {
			List<Integer> stops = meta.stops();
			if (stop < stops.size()) {
				return stops.get(stop);
			}
		}
		return value.get();
	}

	private void loadEdited() {
		int argb = edited();
		float[] hsv = Colors.toHsv(argb);
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
		alpha = (argb >>> 24) / 255f;
		hex.setText(ColorSetting.toHex(argb, hasAlpha && stop < 0));
	}

	private void layoutHeight() {
		Mode mode = meta.mode();
		float hh = PAD + TABS_H + 10;
		if (mode == Mode.GRADIENT) {
			hh += STOPS_H + 10;
		}
		hh += SV_H + 12 + BAR_H;
		if (hasAlpha && stop < 0) {
			hh += 10 + BAR_H;
		}
		if (mode != Mode.STATIC) {
			hh += 22 + BAR_H;
		}
		hh += 12 + 28 + PAD;
		this.h = hh;
	}

	@Override
	protected boolean ownsFocus(Widget widget) {
		return widget == hex;
	}

	private float innerW() {
		return w - PAD * 2;
	}

	private float tabsY() {
		return y + PAD;
	}

	private float stopsY() {
		return tabsY() + TABS_H + 10;
	}

	private float svY() {
		return meta.mode() == Mode.GRADIENT ? stopsY() + STOPS_H + 10 : tabsY() + TABS_H + 10;
	}

	private float hueY() {
		return svY() + SV_H + 12;
	}

	private boolean alphaShown() {
		return hasAlpha && stop < 0;
	}

	private float alphaY() {
		return hueY() + BAR_H + 10;
	}

	private float speedY() {
		return (alphaShown() ? alphaY() + BAR_H : hueY() + BAR_H) + 22;
	}

	private float hexY() {
		float below = meta.mode() != Mode.STATIC ? speedY() + BAR_H : (alphaShown() ? alphaY() + BAR_H : hueY() + BAR_H);
		return below + 12;
	}

	private float stopSize() {
		return STOPS_H;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float a = open.value();
		float lift = Animation.motion((1 - a) * -6, 0);
		g.pose().pushMatrix();
		g.pose().translate(0, lift);
		Shapes.shadow(g, x, y + 8, w, h, 14, 20, Colors.argb(0.5f * a, 0));
		Shapes.rect(g, x, y, w, h, 14, Colors.multiplyAlpha(Theme.POPUP, a), Colors.argb(0.14f * a, 0xFFFFFF), 1);

		float sx = x + PAD;
		float sw = innerW();
		Mode mode = meta.mode();

		// Mode tabs.
		float tw = sw / 3;
		for (Mode m : Mode.values()) {
			float tx = sx + m.ordinal() * tw;
			boolean on = m == mode;
			Shapes.rect(g, tx + 1, tabsY(), tw - 2, TABS_H, 8, Colors.multiplyAlpha(on ? Colors.withAlpha(Theme.accent(), 0.85f) : 0x14FFFFFF, a));
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, m.label, tx + tw / 2, tabsY() + TABS_H / 2, 11, on ? Colors.multiplyAlpha(Theme.DARK_TEXT, a)
				: Theme.text(0.8f, a), Fonts.Align.CENTER, 0, false);
		}

		// Gradient colours.
		if (mode == Mode.GRADIENT) {
			List<Integer> stops = meta.stops();
			float s = stopSize();
			for (int i = 0; i < stops.size(); i++) {
				float bx = sx + i * (s + 6);
				ColorButton.drawSwatch(g, bx, stopsY(), s, s, 6, stops.get(i), a);
				if (i == stop) {
					Shapes.outline(g, bx - 2, stopsY() - 2, s + 4, s + 4, 8, Colors.multiplyAlpha(0xFFFFFFFF, a), 2);
				}
			}
			float bx = sx + stops.size() * (s + 6);
			if (stops.size() < ColorSetting.MAX_STOPS) {
				smallButton(g, bx, stopsY(), "+", a);
				bx += s + 6;
			}
			if (stops.size() > 2) {
				smallButton(g, bx, stopsY(), "-", a);
			}
		}

		float sy = svY();
		int pure = Colors.hsv(hue, 1, 1);
		// Saturation left->right, value top->bottom.
		Shapes.gradientRectH(g, sx, sy, sw, SV_H, 8, Colors.argb(a, 0xFFFFFF), Colors.multiplyAlpha(pure, a));
		Shapes.gradientRect(g, sx, sy, sw, SV_H, 8, 0x00000000, Colors.argb(a, 0));
		knob(g, sx + sat * sw, sy + (1 - val) * SV_H, Colors.hsv(hue, sat, val), a);

		// Hue bar.
		float hy = hueY();
		float seg = sw / (HUE_STOPS.length - 1);
		for (int i = 0; i < HUE_STOPS.length - 1; i++) {
			Shapes.gradientRectH(g, sx + i * seg - 0.3f, hy, seg + 0.6f, BAR_H, 0,
				Colors.multiplyAlpha(HUE_STOPS[i], a), Colors.multiplyAlpha(HUE_STOPS[i + 1], a));
		}
		Shapes.outline(g, sx, hy, sw, BAR_H, 2, Colors.argb(0.2f * a, 0xFFFFFF), 1);
		knob(g, sx + hue * sw, hy + BAR_H / 2, pure, a);

		// Alpha bar (main colour only).
		if (alphaShown()) {
			float ay = alphaY();
			int rgb = Colors.hsv(hue, sat, val) & 0xFFFFFF;
			ColorButton.drawSwatch(g, sx, ay, sw, BAR_H, 2, 0, a);
			Shapes.gradientRectH(g, sx, ay, sw, BAR_H, 2, Colors.argb(0, rgb), Colors.argb(a, rgb));
			knob(g, sx + alpha * sw, ay + BAR_H / 2, Colors.argb(alpha, rgb), a);
		}

		// Speed.
		if (mode != Mode.STATIC) {
			float py = speedY();
			Fonts.draw(g, Typeface.MONO_BOLD, "SPEED", sx, py - 10, 8, Theme.text(0.45f, a), Fonts.Align.LEFT, 1.5f, false);
			Fonts.draw(g, Typeface.MONO_MEDIUM, String.format(java.util.Locale.ROOT, "%.1fx", meta.speed()), sx + sw, py - 10, 8.5f, Theme.text(0.6f, a),
				Fonts.Align.RIGHT, 0, false);
			Shapes.rect(g, sx, py + BAR_H / 2 - 2, sw, 4, 2, Colors.argb(0.15f * a, 0xFFFFFF));
			float p = (meta.speed() - 0.1f) / 3.9f;
			Shapes.rect(g, sx, py + BAR_H / 2 - 2, sw * p, 4, 2, Colors.multiplyAlpha(Theme.accent(), a));
			knob(g, sx + p * sw, py + BAR_H / 2, Theme.accent(), a);
		}

		// Hex field and a live preview of the (moving) colour.
		float fy = hexY();
		hex.bounds(sx, fy, 128, 28);
		hex.render(g, mx, my - lift, a, true);
		ColorButton.drawSwatch(g, sx + sw - 64, fy, 64, 28, 7, mode == Mode.STATIC ? current() : meta.argb(), a);
		g.pose().popMatrix();
	}

	private void smallButton(GuiGraphicsExtractor g, float bx, float by, String label, float a) {
		float s = stopSize();
		Shapes.rect(g, bx, by, s, s, 6, Colors.multiplyAlpha(0x1FFFFFFF, a), Colors.multiplyAlpha(0x40FFFFFF, a), 1);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, label, bx + s / 2, by + s / 2, 15, Theme.text(0.9f, a), Fonts.Align.CENTER, 0, false);
	}

	private static void knob(GuiGraphicsExtractor g, float cx, float cy, int color, float fade) {
		Shapes.circle(g, cx, cy, 7.5f, Colors.argb(0.35f * fade, 0));
		Shapes.circle(g, cx, cy, 6.5f, Colors.withAlpha(color, fade), Colors.argb(fade, 0xFFFFFF), 2);
	}

	private int current() {
		int rgb = Colors.hsv(hue, sat, val) & 0xFFFFFF;
		return alphaShown() ? Colors.argb(alpha, rgb) : (stop >= 0 ? 0xFF000000 | rgb : (hasAlpha ? Colors.argb(alpha, rgb) : 0xFF000000 | rgb));
	}

	private void commit() {
		apply(current());
		hex.setText(ColorSetting.toHex(current(), hasAlpha && stop < 0));
	}

	private void apply(int color) {
		if (stop >= 0) {
			List<Integer> stops = meta.stops();
			if (stop < stops.size()) {
				stops.set(stop, color | 0xFF000000);
				meta.setStops(stops);
			}
		} else {
			value.set(color);
		}
	}

	private void applyHex(String text) {
		int digits = text.replace("#", "").length();
		if (digits != 6 && digits != 8) {
			return; // wait until a full code is typed
		}
		Integer parsed = ColorSetting.parseHex(text);
		if (parsed == null) {
			return;
		}
		float[] hsv = Colors.toHsv(parsed);
		hue = hsv[0];
		sat = hsv[1];
		val = hsv[2];
		alpha = hasAlpha ? (parsed >>> 24) / 255f : 1;
		apply(current());
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return true;
		}
		if (hex.contains(mx, my)) {
			host.focus(hex);
			hex.mouseClicked(mx, my, button);
			return true;
		}
		host.focus(null);
		float sx = x + PAD;
		float sw = innerW();
		// Mode tabs.
		if (my >= tabsY() && my <= tabsY() + TABS_H && mx >= sx && mx <= sx + sw) {
			Mode m = Mode.values()[Math.clamp((int) ((mx - sx) / (sw / 3)), 0, 2)];
			meta.setMode(m);
			stop = m == Mode.GRADIENT ? 0 : -1;
			loadEdited();
			layoutHeight();
			dragging = Part.NONE;
			return true;
		}
		// Gradient colours, + and -.
		if (meta.mode() == Mode.GRADIENT && my >= stopsY() && my <= stopsY() + STOPS_H) {
			List<Integer> stops = meta.stops();
			int slot = (int) ((mx - sx) / (stopSize() + 6));
			if (slot >= 0 && slot < stops.size()) {
				stop = slot;
			} else if (slot == stops.size() && stops.size() < ColorSetting.MAX_STOPS) {
				int last = stops.getLast();
				float[] hsv = Colors.toHsv(last);
				stops.add(Colors.hsv((hsv[0] + 0.2f) % 1f, Math.max(0.5f, hsv[1]), Math.max(0.6f, hsv[2])));
				meta.setStops(stops);
				stop = stops.size() - 1;
			} else if (stops.size() > 2 && (slot == stops.size() + (stops.size() < ColorSetting.MAX_STOPS ? 1 : 0))) {
				stops.remove(Math.max(0, stop));
				meta.setStops(stops);
				stop = Math.min(Math.max(0, stop), stops.size() - 1);
			}
			loadEdited();
			layoutHeight();
			dragging = Part.NONE;
			return true;
		}
		if (my >= svY() && my <= svY() + SV_H) {
			dragging = Part.SV;
		} else if (my >= hueY() - 4 && my <= hueY() + BAR_H + 4) {
			dragging = Part.HUE;
		} else if (alphaShown() && my >= alphaY() - 4 && my <= alphaY() + BAR_H + 4) {
			dragging = Part.ALPHA;
		} else if (meta.mode() != Mode.STATIC && my >= speedY() - 4 && my <= speedY() + BAR_H + 4) {
			dragging = Part.SPEED;
		} else {
			dragging = Part.NONE;
		}
		if (mx < sx - 6 || mx > sx + sw + 6) {
			dragging = Part.NONE;
		}
		drag(mx, my);
		return true;
	}

	@Override
	public boolean mouseDragged(float mx, float my, int button) {
		drag(mx, my);
		return true;
	}

	@Override
	public boolean mouseReleased(float mx, float my, int button) {
		dragging = Part.NONE;
		return true;
	}

	private void drag(float mx, float my) {
		float px = Math.clamp((mx - x - PAD) / innerW(), 0f, 1f);
		switch (dragging) {
			case SV -> {
				sat = px;
				val = 1 - Math.clamp((my - svY()) / SV_H, 0f, 1f);
			}
			case HUE -> hue = Math.min(px, 0.9999f);
			case ALPHA -> alpha = px;
			case SPEED -> {
				meta.setSpeed(0.1f + px * 3.9f);
				return;
			}
			default -> {
				return;
			}
		}
		commit();
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		return hex.keyPressed(event);
	}

	public boolean charTyped(CharacterEvent event) {
		return hex.charTyped(event);
	}
}
