package dev.mordo.kairo.test;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestSingleplayerContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Dev-only backend check (run with an empty file build/tour-vk, plus -Pvulkan for Vulkan): goes through the menus
 * and a world one piece at a time, logs "VKSTEP" with the average frame time of each step, and takes a screenshot of
 * each, so OpenGL and Vulkan can be compared. The window is not resized (resizing crashes 26.3's Vulkan backend in
 * game tests, with or without Kairo).
 */
final class VulkanBisect {
	private final String api = System.getProperty("sun.java.command", "").contains("vulkan") ? "vk" : "gl";
	private final String p = api + "_" + (Edition.isVoid() ? "v" : "k") + "_";

	void run(ClientGameTestContext ctx) {
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			mc.options.pauseOnLostFocus = false;
		});
		step(ctx, "01 vanilla title", mc -> mc.gui.setScreen(new TitleScreen()));
		step(ctx, "02 Kairo main menu", mc -> mc.gui.setScreen(new dev.mordo.kairo.gui.main.MainMenuScreen()));
		step(ctx, "03 Kairo panel over the menu", mc -> mc.gui.setScreen(
			dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::mods, null)));
		step(ctx, "04 back to title", mc -> mc.gui.setScreen(new TitleScreen()));
		try (TestSingleplayerContext world = ctx.worldBuilder().create()) {
			var server = world.getServer();
			server.runCommand("gamerule advance_time false");
			server.runCommand("time set 1000");
			server.runCommand("weather clear");
			world.getConnection().waitForChunksRender();
			step(ctx, "10 world, day", mc -> mc.gui.setScreen(null));
			step(ctx, "11 world, ring menu", mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(m -> m.ring(), null)));
			step(ctx, "12 world, visuals panel", mc -> mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(
				dev.mordo.kairo.gui.menu.MenuScreen::visualsPage, null)));
			step(ctx, "13 world, inventory", mc -> mc.gui.setScreen(
				new net.minecraft.client.gui.screens.inventory.InventoryScreen(mc.player)));
			ctx.runOnClient(mc -> mc.gui.setScreen(null));
			server.runCommand("time set 18000");
			step(ctx, "14 world, night", mc -> { });
			if (Edition.isVoid()) {
				step(ctx, "20 void, galaxy atmosphere", mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.atmosphere.set(
					dev.mordo.kairo.core.VoidSettings.Atmosphere.GALAXY));
				step(ctx, "21 void, storm atmosphere", mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.atmosphere.set(
					dev.mordo.kairo.core.VoidSettings.Atmosphere.STORM));
				ctx.runOnClient(mc -> dev.mordo.kairo.core.VoidSettings.INSTANCE.atmosphere.reset());
			}
			step(ctx, "30 bloom, filter, vignette, motion blur", mc -> {
				var v = dev.mordo.kairo.core.GameVisuals.INSTANCE;
				v.glow.set(0.6);
				v.filter.set(dev.mordo.kairo.core.GameVisuals.Filter.CINEMATIC);
				v.vignette.set(0.5);
				v.motionBlur.set(0.5);
			});
			ctx.runOnClient(mc -> {
				var v = dev.mordo.kairo.core.GameVisuals.INSTANCE;
				v.glow.reset();
				v.filter.reset();
				v.vignette.reset();
				v.motionBlur.reset();
			});
		}
		step(ctx, "99 done", mc -> mc.gui.setScreen(new TitleScreen()));
	}

	private void step(ClientGameTestContext ctx, String name, Consumer<Minecraft> action) {
		ctx.runOnClient(mc -> {
			Kairo.LOGGER.info("VKSTEP {}", name);
			action.accept(mc);
		});
		ctx.waitTicks(20); // let it settle
		long[] sum = new long[2];
		for (int i = 0; i < 60; i++) {
			ctx.waitTicks(1);
			ctx.runOnClient(mc -> {
				sum[0] += mc.getFrameTimeNs();
				sum[1]++;
			});
		}
		ctx.takeScreenshot(p + name.replace(' ', '_').replace(',', '_'));
		ctx.runOnClient(mc -> Kairo.LOGGER.info("VKSTEP ok: {}  frame {} ms", name,
			String.format(Locale.ROOT, "%.2f", sum[0] / 1e6 / Math.max(1, sum[1]))));
	}
}
