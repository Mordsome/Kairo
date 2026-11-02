package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;

/**
 * Camera tilt when hurt, explosion particles from crystals and anchors, and the fire overlay. Visual only.
 * (Vanilla has no explosion camera shake; the hurt tilt is the only damage camera movement.)
 */
public final class HurtEffects extends Module {
	public final SliderSetting hurtTilt = add(new SliderSetting("hurt_tilt", "Hurt camera tilt", 1.0, 0.0, 1.0, 0.05).percent()
		.description("On top of vanilla's Damage Tilt option")
		.<SliderSetting>pvpDefault(0.0));
	public final SliderSetting explosionParticles = add(new SliderSetting("explosion_particles", "Explosion particles", 1.0, 0.0, 1.0, 0.05).percent()
		.description("Debris and smoke from crystals and respawn anchors")
		.<SliderSetting>pvpDefault(0.1));
	public final BooleanSetting hideExplosionFlash = add(new BooleanSetting("hide_explosion_flash", "Hide explosion flash", false)
		.description("Skip the big white explosion puff that covers the screen")
		.<BooleanSetting>pvpDefault(true));
	public final SliderSetting explosionLimit = add(new SliderSetting("explosion_limit", "Max explosions per second", 0, 0, 10, 1)
		.format(v -> v < 0.5 ? "No limit" : Math.round(v) + "/s")
		.description("Extra explosions within a second show no particles at all")
		.<SliderSetting>pvpDefault(3.0));

	/** Times (ms) of recent explosions that were allowed to show particles. */
	private final long[] recentExplosions = new long[16];
	private int recentIndex;
	public final SliderSetting fireOverlay = add(new SliderSetting("fire_overlay", "Fire overlay height", 1.0, 0.3, 1.0, 0.05).percent()
		.<SliderSetting>pvpDefault(0.55));
	public final SliderSetting fireOpacity = add(new SliderSetting("fire_opacity", "Fire overlay opacity", 1.0, 0.2, 1.0, 0.05).percent()
		.<SliderSetting>pvpDefault(0.7));

	public HurtEffects() {
		super("hurt_effects", "Hurt Camera", "Damage tilt, explosions, fire overlay", Category.PVP, Icon.HEART, true);
	}

	/** Effective explosion particle amount (1 = vanilla). */
	public float explosionAmount() {
		return isEnabled() ? explosionParticles.floatValue() : 1;
	}

	/**
	 * Called once per explosion packet: false if this explosion should show no particles at all
	 * (over the per-second limit). Sound and everything else are untouched.
	 */
	public boolean allowExplosionParticles() {
		int limit = isEnabled() ? explosionLimit.intValue() : 0;
		if (limit <= 0) {
			return true;
		}
		long now = System.currentTimeMillis();
		int inLastSecond = 0;
		for (long t : recentExplosions) {
			if (now - t < 1000) {
				inLastSecond++;
			}
		}
		if (inLastSecond >= Math.min(limit, recentExplosions.length)) {
			return false;
		}
		recentExplosions[recentIndex] = now;
		recentIndex = (recentIndex + 1) % recentExplosions.length;
		return true;
	}

	/** Whether to show the big explosion emitter (the white puff). */
	public boolean showExplosionFlash() {
		return !(isEnabled() && hideExplosionFlash.isOn());
	}

	/** How far to push the fire overlay down, in overlay units (0 = vanilla). */
	public float fireDrop() {
		return isEnabled() ? (1 - fireOverlay.floatValue()) * 0.6f : 0;
	}

	public float fireAlpha() {
		return isEnabled() ? fireOpacity.floatValue() : 1;
	}
}
