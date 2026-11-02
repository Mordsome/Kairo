package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.keybinds.InputTracker;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.gui.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Left | right clicks per second over a rolling one-second window (your own clicks only). */
public final class CpsHud extends HudElement {
	public enum Format implements EnumSetting.Labeled {
		LEFT_RIGHT("L | R CPS"),
		LEFT_ONLY("L CPS"),
		COMBINED("Total CPS");

		private final String label;

		Format(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Format> format = add(new EnumSetting<>("format", "Format", Format.LEFT_RIGHT));
	public final BooleanSetting attachToKeystrokes = add(new BooleanSetting("attach", "Attach to Keystrokes", true)
		.description("Sits right under the Keystrokes box"));

	private String value = "0 | 0";
	private String valueWithUnit = "0 | 0 CPS";
	private int lastLeft = -1;
	private int lastRight = -1;
	private Format lastFormat;

	public CpsHud() {
		super("cps", "CPS Counter", "Left | right clicks per second", Icon.MOUSE, true, Anchor.TOP_LEFT, 0.004, 0.16);
	}

	@Override
	protected void layout(boolean preview) {
		int left = preview ? 0 : InputTracker.leftCps();
		int right = preview ? 0 : InputTracker.rightCps();
		Format f = format.get();
		// Only build a new string when something changed (this runs every frame).
		if (left != lastLeft || right != lastRight || f != lastFormat) {
			lastLeft = left;
			lastRight = right;
			lastFormat = f;
			value = switch (f) {
				case LEFT_RIGHT -> left + " | " + right;
				case LEFT_ONLY -> Integer.toString(left);
				case COMBINED -> Integer.toString(left + right);
			};
			valueWithUnit = value + " CPS";
		}
		float textW = HudText.width(valueWithUnit);
		float attached = HudManager.attachedWidth();
		width = Math.max(textW + pad() * 2, attached);
		height = HudText.LINE + pad() * 2;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		drawBackground(g);
		float textW = HudText.width(valueWithUnit);
		float x = (width - textW) / 2;
		float after = HudText.draw(g, value, x, pad(), valueColor.argb(), shadow());
		HudText.draw(g, " CPS", after, pad(), textColor.argb(), shadow());
	}
}
