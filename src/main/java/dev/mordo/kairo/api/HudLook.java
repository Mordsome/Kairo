package dev.mordo.kairo.api;

import dev.mordo.kairo.core.settings.Setting;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A look for the HUD (since Kairo 0.4.0): how the box behind each HUD mod is drawn, and the box behind each key of
 * Keystrokes. Kairo's own look is a plain rounded box in the player's background colour (and in Kairo Void, the HUD
 * style they picked); an addon can add more. The player picks one in Visuals (HUD look).
 *
 * <p>Only the boxes change: what is drawn on top of them (text, icons, bars) stays the mod's own. The box is drawn
 * in the mod's own space, already moved and scaled to where the mod sits on screen, so one unit is one GUI pixel at
 * the mod's size. Draw with {@link dev.mordo.kairo.render.Shapes}.
 */
public interface HudLook {
	/** A short id, unique among HUD looks, lower case with dashes: "glass". Saved in the player's settings. */
	String id();

	/** The name shown in the chooser: "Glass". */
	String name();

	/**
	 * One box.
	 *
	 * @param radius  the corner radius the player set for this mod
	 * @param fill    the colour the player set for the box (its alpha is how strongly it shows; 0 when the player
	 *                turned the background off and the box is only there because a key is pressed)
	 * @param accent  the player's accent colour (Kairo's theme colour, or the PvP colour in PvP mode)
	 * @param seed    differs from box to box, so moving looks don't all move in step
	 * @param pressed 0 to 1: how far a key of Keystrokes is pressed down (always 0 for other boxes). {@code fill}
	 *                already blends towards the player's pressed colour
	 */
	void box(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int fill, int accent, float seed, float pressed);

	/** The look's own options, shown under the chooser and saved with the player's settings. */
	default List<Setting<?>> settings() {
		return List.of();
	}
}
