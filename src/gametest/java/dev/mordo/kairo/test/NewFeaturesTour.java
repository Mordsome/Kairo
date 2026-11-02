package dev.mordo.kairo.test;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.cosmetics.Emotes;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;

import java.util.Locale;

/**
 * Screenshots and checks of the newest features, in either edition (prefix "n_" or "void_n_"): the emote wheel pages
 * and the new emotes, the new kill effects, projectile trails and the pearl ring, item physics, bloom, the living ring,
 * look codes and fewer flashes. Lines starting "CHECK" in the log are the results to read.
 */
final class NewFeaturesTour {
	private final String prefix;
	private final int width;
	private final int height;

	NewFeaturesTour(String prefix, int width, int height) {
		this.prefix = prefix;
		this.width = width;
		this.height = height;
	}

	void run(ClientGameTestContext ctx, TestSingleplayerContext world) {
		TestInput input = ctx.getInput();
		var server = world.getServer();
		server.runCommand("gamerule advance_time false");
		server.runCommand("weather clear");
		server.runCommand("time set 6000");
		server.runCommand("gamemode survival @p");
		server.runCommand("tp @p 0 -60 0 0 0");
		ctx.waitTicks(20);

		lookCodes(ctx);
		ring(ctx, input);
		visualsMenu(ctx, input);
		emotes(ctx, input);
		kills(ctx, input, world);
		trails(ctx, world);
		items(ctx, world);
		bloom(ctx, world);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot(prefix + name);
	}

	/** A look code made, the look changed, and the code put back: the colour returns. */
	private void lookCodes(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			ClientSettings s = ClientSettings.INSTANCE;
			int before = s.accent.getNormal();
			GameVisuals.INSTANCE.projectileTrail.set(GameVisuals.ProjectileTrail.RAINBOW);
			String code = dev.mordo.kairo.core.LookCodes.export();
			s.accent.set(0xFF123456);
			GameVisuals.INSTANCE.projectileTrail.reset();
			int changed = dev.mordo.kairo.core.LookCodes.apply(code);
			int bad = dev.mordo.kairo.core.LookCodes.apply("hello there");
			Kairo.LOGGER.info("CHECK look code: {} chars, changed {}, accent back {}, trail back {}, junk -> {}", code.length(), changed,
				s.accent.getNormal() == before, GameVisuals.INSTANCE.projectileTrail.get() == GameVisuals.ProjectileTrail.RAINBOW, bad);
			GameVisuals.INSTANCE.projectileTrail.reset();
		});
	}

	/** The living ring (both editions now). */
	private void ring(ClientGameTestContext ctx, TestInput input) {
		input.setCursorPos(width / 2.0, height / 2.0 - 200);
		input.pressKey(key(InputConstants.KEY_RSHIFT));
		ctx.waitTicks(25);
		shot(ctx, "n_00_living_ring");
		input.pressKey(key(InputConstants.KEY_ESCAPE));
		ctx.waitTicks(10);
	}

	/** Where the new options live in the Visuals menu, and the wardrobe's emote pages. */
	private void visualsMenu(ClientGameTestContext ctx, TestInput input) {
		boolean isVoid = dev.mordo.kairo.core.Edition.isVoid();
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::visualsPage, null)));
		input.setCursorPos(width * 0.97, height * 0.97);
		ctx.waitTicks(30);
		int[] pages = isVoid ? new int[]{1, 2, 5, 6} : new int[]{1, 2, 3, 4, 10};
		for (int page : pages) {
			ctx.runOnClient(mc -> {
				var menu = (dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen();
				if (isVoid) {
					menu.voidVisuals().select(page);
				} else {
					menu.visuals().select(page);
				}
			});
			ctx.waitTicks(16);
			shot(ctx, "n_11_visuals_" + page);
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::cosmetics, null)));
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> ((dev.mordo.kairo.gui.menu.MenuScreen) mc.gui.screen()).cosmetics().select(5));
		ctx.waitTicks(16);
		shot(ctx, "n_12_wardrobe_emotes");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	private void emotes(ClientGameTestContext ctx, TestInput input) {
		// The wheel, a page at a time.
		ctx.runOnClient(mc -> dev.mordo.kairo.cosmetics.EmoteWheelScreen.open());
		input.setCursorPos(width / 2.0 + 170, height / 2.0 - 60);
		ctx.waitTicks(16);
		for (int page = 0; page < 5; page++) {
			int p = page;
			ctx.runOnClient(mc -> ((dev.mordo.kairo.cosmetics.EmoteWheelScreen) mc.gui.screen()).showPage(p));
			ctx.waitTicks(14);
			shot(ctx, "n_01_wheel_page_" + page);
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
		// Every new emote, from the front.
		ctx.runOnClient(mc -> mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_FRONT));
		ctx.waitTicks(5);
		Emotes.Emote[] all = Emotes.Emote.values();
		for (int i = Emotes.Emote.ZOMBIE.ordinal() + 1; i < all.length; i++) {
			Emotes.Emote emote = all[i];
			ctx.runOnClient(mc -> Emotes.playHere(emote));
			ctx.waitTicks(emote.loops() ? 22 : 12);
			shot(ctx, "n_02_emote_" + emote.name().toLowerCase(Locale.ROOT));
		}
		ctx.runOnClient(mc -> {
			Emotes.stop();
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
		});
		ctx.waitTicks(5);
	}

	/** The three new kill effects on a one-health husk. */
	private void kills(ClientGameTestContext ctx, TestInput input, TestSingleplayerContext world) {
		var server = world.getServer();
		String[] effects = {"SHATTER", "IMPLODE", "ASCEND"};
		for (String name : effects) {
			ctx.runOnClient(mc -> GameVisuals.INSTANCE.killEffect.set(GameVisuals.KillEffect.valueOf(name)));
			server.runCommand("tp @p 0 -60 0 0 0");
			server.runCommand("summon husk 0 -60 2.5 {NoAI:1b,Health:1f,Rotation:[180f,0f]}");
			ctx.waitTicks(15);
			input.lookAt(new net.minecraft.core.BlockPos(0, -59, 2));
			ctx.waitTicks(5);
			input.pressMouse(InputConstants.MOUSE_BUTTON_LEFT);
			// The hit only hurts it; finish it off right away (within the 5 s after a hit, that counts as your kill).
			ctx.waitTicks(1);
			server.runCommand("kill @e[type=husk]");
			ctx.waitTicks(5);
			shot(ctx, "n_03_kill_" + name.toLowerCase(Locale.ROOT) + "_a");
			ctx.waitTicks(10);
			shot(ctx, "n_03_kill_" + name.toLowerCase(Locale.ROOT) + "_b");
			server.runCommand("kill @e[type=husk]");
			ctx.waitTicks(20);
		}
		ctx.runOnClient(mc -> GameVisuals.INSTANCE.killEffect.reset());
	}

	/** Arrows flying past with trails, and an ender pearl landing. */
	private void trails(ClientGameTestContext ctx, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("tp @p 0 -60 0 0 0");
		for (String style : new String[]{"ACCENT", "RAINBOW", "FLAME"}) {
			ctx.runOnClient(mc -> GameVisuals.INSTANCE.projectileTrail.set(GameVisuals.ProjectileTrail.valueOf(style)));
			server.runCommand("summon arrow 5 -58.5 6 {Motion:[-0.7d,0.05d,0.0d],NoGravity:1b}");
			server.runCommand("summon arrow 5 -57.5 7 {Motion:[-0.6d,0.0d,0.0d],NoGravity:1b}");
			ctx.waitTicks(9);
			shot(ctx, "n_04_trail_" + style.toLowerCase(Locale.ROOT));
			server.runCommand("kill @e[type=arrow]");
			ctx.waitTicks(10);
		}
		server.runCommand("summon ender_pearl 0 -56 4 {Motion:[0.0d,-0.6d,0.0d]}");
		ctx.waitTicks(9);
		shot(ctx, "n_05_pearl_ring");
		ctx.runOnClient(mc -> GameVisuals.INSTANCE.projectileTrail.reset());
		ctx.waitTicks(10);
	}

	/** Dropped items, with and without item physics (and the rare glow). */
	private void items(ClientGameTestContext ctx, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("tp @p 0 -59 0 0 40");
		String[] items = {"diamond_sword", "stone", "nether_star", "enchanted_golden_apple", "oak_planks", "bow", "diamond"};
		for (int i = 0; i < items.length; i++) {
			server.runCommand("summon item " + (i - 3) * 0.7 + " -60 2.5 {Item:{id:\"minecraft:" + items[i] + "\",count:" + (i == 4 ? 5 : 1)
				+ "},PickupDelay:32767,Age:-32768}");
		}
		ctx.waitTicks(30);
		shot(ctx, "n_06_items_vanilla");
		ctx.runOnClient(mc -> {
			GameVisuals.INSTANCE.itemPhysics.set(true);
			GameVisuals.INSTANCE.rareItemGlow.set(true);
		});
		ctx.waitTicks(10);
		shot(ctx, "n_07_items_physics");
		server.runCommand("summon item 0 -57 3 {Item:{id:\"minecraft:diamond_pickaxe\",count:1},PickupDelay:32767}");
		ctx.waitTicks(4);
		shot(ctx, "n_08_item_falling");
		ctx.runOnClient(mc -> {
			GameVisuals.INSTANCE.itemPhysics.reset();
			GameVisuals.INSTANCE.rareItemGlow.reset();
		});
		server.runCommand("kill @e[type=item]");
		ctx.waitTicks(5);
	}

	/** Bloom on lava and glowstone, off and on; and fewer flashes on an explosion. */
	private void bloom(ClientGameTestContext ctx, TestSingleplayerContext world) {
		var server = world.getServer();
		server.runCommand("time set 18000");
		server.runCommand("tp @p 0 -59 0 0 20");
		server.runCommand("fill -2 -61 4 2 -61 6 lava");
		server.runCommand("setblock -3 -60 5 glowstone");
		server.runCommand("setblock 3 -60 5 sea_lantern");
		ctx.waitTicks(30);
		shot(ctx, "n_09_bloom_off");
		ctx.runOnClient(mc -> GameVisuals.INSTANCE.glow.set(0.7));
		ctx.waitTicks(5);
		shot(ctx, "n_10_bloom_on");
		ctx.runOnClient(mc -> {
			GameVisuals.INSTANCE.glow.reset();
			GameVisuals.INSTANCE.explosionFlash.set(1.0);
			ClientSettings.INSTANCE.reduceFlashing.set(true);
			dev.mordo.kairo.render.WorldFx.explosion(mc.player.position().add(0, 0, 3), 2);
			Kairo.LOGGER.info("CHECK fewer flashes: explosion flash {}", dev.mordo.kairo.render.WorldFx.flash());
			ClientSettings.INSTANCE.reduceFlashing.set(false);
			dev.mordo.kairo.render.WorldFx.explosion(mc.player.position().add(0, 0, 3), 2);
			Kairo.LOGGER.info("CHECK full flashes: explosion flash {}", dev.mordo.kairo.render.WorldFx.flash());
			GameVisuals.INSTANCE.explosionFlash.reset();
		});
		server.runCommand("fill -3 -61 4 3 -60 6 air");
		server.runCommand("fill -3 -61 4 3 -61 6 grass_block");
		server.runCommand("time set 6000");
		ctx.waitTicks(10);
	}

	private static InputConstants.Key key(int code) {
		return InputConstants.Type.KEYBOARD.getOrCreate(code);
	}
}
