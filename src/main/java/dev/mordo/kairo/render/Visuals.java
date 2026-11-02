package dev.mordo.kairo.render;

import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.gui.Toasts;

/**
 * Kairo Void's master switch: one toggle (ring segment, key, Visuals page) that turns every visual off and on again
 * without touching the choices underneath, for when you just want plain Minecraft for a while.
 */
public final class Visuals {
	private Visuals() {
	}

	/** True when Void's visuals should draw (always false in the main Kairo jar). */
	public static boolean on() {
		return Edition.isVoid() && VoidSettings.INSTANCE.master.isOn();
	}

	/** Whether the shared in-game visuals (filters, hand animations, hit effects...) run: always in Kairo, unless switched off in Void. */
	public static boolean live() {
		return !Edition.isVoid() || VoidSettings.INSTANCE.master.isOn();
	}

	public static void toggle() {
		var master = VoidSettings.INSTANCE.master;
		master.set(!master.isOn());
		Toasts.show(master.isOn() ? "Visuals on" : "Visuals off  ·  plain Minecraft", master.isOn() ? dev.mordo.kairo.gui.Theme.accent() : 0xFF9AA3AD);
		dev.mordo.kairo.sound.UiSounds.toggle(master.isOn());
	}
}
