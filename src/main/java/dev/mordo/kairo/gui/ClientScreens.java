package dev.mordo.kairo.gui;

import dev.mordo.kairo.gui.debug.RenderTestScreen;
import dev.mordo.kairo.gui.menu.MenuScreen;
import net.minecraft.client.Minecraft;

/** Opens the client's screens. */
public final class ClientScreens {
	private ClientScreens() {
	}

	/** The Right Shift menu, starting on the ring. */
	public static void openRing() {
		Minecraft.getInstance().gui.setScreen(new MenuScreen());
	}

	/** Developer screen for the render foundation (Settings > About). */
	public static void openRenderTest() {
		Minecraft.getInstance().gui.setScreen(new RenderTestScreen());
	}
}
