package dev.mordo.kairo.test;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.cosmetics.Cosmetics;
import dev.mordo.kairo.cosmetics.Emotes;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.render.VoidIntro;
import dev.mordo.kairo.render.VoidNoir;
import dev.mordo.kairo.render.VoidSky;
import dev.mordo.kairo.render.VoidWorldFx;
import net.fabricmc.fabric.api.client.gametest.v1.TestInput;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.CameraType;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.world.phys.Vec3;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

/**
 * Kairo Void 0.3.0: the intro, the custom sky and living aurora, auras, glyphs, trails, your look, hit impacts,
 * damage markers, the cinematic kill, the block overlay, nametags and music emotes. Run with build/tour-030 and
 * -Pvoid. Lines starting "CHECK" in the log are the results to read.
 */
final class Update030Tour {
	private static final String P = "x_";

	void run(ClientGameTestContext ctx) {
		TestInput input = ctx.getInput();
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			Kairo.LOGGER.info("CHECK edition void: {}", Edition.isVoid());
		});
		writeFiles();
		intro(ctx);
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			ctx.runOnClient(mc -> mc.options.pauseOnLostFocus = false);
			var server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("gamemode survival @p");
			server.runCommand("weather clear");
			server.runCommand("time set 1000");
			server.runCommand("tp @p 0 -60 0 0 0");
			world.getConnection().waitForChunksRender();
			ctx.waitTicks(20);
			// The world effects get their own shots; off for the rest so those stay comparable.
			ctx.runOnClient(mc -> {
				VoidSettings.INSTANCE.worldRibbons.set(false);
				VoidSettings.INSTANCE.motes.set(false);
				VoidSettings.INSTANCE.glyphs.set(false);
				VoidSettings.INSTANCE.blockOverlay.set(VoidSettings.BlockOverlay.NONE);
			});
			sky(ctx, server);
			you(ctx, server);
			pov(ctx, server);
			combat(ctx, server);
			lightParticles(ctx, server);
			overlay(ctx, server);
			music(ctx, server);
			menus(ctx);
		}
		ctx.runOnClient(mc -> mc.gui.setScreen(new TitleScreen()));
		ctx.waitTicks(10);
	}

	private void shot(ClientGameTestContext ctx, String name) {
		ctx.takeScreenshot(P + name);
	}

	// ---- files the tour uses ----

	private static void writeFiles() {
		try {
			// A 2:1 panorama: a violet-to-orange sky, a big moon and stars.
			Path skies = dev.mordo.kairo.render.SkyImage.folder();
			Files.createDirectories(skies);
			BufferedImage img = new BufferedImage(2048, 1024, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = img.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
			g.setPaint(new GradientPaint(0, 0, new Color(0x1B0B3A), 0, 512, new Color(0xFF7A45)));
			g.fillRect(0, 0, 2048, 512);
			g.setPaint(new GradientPaint(0, 512, new Color(0xFF7A45), 0, 1024, new Color(0x200A20)));
			g.fillRect(0, 512, 2048, 512);
			Random r = new Random(3);
			g.setColor(Color.WHITE);
			for (int i = 0; i < 400; i++) {
				int s = r.nextInt(3) + 1;
				g.fillOval(r.nextInt(2048), r.nextInt(380), s, s);
			}
			g.setColor(new Color(0xFFF1D0));
			g.fillOval(900, 220, 160, 160);
			g.dispose();
			ImageIO.write(img, "png", skies.resolve("tour_sunset.png").toFile());

			// A 12-second beat track at 120 BPM (a kick every half second), as a WAV.
			Path sounds = dev.mordo.kairo.sound.SoundLibrary.folder();
			Files.createDirectories(sounds);
			int rate = 22050;
			int n = rate * 12;
			short[] pcm = new short[n];
			for (int i = 0; i < n; i++) {
				double t = i / (double) rate;
				double sinceBeat = t % 0.5;
				double kick = Math.sin(2 * Math.PI * (55 + 120 * Math.exp(-sinceBeat * 30)) * sinceBeat) * Math.exp(-sinceBeat * 9);
				double hat = (r.nextDouble() * 2 - 1) * Math.exp(-((t + 0.25) % 0.5) * 60) * 0.25;
				pcm[i] = (short) (Math.clamp(kick * 0.7 + hat, -1, 1) * 30000);
			}
			ByteArrayOutputStream wav = new ByteArrayOutputStream();
			ByteBuffer header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
			header.put("RIFF".getBytes()).putInt(36 + n * 2).put("WAVE".getBytes()).put("fmt ".getBytes()).putInt(16).putShort((short) 1)
				.putShort((short) 1).putInt(rate).putInt(rate * 2).putShort((short) 2).putShort((short) 16).put("data".getBytes()).putInt(n * 2);
			wav.write(header.array());
			ByteBuffer data = ByteBuffer.allocate(n * 2).order(ByteOrder.LITTLE_ENDIAN);
			for (short s : pcm) {
				data.putShort(s);
			}
			wav.write(data.array());
			Files.write(sounds.resolve("tour_beat.wav"), wav.toByteArray());
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	// ---- intro ----

	private void intro(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> {
			mc.gui.setScreen(new TitleScreen());
			VoidIntro.replay();
		});
		ctx.waitTicks(14);
		shot(ctx, "00_intro_kairo");
		ctx.waitTicks(10);
		shot(ctx, "01_intro_void_pop");
		ctx.waitTicks(12);
		shot(ctx, "02_intro_hold");
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK intro finished: {}", !VoidIntro.playing()));
		shot(ctx, "03_intro_done");
	}

	// ---- sky ----

	private void sky(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		VoidSettings s = VoidSettings.INSTANCE;
		ctx.runOnClient(mc -> VoidSky.testTime = 40);
		server.runCommand("time set 18000");
		server.runCommand("tp @p 0 -60 0 0 -35");
		ctx.runOnClient(mc -> {
			s.aurora.set(true);
			s.auroraStyle.set(VoidSettings.AuroraStyle.RIBBONS);
		});
		ctx.waitTicks(20);
		shot(ctx, "10_aurora_ribbons");
		ctx.runOnClient(mc -> s.auroraColor.set(VoidSettings.AuroraColor.RAINBOW));
		ctx.waitTicks(5);
		shot(ctx, "11_aurora_rainbow");
		ctx.runOnClient(mc -> s.auroraColor.reset());
		server.runCommand("time set 1000");
		server.runCommand("tp @p 0 -60 0 0 -10");
		ctx.runOnClient(mc -> {
			s.skyImage.set("image:tour_sunset.png");
			s.customSky.set(true);
		});
		ctx.waitFor(mc -> dev.mordo.kairo.render.SkyImage.ready(), 200);
		ctx.waitTicks(10);
		shot(ctx, "12_custom_sky_day");
		server.runCommand("time set 18000");
		ctx.waitTicks(10);
		shot(ctx, "13_custom_sky_night");
		ctx.runOnClient(mc -> {
			s.customSky.reset();
			VoidSky.testTime = -1;
		});
		server.runCommand("time set 1000");
		ctx.waitTicks(5);
	}

	// ---- you ----

	private void you(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		VoidSettings s = VoidSettings.INSTANCE;
		server.runCommand("tp @p 0 -60 0 30 10");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_BACK));
		for (VoidSettings.AuraStyle style : VoidSettings.AuraStyle.values()) {
			if (style == VoidSettings.AuraStyle.NONE) {
				continue;
			}
			ctx.runOnClient(mc -> s.aura.set(style));
			ctx.waitTicks(15);
			shot(ctx, "20_aura_" + style.name().toLowerCase());
		}
		ctx.runOnClient(mc -> {
			s.aura.reset();
			s.glyphs.set(true);
		});
		ctx.waitTicks(15);
		shot(ctx, "21_line_glyphs");
		ctx.runOnClient(mc -> s.glyphs.set(false));
		// Trails: walk the player along a curve, a small step every tick.
		for (VoidSettings.TrailStyle style : VoidSettings.TrailStyle.values()) {
			if (style == VoidSettings.TrailStyle.NONE) {
				continue;
			}
			ctx.runOnClient(mc -> s.playerTrail.set(style));
			for (int i = 0; i <= 16; i++) {
				double a = i / 16.0 * Math.PI * 0.8;
				server.runCommand(String.format(java.util.Locale.ROOT, "tp @p %.3f -60 %.3f 120 15", 4 - Math.cos(a) * 4, Math.sin(a) * 4));
				ctx.waitTicks(1);
			}
			shot(ctx, "22_trail_" + style.name().toLowerCase());
			ctx.waitTicks(25);
		}
		ctx.runOnClient(mc -> s.playerTrail.reset());
		// Your look, from the front.
		server.runCommand("tp @p 0 -60 0 0 0");
		ctx.runOnClient(mc -> mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT));
		for (VoidSettings.SelfLook look : VoidSettings.SelfLook.values()) {
			ctx.runOnClient(mc -> s.selfLook.set(look));
			ctx.waitTicks(10);
			shot(ctx, "23_look_" + look.name().toLowerCase());
		}
		ctx.runOnClient(mc -> {
			s.selfLook.reset();
			s.ownNametag.set(true);
		});
		for (VoidSettings.NametagStyle style : VoidSettings.NametagStyle.values()) {
			ctx.runOnClient(mc -> s.nametags.set(style));
			ctx.waitTicks(8);
			shot(ctx, "24_nametag_" + style.name().toLowerCase());
		}
		ctx.runOnClient(mc -> {
			s.nametags.reset();
			s.ownNametag.reset();
			mc.options.setCameraType(CameraType.FIRST_PERSON);
		});
	}

	// ---- through your own eyes ----

	private void pov(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		VoidSettings s = VoidSettings.INSTANCE;
		server.runCommand("time set 18000");
		server.runCommand("tp @p 0 -60 0 20 -12");
		ctx.runOnClient(mc -> {
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			s.worldRibbons.set(true);
			s.motes.set(true);
			s.glyphs.set(true);
			s.aura.set(VoidSettings.AuraStyle.SHARDS);
		});
		ctx.waitTicks(30);
		shot(ctx, "25_pov_night");
		server.runCommand("tp @p 0 -60 0 200 -20");
		ctx.waitTicks(10);
		shot(ctx, "26_pov_night_behind");
		server.runCommand("tp @p 0 -60 0 20 40");
		ctx.waitTicks(10);
		shot(ctx, "27_pov_glyphs_down");
		ctx.runOnClient(mc -> s.ribbonColor.set(VoidSettings.AuroraColor.RAINBOW));
		server.runCommand("tp @p 0 -60 0 20 -12");
		ctx.waitTicks(10);
		shot(ctx, "28_pov_rainbow_ribbons");
		ctx.runOnClient(mc -> s.ribbonColor.reset());
		server.runCommand("time set 1000");
		ctx.waitTicks(10);
		shot(ctx, "29_pov_day");
		ctx.runOnClient(mc -> {
			s.worldRibbons.set(false);
			s.motes.set(false);
			s.glyphs.set(false);
			s.aura.reset();
		});
	}

	// ---- light particles ----

	private void lightParticles(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		server.runCommand("tp @p 0 -60 0 0 10");
		ctx.waitTicks(5);
		String[] shots = {"crit 0 -59 3 0.4 0.4 0.4 0.6 40", "poof 0 -59 3 0.3 0.3 0.3 0.05 25", "totem_of_undying 0 -59 3 0.3 0.6 0.3 0.6 60",
			"heart 0 -58.5 3 0.6 0.4 0.6 0 8", "explosion_emitter 0 -59 5 0 0 0 0 1"};
		String[] names = {"crit", "poof", "totem", "heart", "explosion"};
		for (int i = 0; i < shots.length; i++) {
			server.runCommand("particle minecraft:" + shots[i]);
			ctx.waitTicks(3);
			shot(ctx, "36_light_" + names[i]);
			ctx.waitTicks(20);
		}
		// Kairo's own effects go the same way: a landing ring and a kill effect.
		ctx.runOnClient(mc -> {
			var level = mc.level;
			for (int i = 0; i < 24; i++) {
				double a = i / 24.0 * Math.PI * 2;
				dev.mordo.kairo.render.LightParticles.add(level, net.minecraft.core.particles.ParticleTypes.END_ROD, Math.cos(a) * 0.5, -59, 3 + Math.sin(a) * 0.5,
					Math.cos(a) * 0.15, 0.05, Math.sin(a) * 0.15);
			}
			Kairo.LOGGER.info("CHECK light particles live: {}", dev.mordo.kairo.render.LightParticles.count());
		});
		ctx.waitTicks(3);
		shot(ctx, "37_light_kairo_effect");
		ctx.waitTicks(30);
	}

	// ---- combat ----

	private void combat(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		VoidSettings s = VoidSettings.INSTANCE;
		server.runCommand("tp @p 0 -60 0 0 0");
		server.runCommand("summon husk 0 -60 3 {NoAI:1b,Rotation:[180f,0f],PersistenceRequired:1b}");
		ctx.waitTicks(15);
		Vec3 hit = new Vec3(0, -58.9, 2.55);
		for (VoidSettings.HitImpact style : VoidSettings.HitImpact.values()) {
			if (style == VoidSettings.HitImpact.NONE) {
				continue;
			}
			ctx.runOnClient(mc -> {
				s.hitImpact.set(style);
				VoidWorldFx.onHit(hit, 1.95f, style.ordinal() % 2 == 0);
			});
			ctx.waitTicks(3);
			shot(ctx, "30_impact_" + style.name().toLowerCase());
			ctx.waitTicks(15);
		}
		ctx.runOnClient(mc -> {
			s.hitImpact.reset();
			dev.mordo.kairo.core.GameVisuals.INSTANCE.damageNumbers.set(true);
			var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(8), e -> !(e instanceof net.minecraft.world.entity.player.Player)).getFirst();
			dev.mordo.kairo.render.KineticNumbers.add(husk, 4, false);
		});
		ctx.waitTicks(3);
		shot(ctx, "31_marker_hit");
		ctx.runOnClient(mc -> {
			var husk = mc.level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, mc.player.getBoundingBox().inflate(8), e -> !(e instanceof net.minecraft.world.entity.player.Player)).getFirst();
			dev.mordo.kairo.render.KineticNumbers.add(husk, 7.5f, true);
		});
		ctx.waitTicks(3);
		shot(ctx, "32_marker_crit_combo");
		ctx.waitTicks(17);
		shot(ctx, "33_marker_breaking");
		ctx.runOnClient(mc -> dev.mordo.kairo.core.GameVisuals.INSTANCE.damageNumbers.reset());
		// The cinematic kill, held at full strength, each style, and the calm one.
		server.runCommand("tp @p 0 -60 -2 20 8");
		ctx.waitTicks(10);
		for (VoidSettings.NoirStyle style : VoidSettings.NoirStyle.values()) {
			ctx.runOnClient(mc -> {
				s.noirStyle.set(style);
				VoidNoir.testAmount = 1;
			});
			ctx.waitTicks(4);
			shot(ctx, "34_noir_" + style.name().toLowerCase());
		}
		ctx.runOnClient(mc -> {
			s.noirStyle.reset();
			ClientSettings.INSTANCE.reduceFlashing.set(true);
		});
		ctx.waitTicks(4);
		shot(ctx, "35_noir_fewer_flashes");
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.reduceFlashing.reset();
			VoidNoir.testAmount = -1;
			s.noirKill.set(true);
			VoidNoir.onKill(new Vec3(1.5, -59, 2));
		});
		ctx.waitTicks(4);
		shot(ctx, "35_noir_wave_smooth");
		ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK noir wave on its way (not a flash): amount after 0.2 s = {}", VoidNoir.amount()));
		ctx.waitTicks(40);
		ctx.runOnClient(mc -> {
			s.noirSpeed.set(VoidSettings.NoirSpeed.SHARP);
			VoidNoir.onKill(new Vec3(1.5, -59, 2));
		});
		ctx.waitTicks(2);
		shot(ctx, "35_noir_wave_sharp");
		ctx.waitTicks(30);
		ctx.runOnClient(mc -> s.noirSpeed.reset());
		ctx.runOnClient(mc -> {
			Kairo.LOGGER.info("CHECK noir over after its length: {}", VoidNoir.amount() == 0);
			s.noirKill.reset();
		});
		server.runCommand("kill @e[type=husk]");
	}

	// ---- block overlay ----

	private void overlay(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		VoidSettings s = VoidSettings.INSTANCE;
		server.runCommand("setblock 0 -60 2 minecraft:polished_andesite");
		server.runCommand("tp @p 0 -60 0 0 30");
		ctx.waitTicks(10);
		for (VoidSettings.BlockOverlay style : VoidSettings.BlockOverlay.values()) {
			if (style == VoidSettings.BlockOverlay.NONE) {
				continue;
			}
			ctx.runOnClient(mc -> s.blockOverlay.set(style));
			ctx.waitTicks(8);
			shot(ctx, "40_overlay_" + style.name().toLowerCase());
		}
		// Gliding: look from one block to another and catch the overlay on its way.
		server.runCommand("setblock 2 -60 2 minecraft:polished_andesite");
		ctx.runOnClient(mc -> s.blockOverlay.set(VoidSettings.BlockOverlay.BRACKETS));
		server.runCommand("tp @p 0 -60 0 -11 24");
		ctx.waitTicks(15);
		server.runCommand("tp @p 0 -60 0 -45 18");
		ctx.waitTicks(1);
		shot(ctx, "41_overlay_glide_moving");
		ctx.waitTicks(15);
		shot(ctx, "42_overlay_glide_settled");
		ctx.runOnClient(mc -> s.blockOverlay.set(VoidSettings.BlockOverlay.NONE));
		server.runCommand("setblock 0 -60 2 minecraft:air");
		server.runCommand("setblock 2 -60 2 minecraft:air");
	}

	// ---- music emote ----

	private void music(ClientGameTestContext ctx, net.fabricmc.fabric.api.client.gametest.v1.context.TestServerContext server) {
		Cosmetics c = Cosmetics.get();
		server.runCommand("tp @p 0 -60 0 0 0");
		ctx.runOnClient(mc -> {
			c.music1.set("file:tour_beat.wav");
			c.music1Bpm.set(120.0);
			c.music1Name.set("Pull my pearl");
			mc.options.setCameraType(CameraType.THIRD_PERSON_FRONT);
			Emotes.playHere(Emotes.Emote.MUSIC_1);
			Kairo.LOGGER.info("CHECK music emote name on the wheel: {}", Emotes.Emote.MUSIC_1.label());
		});
		ctx.waitFor(mc -> dev.mordo.kairo.sound.SoundLibrary.resolveSong("file:tour_beat.wav") != null, 200);
		ctx.waitTicks(12);
		ctx.runOnClient(mc -> Kairo.LOGGER.info("CHECK music emote: song read, emote still {}, sounds playing on the jukebox channel {}",
			Emotes.current(), "(listen in the run)"));
		shot(ctx, "50_music_beat_bounce_a");
		ctx.waitTicks(5);
		shot(ctx, "51_music_beat_bounce_b");
		ctx.runOnClient(mc -> {
			c.music1Dance.set(Cosmetics.MusicDance.BEAT_STEP);
		});
		ctx.waitTicks(7);
		shot(ctx, "52_music_beat_step");
		ctx.runOnClient(mc -> {
			Emotes.stop();
			mc.options.setCameraType(CameraType.FIRST_PERSON);
			c.music1Dance.reset();
		});
		ctx.waitTicks(5);
	}

	// ---- the new cards ----

	private void menus(ClientGameTestContext ctx) {
		ctx.runOnClient(mc -> mc.gui.setScreen(MenuScreen.openOn(MenuScreen::visualsPage, null)));
		ctx.waitTicks(25);
		shot(ctx, "59_visuals_favourites");
		int you = VisualsGroups.YOU;
		ctx.runOnClient(mc -> ((MenuScreen) mc.gui.screen()).voidVisuals().select(you));
		ctx.waitTicks(20);
		shot(ctx, "60_visuals_you");
		ctx.runOnClient(mc -> ((MenuScreen) mc.gui.screen()).voidVisuals().select(2));
		ctx.waitTicks(20);
		shot(ctx, "61_visuals_combat");
		ctx.runOnClient(mc -> ((MenuScreen) mc.gui.screen()).voidVisuals().openVisual("Custom sky"));
		ctx.waitTicks(20);
		shot(ctx, "62_custom_sky_card");
		ctx.runOnClient(mc -> ((MenuScreen) mc.gui.screen()).voidVisuals().openVisual("Aura"));
		ctx.waitTicks(20);
		shot(ctx, "63_aura_card");
		ctx.runOnClient(mc -> {
			mc.gui.setScreen(MenuScreen.openOn(MenuScreen::cosmetics, null));
		});
		ctx.waitTicks(20);
		ctx.runOnClient(mc -> ((MenuScreen) mc.gui.screen()).cosmetics().select(5));
		ctx.waitTicks(15);
		ctx.getInput().scroll(-30);
		ctx.waitTicks(15);
		shot(ctx, "64_wardrobe_music_options");
		ctx.runOnClient(mc -> mc.gui.setScreen(null));
		ctx.waitTicks(5);
	}

	/** Index of the "You" group in Kairo Void's Visuals (0 = All). */
	private static final class VisualsGroups {
		static final int YOU = dev.mordo.kairo.gui.menu.VoidVisualsPage.Group.YOU.ordinal() + 1;
	}
}
