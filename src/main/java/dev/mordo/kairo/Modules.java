package dev.mordo.kairo;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.hud.ArmorStatusHud;
import dev.mordo.kairo.hud.CoordinatesHud;
import dev.mordo.kairo.hud.CpsHud;
import dev.mordo.kairo.hud.FpsHud;
import dev.mordo.kairo.hud.KeystrokesHud;
import dev.mordo.kairo.hud.PingHud;
import dev.mordo.kairo.mods.ChatTweaks;
import dev.mordo.kairo.mods.Crosshair;
import dev.mordo.kairo.mods.Fullbright;
import dev.mordo.kairo.mods.HitSounds;
import dev.mordo.kairo.mods.HurtEffects;
import dev.mordo.kairo.mods.TotemChanger;
import dev.mordo.kairo.mods.ScrollableTooltips;
import dev.mordo.kairo.mods.ShulkerPreview;
import dev.mordo.kairo.mods.TotemPops;
import dev.mordo.kairo.mods.Waypoints;
import dev.mordo.kairo.mods.Zoom;

/**
 * Registers every module, in the order they appear in the Mods panel.
 * Phase 4: settings are declared; what each mod does is implemented in phases 5 and 6.
 */
final class Modules {
	private Modules() {
	}

	static void registerAll() {
		ModuleManager.register(new KeystrokesHud());
		ModuleManager.register(new CpsHud());
		ModuleManager.register(new CoordinatesHud());
		ModuleManager.register(new PingHud());
		ModuleManager.register(new FpsHud());
		ModuleManager.register(new ArmorStatusHud());
		ModuleManager.register(new dev.mordo.kairo.hud.DirectionHud());
		ModuleManager.register(new TotemPops());
		ModuleManager.register(new HurtEffects());
		ModuleManager.register(new HitSounds());
		ModuleManager.register(new TotemChanger());
		ModuleManager.register(new Crosshair());
		ModuleManager.register(new dev.mordo.kairo.mods.Hotbar());
		ModuleManager.register(new dev.mordo.kairo.mods.Saturation());
		ModuleManager.register(new dev.mordo.kairo.mods.InventoryGlass());
		ModuleManager.register(new dev.mordo.kairo.mods.BlockOutline());
		ModuleManager.register(new dev.mordo.kairo.mods.Skins3D());
		ModuleManager.register(new dev.mordo.kairo.cosmetics.Cosmetics());
		ModuleManager.register(new dev.mordo.kairo.mods.ItemScale());
		ModuleManager.register(new Zoom());
		ModuleManager.register(new Fullbright());
		ModuleManager.register(new Waypoints());
		ModuleManager.register(new ChatTweaks());
		ModuleManager.register(new ScrollableTooltips());
		ModuleManager.register(new ShulkerPreview());
		ModuleManager.register(new dev.mordo.kairo.mods.Screenshots());
	}
}
