package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.api.Addons;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.gui.widgets.ThemePicker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;

/**
 * The home menu socket with the Command Bar addon (run with build/tour-commandbar and -Paddons=...; Themes too for
 * the themed shots): the bar with its tiles, a search, Tab switching a mod, Enter opening it, Back leading home, and
 * the chooser in Visuals → Ring. Without the addon, Right Shift's menu is the ring.
 */
final class CommandBarTour {
	private static final String P = "cbar_" + (Edition.isVoid() ? "void_" : "kairo_");

	void run(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			Kairo.LOGGER.info("CHECK home menus: {}; chosen: {}; active: {}", Addons.homeMenuChoices().stream().map(c -> c.id()).toList(),
				ClientSettings.INSTANCE.homeMenu.get(), Addons.activeHomeMenu() == null ? "ring" : Addons.activeHomeMenu().id());
		});
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			world.getServer().runCommand("tp @p 0 -60 0 0 20");
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(10);
			ctx.runOnClient(mc -> mc.gui.setScreen(new MenuScreen()));
			ctx.waitTicks(25);
			ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK Right Shift's menu opens on: {}",
				((MenuScreen) mc.gui.screen()).home().getClass().getSimpleName()));
			ctx.takeScreenshot(P + "1_home");
			if (Addons.activeHomeMenu() == null) {
				ctx.runOnClient(mc -> mc.gui.setScreen(null));
				return;
			}
			var input = ctx.getInput();
			// Click the second tile with the mouse (where the bar lays it out: 22% down, then the bar and a gap).
			double[] at = ctx.computeOnClient(mc -> {
				MenuScreen menu = (MenuScreen) mc.gui.screen();
				double unit = menu.unit() * mc.getWindow().getGuiScale();
				double vw = mc.getWindow().getWidth() / unit;
				double vh = mc.getWindow().getHeight() / unit;
				double w = Math.min(920, vw * 0.64);
				double x = (vw - w) / 2;
				int n = dev.mordo.kairo.gui.menu.RingSection.active().length;
				int cols = n <= 4 ? n : (n + 1) / 2;
				double tw = (w - 12 * (cols - 1)) / cols;
				double top = vh * 0.22 + 64 + 18;
				return new double[]{(x + tw + 12 + tw / 2) * unit, (top + 62) * unit};
			});
			input.setCursorPos(at[0], at[1]);
			ctx.waitTicks(3);
			input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(20);
			ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK clicking the second tile opened: {}",
				((MenuScreen) mc.gui.screen()).currentPage().getClass().getSimpleName()));
			ctx.takeScreenshot(P + "1b_clicked_tile_2");
			ctx.runOnClient(mc -> {
				MenuScreen menu = (MenuScreen) mc.gui.screen();
				menu.navigate(menu.home());
			});
			ctx.waitTicks(20);
			input.typeChars("keys");
			ctx.waitTicks(8);
			ctx.takeScreenshot(P + "2_search_keys");
			boolean before = ctx.computeOnClient(mc -> ModuleManager.get("keystrokes").isEnabled());
			input.pressKey(InputConstants.KEY_TAB);
			ctx.waitTicks(4);
			boolean after = ctx.computeOnClient(mc -> ModuleManager.get("keystrokes").isEnabled());
			ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK Tab switched Keystrokes: {} -> {}", before, after));
			input.pressKey(InputConstants.KEY_TAB);
			input.pressKey(InputConstants.KEY_ESCAPE);
			ctx.waitTicks(4);
			input.typeChars("sky");
			ctx.waitTicks(8);
			ctx.takeScreenshot(P + "3_search_sky");
			input.pressKey(InputConstants.KEY_ESCAPE);
			input.typeChars("keys");
			ctx.waitTicks(4);
			input.pressKey(InputConstants.KEY_RETURN);
			ctx.waitTicks(25);
			ctx.takeScreenshot(P + "4_enter_opened_keystrokes");
			ctx.runOnClient(mc -> {
				MenuScreen menu = (MenuScreen) mc.gui.screen();
				menu.navigate(menu.home());
			});
			ctx.waitTicks(25);
			ctx.takeScreenshot(P + "5_back_home");
			ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK after Back the search is empty and the menu is still open: {}",
				mc.gui.screen() instanceof MenuScreen));
			input.typeChars("2");
			ctx.waitTicks(25);
			ctx.takeScreenshot(P + "6_number_2");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);
			// In themes, if Themes is there too.
			for (String theme : new String[]{"neon", "retro", "minimal"}) {
				ctx.runOnClient(mc -> ThemePicker.entries().stream().filter(e -> e.id().equals(theme)).findFirst().ifPresent(ThemePicker.Entry::apply));
				ctx.runOnClient(mc -> mc.gui.setScreen(new MenuScreen()));
				ctx.waitTicks(20);
				ctx.getInput().typeChars("cps");
				ctx.waitTicks(8);
				ctx.takeScreenshot(P + "7_theme_" + theme);
				ctx.runOnClient(mc -> mc.gui.setScreen(null));
				ctx.waitTicks(4);
			}
			// Retro with a gold frame, on the pause menu.
			ctx.runOnClient(mc -> {
				for (var setting : Addons.menuLookSettings()) {
					if (setting.id().equals("themes_retro_border") && setting instanceof dev.mordo.kairo.core.settings.EnumSetting<?> e) {
						setEnum(e, "GOLD");
					}
				}
				ThemePicker.entries().stream().filter(e -> e.id().equals("retro")).findFirst().ifPresent(ThemePicker.Entry::apply);
				mc.gui.setScreen(new PauseScreen(true));
			});
			ctx.waitTicks(20);
			ctx.takeScreenshot(P + "8_retro_gold_pause");
			ctx.runOnClient(mc -> {
				MenuScreen menu = MenuScreen.openOn(MenuScreen::visualsPage, null);
				mc.gui.setScreen(menu);
				if (!Edition.isVoid()) {
					menu.visuals().select(HudThemesTour.section(menu, "Ring"));
				}
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot(P + "9_visuals_ring_chooser");
			ctx.runOnClient(mc -> {
				mc.gui.setScreen(null);
				ThemePicker.entries().getFirst().apply();
			});
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(5);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setEnum(dev.mordo.kairo.core.settings.EnumSetting setting, String name) {
		for (Object value : setting.getDefault().getClass().getEnumConstants()) {
			if (((Enum<?>) value).name().equals(name)) {
				setting.set(value);
			}
		}
	}
}
