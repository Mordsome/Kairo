package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Easing;

/**
 * Brighter caves and nights by raising the gamma value the lightmap uses. Gamma only:
 * no night vision effect, no changes to light levels or lighting data.
 */
public final class Fullbright extends Module {
	/** Gamma at full strength (vanilla's "Bright" is 1.0). */
	private static final float MAX_GAMMA = 15f;

	public final SliderSetting strength = add(new SliderSetting("strength", "Strength", 1.0, 0.1, 1.0, 0.05).percent());
	public final SliderSetting fade = add(new SliderSetting("fade", "Fade time", 400, 0, 1500, 50).unit("ms"));
	public final KeybindSetting toggleKey = add(new KeybindSetting("toggle_key", "Toggle key", Keybind.NONE)
		.onPress(() -> {
			toggle(); // in PvP mode this only changes PvP mode's setting
			String text = isEnabled() ? "Fullbright on" : "Fullbright off";
			Toasts.show(PvpMode.isActive() ? text + " (PvP mode)" : text, dev.mordo.kairo.gui.Theme.modeAccent());
		}));

	private final Animation amount = new Animation(0);

	public Fullbright() {
		super("fullbright", "Fullbright", "Gamma boost for caves and nights", Category.VISUAL, Icon.SUN, false);
	}

	@Override
	protected void onEnable() {
		amount.animateTo(1, fade.floatValue(), Easing.IN_OUT_CUBIC);
	}

	@Override
	protected void onDisable() {
		amount.animateTo(0, fade.floatValue(), Easing.IN_OUT_CUBIC);
	}

	/** The gamma to use instead of the player's option. */
	public float gamma(float original) {
		float t = amount.value();
		if (t <= 0) {
			return original;
		}
		float boosted = Math.max(original, 1 + (MAX_GAMMA - 1) * strength.floatValue());
		return original + (boosted - original) * t;
	}
}
