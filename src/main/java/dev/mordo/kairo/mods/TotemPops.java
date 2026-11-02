package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;

/** How big and how long the totem-of-undying pop animation is, and how many particles it makes. Visual only. */
public final class TotemPops extends Module {
	public final SliderSetting popScale = add(new SliderSetting("pop_scale", "Pop animation size", 1.0, 0.1, 1.0, 0.05).percent()
		.<SliderSetting>pvpDefault(0.5));
	public final SliderSetting popDuration = add(new SliderSetting("pop_duration", "Pop animation length", 1.0, 0.2, 1.0, 0.05).percent()
		.<SliderSetting>pvpDefault(0.6));
	public final SliderSetting particles = add(new SliderSetting("particles", "Totem particles", 1.0, 0.0, 1.0, 0.05).percent()
		.<SliderSetting>pvpDefault(0.3));

	public TotemPops() {
		super("totem_pops", "Totem Pops", "Smaller, shorter pop animation", Category.PVP, Icon.TOTEM, true);
	}

	public float scale() {
		return isEnabled() ? popScale.floatValue() : 1;
	}

	public float duration() {
		return isEnabled() ? popDuration.floatValue() : 1;
	}

	public float particleAmount() {
		return isEnabled() ? particles.floatValue() : 1;
	}
}
