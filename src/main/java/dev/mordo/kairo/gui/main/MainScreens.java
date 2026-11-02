package dev.mordo.kairo.gui.main;

import dev.mordo.kairo.core.ClientSettings;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.jspecify.annotations.Nullable;

/**
 * Swaps Minecraft's title, world list and server list screens for the client's own, wherever the game opens them
 * (start-up, leaving a world, "Back to server list" after a disconnect...). Turned off in Visuals → Client main menu.
 */
public final class MainScreens {
	/** Fabric's client game tests wait for the vanilla title screen, so leave it alone there. */
	private static final boolean GAME_TEST = System.getProperty("fabric.client.gametest") != null;
	/** Lets the visual tour check the real swap for a moment (it's off during game tests otherwise). */
	public static boolean testOverride;

	private MainScreens() {
	}

	public static @Nullable Screen replace(@Nullable Screen screen) {
		if (screen == null || (GAME_TEST && !testOverride)) {
			return screen;
		}
		// First launch: the welcome setup comes before any title screen.
		if (screen.getClass() == TitleScreen.class && !ClientSettings.INSTANCE.onboarded.isOn()) {
			return new dev.mordo.kairo.gui.welcome.WelcomeScreen(null);
		}
		if (!ClientSettings.INSTANCE.customMainMenu.isOn()) {
			return screen;
		}
		if (screen.getClass() == TitleScreen.class) {
			return new MainMenuScreen();
		}
		if (screen.getClass() == SelectWorldScreen.class) {
			return new WorldsScreen(ScreenParents.parentOf(screen));
		}
		if (screen.getClass() == JoinMultiplayerScreen.class) {
			return new ServersScreen(ScreenParents.parentOf(screen));
		}
		return screen;
	}
}
