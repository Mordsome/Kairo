package dev.mordo.kairo.core;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;

/**
 * In-game looks, edited in the Visuals panel: hand animations, screen effects and hit effects. Everything here
 * only changes how things are drawn on your screen. Saved in the profile under "game_visuals".
 */
public final class GameVisuals extends SettingHolder {
	public static final GameVisuals INSTANCE = new GameVisuals();

	/** How the held item moves when you attack. */
	public enum SwingStyle implements EnumSetting.Labeled {
		VANILLA("Vanilla"),
		SMOOTH("Smooth"),
		SLASH("Slash"),
		CHOP("Chop"),
		STAB("Stab"),
		SPIN("Spin"),
		FLIP("Flip"),
		SWIRL("Swirl"),
		PUNCH("Punch"),
		SWEEP("Sweep");

		private final String label;

		SwingStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** How the held tool moves while you hold attack on a block. */
	public enum MiningStyle implements EnumSetting.Labeled {
		VANILLA("Vanilla"),
		SIDEWAYS("Sideways"),
		SMOOTH("Smooth"),
		HAMMER("Hammer"),
		DRILL("Drill"),
		CIRCLE("Circle"),
		SAW("Saw");

		private final String label;

		MiningStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum Filter implements EnumSetting.Labeled {
		NONE("None"),
		CINEMATIC("Cinematic"),
		WARM("Warm"),
		COLD("Cold"),
		VIBRANT("Vibrant"),
		DREAMY("Dreamy"),
		NOIR("Noir"),
		RETRO("Retro"),
		NEON("Neon"),
		VAPORWAVE("Vaporwave"),
		SUNSET("Sunset"),
		MATRIX("Matrix");

		private final String label;

		Filter(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum HitBurst implements EnumSetting.Labeled {
		NONE("None"),
		SPARKS("Sparks"),
		CRITS("Crits"),
		MAGIC("Magic"),
		HEARTS("Hearts"),
		STARS("Stars"),
		FLAMES("Flames"),
		SOUL("Soul fire"),
		BLOOD("Blood");

		private final String label;

		HitBurst(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum KillEffect implements EnumSetting.Labeled {
		NONE("None"),
		LIGHTNING("Lightning"),
		FIREWORK("Firework"),
		SOUL("Soul rising"),
		EXPLOSION("Poof"),
		TOTEM("Totem burst"),
		HEARTS("Hearts"),
		BLOOD("Blood splash"),
		VORTEX("Vortex"),
		SHATTER("Shatter"),
		IMPLODE("Void implosion"),
		ASCEND("Ascend");

		private final String label;

		KillEffect(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** Extra look for the item in your hand. */
	public enum HeldShine implements EnumSetting.Labeled {
		NONE("None"),
		SHIMMER("Shimmer"),
		OUTLINE("Outline"),
		BOTH("Shimmer + outline");

		private final String label;

		HeldShine(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** Colour of the enchantment shimmer (on every enchanted item, and the held-item shimmer). */
	public enum GlintColor implements EnumSetting.Labeled {
		VANILLA("Vanilla"),
		ACCENT("Your colour"),
		RAINBOW("Rainbow"),
		CUSTOM("Custom");

		private final String label;

		GlintColor(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	/** Weapon inspect (like in shooters): what the item does when you press the inspect key. */
	public enum InspectStyle implements EnumSetting.Labeled {
		LOOK_OVER("Look over"),
		TWIRL("Twirl"),
		TOSS("Toss & catch"),
		FLOURISH("Flourish");

		private final String label;

		InspectStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum ProjectileTrail implements EnumSetting.Labeled {
		NONE("None"),
		ACCENT("Your colour"),
		RAINBOW("Rainbow"),
		FLAME("Flame"),
		MAGIC("Magic"),
		CLOUD("Cloud"),
		SPARKLE("Sparkle");

		private final String label;

		ProjectileTrail(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum JumpEffect implements EnumSetting.Labeled {
		NONE("None"),
		DUST("Dust puff"),
		RING("Shockwave"),
		SPARKLES("Sparkles"),
		FLAMES("Flames"),
		HEARTS("Hearts"),
		CLOUD("Cloud"),
		SOUL("Soul");

		private final String label;

		JumpEffect(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum ExplosionStyle implements EnumSetting.Labeled {
		VANILLA("Vanilla"),
		SHOCKWAVE("Shockwave"),
		INFERNO("Inferno"),
		FIREWORK("Firework"),
		MAGIC("Magic"),
		VOID("Void");

		private final String label;

		ExplosionStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	// ---- hand animations ----

	public final EnumSetting<SwingStyle> swingStyle = add(new EnumSetting<>("swing_style", "Attack animation", SwingStyle.VANILLA)
		.description("How your weapon moves when you hit"));
	public final SliderSetting swingDuration = add(new SliderSetting("swing_duration", "Attack animation length", 1.0, 0.5, 3.0, 0.05).multiplier()
		.description("Above 1x plays it slower (looks only; hits and cooldowns don't change)"));
	public final BooleanSetting swingTrail = add(new BooleanSetting("swing_trail", "Swing trail", false)
		.description("A glowing streak behind the blade"));
	public final BooleanSetting swingTrailRainbow = add(new BooleanSetting("swing_trail_rainbow", "Rainbow trail", false));
	public final EnumSetting<MiningStyle> miningStyle = add(new EnumSetting<>("mining_style", "Mining animation", MiningStyle.VANILLA)
		.description("How your tool moves while you break blocks"));
	public final SliderSetting miningDuration = add(new SliderSetting("mining_duration", "Mining swing length", 0.8, 0.3, 2.0, 0.05).unit("s")
		.description("Seconds per swing (looks only; mining speed doesn't change)"));

	// ---- inspect ----

	public final dev.mordo.kairo.core.settings.KeybindSetting inspectKey = add(new dev.mordo.kairo.core.settings.KeybindSetting("inspect_key",
		"Inspect key", dev.mordo.kairo.core.keybinds.Keybind.keyboard(com.mojang.blaze3d.platform.InputConstants.KEY_V))
		.onPress(dev.mordo.kairo.render.HandAnimations::inspect)
		.<dev.mordo.kairo.core.settings.KeybindSetting>description("Show off the item in your hand; attacking or using it stops"));
	public final EnumSetting<InspectStyle> inspectStyle = add(new EnumSetting<>("inspect_style", "Inspect animation", InspectStyle.LOOK_OVER));

	// ---- held item ----

	public final EnumSetting<HeldShine> heldShine = add(new EnumSetting<>("held_shine", "Held item look", HeldShine.NONE)
		.description("Shimmer: an enchantment-style sheen; Outline: a glowing edge in the shimmer colour"));
	public final EnumSetting<GlintColor> glintColor = add(new EnumSetting<>("glint_color", "Shimmer colour", GlintColor.VANILLA)
		.description("Also the outline colour, and the shine on enchanted items"));
	public final dev.mordo.kairo.core.settings.ColorSetting glintCustom = add(new dev.mordo.kairo.core.settings.ColorSetting("glint_custom",
		"Custom shimmer colour", 0xFFFF6BD6, false).<dev.mordo.kairo.core.settings.ColorSetting>visibleWhen(() -> glintColor.get() == GlintColor.CUSTOM));

	// ---- screen effects ----

	public final EnumSetting<Filter> filter = add(new EnumSetting<>("filter", "Colour filter", Filter.NONE));
	public final SliderSetting filterStrength = add(new SliderSetting("filter_strength", "Filter strength", 0.85, 0.0, 1.0, 0.05).percent());
	public final SliderSetting saturation = add(new SliderSetting("saturation", "Saturation", 1.0, 0.0, 2.0, 0.05).multiplier());
	public final SliderSetting contrast = add(new SliderSetting("contrast", "Contrast", 1.0, 0.5, 1.6, 0.05).multiplier());
	public final SliderSetting glow = add(new SliderSetting("glow", "Glow", 0.0, 0.0, 1.0, 0.05).percent()
		.description("Bright things bleed soft light"));
	public final SliderSetting vignette = add(new SliderSetting("vignette", "Vignette", 0.0, 0.0, 1.0, 0.05).percent());
	public final BooleanSetting accentVignette = add(new BooleanSetting("accent_vignette", "Vignette in your colour", false));
	public final SliderSetting chromatic = add(new SliderSetting("chromatic", "Chromatic aberration", 0.0, 0.0, 1.0, 0.05).percent()
		.description("Colour fringes towards the edges"));
	public final SliderSetting grain = add(new SliderSetting("grain", "Film grain", 0.0, 0.0, 1.0, 0.05).percent());
	public final SliderSetting letterbox = add(new SliderSetting("letterbox", "Cinema bars", 0.0, 0.0, 1.0, 0.05).percent());
	public final SliderSetting motionBlur = add(new SliderSetting("motion_blur", "Motion blur", 0.0, 0.0, 1.0, 0.05).percent());
	public final BooleanSetting motionBlurTurning = add(new BooleanSetting("motion_blur_turning", "Blur only when turning", true)
		.description("The smear follows how fast you turn; standing still stays sharp"));
	public final BooleanSetting lowHealthPulse = add(new BooleanSetting("low_health_pulse", "Low health pulse", false)
		.description("The screen edges pulse red under 4 hearts"));

	// ---- hit effects ----

	public final EnumSetting<HitBurst> hitBurst = add(new EnumSetting<>("hit_burst", "Hit particles", HitBurst.NONE)
		.description("Extra particles on what you hit"));
	public final SliderSetting hitAmount = add(new SliderSetting("hit_amount", "Hit particle amount", 1.0, 0.25, 3.0, 0.25).multiplier());
	public final EnumSetting<KillEffect> killEffect = add(new EnumSetting<>("kill_effect", "Kill effect", KillEffect.NONE)
		.description("Plays where something you hit dies"));
	public final BooleanSetting killSound = add(new BooleanSetting("kill_sound", "Kill effect sound", true));
	public final BooleanSetting hitFlash = add(new BooleanSetting("hit_flash", "Hit flash", false)
		.description("The screen edges flash in your colour when you land a hit"));
	public final BooleanSetting damageNumbers = add(new BooleanSetting("damage_numbers", "Damage numbers", false)
		.description("Floating numbers above what you hit"));

	// ---- movement and world ----

	public final EnumSetting<JumpEffect> jumpEffect = add(new EnumSetting<>("jump_effect", "Jump effect", JumpEffect.NONE));
	public final BooleanSetting landingEffect = add(new BooleanSetting("landing_effect", "Landing effect", true)
		.description("The jump effect again, bigger, when you land from a height"));
	public final SliderSetting speedLines = add(new SliderSetting("speed_lines", "Speed lines", 0.0, 0.0, 1.0, 0.05).percent()
		.description("Streaks at the screen edges while sprinting, flying or falling fast"));
	public final EnumSetting<ExplosionStyle> explosionStyle = add(new EnumSetting<>("explosion_style", "Explosion style", ExplosionStyle.VANILLA)
		.description("Extra particles on top of vanilla's (PvP mode's calm explosions still apply)"));
	public final SliderSetting explosionShake = add(new SliderSetting("explosion_shake", "Explosion camera shake", 0.0, 0.0, 1.0, 0.05).percent()
		.description("Only your view shakes; your aim doesn't move"));
	public final SliderSetting explosionFlash = add(new SliderSetting("explosion_flash", "Explosion flash", 0.0, 0.0, 1.0, 0.05).percent());

	// ---- projectiles and dropped items ----

	public final EnumSetting<ProjectileTrail> projectileTrail = add(new EnumSetting<>("projectile_trail", "Projectile trails", ProjectileTrail.NONE)
		.description("A trail behind arrows, tridents, ender pearls, snowballs and potions"));
	public final BooleanSetting trailOnlyMine = add(new BooleanSetting("trail_only_mine", "Only my projectiles", false));
	public final BooleanSetting pearlRipple = add(new BooleanSetting("pearl_ripple", "Pearl landing ring", true)
		.description("A ring of light where an ender pearl lands"));
	public final BooleanSetting itemPhysics = add(new BooleanSetting("item_physics", "Item physics", false)
		.description("Dropped items lie flat on the ground and tumble as they fall, instead of floating and spinning"));
	public final BooleanSetting rareItemGlow = add(new BooleanSetting("rare_item_glow", "Rare item glow", false)
		.description("A soft glow under dropped rare items, in their rarity colour"));

	private GameVisuals() {
		super("game_visuals");
	}

	/** True when any hand animation differs from vanilla. */
	public boolean animatesHands() {
		if (!dev.mordo.kairo.render.Visuals.live()) {
			return false;
		}
		return swingStyle.get() != SwingStyle.VANILLA || miningStyle.get() != MiningStyle.VANILLA
			|| Math.abs(swingDuration.floatValue() - 1f) > 0.01f || swingTrail.isOn() || dev.mordo.kairo.render.HandAnimations.inspecting()
			|| dev.mordo.kairo.render.VoidFx.energyBlades();
	}

	/** True when any screen effect is on. */
	public boolean screenEffects() {
		return filter.get() != Filter.NONE || Math.abs(saturation.floatValue() - 1f) > 0.01f || Math.abs(contrast.floatValue() - 1f) > 0.01f
			|| glow.floatValue() > 0 || vignette.floatValue() > 0 || chromatic.floatValue() > 0 || grain.floatValue() > 0
			|| letterbox.floatValue() > 0 || lowHealthPulse.isOn() || speedLines.floatValue() > 0 || explosionFlash.floatValue() > 0 || hitFlash.isOn();
	}
}
