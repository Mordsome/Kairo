package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** WASD (or arrows), space bar and mouse buttons, with a smooth press fade. Shows your own key presses only. */
public final class KeystrokesHud extends HudElement {
	public enum KeyStyle implements EnumSetting.Labeled {
		LETTERS("Letters"),
		ARROWS("Arrows");

		private final String label;

		KeyStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<KeyStyle> style = add(new EnumSetting<>("style", "Key labels", KeyStyle.ARROWS)
		.description("Letters (W A S D) or arrows"));
	/** What the mouse buttons show: their names, your live clicks per second, or both. */
	public enum MouseText implements EnumSetting.Labeled {
		LABEL("Label"),
		CPS("CPS"),
		BOTH("Both");

		private final String label;

		MouseText(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final BooleanSetting showSpace = add(new BooleanSetting("show_space", "Space bar", false));
	public final BooleanSetting showMouse = add(new BooleanSetting("show_mouse", "Mouse buttons", true)
		.description("LMB | RMB row under the keys"));
	public final EnumSetting<MouseText> mouseText = add(new EnumSetting<>("mouse_text", "Show CPS in Keystrokes", MouseText.LABEL)
		.description("Mouse buttons show LMB / RMB, your live CPS for each button, or both (small label above the number)")
		.visibleWhen(showMouse::isOn));
	public final SliderSetting keySize = add(new SliderSetting("key_size", "Key size", 22, 14, 36, 1).unit("px"));
	public final SliderSetting gap = add(new SliderSetting("gap", "Gap", 2, 0, 6, 1).unit("px"));
	public final ColorSetting pressedColor = add(new ColorSetting("pressed_color", "Pressed color", 0xBFFFFFFF).accentable());
	public final ColorSetting pressedTextColor = add(new ColorSetting("pressed_text_color", "Pressed text color", 0xFF000000));
	public final SliderSetting fadeTime = add(new SliderSetting("fade_time", "Press fade", 90, 0, 300, 10).unit("ms"));

	private static final int W = 0, A = 1, S = 2, D = 3, LMB = 4, RMB = 5, SPACE = 6;
	private final Animation[] press = new Animation[7];
	/** When each key was last pressed down (for Void's pop and ripple), and whether it's down now. */
	private final long[] pressedAt = new long[7];
	private final boolean[] down = new boolean[7];

	public KeystrokesHud() {
		super("keystrokes", "Keystrokes", "WASD and mouse buttons", Icon.KEYBOARD, true, Anchor.TOP_LEFT, 0.004, 0.008);
		for (int i = 0; i < press.length; i++) {
			press[i] = new Animation(0);
		}
	}

	/** True if the mouse buttons show live CPS (so the separate CPS box can step aside). */
	public boolean showsCps() {
		return showMouse.isOn() && mouseText.get() != MouseText.LABEL;
	}

	private float mouseRowHeight() {
		if (showMouse.isOn() && mouseText.get() == MouseText.BOTH) {
			return Math.max(19, keySize.floatValue() * 0.9f);
		}
		return Math.max(12, keySize.floatValue() * 0.8f);
	}

	private float spaceHeight() {
		return Math.max(8, keySize.floatValue() * 0.5f);
	}

	@Override
	protected void layout(boolean preview) {
		float ks = keySize.floatValue();
		float gp = gap.floatValue();
		width = ks * 3 + gp * 2;
		height = ks * 2 + gp;
		if (showMouse.isOn()) {
			height += gp + mouseRowHeight();
		}
		if (showSpace.isOn()) {
			height += gp + spaceHeight();
		}
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		Options o = Minecraft.getInstance().options;
		update(W, o.keyUp, preview);
		update(A, o.keyLeft, preview);
		update(S, o.keyDown, preview);
		update(D, o.keyRight, preview);
		update(LMB, o.keyAttack, preview);
		update(RMB, o.keyUse, preview);
		update(SPACE, o.keyJump, preview);

		float ks = keySize.floatValue();
		float gp = gap.floatValue();
		boolean arrows = style.get() == KeyStyle.ARROWS;
		key(g, W, ks + gp, 0, ks, ks, arrows ? null : "W", -Math.PI / 2, null);
		key(g, A, 0, ks + gp, ks, ks, arrows ? null : "A", Math.PI, null);
		key(g, S, ks + gp, ks + gp, ks, ks, arrows ? null : "S", Math.PI / 2, null);
		key(g, D, (ks + gp) * 2, ks + gp, ks, ks, arrows ? null : "D", 0, null);
		float y = ks * 2 + gp * 2;
		if (showMouse.isOn()) {
			float half = (width - gp) / 2;
			float mh = mouseRowHeight();
			MouseText text = mouseText.get();
			if (text == MouseText.LABEL) {
				key(g, LMB, 0, y, half, mh, "LMB", 0, null);
				key(g, RMB, half + gp, y, half, mh, "RMB", 0, null);
			} else {
				String left = Integer.toString(preview ? 0 : dev.mordo.kairo.core.keybinds.InputTracker.leftCps());
				String right = Integer.toString(preview ? 0 : dev.mordo.kairo.core.keybinds.InputTracker.rightCps());
				boolean both = text == MouseText.BOTH;
				key(g, LMB, 0, y, half, mh, left, 0, both ? "LMB" : null);
				key(g, RMB, half + gp, y, half, mh, right, 0, both ? "RMB" : null);
			}
			y += mh + gp;
		}
		if (showSpace.isOn()) {
			float sh = spaceHeight();
			int i = SPACE;
			key(g, i, 0, y, width, sh, null, Double.NaN, null);
			float t = press[i].value();
			int line = Colors.lerp(textColor.argb(), pressedTextColor.argb(), t);
			Shapes.rect(g, width / 2 - ks * 0.6f, y + sh / 2 - 0.75f, ks * 1.2f, 1.5f, 0.75f, line);
		}
	}

	private void update(int index, KeyMapping mapping, boolean preview) {
		boolean isDown = !preview && mapping.isDown();
		if (isDown && !down[index]) {
			pressedAt[index] = System.nanoTime();
		}
		down[index] = isDown;
		press[index].animateTo(isDown ? 1 : 0, fadeTime.floatValue(), Easing.OUT_CUBIC);
	}

	private static boolean ripples() {
		return dev.mordo.kairo.render.Visuals.on() && dev.mordo.kairo.core.VoidSettings.INSTANCE.keyRipples.isOn();
	}

	/**
	 * One key box. {@code label} null with a finite angle draws an arrow pointing that way; {@code caption} (or null)
	 * is a small label drawn above the main one.
	 */
	private void key(GuiGraphicsExtractor g, int index, float x, float y, float w, float h, String label, double arrowAngle, String caption) {
		float t = press[index].value();
		int idle = background.isOn() ? backgroundColor.argb() : 0;
		int fill = Colors.lerp(idle, pressedColor.argb(), t);
		float radius = cornerRadius.floatValue();
		float age = pressedAt[index] == 0 ? 9 : (System.nanoTime() - pressedAt[index]) / 1e9f;
		boolean fx = ripples();
		g.pose().pushMatrix();
		if (fx && age < 0.25f) {
			// A quick pop as the key goes down.
			float pop = 1 + 0.12f * (float) Math.sin(age / 0.25f * Math.PI);
			g.pose().translate(x + w / 2, y + h / 2);
			g.pose().scale(pop, pop);
			g.pose().translate(-(x + w / 2), -(y + h / 2));
		}
		if (background.isOn()) {
			HudFx.under(g, x, y, w, h, radius, index * 1.7f, t);
		}
		if (Colors.alpha(fill) > 0) {
			Shapes.rect(g, x, y, w, h, radius, fill);
		}
		if (background.isOn()) {
			HudFx.over(g, x, y, w, h, radius, index * 1.7f, t);
		}
		g.pose().popMatrix();
		if (fx && age < 0.45f) {
			// A ripple running out from the key.
			float k = age / 0.45f;
			float grow = 2 + k * Math.min(w, h) * 0.55f;
			int accent = dev.mordo.kairo.gui.Theme.accent();
			Shapes.rect(g, x - grow, y - grow, w + grow * 2, h + grow * 2, radius + grow, 0, Colors.withAlpha(accent, 0.85f * (1 - k) * (1 - k)), 1.3f);
		}
		int fg = Colors.lerp(textColor.argb(), pressedTextColor.argb(), t);
		if (label != null && caption != null) {
			// Small caption on top, the number under it.
			g.pose().pushMatrix();
			g.pose().translate(x + w / 2, y + 2.5f);
			g.pose().scale(0.6f, 0.6f);
			HudText.drawCentered(g, caption, 0, 0, Colors.multiplyAlpha(fg, 0.75f), false);
			g.pose().popMatrix();
			HudText.drawCentered(g, label, x + w / 2, y + h - 10.5f, fg, shadow() && t < 0.5f);
		} else if (label != null) {
			HudText.drawCentered(g, label, x + w / 2, y + (h - 8) / 2, fg, shadow() && t < 0.5f);
		} else if (!Double.isNaN(arrowAngle)) {
			float size = Math.max(5, w * 0.32f);
			if (shadow() && t < 0.5f) {
				Shapes.triangle(g, x + w / 2 + 0.8f, y + h / 2 + 0.8f, size, (float) arrowAngle, Colors.multiplyAlpha(0xFF000000 | (fg & 0xFCFCFC) >> 2, 1));
			}
			Shapes.triangle(g, x + w / 2, y + h / 2, size, (float) arrowAngle, fg);
		}
	}
}
