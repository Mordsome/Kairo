package dev.mordo.kairo.test;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.mods.DropWarning;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Kairo 0.4.0: the inventory glass fixes (stray dots, the furnace's flame and arrow, the recipe book button), the
 * shortcut buttons, menu sizes, the Reconnect button, Restart in About and Drop Warning. Run with build/tour-040.
 */
final class Update040Tour {
	private static final String P = "y_";

	void run(ClientGameTestContext ctx) {
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			Kairo.LOGGER.info("CHECK 0.4.0 tour, edition void: {}", Edition.isVoid());
		});
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			TestServerContext server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode survival @p");
			server.runCommand("time set 6000");
			server.runCommand("tp @p 0 -60 0 0 20");
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			hud(ctx);
			inventory(ctx, server);
			dropWarning(ctx, server);
			menus(ctx);
		}
		reconnect(ctx);
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(10);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot(P + name);
	}

	/** The HUD at its new default size (130%), smaller and bigger: the layout scales evenly and stays on screen. */
	private void hud(ClientGameTestContext ctx) {
		var size = ClientSettings.INSTANCE.hudScale;
		ctx.runOnClient(mc -> {
			for (var name : new String[]{"keystrokes", "cps", "fps", "coordinates", "ping", "armor_status"}) {
				var m = ModuleManager.get(name);
				if (m != null && !m.isEnabled()) {
					m.toggle();
				}
			}
			dev.mordo.kairo.core.ModuleManager.syncAll();
			Kairo.LOGGER.info("CHECK HUD size default: {}", size.value());
		});
		ctx.waitTicks(5);
		shot(ctx, "12_hud_130");
		ctx.runOnClient(mc -> size.set(1.0));
		ctx.waitTicks(5);
		shot(ctx, "13_hud_100");
		ctx.runOnClient(mc -> size.set(1.7));
		ctx.waitTicks(5);
		shot(ctx, "14_hud_170");
		ctx.runOnClient(mc -> {
			int w = mc.getWindow().getGuiScaledWidth();
			int h = mc.getWindow().getGuiScaledHeight();
			boolean inside = true;
			for (var element : dev.mordo.kairo.hud.HudManager.elements()) {
				if (!dev.mordo.kairo.hud.HudManager.isShown(element)) {
					continue;
				}
				dev.mordo.kairo.hud.HudManager.prepare(element, false);
				float[] at = dev.mordo.kairo.hud.HudManager.place(element, w, h);
				inside &= at[0] >= 0 && at[1] >= 0 && at[0] + element.width() * at[2] <= w + 0.5f && at[1] + element.height() * at[2] <= h + 0.5f;
			}
			Kairo.LOGGER.info("CHECK every HUD element on screen at 170%: {}", inside);
			size.reset();
			mc.gui.setScreen(new dev.mordo.kairo.gui.layout.LayoutScreen());
		});
		ctx.waitTicks(25);
		shot(ctx, "15_layout_editor_hud_size");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	private void inventory(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("give @p minecraft:netherite_sword");
		server.runCommand("give @p minecraft:golden_apple 2");
		server.runCommand("give @p minecraft:bread 17");
		server.runCommand("give @p minecraft:raw_iron 8");
		server.runCommand("give @p minecraft:coal 12");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.gui.setScreen(new InventoryScreen(mc.player)));
		ctx.waitTicks(20);
		shot(ctx, "00_inventory");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		// A burning furnace: iron in, coal under it.
		server.runCommand("setblock 0 -60 2 minecraft:furnace[facing=north]{Items:[{Slot:0b,id:\"minecraft:raw_iron\",count:8},{Slot:1b,id:\"minecraft:coal\",count:8}]}");
		ctx.waitTicks(60);
		ctx.runOnClient(mc -> mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND,
			new BlockHitResult(new Vec3(0.5, -59.5, 2), Direction.NORTH, new BlockPos(0, -60, 2), false)));
		ctx.waitTicks(25);
		shot(ctx, "01_furnace");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	private void dropWarning(ClientGameTestContext ctx, TestServerContext server) {
		server.runCommand("clear @p");
		server.runCommand("item replace entity @p weapon.mainhand with minecraft:diamond 3");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.gameMode.dropItem(mc.player, false));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK drop warning held the first drop: still holding {}", mc.player.getMainHandItem().getCount()));
		shot(ctx, "02_drop_warning");
		ctx.runOnClient(mc -> mc.gameMode.dropItem(mc.player, false));
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			Kairo.LOGGER.info("CHECK the second drop went through: now holding {}", mc.player.getMainHandItem().getCount());
			DropWarning warning = ModuleManager.get(DropWarning.class);
			Kairo.LOGGER.info("CHECK bread is not valuable: {}", warning.valuable(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.BREAD)));
		});
	}

	private void menus(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.setScreen(new PauseScreen(true)));
		ctx.waitTicks(15);
		shot(ctx, "03_pause_shortcuts");
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.pauseModsButton.set(false);
			ClientSettings.INSTANCE.pauseVisualsButton.set(false);
			mc.gui.setScreen(new PauseScreen(true));
		});
		ctx.waitTicks(15);
		shot(ctx, "03_pause_shortcuts_two_off");
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.pauseModsButton.reset();
			ClientSettings.INSTANCE.pauseVisualsButton.reset();
		});
		ctx.runOnClient(mc -> mc.gui.setScreen(new MenuScreen()));
		ctx.waitTicks(30);
		shot(ctx, "04_ring_menu_size");
		ctx.runOnClient(mc -> {
			Kairo.LOGGER.info("CHECK menu size: setting {}, gui scale factor {}, drawn at {}", ClientSettings.INSTANCE.menuScale.value(),
				dev.mordo.kairo.core.UiScaling.guiScaleFactor(), dev.mordo.kairo.core.UiScaling.menus());
			MenuScreen menu = MenuScreen.openOn(MenuScreen::settings, null);
			mc.gui.setScreen(menu);
			menu.settings().showAbout();
		});
		ctx.waitTicks(30);
		shot(ctx, "05_about_restart");
		// Animations and Combat Effects: locked in plain Kairo without their addons, back with them.
		ctx.runOnClient(mc -> {
			var v = dev.mordo.kairo.core.GameVisuals.INSTANCE;
			Kairo.LOGGER.info("CHECK addons loaded: {}; animations there: {}, combat effects there: {}; kill effect setting reads {}",
				dev.mordo.kairo.api.Addons.loaded().size(), dev.mordo.kairo.api.Features.ANIMATIONS.has(),
				dev.mordo.kairo.api.Features.COMBAT_EFFECTS.has(), v.killEffect.get());
			MenuScreen menu = MenuScreen.openOn(MenuScreen::visualsPage, null);
			mc.gui.setScreen(menu);
			if (!Edition.isVoid()) {
				menu.visuals().select(0);
			}
		});
		ctx.waitTicks(30);
		shot(ctx, "09_visuals_first_section");
		ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.openOn(MenuScreen::screenshots, null)));
		ctx.waitTicks(40);
		shot(ctx, "11_screenshots_page");
		ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.openOn(MenuScreen::mods, null)));
		ctx.waitTicks(30);
		ctx.getInput().typeChars("time");
		ctx.waitTicks(3);
		shot(ctx, "06_mods_search_moving");
		ctx.waitTicks(20);
		shot(ctx, "07_mods_search_settled");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	private void reconnect(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.mods.Reconnect.remember(new ServerData("Test server", "play.example.invalid", ServerData.Type.OTHER));
			mc.gui.setScreen(new DisconnectedScreen(new TitleScreen(), Component.literal("Connection lost"), Component.literal("Kicked by an operator")));
		});
		ctx.waitTicks(15);
		shot(ctx, "08_reconnect");
	}
}
