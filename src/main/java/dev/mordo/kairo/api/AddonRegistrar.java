package dev.mordo.kairo.api;

import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.Setting;

/**
 * The sockets an addon can plug into (version {@link Addons#API_VERSION}). Everything here only adds: an addon gets
 * its own entry next to Kairo's and next to other addons', so any set of addons works together.
 */
public interface AddonRegistrar {
	/** The version of these sockets this Kairo has. */
	int apiVersion();

	/** True in Kairo Void, false in Kairo. */
	boolean isVoid();

	/**
	 * Adds a look for the menus (panels, the ring's segments, the backdrop). The player picks one look at a time in
	 * Visuals; a look that has just been installed is picked for them once.
	 */
	void menuLook(MenuLook look);

	/**
	 * Adds a look for the HUD (the boxes behind HUD mods and Keystrokes' keys; since Kairo 0.4.0). The player picks
	 * one in Visuals; a look that has just been installed is picked for them once.
	 */
	void hudLook(HudLook look);

	/**
	 * Adds a home menu: something Right Shift can open instead of the ring (since Kairo 0.4.0). The player picks it in
	 * Visuals → Ring; one that has just been installed is picked for them once.
	 */
	void homeMenu(HomeMenu home);

	/**
	 * Adds a tab of the addon's own to Visuals (since Kairo 0.4.0): its name, its icon and the options in it (in
	 * Kairo Void, a card under Interface). Kairo saves the options with the player's settings.
	 */
	void visualsTab(String name, dev.mordo.kairo.gui.Icon icon, java.util.List<Setting<?>> settings);

	/**
	 * Adds a mod to the Mods list (a {@link Module}, or a {@link dev.mordo.kairo.hud.HudElement} for something on the
	 * HUD). Its id must be unique: start it with your addon's own id.
	 */
	void mod(Module mod);

	/**
	 * Turns on a part of plain Kairo that only comes with an addon (since Kairo 0.4.0): "animations" or
	 * "combat-effects" (see {@link Features}). In Kairo Void everything is always there.
	 */
	void unlock(String feature);
}
