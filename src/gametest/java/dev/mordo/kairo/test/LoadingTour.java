package dev.mordo.kairo.test;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.minecraft.client.gui.screens.TitleScreen;

/** The loading screen in the accent colour (run with build/tour-loading): an orange accent, then a reload. */
final class LoadingTour {
	void run(ClientGameTestContext ctx) {
		String p = "loading_" + (Edition.isVoid() ? "void" : "kairo");
		ctx.getInput().resizeWindow(1280, 720);
		ctx.waitFor(mc -> mc.gui.overlay() == null);
		ctx.runOnClient(mc -> {
			ClientSettings.INSTANCE.onboarded.set(true);
			ClientSettings.INSTANCE.wallpaperColors.set(false);
			ClientSettings.INSTANCE.accent.set(0xFFFF8A3D);
			mc.gui.setScreen(new TitleScreen());
		});
		ctx.waitTicks(10);
		ctx.runOnClient(mc -> mc.reloadResourcePacks());
		ctx.waitTicks(2);
		ctx.takeScreenshot(p + "_orange");
		ctx.waitFor(mc -> mc.gui.overlay() == null, 1200);
		ctx.waitTicks(20);
		ctx.takeScreenshot(p + "_after");
		ctx.runOnClient(mc -> {
			Kairo.LOGGER.info("CHECK loading done, screen: {}", mc.gui.screen() == null ? "none" : mc.gui.screen().getClass().getSimpleName());
			ClientSettings.INSTANCE.accent.reset();
			ClientSettings.INSTANCE.wallpaperColors.reset();
		});
	}
}
