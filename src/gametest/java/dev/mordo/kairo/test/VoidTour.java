package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Screenshots of Kairo Void (./gradlew runClientGameTest -Pvoid): its flat menus, trimmed wardrobe, and the
 * combat, sky and menu effects. Saved with a "void_" prefix next to the main tour's screenshots.
 */
final class VoidTour {
	private final int width = 1600;
	private final int height = 900;

	void run(ClientGameTestContext ctx) {
		TestInput input = ctx.getInput();
		input.resizeWindow(width, height);
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ClientSettings.INSTANCE.onboarded.set(true);
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		input.setCursorPos(width * 0.5, height * 0.97);
		ctx.waitTicks(60);
		ctx.takeScreenshot("void_00_main_menu");
		// The other two styles, the style picker, the welcome style step and a screen over the drawn space scene.
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.MainMenuSettings.setVoidStyle(1);
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		input.setCursorPos(width * 0.5, height * 0.52);
		ctx.waitTicks(40);
		ctx.takeScreenshot("void_02_simple");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.MainMenuSettings.setVoidStyle(2);
			dev.mordo.kairo.gui.main.MainScreens.testOverride = true;
			mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
			dev.mordo.kairo.gui.main.MainScreens.testOverride = false;
		});
		input.setCursorPos(width * 0.5, height * 0.97);
		ctx.waitTicks(40);
		ctx.takeScreenshot("void_03_minecraft_style");
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.WorldsScreen(mc.gui.screen())));
		ctx.waitTicks(30);
		ctx.takeScreenshot("void_04_worlds");
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuSettingsScreen(new dev.mordo.kairo.gui.main.MainMenuScreen())));
		ctx.waitTicks(30);
		ctx.takeScreenshot("void_05_style_picker");
		// The Void welcome, every step.
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.welcome.WelcomeScreen(null)));
		input.setCursorPos(width * 0.5, height * 0.97);
		ctx.waitTicks(60);
		ctx.takeScreenshot("void_06_welcome_0");
		for (int i = 1; i < 9; i++) {
			input.pressKey(key(InputConstants.KEY_RIGHT));
			ctx.waitTicks(20);
			ctx.takeScreenshot("void_06_welcome_" + i);
		}
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.MainMenuSettings.setVoidStyle(0);
			dev.mordo.kairo.core.ClientSettings.INSTANCE.accent.reset();
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		ctx.waitTicks(10);
		if (!quick()) {
			input.setCursorPos(width * 0.12, height * 0.43);
			ctx.waitTicks(150);
			ctx.takeScreenshot("void_00b_main_menu_later");
		}

		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(10);
			ctx.takeScreenshot("void_01_world");
			if (newOnly()) {
				new NewFeaturesTour("void_", width, height).run(ctx, world);
				return;
			}
			menus(ctx, input);
			crystals(ctx, input, world);
			blades(ctx, input, world);
			atmospheres(ctx, world);
			if (quick()) {
				return;
			}
			combat(ctx, input, world);
			sky(ctx, world);
			new NewFeaturesTour("void_", width, height).run(ctx, world);
		}
	}

	/** build/tour-new-only: the menus before the world, then only the newest features. */
	private static boolean newOnly() {
		return java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.dir")).resolve("../../tour-new-only").normalize());
	}

	/** build/tour-void-quick: only the newest parts (menus, blades, atmospheres). */
	private static boolean quick() {
		return java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.dir")).resolve("../../tour-void-quick").normalize());
	}

	/** Every crystal style, a crystal popping, and the master switch off. */
	private void crystals(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode creative @p");
		server.runCommand("time set 18000");
		server.runCommand("tp @p 0 -60 -5 0 10");
		server.runCommand("summon end_crystal 0 -60 0 {ShowBottom:1b}");
		server.runCommand("summon end_crystal 3 -60 2 {ShowBottom:0b}");
		ctx.waitTicks(20);
		for (var style : dev.mordo.kairo.core.VoidSettings.CrystalStyle.values()) {
			ctx.runOnClient(mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.crystalStyle.set(style));
			ctx.waitTicks(6);
			ctx.takeScreenshot("void_07_crystal_" + style.name().toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.crystalStyle.reset());
		server.runCommand("damage @e[type=end_crystal,limit=1,sort=nearest] 1");
		ctx.waitTicks(2);
		ctx.takeScreenshot("void_08_crystal_pop_a");
		ctx.waitTicks(5);
		ctx.takeScreenshot("void_08_crystal_pop_b");
		server.runCommand("kill @e[type=end_crystal]");
		// The master switch off: plain Minecraft, and the ring shows it.
		ctx.runOnClient(mc -> dev.mordo.kairo.render.Visuals.toggle());
		ctx.waitTicks(5);
		input.setCursorPos(width / 2.0, height / 2.0);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(20);
		ctx.takeScreenshot("void_09_ring_effects_off");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> dev.mordo.kairo.render.Visuals.toggle());
		server.runCommand("time set 6000");
		ctx.waitTicks(5);
	}

	/** The energy blade on the sword, a few moments into a swing (real time, so roughly). */
	private void blades(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode survival @p");
		server.runCommand("time set 6000");
		server.runCommand("tp @p 0 -60 0 0 0");
		server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
		ctx.waitTicks(30);
		// Swing, then pin the hand clock at points through the swing (vanilla style, then Slash style).
		for (String style : new String[]{"VANILLA", "SLASH"}) {
			ctx.runOnClient(mc -> dev.mordo.kairo.core.GameVisuals.INSTANCE.swingStyle.set(
				dev.mordo.kairo.core.GameVisuals.SwingStyle.valueOf(style)));
			long[] s0 = new long[1];
			ctx.runOnClient(mc -> {
				s0[0] = System.nanoTime();
				dev.mordo.kairo.render.HandAnimations.testClock = s0[0];
			});
			input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(1);
			for (int ms : new int[]{25, 50, 90}) {
				ctx.runOnClient(mc -> dev.mordo.kairo.render.HandAnimations.testClock = s0[0] + ms * 1_000_000L);
				ctx.waitTicks(1);
				ctx.takeScreenshot("void_19_blade_" + style.toLowerCase(java.util.Locale.ROOT) + "_" + ms);
			}
			ctx.runOnClient(mc -> dev.mordo.kairo.render.HandAnimations.testClock = 0);
			ctx.waitTicks(25);
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.core.GameVisuals.INSTANCE.swingStyle.reset());
		// HUD styles, with a key held (pop and ripple on the W key).
		for (var style : dev.mordo.kairo.core.VoidSettings.HudStyle.values()) {
			ctx.runOnClient(mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.hudStyle.set(style));
			input.holdKey(key(InputConstants.KEY_W));
			ctx.waitTicks(2);
			ctx.takeScreenshot("void_18_hud_" + style.name().toLowerCase(java.util.Locale.ROOT));
			input.releaseKey(key(InputConstants.KEY_W));
			ctx.waitTicks(10);
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.hudStyle.reset());
		server.runCommand("tp @p 0 -60 0 0 0");
	}

	/** Every atmosphere, by day (blood moon at night), over a little build so snow and light show on blocks. */
	private void atmospheres(ClientGameTestContext ctx, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamerule advance_time false");
		server.runCommand("weather clear");
		server.runCommand("fill -5 -60 7 5 -58 8 stone_bricks");
		server.runCommand("fill -3 -57 7 3 -57 8 oak_planks");
		server.runCommand("fill 6 -60 4 8 -59 6 oak_leaves");
		server.runCommand("fill -8 -60 3 -7 -60 4 dirt_path");
		server.runCommand("gamemode spectator @p");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidSky.testTime = 10.3f);
		var A = dev.mordo.kairo.core.VoidSettings.Atmosphere.class;
		String[][] shots = {
			{"BLIZZARD", "6000", "0", "38"}, {"GALAXY", "6000", "20", "-20"}, {"BLOOD_MOON", "18000", "219", "-25"},
			{"INFERNO", "6000", "0", "8"}, {"SAKURA", "6000", "0", "8"}, {"TOXIC", "6000", "0", "8"}, {"ABYSS", "6000", "0", "25"},
			{"SYNTHWAVE", "13000", "76", "-4"}, {"STORM", "6000", "0", "0"},
		};
		for (String[] shot : shots) {
			server.runCommand("time set " + shot[1]);
			server.runCommand("tp @p 0 -57.5 0 " + shot[2] + " " + shot[3]);
			ctx.runOnClient(mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.atmosphere.set(Enum.valueOf(A, shot[0])));
			ctx.waitTicks(45);
			ctx.takeScreenshot("void_40_atmo_" + shot[0].toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.VoidSettings.INSTANCE.atmosphere.reset();
			dev.mordo.kairo.render.VoidSky.testTime = -1;
		});
		ctx.waitTicks(5);
	}

	/** Noon haze, a sunset, and the night sky in a few directions. */
	private void sky(ClientGameTestContext ctx, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamerule advance_time false");
		server.runCommand("weather clear");
		server.runCommand("gamemode spectator @p");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidSky.testTime = 10.3f);
		String[][] shots = {
			{"6000", "0", "0", "void_30_noon_haze"},
			{"12600", "90", "-5", "void_31_sunset_west"},
			{"12600", "-90", "-20", "void_32_sunset_east"},
			{"18000", "180", "-25", "void_33_aurora_north"},
			{"18000", "221", "-17", "void_34_planet"},
			{"18000", "30", "-55", "void_35_night_up"},
			{"18000", "300", "-35", "void_36_night_west"},
			{"23200", "-90", "-5", "void_37_sunrise"},
		};
		for (String[] shot : shots) {
			server.runCommand("time set " + shot[0]);
			server.runCommand("tp @p 0 -56 0 " + shot[1] + " " + shot[2]);
			ctx.waitTicks(12);
			ctx.takeScreenshot(shot[3]);
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidSky.testTime = -1);
	}

	/** Slashes in the air, a hit with shockwave and flash, a falling crit (double slash), a totem pop. */
	private void combat(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("gamemode survival @p");
		server.runCommand("time set 6000");
		server.runCommand("tp @p 0 -60 0 0 0");
		server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
		ctx.waitTicks(30);
		// Air swing.
		long[] a0 = new long[1];
		ctx.runOnClient(mc -> {
			a0[0] = System.nanoTime();
			dev.mordo.kairo.render.VoidFx.testClock = a0[0];
		});
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		for (int ms : new int[]{40, 100, 180, 300}) {
			ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = a0[0] + ms * 1_000_000L);
			ctx.waitTicks(1);
			ctx.takeScreenshot("void_20_slash_" + ms + "ms");
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = 0);
		ctx.waitTicks(25);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		ctx.takeScreenshot("void_22_slash_other_way");
		ctx.waitTicks(25);

		// A hit on a husk, with the effect clock pinned so each picture shows a known moment.
		server.runCommand("summon husk 0 -60 2.4 {NoAI:1b,Health:200f,Attributes:[{id:\"minecraft:max_health\",base:200}],Rotation:[180f,0f]}");
		input.lookAt(new net.minecraft.core.BlockPos(0, -59, 2));
		ctx.waitTicks(25);
		long[] t0 = new long[1];
		ctx.runOnClient(mc -> {
			t0[0] = System.nanoTime();
			dev.mordo.kairo.render.VoidFx.testClock = t0[0];
		});
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = t0[0] + 30_000_000L);
		ctx.waitTicks(1);
		ctx.takeScreenshot("void_23_hit_30ms");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = t0[0] + 120_000_000L);
		ctx.waitTicks(1);
		ctx.takeScreenshot("void_24_hit_120ms");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = t0[0] + 250_000_000L);
		ctx.waitTicks(1);
		ctx.takeScreenshot("void_25_hit_250ms");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = 0);
		ctx.waitTicks(25);
		// A critical hit while falling.
		server.runCommand("tp @p 0 -58.2 0");
		ctx.waitTicks(3);
		input.lookAt(new net.minecraft.core.BlockPos(0, -60, 2));
		ctx.runOnClient(mc -> {
			t0[0] = System.nanoTime();
			dev.mordo.kairo.render.VoidFx.testClock = t0[0];
		});
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = t0[0] + 110_000_000L);
		ctx.waitTicks(1);
		ctx.takeScreenshot("void_26_crit_110ms");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = t0[0] + 220_000_000L);
		ctx.waitTicks(1);
		ctx.takeScreenshot("void_27_crit_220ms");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidFx.testClock = 0);
		ctx.waitTicks(30);
		server.runCommand("kill @e[type=husk]");

		// Totem pop.
		ctx.runOnClient(mc -> mc.player.displayItemActivation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)));
		ctx.waitTicks(3);
		ctx.takeScreenshot("void_28_totem_a");
		ctx.waitTicks(8);
		ctx.takeScreenshot("void_29_totem_b");
		ctx.waitTicks(30);
	}

	private void menus(ClientGameTestContext ctx, TestInput input) {
		input.setCursorPos(width / 2.0, height / 2.0);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(20);
		ctx.takeScreenshot("void_10_ring");
		input.setCursorPos(width / 2.0, height / 2.0 - 200);
		ctx.waitTicks(10);
		ctx.takeScreenshot("void_11_ring_hover");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);

		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visualsPage, null)));
		input.setCursorPos(width * 0.95, height * 0.95);
		ctx.waitTicks(30);
		ctx.takeScreenshot("void_12_visuals");
		for (int i = 1; i <= 8; i++) {
			int index = i;
			ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).voidVisuals().select(index));
			ctx.waitTicks(16);
			ctx.takeScreenshot("void_12_visuals_" + i);
		}
		// Hover a card, then open one card's options.
		input.setCursorPos(width * 0.42, height * 0.3);
		ctx.waitTicks(10);
		ctx.takeScreenshot("void_12_visuals_hover");
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(20);
		ctx.takeScreenshot("void_12_visuals_detail");
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::mods, null)));
		ctx.waitTicks(30);
		ctx.takeScreenshot("void_13_mods");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.cosmetics.Cosmetics.get().cape.set("design:galaxy");
			mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::cosmetics, null));
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("void_14_cosmetics");
		ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).cosmetics().select(1));
		ctx.waitTicks(10);
		ctx.takeScreenshot("void_15_emotes");
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.cosmetics.EmoteWheelScreen()));
		input.setCursorPos(width / 2.0 + 180, height / 2.0);
		ctx.waitTicks(20);
		ctx.takeScreenshot("void_16_emote_wheel");
		ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.PauseScreen(true)));
		ctx.waitTicks(20);
		ctx.takeScreenshot("void_17_pause");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	private static InputConstants.Key key(int code) {
		return InputConstants.Type.KEYBOARD.getOrCreate(code);
	}
}
