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
}
