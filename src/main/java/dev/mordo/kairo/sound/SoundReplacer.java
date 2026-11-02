package dev.mordo.kairo.sound;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.HitSounds;
import dev.mordo.kairo.mods.TotemChanger;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.Set;

/** Decides whether a sound about to play should be swapped for one of the player's picks (see SoundEngineMixin). */
public final class SoundReplacer {
	private static final Set<String> HURT = Set.of(
		"entity.player.hurt", "entity.player.hurt_on_fire", "entity.player.hurt_drown",
		"entity.player.hurt_freeze", "entity.player.hurt_sweet_berry_bush");
	private static final Set<String> ATTACK = Set.of(
		"entity.player.attack.strong", "entity.player.attack.weak", "entity.player.attack.crit",
		"entity.player.attack.sweep", "entity.player.attack.knockback");

	private SoundReplacer() {
	}

	public static @Nullable SoundInstance replace(SoundInstance instance) {
		Identifier id = instance.getIdentifier();
		if (!id.getNamespace().equals("minecraft")) {
			return null; // ours, or another mod's
		}
		String path = id.getPath();
		if (path.equals("item.totem.use")) {
			TotemChanger totem = ModuleManager.get(TotemChanger.class);
			String key = totem.popSoundKey();
			return key == null ? null
				: CustomSoundInstance.at(key, instance, totem.popVolume.floatValue(), totem.popPitch.floatValue());
		}
		boolean hurt = HURT.contains(path);
		if (hurt || ATTACK.contains(path)) {
			HitSounds hits = ModuleManager.get(HitSounds.class);
			String packKey = !hurt && isNearMe(instance) ? hits.packHitKey(path.equals("entity.player.attack.crit")) : null;
			if (packKey != null) {
				return CustomSoundInstance.at(packKey, instance, hits.packVolume.floatValue(), 1);
			}
			String key = hurt ? hits.hurtKey(isNearMe(instance)) : (isNearMe(instance) ? hits.hitKey() : null);
			if (key == null) {
				return null;
			}
			float pitch = hits.pitchVariation.isOn() ? instance.getPitch() : 1;
			float volume = hurt ? hits.hurtVolume.floatValue() : hits.hitVolume.floatValue();
			return CustomSoundInstance.at(key, instance, volume, pitch);
		}
		return null;
	}

	/** Sounds about the local player are played at (or right by) the player's position. */
	private static boolean isNearMe(SoundInstance instance) {
		LocalPlayer player = Minecraft.getInstance().player;
		if (player == null) {
			return false;
		}
		if (instance.isRelative()) {
			return true;
		}
		double dx = instance.getX() - player.getX();
		double dy = instance.getY() - player.getY();
		double dz = instance.getZ() - player.getZ();
		return dx * dx + dy * dy + dz * dz < 2.25;
	}

	static boolean isOurs(Identifier id) {
		return id.getNamespace().equals(ClientInfo.MOD_ID);
	}
}
