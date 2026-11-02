package dev.mordo.kairo.sound;

import dev.mordo.kairo.core.ClientSettings;
import net.minecraft.client.Minecraft;

/** Soft built-in sounds for the client's own menus. Volume comes from Visuals → Menu sounds (0 = off). */
public final class UiSounds {
	private static long lastHover;

	private UiSounds() {
	}

	public static void click() {
		play("ui_click", 1, 1);
	}

	/** Rate-limited so sweeping the mouse across a list doesn't rattle. */
	public static void hover() {
		long now = System.currentTimeMillis();
		if (now - lastHover > 45) {
			lastHover = now;
			play("ui_hover", 0.7f, 1);
		}
	}

	public static void toggle(boolean on) {
		play(on ? "ui_toggle_on" : "ui_toggle_off", 1, 1);
	}

	public static void open() {
		play("ui_open", 1, 1);
	}

	public static void close() {
		play("ui_close", 1, 1);
	}

	public static void pvp(boolean on) {
		play(on ? "ui_pvp_on" : "ui_pvp_off", 1, 1);
	}

	private static void play(String name, float volume, float pitch) {
		float master = ClientSettings.INSTANCE.menuSounds.floatValue();
		if (master <= 0.001f) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		mc.getSoundManager().play(CustomSoundInstance.ui(SoundLibrary.uiKey(name), volume * master, pitch));
	}
}
