package dev.mordo.kairo;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.config.ConfigManager;
import dev.mordo.kairo.core.keybinds.ClientKeybinds;
import dev.mordo.kairo.core.keybinds.KeybindManager;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.hud.HudManager;
import dev.mordo.kairo.render.KairoPipelines;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class Kairo implements ClientModInitializer {
	public static final Logger LOGGER = LoggerFactory.getLogger(ClientInfo.NAME);
	private static boolean pipelinesWarm;

	@Override
	public void onInitializeClient() {
		LOGGER.info("Starting {} {} by {}", ClientInfo.FULL_NAME, ClientInfo.VERSION, ClientInfo.AUTHOR);

		ModuleManager.registerHolder(ClientSettings.INSTANCE);
		ModuleManager.registerHolder(ClientKeybinds.INSTANCE);
		ModuleManager.registerHolder(dev.mordo.kairo.core.MainMenuSettings.INSTANCE);
		ModuleManager.registerHolder(dev.mordo.kairo.core.PvpSettings.INSTANCE);
		ModuleManager.registerHolder(dev.mordo.kairo.core.GameVisuals.INSTANCE);
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			ModuleManager.registerHolder(dev.mordo.kairo.core.VoidSettings.INSTANCE);
		}
		Modules.registerAll();

		KeybindManager.collect();
		HudManager.init();
		Toasts.register();
		ConfigManager.init();
		for (var module : ModuleManager.modules()) {
			module.init();
		}
		dev.mordo.kairo.render.background.MenuBackground.prepareAtStartup();
		dev.mordo.kairo.core.update.UpdateInstaller.init();
		dev.mordo.kairo.render.HitFx.init();
		dev.mordo.kairo.render.WorldFx.init();
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			// Auras, glyphs, trails, hit impacts and the block overlay, drawn into the world.
			net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents.COLLECT_SUBMITS.register(dev.mordo.kairo.render.VoidWorldFx::submit);
		}
		// Registered after the config is loaded, so restoring the saved mode at start-up stays silent.
		dev.mordo.kairo.core.PvpMode.addListener(dev.mordo.kairo.sound.UiSounds::pvp);

		ClientTickEvents.END_CLIENT_TICK.register(client -> {
			if (!pipelinesWarm && client.isGameLoadFinished()) {
				// Only now are our shader files visible to the game's shader cache.
				pipelinesWarm = true;
				warmUpRendering();
			}
			ModuleManager.tick();
			dev.mordo.kairo.render.HitFx.tick(client);
			dev.mordo.kairo.render.WorldFx.tick(client);
			dev.mordo.kairo.render.Atmospheres.tick(client);
			dev.mordo.kairo.render.ProjectileTrails.tick(client);
			dev.mordo.kairo.render.HitTint.tick(client);
			dev.mordo.kairo.render.GlintTint.tick(client);
			ModuleManager.get(dev.mordo.kairo.mods.TotemChanger.class).tickRebuild();
			ModuleManager.get(dev.mordo.kairo.mods.InventoryGlass.class).tickRebuild();
			dev.mordo.kairo.render.background.WallpaperColors.tick();
			dev.mordo.kairo.gui.Theme.refresh();
			dev.mordo.kairo.core.update.UpdateChecker.tick(client);
			ConfigManager.tick();
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> {
			ConfigManager.save();
			// Only if the player clicked Update and the download checked out.
			dev.mordo.kairo.core.update.UpdateInstaller.onExit();
		});

		LOGGER.info("{} ready: {} modules, profile '{}'", ClientInfo.NAME, ModuleManager.modules().size(), ConfigManager.activeProfile());
	}

	/** Compiles our shaders up front: no hitch on first menu open, and errors show up in the log right away. */
	private static void warmUpRendering() {
		try {
			KairoPipelines.warmUp();
			LOGGER.info("Render pipelines compiled");
		} catch (RuntimeException e) {
			LOGGER.error("Couldn't compile render pipelines; menus will fall back where possible", e);
		}
	}
}
