package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.config.ConfigManager;
import dev.mordo.kairo.core.update.UpdateChecker;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.hud.CpsHud;
import dev.mordo.kairo.hud.KeystrokesHud;
import dev.mordo.kairo.render.ColorScience;
import dev.mordo.kairo.render.background.MenuBackground;
import dev.mordo.kairo.render.background.PaletteExtractor;
import dev.mordo.kairo.render.background.WallpaperColors;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.gui.screens.TitleScreen;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * 0.2.0 checks and screenshots, in either edition (prefix "k_" or "v_"): wallpaper colours from a red, a blue and a
 * very bright picture on every kind of screen, contrast, the config upgrade from 0.1.0, look codes, the update check
 * (a real request to GitHub, plus a sample update on the About page), the CPS label and CPS in Keystrokes.
 * Run it with an empty file build/tour-020. Lines starting "CHECK" in the log are the results to read.
 */
final class Update020Tour {
	private static final int W = 1600;
	private static final int H = 900;
	private final String p = Edition.isVoid() ? "v_" : "k_";

	void run(ClientGameTestContext ctx) {
		TestInput input = ctx.getInput();
		input.resizeWindow(W, H);
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.waitTicks(5);
		ctx.runOnClient(mc -> ClientSettings.INSTANCE.onboarded.set(true));

		paletteChecks(ctx);
		contrastChecks(ctx);
		updateChecks(ctx);
		migrationCheck(ctx);
		lookCodeCheck(ctx);

		writeWallpapers();
		wallpaper(ctx, input, "red", true);
		wallpaper(ctx, input, "blue", false);
		wallpaper(ctx, input, "bright", true);
		tintsOff(ctx, input);
		welcome(ctx, input);
		about(ctx, input);
		world(ctx, input);

		// Back to plain settings, and end on Minecraft's title screen (the game test needs that).
		ctx.runOnClient(mc -> {
			ClientSettings s = ClientSettings.INSTANCE;
			s.wallpaperColors.reset();
			s.accent.reset();
			MainMenuSettings.INSTANCE.background.reset();
			s.checkUpdates.reset();
			mc.gui.setScreen(new TitleScreen());
		});
		ctx.waitTicks(10);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot(p + name);
	}

	private static String hex(int c) {
		return String.format(Locale.ROOT, "#%06X", c & 0xFFFFFF);
	}

	private static String hexes(List<Integer> list) {
		return String.join(" ", list.stream().map(Update020Tour::hex).toList());
	}

	// ---- pictures ----

	private static BufferedImage picture(String kind) {
		BufferedImage img = new BufferedImage(960, 540, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = img.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		Random random = new Random(kind.hashCode());
		switch (kind) {
			case "red" -> {
				// A deep red evening: dark maroon to crimson, a warm glow and some dark shapes.
				g.setPaint(new GradientPaint(0, 0, new Color(0x2A0306), 960, 540, new Color(0xB3121C)));
				g.fillRect(0, 0, 960, 540);
				g.setColor(new Color(0xE0482A));
				g.fillOval(560, 120, 220, 220);
				g.setColor(new Color(0x14070A));
				for (int i = 0; i < 14; i++) {
					int x = random.nextInt(960);
					g.fillRect(x, 380 + random.nextInt(60), 30 + random.nextInt(60), 200);
				}
			}
			case "blue" -> {
				// Deep sea: navy to bright blue, cyan light and a teal floor.
				g.setPaint(new GradientPaint(0, 0, new Color(0x2F7DE1), 0, 540, new Color(0x061430)));
				g.fillRect(0, 0, 960, 540);
				g.setColor(new Color(0x48E0F0));
				for (int i = 0; i < 6; i++) {
					g.fillOval(random.nextInt(900), random.nextInt(300), 40, 40);
				}
				g.setColor(new Color(0x0E5A5A));
				g.fillRect(0, 450, 960, 90);
			}
			case "bright" -> {
				// Very bright and pale: cream with butter yellow, blush pink and a pale sky.
				g.setColor(new Color(0xFFF8E8));
				g.fillRect(0, 0, 960, 540);
				g.setColor(new Color(0xFFE48A));
				g.fillOval(-100, 200, 500, 500);
				g.setColor(new Color(0xFFD3DE));
				g.fillOval(520, -80, 520, 420);
				g.setColor(new Color(0xD8EEFF));
				g.fillRect(300, 0, 200, 540);
			}
			default -> {
				// Grey: concrete and shadows.
				g.setPaint(new GradientPaint(0, 0, new Color(0x9A9A9A), 960, 540, new Color(0x2E2E2E)));
				g.fillRect(0, 0, 960, 540);
			}
		}
		g.dispose();
		return img;
	}

	private static void writeWallpapers() {
		try {
			Path dir = MenuBackground.folder();
			Files.createDirectories(dir);
			for (String kind : new String[]{"red", "blue", "bright"}) {
				ImageIO.write(picture(kind), "png", dir.resolve("test_" + kind + ".png").toFile());
			}
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ---- logic checks ----

	private void paletteChecks(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			for (String kind : new String[]{"red", "blue", "bright", "grey"}) {
				BufferedImage img = picture(kind);
				int[] px = img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth());
				long t0 = System.nanoTime();
				List<Integer> swatches = PaletteExtractor.extract(px);
				double ms = (System.nanoTime() - t0) / 1e6;
				double minL = 100;
				double maxL = 0;
				for (int c : swatches) {
					double l = ColorScience.lch(c)[0];
					minL = Math.min(minL, l);
					maxL = Math.max(maxL, l);
				}
				double[] first = ColorScience.lch(swatches.getFirst());
				Kairo.LOGGER.info("CHECK palette {}: {} swatches [{}], first hue {} chroma {}, lightness {}..{}, {} ms", kind, swatches.size(),
					hexes(swatches), Math.round(first[2]), Math.round(first[1]), Math.round(minL), Math.round(maxL), String.format(Locale.ROOT, "%.1f", ms));
			}
		});
	}

	/** For each wallpaper's main colour, at full tint strength: main and secondary text against the panel. */
	private void contrastChecks(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			ClientSettings s = ClientSettings.INSTANCE;
			s.backgroundTint.set(1.0);
			s.textTint.set(1.0);
			double worst = 21;
			for (String kind : new String[]{"red", "blue", "bright", "grey"}) {
				BufferedImage img = picture(kind);
				List<Integer> swatches = PaletteExtractor.extract(img.getRGB(0, 0, img.getWidth(), img.getHeight(), null, 0, img.getWidth()));
				for (int c : swatches) {
					s.accent.set(c);
					Theme.refresh();
					worst = Math.min(worst, Math.min(Theme.mainContrast, Theme.softContrast));
				}
				s.accent.set(swatches.getFirst());
				Theme.refresh();
				Kairo.LOGGER.info("CHECK contrast {}: accent {} panel {} text {} soft {} -> main {} soft {}", kind, hex(s.accent.argb()),
					hex(Theme.panelColor()), hex(Theme.TEXT), hex(Theme.TEXT_SOFT), String.format(Locale.ROOT, "%.2f", Theme.mainContrast),
					String.format(Locale.ROOT, "%.2f", Theme.softContrast));
			}
			// Pathological: a near-white accent.
			s.accent.set(0xFFFFFFF0);
			Theme.refresh();
			worst = Math.min(worst, Math.min(Theme.mainContrast, Theme.softContrast));
			Kairo.LOGGER.info("CHECK contrast worst over every swatch at full tint: {} (needs >= 4.5): {}", String.format(Locale.ROOT, "%.2f", worst),
				worst >= 4.5 ? "PASS" : "FAIL");
			// ensureContrast itself: a mid red on a dark panel gets lighter until it passes.
			int fixed = ColorScience.ensureContrast(0xFF803030, 0xFF1A1012, 1f, 4.5);
			Kairo.LOGGER.info("CHECK ensureContrast {} -> {} ({})", hex(0xFF803030), hex(fixed),
				String.format(Locale.ROOT, "%.2f", ColorScience.contrast(fixed, 0xFF1A1012)));
			s.accent.reset();
			s.backgroundTint.reset();
			s.textTint.reset();
			Theme.refresh();
		});
	}

	private void updateChecks(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			String json = """
				{"tag_name":"v0.3.1","draft":false,"prerelease":false,"html_url":"https://github.com/Mordsome/Kairo/releases/tag/v0.3.1",
				 "body":"# Kairo 0.3.1\\n\\n**Bold** and [a link](https://example.com) and `code`.\\n- one\\n- two",
				 "assets":[
				  {"name":"kairo-0.3.1.jar","size":123,"browser_download_url":"https://github.com/Mordsome/Kairo/releases/download/v0.3.1/kairo-0.3.1.jar"},
				  {"name":"kairo-void-0.3.1.jar","size":124,"browser_download_url":"https://github.com/Mordsome/Kairo/releases/download/v0.3.1/kairo-void-0.3.1.jar"},
				  {"name":"SHA256SUMS.txt","size":200,"browser_download_url":"https://github.com/Mordsome/Kairo/releases/download/v0.3.1/SHA256SUMS.txt"},
				  {"name":"kairo-0.3.1.jar","size":1,"browser_download_url":"https://evil.example/kairo-0.3.1.jar"}]}""";
			var normal = UpdateChecker.parse(json, "0.2.0", false);
			var voidOne = UpdateChecker.parse(json, "0.2.0", true);
			var same = UpdateChecker.parse(json, "0.3.1", false);
			var newer = UpdateChecker.parse(json, "0.10.0", false);
			Kairo.LOGGER.info("CHECK update parse: kairo {} {}, void {}, same version -> {}, older release -> {}",
				normal == null ? null : normal.jarName(), normal == null ? null : normal.jarUrl(), voidOne == null ? null : voidOne.jarName(), same, newer);
			Kairo.LOGGER.info("CHECK update notes: {}", normal == null ? null : normal.notes().replace("\n", " / "));
			Kairo.LOGGER.info("CHECK version compare: 0.2.0<0.10.0 {}, 0.2.0=0.2 {}, 1.0.0>0.9.9 {}", UpdateChecker.compare("0.2.0", "0.10.0") < 0,
				UpdateChecker.compare("0.2.0", "0.2") == 0, UpdateChecker.compare("1.0.0", "0.9.9") > 0);
			Kairo.LOGGER.info("CHECK update in a dev run: {}", dev.mordo.kairo.core.update.UpdateInstaller.unavailableReason());
		});
	}

	/** A 0.1.0 profile (Living menus off, a pink accent) loads with its look unchanged. */
	private void migrationCheck(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			try {
				Path dir = net.fabricmc.loader.api.FabricLoader.getInstance().getConfigDir().resolve("kairo").resolve("profiles");
				Files.createDirectories(dir);
				Files.writeString(dir.resolve("migtest.json"), """
					{"configVersion": 1, "pvpMode": false, "holders": {
					  "client": {"settings": {"accent": "#FFFF5577", "menu_fx": false, "living_ring": true}},
					  "cps": {"settings": {"format": "COMBINED"}}}}""");
				ConfigManager.switchProfile("migtest");
				ClientSettings s = ClientSettings.INSTANCE;
				CpsHud cps = ModuleManager.get(CpsHud.class);
				Kairo.LOGGER.info("CHECK migration: accent {} (kept: {}), tint backgrounds {}, tint text {}, living menus {}, panel edge light {}, cps format {}, cps label '{}', wallpaper colours {}",
					hex(s.accent.getNormal()), s.accent.getNormal() == 0xFFFF5577, s.tintBackgrounds.isOn(), s.tintText.isOn(), s.menuFx.isOn(),
					s.panelEdgeLight.isOn(), cps.format.get(), cps.label.get(), s.wallpaperColors.isOn());
				String saved = Files.readString(dir.resolve("migtest.json"));
				Kairo.LOGGER.info("CHECK migration saved as config v2: {}, 0.1.0 file kept as backup: {}", saved.contains("\"configVersion\": 2"),
					Files.exists(dir.resolve("migtest.json.v1-backup")));
				ConfigManager.switchProfile("default");
				ConfigManager.deleteProfile("migtest");
				Files.deleteIfExists(dir.resolve("migtest.json.v1-backup"));
				Kairo.LOGGER.info("CHECK fresh profile defaults: tint backgrounds {}, tint text {}, panel edge light {}, check updates {}",
					s.tintBackgrounds.isOn(), s.tintText.isOn(), s.panelEdgeLight.isOn(), s.checkUpdates.isOn());
			} catch (Exception e) {
				Kairo.LOGGER.error("CHECK migration FAILED", e);
			}
		});
	}

	/** Look codes carry the colours and tints, never the wallpaper, and wearing one stops following your wallpaper. */
	private void lookCodeCheck(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			ClientSettings s = ClientSettings.INSTANCE;
			s.accent.set(0xFF33AA77);
			s.textTint.set(0.8);
			String code = dev.mordo.kairo.core.LookCodes.export();
			s.accent.reset();
			s.textTint.reset();
			s.wallpaperColors.set(true);
			int changed = dev.mordo.kairo.core.LookCodes.apply(code);
			Kairo.LOGGER.info("CHECK look code 0.2.0: changed {}, accent {}, text tint {}, wallpaper colours now {}", changed, hex(s.accent.getNormal()),
				s.textTint.get(), s.wallpaperColors.isOn());
			s.accent.reset();
			s.textTint.reset();
			s.wallpaperColors.reset();
		});
	}

	// ---- screens ----

	/** One wallpaper: its colours become the theme; the main menu, its settings, Visuals → Theme, Mods and Minecraft's options. */
	private void wallpaper(ClientGameTestContext ctx, TestInput input, String kind, boolean everything) {
		ctx.runOnClient(mc -> {
			MainMenuSettings.INSTANCE.background.set("image:test_" + kind + ".png");
			ClientSettings.INSTANCE.wallpaperColors.set(true);
			mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
		});
		ctx.waitFor(mc -> !WallpaperColors.swatches().isEmpty(), 400);
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK wallpaper {}: swatches [{}], theme colour {}, picked #{}", kind, hexes(WallpaperColors.swatches()),
			hex(ClientSettings.INSTANCE.accent.argb()), WallpaperColors.pickedIndex()));
		input.setCursorPos(W * 0.97, H * 0.5);
		ctx.waitTicks(20);
		shot(ctx, "20_" + kind + "_main_menu");

		ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.openOn(MenuScreen::visualsPage, null)));
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> {
			MenuScreen menu = (MenuScreen) mc.gui.screen();
			if (Edition.isVoid()) {
				menu.voidVisuals().openVisual("Wallpaper colours");
			} else {
				menu.visuals().select(5);
			}
		});
		ctx.waitTicks(25);
		shot(ctx, "21_" + kind + "_visuals_theme");
		if (!everything) {
			return;
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.openOn(MenuScreen::mods, null)));
		ctx.waitTicks(30);
		shot(ctx, "22_" + kind + "_mods");
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuSettingsScreen(null)));
		ctx.waitTicks(30);
		shot(ctx, "23_" + kind + "_background_picker");
		ctx.runOnClient(mc -> mc.gui.setScreen(new net.minecraft.client.gui.screens.options.OptionsScreen(new TitleScreen(), mc.options)));
		ctx.waitTicks(20);
		shot(ctx, "24_" + kind + "_minecraft_options");
		// Picking another swatch.
		ctx.runOnClient(mc -> {
			WallpaperColors.pick(2);
			mc.gui.setScreen(MenuScreen.openOn(MenuScreen::mods, null));
		});
		ctx.waitTicks(30);
		shot(ctx, "25_" + kind + "_mods_swatch_3");
		ctx.runOnClient(mc -> WallpaperColors.pick(0));
	}

	/** The same red theme with both tints off: the accent changes, panels and text stay neutral. */
	private void tintsOff(ClientGameTestContext ctx, TestInput input) {
		ctx.runOnClient(mc -> {
			MainMenuSettings.INSTANCE.background.set("image:test_red.png");
			ClientSettings.INSTANCE.tintBackgrounds.set(false);
			ClientSettings.INSTANCE.tintText.set(false);
			mc.gui.setScreen(MenuScreen.openOn(MenuScreen::mods, null));
		});
		ctx.waitTicks(40);
		shot(ctx, "26_red_tints_off");
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.tintBackgrounds.reset();
			ClientSettings.INSTANCE.tintText.reset();
			ClientSettings.INSTANCE.panelEdgeLight.set(false);
		});
		ctx.waitTicks(10);
		shot(ctx, "27_red_no_panel_edge_light");
		ctx.runOnClient(mc -> ClientSettings.INSTANCE.panelEdgeLight.reset());
	}

	/** The setup: the colour step with your wallpaper's colours (Kairo), and the updates step. */
	private void welcome(ClientGameTestContext ctx, TestInput input) {
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.welcome.WelcomeScreen(null)));
		input.setCursorPos(W / 2.0, H * 0.97);
		ctx.waitTicks(40);
		int steps = Edition.isVoid() ? 7 : 7;
		for (int i = 1; i <= steps; i++) {
			input.pressKey(key(InputConstants.KEY_RIGHT));
			ctx.waitTicks(18);
			if (i == 2 && !Edition.isVoid()) {
				shot(ctx, "30_welcome_colour_wallpaper");
			}
		}
		shot(ctx, "31_welcome_updates");
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(10);
	}

	/** Settings → About: a real check against GitHub, then a sample newer version with its notes. */
	private void about(ClientGameTestContext ctx, TestInput input) {
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.checkUpdates.set(true);
			MenuScreen menu = MenuScreen.openOn(MenuScreen::settings, null);
			mc.gui.setScreen(menu);
			menu.settings().showAbout();
		});
		input.setCursorPos(W * 0.97, H * 0.97);
		ctx.waitFor(mc -> UpdateChecker.state() != UpdateChecker.State.IDLE && UpdateChecker.state() != UpdateChecker.State.CHECKING, 400);
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK real update check against GitHub: {} (latest offered: {})", UpdateChecker.state(),
			UpdateChecker.latest() == null ? "none" : UpdateChecker.latest().version()));
		shot(ctx, "40_about_checked");
		ctx.runOnClient(mc -> {
			String notes = "";
			try {
				notes = Files.readString(Path.of(System.getProperty("user.dir")).resolve("../../../docs/release-notes/v0.2.0.md").normalize());
			} catch (Exception e) {
				notes = "# Kairo 0.2.1\n- Sample notes";
			}
			UpdateChecker.showForTest(new UpdateChecker.Release("0.2.1", "https://github.com/Mordsome/Kairo/releases/tag/v0.2.1",
				UpdateChecker.plainText(notes), "kairo-0.2.1.jar", "https://github.com/Mordsome/Kairo/releases/download/v0.2.1/kairo-0.2.1.jar", 1, ""));
		});
		ctx.waitTicks(10);
		shot(ctx, "41_about_update_available");
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(5);
	}

	/** In a world: tinted HUD boxes, the CPS label, CPS in Keystrokes, the layout editor and the mod pages. */
	private void world(ClientGameTestContext ctx, TestInput input) {
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			var server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 6000");
			server.runCommand("weather clear");
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			KeystrokesHud keys = ModuleManager.get(KeystrokesHud.class);
			CpsHud cps = ModuleManager.get(CpsHud.class);
			ctx.runOnClient(mc -> {
				MainMenuSettings.INSTANCE.background.set("image:test_red.png");
				keys.enabled.set(true);
				cps.enabled.set(true);
				keys.followTheme.set(true);
				cps.followTheme.set(true);
			});
			clicks(ctx, input);
			shot(ctx, "50_hud_theme_label_cps");

			ctx.runOnClient(mc -> {
				cps.label.set("");
				keys.mouseText.set(KeystrokesHud.MouseText.CPS);
				cps.hideWithKeystrokes.set(false);
			});
			clicks(ctx, input);
			shot(ctx, "51_hud_cps_in_keys_no_label");

			ctx.runOnClient(mc -> {
				keys.mouseText.set(KeystrokesHud.MouseText.BOTH);
				cps.hideWithKeystrokes.set(true);
			});
			clicks(ctx, input);
			shot(ctx, "52_hud_both_cps_box_hidden");
			ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK cps covered by keystrokes: {}", dev.mordo.kairo.hud.HudManager.isCovered(cps)));

			ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.layout.LayoutScreen()));
			ctx.waitTicks(25);
			shot(ctx, "53_layout_cps_in_keystrokes");
			// PvP layout: keys show labels there, so the CPS box is back.
			ctx.runOnClient(mc -> {
				dev.mordo.kairo.core.PvpMode.set(true);
				keys.mouseText.setPvpOverride(KeystrokesHud.MouseText.LABEL);
			});
			ctx.waitTicks(25);
			shot(ctx, "54_layout_pvp_labels");
			ctx.runOnClient(mc -> {
				Kairo.LOGGER.info("CHECK pvp layout: cps covered {}", dev.mordo.kairo.hud.HudManager.isCovered(cps));
				keys.mouseText.clearPvpOverride();
				dev.mordo.kairo.core.PvpMode.set(false);
				mc.gui.setScreen(MenuScreen.forModSettings(keys, null));
			});
			ctx.waitTicks(25);
			shot(ctx, "55_keystrokes_settings");
			ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.forModSettings(cps, null)));
			ctx.waitTicks(25);
			shot(ctx, "56_cps_settings");
			ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.forModSettings(ModuleManager.get(dev.mordo.kairo.mods.Hotbar.class), null)));
			ctx.waitTicks(25);
			input.scroll(-20);
			ctx.waitTicks(15);
			shot(ctx, "57_hotbar_settings_theme_switches");
			ctx.runOnClient(mc -> {
				mc.gui.setScreen(null);
				keys.mouseText.reset();
				keys.followTheme.reset();
				cps.followTheme.reset();
				cps.label.reset();
				cps.hideWithKeystrokes.reset();
				cps.attachToKeystrokes.reset();
			});
			ctx.waitTicks(5);
		}
	}

	/** A burst of clicks so the CPS numbers aren't zero. */
	private static void clicks(ClientGameTestContext ctx, TestInput input) {
		ctx.waitTicks(5);
		for (int i = 0; i < 7; i++) {
			input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			if (i % 2 == 0) {
				input.pressMouse(InputConstants.MOUSE_BUTTON_RIGHT);
			}
			ctx.waitTicks(1);
		}
		ctx.waitTicks(2);
	}

	private static InputConstants.Key key(int code) {
		return InputConstants.Type.KEYBOARD.getOrCreate(code);
	}
}
