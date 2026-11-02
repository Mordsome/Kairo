package dev.mordo.kairo.core;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;

/** Client-wide look and feel (the Visuals section of the ring). Saved in the profile under "client". */
public final class ClientSettings extends SettingHolder {
	public static final ClientSettings INSTANCE = new ClientSettings();

	public final ColorSetting accent = add(new ColorSetting("accent", "Accent color", Edition.isVoid() ? 0xFF8B5CF6 : 0xFF7FD8FF, false));
	public final ColorSetting pvpAccent = add(new ColorSetting("pvp_accent", "PvP accent color", 0xFFFF9A52, false));
	public final BooleanSetting accentEverywhere = add(new BooleanSetting("accent_everywhere", "Accent on every mod", false)
		.description("Every mod colour (crosshair, outlines, keystrokes...) uses your accent, even ones you picked yourself"));
	/** rgba(12,16,24,0.44) */
	public final ColorSetting glassTint = add(new ColorSetting("glass_tint", "Glass tint", 0x700C1018));
	public final SliderSetting blurStrength = add(new SliderSetting("blur_strength", "Blur strength", 1.0, 0.0, 2.0, 0.05)
		.percent()
		.description("How strongly menus blur the world behind them"));
	public final SliderSetting backgroundDim = add(new SliderSetting("background_dim", "Background dim", 0.30, 0.0, 0.8, 0.01)
		.percent());
	public final SliderSetting animationSpeed = add(new SliderSetting("animation_speed", "Animation speed", 1.0, 0.5, 2.0, 0.05)
		.multiplier());
	public final BooleanSetting reduceMotion = add(new BooleanSetting("reduce_motion", "Reduce motion", false)
		.description("Fades only: no scaling, sliding or rotating"));
	/** Safety: tones down every flash (explosions, hits, lightning, the totem blast, the low-health beat). */
	public final BooleanSetting reduceFlashing = add(new BooleanSetting("reduce_flashing", "Fewer flashes", false)
		.description("Tones down flashing effects: explosion and hit flashes, lightning, the totem blast and the low-health beat"));
	public final BooleanSetting customFonts = add(new BooleanSetting("custom_fonts", "Client fonts", true)
		.description("Use Outfit and JetBrains Mono instead of the Minecraft font"));

	public enum HudFont implements EnumSetting.Labeled {
		MINECRAFT("Minecraft"),
		CLIENT("Outfit");

		private final String label;

		HudFont(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<HudFont> hudFont = add(new EnumSetting<>("hud_font", "HUD font", HudFont.MINECRAFT)
		.description("Font for HUD mods like Keystrokes and Coordinates"));

	public final SliderSetting menuSounds = add(new SliderSetting("menu_sounds", "Menu sounds", 0.6, 0.0, 1.0, 0.05).percent()
		.description("Soft clicks and swooshes in the client's menus (0% = off)"));
	public final BooleanSetting customMainMenu = add(new BooleanSetting("custom_main_menu", "Client main menu", true)
		.description("Kairo's title screen, worlds and servers screens"));
	public final BooleanSetting restyleMenus = add(new BooleanSetting("restyle_menus", "Restyle other menus", true)
		.description("Rounded buttons and the glass backdrop in Minecraft's own menus"));

	/** How the Mods panel shows mods (the switcher next to its search box). */
	public enum ModsLayout implements EnumSetting.Labeled {
		CARDS("Cards"),
		LARGE("Large tiles"),
		LIST("List"),
		TINY("Tiny tiles");

		private final String label;

		ModsLayout(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<ModsLayout> modsLayout = add(new EnumSetting<>("mods_layout", "Mods layout", ModsLayout.CARDS)
		.<EnumSetting<ModsLayout>>hidden());

	// ---- buttons in Minecraft's own menus (Visuals → Buttons) ----

	public enum ClickEffect implements EnumSetting.Labeled {
		NONE("None"),
		RIPPLE("Ripple"),
		POP("Pop"),
		SHINE("Shine"),
		RAINBOW("Rainbow"),
		SPARKS("Sparks"),
		SHOCKWAVE("Shockwave"),
		CONFETTI("Confetti");

		private final String label;

		ClickEffect(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final SliderSetting buttonRadius = add(new SliderSetting("button_radius", "Corner radius", 5, 0, 10, 0.5).unit("px")
		.description("0 = square corners; also used by text fields and sliders"));
	public final BooleanSetting buttonAnimations = add(new BooleanSetting("button_animations", "Animated buttons", true)
		.description("Buttons fade in when a menu opens and their highlight fades in and out"));
	public final BooleanSetting buttonGlow = add(new BooleanSetting("button_glow", "Hover glow", true)
		.description("A soft accent glow under the button you point at"));
	public final BooleanSetting buttonOutline = add(new BooleanSetting("button_outline", "Outline", true));
	public final ColorSetting buttonColor = add(new ColorSetting("button_color", "Button color", 0x9E0E131B));
	public final EnumSetting<ClickEffect> buttonClick = add(new EnumSetting<>("button_click", "Click effect", ClickEffect.RIPPLE)
		.description("What happens when you click a button"));

	/** The ring menu breathes, lights orbit it and the centre pulses (in Kairo Void it's also just the ring). */
	public final BooleanSetting livingRing = add(new BooleanSetting("living_ring", "Living ring", true)
		.description("The ring breathes, lights orbit it and the centre pulses"));
	public final BooleanSetting menuFx = add(new BooleanSetting("menu_fx", "Living menus", true)
		.description("Drifting light and motes behind the menus, and a light running along big panels"));

	public final BooleanSetting hidePauseExtras = add(new BooleanSetting("hide_pause_extras", "Hide extra pause buttons", false)
		.description("The small icon row (bug report, feedback, friends, player reporting) in the pause menu"));

	/** Which ring segments show, in order (Visuals → Ring). */
	public final dev.mordo.kairo.core.settings.TextSetting ringSegments = add(new dev.mordo.kairo.core.settings.TextSetting("ring_segments",
		"Ring segments", dev.mordo.kairo.gui.menu.RingSection.DEFAULT, 400).<dev.mordo.kairo.core.settings.TextSetting>hidden());

	/** Set once the first-run welcome setup is done (or skipped). */
	public final BooleanSetting onboarded = add(new BooleanSetting("onboarded", "Welcome setup done", false).<BooleanSetting>hidden());

	/** Easter egg (click the logo in Settings → About): the ring menu can be spun. */
	public final BooleanSetting ringSpinUnlocked = add(new BooleanSetting("ring_spin_unlocked", "Spinning ring found", false).<BooleanSetting>hidden());
	public final BooleanSetting ringSpin = add(new BooleanSetting("ring_spin", "Spinning ring", false)
		.<BooleanSetting>description("Drag, flick or scroll the ring menu to spin it").<BooleanSetting>hidden());

	private ClientSettings() {
		super("client");
		// Mod colours that follow the accent read it from here.
		ColorSetting.accentSource = () -> accent.argb();
		ColorSetting.accentEverywhere = accentEverywhere::isOn;
	}
}
