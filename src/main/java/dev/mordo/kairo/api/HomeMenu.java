package dev.mordo.kairo.api;

import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.gui.Icon;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * A different home for Kairo's menu (since Kairo 0.4.0): what Right Shift opens instead of the ring, and where "Back"
 * in every Kairo page leads. The player picks one in Visuals → Ring (Right Shift opens).
 *
 * <p>Kairo hands the menu a {@link Context}: the player's ring sections (as {@link Item}s, in their order) and a
 * search over pages, mods and mod settings. Running an item does what the ring would do (open a page, toggle PvP
 * mode, open a mod's settings). The menu only draws and reacts to input; everything is in Kairo's reference units
 * ({@code width} x {@code height} is the whole screen), and the backdrop behind it is already drawn. Draw with
 * {@link dev.mordo.kairo.render.Shapes} ({@code surface} and {@code glassRect} follow the player's theme),
 * {@link dev.mordo.kairo.render.font.Fonts}, {@link dev.mordo.kairo.gui.IconRenderer} and
 * {@link dev.mordo.kairo.gui.Theme}.
 */
public interface HomeMenu {
	/** A short id, unique among home menus, lower case with dashes: "command-bar". Saved in the player's settings. */
	String id();

	/** The name shown in the chooser: "Command bar". */
	String name();

	/** The menu's own options, shown under the chooser and saved with the player's settings. */
	default List<Setting<?>> settings() {
		return List.of();
	}

	/** True if the menu takes typing (a search box): Kairo then switches text input on while it is shown. */
	default boolean takesText() {
		return false;
	}

	/** The menu was opened, or the player came back to it from a page: start fresh (an empty search, say). */
	default void opened(Context menu) {
	}

	/**
	 * Draws the menu. {@code appear} goes from 0 to 1 as it opens and back to 0 as it leaves (fade and move with it);
	 * {@code mx}, {@code my} is the mouse.
	 */
	void render(GuiGraphicsExtractor g, Context menu, float mx, float my, float width, float height, float appear);

	/** A click (button 0 left, 1 right). Return true if the menu used it. */
	default boolean mouseClicked(Context menu, float mx, float my, int button) {
		return false;
	}

	default boolean mouseScrolled(Context menu, float mx, float my, float amount) {
		return false;
	}

	/**
	 * A key went down ({@code key} is a key code, as in {@code com.mojang.blaze3d.platform.InputConstants}). Return true
	 * if the menu used it. Keys it doesn't use keep their usual meaning (the menu key closes the menu).
	 */
	default boolean keyPressed(Context menu, int key, boolean shift, boolean ctrl) {
		return false;
	}

	/** A character was typed. */
	default boolean charTyped(Context menu, int codepoint) {
		return false;
	}

	/** Esc: return true if the menu handled it (cleared its search, say); false closes the menu. */
	default boolean escape(Context menu) {
		return false;
	}

	/** What Kairo gives a home menu. */
	interface Context {
		/** The player's ring sections, in their order (3 to 8; Visuals → Ring). */
		List<Item> sections();

		/**
		 * Pages, actions, mods and mod settings matching {@code query}, best first (at most {@code limit}). Matches
		 * names first, then descriptions; an empty query gives nothing.
		 */
		List<Item> search(String query, int limit);

		/** Closes the menu, back to the game (or wherever it was opened from). */
		void close();
	}

	/**
	 * Something the menu can show and run.
	 *
	 * @param title  its name: "Visuals", "Keystrokes", "Show CPS"
	 * @param detail a line under it: a page's description, a mod's description, the mod a setting belongs to
	 * @param icon   its icon (draw with {@link dev.mordo.kairo.gui.IconRenderer})
	 * @param kind   what it is: "Page", "Action", "Mod" or "Setting"
	 * @param on     for things that are on or off (PvP mode, a mod): whether it is on now; null otherwise
	 * @param open   runs it: opens the page or the mod's settings, or does the action
	 * @param toggle for a mod: switches it on or off without leaving the menu; null otherwise
	 */
	record Item(String title, String detail, Icon icon, String kind, @Nullable BooleanSupplier on, Runnable open, @Nullable Runnable toggle) {
	}
}
