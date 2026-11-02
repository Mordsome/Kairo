package dev.mordo.kairo.sound;

import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.valueproviders.ConstantFloat;

/**
 * Plays one of {@link SoundLibrary}'s sounds through the normal sound engine (volume sliders, 3D position,
 * device switching all work as usual). It resolves to itself instead of looking the id up in sounds.json.
 */
public final class CustomSoundInstance extends AbstractSoundInstance {
	private CustomSoundInstance(String key, SoundSource source, float volume, float pitch) {
		super(SoundLibrary.location(key), source, SoundInstance.createUnseededRandom());
		this.volume = volume;
		this.pitch = pitch;
	}

	/** At a position in the world (replaces a world sound). */
	public static CustomSoundInstance at(String key, SoundInstance original, float volume, float pitch) {
		CustomSoundInstance s = new CustomSoundInstance(key, original.getSource(), volume, pitch);
		s.x = original.getX();
		s.y = original.getY();
		s.z = original.getZ();
		s.relative = original.isRelative();
		s.attenuation = original.getAttenuation();
		return s;
	}

	/** At a point in the world (a new sound, not a replaced one). */
	public static CustomSoundInstance world(String key, net.minecraft.world.phys.Vec3 at, float volume, float pitch) {
		CustomSoundInstance s = new CustomSoundInstance(key, SoundSource.PLAYERS, volume, pitch);
		s.x = at.x;
		s.y = at.y;
		s.z = at.z;
		return s;
	}

	/** Non-positional, for UI and previews. */
	public static CustomSoundInstance ui(String key, float volume, float pitch) {
		CustomSoundInstance s = new CustomSoundInstance(key, SoundSource.UI, volume, pitch);
		s.relative = true;
		s.attenuation = Attenuation.NONE;
		return s;
	}

	/** A vanilla sound event, non-positional (previewing "Vanilla" in a sound picker). */
	public static SoundInstance vanillaUi(Identifier event, float volume) {
		return SimpleSoundInstance.forUI(SoundEvent.createVariableRangeEvent(event), 1, volume);
	}

	@Override
	public WeighedSoundEvents getOrResolve(SoundManager manager) {
		if (soundEvent == null) {
			soundEvent = new WeighedSoundEvents(identifier, null);
			sound = new Sound(identifier, ConstantFloat.of(1), ConstantFloat.of(1), 1, Sound.Type.FILE, false, false, 16);
			soundEvent.addSound(sound);
		}
		return soundEvent;
	}
}
