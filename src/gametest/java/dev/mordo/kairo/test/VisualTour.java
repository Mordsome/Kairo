package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.keybinds.ClientKeybinds;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.gui.ClientScreens;
import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

/**
 * Walks through the client's screens in a fresh world and saves screenshots
 * (build/run/clientGameTest/screenshots) so visuals can be checked without clicking around by hand.
 * Run with: ./gradlew runClientGameTest
 */
public class VisualTour implements FabricClientGameTest {
	private int width = 1600;
	private int height = 900;

	@Override
	public void runTest(ClientGameTestContext ctx) {
		TestInput input = ctx.getInput();
		input.resizeWindow(width, height);
		if (ShowcaseTour.flag("tour-020")) {
			new Update020Tour().run(ctx);
			return;
		}
		if (ShowcaseTour.flag("tour-showcase")) {
			new ShowcaseTour().run(ctx);
			return;
		}
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			new VoidTour().run(ctx);
			return;
		}
		// The Kairo loading screen (a resource reload shows it over the current screen).
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(14);
		ctx.takeScreenshot("p14_00_loading_screen");
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.waitTicks(5);
		boolean updateOnly = flag("tour-update-only");
		if (updateOnly) {
			updateMenuTour(ctx, input);
		}
		boolean visualsOnly = flag("tour-visuals-only") || updateOnly;
		boolean cosmeticsOnly = flag("tour-cosmetics-only") || visualsOnly;
		if (!visualsOnly) {
			welcomeTour(ctx, input);
		}
		if (!cosmeticsOnly) {
			mainMenuTour(ctx, input);
		}
		if (java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.dir")).resolve("../../tour-menu-only").normalize())) {
			return; // quick runs while working on the menus
		}
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			// The test window usually isn't focused; don't let vanilla open the pause menu over the screenshots.
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			buildScenery(world);
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(10);
			ctx.takeScreenshot("00_world");

			ctx.runOnClient(mc -> ClientScreens.openRenderTest());
			ctx.waitTicks(30);
			ctx.takeScreenshot("01_render_test");
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			ctx.waitTicks(5);

			if (flag("tour-new-only")) {
				new NewFeaturesTour("", width, height).run(ctx, world);
				return;
			}
			if (updateOnly) {
				updateWorldTour(ctx, input, world);
				return;
			}
			visualsTour(ctx, input, world);
			if (visualsOnly) {
				return;
			}
			cosmeticsTour(ctx, input, world);
			if (cosmeticsOnly) {
				return;
			}
			ringTour(ctx, input, "1");
			panelTour(ctx, input);
			hudTour(ctx, input, world);
			featureTour(ctx, input, world);
			new NewFeaturesTour("", width, height).run(ctx, world);
			pvpTour(ctx, input);
			waypointsTour(ctx, input);
			pvpOptionsTour(ctx, input);
			hotbarTour(ctx, input, world);
			phase12Tour(ctx, input, world);
			phase8WorldTour(ctx, input, world);

			// In-game PvP keybind shows a toast.
			ctx.runOnClient(mc -> ClientKeybinds.INSTANCE.togglePvp.set(Keybind.keyboard(InputConstants.KEY_K)));
			input.pressKey(key(InputConstants.KEY_K));
			ctx.waitTicks(6);
			ctx.takeScreenshot("19_pvp_toast");
			input.pressKey(key(InputConstants.KEY_K));
			ctx.waitTicks(40);

			// Smaller window: everything should scale with the height.
			width = 1280;
			height = 720;
			input.resizeWindow(width, height);
			ctx.waitTicks(10);
			input.pressKey(key(InputConstants.KEY_RSHIFT));
			ctx.waitTicks(20);
			ctx.takeScreenshot("20_ring_720p");
			input.pressKey(key(InputConstants.KEY_ESCAPE));
			ctx.waitTicks(10);
		}

		// Back at the title: now there's a saved world for the worlds screen and "Jump back in".
		width = 1600;
		height = 900;
		input.resizeWindow(width, height);
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen()));
		ctx.waitTicks(50);
		ctx.takeScreenshot("p8_20_main_menu_with_world");
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.WorldsScreen(new net.minecraft.client.gui.screens.TitleScreen())));
		ctx.waitTicks(40);
		moveTo(input, 400, 175);
		ctx.waitTicks(12);
		ctx.takeScreenshot("p8_21_worlds_with_world");
		ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
		ctx.waitTicks(10);
	}

	private void ringTour(ClientGameTestContext ctx, TestInput input, String prefix) {
		input.setCursorPos(width / 2.0, height / 2.0);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(2);
		ctx.takeScreenshot(prefix + "0_ring_opening");
		ctx.waitTicks(20);
		ctx.takeScreenshot(prefix + "1_ring_idle");

		hoverSegment(input, 0);
		ctx.waitTicks(8);
		ctx.takeScreenshot(prefix + "2_ring_hover_mods");

		hoverSegment(input, 2);
		ctx.waitTicks(8);
		ctx.takeScreenshot(prefix + "3_ring_hover_pvp");

		input.pressKey(key(InputConstants.KEY_P));
		ctx.waitTicks(10);
		ctx.takeScreenshot(prefix + "4_ring_pvp_on");

		input.setCursorPos(width / 2.0, height / 2.0);
		ctx.waitTicks(8);
		ctx.takeScreenshot(prefix + "5_ring_pvp_idle");

		input.pressKey(key(InputConstants.KEY_P));
		ctx.waitTicks(4);
		input.pressKey(key(InputConstants.KEY_TAB));
		input.pressKey(key(InputConstants.KEY_TAB));
		ctx.waitTicks(8);
		ctx.takeScreenshot(prefix + "6_ring_keyboard_settings");

		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(1);
		ctx.takeScreenshot(prefix + "7_ring_closing");
		ctx.waitTicks(10);
		ctx.takeScreenshot(prefix + "8_ring_closed");
	}

	/** Mods panel, per-mod settings, popups, Visuals and Settings. Positions are in reference units (1440x810). */
	private void panelTour(ClientGameTestContext ctx, TestInput input) {
		input.setCursorPos(width / 2.0, height / 2.0);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(15);
		input.pressKey(key(InputConstants.KEY_1));
		ctx.waitTicks(6);
		ctx.takeScreenshot("30_mods_opening");
		ctx.waitTicks(14);
		ctx.takeScreenshot("31_mods_grid");
		// The other mod list layouts.
		var modsLayout = dev.mordo.kairo.core.ClientSettings.INSTANCE.modsLayout;
		ctx.runOnClient(mc -> modsLayout.set(dev.mordo.kairo.core.ClientSettings.ModsLayout.LARGE));
		ctx.waitTicks(12);
		ctx.takeScreenshot("31a_mods_large");
		ctx.runOnClient(mc -> modsLayout.set(dev.mordo.kairo.core.ClientSettings.ModsLayout.LIST));
		ctx.waitTicks(12);
		ctx.takeScreenshot("31b_mods_list");
		ctx.runOnClient(mc -> modsLayout.set(dev.mordo.kairo.core.ClientSettings.ModsLayout.TINY));
		ctx.waitTicks(12);
		ctx.takeScreenshot("31c_mods_tiny");
		ctx.runOnClient(mc -> modsLayout.reset());
		ctx.waitTicks(12);

		moveTo(input, 546, 229);
		ctx.waitTicks(6);
		ctx.takeScreenshot("32_mods_card_hover");

		input.typeChars("co");
		ctx.waitTicks(12);
		ctx.takeScreenshot("33_mods_search");
		input.pressKey(key(InputConstants.KEY_BACKSPACE));
		input.pressKey(key(InputConstants.KEY_BACKSPACE));
		input.pressKey(key(InputConstants.KEY_ESCAPE)); // drop search focus
		ctx.waitTicks(12);

		click(input, 658, 244); // gear on the first card (Keystrokes)
		ctx.waitTicks(14);
		ctx.takeScreenshot("34_mod_settings");

		click(input, 1202, 498); // background color button (below the preview)
		ctx.waitTicks(8);
		ctx.takeScreenshot("35_color_picker");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(4);

		// Give one setting a PvP override so the section shows both states.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.KeystrokesHud.class).scale.setPvpOverride(0.8));
		moveTo(input, 800, 500);
		input.scroll(-12);
		ctx.waitTicks(14);
		ctx.takeScreenshot("37_mod_settings_pvp");

		input.pressKey(key(InputConstants.KEY_ESCAPE)); // back to the grid
		ctx.waitTicks(12);
		click(input, 262, 240); // HUD category
		ctx.waitTicks(14);
		ctx.takeScreenshot("38_mods_hud_category");

		click(input, 262, 117); // back to ring
		ctx.waitTicks(4);
		ctx.takeScreenshot("39_panel_to_ring");
		ctx.waitTicks(16);
		input.pressKey(key(InputConstants.KEY_4));
		ctx.waitTicks(20);
		ctx.takeScreenshot("40_visuals");

		click(input, 262, 117);
		ctx.waitTicks(20);
		input.pressKey(key(InputConstants.KEY_2));
		ctx.waitTicks(20);
		ctx.takeScreenshot("41_settings_keybinds");
		click(input, 262, 240);
		ctx.waitTicks(10);
		ctx.takeScreenshot("42_settings_profiles");
		click(input, 262, 282);
		ctx.waitTicks(10);
		ctx.takeScreenshot("43_settings_about");

		// Easter egg: the logo spins and unlocks the spinning ring.
		click(input, 450, 219);
		ctx.waitTicks(6);
		ctx.takeScreenshot("44_logo_spin");
		ctx.waitTicks(20);
		ctx.takeScreenshot("45_easter_egg_found");
		boolean unlocked = ctx.computeOnClient(mc -> dev.mordo.kairo.core.ClientSettings.INSTANCE.ringSpinUnlocked.isOn()
			&& dev.mordo.kairo.core.ClientSettings.INSTANCE.ringSpin.isOn());
		if (!unlocked) {
			throw new AssertionError("Clicking the About logo didn't unlock the spinning ring");
		}

		// Back to the ring and flick it round.
		click(input, 262, 117);
		ctx.waitTicks(20);
		double ppu = Math.min(height / 810.0, width / 1080.0);
		double cx = width / 2.0;
		double cy = height / 2.0;
		double r = 201 * ppu;
		double start = -Math.PI / 2;
		input.setCursorPos(cx + Math.cos(start) * r, cy + Math.sin(start) * r);
		ctx.waitTick();
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 6; i++) {
			double a = start + i * 0.35;
			input.setCursorPos(cx + Math.cos(a) * r, cy + Math.sin(a) * r);
			ctx.waitTick();
		}
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		ctx.takeScreenshot("46_ring_spinning");
		ctx.waitTicks(80);
		ctx.takeScreenshot("47_ring_spun_and_snapped");
		boolean stillOpen = ctx.computeOnClient(mc -> mc.gui.screen() instanceof dev.mordo.kairo.gui.menu.MenuScreen);
		if (!stillOpen) {
			throw new AssertionError("Spinning the ring closed it or opened a section");
		}
		// Turn spinning off again so the rest of the tour hovers segments where it expects them.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ClientSettings.INSTANCE.ringSpin.set(false));
		ctx.waitTicks(30);

		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(12);
	}

	/** Default HUD in-game, the layout editor (including a drag), and a settings preview. */
	private void hudTour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("item replace entity @a armor.head with netherite_helmet");
		server.runCommand("item replace entity @a armor.chest with diamond_chestplate[damage=500]");
		server.runCommand("item replace entity @a armor.legs with netherite_leggings");
		server.runCommand("item replace entity @a armor.feet with iron_boots[damage=180]");
		server.runCommand("item replace entity @a weapon.mainhand with netherite_sword");
		server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
		server.runCommand("give @a totem_of_undying 3");
		ctx.runOnClient(mc -> {
			mc.options.pauseOnLostFocus = false;
			if (mc.gui.screen() != null) {
				mc.gui.setScreen(null);
			}
		});
		ctx.waitTicks(10);
		input.holdKey(options -> options.keyUp);
		ctx.waitTicks(4);
		ctx.takeScreenshot("50_hud_default");
		input.releaseKey(options -> options.keyUp);
		ctx.waitTicks(5);

		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(15);
		input.pressKey(key(InputConstants.KEY_5));
		ctx.waitTicks(20);
		input.setCursorPos(width - 30, 22); // over Ping (top-right)
		ctx.waitTicks(4);
		ctx.takeScreenshot("51_layout_editor");

		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 10; i++) {
			input.setCursorPos(width - 30 - i * 57, 22 + i * 20);
			ctx.waitTick();
		}
		ctx.takeScreenshot("52_layout_dragging");
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(4);
		ctx.takeScreenshot("53_layout_dropped");
		layoutHandlesTour(ctx, input);
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
		ctx.takeScreenshot("54_hud_after_move");

		// Settings preview for Armor Status (opened straight from code; positions are covered elsewhere).
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.forModSettings(
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.ArmorStatusHud.class), null)));
		ctx.waitTicks(20);
		ctx.takeScreenshot("55_settings_preview");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
	}

	/** FPS element's box in screen pixels: x, y, w, h. */
	private double[] fpsBox(ClientGameTestContext ctx) {
		return ctx.computeOnClient(mc -> {
			var fps = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CoordinatesHud.class);
			mc.gui.toastManager().clear();
			int gw = mc.getWindow().getGuiScaledWidth();
			int gh = mc.getWindow().getGuiScaledHeight();
			float[] p = dev.mordo.kairo.hud.HudManager.place(fps, gw, gh);
			double s = mc.getWindow().getGuiScale();
			return new double[]{p[0] * s, p[1] * s, fps.width() * p[2] * s, fps.height() * p[2] * s, s};
		});
	}

	/** Layout editor corner handles: resize grip and the × that hides an element (and + to bring it back). */
	private void layoutHandlesTour(ClientGameTestContext ctx, TestInput input) {
		double[] b = fpsBox(ctx);
		double s = b[4];
		input.setCursorPos(b[0] + b[2] / 2, b[1] + b[3] / 2);
		ctx.waitTicks(4);
		ctx.takeScreenshot("53a_layout_handles");

		double gx = b[0] + b[2] + 1.5 * s;
		double gy = b[1] + b[3] + 1.5 * s;
		input.setCursorPos(gx, gy);
		ctx.waitTick();
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 8; i++) {
			input.setCursorPos(gx + i * b[2] * 0.08, gy + i * b[3] * 0.08);
			ctx.waitTick();
		}
		ctx.takeScreenshot("53b_layout_resizing");
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		double scale = ctx.computeOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CoordinatesHud.class).scale.value());
		if (scale < 1.15) {
			throw new AssertionError("Dragging the resize grip didn't scale the FPS counter: " + scale);
		}

		b = fpsBox(ctx);
		input.setCursorPos(b[0] + b[2] + 1.5 * s, b[1] - 1.5 * s);
		ctx.waitTicks(2);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		ctx.takeScreenshot("53c_layout_hidden_with_x");
		boolean hidden = ctx.computeOnClient(mc -> !dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CoordinatesHud.class).isEnabled());
		if (!hidden) {
			throw new AssertionError("The × handle didn't hide the FPS counter");
		}
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT); // + on the placeholder brings it back
		ctx.waitTicks(3);
		boolean back = ctx.computeOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CoordinatesHud.class).isEnabled());
		if (!back) {
			throw new AssertionError("The + handle didn't bring the FPS counter back");
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CoordinatesHud.class).resetPosition());
		ctx.waitTicks(2);
		multiSelectTour(ctx, input);
	}

	/** Two-colour checkerboard (0 = transparent) over a skin region. */
	private static void fillChecker(com.mojang.blaze3d.platform.NativeImage image, int x0, int y0, int w, int h, int a, int b) {
		for (int y = y0; y < y0 + h; y++) {
			for (int x = x0; x < x0 + w; x++) {
				image.setPixel(x, y, ((x + y) & 1) == 0 ? a : b);
			}
		}
	}

	/** An element's box in screen pixels: x, y, w, h, gui scale. */
	private double[] elementBox(ClientGameTestContext ctx, Class<? extends dev.mordo.kairo.hud.HudElement> type) {
		return ctx.computeOnClient(mc -> {
			var element = dev.mordo.kairo.core.ModuleManager.get(type);
			int gw = mc.getWindow().getGuiScaledWidth();
			int gh = mc.getWindow().getGuiScaledHeight();
			float[] p = dev.mordo.kairo.hud.HudManager.place(element, gw, gh);
			double s = mc.getWindow().getGuiScale();
			return new double[]{p[0] * s, p[1] * s, element.width() * p[2] * s, element.height() * p[2] * s, s};
		});
	}

	/** Shift-click two elements, drag them together; then the CPS attach button in the toolbar. */
	private void multiSelectTour(ClientGameTestContext ctx, TestInput input) {
		var coordsType = dev.mordo.kairo.hud.CoordinatesHud.class;
		var pingType = dev.mordo.kairo.hud.PingHud.class;
		double[] c = elementBox(ctx, coordsType);
		double[] p = elementBox(ctx, pingType);
		// A click on empty space clears the selection first.
		input.setCursorPos(width * 0.45, height * 0.33);
		ctx.waitTick();
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		input.holdKey(key(InputConstants.KEY_LSHIFT));
		input.setCursorPos(c[0] + c[2] / 2, c[1] + c[3] / 2);
		ctx.waitTick();
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		input.setCursorPos(p[0] + p[2] / 2, p[1] + p[3] / 2);
		ctx.waitTick();
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTick();
		input.releaseKey(key(InputConstants.KEY_LSHIFT));
		ctx.waitTicks(3);
		ctx.takeScreenshot("53d_two_selected");

		input.setCursorPos(c[0] + c[2] / 2, c[1] + c[3] / 2);
		ctx.waitTick();
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 8; i++) {
			input.setCursorPos(c[0] + c[2] / 2 + i * 12, c[1] + c[3] / 2 - i * 8);
			ctx.waitTick();
		}
		ctx.takeScreenshot("53e_group_dragging");
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		double[] c2 = elementBox(ctx, coordsType);
		double[] p2 = elementBox(ctx, pingType);
		double movedC = Math.hypot(c2[0] - c[0], c2[1] - c[1]);
		double movedP = Math.hypot(p2[0] - p[0], p2[1] - p[1]);
		if (movedC < 40 || movedP < 40) {
			throw new AssertionError("Dragging a multi-selection should move both elements: " + movedC + ", " + movedP);
		}

		// Ctrl+Z puts both back, Ctrl+Y moves them again.
		input.holdKey(key(InputConstants.KEY_LCONTROL));
		input.pressKey(key(InputConstants.KEY_Z));
		input.releaseKey(key(InputConstants.KEY_LCONTROL));
		ctx.waitTicks(8);
		ctx.takeScreenshot("53g_undone");
		double[] c3 = elementBox(ctx, coordsType);
		if (Math.hypot(c3[0] - c[0], c3[1] - c[1]) > 4) {
			throw new AssertionError("Ctrl+Z didn't undo the group move");
		}
		input.holdKey(key(InputConstants.KEY_LCONTROL));
		input.pressKey(key(InputConstants.KEY_Y));
		input.releaseKey(key(InputConstants.KEY_LCONTROL));
		ctx.waitTicks(8);
		double[] c4 = elementBox(ctx, coordsType);
		if (Math.hypot(c4[0] - c2[0], c4[1] - c2[1]) > 4) {
			throw new AssertionError("Ctrl+Y didn't redo the group move");
		}

		// Select CPS on its own: the toolbar offers attaching it to Keystrokes.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CpsHud.class).attachToKeystrokes.set(false));
		ctx.waitTicks(3);
		double[] cps = elementBox(ctx, dev.mordo.kairo.hud.CpsHud.class);
		input.setCursorPos(cps[0] + cps[2] / 2, cps[1] + cps[3] / 2);
		ctx.waitTick();
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(4);
		ctx.takeScreenshot("53f_cps_attach_button");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ModuleManager.get(coordsType).resetPosition();
			dev.mordo.kairo.core.ModuleManager.get(pingType).resetPosition();
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CpsHud.class).attachToKeystrokes.reset();
		});
		ctx.waitTicks(3);
	}

	/** This round's additions: Buttons settings and click effects, glass inventories, block outline, compass,
	 * saturation and 3D skins. */
	private void phase12Tour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		var settings = dev.mordo.kairo.core.ClientSettings.INSTANCE;

		// Chat: ↑ brings back the last message/command instead of jumping into the search box.
		input.pressKey(key(InputConstants.KEY_T));
		ctx.waitTicks(5);
		input.pressKey(key(InputConstants.KEY_UP));
		ctx.waitTicks(2);
		String focus = ctx.computeOnClient(mc -> {
			var screen = mc.gui.screen();
			var focused = screen == null ? null : screen.getFocused();
			return focused instanceof net.minecraft.client.gui.components.EditBox box ? box.getMessage().getString() : String.valueOf(focused);
		});
		if (focus.equals("Search chat")) {
			throw new AssertionError("Arrow up moved focus into the chat search box");
		}
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(5);

		// Visuals → Buttons, with the clickable preview.
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visuals, null)));
		ctx.waitTicks(20);
		click(input, 262, 366);
		ctx.waitTicks(12);
		ctx.takeScreenshot("p12_01_visuals_buttons");
		click(input, 524, 224);
		ctx.waitTicks(3);
		ctx.takeScreenshot("p12_02_ripple");
		ctx.runOnClient(mc -> settings.buttonClick.set(dev.mordo.kairo.core.ClientSettings.ClickEffect.CONFETTI));
		ctx.waitTicks(2);
		click(input, 524, 224);
		ctx.waitTicks(4);
		ctx.takeScreenshot("p12_03_confetti");
		ctx.runOnClient(mc -> {
			settings.buttonClick.set(dev.mordo.kairo.core.ClientSettings.ClickEffect.SPARKS);
			settings.buttonRadius.set(0.0);
		});
		ctx.waitTicks(2);
		click(input, 524, 224);
		ctx.waitTicks(3);
		ctx.takeScreenshot("p12_04_square_sparks");
		ctx.runOnClient(mc -> {
			settings.buttonClick.reset();
			settings.buttonRadius.reset();
			mc.gui.setScreen(null);
		});
		ctx.waitTicks(10);

		// Glass inventory (survival) and the creative inventory.
		server.runCommand("gamemode survival @a");
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
		ctx.waitTicks(15);
		ctx.takeScreenshot("p12_05_inventory_glass");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		server.runCommand("gamemode creative @a");
		ctx.waitTicks(10);
		input.pressKey(key(InputConstants.KEY_E));
		ctx.waitTicks(15);
		ctx.takeScreenshot("p12_06_creative_inventory_glass");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);

		// Block outline (accent, fill) and the compass bar.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.DirectionHud.class).enabled.set(true));
		ctx.waitTicks(5);
		ctx.takeScreenshot("p12_07_outline_and_compass");
		ctx.runOnClient(mc -> {
			var outline = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.BlockOutline.class);
			outline.rainbow.set(true);
			outline.width.set(3.0);
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("p12_08_outline_rainbow_thick");
		ctx.runOnClient(mc -> {
			var outline = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.BlockOutline.class);
			outline.rainbow.reset();
			outline.width.reset();
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.DirectionHud.class).enabled.set(false);
		});

		// Saturation on Minecraft's hunger icons (survival, a little hungry and well fed).
		server.runCommand("gamemode survival @a");
		ctx.runOnClient(mc -> {
			var hotbar = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Hotbar.class);
			hotbar.healthStyle.set(dev.mordo.kairo.mods.Hotbar.HealthStyle.HEARTS);
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Saturation.class).number.set(true);
		});
		server.runCommand("effect give @a minecraft:saturation 1 2");
		ctx.waitTicks(10);
		ctx.takeScreenshot("p12_09_saturation_vanilla_icons");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Hotbar.class).healthStyle.set(
			dev.mordo.kairo.mods.Hotbar.HealthStyle.SQUARES));
		ctx.waitTicks(3);
		ctx.takeScreenshot("p12_10_saturation_squares");
		// Holding food while hungry and hurt: hunger, saturation and health previews flash.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Hotbar.class).healthStyle.set(
			dev.mordo.kairo.mods.Hotbar.HealthStyle.HEARTS));
		server.runCommand("effect clear @a");
		server.runCommand("effect give @p minecraft:hunger 3 120");
		ctx.waitTicks(70);
		server.runCommand("damage @p 7 minecraft:magic");
		server.runCommand("item replace entity @p weapon.mainhand with cooked_beef");
		ctx.waitTicks(10);
		ctx.takeScreenshot("p14_01_food_preview");
		server.runCommand("effect clear @a");
		server.runCommand("effect give @a minecraft:instant_health 1 5");
		server.runCommand("effect give @a minecraft:saturation 1 20");
		server.runCommand("item replace entity @p weapon.mainhand with netherite_sword");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Hotbar.class).healthStyle.reset();
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Saturation.class).number.reset();
		});
		server.runCommand("gamemode creative @a");

		// 3D skin layers: the player from the front, 3D then flat.
		server.runCommand("item replace entity @a armor.head with air");
		server.runCommand("item replace entity @a armor.chest with air");
		server.runCommand("item replace entity @a armor.legs with air");
		server.runCommand("item replace entity @a armor.feet with air");
		ctx.runOnClient(mc -> {
			mc.gui.hud.getChat().clearMessages(false);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		});
		input.setCursorPos(width / 2.0, height / 2.0);
		input.holdKey(key(InputConstants.KEY_C)); // zoom in on the player
		ctx.waitTicks(20);
		ctx.takeScreenshot("p12_11_skin_3d");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Skins3D.class).enabled.set(false));
		ctx.waitTicks(3);
		ctx.takeScreenshot("p12_12_skin_flat");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Skins3D.class).enabled.set(true);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("p12_13_skin_3d_back");
		// A made-up skin with a bold outer layer, to check the cubes land on the right faces.
		ctx.runOnClient(mc -> {
			var id = net.minecraft.resources.Identifier.fromNamespaceAndPath("kairo", "test/skin_layers");
			var image = new com.mojang.blaze3d.platform.NativeImage(64, 64, true);
			for (int y = 0; y < 64; y++) {
				for (int x = 0; x < 64; x++) {
					image.setPixel(x, y, 0);
				}
			}
			fillChecker(image, 32, 0, 32, 16, 0xFFFF5A5F, 0xFFFFC94D);   // hat: red/gold
			fillChecker(image, 16, 32, 24, 16, 0xFF7FD8FF, 0xFF2E6BFF);  // jacket: blues
			fillChecker(image, 40, 32, 16, 16, 0xFF8CE05A, 0);           // right sleeve
			fillChecker(image, 48, 48, 16, 16, 0xFF8CE05A, 0);           // left sleeve
			fillChecker(image, 0, 32, 16, 16, 0xFFB06BFF, 0xFFFF9A52);   // right pants
			fillChecker(image, 0, 48, 16, 16, 0xFFB06BFF, 0xFFFF9A52);   // left pants
			image.setPixel(40, 8, 0xFFFFFFFF); // marks the hat front's top-left texel
			mc.getTextureManager().register(id, new net.minecraft.client.renderer.texture.DynamicTexture(() -> "kairo test skin", image));
			dev.mordo.kairo.mods.Skins3D.testTexture = id;
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("p12_14_skin_3d_test_front");
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
		ctx.waitTicks(5);
		ctx.takeScreenshot("p12_15_skin_3d_test_back");
		input.releaseKey(key(InputConstants.KEY_C));
		ctx.waitTicks(10);
		ctx.takeScreenshot("p12_16_skin_3d_test_full");
		ctx.runOnClient(mc -> dev.mordo.kairo.mods.Skins3D.testTexture = null);
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON));
		ctx.waitTicks(5);
	}

	/** Right-click PvP Mode in the ring: the PvP options page. */
	private void pvpOptionsTour(ClientGameTestContext ctx, TestInput input) {
		input.setCursorPos(width / 2.0, height / 2.0);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(15);
		hoverSegment(input, 2);
		ctx.waitTicks(6);
		ctx.takeScreenshot("p11_00_ring_pvp_hint");
		input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
		ctx.waitTicks(20);
		ctx.takeScreenshot("p11_01_pvp_options_camera");
		boolean open = ctx.computeOnClient(mc -> mc.gui.screen() instanceof dev.mordo.kairo.gui.menu.MenuScreen);
		if (!open) {
			throw new AssertionError("Right-clicking PvP Mode closed the menu instead of opening its options");
		}
		click(input, 262, 240);
		ctx.waitTicks(10);
		ctx.takeScreenshot("p11_02_pvp_options_explosions");
		click(input, 262, 324);
		ctx.waitTicks(10);
		ctx.takeScreenshot("p11_03_pvp_options_items");
		click(input, 262, 366);
		ctx.waitTicks(10);
		ctx.takeScreenshot("p11_04_pvp_options_world");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(12);

		// Hide waypoints in PvP: markers go away only while PvP mode is on.
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.PvpSettings.INSTANCE.hideWaypoints.set(true);
			dev.mordo.kairo.core.PvpSettings.INSTANCE.noViewBobbing.set(true);
			dev.mordo.kairo.core.PvpMode.set(true);
		});
		ctx.waitTicks(10);
		ctx.takeScreenshot("p11_05_pvp_waypoints_hidden");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.PvpMode.set(false);
			dev.mordo.kairo.core.PvpSettings.INSTANCE.hideWaypoints.reset();
			dev.mordo.kairo.core.PvpSettings.INSTANCE.noViewBobbing.reset();
		});
		ctx.waitTicks(30);
	}

	/** The Hotbar mod: bars, damage trail, absorption, squares, separate slots, and vanilla for comparison. */
	private void hotbarTour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		server.runCommand("gamemode survival @a");
		server.runCommand("xp add @a 7 levels");
		server.runCommand("xp add @a 9 points");
		ctx.waitTicks(10);
		ctx.takeScreenshot("p11_10_hotbar_bars");
		input.pressKey(key(InputConstants.KEY_6));
		ctx.waitTick();
		ctx.takeScreenshot("p11_11_selection_sliding");
		ctx.waitTicks(5);
		server.runCommand("damage @p 9 minecraft:magic");
		ctx.waitTicks(3);
		ctx.takeScreenshot("p11_12_damage_trail");
		ctx.waitTicks(30);
		server.runCommand("effect give @a minecraft:absorption 60 1");
		ctx.waitTicks(5);
		ctx.takeScreenshot("p11_13_absorption");
		var hotbar = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Hotbar.class);
		ctx.runOnClient(mc -> hotbar.healthStyle.set(dev.mordo.kairo.mods.Hotbar.HealthStyle.SQUARES));
		ctx.waitTicks(3);
		ctx.takeScreenshot("p11_14_squares");
		ctx.runOnClient(mc -> {
			hotbar.style.set(dev.mordo.kairo.mods.Hotbar.HotbarStyle.SLOTS);
			hotbar.slotNumbers.set(true);
		});
		ctx.waitTicks(3);
		ctx.takeScreenshot("p11_15_separate_slots");
		ctx.runOnClient(mc -> hotbar.enabled.set(false));
		ctx.waitTicks(3);
		ctx.takeScreenshot("p11_16_vanilla_for_comparison");
		ctx.runOnClient(mc -> {
			hotbar.enabled.set(true);
			hotbar.style.reset();
			hotbar.slotNumbers.reset();
			hotbar.healthStyle.reset();
		});
		server.runCommand("effect clear @a");
		server.runCommand("effect give @a minecraft:instant_health 1 5");
		server.runCommand("gamemode creative @a");
		input.pressKey(key(InputConstants.KEY_1));
		ctx.waitTicks(10);
	}

	/** Phase 6: zoom, fullbright, waypoints, chat, shulker preview, totem pop. */
	private void featureTour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		input.setCursorPos(width / 2.0, height / 2.0);

		// Zoom: hold C, then scroll in further.
		input.holdKey(key(InputConstants.KEY_C));
		ctx.waitTicks(15);
		ctx.takeScreenshot("60_zoom");
		input.scroll(3);
		ctx.waitTicks(15);
		ctx.takeScreenshot("61_zoom_scrolled");
		input.releaseKey(key(InputConstants.KEY_C));
		ctx.waitTicks(15);

		// Waypoints in front, to the side and behind (the one behind clamps to the edge).
		ctx.runOnClient(mc -> {
			var store = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Waypoints.class).store();
			store.add(new dev.mordo.kairo.mods.waypoints.Waypoint("Base", 30, -55, 70, "minecraft:overworld", 0xFF5AC8FA));
			store.add(new dev.mordo.kairo.mods.waypoints.Waypoint("Mine", -14, -58, 25, "minecraft:overworld", 0xFFFFC145));
			store.add(new dev.mordo.kairo.mods.waypoints.Waypoint("Portal", 4, -60, -90, "minecraft:overworld", 0xFFB06BFF));
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("62_waypoints");

		// Fullbright at night.
		server.runCommand("time set 18000");
		ctx.waitTicks(10);
		ctx.takeScreenshot("63_night");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Fullbright.class).toggle());
		ctx.waitTicks(20);
		ctx.takeScreenshot("64_fullbright");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Fullbright.class).toggle());
		server.runCommand("time set day");
		ctx.waitTicks(15);

		// Settings pages: waypoint manager and crosshair editor.
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.forModSettings(
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Waypoints.class), null)));
		ctx.waitTicks(20);
		ctx.takeScreenshot("65_waypoint_manager");
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.forModSettings(
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Crosshair.class), null)));
		ctx.waitTicks(20);
		ctx.takeScreenshot("66_crosshair_settings");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);

		// Chat: repeated messages stack, timestamps, search bar.
		server.runCommand("say hello there");
		server.runCommand("say hello there");
		server.runCommand("say hello there");
		server.runCommand("say something else");
		ctx.waitTicks(3);
		ctx.takeScreenshot("67_chat_hud");
		input.pressKey(key(InputConstants.KEY_T));
		ctx.waitTicks(10);
		ctx.takeScreenshot("68_chat_open");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(5);

		// Shulker preview: hotbar with Shift, then the inventory tooltip.
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a hotbar.1 with purple_shulker_box[container=["
			+ "{slot:0,item:{id:\"minecraft:diamond\",count:64}},"
			+ "{slot:1,item:{id:\"minecraft:golden_apple\",count:12}},"
			+ "{slot:4,item:{id:\"minecraft:ender_pearl\",count:16}},"
			+ "{slot:9,item:{id:\"minecraft:totem_of_undying\"}},"
			+ "{slot:13,item:{id:\"minecraft:netherite_ingot\",count:5}},"
			+ "{slot:26,item:{id:\"minecraft:experience_bottle\",count:64}}]]");
		input.pressKey(key(InputConstants.KEY_2));
		ctx.waitTicks(5);
		input.holdKey(key(InputConstants.KEY_LSHIFT));
		ctx.waitTicks(5);
		ctx.takeScreenshot("69_shulker_hotbar");
		input.releaseKey(key(InputConstants.KEY_LSHIFT));
		ctx.waitTicks(5);
		input.pressKey(key(InputConstants.KEY_E));
		ctx.waitTicks(5);
		double[] slot = ctx.computeOnClient(mc -> {
			double scale = mc.getWindow().getGuiScale();
			int left = (mc.getWindow().getGuiScaledWidth() - 176) / 2;
			int top = (mc.getWindow().getGuiScaledHeight() - 166) / 2;
			// Hotbar slot 2 in the survival inventory.
			return new double[] {(left + 8 + 18 + 8) * scale, (top + 142 + 8) * scale};
		});
		input.setCursorPos(slot[0], slot[1]);
		ctx.waitTicks(5);
		ctx.takeScreenshot("70_shulker_tooltip");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(5);

		// PvP mode: smaller, shorter totem pop and reduced hurt tilt.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.toggle());
		ctx.waitTicks(40);
		server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
		server.runCommand("damage @p 1000 minecraft:generic");
		ctx.waitTicks(4);
		ctx.takeScreenshot("71_totem_pop_pvp");
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.toggle());
		server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
		server.runCommand("effect give @a instant_health 1 5");
		ctx.waitTicks(40);
		server.runCommand("damage @p 1000 minecraft:generic");
		ctx.waitTicks(4);
		ctx.takeScreenshot("72_totem_pop_normal");
		ctx.waitTicks(40);

		// Fire overlay (lowered and see-through by default).
		server.runCommand("effect give @a instant_health 1 5");
		server.runCommand("execute at @p run setblock ~ ~ ~ fire");
		ctx.waitTicks(10);
		ctx.takeScreenshot("73_fire_overlay");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.HurtEffects.class).toggle());
		ctx.waitTicks(2);
		ctx.takeScreenshot("74_fire_vanilla");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.HurtEffects.class).toggle());
		server.runCommand("execute at @p run fill ~-4 ~-1 ~-4 ~4 ~2 ~4 air replace fire");
		server.runCommand("gamemode creative @a");
		ctx.waitTicks(40);
	}

	/** Phase 7: PvP layout editing, gliding between layouts, PvP previews, and normal values coming back exactly. */
	private void pvpTour(ClientGameTestContext ctx, TestInput input) {
		String normalBefore = ctx.computeOnClient(mc -> normalLayout());

		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.set(true));
		ctx.waitTicks(20);
		ctx.takeScreenshot("80_pvp_hud");

		// Layout editor while in PvP mode: edits go into the PvP layout.
		input.setCursorPos(width / 2.0, height / 2.0);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(15);
		input.pressKey(key(InputConstants.KEY_5));
		ctx.waitTicks(20);
		ctx.takeScreenshot("81_layout_pvp");

		// Drag Coordinates up to the top centre.
		double[] from = ctx.computeOnClient(mc -> {
			var e = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.CoordinatesHud.class);
			double gs = mc.getWindow().getGuiScale();
			int gw = mc.getWindow().getGuiScaledWidth();
			int gh = mc.getWindow().getGuiScaledHeight();
			dev.mordo.kairo.hud.HudManager.prepare(e, false);
			float s = dev.mordo.kairo.hud.HudManager.scale(e);
			return new double[] {(dev.mordo.kairo.hud.HudManager.x(e, gw) + e.width() * s / 2) * gs,
				(dev.mordo.kairo.hud.HudManager.y(e, gh) + e.height() * s / 2) * gs};
		});
		input.setCursorPos(from[0], from[1]);
		ctx.waitTicks(2);
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 10; i++) {
			input.setCursorPos(from[0] + (width * 0.5 - from[0]) * i / 10, from[1] + (height * 0.2 - from[1]) * i / 10);
			ctx.waitTick();
		}
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		// Hide Keystrokes in PvP mode (same as selecting it and pressing Hide).
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.KeystrokesHud.class).enabled.setEffective(false));
		ctx.waitTicks(5);
		ctx.takeScreenshot("82_layout_pvp_edited");

		// Back to the normal layout: elements glide home.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.set(false));
		ctx.waitTicks(3);
		ctx.takeScreenshot("83_layout_glide");
		ctx.waitTicks(15);
		ctx.takeScreenshot("84_layout_normal");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);

		String normalAfter = ctx.computeOnClient(mc -> normalLayout());
		if (!normalBefore.equals(normalAfter)) {
			throw new AssertionError("Normal layout changed while editing PvP mode:\n" + normalBefore + "\n" + normalAfter);
		}

		// In-game flip with the keybind: HUD glides into the PvP layout.
		ctx.runOnClient(mc -> ClientKeybinds.INSTANCE.togglePvp.set(Keybind.keyboard(InputConstants.KEY_K)));
		input.pressKey(key(InputConstants.KEY_K));
		ctx.waitTicks(3);
		ctx.takeScreenshot("85_ingame_glide");
		ctx.waitTicks(20);
		ctx.takeScreenshot("86_ingame_pvp");

		// Mods grid and a settings page while in PvP mode.
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(15);
		input.pressKey(key(InputConstants.KEY_1));
		ctx.waitTicks(20);
		ctx.takeScreenshot("87_mods_grid_pvp");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(15);
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.forModSettings(
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.hud.ArmorStatusHud.class), null)));
		ctx.waitTicks(20);
		ctx.takeScreenshot("88_settings_preview_pvp");
		moveTo(input, 800, 600);
		input.scroll(-30);
		ctx.waitTicks(15);
		ctx.takeScreenshot("89_settings_pvp_section");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.gui.setScreen(null));

		input.pressKey(key(InputConstants.KEY_K)); // back to normal
		ctx.waitTicks(20);
	}

	/**
	 * Phase 8, before any world: the client's main menu, worlds and servers screens, and vanilla menus restyled.
	 * (Under game tests the title screen isn't swapped automatically, so the screens are opened directly.)
	 */
	private void mainMenuTour(ClientGameTestContext ctx, TestInput input) {
		// The real swap: opening vanilla's title, worlds and servers screens gives the client's own.
		String swapped = ctx.computeOnClient(mc -> {
			// Test runs start with a fresh config, where the title screen would show the first-run welcome instead.
			dev.mordo.kairo.core.ClientSettings.INSTANCE.onboarded.set(true);
			dev.mordo.kairo.gui.main.MainScreens.testOverride = true;
			try {
				var title = new net.minecraft.client.gui.screens.TitleScreen();
				mc.gui.setScreen(title);
				String a = mc.gui.screen().getClass().getSimpleName();
				mc.gui.setScreen(new net.minecraft.client.gui.screens.worldselection.SelectWorldScreen(title));
				String b = mc.gui.screen().getClass().getSimpleName();
				mc.gui.setScreen(new net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen(title));
				String c = mc.gui.screen().getClass().getSimpleName();
				return a + "," + b + "," + c;
			} finally {
				dev.mordo.kairo.gui.main.MainScreens.testOverride = false;
				mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
			}
		});
		if (!swapped.equals("MainMenuScreen,WorldsScreen,ServersScreen")) {
			throw new AssertionError("Screen swap didn't happen: " + swapped);
		}
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> {
			var servers = new net.minecraft.client.multiplayer.ServerList(mc);
			servers.load();
			if (servers.size() == 0) {
				servers.add(new net.minecraft.client.multiplayer.ServerData("Local test server", "localhost:25599",
					net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
				servers.add(new net.minecraft.client.multiplayer.ServerData("Crystal PvP", "pvp.example.invalid",
					net.minecraft.client.multiplayer.ServerData.Type.OTHER), false);
				servers.save();
			}
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		input.setCursorPos(width * 0.75, height * 0.15);
		ctx.waitTicks(4);
		ctx.takeScreenshot("p8_00_main_menu_intro");
		ctx.waitTicks(40);
		ctx.takeScreenshot("p8_01_main_menu");
		moveTo(input, 275, 437);
		ctx.waitTicks(10);
		ctx.takeScreenshot("p8_02_main_menu_hover");

		click(input, 275, 365); // Singleplayer
		ctx.waitTicks(30);
		ctx.takeScreenshot("p8_03_worlds");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(20);

		click(input, 275, 437); // Multiplayer
		ctx.waitTicks(60);
		ctx.takeScreenshot("p8_04_servers");
		moveTo(input, 700, 180);
		ctx.waitTicks(10);
		ctx.takeScreenshot("p8_05_servers_hover");
		var menuSettings = dev.mordo.kairo.core.MainMenuSettings.INSTANCE;
		ctx.runOnClient(mc -> menuSettings.serverLayout.set(dev.mordo.kairo.core.MainMenuSettings.ServerLayout.COMPACT));
		ctx.waitTicks(10);
		ctx.takeScreenshot("p13_00_servers_compact");
		ctx.runOnClient(mc -> menuSettings.serverLayout.set(dev.mordo.kairo.core.MainMenuSettings.ServerLayout.TILES));
		ctx.waitTicks(10);
		ctx.takeScreenshot("p13_01_servers_tiles");
		ctx.runOnClient(mc -> menuSettings.showAddresses.set(false));
		ctx.waitTicks(5);
		ctx.takeScreenshot("p13_02_servers_tiles_no_addresses");
		ctx.runOnClient(mc -> {
			menuSettings.serverLayout.reset();
			menuSettings.showAddresses.reset();
		});
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(20);

		// The other main menu layouts.
		ctx.runOnClient(mc -> menuSettings.layout.set(dev.mordo.kairo.core.MainMenuSettings.Layout.DOCK));
		ctx.waitTicks(15);
		ctx.takeScreenshot("p13_03_dock");
		moveTo(input, 700, 690);
		ctx.waitTicks(12);
		ctx.takeScreenshot("p13_04_dock_magnified");
		ctx.runOnClient(mc -> menuSettings.layout.set(dev.mordo.kairo.core.MainMenuSettings.Layout.ORBIT));
		input.setCursorPos(width * 0.55, height * 0.3);
		ctx.waitTicks(30);
		ctx.takeScreenshot("p13_05_orbit");
		input.pressKey(key(InputConstants.KEY_RIGHT));
		ctx.waitTicks(4);
		ctx.takeScreenshot("p13_06_orbit_spinning");
		ctx.waitTicks(30);
		ctx.takeScreenshot("p13_07_orbit_next");
		ctx.runOnClient(mc -> menuSettings.layout.reset());
		ctx.waitTicks(15);

		click(input, 275, 581); // Options (vanilla, restyled)
		ctx.waitTicks(2);
		ctx.takeScreenshot("p12_00_buttons_fading_in");
		ctx.waitTicks(23);
		ctx.takeScreenshot("p8_06_options");
		ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.VideoSettingsScreen(mc.gui.screen(), mc, mc.options)));
		ctx.waitTicks(20);
		ctx.takeScreenshot("p8_07_video_settings");

		click(input, 275, 509); // (back on the main menu after closing) the client menu
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::mods,
			new dev.mordo.kairo.gui.main.MainMenuScreen())));
		ctx.waitTicks(30);
		ctx.takeScreenshot("p8_08_mods_from_main_menu");

		mainMenuSettingsTour(ctx, input);

		// Back to vanilla's title screen so the game test can create its world.
		ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen()));
		ctx.waitTicks(10);
	}

	/** Main menu settings: reordering, hiding, image / GIF / panorama backgrounds, Minecraft style. */
	private void mainMenuSettingsTour(ClientGameTestContext ctx, TestInput input) {
		var menu = dev.mordo.kairo.core.MainMenuSettings.INSTANCE;
		ctx.runOnClient(mc -> {
			try {
				writeTestBackgrounds();
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuSettingsScreen(new dev.mordo.kairo.gui.main.MainMenuScreen()));
		});
		ctx.waitTicks(40);
		ctx.takeScreenshot("p9_00_menu_settings");

		// Style tiles: Minecraft, then Orbit, then back to Kairo.
		click(input, 526, 198);
		ctx.waitTicks(5);
		boolean vanillaPicked = ctx.computeOnClient(mc -> !dev.mordo.kairo.core.ClientSettings.INSTANCE.customMainMenu.isOn());
		click(input, 340, 198);
		ctx.waitTicks(5);
		String orbitPicked = ctx.computeOnClient(mc -> dev.mordo.kairo.core.ClientSettings.INSTANCE.customMainMenu.isOn() + ","
			+ dev.mordo.kairo.core.MainMenuSettings.INSTANCE.layout.get());
		ctx.takeScreenshot("p14_02_style_orbit_selected");
		click(input, 153, 198);
		ctx.waitTicks(5);
		String classicPicked = ctx.computeOnClient(mc -> dev.mordo.kairo.core.ClientSettings.INSTANCE.customMainMenu.isOn() + ","
			+ dev.mordo.kairo.core.MainMenuSettings.INSTANCE.layout.get());
		if (!vanillaPicked || !orbitPicked.equals("true,ORBIT") || !classicPicked.equals("true,CLASSIC")) {
			throw new AssertionError("Style tiles picked the wrong style: minecraft=" + vanillaPicked + " orbit=" + orbitPicked + " kairo=" + classicPicked);
		}

		// Drag "Singleplayer" (first row) down to third place, and hide "Options".
		double row0 = (288 + 23) * pxPerUnit();
		double rowX = 300 * pxPerUnit();
		input.setCursorPos(rowX, row0);
		ctx.waitTicks(2);
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		for (int i = 1; i <= 10; i++) {
			input.setCursorPos(rowX, row0 + (110 * pxPerUnit()) * i / 10);
			ctx.waitTick();
		}
		ctx.takeScreenshot("p9_01_dragging");
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.runOnClient(mc -> menu.setHidden("options", true));
		ctx.waitTicks(10);
		String order = ctx.computeOnClient(mc -> String.join(",", menu.buttonOrder()));
		if (!order.startsWith("multiplayer,client,singleplayer")) {
			throw new AssertionError("Drag didn't reorder the buttons: " + order);
		}

		// Image background (live behind the settings), then the main menu with it.
		ctx.runOnClient(mc -> menu.background.set("image:tour-sunset.png"));
		ctx.waitTick();
		ctx.takeScreenshot("p9_02a_image_loading"); // dark or fading in, never the stock panorama
		ctx.waitTicks(30);
		ctx.takeScreenshot("p9_02_settings_image_background");
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen()));
		ctx.waitTicks(40);
		ctx.takeScreenshot("p9_03_main_menu_image_reordered");

		// Animated GIF: two shots a moment apart should differ.
		ctx.runOnClient(mc -> menu.background.set("image:tour-pulse.gif"));
		ctx.waitTicks(30);
		ctx.takeScreenshot("p9_04_gif_a");
		ctx.waitTicks(7);
		ctx.takeScreenshot("p9_05_gif_b");

		// Custom panorama (six faces, resources reload once).
		ctx.runOnClient(mc -> menu.background.set("cube:tour-cube"));
		ctx.waitTicks(200);
		ctx.takeScreenshot("p9_06_custom_panorama");

		// Minecraft style: vanilla title, restyled, with the way back to these settings.
		ctx.runOnClient(mc -> {
			menu.background.set(dev.mordo.kairo.core.MainMenuSettings.VANILLA_BACKGROUND);
			dev.mordo.kairo.core.ClientSettings.INSTANCE.customMainMenu.set(false);
			mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
		});
		ctx.waitTicks(200);
		ctx.takeScreenshot("p9_07_minecraft_style_title");

		// Same title without the friends / language / accessibility icons; Options and Quit move up.
		ctx.runOnClient(mc -> {
			menu.hideTitleIcons.set(true);
			mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("p15_00_title_without_icons");
		boolean iconsGone = ctx.computeOnClient(mc -> mc.gui.screen().children().stream()
			.noneMatch(c -> c instanceof net.minecraft.client.gui.components.SpriteIconButton));
		if (!iconsGone) {
			throw new AssertionError("Title screen icon row wasn't hidden");
		}
		ctx.runOnClient(mc -> menu.hideTitleIcons.reset());

		// Back to defaults for the rest of the tour.
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ClientSettings.INSTANCE.customMainMenu.set(true);
			menu.order.reset();
			menu.hiddenButtons.reset();
		});
	}

	/** A test image, a 4-frame animated GIF and a 6-face panorama in config/kairo/backgrounds. */
	private static void writeTestBackgrounds() throws java.io.IOException {
		java.nio.file.Path dir = dev.mordo.kairo.render.background.MenuBackground.folder();
		java.nio.file.Files.createDirectories(dir);
		var sunset = new java.awt.image.BufferedImage(1600, 900, java.awt.image.BufferedImage.TYPE_INT_RGB);
		var g = sunset.createGraphics();
		g.setPaint(new java.awt.GradientPaint(0, 0, new java.awt.Color(0x2B1055), 0, 900, new java.awt.Color(0xF7797D)));
		g.fillRect(0, 0, 1600, 900);
		g.setColor(new java.awt.Color(0xFFD27A));
		g.fillOval(1050, 380, 260, 260);
		g.setColor(new java.awt.Color(0x1A0B2E));
		g.fillPolygon(new int[]{0, 400, 700, 1000, 1300, 1600, 1600, 0}, new int[]{700, 520, 640, 500, 620, 560, 900, 900}, 8);
		g.dispose();
		javax.imageio.ImageIO.write(sunset, "png", dir.resolve("tour-sunset.png").toFile());

		// Animated GIF: a dot moving across, 4 frames at 150 ms.
		var writer = javax.imageio.ImageIO.getImageWritersByFormatName("gif").next();
		try (var out = javax.imageio.ImageIO.createImageOutputStream(dir.resolve("tour-pulse.gif").toFile())) {
			writer.setOutput(out);
			writer.prepareWriteSequence(null);
			for (int f = 0; f < 4; f++) {
				var frame = new java.awt.image.BufferedImage(320, 180, java.awt.image.BufferedImage.TYPE_INT_RGB);
				var fg = frame.createGraphics();
				fg.setColor(new java.awt.Color(0x10203A));
				fg.fillRect(0, 0, 320, 180);
				fg.setColor(new java.awt.Color(0x7FD8FF));
				fg.fillOval(20 + f * 80, 60, 60, 60);
				fg.dispose();
				var type = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(frame);
				var meta = writer.getDefaultImageMetadata(type, null);
				String format = meta.getNativeMetadataFormatName();
				var root = (javax.imageio.metadata.IIOMetadataNode) meta.getAsTree(format);
				var gce = new javax.imageio.metadata.IIOMetadataNode("GraphicControlExtension");
				gce.setAttribute("disposalMethod", "none");
				gce.setAttribute("userInputFlag", "FALSE");
				gce.setAttribute("transparentColorFlag", "FALSE");
				gce.setAttribute("delayTime", "15");
				gce.setAttribute("transparentColorIndex", "0");
				root.appendChild(gce);
				meta.setFromTree(format, root);
				writer.writeToSequence(new javax.imageio.IIOImage(frame, null, meta), null);
			}
			writer.endWriteSequence();
		}

		// Panorama: six coloured faces with a big number on each.
		java.nio.file.Path cube = dir.resolve("tour-cube");
		java.nio.file.Files.createDirectories(cube);
		int[] colors = {0x3A86FF, 0x8338EC, 0xFF006E, 0xFB5607, 0xFFBE0B, 0x06D6A0};
		for (int i = 0; i < 6; i++) {
			var face = new java.awt.image.BufferedImage(512, 512, java.awt.image.BufferedImage.TYPE_INT_RGB);
			var fg = face.createGraphics();
			fg.setColor(new java.awt.Color(colors[i]));
			fg.fillRect(0, 0, 512, 512);
			fg.setColor(java.awt.Color.WHITE);
			fg.setFont(new java.awt.Font(java.awt.Font.SANS_SERIF, java.awt.Font.BOLD, 220));
			fg.drawString(Integer.toString(i), 190, 330);
			fg.dispose();
			javax.imageio.ImageIO.write(face, "png", cube.resolve("panorama_" + i + ".png").toFile());
		}
	}

	/** The waypoints screen: opened with M, every dimension, sorting, the editor, search. */
	private void waypointsTour(ClientGameTestContext ctx, TestInput input) {
		ctx.runOnClient(mc -> {
			var store = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Waypoints.class).store();
			store.add(new dev.mordo.kairo.mods.waypoints.Waypoint("Nether hub", 12, 70, -40, "minecraft:the_nether", 0xFFFF6B8B));
			store.add(new dev.mordo.kairo.mods.waypoints.Waypoint("Fortress", -180, 64, 220, "minecraft:the_nether", 0xFFFFE066));
			store.add(new dev.mordo.kairo.mods.waypoints.Waypoint("End city", 1200, 90, -860, "minecraft:the_end", 0xFFC792FF));
		});
		input.pressKey(key(InputConstants.KEY_M));
		ctx.waitTicks(30);
		boolean opened = ctx.computeOnClient(mc -> mc.gui.screen() instanceof dev.mordo.kairo.mods.waypoints.WaypointsScreen);
		if (!opened) {
			throw new AssertionError("M didn't open the waypoints screen");
		}
		input.setCursorPos(width * 0.5, height * 0.95);
		ctx.waitTicks(5);
		ctx.takeScreenshot("p10_00_waypoints_nearest");

		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Waypoints.class)
			.sort.set(dev.mordo.kairo.mods.waypoints.WaypointsScreen.Sort.NAME));
		input.pressKey(key(InputConstants.KEY_DOWN));
		input.pressKey(key(InputConstants.KEY_DOWN));
		ctx.waitTicks(10);
		ctx.takeScreenshot("p10_01_waypoints_by_name");

		input.pressKey(key(InputConstants.KEY_RETURN));
		ctx.waitTicks(20);
		ctx.takeScreenshot("p10_02_waypoint_editor");
		input.pressKey(key(InputConstants.KEY_ESCAPE)); // leaves the name field
		input.pressKey(key(InputConstants.KEY_RETURN)); // closes the editor
		ctx.waitTicks(10);

		input.typeChars("nether");
		ctx.waitTicks(10);
		ctx.takeScreenshot("p10_03_waypoints_search");
		input.pressKey(key(InputConstants.KEY_ESCAPE)); // leave search
		input.pressKey(key(InputConstants.KEY_ESCAPE)); // close
		ctx.waitTicks(15);
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.Waypoints.class)
			.sort.set(dev.mordo.kairo.mods.waypoints.WaypointsScreen.Sort.NEAREST));
	}

	/** Phase 8, in a world: pause menu restyle, totem looks, sound pickers, explosions in PvP mode. */
	private void phase8WorldTour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(15);
		ctx.takeScreenshot("p8_10_pause_menu");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);

		// Pause menu with the extra icon row hidden: top part moves down, Save and Quit stays put.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ClientSettings.INSTANCE.hidePauseExtras.set(true));
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(15);
		ctx.takeScreenshot("p9_10_pause_menu_compact");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ClientSettings.INSTANCE.hidePauseExtras.set(false));

		// Item Scale: sword and totem before and after.
		server.runCommand("item replace entity @a weapon.mainhand with netherite_sword");
		server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(15);
		ctx.takeScreenshot("p10_00_items_normal");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.ItemScale.class).enabled.set(true));
		ctx.waitTicks(5);
		ctx.takeScreenshot("p10_01_items_scaled");
		ctx.runOnClient(mc -> {
			var mod = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.ItemScale.class);
			mod.mainHand.set(0.45);
			mod.offHand.set(0.45);
			mod.height.set(-0.08);
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("p10_02_items_tiny_lower");
		ctx.runOnClient(mc -> {
			var mod = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.ItemScale.class);
			mod.mainHand.reset();
			mod.offHand.reset();
			mod.height.reset();
			mod.enabled.set(false);
		});

		// F2: preview card, then the chat message with its buttons.
		input.pressKey(key(InputConstants.KEY_F2));
		ctx.waitTicks(25);
		ctx.takeScreenshot("p9_11_screenshot_card");
		input.pressKey(key(InputConstants.KEY_T));
		ctx.waitTicks(10);
		ctx.takeScreenshot("p9_12_screenshot_chat");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);

		// Built-in sounds load and resolve.
		boolean soundsOk = ctx.computeOnClient(mc -> dev.mordo.kairo.sound.SoundLibrary.resolve("builtin:beep") != null
			&& dev.mordo.kairo.sound.SoundLibrary.resolve("builtin:ding") != null);
		if (!soundsOk) {
			throw new AssertionError("Built-in sounds didn't resolve");
		}
		ctx.runOnClient(mc -> {
			var hits = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.HitSounds.class);
			hits.hurtSound.set("builtin:beep");
			var totem = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.TotemChanger.class);
			totem.popSound.set("builtin:chime");
		});

		// Totem look: a built-in recolour (reloads resources once).
		server.runCommand("give @a totem_of_undying 2");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.TotemChanger.class).look.set("tint:frost"));
		ctx.waitTicks(200);
		input.pressKey(key(InputConstants.KEY_1));
		ctx.waitTicks(5);
		ctx.takeScreenshot("p8_11_totem_frost");

		// Totem look from a JPEG with a plain background (made here), background removed.
		ctx.runOnClient(mc -> {
			try {
				java.nio.file.Path dir = dev.mordo.kairo.mods.TotemChanger.folder();
				java.nio.file.Files.createDirectories(dir);
				var img = new java.awt.image.BufferedImage(200, 160, java.awt.image.BufferedImage.TYPE_INT_RGB);
				var g = img.createGraphics();
				g.setColor(java.awt.Color.WHITE);
				g.fillRect(0, 0, 200, 160);
				g.setColor(new java.awt.Color(0xE0, 0x3A, 0x8C));
				g.fillOval(50, 20, 100, 120);
				g.setColor(new java.awt.Color(0xFF, 0xD1, 0x4A));
				g.fillOval(78, 50, 44, 44);
				g.dispose();
				javax.imageio.ImageIO.write(img, "jpg", dir.resolve("tour-test.jpg").toFile());
			} catch (java.io.IOException e) {
				throw new RuntimeException(e);
			}
			var totem = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.TotemChanger.class);
			totem.removeBackground.set(true);
			totem.look.set("file:tour-test.jpg");
		});
		ctx.waitTicks(200);
		ctx.takeScreenshot("p8_12_totem_image");

		// Pop it (chime instead of the vanilla sound).
		server.runCommand("gamemode survival @a");
		server.runCommand("item replace entity @a weapon.offhand with totem_of_undying");
		server.runCommand("damage @p 1000 minecraft:generic");
		ctx.waitTicks(5);
		ctx.takeScreenshot("p8_13_totem_pop_image");
		ctx.waitTicks(40);
		server.runCommand("gamemode creative @a");

		// Settings pages with the new pickers.
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.forModSettings(
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.TotemChanger.class), null)));
		ctx.waitTicks(25);
		ctx.takeScreenshot("p8_14_totem_settings");
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.forModSettings(
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.HitSounds.class), null)));
		ctx.waitTicks(25);
		ctx.takeScreenshot("p8_15_hit_sounds");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);

		// Back to the vanilla totem for the rest of the tour.
		ctx.runOnClient(mc -> dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.TotemChanger.class).look.set("vanilla"));
		ctx.waitTicks(200);

		// Crystal explosions: normal, then PvP mode (no flash, little debris).
		server.runCommand("summon end_crystal 0 -58 4");
		ctx.waitTicks(5);
		server.runCommand("damage @e[type=end_crystal,limit=1] 1");
		ctx.waitTicks(3);
		ctx.takeScreenshot("p8_16_explosion_normal");
		ctx.waitTicks(60);
		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.set(true));
		server.runCommand("summon end_crystal 0 -58 4");
		ctx.waitTicks(5);
		server.runCommand("damage @e[type=end_crystal,limit=1] 1");
		ctx.waitTicks(3);
		ctx.takeScreenshot("p8_17_explosion_pvp");
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.set(false));
		ctx.waitTicks(20);

		// The worlds screen with a world in it (this one), opened over the game.
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.WorldsScreen(null)));
		ctx.waitTicks(30);
		moveTo(input, 400, 200);
		ctx.waitTicks(10);
		ctx.takeScreenshot("p8_18_worlds_list");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(10);
	}

	/** The normal-layer layout of every HUD element, for comparing before/after PvP edits. */
	private static String normalLayout() {
		StringBuilder sb = new StringBuilder();
		for (var e : dev.mordo.kairo.hud.HudManager.elements()) {
			sb.append(e.id()).append(' ').append(e.enabled.getNormal()).append(' ').append(e.anchor.getNormal())
				.append(' ').append(e.offsetX.getNormal()).append(' ').append(e.offsetY.getNormal())
				.append(' ').append(e.scale.getNormal()).append('\n');
		}
		return sb.toString();
	}

	private double pxPerUnit() {
		return Math.min(height / 810.0, width / 1080.0);
	}

	private void moveTo(TestInput input, double refX, double refY) {
		input.setCursorPos(refX * pxPerUnit(), refY * pxPerUnit());
	}

	private void click(TestInput input, double refX, double refY) {
		moveTo(input, refX, refY);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
	}

	private static boolean flag(String name) {
		return java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.dir")).resolve("../../" + name).normalize());
	}

	/** The first-run setup, step by step. */
	private void welcomeTour(ClientGameTestContext ctx, TestInput input) {
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.welcome.WelcomeScreen(null)));
		input.setCursorPos(width / 2.0, height * 0.95);
		ctx.waitTicks(10);
		ctx.takeScreenshot("w_00_welcome_intro");
		ctx.waitTicks(30);
		ctx.takeScreenshot("w_01_welcome");
		for (int i = 1; i < 9; i++) {
			input.pressKey(key(InputConstants.KEY_RIGHT));
			ctx.waitTicks(14);
			if (i == 2) {
				click(input, 766, 325); // Rose
				ctx.waitTicks(10);
			}
			ctx.takeScreenshot("w_0" + (i + 1) + "_step_" + i);
		}
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ClientSettings.INSTANCE.accent.reset();
			dev.mordo.kairo.cosmetics.Cosmetics.get().cape.reset();
			dev.mordo.kairo.cosmetics.Cosmetics.get().wings.reset();
			mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
		});
		ctx.waitTicks(10);
	}

	/** Dashboard main menu, the style picker and the welcome style step. */
	private void updateMenuTour(ClientGameTestContext ctx, TestInput input) {
		var menu = dev.mordo.kairo.core.MainMenuSettings.INSTANCE;
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ClientSettings.INSTANCE.onboarded.set(true);
			var cos = dev.mordo.kairo.cosmetics.Cosmetics.get();
			cos.wings.set(dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.ANGEL);
			cos.hat.set(dev.mordo.kairo.cosmetics.Cosmetics.HatStyle.CAT_EARS);
			cos.cape.set("design:aurora");
			menu.layout.set(dev.mordo.kairo.core.MainMenuSettings.Layout.DASHBOARD);
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		input.setCursorPos(width * 0.5, height * 0.97);
		ctx.waitTicks(60);
		ctx.takeScreenshot("u_00_dashboard");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.toggle());
		ctx.waitTicks(12);
		ctx.takeScreenshot("u_00b_dashboard_pvp");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.PvpMode.toggle());
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuSettingsScreen(mc.gui.screen())));
		ctx.waitTicks(30);
		ctx.takeScreenshot("u_01_style_picker");
		ctx.runOnClient(mc -> {
			var cos = dev.mordo.kairo.cosmetics.Cosmetics.get();
			cos.wings.reset();
			cos.hat.reset();
			cos.cape.reset();
		});
		// Custom home screen: edit mode, resize the skin, add a text widget, done.
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.gui.main.MainScreens.testOverride = false;
			dev.mordo.kairo.gui.main.MainMenuScreen.startEditing = true;
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		ctx.waitTicks(40);
		ctx.takeScreenshot("h_00_edit_mode");
		double u = height / 810.0;
		input.setCursorPos(1384 * u, 560 * u);
		ctx.waitTicks(2);
		input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(2);
		for (int step = 1; step <= 8; step++) {
			input.setCursorPos(1384 * u, (560 - step * 20) * u);
			ctx.waitTicks(1);
		}
		ctx.takeScreenshot("h_01_resizing");
		input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(5);
		input.setCursorPos(810 * u, 40 * u);
		ctx.waitTicks(2);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(8);
		ctx.takeScreenshot("h_02_picker");
		input.setCursorPos(519 * u, 509 * u);
		ctx.waitTicks(2);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(8);
		ctx.takeScreenshot("h_03_added_text");
		input.setCursorPos(288 * u, 515 * u);
		ctx.waitTicks(2);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(8);
		ctx.takeScreenshot("h_03b_button_options");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(4);
		ctx.runOnClient(mc -> {
			if (mc.gui.screen() instanceof dev.mordo.kairo.gui.main.MainMenuScreen) {
				mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuSettingsScreen(mc.gui.screen()));
			}
		});
		ctx.waitTicks(25);
		ctx.takeScreenshot("h_04_settings_custom");
		ctx.runOnClient(mc -> menu.layout.set(dev.mordo.kairo.core.MainMenuSettings.Layout.DOCK));
		ctx.waitTicks(8);
		ctx.takeScreenshot("h_05_settings_dock");
		ctx.runOnClient(mc -> {
			menu.layout.set(dev.mordo.kairo.core.MainMenuSettings.Layout.DASHBOARD);
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		ctx.waitTicks(40);
		ctx.takeScreenshot("h_06_custom_after");
		ctx.runOnClient(mc -> {
			menu.homeLayout.reset();
		});

		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.welcome.WelcomeScreen(null)));
		ctx.waitTicks(10);
		for (int i = 0; i < 3; i++) {
			input.pressKey(key(InputConstants.KEY_RIGHT));
			ctx.waitTicks(4);
		}
		ctx.waitTicks(16);
		ctx.takeScreenshot("u_02_welcome_styles");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ClientSettings.INSTANCE.accent.reset();
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		ctx.waitTicks(5);
	}

	/** In a world: ring editor, new wings, cat ears, animated cape, held item looks, jumps, explosions, damage numbers. */
	private void updateWorldTour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		var v = dev.mordo.kairo.core.GameVisuals.INSTANCE;
		var c = dev.mordo.kairo.cosmetics.Cosmetics.get();
		// Outline with Item Scale on, on a sword and on a block; gradient accent; hold-to-pick emote wheel.
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.ItemScale.class).enabled.set(true);
			v.heldShine.set(dev.mordo.kairo.core.GameVisuals.HeldShine.OUTLINE);
			v.glintColor.set(dev.mordo.kairo.core.GameVisuals.GlintColor.CUSTOM);
			v.glintCustom.set(0xFF22E39A);
		});
		server.runCommand("item replace entity @p weapon.mainhand with netherite_sword");
		ctx.waitTicks(30);
		ctx.takeScreenshot("o_00_outline_scaled_sword");
		server.runCommand("item replace entity @p weapon.mainhand with smooth_stone");
		ctx.waitTicks(30);
		ctx.takeScreenshot("o_01_outline_block");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.ItemScale.class).enabled.set(false);
			v.glintCustom.setMode(dev.mordo.kairo.core.settings.ColorSetting.Mode.CHROMA);
		});
		ctx.waitTicks(20);
		ctx.takeScreenshot("o_02_outline_block_chroma");
		ctx.runOnClient(mc -> {
			v.heldShine.reset();
			v.glintColor.reset();
			v.glintCustom.reset();
			var accent = dev.mordo.kairo.core.ClientSettings.INSTANCE.accent;
			accent.setMode(dev.mordo.kairo.core.settings.ColorSetting.Mode.GRADIENT);
			accent.setStops(java.util.List.of(0xFFFF5CA8, 0xFF7FD8FF, 0xFFFFC94D));
			accent.setSpeed(3f);
			mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visuals, null));
		});
		ctx.waitTicks(25);
		ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).visuals().select(5));
		ctx.waitTicks(10);
		ctx.takeScreenshot("o_03_gradient_accent_a");
		ctx.waitTicks(20);
		ctx.takeScreenshot("o_04_gradient_accent_b");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.core.ClientSettings.INSTANCE.accent.reset();
			mc.gui.setScreen(null);
		});
		ctx.waitTicks(10);
		input.holdKey(key(InputConstants.KEY_B));
		ctx.waitTicks(4);
		// The keyboard's auto-repeat while B is held (these used to close the wheel).
		for (int r = 0; r < 6; r++) {
			ctx.runOnClient(mc -> mc.gui.screen().keyPressed(new net.minecraft.client.input.KeyEvent(InputConstants.KEY_B, 0, 0)));
			ctx.waitTicks(1);
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.Kairo.LOGGER.info("CHECK wheel open after repeats: {}", mc.gui.screen()));
		input.setCursorPos(width / 2.0 + 200 * (height / 810.0), height / 2.0);
		ctx.waitTicks(8);
		ctx.takeScreenshot("o_05_wheel_holding");
		input.releaseKey(key(InputConstants.KEY_B));
		ctx.waitTicks(3);
		ctx.runOnClient(mc -> dev.mordo.kairo.Kairo.LOGGER.info("CHECK emote after hold-release: {} screen {}", dev.mordo.kairo.cosmetics.Emotes.current(), mc.gui.screen()));
		ctx.runOnClient(mc -> dev.mordo.kairo.cosmetics.Emotes.stop());
		ctx.waitTicks(5);

		// Fix checks: wings turning in the wardrobe preview, bandana tiles, filter tiles, held glow.
		ctx.runOnClient(mc -> {
			c.wings.set(dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.ANGEL);
			mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::cosmetics, null));
		});
		input.setCursorPos(width * 0.97, height * 0.97);
		ctx.waitTicks(25);
		ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).cosmetics().select(1));
		for (int i = 0; i < 4; i++) {
			ctx.waitTicks(22);
			ctx.takeScreenshot("f_0" + i + "_wings_turning");
		}
		ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).cosmetics().select(3));
		ctx.waitTicks(10);
		ctx.takeScreenshot("f_10_bandana_tiles");
		ctx.runOnClient(mc -> {
			c.wings.reset();
			mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visuals, null));
		});
		ctx.waitTicks(25);
		ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).visuals().select(1));
		ctx.waitTicks(10);
		ctx.takeScreenshot("f_11_filter_tiles");
		ctx.runOnClient(mc -> {
			mc.gui.setScreen(null);
			v.heldShine.set(dev.mordo.kairo.core.GameVisuals.HeldShine.OUTLINE);
		});
		server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
		ctx.waitTicks(30);
		ctx.takeScreenshot("f_20_held_glow");
		ctx.runOnClient(mc -> v.heldShine.reset());
		int[][] inspects = {{0, 20}, {1, 11}, {2, 11}, {3, 16}};
		for (int[] ins : inspects) {
			ctx.runOnClient(mc -> {
				v.inspectStyle.set(dev.mordo.kairo.core.GameVisuals.InspectStyle.values()[ins[0]]);
				dev.mordo.kairo.render.HandAnimations.inspect();
			});
			ctx.waitTicks(ins[1]);
			ctx.takeScreenshot("f_3" + ins[0] + "_inspect_" + v.inspectStyle.get().name().toLowerCase(java.util.Locale.ROOT));
			ctx.waitTicks(45);
		}

		// Ring with Waypoints and Emotes added, and the editor.
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.gui.menu.RingSection.toggle(dev.mordo.kairo.gui.menu.RingSection.WAYPOINTS);
			dev.mordo.kairo.gui.menu.RingSection.toggle(dev.mordo.kairo.gui.menu.RingSection.EMOTES);
		});
		input.setCursorPos(width / 2.0, height / 2.0);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(25);
		ctx.takeScreenshot("u_10_ring_eight");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visuals, null)));
		input.setCursorPos(width * 0.97, height * 0.97);
		ctx.waitTicks(25);
		for (int[] pick : new int[][]{{0, 11}, {3, 12}, {4, 13}}) {
			ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).visuals().select(pick[0]));
			ctx.waitTicks(10);
			ctx.takeScreenshot("u_" + pick[1] + "_visuals_" + pick[0]);
		}
		ctx.runOnClient(mc -> {
			mc.gui.setScreen(null);
			dev.mordo.kairo.core.ClientSettings.INSTANCE.ringSegments.reset();
		});

		// New wings from the back, cat ears up close, an animated cape.
		ctx.runOnClient(mc -> {
			mc.options.fov().set(40);
			c.cape.set("design:aurora");
			c.hat.set(dev.mordo.kairo.cosmetics.Cosmetics.HatStyle.CAT_EARS);
			c.hatColor.set(0xFFF2A65A);
		});
		for (var wing : new dev.mordo.kairo.cosmetics.Cosmetics.WingStyle[]{dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.SERAPH,
			dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.ENERGY, dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.SKELETAL}) {
			ctx.runOnClient(mc -> {
				c.wings.set(wing);
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
			});
			ctx.waitTicks(8);
			ctx.takeScreenshot("u_2" + wing.ordinal() + "_wings_" + wing.name().toLowerCase(java.util.Locale.ROOT));
		}
		ctx.waitTicks(10);
		ctx.takeScreenshot("u_29_aurora_later");
		ctx.runOnClient(mc -> {
			c.wings.reset();
			mc.options.fov().set(20);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		});
		for (int i = 0; i < 6; i++) {
			ctx.waitTicks(11);
			ctx.takeScreenshot("u_3" + i + "_cat_ears");
		}
		ctx.runOnClient(mc -> {
			c.earSpacing.set(1.5);
			c.earAngle.set(25.0);
			c.earSize.set(1.3);
			c.hatHeight.set(1.0);
		});
		ctx.waitTicks(5);
		ctx.takeScreenshot("u_36_cat_ears_fit");
		ctx.runOnClient(mc -> {
			for (var s : java.util.List.of(c.earSpacing, c.earAngle, c.earSize, c.hatHeight, c.hat, c.hatColor, c.cape)) {
				s.reset();
			}
			mc.options.fov().set(70);
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});

		// Held item: shimmer + glow, rainbow shimmer colour.
		server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
		ctx.runOnClient(mc -> {
			v.heldShine.set(dev.mordo.kairo.core.GameVisuals.HeldShine.BOTH);
			v.glintColor.set(dev.mordo.kairo.core.GameVisuals.GlintColor.CUSTOM);
			v.glintCustom.set(0xFFFF3BA7);
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("u_40_held_rainbow");
		ctx.runOnClient(mc -> v.glintColor.set(dev.mordo.kairo.core.GameVisuals.GlintColor.ACCENT));
		ctx.waitTicks(5);
		ctx.takeScreenshot("u_41_held_accent");
		ctx.runOnClient(mc -> {
			v.heldShine.reset();
			v.glintColor.reset();
		});

		// Damage numbers and hit flash on a husk.
		ctx.runOnClient(mc -> {
			v.damageNumbers.set(true);
			v.hitFlash.set(true);
		});
		server.runCommand("gamemode survival @p");
		input.lookAt(new net.minecraft.core.BlockPos(0, -59, 2));
		server.runCommand("summon husk 0 -60 2.2 {NoAI:1b,Health:20f,Rotation:[180f,0f]}");
		ctx.waitTicks(10);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(4);
		ctx.takeScreenshot("u_50_damage_number");
		server.runCommand("kill @e[type=husk]");
		ctx.runOnClient(mc -> {
			v.damageNumbers.reset();
			v.hitFlash.reset();
		});
		server.runCommand("gamemode creative @p");

		// Jump effect from the side, explosion with shockwave + flash.
		ctx.runOnClient(mc -> {
			v.jumpEffect.set(dev.mordo.kairo.core.GameVisuals.JumpEffect.RING);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitTicks(5);
		input.holdKeyFor((net.minecraft.client.Options o) -> o.keyJump, 3);
		ctx.waitTicks(2);
		ctx.takeScreenshot("u_60_jump_ring");
		ctx.runOnClient(mc -> {
			v.jumpEffect.reset();
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			v.explosionStyle.set(dev.mordo.kairo.core.GameVisuals.ExplosionStyle.SHOCKWAVE);
			v.explosionFlash.set(0.6);
			v.explosionShake.set(1.0);
		});
		ctx.waitTicks(20);
		input.lookAt(new net.minecraft.core.BlockPos(0, -60, 8));
		server.runCommand("summon tnt 0 -60 8 {fuse:1}");
		ctx.waitTicks(3);
		ctx.takeScreenshot("u_61_explosion_a");
		ctx.waitTicks(4);
		ctx.takeScreenshot("u_62_explosion_b");
		ctx.runOnClient(mc -> {
			v.explosionStyle.reset();
			v.explosionFlash.reset();
			v.explosionShake.reset();
			v.speedLines.set(1.0);
		});
		// Speed lines while sprinting with Speed.
		server.runCommand("effect give @p speed 10 6");
		server.runCommand("tp @p 0 -60 0 180 0");
		ctx.waitTicks(5);
		input.holdKey((net.minecraft.client.Options o) -> o.keySprint);
		input.holdKey((net.minecraft.client.Options o) -> o.keyUp);
		ctx.waitTicks(14);
		ctx.takeScreenshot("u_70_speed_lines");
		input.releaseKey((net.minecraft.client.Options o) -> o.keyUp);
		input.releaseKey((net.minecraft.client.Options o) -> o.keySprint);
		ctx.runOnClient(mc -> v.speedLines.reset());
		ctx.waitTicks(10);
	}

	/** Visuals: the in-game sections, attack and mining animations mid-swing, screen filters, hit and kill effects. */
	private void visualsTour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var v = dev.mordo.kairo.core.GameVisuals.INSTANCE;
		var server = world.getServer();
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visuals, null)));
		input.setCursorPos(width * 0.95, height * 0.95);
		ctx.waitTicks(30);
		for (int i = 0; i < 4; i++) {
			int index = i;
			ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).visuals().select(index));
			ctx.waitTicks(10);
			ctx.takeScreenshot("v_0" + i + "_visuals_section");
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);

		// Attack animations, caught mid-swing (slowed down), with the trail.
		server.runCommand("gamemode survival @p");
		server.runCommand("item replace entity @p weapon.mainhand with diamond_sword");
		ctx.runOnClient(mc -> {
			v.swingDuration.set(2.5);
			v.swingTrail.set(true);
		});
		ctx.waitTicks(5);
		for (var style : dev.mordo.kairo.core.GameVisuals.SwingStyle.values()) {
			ctx.runOnClient(mc -> {
				v.swingStyle.set(style);
				mc.player.swing(net.minecraft.world.InteractionHand.MAIN_HAND, net.minecraft.world.item.component.SwingAnimation.DEFAULT, false);
			});
			ctx.waitTicks(5);
			ctx.takeScreenshot("v_1" + style.ordinal() + "_swing_" + style.name().toLowerCase(java.util.Locale.ROOT));
			ctx.waitTicks(14);
		}
		ctx.runOnClient(mc -> {
			v.swingStyle.reset();
			v.swingDuration.reset();
			v.swingTrail.reset();
		});

		// Hit particles, then each kill effect on a one-health husk (husks don't burn in daylight).
		ctx.runOnClient(mc -> v.hitBurst.set(dev.mordo.kairo.core.GameVisuals.HitBurst.SPARKS));
		input.lookAt(new net.minecraft.core.BlockPos(0, -59, 2));
		server.runCommand("summon husk 0 -60 2.2 {NoAI:1b,Health:20f,Rotation:[180f,0f]}");
		ctx.waitTicks(10);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(3);
		ctx.takeScreenshot("v_30_hit_sparks");
		server.runCommand("kill @e[type=husk]");
		ctx.waitTicks(30);
		String[] kills = {"LIGHTNING", "FIREWORK", "VORTEX", "SOUL", "TOTEM"};
		for (String kill : kills) {
			ctx.runOnClient(mc -> {
				v.hitBurst.set(dev.mordo.kairo.core.GameVisuals.HitBurst.NONE);
				v.killEffect.set(Enum.valueOf(dev.mordo.kairo.core.GameVisuals.KillEffect.class, kill));
			});
			server.runCommand("summon husk 0 -60 2.2 {NoAI:1b,Health:1f,Rotation:[180f,0f]}");
			ctx.waitTicks(10);
			input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(3);
			ctx.takeScreenshot("v_31_kill_" + kill.toLowerCase(java.util.Locale.ROOT) + "_a");
			ctx.waitTicks(8);
			ctx.takeScreenshot("v_31_kill_" + kill.toLowerCase(java.util.Locale.ROOT) + "_b");
			ctx.waitTicks(40);
		}
		server.runCommand("kill @e[type=husk]");
		ctx.runOnClient(mc -> {
			v.hitBurst.reset();
			v.killEffect.reset();
		});
		// Mining animations on obsidian with a wooden pickaxe (it never breaks).
		server.runCommand("setblock 0 -59 2 obsidian");
		server.runCommand("item replace entity @p weapon.mainhand with wooden_pickaxe");
		ctx.waitTicks(5);
		input.lookAt(new net.minecraft.core.BlockPos(0, -59, 2));
		for (var style : dev.mordo.kairo.core.GameVisuals.MiningStyle.values()) {
			ctx.runOnClient(mc -> v.miningStyle.set(style));
			input.holdMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(13);
			ctx.takeScreenshot("v_4" + style.ordinal() + "_mining_" + style.name().toLowerCase(java.util.Locale.ROOT) + "_a");
			ctx.waitTicks(4);
			ctx.takeScreenshot("v_4" + style.ordinal() + "_mining_" + style.name().toLowerCase(java.util.Locale.ROOT) + "_b");
			input.releaseMouse(InputConstants.MOUSE_BUTTON_LEFT);
			ctx.waitTicks(20);
		}
		ctx.runOnClient(mc -> v.miningStyle.reset());
		server.runCommand("setblock 0 -59 2 air");
		server.runCommand("item replace entity @p weapon.mainhand with air");
		server.runCommand("gamemode creative @p");
		server.runCommand("tp @p 0 -60 0 0 0");
		ctx.waitTicks(10);

		// Screen filters and effects.
		var F = dev.mordo.kairo.core.GameVisuals.Filter.class;
		String[][] looks = {{"CINEMATIC", "0", "0.3", "0", "0", "0.6"}, {"DREAMY", "0.8", "0", "0", "0", "0"}, {"NOIR", "0", "0.6", "0.6", "0", "1"},
			{"VAPORWAVE", "0.4", "0.5", "0", "0.7", "0"}, {"NEON", "0.6", "0.4", "0", "0.3", "0"}, {"RETRO", "0", "0.5", "0.8", "0", "0"},
			{"SUNSET", "0.3", "0.3", "0", "0", "0"}, {"MATRIX", "0.5", "0.6", "0.3", "0", "0"}, {"WARM", "0", "0", "0", "0", "0"},
			{"COLD", "0", "0", "0", "0", "0"}, {"VIBRANT", "0", "0", "0", "0", "0"}};
		int n = 0;
		for (String[] look : looks) {
			ctx.runOnClient(mc -> {
				v.filter.set(Enum.valueOf(F, look[0]));
				v.glow.set(Double.parseDouble(look[1]));
				v.vignette.set(Double.parseDouble(look[2]));
				v.grain.set(Double.parseDouble(look[3]));
				v.chromatic.set(Double.parseDouble(look[4]));
				v.letterbox.set(Double.parseDouble(look[5]));
			});
			ctx.waitTicks(4);
			ctx.takeScreenshot("v_5" + (n < 10 ? "0" : "") + n++ + "_filter_" + look[0].toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> {
			for (var setting : java.util.List.of(v.filter, v.glow, v.vignette, v.grain, v.chromatic, v.letterbox)) {
				setting.reset();
			}
		});
		ctx.waitTicks(5);
	}

	/** The wardrobe, cosmetics on the player from both sides, emotes and the emote wheel. */
	private void cosmeticsTour(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var c = dev.mordo.kairo.cosmetics.Cosmetics.get();
		ctx.runOnClient(mc -> {
			c.cape.set("design:galaxy");
			c.wings.set(dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.ANGEL);
			c.hat.set(dev.mordo.kairo.cosmetics.Cosmetics.HatStyle.CROWN);
			c.bandana.set(dev.mordo.kairo.cosmetics.Cosmetics.BandanaStyle.SCARF);
			mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::cosmetics, null));
		});
		input.setCursorPos(width * 0.9, height * 0.9);
		ctx.waitTicks(30);
		String[] names = {"capes", "wings", "hats", "bandanas", "trails", "emotes"};
		for (int i = 0; i < names.length; i++) {
			int index = i;
			ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).cosmetics().select(index));
			ctx.waitTicks(10);
			ctx.takeScreenshot("c_0" + i + "_wardrobe_" + names[i]);
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);

		// On the player: zoomed third person, front and back, a different set each time.
		var wings = dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.values();
		var hats = dev.mordo.kairo.cosmetics.Cosmetics.HatStyle.values();
		var bandanas = dev.mordo.kairo.cosmetics.Cosmetics.BandanaStyle.values();
		String[] capes = {"design:kairo", "design:sunset", "design:ocean", "design:aurora", "design:flames", "design:frost", "design:rainbow",
			"design:neon", "design:lava", "design:cherry", "design:midnight", "design:carbon"};
		ctx.runOnClient(mc -> {
			mc.options.fov().set(40);
		});
		for (int i = 1; i < hats.length; i++) {
			int n = i;
			ctx.runOnClient(mc -> {
				c.wings.set(wings[1 + (n - 1) % (wings.length - 1)]);
				c.hat.set(hats[n]);
				c.bandana.set(bandanas[1 + (n - 1) % (bandanas.length - 1)]);
				c.cape.set(capes[(n - 1) % capes.length]);
				mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
			});
			ctx.waitTicks(8);
			String tag = (n < 10 ? "0" : "") + n;
			ctx.takeScreenshot("c_1" + tag + "_front_" + hats[n].name().toLowerCase(java.util.Locale.ROOT));
			ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK));
			ctx.waitTicks(6);
			ctx.takeScreenshot("c_1" + tag + "_back_" + wings[1 + (n - 1) % (wings.length - 1)].name().toLowerCase(java.util.Locale.ROOT));
		}
		// A trail while standing.
		ctx.runOnClient(mc -> {
			c.trailWhenMoving.set(false);
			c.trail.set(dev.mordo.kairo.cosmetics.Cosmetics.TrailStyle.HEARTS);
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT);
		});
		ctx.waitTicks(30);
		ctx.takeScreenshot("c_20_trail_hearts");
		ctx.runOnClient(mc -> {
			c.trail.reset();
			c.trailWhenMoving.reset();
			c.wings.set(dev.mordo.kairo.cosmetics.Cosmetics.WingStyle.DRAGON);
			c.hat.reset();
			c.bandana.reset();
		});

		// Emotes from the front, plus a side view of the backflip.
		var E = dev.mordo.kairo.cosmetics.Emotes.Emote.class;
		String[] emotes = {"SIT", "LIE_DOWN", "WAVE", "DAB", "CLAP", "DANCE", "CHEER", "SALUTE", "BOW", "T_POSE", "FACEPALM", "SHRUG", "ZOMBIE", "SPIN"};
		for (String name : emotes) {
			var emote = Enum.valueOf(E, name);
			ctx.runOnClient(mc -> dev.mordo.kairo.cosmetics.Emotes.playHere(emote));
			ctx.waitTicks(emote.loops() ? 24 : 12);
			ctx.takeScreenshot("c_3" + name.toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.cosmetics.Emotes.stop();
			mc.player.setYRot(90);
			mc.player.yRotO = 90;
			mc.player.yBodyRot = 90;
			mc.player.yHeadRot = 90;
		});
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> dev.mordo.kairo.cosmetics.Emotes.playHere(dev.mordo.kairo.cosmetics.Emotes.Emote.BACKFLIP));
		for (int i = 0; i < 4; i++) {
			ctx.waitTicks(4);
			ctx.takeScreenshot("c_4" + i + "_backflip");
		}
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.cosmetics.Emotes.playHere(dev.mordo.kairo.cosmetics.Emotes.Emote.SIT);
		});
		ctx.waitTicks(24);
		ctx.takeScreenshot("c_45_sit_side");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.cosmetics.Emotes.stop();
			mc.player.setYRot(0);
			mc.player.yRotO = 0;
			mc.player.yBodyRot = 0;
			mc.player.yHeadRot = 0;
			mc.options.fov().set(70);
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});
		ctx.waitTicks(5);
		// The emote wheel, with a segment hovered.
		ctx.runOnClient(mc -> dev.mordo.kairo.cosmetics.EmoteWheelScreen.open());
		ctx.waitTicks(4);
		input.setCursorPos(width / 2.0 + 150 * pxPerUnit(), height / 2.0 - 120 * pxPerUnit());
		ctx.waitTicks(16);
		ctx.takeScreenshot("c_50_emote_wheel");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.runOnClient(mc -> {
			c.cape.reset();
			c.wings.reset();
		});
		ctx.waitTicks(5);
	}

	/** Puts the cursor on a segment's label (reference radius 201, screen = 810 units tall). */
	private void hoverSegment(TestInput input, int index) {
		double pxPerUnit = Math.min(height / 810.0, width / 1080.0);
		double angle = -Math.PI / 2 + index * Math.PI * 2 / 6;
		input.setCursorPos(width / 2.0 + Math.cos(angle) * 201 * pxPerUnit, height / 2.0 + Math.sin(angle) * 201 * pxPerUnit);
	}

	private static InputConstants.Key key(int scancode) {
		return InputConstants.Type.KEYBOARD.getOrCreate(scancode);
	}

	/** Something colourful in front of the camera so blur and glass are easy to judge. */
	private static void buildScenery(TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("time set day");
		server.runCommand("gamerule advance_time false");
		server.runCommand("gamerule advance_weather false");
		String[] colors = {"red", "orange", "yellow", "lime", "light_blue", "blue", "purple", "magenta", "white", "black"};
		for (int i = 0; i < colors.length; i++) {
			int x = -10 + i * 2;
			server.runCommand("fill " + x + " -60 6 " + (x + 1) + " -52 6 " + colors[i] + "_concrete");
		}
		server.runCommand("tp @a 0 -60 0 0 0");
	}
}
