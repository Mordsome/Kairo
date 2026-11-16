package dev.mordo.kairo.api;

import dev.mordo.kairo.core.Module;

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
	 * Adds a mod to the Mods list (a {@link Module}, or a {@link dev.mordo.kairo.hud.HudElement} for something on the
	 * HUD). Its id must be unique: start it with your addon's own id.
	 */
	void mod(Module mod);
}
