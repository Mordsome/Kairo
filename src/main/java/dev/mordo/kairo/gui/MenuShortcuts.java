package dev.mordo.kairo.gui;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.gui.menu.MenuScreen;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;

/**
 * Ways into Kairo's menus besides the ring (Right Shift): small buttons in the pause menu's top-left corner (Kairo,
 * Mods, Visuals, Cosmetics) and a Cosmetics button in the inventory's top-left corner, out of the way of every
 * panel. Each one has its own switch in Visuals → Menus. Every page also has its own key (Settings →
 * Keybinds), unbound by default.
 */
public final class MenuShortcuts {
	private MenuShortcuts() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ClientSettings s = ClientSettings.INSTANCE;
			if (screen instanceof PauseScreen pause && pause.showsPauseMenu()) {
				int x = 6;
				if (s.pauseKairoButton.isOn()) {
					x = add(screen, "Kairo", x, ClientScreens::openRing);
				}
				if (s.pauseModsButton.isOn()) {
					x = add(screen, "Mods", x, () -> open(MenuScreen::mods));
				}
				if (s.pauseVisualsButton.isOn()) {
					x = add(screen, "Visuals", x, () -> open(MenuScreen::visualsPage));
				}
				if (s.pauseCosmeticsButton.isOn()) {
					add(screen, "Cosmetics", x, () -> open(MenuScreen::cosmetics));
				}
			} else if ((screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen) && s.inventoryCosmeticsButton.isOn()) {
				// Top-left: the top-right is where Minecraft's "New recipe" and advancement pop-ups appear.
				add(screen, "Cosmetics", 6, () -> open(MenuScreen::cosmetics));
			}
		});
	}

	private static int add(net.minecraft.client.gui.screens.Screen screen, String label, int x, Runnable action) {
		int w = Minecraft.getInstance().font.width(label) + 16;
		Screens.getWidgets(screen).add(Button.builder(Component.literal(label), b -> action.run()).bounds(x, 6, w, 18).build());
		return x + w + 4;
	}

	/** Opens the menu straight onto a page; closing it goes back to the game. */
	public static void open(java.util.function.Function<MenuScreen, dev.mordo.kairo.gui.menu.Page> page) {
		Minecraft.getInstance().gui.setScreen(MenuScreen.openOn(page, null));
	}
}
