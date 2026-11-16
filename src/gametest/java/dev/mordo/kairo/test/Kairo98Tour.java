package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.api.Addons;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.gui.main.MainMenuScreen;
import dev.mordo.kairo.gui.menu.MenuScreen;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;

/**
 * Kairo 98 (run with build/tour-kairo98 and -Paddons=...): which addons loaded (a clashing one stays off), the
 * desktop main menu, the Start menu, Kairo's pages, the pause menu, the inventory, a chest and the HUD.
 */
final class Kairo98Tour {
	private static final String P = "k98_" + (Edition.isVoid() ? "void_" : "kairo_");

	void run(ClientGameTestContext ctx) {
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			Kairo.LOGGER.info("CHECK addons loaded: {}", Addons.loaded().stream().map(Addons.Loaded::id).toList());
			Kairo.LOGGER.info("CHECK menu look: {}, HUD look: {}, Right Shift opens: {}, look pack active: {}", ClientSettings.INSTANCE.menuLook.get(),
				ClientSettings.INSTANCE.hudLook.get(), ClientSettings.INSTANCE.homeMenu.get(), dev.mordo.kairo.gui.LookPack.active());
			mc.gui.setScreen(new MainMenuScreen());
		});
		ctx.waitTicks(40);
		ctx.takeScreenshot(P + "1_main_menu");
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			world.getServer().runCommand("tp @p 0 -60 0 0 20");
			world.getServer().runCommand("time set noon");
			world.getServer().runCommand("give @p diamond_sword");
			world.getServer().runCommand("give @p bread 12");
			world.getServer().runCommand("give @p oak_planks 40");
			world.getServer().runCommand("experience add @p 5 levels");
			world.getServer().runCommand("setblock 1 -60 2 chest");
			world.getConnection().waitForChunksRender();
			ctx.runOnClient(mc -> {
				for (String id : new String[]{"keystrokes", "fps", "coordinates"}) {
					var m = ModuleManager.get(id);
					if (m != null && !m.isEnabled()) {
						m.toggle();
					}
				}
				ModuleManager.syncAll();
			});
			ctx.waitTicks(20);
			ctx.getInput().holdKey(InputConstants.KEY_W);
			ctx.waitTicks(4);
			ctx.takeScreenshot(P + "2_hud");
			ctx.getInput().releaseKey(InputConstants.KEY_W);
			ctx.runOnClient(mc -> mc.gui.setScreen(new MenuScreen()));
			ctx.waitTicks(30);
			ctx.takeScreenshot(P + "3_start_menu");
			ctx.getInput().pressKey(InputConstants.KEY_DOWN);
			ctx.getInput().pressKey(InputConstants.KEY_DOWN);
			ctx.waitTicks(4);
			ctx.takeScreenshot(P + "3b_start_menu_picked");
			ctx.getInput().pressKey(InputConstants.KEY_RETURN);
			ctx.waitTicks(30);
			ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK Enter on the second item opened: {}",
				mc.gui.screen() instanceof MenuScreen m ? m.currentPage().getClass().getSimpleName() : String.valueOf(mc.gui.screen())));
			ctx.takeScreenshot(P + "4_settings_page");
			ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.openOn(MenuScreen::mods, null)));
			ctx.waitTicks(30);
			ctx.takeScreenshot(P + "5_mods_page");
			ctx.runOnClient(mc -> {
				MenuScreen menu = MenuScreen.openOn(MenuScreen::visualsPage, null);
				mc.gui.setScreen(menu);
				if (!Edition.isVoid()) {
					menu.visuals().select(HudThemesTour.section(menu, "Theme"));
				}
			});
			ctx.waitTicks(30);
			ctx.takeScreenshot(P + "6_visuals_theme");
			ctx.runOnClient(mc -> mc.gui.setScreen(new PauseScreen(true)));
			ctx.waitTicks(20);
			ctx.takeScreenshot(P + "7_pause");
			ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
			ctx.waitTicks(15);
			ctx.takeScreenshot(P + "8_inventory");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			world.getServer().runCommand("item replace block 1 -60 2 container.0 with gold_ingot 7");
			ctx.runOnClient(mc -> {
				var hit = new net.minecraft.world.phys.BlockHitResult(new net.minecraft.world.phys.Vec3(1.5, -59.5, 2.5),
					net.minecraft.core.Direction.UP, new net.minecraft.core.BlockPos(1, -60, 2), false);
				mc.gameMode.useItemOn(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND, hit);
			});
			ctx.waitTicks(15);
			ctx.takeScreenshot(P + "9_chest");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);
			extras(ctx);
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(5);
	}

	/** The newer parts: the Visuals tab, window buttons, HUD frames, choppy swings, live colour schemes, the boot screen. */
	private void extras(ClientGameTestContext ctx) {
		if (Edition.isVoid()) {
			return;
		}
		// The Theme section (the colour sample) and Kairo 98's own tab.
		ctx.runOnClient(mc -> {
			MenuScreen menu = MenuScreen.openOn(MenuScreen::visualsPage, null);
			mc.gui.setScreen(menu);
			menu.visuals().select(HudThemesTour.section(menu, "Kairo 98"));
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot(P + "10_visuals_tab");
		// Window buttons with the real mouse: maximise, then maximise again, then close.
		for (String which : new String[]{"maximize", "maximize", "close"}) {
			double[] at = ctx.computeOnClient(mc -> buttonCentre(mc, which));
			if (at == null) {
				ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK window button {} not found", which));
				break;
			}
			ctx.getInput().setCursorPos(at[0], at[1]);
			ctx.waitTicks(2);
			ctx.getInput().pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(15);
			ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK after {}: maximized {}, menu open {}", which,
				dev.mordo.kairo.gui.menu.PanelPage.maximized, mc.gui.screen() instanceof MenuScreen));
			if (which.equals("maximize") && dev.mordo.kairo.gui.menu.PanelPage.maximized) {
				ctx.takeScreenshot(P + "11_maximized");
			}
		}
		// Simple HUD frames, and the choppy swing steps.
		ctx.runOnClient(mc -> {
			mc.gui.setScreen(null);
			setEnum("kairo98_hud_boxes", "SIMPLE");
			setting("kairo98_retro_animations").set(cast(true));
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot(P + "12_hud_simple");
		ctx.runOnClient(mc -> {
			try {
				var choppy = Class.forName("dev.mordo.kairo.addons.k98.Kairo98").getMethod("choppy", float.class);
				StringBuilder steps = new StringBuilder();
				for (float p = 0.05f; p < 1; p += 0.1f) {
					steps.append(String.format(java.util.Locale.ROOT, "%.2f->%.2f ", p, (float) choppy.invoke(null, p)));
				}
				Kairo.LOGGER.info("CHECK choppy swing (4 steps): {}", steps);
			} catch (ReflectiveOperationException e) {
				throw new RuntimeException(e);
			}
			setEnum("kairo98_hud_boxes", "TITLE_BAR");
			setting("kairo98_retro_animations").set(cast(false));
		});
		// Another colour scheme: no reload (no loading overlay), the inventory takes the new colours.
		ctx.runOnClient(mc -> setEnum("kairo98_scheme", "HIGH_CONTRAST"));
		boolean[] overlay = {false};
		for (int i = 0; i < 80; i++) {
			ctx.waitTicks(1);
			if (ctx.computeOnClient(mc -> mc.gui.overlay() != null)) {
				overlay[0] = true;
			}
		}
		ctx.runOnClient(mc -> {
			Kairo.LOGGER.info("CHECK scheme change showed a loading screen: {}", overlay[0]);
			mc.gui.setScreen(new InventoryScreen(mc.player));
		});
		ctx.waitTicks(15);
		ctx.takeScreenshot(P + "13_high_contrast_inventory");
		ctx.runOnClient(mc -> {
			mc.gui.setScreen(null);
			setEnum("kairo98_scheme", "CLASSIC");
		});
		ctx.waitTicks(40);
		// The start-up screen: a resource reload shows it.
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(3);
		ctx.takeScreenshot(P + "14_boot_screen");
		ctx.waitFor(mc -> mc.gui.overlay() == null, 1200);
	}

	@SuppressWarnings("unchecked")
	private static <T> T cast(Object o) {
		return (T) o;
	}

	@SuppressWarnings({"rawtypes"})
	private static dev.mordo.kairo.core.settings.Setting setting(String id) {
		for (var holder : ModuleManager.allHolders()) {
			for (var setting : holder.settings()) {
				if (setting.id().equals(id)) {
					return setting;
				}
			}
		}
		throw new IllegalStateException("No setting " + id);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static void setEnum(String id, String name) {
		dev.mordo.kairo.core.settings.Setting setting = setting(id);
		for (Object value : setting.getDefault().getClass().getEnumConstants()) {
			if (((Enum<?>) value).name().equals(name)) {
				setting.set(value);
			}
		}
	}

	/** The centre of a window button in window pixels, from where the look drew it last frame. */
	private static double[] buttonCentre(net.minecraft.client.Minecraft mc, String which) {
		if (!(mc.gui.screen() instanceof MenuScreen menu)) {
			return null;
		}
		var look = Addons.activeLook();
		double unit = menu.unit() * mc.getWindow().getGuiScale();
		double vw = mc.getWindow().getWidth() / unit;
		double vh = mc.getWindow().getHeight() / unit;
		for (double y = 0; y < vh * 0.3; y += 2) {
			for (double x = vw * 0.6; x < vw; x += 2) {
				if (which.equals(look.windowButton((float) x, (float) y))) {
					return new double[]{(x + 3) * unit, (y + 3) * unit};
				}
			}
		}
		return null;
	}
}
