package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.cosmetics.Cosmetics;
import dev.mordo.kairo.cosmetics.Emotes;
import dev.mordo.kairo.gui.menu.MenuScreen;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.Set;

/**
 * Showcase screenshots for the README and the release page (build/tour-showcase): the menus, then a normal world with
 * a fixed seed (a cherry grove and a village) for the HUD, panels, cosmetics, emotes and effects, at 1920x1080.
 * Names start with "t_" (Kairo) or "v_" (Kairo Void). build/tour-showcase-scout only photographs the spawn area.
 */
final class ShowcaseTour {
	static final String SEED = "-1135674213"; // the world the showcase spots were picked in
	private final int width = 1920;
	private final int height = 1080;
	private final boolean isVoid = Edition.isVoid();
	private final String p = isVoid ? "v_" : "t_";
	private TestSingleplayerContext world;
	private ClientGameTestContext ctx;
	private TestInput input;

	static boolean flag(String name) {
		return java.nio.file.Files.exists(java.nio.file.Path.of(System.getProperty("user.dir")).resolve("../../" + name).normalize());
	}

	void run(ClientGameTestContext ctx) {
		this.ctx = ctx;
		this.input = ctx.getInput();
		input.resizeWindow(width, height);
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			mc.options.renderDistance().set(16);
			mc.options.simulationDistance().set(8);
		});
		menus();
		if (flag("tour-showcase-menus")) {
			return; // only the menus (build/tour-showcase-menus)
		}
		try (TestSingleplayerContext w = ctx.worldBuilder().setUseConsistentSettings(false).adjustSettings(s -> {
			s.setSeed(SEED);
			s.setWorldType(s.getNormalPresetList().getFirst());
		}).create()) {
			world = w;
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			settle(200);
			cmd("gamerule advance_time false");
			cmd("gamerule advance_weather false");
			cmd("gamerule spawn_mobs false");
			cmd("weather clear");
			cmd("time set 6000");
			int[] spawn = ctx.computeOnClient(mc -> new int[]{mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ()});
			if (flag("tour-showcase-scout")) {
				hud(false);
				cmd("gamemode spectator @p");
				scout(spawn);
				return;
			}
			// The cherry grove spot the showcase framing was made for (findBiome finds a different edge from each spawn).
			int[] grove = ground(968, -968);
			int[] village = findVillage(spawn);
			Kairo.LOGGER.info("CHECK showcase spawn {} grove {} village {}", java.util.Arrays.toString(spawn), java.util.Arrays.toString(grove),
				java.util.Arrays.toString(village));
			setUpPlayer();
			if (flag("tour-showcase-redo")) {
				clearing(grove);
				looks(grove);
				if (isVoid) {
					daytime(grove);
				}
				return;
			}
			inGrove(grove);
			atVillage(village != null ? village : grove);
			if (isVoid) {
				voidShots(grove, village != null ? village : grove);
			}
		}
	}

	// ---- helpers ----

	private void cmd(String command) {
		world.getServer().runCommand(command);
	}

	private void shot(String name) {
		ctx.takeScreenshot(p + name);
	}

	private void hud(boolean shown) {
		ctx.runOnClient(mc -> {
			if (mc.gui.hud.isHidden() == shown) {
				mc.gui.hud.toggle();
			}
		});
	}

	private void tp(int[] at, double dx, double dy, double dz, float yaw, float pitch) {
		cmd("tp @p " + (at[0] + dx) + " " + (at[1] + dy) + " " + (at[2] + dz) + " " + yaw + " " + pitch);
		settle(40);
	}

	/** Waits for the chunks around to be drawn (or at most a while, with the far render distance). */
	private void settle(int ticks) {
		ctx.waitTicks(ticks);
		try {
			world.getConnection().waitForChunksRender();
		} catch (AssertionError e) {
			// still drawing far away: good enough
		}
		ctx.waitTicks(10);
	}

	private void camera(CameraType type) {
		ctx.runOnClient(mc -> mc.options.setCameraType(type));
	}

	private void screen(java.util.function.Function<net.minecraft.client.Minecraft, net.minecraft.client.gui.screens.Screen> make, int ticks) {
		ctx.runOnClient(mc -> mc.gui.setScreen(make.apply(mc)));
		ctx.waitTicks(ticks);
	}

	private void closeScreen() {
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(8);
	}

	private static InputConstants.Key key(int code) {
		return InputConstants.Type.KEYBOARD.getOrCreate(code);
	}

	/** Ground height at x, z (ignoring leaves), with the chunk generated first. */
	private int[] ground(int x, int z) {
		int y = world.getServer().computeOnServer(s -> {
			var level = s.overworld();
			level.getChunk(x >> 4, z >> 4);
			return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
		});
		return new int[]{x, y, z};
	}

	private int[] findBiome(int[] spawn) {
		int[] found = world.getServer().computeOnServer(s -> {
			var level = s.overworld();
			for (var key : java.util.List.of(net.minecraft.world.level.biome.Biomes.CHERRY_GROVE, net.minecraft.world.level.biome.Biomes.MEADOW,
				net.minecraft.world.level.biome.Biomes.FLOWER_FOREST)) {
				var result = level.findClosestBiome3d(h -> h.is(key), new BlockPos(spawn[0], 80, spawn[2]), 4000, 32, 64);
				if (result != null) {
					return new int[]{result.getFirst().getX(), result.getFirst().getZ()};
				}
			}
			return new int[]{spawn[0], spawn[2]};
		});
		return ground(found[0], found[1]);
	}

	private int[] findVillage(int[] spawn) {
		BlockPos at = world.getServer().computeOnServer(s -> s.overworld().findNearestMapStructure(net.minecraft.tags.StructureTags.VILLAGE,
			new BlockPos(spawn[0], 80, spawn[2]), 80, false));
		return at == null ? null : ground(at.getX(), at.getZ());
	}

	/** A clearing in the grove so the player stands in the open, on grass. */
	private void clearing(int[] g) {
		cmd("fill " + (g[0] - 4) + " " + g[1] + " " + (g[2] - 4) + " " + (g[0] + 4) + " " + (g[1] + 7) + " " + (g[2] + 4) + " air");
		cmd("fill " + (g[0] - 4) + " " + (g[1] - 1) + " " + (g[2] - 4) + " " + (g[0] + 4) + " " + (g[1] - 1) + " " + (g[2] + 4) + " grass_block");
	}

	private void scout(int[] s) {
		for (int yaw = 0; yaw < 360; yaw += 45) {
			tp(s, 0, 2, 0, yaw, 5);
			shot("scout_low_" + yaw);
		}
	}

	// ---- menus (no world) ----

	private void menus() {
		input.setCursorPos(width * 0.5, height * 0.98);
		if (isVoid) {
			String[] names = {"main_menu_stage", "main_menu_simple"};
			for (int i = 0; i < 2; i++) {
				int style = i;
				ctx.runOnClient(mc -> {
					MainMenuSettings.setVoidStyle(style);
					mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
				});
				ctx.waitTicks(i == 0 ? 120 : 50);
				shot("0" + i + "_" + names[i]);
			}
			ctx.runOnClient(mc -> {
				MainMenuSettings.setVoidStyle(2);
				dev.mordo.kairo.gui.main.MainScreens.testOverride = true;
				mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
				dev.mordo.kairo.gui.main.MainScreens.testOverride = false;
			});
			ctx.waitTicks(50);
			shot("02_main_menu_minecraft_style");
			ctx.runOnClient(mc -> MainMenuSettings.setVoidStyle(0));
		} else {
			MainMenuSettings.Layout[] layouts = {MainMenuSettings.Layout.CLASSIC, MainMenuSettings.Layout.DOCK, MainMenuSettings.Layout.ORBIT,
				MainMenuSettings.Layout.DASHBOARD};
			for (int i = 0; i < layouts.length; i++) {
				MainMenuSettings.Layout layout = layouts[i];
				ctx.runOnClient(mc -> {
					ClientSettings.INSTANCE.customMainMenu.set(true);
					MainMenuSettings.INSTANCE.layout.set(layout);
					mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen());
				});
				ctx.waitTicks(i == 0 ? 120 : 60);
				shot("0" + i + "_main_menu_" + layout.name().toLowerCase(java.util.Locale.ROOT));
			}
			ctx.runOnClient(mc -> {
				dev.mordo.kairo.gui.main.MainScreens.testOverride = true;
				mc.gui.setScreen(new net.minecraft.client.gui.screens.TitleScreen());
				dev.mordo.kairo.gui.main.MainScreens.testOverride = false;
			});
			ctx.waitTicks(50);
			shot("04_main_menu_minecraft_style");
			ctx.runOnClient(mc -> MainMenuSettings.INSTANCE.layout.set(MainMenuSettings.Layout.CLASSIC));
		}
		// The main menu settings, the worlds screen and the first-run setup.
		screen(mc -> new dev.mordo.kairo.gui.main.MainMenuSettingsScreen(new dev.mordo.kairo.gui.main.MainMenuScreen()), 40);
		shot("05_main_menu_settings");
		screen(mc -> new dev.mordo.kairo.gui.main.WorldsScreen(new dev.mordo.kairo.gui.main.MainMenuScreen()), 40);
		shot("06_singleplayer");
		screen(mc -> new dev.mordo.kairo.gui.welcome.WelcomeScreen(null), 90);
		shot("07_welcome_1");
		int steps = isVoid ? 4 : 3;
		for (int i = 1; i < steps; i++) {
			input.pressKey(key(InputConstants.KEY_RIGHT));
			ctx.waitTicks(30);
			shot("07_welcome_" + (i + 1));
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen()));
		ctx.waitTicks(10);
	}

	// ---- in the world ----

	private void setUpPlayer() {
		cmd("gamemode survival @p");
		cmd("effect give @p resistance infinite 255 true");
		cmd("effect give @p saturation infinite 255 true");
		String[][] items = {{"hotbar.0", "diamond_sword"}, {"hotbar.1", "totem_of_undying"}, {"hotbar.2", "golden_apple 16"},
			{"hotbar.3", "ender_pearl 16"}, {"hotbar.4", "end_crystal 32"}, {"hotbar.5", "obsidian 64"}, {"hotbar.6", "bow"},
			{"hotbar.7", "arrow 64"}, {"hotbar.8", "cooked_beef 32"}, {"weapon.offhand", "totem_of_undying"},
			{"armor.head", "netherite_helmet"}, {"armor.chest", "netherite_chestplate"}, {"armor.legs", "netherite_leggings"},
			{"armor.feet", "netherite_boots"}};
		for (String[] item : items) {
			cmd("item replace entity @p " + item[0] + " with " + item[1]);
		}
		ctx.runOnClient(mc -> {
			Set<String> on = Set.of("keystrokes", "cps", "armor_status");
			for (var element : dev.mordo.kairo.hud.HudManager.elements()) {
				element.enabled.set(on.contains(element.id()));
			}
		});
	}

	private void inGrove(int[] g) {
		clearing(g);
		// Plain gameplay with the HUD.
		tp(g, 0, 0, 0, 30, 2);
		hud(true);
		ctx.waitTicks(20);
		shot("10_gameplay_hud");
		// The ring, the panels and the wardrobe.
		input.setCursorPos(width / 2.0, height / 2.0 - 240);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(30);
		shot("11_ring_menu");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
		input.setCursorPos(width * 0.98, height * 0.98);
		screen(mc -> MenuScreen.openOn(MenuScreen::mods, null), 40);
		shot("12_mods");
		screen(mc -> MenuScreen.openOn(MenuScreen::visualsPage, null), 40);
		int[] pages = isVoid ? new int[]{0, 1, 2, 4, 7} : new int[]{0, 1, 2, 3, 10};
		for (int page : pages) {
			ctx.runOnClient(mc -> {
				var menu = (MenuScreen) mc.gui.screen();
				if (isVoid) {
					menu.voidVisuals().select(page);
				} else {
					menu.visuals().select(page);
				}
			});
			ctx.waitTicks(25);
			shot("13_visuals_" + page);
		}
		screen(mc -> MenuScreen.openOn(MenuScreen::settings, null), 40);
		shot("14_settings");
		ctx.runOnClient(mc -> {
			Cosmetics c = Cosmetics.get();
			c.cape.set(isVoid ? "design:galaxy" : "design:aurora");
			c.wings.set(isVoid ? Cosmetics.WingStyle.ENERGY : Cosmetics.WingStyle.ANGEL);
			c.hat.set(isVoid ? Cosmetics.HatStyle.HALO : Cosmetics.HatStyle.CROWN);
		});
		screen(mc -> MenuScreen.openOn(MenuScreen::cosmetics, null), 40);
		shot("15_wardrobe_capes");
		ctx.runOnClient(mc -> ((MenuScreen) mc.gui.screen()).cosmetics().select(1));
		ctx.waitTicks(20);
		shot("15_wardrobe_wings");
		ctx.runOnClient(mc -> ((MenuScreen) mc.gui.screen()).cosmetics().select(5));
		ctx.waitTicks(20);
		shot("15_wardrobe_emotes");
		closeScreen();
		ctx.runOnClient(mc -> dev.mordo.kairo.cosmetics.EmoteWheelScreen.open());
		input.setCursorPos(width / 2.0 + 230, height / 2.0 - 90);
		ctx.waitTicks(25);
		shot("16_emote_wheel");
		closeScreen();
		screen(mc -> new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player), 30);
		shot("17_inventory");
		closeScreen();
		screen(mc -> new dev.mordo.kairo.gui.layout.LayoutScreen(), 30);
		shot("18_hud_layout_editor");
		closeScreen();

		looks(g);
	}

	/** Cosmetics and emotes from the front and back, no HUD, and a sunset. */
	private void looks(int[] g) {
		ctx.runOnClient(mc -> {
			Cosmetics c = Cosmetics.get();
			c.cape.set(isVoid ? "design:galaxy" : "design:aurora");
			c.wings.set(isVoid ? Cosmetics.WingStyle.ENERGY : Cosmetics.WingStyle.ANGEL);
			c.hat.set(isVoid ? Cosmetics.HatStyle.HALO : Cosmetics.HatStyle.CROWN);
		});
		hud(false);
		tp(g, 0, 0, 0, 210, 0);
		camera(CameraType.THIRD_PERSON_FRONT);
		ctx.waitTicks(30);
		shot("20_cosmetics_front");
		tp(g, 0, 0, 0, 30, 0);
		camera(CameraType.THIRD_PERSON_BACK);
		ctx.waitTicks(20);
		shot("21_cosmetics_back");
		tp(g, 0, 0, 0, 210, 0);
		camera(CameraType.THIRD_PERSON_FRONT);
		Emotes.Emote[] emotes = {Emotes.Emote.FLOSS, Emotes.Emote.LEVITATE, Emotes.Emote.DAB, Emotes.Emote.MEDITATE, Emotes.Emote.HERO,
			Emotes.Emote.CHEER};
		for (Emotes.Emote emote : emotes) {
			ctx.runOnClient(mc -> Emotes.playHere(emote));
			ctx.waitTicks(emote.loops() ? 26 : 14);
			shot("22_emote_" + emote.name().toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> Emotes.stop());
		ctx.waitTicks(5);
		// A sunset in the grove, with bloom.
		cmd("time set 12500");
		ctx.runOnClient(mc -> GameVisuals.INSTANCE.glow.set(0.5));
		tp(g, 0, 0, 0, 90, -6);
		camera(CameraType.THIRD_PERSON_BACK);
		ctx.waitTicks(20);
		shot("23_sunset_cosmetics");
		ctx.runOnClient(mc -> GameVisuals.INSTANCE.glow.reset());
		cmd("time set 6000");
		camera(CameraType.FIRST_PERSON);
		ctx.waitTicks(5);
	}

	private void atVillage(int[] v) {
		// The village from above, plain and with colour filters.
		cmd("gamemode spectator @p");
		tp(v, -18, 14, -18, -45, 22);
		ctx.waitTicks(30);
		shot("30_village");
		GameVisuals.Filter[] filters = {GameVisuals.Filter.CINEMATIC, GameVisuals.Filter.VAPORWAVE, GameVisuals.Filter.NOIR};
		for (GameVisuals.Filter f : filters) {
			ctx.runOnClient(mc -> GameVisuals.INSTANCE.filter.set(f));
			ctx.waitTicks(6);
			shot("31_filter_" + f.name().toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> GameVisuals.INSTANCE.filter.reset());

		// Combat at the edge of the village: a hit, a kill effect, the attack animation.
		int[] arena = ground(v[0] - 26, v[2] - 26);
		cmd("fill " + (arena[0] - 3) + " " + arena[1] + " " + (arena[2] - 3) + " " + (arena[0] + 3) + " " + (arena[1] + 4) + " " + (arena[2] + 8) + " air");
		cmd("gamemode survival @p");
		tp(arena, 0.5, 0, 0.5, 0, 8);
		hud(true);
		ctx.runOnClient(mc -> {
			GameVisuals.INSTANCE.hitBurst.set(GameVisuals.HitBurst.SPARKS);
			GameVisuals.INSTANCE.hitAmount.set(0.5);
			GameVisuals.INSTANCE.swingStyle.set(GameVisuals.SwingStyle.SLASH);
			GameVisuals.INSTANCE.swingTrail.set(true);
			GameVisuals.INSTANCE.killEffect.set(isVoid ? GameVisuals.KillEffect.IMPLODE : GameVisuals.KillEffect.SHATTER);
		});
		cmd("summon husk " + (arena[0] + 0.5) + " " + arena[1] + " " + (arena[2] + 3.2) + " {NoAI:1b,Rotation:[180f,0f]}");
		ctx.waitTicks(20);
		input.lookAt(new BlockPos(arena[0], arena[1] + 1, arena[2] + 3));
		ctx.waitTicks(5);
		long[] t0 = new long[1];
		ctx.runOnClient(mc -> {
			t0[0] = System.nanoTime();
			dev.mordo.kairo.render.HandAnimations.testClock = t0[0];
			dev.mordo.kairo.render.VoidFx.testClock = t0[0];
		});
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.render.HandAnimations.testClock = t0[0] + 70_000_000L;
			dev.mordo.kairo.render.VoidFx.testClock = t0[0] + 90_000_000L;
		});
		ctx.waitTicks(2);
		shot("32_hit");
		ctx.runOnClient(mc -> {
			dev.mordo.kairo.render.HandAnimations.testClock = 0;
			dev.mordo.kairo.render.VoidFx.testClock = 0;
		});
		ctx.waitTicks(15);
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		cmd("kill @e[type=husk]");
		ctx.waitTicks(isVoid ? 11 : 4);
		shot("33_kill_effect");
		ctx.waitTicks(30);

		// Projectile trails and item physics.
		ctx.runOnClient(mc -> {
			GameVisuals.INSTANCE.projectileTrail.set(GameVisuals.ProjectileTrail.RAINBOW);
			GameVisuals.INSTANCE.itemPhysics.set(true);
			GameVisuals.INSTANCE.rareItemGlow.set(true);
		});
		tp(arena, 0.5, 0, 0.5, 0, 12);
		String[] drops = {"diamond_sword", "nether_star", "enchanted_golden_apple", "totem_of_undying", "diamond", "golden_apple"};
		for (int i = 0; i < drops.length; i++) {
			cmd("summon item " + (arena[0] - 1.2 + i * 0.6) + " " + arena[1] + " " + (arena[2] + 2.5 + (i % 2) * 0.5) + " {Item:{id:\"minecraft:" + drops[i]
				+ "\",count:1},PickupDelay:32767,Age:-32768}");
		}
		ctx.waitTicks(30);
		cmd("summon arrow " + (arena[0] + 6) + " " + (arena[1] + 1.6) + " " + (arena[2] + 5) + " {Motion:[-0.8d,0.02d,0.0d],NoGravity:1b}");
		cmd("summon arrow " + (arena[0] + 6) + " " + (arena[1] + 2.4) + " " + (arena[2] + 6) + " {Motion:[-0.7d,0.0d,0.0d],NoGravity:1b}");
		ctx.waitTicks(10);
		shot("34_trails_and_item_physics");
		cmd("kill @e[type=arrow]");
		cmd("kill @e[type=item]");
		ctx.runOnClient(mc -> {
			GameVisuals.INSTANCE.projectileTrail.reset();
			GameVisuals.INSTANCE.itemPhysics.reset();
			GameVisuals.INSTANCE.rareItemGlow.reset();
			GameVisuals.INSTANCE.hitBurst.reset();
			GameVisuals.INSTANCE.hitAmount.reset();
			GameVisuals.INSTANCE.swingStyle.reset();
			GameVisuals.INSTANCE.swingTrail.reset();
			GameVisuals.INSTANCE.killEffect.reset();
		});
		hud(false);
	}

	/** Kairo Void: atmospheres, the night sky, crystals and the HUD styles. */
	private void voidShots(int[] g, int[] v) {
		var vs = dev.mordo.kairo.core.VoidSettings.INSTANCE;
		cmd("gamemode spectator @p");
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidSky.testTime = 10.3f);
		String[][] atmos = {{"BLIZZARD", "6000"}, {"GALAXY", "6000"}, {"BLOOD_MOON", "18000"}, {"INFERNO", "6000"}, {"SAKURA", "6000"},
			{"TOXIC", "6000"}, {"ABYSS", "6000"}, {"SYNTHWAVE", "13000"}, {"STORM", "6000"}};
		for (String[] a : atmos) {
			cmd("time set " + a[1]);
			boolean sky = a[0].equals("BLOOD_MOON") || a[0].equals("SYNTHWAVE") || a[0].equals("GALAXY");
			tp(v, -18, 10, -18, a[0].equals("SYNTHWAVE") ? 76 : a[0].equals("BLOOD_MOON") ? 200 : -45, sky ? -8 : 18);
			ctx.runOnClient(mc -> vs.atmosphere.set(dev.mordo.kairo.core.VoidSettings.Atmosphere.valueOf(a[0])));
			ctx.waitTicks(60);
			shot("40_atmosphere_" + a[0].toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> vs.atmosphere.reset());
		String[][] sky = {{"18000", "180", "-22", "41_aurora"}, {"18000", "221", "-17", "42_night_sky_planet"}, {"18000", "30", "-55", "43_galaxy_overhead"},
			{"12600", "90", "-4", "44_sunset"}};
		for (String[] s : sky) {
			cmd("time set " + s[0]);
			tp(g, 0, 12, 0, Float.parseFloat(s[1]), Float.parseFloat(s[2]));
			ctx.waitTicks(20);
			shot(s[3]);
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.render.VoidSky.testTime = -1);

		// Crystals at dusk.
		cmd("time set 13500");
		cmd("summon end_crystal " + (g[0] + 0.5) + " " + g[1] + " " + (g[2] + 3.5) + " {ShowBottom:1b}");
		cmd("summon end_crystal " + (g[0] + 3.5) + " " + g[1] + " " + (g[2] + 5.5) + " {ShowBottom:0b}");
		tp(g, 0.5, 1.2, -2.5, 0, 8);
		for (var style : new dev.mordo.kairo.core.VoidSettings.CrystalStyle[]{dev.mordo.kairo.core.VoidSettings.CrystalStyle.PRISM,
			dev.mordo.kairo.core.VoidSettings.CrystalStyle.VOID, dev.mordo.kairo.core.VoidSettings.CrystalStyle.PLASMA,
			dev.mordo.kairo.core.VoidSettings.CrystalStyle.HOLO}) {
			ctx.runOnClient(mc -> vs.crystalStyle.set(style));
			ctx.waitTicks(10);
			shot("45_crystal_" + style.name().toLowerCase(java.util.Locale.ROOT));
		}
		ctx.runOnClient(mc -> vs.crystalStyle.reset());
		cmd("kill @e[type=end_crystal]");

		daytime(g);
	}

	/** The energy blade on the sword, the HUD styles and the totem blast, by day. */
	private void daytime(int[] g) {
		var vs = dev.mordo.kairo.core.VoidSettings.INSTANCE;
		cmd("time set 6000");
		cmd("gamemode survival @p");
		tp(g, 0, 0, 0, 30, 2);
		hud(true);
		long[] t0 = new long[1];
		ctx.runOnClient(mc -> {
			t0[0] = System.nanoTime();
			dev.mordo.kairo.render.HandAnimations.testClock = t0[0];
		});
		input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
		ctx.waitTicks(1);
		for (int ms : new int[]{30, 55, 85}) {
			ctx.runOnClient(mc -> dev.mordo.kairo.render.HandAnimations.testClock = t0[0] + ms * 1_000_000L);
			ctx.waitTicks(2);
			shot("46_energy_blade_" + ms);
		}
		ctx.runOnClient(mc -> dev.mordo.kairo.render.HandAnimations.testClock = 0);
		ctx.waitTicks(20);
		for (var style : dev.mordo.kairo.core.VoidSettings.HudStyle.values()) {
			ctx.runOnClient(mc -> vs.hudStyle.set(style));
			input.holdKey(key(InputConstants.KEY_W));
			ctx.waitTicks(2);
			shot("47_hud_" + style.name().toLowerCase(java.util.Locale.ROOT));
			input.releaseKey(key(InputConstants.KEY_W));
			ctx.waitTicks(10);
			tp(g, 0, 0, 0, 30, 2);
		}
		ctx.runOnClient(mc -> vs.hudStyle.reset());
		// The totem blast.
		ctx.runOnClient(mc -> mc.player.displayItemActivation(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.TOTEM_OF_UNDYING)));
		ctx.waitTicks(4);
		shot("48_totem_blast");
		ctx.waitTicks(30);
	}
}
