package dev.mordo.kairo.gui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.Atmosphere;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.Binding;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.PickTile;
import dev.mordo.kairo.gui.widgets.ScrollState;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.gui.widgets.TextField;
import dev.mordo.kairo.gui.widgets.ToggleSwitch;
import dev.mordo.kairo.gui.widgets.VisualCard;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Kairo Void's Visuals: every visual as a card in a grid, like the Mods panel. Categories on the left, a search,
 * quality presets at the top; a card's switch turns it on, the card (or its gear) opens its options.
 */
public final class VoidVisualsPage extends PanelPage {
	public enum Group {
		PRESETS("Presets", Icon.SPARKLE),
		COMBAT("Combat", Icon.SWORD),
		CRYSTALS("Crystals", Icon.CUBE),
		HAND("Hand", Icon.MOUSE),
		SCREEN("Screen", Icon.EYE),
		WORLD("World & sky", Icon.SUN),
		ATMOSPHERE("Atmospheres", Icon.GLOBE),
		INTERFACE("Interface", Icon.LAYOUT);

		final String label;
		final Icon icon;

		Group(String label, Icon icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	/** One visual: its card, its switch and its options. */
	private record Visual(String name, String description, Icon icon, Group group, Binding<Boolean> on, List<Setting<?>> settings,
						  SettingsList.@Nullable Section header, VisualCard card) {
	}

	private static final float GAP = 14;
	private final ToggleSwitch master = new ToggleSwitch(Binding.normal(VoidSettings.INSTANCE.master));

	private final List<Visual> visuals = new ArrayList<>();
	private final List<Visual> shown = new ArrayList<>();
	private final List<NavItem> nav = new ArrayList<>();
	private final TextField search;
	private final ScrollState gridScroll = new ScrollState();
	private final Button[] presets = new Button[4];
	private @Nullable Group group;
	private float gridTop;
	private float gridBottom;

	private final Animation detail = new Animation(0);
	private @Nullable Visual open;
	private final Map<Visual, SettingsList> lists = new HashMap<>();
	private final Map<Visual, ToggleSwitch> toggles = new HashMap<>();
	private final Button back;
	private final Button reset;

	VoidVisualsPage(MenuScreen menu) {
		super(menu);
		search = new TextField("Search visuals", Icon.SEARCH).maxLength(32).onChange(text -> refresh());
		build();
		nav.add(new NavItem("All", Icon.GRID, () -> Integer.toString(visuals.size()),
			() -> group == null, () -> selectGroup(null)));
		for (Group g : Group.values()) {
			nav.add(new NavItem(g.label, g.icon, () -> Long.toString(visuals.stream().filter(v -> v.group() == g).count()),
				() -> group == g, () -> selectGroup(g)));
		}
		VoidSettings.Quality[] levels = {VoidSettings.Quality.LOW, VoidSettings.Quality.MEDIUM, VoidSettings.Quality.HIGH, VoidSettings.Quality.INSANE};
		for (int i = 0; i < levels.length; i++) {
			VoidSettings.Quality level = levels[i];
			presets[i] = new Button(level.label(), null, Button.Style.GHOST, () -> {
				VoidSettings.INSTANCE.quality.set(level);
				dev.mordo.kairo.sound.UiSounds.click();
			}).textSize(11.5f);
		}
		back = new Button("", Icon.ARROW_LEFT, Button.Style.GHOST, this::closeDetail);
		reset = new Button("Reset", null, Button.Style.SUBTLE, () -> {
			if (open != null) {
				for (Setting<?> s : open.settings()) {
					s.reset();
				}
			}
		}).textSize(11.5f);
	}

	// ---- the catalogue ----

	private void build() {
		VoidSettings vs = VoidSettings.INSTANCE;
		GameVisuals v = GameVisuals.INSTANCE;
		ClientSettings cs = ClientSettings.INSTANCE;

		// Presets: a whole look in one click.
		for (dev.mordo.kairo.core.VoidPresets.Preset preset : dev.mordo.kairo.core.VoidPresets.ALL) {
			Binding<Boolean> pick = new Binding<>() {
				@Override
				public Boolean get() {
					return dev.mordo.kairo.core.VoidPresets.active(preset);
				}

				@Override
				public void set(Boolean value) {
					if (value) {
						dev.mordo.kairo.core.VoidPresets.apply(preset);
					}
				}
			};
			add(preset.name(), preset.description(), Icon.SPARKLE, Group.PRESETS, pick, List.of(), null);
			visuals.getLast().card().pickOnly().art((g, x, y, w, h, a) -> VisualPreviews.preset(g, preset, x, y, w, h, a));
		}

		// Share your look: a code friends can paste.
		add("Copy my look", "Your colours and every visual as one code, copied to paste to a friend", Icon.COPY, Group.PRESETS,
			action(LookShare::copy), List.of(), null);
		visuals.getLast().card().pickOnly();
		add("Paste a look", "Copy a friend's look code, then click here to wear it", Icon.LINK, Group.PRESETS, action(LookShare::paste), List.of(), null);
		visuals.getLast().card().pickOnly();

		// Crystals
		crystal(VoidSettings.CrystalStyle.PRISM, "Prism", "Glowing glass in your colour with rings orbiting it");
		crystal(VoidSettings.CrystalStyle.VOID, "Void core", "A dark heart with a blazing rim, like a tiny black hole");
		crystal(VoidSettings.CrystalStyle.PLASMA, "Plasma", "White-hot energy, crackling arcs and a pulsing glow");
		crystal(VoidSettings.CrystalStyle.HOLO, "Hologram", "See-through, flickering light with scan lines");
		add("Crystal pop", "A burst of light, a ring and a ripple when a crystal explodes", Icon.FLAME, Group.CRYSTALS, bool(vs.crystalPop), List.of(), null);

		// Combat
		add("Energy blades", "Your weapon leaves a blade of light when you swing, brighter on critical hits", Icon.SWORD, Group.COMBAT,
			bool(vs.slashes), List.of(vs.slashSize, vs.slashGlow), null);
		add("Impact shockwaves", "A ripple bends the picture where your hit lands", Icon.SIGNAL, Group.COMBAT, bool(vs.shockwaves), List.of(), null);
		add("White hit flash", "What you hit flashes white for a moment", Icon.SPARKLE, Group.COMBAT, bool(vs.targetFlash), List.of(), null);
		add("Hit-stop", "The picture holds for a split second on strong hits", Icon.CLOCK, Group.COMBAT, bool(vs.hitStop),
			List.of(vs.hitStopLength), null);
		add("Hit colour", "Hurt mobs and players flash in your colour instead of red", Icon.HEART, Group.COMBAT, bool(vs.hitColor),
			List.of(vs.hitColorValue), null);
		add("Crit sparks", "More crit and magic sparks when you hit", Icon.SPARKLE, Group.COMBAT, slider(vs.particleBoost, 1.0, 2.5),
			List.of(vs.particleBoost), null);
		List<PickTile> hits = VisualsArt.hitTiles();
		add("Hit particles", "A burst on whatever you hit", Icon.FLAME, Group.COMBAT, choice(v.hitBurst, GameVisuals.HitBurst.NONE, GameVisuals.HitBurst.SPARKS),
			List.of(v.hitAmount), grid("STYLE", hits));
		List<PickTile> kills = VisualsArt.killTiles();
		add("Kill effects", "Lightning, fireworks, a vortex... where your target falls", Icon.TOTEM, Group.COMBAT,
			choice(v.killEffect, GameVisuals.KillEffect.NONE, GameVisuals.KillEffect.VORTEX), List.of(v.killSound), grid("EFFECT", kills));
		var hitSounds = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.HitSounds.class);
		add("Sound pack", "A matching hit, critical hit and kill sound", Icon.VOLUME, Group.COMBAT,
			choice(hitSounds.pack, dev.mordo.kairo.mods.HitSounds.Pack.NONE, dev.mordo.kairo.mods.HitSounds.Pack.VOID), List.of(hitSounds.packVolume),
			grid("PACK (CLICK TO HEAR IT)", VisualsArt.soundPackTiles()));
		add("Damage numbers", "Floating numbers for the damage you deal", Icon.INFO, Group.COMBAT, bool(v.damageNumbers), List.of(), null);
		add("Edge flash", "The screen edges flash in your colour when you land a hit", Icon.EYE, Group.COMBAT, bool(v.hitFlash), List.of(), null);
		add("Totem blast", "Golden rays and a slow ripple when a totem saves you", Icon.TOTEM, Group.COMBAT, bool(vs.totemBlast), List.of(), null);

		// Hand
		List<PickTile> swing = VisualsArt.swingTiles();
		add("Attack animation", "How your weapon moves when you swing", Icon.SWORD, Group.HAND,
			choice(v.swingStyle, GameVisuals.SwingStyle.VANILLA, GameVisuals.SwingStyle.SLASH), List.of(v.swingDuration), grid("STYLE", swing));
		List<PickTile> mining = VisualsArt.miningTiles();
		add("Mining animation", "How your tool moves while you break blocks", Icon.CUBE, Group.HAND,
			choice(v.miningStyle, GameVisuals.MiningStyle.VANILLA, GameVisuals.MiningStyle.SMOOTH), List.of(v.miningDuration), grid("STYLE", mining));
		List<PickTile> inspect = VisualsArt.inspectTiles();
		add("Inspect", "Show off the item in your hand with a key, like in shooters", Icon.KEYBOARD, Group.HAND, inspectBinding(v),
			List.of(v.inspectKey), grid("STYLE", inspect));
		add("Swing trail", "A glowing streak follows the blade", Icon.SPARKLE, Group.HAND, bool(v.swingTrail), List.of(v.swingTrailRainbow), null);
		List<PickTile> shine = VisualsArt.shineTiles();
		add("Held item glow", "A shimmer or a glowing outline on what you hold", Icon.SUN, Group.HAND,
			choice(v.heldShine, GameVisuals.HeldShine.NONE, GameVisuals.HeldShine.OUTLINE), List.of(), grid("LOOK", shine));
		List<PickTile> glint = VisualsArt.glintTiles();
		add("Enchant colour", "Recolour the enchantment shine on every item", Icon.SPARKLE, Group.HAND,
			choice(v.glintColor, GameVisuals.GlintColor.VANILLA, GameVisuals.GlintColor.ACCENT), List.of(v.glintCustom), grid("COLOUR", glint));

		// Screen
		List<PickTile> filters = VisualsArt.filterTiles();
		add("Colour filter", "Cinematic, neon, noir, vaporwave and more", Icon.IMAGE, Group.SCREEN,
			choice(v.filter, GameVisuals.Filter.NONE, GameVisuals.Filter.CINEMATIC), List.of(v.filterStrength, v.saturation, v.contrast), grid("FILTER", filters));
		add("Bloom", "Bright things glow: the sky, fire, lava, your blades", Icon.SUN, Group.SCREEN, slider(v.glow, 0, 0.5), List.of(v.glow), null);
		add("Vignette", "Darker corners, or corners in your colour", Icon.EYE, Group.SCREEN, slider(v.vignette, 0, 0.5),
			List.of(v.vignette, v.accentVignette), null);
		add("Colour fringes", "Colour fringes towards the edges", Icon.EYE_OFF, Group.SCREEN, slider(v.chromatic, 0, 0.4), List.of(v.chromatic), null);
		add("Film grain", "A fine moving grain", Icon.CAMERA, Group.SCREEN, slider(v.grain, 0, 0.4), List.of(v.grain), null);
		add("Cinema bars", "Black bars top and bottom", Icon.VIEW_LIST, Group.SCREEN, slider(v.letterbox, 0, 0.6), List.of(v.letterbox), null);
		add("Motion blur", "Smears the picture as you turn", Icon.REFRESH, Group.SCREEN, slider(v.motionBlur, 0, 0.5),
			List.of(v.motionBlur, v.motionBlurTurning), null);
		add("Speed lines", "Streaks at the edges when you move fast", Icon.ARROW_RIGHT, Group.SCREEN, slider(v.speedLines, 0, 0.6), List.of(v.speedLines), null);
		add("Low health", "The edges beat red when you're about to die", Icon.HEART, Group.SCREEN, bool(v.lowHealthPulse), List.of(), null);
		add("Fewer flashes", "Tones down every flash: explosions, hits, lightning, the totem blast", Icon.EYE_OFF, Group.SCREEN, bool(cs.reduceFlashing),
			List.of(), null);

		// World & sky
		add("Cosmic sky", "Nebula, a turning galaxy, a ringed planet and shooting stars at night", Icon.GLOBE, Group.WORLD, bool(vs.cosmicSky),
			List.of(vs.planet, vs.shootingStars), null);
		add("Aurora", "Curtains of light over the northern sky", Icon.SPARKLE, Group.WORLD, bool(vs.aurora), List.of(), null);
		add("Rich sunsets", "Deeper, warmer colours at sunrise and sunset", Icon.SUN, Group.WORLD, bool(vs.sunsets), List.of(), null);
		add("Depth fog", "Distant land fades into a sky-coloured haze", Icon.COMPASS, Group.WORLD, slider(vs.depthFog, 0, 0.5), List.of(vs.depthFog), null);
		add("Fireflies", "Little lights drift over grass and flowers at night", Icon.SPARKLE, Group.WORLD, bool(vs.fireflies), List.of(), null);
		List<PickTile> jumps = VisualsArt.jumpTiles();
		add("Jump effects", "A puff, ring or sparkles when you jump and land", Icon.ARROW_RIGHT, Group.WORLD,
			choice(v.jumpEffect, GameVisuals.JumpEffect.NONE, GameVisuals.JumpEffect.RING), List.of(v.landingEffect), grid("STYLE", jumps));
		add("Projectile trails", "Arrows, pearls, tridents and snowballs leave a trail", Icon.ARROW_RIGHT, Group.WORLD,
			choice(v.projectileTrail, GameVisuals.ProjectileTrail.NONE, GameVisuals.ProjectileTrail.ACCENT), List.of(v.trailOnlyMine, v.pearlRipple),
			grid("TRAIL", VisualsArt.projectileTiles()));
		add("Item physics", "Dropped items lie flat and tumble as they fall", Icon.BOX, Group.WORLD, bool(v.itemPhysics), List.of(v.rareItemGlow), null);
		List<PickTile> booms = VisualsArt.explosionTiles();
		add("Explosions", "Bigger blasts, camera shake and a flash", Icon.FLAME, Group.WORLD,
			choice(v.explosionStyle, GameVisuals.ExplosionStyle.VANILLA, GameVisuals.ExplosionStyle.SHOCKWAVE), List.of(v.explosionShake, v.explosionFlash),
			grid("STYLE", booms));

		// Atmospheres
		atmosphere(Atmosphere.BLIZZARD, "Blizzard", "A white-out storm: thick snow, frozen fog, snow settling on every block", Icon.CUBE);
		atmosphere(Atmosphere.GALAXY, "Galaxy", "Day or night, the sky opens into space: nebulae, planets and violet light", Icon.GLOBE);
		atmosphere(Atmosphere.BLOOD_MOON, "Blood moon", "A huge red moon, crimson fog and a red cast over everything", Icon.HEART);
		atmosphere(Atmosphere.INFERNO, "Inferno", "A burning orange sky, ash falling and embers rising", Icon.FLAME);
		atmosphere(Atmosphere.SAKURA, "Sakura dream", "Soft pink light, pastel haze and cherry petals on the wind", Icon.SPARKLE);
		atmosphere(Atmosphere.TOXIC, "Toxic", "Poison-green fog, a sickly sky and spores in the air", Icon.EYE_OFF);
		atmosphere(Atmosphere.ABYSS, "Abyss", "Deep-sea blue, light rippling across the ground, drifting particles", Icon.WORLD);
		atmosphere(Atmosphere.SYNTHWAVE, "Synthwave", "A neon striped sun, magenta-to-violet sky and a purple haze", Icon.NOTE);
		atmosphere(Atmosphere.STORM, "Storm", "Dark clouds, heavy rain and lightning lighting up the sky", Icon.SIGNAL);

		// Interface
		add("Glowing HUD", "HUD mods glow in your colour, with neon or running-light styles", Icon.LAYOUT, Group.INTERFACE,
			choice(vs.hudStyle, VoidSettings.HudStyle.CLASSIC, VoidSettings.HudStyle.GLOW), List.of(vs.hudStyle), null);
		List<PickTile> ringTiles = new ArrayList<>();
		for (RingSection rs : RingSection.values()) {
			if (!rs.available()) {
				continue;
			}
			ringTiles.add(PickTile.icon(rs.title, rs.icon, 0xFFE6EEF6, () -> RingSection.isShown(rs), () -> {
				if (!RingSection.toggle(rs)) {
					dev.mordo.kairo.gui.Toasts.show("The ring holds 3 to 8 segments", Theme.accent());
				}
			}));
		}
		add("Living ring", "Just the ring: it breathes, lights orbit it, the centre pulses. Pick its segments here", Icon.GRID, Group.INTERFACE,
			bool(cs.livingRing), List.of(), grid("SEGMENTS (3 TO 8)", ringTiles));
		add("Keystroke ripples", "Keys pop and send out a ripple when you press them", Icon.KEYBOARD, Group.INTERFACE, bool(vs.keyRipples), List.of(), null);
		add("Living menus", "Drifting light behind the menus and a light running along the panels", Icon.GRID, Group.INTERFACE, bool(cs.menuFx),
			List.of(cs.backgroundDim), null);
		add("Accent on all", "Every mod colour (crosshair, outlines, keys...) uses your accent", Icon.SPARKLE, Group.INTERFACE,
			bool(cs.accentEverywhere), List.of(cs.accent), null);
		add("Smooth motion", "How fast menus animate, or fades only", Icon.GAUGE, Group.INTERFACE, invert(cs.reduceMotion),
			List.of(cs.animationSpeed), null);
	}

	private void add(String name, String description, Icon icon, Group group, Binding<Boolean> on, List<Setting<?>> settings,
					 SettingsList.@Nullable Section header) {
		Visual[] self = new Visual[1];
		boolean hasSettings = !settings.isEmpty() || header != null;
		VisualCard card = new VisualCard(name, description, icon, on, hasSettings, () -> openDetail(self[0]));
		self[0] = new Visual(name, description, icon, group, on, settings, header, card);
		visuals.add(self[0]);
	}

	private void atmosphere(Atmosphere which, String name, String description, Icon icon) {
		VoidSettings vs = VoidSettings.INSTANCE;
		Binding<Boolean> on = new Binding<>() {
			@Override
			public Boolean get() {
				return vs.atmosphere.get() == which;
			}

			@Override
			public void set(Boolean value) {
				vs.atmosphere.set(value ? which : Atmosphere.NONE);
			}
		};
		List<Setting<?>> settings = which == Atmosphere.BLIZZARD
			? List.of(vs.atmosphereStrength, vs.atmosphereParticles, vs.snowGround)
			: List.of(vs.atmosphereStrength, vs.atmosphereParticles);
		add(name, description, icon, Group.ATMOSPHERE, on, settings, null);
		visuals.getLast().card().art((g, x, y, w, h, a) -> VisualPreviews.atmosphere(g, which, x, y, w, h, a));
	}

	private void crystal(VoidSettings.CrystalStyle which, String name, String description) {
		VoidSettings vs = VoidSettings.INSTANCE;
		Binding<Boolean> on = new Binding<>() {
			@Override
			public Boolean get() {
				return vs.crystalStyle.get() == which;
			}

			@Override
			public void set(Boolean value) {
				vs.crystalStyle.set(value ? which : VoidSettings.CrystalStyle.VANILLA);
			}
		};
		add(name, description, Icon.CUBE, Group.CRYSTALS, on, List.of(vs.crystalGlow), null);
		visuals.getLast().card().art((g, x, y, w, h, a) -> VisualPreviews.crystal(g, which, x, y, w, h, a));
	}

	private static SettingsList.Section grid(String title, List<PickTile> tiles) {
		return (g, x, y, w, mx, my, fade, put) -> PickTile.grid(g, title, tiles, x, y, w, fade, put);
	}

	/** A card that does something when clicked (never shows as picked). */
	private static Binding<Boolean> action(Runnable run) {
		return new Binding<>() {
			@Override
			public Boolean get() {
				return false;
			}

			@Override
			public void set(Boolean value) {
				if (value) {
					run.run();
				}
			}
		};
	}

	private static Binding<Boolean> bool(BooleanSetting setting) {
		return Binding.normal(setting);
	}

	private static Binding<Boolean> invert(BooleanSetting setting) {
		return new Binding<>() {
			@Override
			public Boolean get() {
				return !setting.getNormal();
			}

			@Override
			public void set(Boolean value) {
				setting.set(!value);
			}
		};
	}

	/** On = any choice but {@code off}; switching on brings back the last choice (or {@code first}). */
	private static <E extends Enum<E>> Binding<Boolean> choice(EnumSetting<E> setting, E off, E first) {
		Object[] last = {null};
		return new Binding<>() {
			@Override
			public Boolean get() {
				return setting.getNormal() != off;
			}

			@Override
			@SuppressWarnings("unchecked")
			public void set(Boolean value) {
				if (value) {
					setting.set(last[0] != null ? (E) last[0] : first);
				} else {
					last[0] = setting.getNormal();
					setting.set(off);
				}
			}
		};
	}

	/** On = above {@code off}; switching on brings back the last strength (or {@code first}). */
	private static Binding<Boolean> slider(SliderSetting setting, double off, double first) {
		double[] last = {first};
		return new Binding<>() {
			@Override
			public Boolean get() {
				return setting.getNormal() > off + 0.001;
			}

			@Override
			public void set(Boolean value) {
				if (value) {
					setting.set(last[0]);
				} else {
					last[0] = setting.getNormal();
					setting.set(off);
				}
			}
		};
	}

	private static Binding<Boolean> inspectBinding(GameVisuals v) {
		return new Binding<>() {
			@Override
			public Boolean get() {
				return !v.inspectKey.getNormal().isNone();
			}

			@Override
			public void set(Boolean value) {
				if (value) {
					v.inspectKey.reset();
				} else {
					v.inspectKey.set(Keybind.NONE);
				}
			}
		};
	}

	// ---- navigation ----

	@Override
	protected String navLabel() {
		return "VISUALS";
	}

	@Override
	protected List<NavItem> navItems() {
		return nav;
	}

	@Override
	public void show() {
		super.show();
		refresh();
	}

	private void selectGroup(@Nullable Group g) {
		group = g;
		closeDetail();
		refresh();
	}

	/** Shows a category by index (0 = All). */
	public void select(int index) {
		selectGroup(index <= 0 ? null : Group.values()[Math.min(index, Group.values().length) - 1]);
	}

	private void refresh() {
		shown.clear();
		String query = search.text().trim().toLowerCase(Locale.ROOT);
		for (Visual v : visuals) {
			if (group != null && v.group() != group) {
				continue;
			}
			if (!query.isEmpty() && !v.name().toLowerCase(Locale.ROOT).contains(query) && !v.description().toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			shown.add(v);
		}
		for (int i = 0; i < shown.size(); i++) {
			shown.get(i).card().appear.set(0).animateTo(1, 340, Easing.UI, i * 22);
		}
		gridScroll.reset();
	}

	private void openDetail(Visual v) {
		open = v;
		list(v).resetScroll();
		host.reset();
		detail.animateTo(1, 320, Easing.UI);
	}

	private void closeDetail() {
		if (detail.target() > 0) {
			host.reset();
			detail.animateTo(0, 280, Easing.UI);
		}
	}

	private SettingsList list(Visual v) {
		return lists.computeIfAbsent(v, k -> SettingsList.forSettings(host, k.settings().isEmpty() ? null : "OPTIONS", k.settings(), false)
			.header(k.header()));
	}

	// ---- drawing ----

	@Override
	protected void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float t = detail.value();
		float slide = Animation.motion(1, 0);
		if (t < 0.999f) {
			g.pose().pushMatrix();
			g.pose().translate(-40 * t * slide, 0);
			renderGrid(g, mx, my, fade * (1 - t), live && t < 0.05f);
			g.pose().popMatrix();
		}
		if (t > 0.001f && open != null) {
			g.pose().pushMatrix();
			g.pose().translate(60 * (1 - t) * slide, 0);
			renderDetail(g, open, mx, my, fade * t, live && t > 0.95f);
			g.pose().popMatrix();
		}
	}

	private void renderGrid(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		long on = visuals.stream().filter(v -> v.group() != Group.PRESETS && v.on().get()).count();
		boolean master = VoidSettings.INSTANCE.master.isOn();
		drawTitle(g, "Visuals", "", contentX, fade);
		search.bounds(contentX + contentW - 240, py + 26, 240, 36);
		put(search, g, mx, my, fade, live);

		// Quality presets: Low · Medium · High · Insane (the current one lit).
		VoidSettings.Quality current = VoidSettings.INSTANCE.quality.get();
		float bw = 66;
		float bx = search.x - 16 - bw * presets.length - 6;
		Shapes.rect(g, bx - 3, py + 26, bw * presets.length + 6, 36, 12, Colors.argb(0.05f * fade, 0xFFFFFF), Colors.argb(0.08f * fade, 0xFFFFFF), 1);
		for (int i = 0; i < presets.length; i++) {
			if (current.ordinal() == i) {
				Shapes.shadow(g, bx + i * bw + 1, py + 29, bw - 2, 30, 9, 8, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.35f), fade));
				Shapes.rect(g, bx + i * bw + 1, py + 29, bw - 2, 30, 9, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.25f), fade),
					Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.7f), fade), 1);
			}
			presets[i].bounds(bx + i * bw, py + 27, bw, 34);
			put(presets[i], g, mx, my, fade, live);
		}
		Fonts.draw(g, Typeface.MONO_BOLD, current == VoidSettings.Quality.CUSTOM ? "QUALITY · CUSTOM" : "QUALITY", bx, py + 72, 7.5f,
			Theme.text(0.4f, fade), Fonts.Align.LEFT, 1.6f, false);

		// The master switch: every visual on or off at once.
		float mw = 150;
		float mxx = contentX + 118;
		int accent = Theme.accent();
		if (master) {
			Shapes.shadow(g, mxx, py + 26, mw, 36, 12, 10, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.3f), fade));
		}
		Shapes.rect(g, mxx, py + 26, mw, 36, 12, Colors.multiplyAlpha(master ? Colors.withAlpha(accent, 0.18f) : 0x0DFFFFFF, fade),
			Colors.multiplyAlpha(master ? Colors.withAlpha(accent, 0.7f) : 0x20FFFFFF, fade), 1);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, master ? on + " on" : "All off", mxx + 14, py + 44, 12, Theme.text(master ? 1 : 0.6f, fade));
		this.master.bounds(mxx + mw - 12 - ToggleSwitch.WIDTH, py + 34, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		put(this.master, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.MONO_BOLD, "ALL VISUALS · J", mxx, py + 72, 7.5f, Theme.text(0.4f, fade), Fonts.Align.LEFT, 1.6f, false);

		gridTop = py + 92;
		gridBottom = py + ph - 20;
		if (shown.isEmpty()) {
			gridScroll.setSizes(0, gridBottom - gridTop);
			Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, "No visuals match \"" + search.text() + "\"", contentX + contentW / 2, gridTop + 60, 13,
				Theme.text(0.5f, fade));
			return;
		}
		// Every tile the same size, four to a row. In All, each category starts under its own heading.
		int columns = 4;
		float cardW = (contentW - GAP * (columns - 1)) / columns;
		float cardH = Math.round(cardW * 0.62f) + 62;
		boolean headings = group == null;
		float[] tops = new float[shown.size()];
		List<Object[]> headingRows = new ArrayList<>();
		float cursor = 0;
		int col = 0;
		Group current2 = null;
		for (int i = 0; i < shown.size(); i++) {
			Group gr = shown.get(i).group();
			if (headings && gr != current2) {
				if (col != 0) {
					cursor += cardH + GAP;
					col = 0;
				}
				headingRows.add(new Object[]{gr, cursor});
				cursor += 30;
				current2 = gr;
			}
			tops[i] = cursor;
			col++;
			if (col == columns) {
				col = 0;
				cursor += cardH + GAP;
			}
		}
		if (col != 0) {
			cursor += cardH + GAP;
		}
		gridScroll.setSizes(cursor - GAP + 8, gridBottom - gridTop);
		g.enableScissor((int) Math.floor(contentX - 12), (int) Math.floor(gridTop - 8), (int) Math.ceil(contentX + contentW + 12), (int) Math.ceil(gridBottom));
		try {
			float offset = gridScroll.value();
			for (Object[] heading : headingRows) {
				Group gr = (Group) heading[0];
				float hy = gridTop + 6 + (float) heading[1] - offset;
				if (hy > gridTop - 30 && hy < gridBottom) {
					dev.mordo.kairo.gui.IconRenderer.draw(g, gr.icon, contentX + 7, hy + 10, 12, Colors.multiplyAlpha(Theme.accent(), fade), 1.9f);
					Fonts.draw(g, Typeface.MONO_BOLD, gr.label.toUpperCase(Locale.ROOT), contentX + 20, hy + 10, 8.5f, Theme.text(0.6f, fade),
						Fonts.Align.LEFT, 2f, false);
					float lx = contentX + 28 + Fonts.width(Typeface.MONO_BOLD, gr.label.toUpperCase(Locale.ROOT), 8.5f, 2f);
					Shapes.gradientRectH(g, lx, hy + 10, contentX + contentW - lx, 1, 0, Colors.argb(0.12f * fade, 0xFFFFFF), Colors.argb(0, 0xFFFFFF));
				}
			}
			int column = 0;
			Group last = null;
			for (int i = 0; i < shown.size(); i++) {
				Visual v = shown.get(i);
				if (headings && v.group() != last) {
					column = 0;
					last = v.group();
				}
				VisualCard card = v.card();
				float cx = contentX + column * (cardW + GAP);
				float cy = gridTop + 6 + tops[i] - offset;
				card.bounds(cx, cy, cardW, cardH);
				if (cy + cardH > gridTop && cy < gridBottom) {
					put(card, g, mx, my, fade, live && my >= gridTop && my <= gridBottom);
				}
				column = (column + 1) % columns;
			}
		} finally {
			g.disableScissor();
		}		gridScroll.drawBar(g, contentX + contentW + 14, gridTop, fade);
	}

	private void renderDetail(GuiGraphicsExtractor g, Visual v, float mx, float my, float fade, boolean live) {
		float top = py + 26;
		back.bounds(contentX - 6, top, 36, 36);
		put(back, g, mx, my, fade, live);
		float titleX = contentX + 40;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, v.name(), titleX, top + 12, 24, Theme.text(1, fade));
		float nameW = Fonts.width(Typeface.OUTFIT_BOLD, v.name(), 24);
		Fonts.draw(g, Typeface.MONO_BOLD, v.group().label.toUpperCase(Locale.ROOT), titleX + nameW + 12, top + 13, 8.5f,
			Theme.text(0.4f, fade), Fonts.Align.LEFT, 1.6f, false);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, v.description(), titleX, top + 34, 11.5f, Theme.text(0.55f, fade));
		ToggleSwitch toggle = toggles.computeIfAbsent(v, k -> new ToggleSwitch(k.on()));
		toggle.bounds(contentX + contentW - ToggleSwitch.WIDTH, top + 8, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		put(toggle, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, v.on().get() ? "On" : "Off", toggle.x - 10, top + 18, 11.5f, Theme.text(0.6f, fade),
			Fonts.Align.RIGHT, 0, false);
		if (!v.settings().isEmpty()) {
			float resetW = reset.preferredWidth();
			reset.bounds(toggle.x - 46 - resetW, top + 3, resetW, 30);
			put(reset, g, mx, my, fade, live);
		}
		float listTop = top + 66;
		Shapes.rect(g, contentX, listTop - 10, contentW, 1, 0, Colors.argb(0.08f * fade, 0xFFFFFF));
		list(v).render(g, contentX, listTop, contentW + 14, py + ph - 20 - listTop, mx, my, fade, live);
	}

	// ---- input ----

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		if (detail.target() > 0 && open != null) {
			return list(open).mouseScrolled(mx, my, amount);
		}
		if (my >= gridTop && my <= gridBottom && mx >= contentX - 10 && mx <= contentX + contentW + 20) {
			gridScroll.scroll(amount);
			return true;
		}
		return false;
	}

	@Override
	public boolean onClick(float mx, float my, int button) {
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && detail.target() > 0) {
			closeDetail();
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (detail.target() > 0 || !Character.isLetterOrDigit(event.codepoint())) {
			return false;
		}
		host.focus(search);
		return search.charTyped(event);
	}

	@Override
	public boolean onEscape() {
		if (detail.target() > 0) {
			closeDetail();
			return true;
		}
		return false;
	}
}
