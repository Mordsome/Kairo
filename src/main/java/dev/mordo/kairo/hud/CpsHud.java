package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.keybinds.InputTracker;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.TextSetting;
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
	public final TextSetting label = add(new TextSetting("label", "Label", "CPS", 12)
		.description("The word after the numbers. Leave it empty for just the numbers (8 | 6)"));
	public final BooleanSetting attachToKeystrokes = add(new BooleanSetting("attach", "Attach to Keystrokes", true)
		.description("Sits right under the Keystrokes box"));
	public final BooleanSetting hideWithKeystrokes = add(new BooleanSetting("hide_with_keystrokes", "Hide when Keystrokes shows CPS", true)
		.description("No separate box while the Keystrokes mouse buttons show your CPS")
		.visibleWhen(() -> ModuleManager.get(KeystrokesHud.class).showsCps()));

	private String value = "0 | 0";
	private String suffix = " CPS";
	private int lastLeft = -1;
	private int lastRight = -1;
	private Format lastFormat;
	private String lastLabel;

	public CpsHud() {
		super("cps", "CPS Counter", "Left | right clicks per second", Icon.MOUSE, true, Anchor.TOP_LEFT, 0.004, 0.16);
	}

	/** Hidden right now because the Keystrokes mouse buttons already show the CPS (and that's what the player chose). */
	public boolean coveredByKeystrokes() {
		KeystrokesHud keys = ModuleManager.get(KeystrokesHud.class);
		return hideWithKeystrokes.isOn() && keys.isEnabled() && keys.showsCps();
	}

	@Override
	protected void layout(boolean preview) {
		int left = preview ? 0 : InputTracker.leftCps();
		int right = preview ? 0 : InputTracker.rightCps();
		Format f = format.get();
		String text = label.get().strip();
		// Only build new strings when something changed (this runs every frame).
		if (left != lastLeft || right != lastRight || f != lastFormat || !text.equals(lastLabel)) {
			lastLeft = left;
			lastRight = right;
			lastFormat = f;
			lastLabel = text;
			value = switch (f) {
				case LEFT_RIGHT -> left + " | " + right;
				case LEFT_ONLY -> Integer.toString(left);
				case COMBINED -> Integer.toString(left + right);
			};
			suffix = text.isEmpty() ? "" : " " + text;
		}
		float textW = HudText.width(value + suffix);
		float attached = HudManager.attachedWidth();
		width = Math.max(textW + pad() * 2, attached);
		height = HudText.LINE + pad() * 2;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		drawBackground(g);
		float textW = HudText.width(value + suffix);
		float x = (width - textW) / 2;
		float after = HudText.draw(g, value, x, pad(), valueColor.argb(), shadow());
		if (!suffix.isEmpty()) {
			HudText.draw(g, suffix, after, pad(), textColor.argb(), shadow());
		}
	}
}
