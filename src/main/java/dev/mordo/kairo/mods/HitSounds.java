package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ChoiceSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.sound.SoundChoices;
import dev.mordo.kairo.sound.SoundLibrary;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Swap the damage sounds for something clearer: a beep, a ding, or your own file. "Hurt" is the sound a player
 * makes when taking damage; "Hit" is the swing/attack sound of your own hits. Audio only.
 */
public final class HitSounds extends Module {
	public static final Identifier HURT_SOUND = Identifier.withDefaultNamespace("entity.player.hurt");
	public static final Identifier HIT_SOUND = Identifier.withDefaultNamespace("entity.player.attack.strong");

	public enum Who implements EnumSetting.Labeled {
		EVERYONE("Everyone"),
		ME("Only me"),
		OTHERS("Only other players");

		private final String label;

		Who(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** A matching set: your hits, your critical hits and your kills. */
	public enum Pack implements EnumSetting.Labeled {
		NONE("None"),
		ARCADE("Arcade"),
		CRYSTAL("Crystal"),
		HEAVY("Heavy"),
		VOID("Void"),
		BUBBLE("Bubble");

		private final String label;

		Pack(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		String key(String what) {
			return name().toLowerCase(java.util.Locale.ROOT) + "_" + what;
		}
	}

	public final EnumSetting<Pack> pack = add(new EnumSetting<>("sound_pack", "Sound pack", Pack.NONE)
		.<EnumSetting<Pack>>description("A matching hit, critical hit and kill sound (replaces the hit sound below)")
		.onChange(p -> previewPack()));
	public final SliderSetting packVolume = add(new SliderSetting("pack_volume", "Pack volume", 0.8, 0.05, 1.0, 0.05).percent()
		.visibleWhen(() -> pack.get() != Pack.NONE));

	public final ChoiceSetting hurtSound = add(SoundChoices.setting("hurt_sound", "Hurt sound",
		"When a player takes damage", HURT_SOUND, () -> this.hurtVolume.value()));
	public final EnumSetting<Who> hurtFor = add(new EnumSetting<>("hurt_for", "Hurt sound for", Who.EVERYONE)
		.visibleWhen(() -> !hurtSound.get().equals(SoundLibrary.VANILLA)));
	public final SliderSetting hurtVolume = add(new SliderSetting("hurt_volume", "Hurt volume", 1.0, 0.05, 1.0, 0.05).percent()
		.visibleWhen(() -> !hurtSound.get().equals(SoundLibrary.VANILLA)));
	public final ChoiceSetting hitSound = add(SoundChoices.setting("hit_sound", "Hit sound",
		"Your own attacks (strong, weak, crit, sweep)", HIT_SOUND, () -> this.hitVolume.value()));
	public final SliderSetting hitVolume = add(new SliderSetting("hit_volume", "Hit volume", 1.0, 0.05, 1.0, 0.05).percent()
		.visibleWhen(() -> !hitSound.get().equals(SoundLibrary.VANILLA)));
	public final BooleanSetting pitchVariation = add(new BooleanSetting("pitch_variation", "Keep pitch variation", false)
		.description("Vanilla plays damage sounds at slightly random pitches"));

	public HitSounds() {
		super("hit_sounds", "Hit Sounds", "Beeps or your own damage sounds", Category.PVP, Icon.VOLUME, true);
	}

	public @Nullable String hurtKey(boolean mine) {
		if (!isEnabled()) {
			return null;
		}
		Who who = hurtFor.get();
		if ((who == Who.ME && !mine) || (who == Who.OTHERS && mine)) {
			return null;
		}
		return SoundLibrary.resolve(hurtSound.get());
	}

	public @Nullable String hitKey() {
		return isEnabled() ? SoundLibrary.resolve(hitSound.get()) : null;
	}

	/** The pack's sound for one of your attacks (crit or not), or null without a pack. */
	public @Nullable String packHitKey(boolean crit) {
		if (!isEnabled() || pack.get() == Pack.NONE) {
			return null;
		}
		return SoundLibrary.resolve("builtin:" + pack.get().key(crit ? "crit" : "hit"));
	}

	/** The chosen pack's kill sound, or null without a pack. */
	public static @Nullable String packKillKey() {
		HitSounds hits = dev.mordo.kairo.core.ModuleManager.get(HitSounds.class);
		if (hits == null || !hits.isEnabled() || hits.pack.get() == Pack.NONE) {
			return null;
		}
		return SoundLibrary.resolve("builtin:" + hits.pack.get().key("kill"));
	}

	public static float packVolume() {
		return dev.mordo.kairo.core.ModuleManager.get(HitSounds.class).packVolume.floatValue();
	}

	private void previewPack() {
		if (pack.get() != Pack.NONE && net.minecraft.client.Minecraft.getInstance().getSoundManager() != null) {
			SoundLibrary.preview("builtin:" + pack.get().key("kill"), null, packVolume.floatValue());
		}
	}
}
