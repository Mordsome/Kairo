package dev.mordo.kairo.api;

import dev.mordo.kairo.core.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A look for Kairo's menus: how panels, the ring's segments and the backdrop behind a menu are drawn. Kairo's own
 * looks are frosted glass (Kairo) and flat grey (Kairo Void); an addon can add more.
 *
 * <p>The three draw methods get exactly what Kairo's own look gets. {@code tint} is the colour of the surface (its
 * alpha says how strongly it should show: plain panels are faint, hovered and selected ones carry your accent
 * colour), {@code stroke} its edge, and {@code opacity} fades the whole thing (menus fade in and out). Draw with
 * {@link dev.mordo.kairo.render.Shapes}; don't call its {@code glass...} methods or {@code backdrop} from here (they
 * are what calls you).
 */
public interface MenuLook {
	/** A short id, unique among looks, lower case with dashes: "plastic". Saved in the player's settings. */
	String id();

	/** The name shown in the chooser: "Plastic". */
	String name();

	/** A panel, card, button or tile. */
	void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int tint, int stroke, float strokeWidth, float opacity);

	/**
	 * A segment of the ring menu: the part of a ring between {@code innerR} and {@code outerR}, centred on
	 * {@code midAngle} and reaching {@code halfAngle} to either side, with {@code gap} cut from its straight edges.
	 */
	void sector(GuiGraphicsExtractor g, float cx, float cy, float innerR, float outerR, float midAngle, float halfAngle, float gap,
				float cornerRadius, int tint, int stroke, float strokeWidth, float opacity);

	/** What covers the world behind an open menu ({@code dim} and {@code vignette} are the player's settings, 0 to 1). */
	void backdrop(GuiGraphicsExtractor g, float opacity, float dim, float vignette);

	/** The look's own options, shown under the chooser and saved with the player's settings. */
	default List<Setting<?>> settings() {
		return List.of();
	}

	// ---- more of the game (Kairo 0.4.0 and later; all optional) ----

	/**
	 * For a look that draws window buttons on its panels (close, minimise, maximise): which one is at
	 * {@code (mx, my)} in the menu's reference units, as drawn last frame: "close", "minimize", "maximize", or null.
	 * Kairo closes the menu for "close" and "minimize" and makes the menus fill the screen (or not) for "maximize".
	 */
	default String windowButton(float mx, float my) {
		return null;
	}

	/** The loading screen's background colour in this look (opaque RGB), or 0 for Kairo's own. */
	default int loadingBackground() {
		return 0;
	}

	/**
	 * Draws the loading screen in the look's style over its background ({@code width} x {@code height} GUI pixels;
	 * {@code progress} 0 to 1; {@code alpha} fades it in and out). Return false to keep Kairo's wordmark and bar.
	 * Use Minecraft's plainest drawing only ({@code g.fill}, {@code g.fillGradient}): the very first loading screen
	 * comes before Kairo's shapes, shaders and fonts (and Minecraft's own font) are loaded.
	 */
	default boolean loading(GuiGraphicsExtractor g, int width, int height, float progress, float alpha) {
		return false;
	}

	/** True while the menus are made to fill the screen (the "maximize" window button). */
	static boolean maximized() {
		return dev.mordo.kairo.gui.menu.PanelPage.maximized;
	}

	/**
	 * The colour for text on the look's surfaces, opaque ARGB, or 0 to keep Kairo's own (light) text. A look with light
	 * surfaces returns a dark colour here: Kairo's menus use it, and so do the labels of Minecraft's own buttons.
	 */
	default int textColor() {
		return 0;
	}

	/**
	 * Draws the background behind the main menu and every menu opened from it (instead of the panorama), filling
	 * {@code width} x {@code height} GUI pixels. Return false to keep the panorama.
	 */
	default boolean desktop(GuiGraphicsExtractor g, int width, int height) {
		return false;
	}

	/**
	 * True if the look repaints Minecraft's own GUI images (inventories and other containers, the hotbar, hearts,
	 * hunger, the experience bar, buttons and every other GUI sprite). Kairo then offers each image to
	 * {@link #repaint} and puts the changed ones in a resource pack it writes itself (nothing of Minecraft's is shipped
	 * in the addon). Kairo's own Inventory glass steps aside while this look is in use.
	 */
	default boolean repaints() {
		return false;
	}

	/** Changes whenever the repainted images would come out different (an option of the look changed, say). */
	default String repaintKey() {
		return "";
	}

	/**
	 * Repaints one image in place. {@code path} is its place in Minecraft's assets, like
	 * {@code textures/gui/container/inventory.png} or {@code textures/gui/sprites/hud/heart/full.png}; {@code argb} holds
	 * {@code width} x {@code height} pixels, row by row. Return true if the image changed. Called on a background
	 * thread: don't touch the game from here.
	 */
	default boolean repaint(String path, int[] argb, int width, int height) {
		return false;
	}
}
