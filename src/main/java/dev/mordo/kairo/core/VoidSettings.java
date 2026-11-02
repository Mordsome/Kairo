package dev.mordo.kairo.core;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.core.settings.SliderSetting;

import java.util.List;

/**
 * Kairo Void's own effects: combat (energy slashes, impact shockwaves, hit-stop, totem blast), the sky (cosmic
 * sky, aurora, sunsets, depth fog) and a quality preset that sets them all at once. Every one can still be switched
 * on its own. All of it only changes what's drawn on your screen. Saved under "void_visuals".
 */
public final class VoidSettings extends SettingHolder {
	public static final VoidSettings INSTANCE = new VoidSettings();

	public enum Quality implements EnumSetting.Labeled {
		LOW("Low"),
		MEDIUM("Medium"),
		HIGH("High"),
		INSANE("Insane"),
		CUSTOM("Custom");

		private final String label;

		Quality(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Quality> quality = add(new EnumSetting<>("quality", "Quality", Quality.HIGH)
		.description("Sets every effect below at once. Changing one yourself switches this to Custom"));

	// ---- combat ----
	public final BooleanSetting slashes = add(new BooleanSetting("energy_slashes", "Energy blades", true)
		.description("Your weapon leaves a blade of light when you swing; brighter and longer on critical hits"));
	public final SliderSetting slashSize = add(new SliderSetting("slash_size", "Blade reach", 1.0, 0.5, 1.5, 0.05).multiplier());
	public final SliderSetting slashGlow = add(new SliderSetting("slash_glow", "Blade brightness", 1.0, 0.2, 1.5, 0.05).percent());
	public final BooleanSetting shockwaves = add(new BooleanSetting("impact_shockwaves", "Impact shockwaves", true)
		.description("A ripple bends the screen where your hit lands"));
	public final BooleanSetting targetFlash = add(new BooleanSetting("target_flash", "White flash on hit", true)
		.description("What you hit flashes white for a moment"));
	public final BooleanSetting hitStop = add(new BooleanSetting("hit_stop", "Hit-stop", true)
		.description("The picture holds for a split second on strong hits (only the picture: the game keeps running)"));
	public final SliderSetting hitStopLength = add(new SliderSetting("hit_stop_length", "Hit-stop length", 55, 20, 120, 5).unit("ms"));
	public final BooleanSetting totemBlast = add(new BooleanSetting("totem_blast", "Totem pop blast", true)
		.description("Golden rays and a slow ripple when a totem saves you"));

	// ---- sky ----
	public final BooleanSetting cosmicSky = add(new BooleanSetting("cosmic_sky", "Cosmic sky", true)
		.description("At night: nebula clouds, a turning galaxy, a ringed planet and shooting stars"));
	public final BooleanSetting planet = add(new BooleanSetting("planet", "Ringed planet", true));
	public final BooleanSetting shootingStars = add(new BooleanSetting("shooting_stars", "Shooting stars", true));
	public final BooleanSetting aurora = add(new BooleanSetting("aurora", "Aurora", true)
		.description("Curtains of light over the night sky"));
	public final BooleanSetting sunsets = add(new BooleanSetting("sunsets", "Rich sunsets", true)
		.description("Deeper, warmer colours at sunrise and sunset"));
	public final SliderSetting depthFog = add(new SliderSetting("depth_fog", "Depth fog", 0.5, 0.0, 1.0, 0.05).percent()
		.description("Distant land fades into a soft, sky-coloured haze"));

	// ---- atmospheres ----

	/** A whole new look for the world: sky, fog, light, colour and weather particles. */
	public enum Atmosphere implements EnumSetting.Labeled {
		NONE("None"),
		BLIZZARD("Blizzard"),
		GALAXY("Galaxy"),
		BLOOD_MOON("Blood moon"),
		INFERNO("Inferno"),
		SAKURA("Sakura dream"),
		TOXIC("Toxic"),
		ABYSS("Abyss"),
		SYNTHWAVE("Synthwave"),
		STORM("Storm");

		private final String label;

		Atmosphere(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Atmosphere> atmosphere = add(new EnumSetting<>("atmosphere", "Atmosphere", Atmosphere.NONE).hidden());
	public final SliderSetting atmosphereStrength = add(new SliderSetting("atmosphere_strength", "Atmosphere strength", 1.0, 0.2, 1.0, 0.05).percent()
		.description("How far the atmosphere takes over from the normal world"));
	public final BooleanSetting atmosphereParticles = add(new BooleanSetting("atmosphere_particles", "Weather particles", true)
		.description("Snow, ash, petals, spores or embers drifting around you"));
	public final BooleanSetting snowGround = add(new BooleanSetting("snow_ground", "Snow on the ground", true)
		.description("Blizzard covers the tops of blocks in snow (only the picture)"));

	// ---- more combat and world ----
	public final BooleanSetting hitColor = add(new BooleanSetting("hit_color", "Hit colour", false)
		.description("The red flash when something is hurt takes your colour instead"));
	public final dev.mordo.kairo.core.settings.ColorSetting hitColorValue = add(
		new dev.mordo.kairo.core.settings.ColorSetting("hit_color_value", "Hit colour", 0xFFFF3355, false).followsAccent());
	public final BooleanSetting fireflies = add(new BooleanSetting("fireflies", "Fireflies", false)
		.description("Little lights drift over grass and flowers at night"));
	public final SliderSetting particleBoost = add(new SliderSetting("particle_boost", "Crit particles", 1.0, 1.0, 4.0, 0.5).multiplier()
		.description("More crit and magic sparks when you hit"));

	// ---- end crystals ----

	public enum CrystalStyle implements EnumSetting.Labeled {
		VANILLA("Vanilla"),
		PRISM("Prism"),
		VOID("Void core"),
		PLASMA("Plasma"),
		HOLO("Hologram");

		private final String label;

		CrystalStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<CrystalStyle> crystalStyle = add(new EnumSetting<>("crystal_style", "Crystal style", CrystalStyle.PRISM)
		.description("Prism: glowing glass and orbiting rings. Void core: a dark heart with a bright rim. Plasma: crackling energy. Hologram: flickering light"));
	public final SliderSetting crystalGlow = add(new SliderSetting("crystal_glow", "Crystal glow", 1.0, 0.2, 1.5, 0.05).percent());
	public final BooleanSetting crystalPop = add(new BooleanSetting("crystal_pop", "Crystal pop", true)
		.description("A burst of light, a ring and a ripple when a crystal explodes"));

	// ---- the master switch ----

	public final BooleanSetting master = add(new BooleanSetting("visuals_on", "Visuals", true)
		.description("Turns every Kairo Void visual on or off at once (your choices are kept)").hidden());

	// ---- interface ----

	public enum HudStyle implements EnumSetting.Labeled {
		CLASSIC("Classic"),
		GLOW("Glow"),
		NEON("Neon"),
		PULSE("Pulse");

		private final String label;

		HudStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<HudStyle> hudStyle = add(new EnumSetting<>("hud_style", "HUD style", HudStyle.GLOW)
		.description("Glow: a soft light in your colour. Neon: bright edges. Pulse: a light that runs round each box"));
	public final BooleanSetting keyRipples = add(new BooleanSetting("key_ripples", "Keystroke ripples", true)
		.description("Keys pop and send out a ripple when you press them"));

	/** The effects a preset sets, in order: see {@link #applyPreset}. */
	private List<Setting<?>> presetControlled() {
		return List.of(slashes, shockwaves, targetFlash, hitStop, totemBlast, cosmicSky, planet, shootingStars, aurora, sunsets, depthFog,
			slashGlow);
	}

	private boolean applying;

	private VoidSettings() {
		super("void_visuals");
		quality.onChange(q -> applyPreset(q));
		for (Setting<?> s : presetControlled()) {
			s.onChange(value -> {
				if (!applying && quality.get() != Quality.CUSTOM && !matchesPreset(quality.get())) {
					applying = true;
					quality.set(Quality.CUSTOM);
					applying = false;
				}
			});
		}
	}

	private void applyPreset(Quality q) {
		if (applying || q == Quality.CUSTOM) {
			return;
		}
		applying = true;
		boolean medium = q.ordinal() >= Quality.MEDIUM.ordinal();
		boolean high = q.ordinal() >= Quality.HIGH.ordinal();
		boolean insane = q == Quality.INSANE;
		slashes.set(true);
		shockwaves.set(medium);
		targetFlash.set(true);
		hitStop.set(medium);
		totemBlast.set(true);
		cosmicSky.set(medium);
		planet.set(high);
		shootingStars.set(high);
		aurora.set(high);
		sunsets.set(true);
		depthFog.set(q == Quality.LOW ? 0.0 : insane ? 0.75 : high ? 0.5 : 0.3);
		slashGlow.set(insane ? 1.3 : q == Quality.LOW ? 0.7 : 1.0);
		applying = false;
	}

	private boolean matchesPreset(Quality q) {
		boolean medium = q.ordinal() >= Quality.MEDIUM.ordinal();
		boolean high = q.ordinal() >= Quality.HIGH.ordinal();
		boolean insane = q == Quality.INSANE;
		return slashes.isOn() && shockwaves.isOn() == medium && targetFlash.isOn() && hitStop.isOn() == medium
			&& totemBlast.isOn() && cosmicSky.isOn() == medium && planet.isOn() == high && shootingStars.isOn() == high && aurora.isOn() == high
			&& sunsets.isOn()
			&& Math.abs(depthFog.floatValue() - (q == Quality.LOW ? 0.0f : insane ? 0.75f : high ? 0.5f : 0.3f)) < 0.01f
			&& Math.abs(slashGlow.floatValue() - (insane ? 1.3f : q == Quality.LOW ? 0.7f : 1.0f)) < 0.01f;
	}

	/** Insane quality: extra layers (more nebula detail, more stars, stronger glow). */
	public boolean insane() {
		return quality.get() == Quality.INSANE;
	}
}
