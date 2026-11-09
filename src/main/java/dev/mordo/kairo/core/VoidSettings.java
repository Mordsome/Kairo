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

	// ---- 0.3.0: intro, sky, you, combat, world ----

	/** Colours the new effects can use. */
	public enum FxColor implements EnumSetting.Labeled {
		ACCENT("Your colour"),
		RAINBOW("Rainbow"),
		DUO("Colour + white"),
		CUSTOM("Custom");

		private final String label;

		FxColor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** The Visuals cards in Favourites, by name, separated by "|". */
	public final dev.mordo.kairo.core.settings.TextSetting favourites = add(new dev.mordo.kairo.core.settings.TextSetting("favourites",
		"Favourites", String.join("|", "Attack animation", "Mining animation", "Held item glow", "Energy blades", "Aura", "Line glyphs",
		"Light ribbons", "Floating motes", "Aurora", "Cosmic sky", "Block overlay", "Light particles", "Hit impact", "Damage markers",
		"Cinematic kill", "Player trail"), 2000).hidden());

	public final BooleanSetting intro = add(new BooleanSetting("intro", "Start-up intro", true)
		.description("When the game opens: Kairo appears, then Void pops in. Once per launch, never when you leave a world. Click to skip"));

	public enum AuroraStyle implements EnumSetting.Labeled {
		CURTAINS("Curtains"),
		RIBBONS("Living ribbons");

		private final String label;

		AuroraStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum AuroraColor implements EnumSetting.Labeled {
		CLASSIC("Green and violet"),
		ACCENT("Your colour"),
		RAINBOW("Rainbow"),
		SUNSET("Sunset");

		private final String label;

		AuroraColor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<AuroraStyle> auroraStyle = add(new EnumSetting<>("aurora_style", "Aurora style", AuroraStyle.RIBBONS)
		.description("Curtains hang in the north. Living ribbons twist and flow across the whole sky"));
	public final EnumSetting<AuroraColor> auroraColor = add(new EnumSetting<>("aurora_color", "Aurora colour", AuroraColor.CLASSIC));
	public final SliderSetting auroraStrength = add(new SliderSetting("aurora_strength", "Aurora brightness", 1.0, 0.3, 2.0, 0.05).percent());

	/** "image:&lt;file&gt;" from config/kairo-void/skies, or empty for none. */
	public final dev.mordo.kairo.core.settings.TextSetting skyImage = add(new dev.mordo.kairo.core.settings.TextSetting("sky_image",
		"Sky image", "", 200).hidden());
	public final BooleanSetting customSky = add(new BooleanSetting("custom_sky", "Custom sky", false)
		.description("Your own picture as the sky. A wide 2:1 panorama wraps all the way round; any other picture works too"));

	public enum SkyTime implements EnumSetting.Labeled {
		ALWAYS("Day and night"),
		NIGHT("Only at night"),
		DAY("Only by day");

		private final String label;

		SkyTime(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<SkyTime> skyTime = add(new EnumSetting<>("sky_time", "Show it", SkyTime.ALWAYS));
	public final SliderSetting skyBrightness = add(new SliderSetting("sky_brightness", "Sky brightness", 1.0, 0.2, 1.5, 0.05).percent()
		.description("Follows day and night: darker at night either way"));
	public final SliderSetting skySpin = add(new SliderSetting("sky_spin", "Sky turning", 0.2, 0.0, 2.0, 0.05).multiplier()
		.description("How fast the picture slowly turns round you (0 = still)"));
	public final BooleanSetting skyStars = add(new BooleanSetting("sky_stars", "Keep the sun, moon and stars", true)
		.description("Draws the sun, the moon, the cosmic sky and the aurora on top of your picture"));

	public enum AuraStyle implements EnumSetting.Labeled {
		NONE("None"),
		CUBES("Orbiting cubes"),
		RIBBONS("Light ribbons"),
		SHARDS("Prism shards"),
		HALO("Halo rings");

		private final String label;

		AuraStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<AuraStyle> aura = add(new EnumSetting<>("aura", "Aura", AuraStyle.NONE)
		.description("Shapes of light floating around you. Only on your screen"));
	public final EnumSetting<FxColor> auraColor = add(new EnumSetting<>("aura_color", "Aura colour", FxColor.ACCENT));
	public final SliderSetting auraSize = add(new SliderSetting("aura_size", "Aura size", 1.0, 0.6, 1.6, 0.05).multiplier());
	public final SliderSetting auraSpeed = add(new SliderSetting("aura_speed", "Aura speed", 1.0, 0.3, 2.5, 0.05).multiplier());
	public final BooleanSetting glyphs = add(new BooleanSetting("line_glyphs", "Line glyphs", true)
		.description("Animated angular lines turning around you: rings of ticks, brackets and runes"));
	public final EnumSetting<FxColor> glyphColor = add(new EnumSetting<>("glyph_color", "Glyph colour", FxColor.DUO));
	public final BooleanSetting youInFirstPerson = add(new BooleanSetting("aura_first_person", "Also in first person", true)
		.description("Your aura and glyphs while you look through your own eyes too, spread wider so they never sit in your face"));

	// The world around you (first person too).
	public final BooleanSetting worldRibbons = add(new BooleanSetting("world_ribbons", "Light ribbons", true)
		.description("Long ribbons of light drifting through the air around you, like an aurora you can walk through"));
	public final EnumSetting<AuroraColor> ribbonColor = add(new EnumSetting<>("world_ribbon_color", "Ribbon colour", AuroraColor.CLASSIC));
	public final SliderSetting ribbonAmount = add(new SliderSetting("world_ribbon_amount", "How many ribbons", 0.6, 0.2, 1.0, 0.05).percent());
	public final BooleanSetting ribbonsByDay = add(new BooleanSetting("world_ribbons_day", "Also by day", true)
		.description("Fainter in daylight, brightest at night"));
	public final BooleanSetting motes = add(new BooleanSetting("world_motes", "Floating motes", true)
		.description("Small lights hanging in the air around you that drift, glow and fade"));
	public final SliderSetting moteAmount = add(new SliderSetting("world_mote_amount", "How many motes", 0.6, 0.2, 1.0, 0.05).percent());
	public final BooleanSetting lightParticles = add(new BooleanSetting("light_particles", "Light particles", true)
		.description("Kairo's effects and Minecraft's combat, death, potion and explosion particles become sparks, shards and rings of light instead of pixel sprites"));

	public enum TrailStyle implements EnumSetting.Labeled {
		NONE("None"),
		RIBBON("Light ribbon"),
		COMET("Comet"),
		TWIN("Twin streaks"),
		PRISM("Prism");

		private final String label;

		TrailStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<TrailStyle> playerTrail = add(new EnumSetting<>("player_trail", "Player trail", TrailStyle.NONE)
		.description("A fading trail of light behind you as you move"));
	public final EnumSetting<FxColor> trailColor = add(new EnumSetting<>("trail_color", "Trail colour", FxColor.ACCENT));
	public final SliderSetting trailLength = add(new SliderSetting("trail_length", "Trail length", 0.8, 0.3, 2.0, 0.05).unit(" s"));

	public enum SelfLook implements EnumSetting.Labeled {
		NONE("Normal"),
		OUTLINE("Outline"),
		GLOW("Glow"),
		HOLOGRAM("Hologram");

		private final String label;

		SelfLook(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<SelfLook> selfLook = add(new EnumSetting<>("self_look", "Your look", SelfLook.NONE)
		.description("An outline, a glow or a hologram look on your own player (F5, the inventory, the menu). Never on other players"));
	public final dev.mordo.kairo.core.settings.ColorSetting fxCustomColor = add(
		new dev.mordo.kairo.core.settings.ColorSetting("fx_custom_color", "Custom colour", 0xFF59F0FF, false)
			.description("Used by everything set to Custom"));

	public enum HitImpact implements EnumSetting.Labeled {
		NONE("None"),
		SHARDS("Shards"),
		SIGIL("Sigil"),
		CROSS("Cross slash"),
		STAR("Star burst"),
		RINGS("Ring burst");

		private final String label;

		HitImpact(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<HitImpact> hitImpact = add(new EnumSetting<>("hit_impact", "Hit impact", HitImpact.SHARDS)
		.description("Light drawn where your hit lands: shards, a sigil, a cross slash, a star or rings. Bigger on crits"));
	public final EnumSetting<FxColor> hitImpactColor = add(new EnumSetting<>("hit_impact_color", "Impact colour", FxColor.DUO));
	public final BooleanSetting kineticNumbers = add(new BooleanSetting("kinetic_numbers", "Animated damage markers", true)
		.description("Damage numbers that punch in, slash, stack into combos and break apart (turn on Damage numbers too)"));

	public enum NoirStyle implements EnumSetting.Labeled {
		WHITE("White world, black lines"),
		INK("Black world, white lines"),
		DUOTONE("Your colour");

		private final String label;

		NoirStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final BooleanSetting noirKill = add(new BooleanSetting("noir_kill", "Cinematic kill", false)
		.description("When you get a kill, a white drawing with black outlines spreads out from it across the world, then draws back in"));
	public enum NoirSpeed implements EnumSetting.Labeled {
		SMOOTH("Slow and smooth"),
		SHARP("Sharp and quick");

		private final String label;

		NoirSpeed(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<NoirSpeed> noirSpeed = add(new EnumSetting<>("noir_speed", "Cinematic speed", NoirSpeed.SMOOTH)
		.description("How fast the drawing spreads out from the kill and draws back in. Fewer flashes always keeps it smooth"));
	public final EnumSetting<NoirStyle> noirStyle = add(new EnumSetting<>("noir_style", "Cinematic style", NoirStyle.WHITE));
	public final SliderSetting noirLength = add(new SliderSetting("noir_length", "Cinematic length", 1.2, 0.5, 3.0, 0.1).unit(" s"));

	public enum BlockOverlay implements EnumSetting.Labeled {
		NONE("None"),
		BRACKETS("Brackets"),
		SCAN("Scan"),
		HOLOGRAM("Hologram grid"),
		PULSE("Pulse frame");

		private final String label;

		BlockOverlay(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<BlockOverlay> blockOverlay = add(new EnumSetting<>("block_overlay", "Block overlay", BlockOverlay.BRACKETS)
		.description("An animated overlay on the block you look at, in place of the outline"));
	public final EnumSetting<FxColor> blockOverlayColor = add(new EnumSetting<>("block_overlay_color", "Overlay colour", FxColor.ACCENT));
	public final BooleanSetting overlayGlide = add(new BooleanSetting("block_overlay_glide", "Glide between blocks", true)
		.description("The overlay slides and reshapes smoothly from one block to the next, and fades in and out"));

	public enum NametagStyle implements EnumSetting.Labeled {
		VANILLA("Vanilla"),
		GLASS("Glass"),
		NEON("Neon"),
		HOLOGRAM("Hologram"),
		SHIMMER("Shimmer");

		private final String label;

		NametagStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<NametagStyle> nametags = add(new EnumSetting<>("nametags", "Nametags", NametagStyle.GLASS)
		.description("Players' nametags with a style. They show exactly when Minecraft would show them, nothing more"));
	public final BooleanSetting ownNametag = add(new BooleanSetting("own_nametag", "Your own nametag in F5", false));

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
