package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.api.Addons;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.gui.main.MainMenuScreen;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.gui.widgets.ThemePicker;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.List;

/**
 * Themes (run with build/tour-hudthemes and -Paddons=...): every theme on the main menu, the pause menu, the ring, the
 * Mods page and the HUD (W held, so one key of Keystrokes is pressed), and the picture picker in Visuals. Without the
 * addon it checks that nothing about themes shows.
 */
final class HudThemesTour {
	private static final String P = "themes_" + (Edition.isVoid() ? "void_" : "kairo_");

	void run(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		List<ThemePicker.Entry> themes = ctx.computeOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			Kairo.LOGGER.info("CHECK themes available: {}; themes: {}; menu look: {}, HUD look: {}", ThemePicker.available(),
				ThemePicker.entries().stream().map(e -> e.id() + (e.menu() != null ? "+menu" : "") + (e.hud() != null ? "+hud" : "")).toList(),
				ClientSettings.INSTANCE.menuLook.get(), ClientSettings.INSTANCE.hudLook.get());
			return ThemePicker.entries();
		});
		// The main menu in every theme.
		for (ThemePicker.Entry theme : themes) {
			ctx.runOnClient(mc -> {
				theme.apply();
				mc.gui.setScreen(new MainMenuScreen());
			});
			ctx.waitTicks(40);
			ctx.takeScreenshot(P + theme.id() + "_1_main_menu");
		}
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			world.getServer().runCommand("tp @p 0 -60 0 0 20");
			world.getServer().runCommand("time set noon");
			world.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> {
				for (String id : new String[]{"keystrokes", "cps", "fps", "coordinates", "ping", "armor_status", "direction"}) {
					var m = ModuleManager.get(id);
					if (m != null && !m.isEnabled()) {
						m.toggle();
					}
				}
				ModuleManager.syncAll();
			});
			ctx.waitTicks(20);
			for (ThemePicker.Entry theme : themes) {
				ctx.runOnClient(mc -> theme.apply());
				ctx.getInput().holdKey(InputConstants.KEY_W);
				ctx.waitTicks(6);
				ctx.takeScreenshot(P + theme.id() + "_2_hud");
				ctx.getInput().releaseKey(InputConstants.KEY_W);
				ctx.runOnClient(mc -> mc.gui.setScreen(new PauseScreen(true)));
				ctx.waitTicks(20);
				ctx.takeScreenshot(P + theme.id() + "_3_pause");
				ctx.runOnClient(mc -> mc.gui.setScreen(new MenuScreen()));
				ctx.waitTicks(30);
				ctx.takeScreenshot(P + theme.id() + "_4_ring");
				ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.openOn(MenuScreen::mods, null)));
				ctx.waitTicks(30);
				ctx.takeScreenshot(P + theme.id() + "_5_mods");
				ctx.runOnClient(mc -> mc.gui.setScreen(null));
				ctx.waitTicks(5);
			}
			if (ThemePicker.available() && !Edition.isVoid()) {
				ctx.runOnClient(mc -> {
					themes.get(Math.min(4, themes.size() - 1)).apply();
					MenuScreen menu = MenuScreen.openOn(MenuScreen::visualsPage, null);
					mc.gui.setScreen(menu);
					menu.visuals().select(section(menu, "Theme"));
				});
				ctx.waitTicks(30);
				ctx.takeScreenshot(P + "picker");
				ctx.runOnClient(mc -> mc.gui.setScreen(null));
			}
			ctx.runOnClient(mc -> themes.getFirst().apply());
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(5);
	}

	/** The index of the Visuals section with this name. */
	static int section(MenuScreen menu, String name) {
		try {
			var field = dev.mordo.kairo.gui.menu.VisualsPage.class.getDeclaredField("sections");
			field.setAccessible(true);
			List<?> sections = (List<?>) field.get(menu.visuals());
			for (int i = 0; i < sections.size(); i++) {
				var accessor = sections.get(i).getClass().getDeclaredMethod("name");
				accessor.setAccessible(true);
				if (name.equals(accessor.invoke(sections.get(i)))) {
					return i;
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
		Kairo.LOGGER.info("CHECK Visuals section '{}' not found", name);
		return 0;
	}
}
