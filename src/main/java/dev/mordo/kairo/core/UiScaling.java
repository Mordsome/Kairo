package dev.mordo.kairo.core;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;

/**
 * How big Kairo draws itself. Kairo's menus are designed to fill the screen the same way at any resolution; these
 * sizes sit on top of that:
 * <ul>
 *     <li><b>Menu size</b>: the ring menu and its panels (and the emote wheel). With "Follow GUI Scale" they also
 *     shrink when Minecraft's GUI Scale option is set below what the window could show, like Minecraft's own menus.</li>
 *     <li><b>Main menu size</b>: Kairo's main menu and the screens around it.</li>
 *     <li><b>HUD size</b>: every HUD mod together (each one still has its own size on top).</li>
 * </ul>
 * Menus never go below a size that stays comfortable to read.
 */
public final class UiScaling {
	/** The smallest and largest the menus are ever drawn, whatever the settings multiply out to. */
	private static final float MIN = 0.6f;
	private static final float MAX = 1.4f;

	private UiScaling() {
	}

	/**
	 * Minecraft's GUI Scale as a part of the biggest scale the window could show (what "Auto" picks): 1 at Auto or
	 * the top setting, less below it. Never under 0.65, so a tiny GUI Scale doesn't make Kairo unreadable.
	 */
	public static float guiScaleFactor() {
		Minecraft mc = Minecraft.getInstance();
		Window window = mc.getWindow();
		int biggest = window.calculateScale(0, mc.isEnforceUnicode());
		int now = window.getGuiScale();
		if (biggest <= 0 || now <= 0 || now >= biggest) {
			return 1;
		}
		return Math.max(0.65f, now / (float) biggest);
	}

	private static float heldMenus = -1;
	private static float heldMainMenu = -1;

	/**
	 * The ring menu, its panels and the emote wheel. While a slider is being dragged the size is held still: the Menu
	 * size slider sits in the very menu it resizes, so a live change would move the slider under the mouse, which
	 * changes the value again, and the menu would flicker between two sizes. It takes the new size when you let go.
	 */
	public static float menus() {
		ClientSettings s = ClientSettings.INSTANCE;
		float scale = s.menuScale.floatValue();
		if (s.menusFollowGuiScale.isOn()) {
			scale *= guiScaleFactor();
		}
		scale = Math.clamp(scale, MIN, MAX);
		if (dev.mordo.kairo.gui.widgets.Slider.anyDragging() && heldMenus > 0) {
			return heldMenus;
		}
		heldMenus = scale;
		return scale;
	}

	/** Kairo's main menu, worlds and servers screens (held still while a slider is dragged, like {@link #menus}). */
	public static float mainMenu() {
		float scale = Math.clamp(ClientSettings.INSTANCE.mainMenuScale.floatValue(), MIN, MAX);
		if (dev.mordo.kairo.gui.widgets.Slider.anyDragging() && heldMainMenu > 0) {
			return heldMainMenu;
		}
		heldMainMenu = scale;
		return scale;
	}

	/** Every HUD mod together. HUD mods are measured in GUI pixels, so they already follow GUI Scale by themselves. */
	public static float hud() {
		return Math.clamp(ClientSettings.INSTANCE.hudScale.floatValue(), 0.5f, 2f);
	}
}
