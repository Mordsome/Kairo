package dev.mordo.kairo.api;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.mods.HitSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.SystemToast;
import net.minecraft.network.chat.Component;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Parts of Kairo that plain Kairo only has with an addon (since 0.4.0): the code is still in Kairo, but its settings
 * are locked to "off" and hidden until the matching addon unlocks them ({@link AddonRegistrar#unlock}). The player's
 * own choices are kept, so they come back as they were once the addon is installed. Kairo Void has everything built in.
 */
public enum Features {
	/** Attack, mining and inspect animations, the swing trail, held item looks and the shimmer colour. */
	ANIMATIONS("animations", "Animations addon"),
	/** Hit particles, kill effects, the edge flash, damage numbers and the sound packs. */
	COMBAT_EFFECTS("combat-effects", "Combat Effects addon");

	public final String id;
	public final String addonName;

	Features(String id, String addonName) {
		this.id = id;
		this.addonName = addonName;
	}

	private static final Set<Features> UNLOCKED = new LinkedHashSet<>();

	/** True when this feature is there: always in Kairo Void, in Kairo only with its addon. */
	public boolean has() {
		return Edition.isVoid() || UNLOCKED.contains(this);
	}

	static void unlock(String id) {
		for (Features feature : values()) {
			if (feature.id.equals(id.toLowerCase(Locale.ROOT))) {
				UNLOCKED.add(feature);
				return;
			}
		}
		throw new IllegalArgumentException("Unknown Kairo feature '" + id + "'");
	}

	/** Locks the settings of each feature to "off" unless it's there. Called once while Kairo starts, before addons load. */
	public static void lockSettings() {
		GameVisuals v = GameVisuals.INSTANCE;
		v.swingStyle.lockedUnless(ANIMATIONS::has, GameVisuals.SwingStyle.VANILLA);
		v.miningStyle.lockedUnless(ANIMATIONS::has, GameVisuals.MiningStyle.VANILLA);
		v.inspectStyle.lockedUnless(ANIMATIONS::has, v.inspectStyle.getDefault());
		v.inspectKey.lockedUnless(ANIMATIONS::has, Keybind.NONE);
		v.swingDuration.lockedUnless(ANIMATIONS::has, v.swingDuration.getDefault());
		v.miningDuration.lockedUnless(ANIMATIONS::has, v.miningDuration.getDefault());
		v.swingTrail.lockedUnless(ANIMATIONS::has, false);
		v.swingTrailRainbow.lockedUnless(ANIMATIONS::has, false);
		v.heldShine.lockedUnless(ANIMATIONS::has, GameVisuals.HeldShine.NONE);
		v.glintColor.lockedUnless(ANIMATIONS::has, GameVisuals.GlintColor.VANILLA);
		v.glintCustom.lockedUnless(ANIMATIONS::has, v.glintCustom.getDefault());

		v.hitBurst.lockedUnless(COMBAT_EFFECTS::has, GameVisuals.HitBurst.NONE);
		v.hitAmount.lockedUnless(COMBAT_EFFECTS::has, v.hitAmount.getDefault());
		v.killEffect.lockedUnless(COMBAT_EFFECTS::has, GameVisuals.KillEffect.NONE);
		v.killSound.lockedUnless(COMBAT_EFFECTS::has, false);
		v.hitFlash.lockedUnless(COMBAT_EFFECTS::has, false);
		v.damageNumbers.lockedUnless(COMBAT_EFFECTS::has, false);
		HitSounds sounds = ModuleManager.get(HitSounds.class);
		sounds.pack.lockedUnless(COMBAT_EFFECTS::has, HitSounds.Pack.NONE);
		sounds.packVolume.lockedUnless(COMBAT_EFFECTS::has, sounds.packVolume.getDefault());
	}

	/**
	 * Once, in plain Kairo without both addons: a pop-up saying where these features went. Players who never used
	 * them lose nothing; players who did get them back as they were by installing the addon.
	 */
	public static void noticeOnce() {
		ClientSettings s = ClientSettings.INSTANCE;
		if (Edition.isVoid() || s.addonsNoticeShown.isOn() || (ANIMATIONS.has() && COMBAT_EFFECTS.has())) {
			return;
		}
		s.addonsNoticeShown.set(true);
		Minecraft mc = Minecraft.getInstance();
		SystemToast.add(mc.gui.toastManager(), SystemToast.SystemToastId.PERIODIC_NOTIFICATION,
			Component.literal("Animations and kill effects are addons now"),
			Component.literal("Get the Animations and Combat Effects addons in the Kairo Launcher (Addons)"));
	}
}
