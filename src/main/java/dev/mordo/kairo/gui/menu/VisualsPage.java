package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.PickTile;
import dev.mordo.kairo.gui.widgets.SettingsList;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;

/**
 * Visuals, in two groups: In game (hand animations, screen effects, hit effects) and Client (theme, glass, motion,
 * menus, buttons). Changes apply live.
 */
public final class VisualsPage extends PanelPage {
	private record Section(String name, String subtitle, List<Setting<?>> resets, SettingsList list) {
	}

	private final List<Section> sections = new ArrayList<>();
	private final List<NavItem> nav = new ArrayList<>();
	private final Button reset;
	private int selected;

	VisualsPage(MenuScreen menu) {
		super(menu);
		ClientSettings s = ClientSettings.INSTANCE;
		GameVisuals v = GameVisuals.INSTANCE;

		nav.add(NavItem.header("IN GAME"));
		List<PickTile> swing = VisualsArt.swingTiles();
		List<PickTile> mining = VisualsArt.miningTiles();
		List<PickTile> shine = VisualsArt.shineTiles();
		List<PickTile> inspect = VisualsArt.inspectTiles();
		List<PickTile> glint = VisualsArt.glintTiles();
		section("Animations", Icon.SWORD, "How your weapon and tools move and look. Hover a tile to see its path.",
			List.of(v.swingDuration, v.swingTrail, v.swingTrailRainbow, v.miningDuration, v.inspectKey, v.glintCustom),
			List.of(v.swingStyle, v.miningStyle, v.inspectStyle, v.heldShine, v.glintColor)).list().header((g, x, y, w, mx, my, fade, put) -> {
			float h = PickTile.grid(g, "ATTACK", swing, x, y, w, fade, put);
			h += PickTile.grid(g, "MINING", mining, x, y + h, w, fade, put);
			h += PickTile.grid(g, "INSPECT (" + v.inspectKey.get().displayName().getString().toUpperCase(java.util.Locale.ROOT) + ")", inspect, x, y + h, w, fade, put);
			h += PickTile.grid(g, "HELD ITEM", shine, x, y + h, w, fade, put);
			return h + PickTile.grid(g, "SHIMMER COLOUR (ALSO ENCHANTED ITEMS)", glint, x, y + h, w, fade, put);
		});
		List<PickTile> filters = VisualsArt.filterTiles();
		section("Screen effects", Icon.EYE, "Colour filters and camera effects on the world (not on menus or the HUD).",
			List.of(v.filterStrength, v.saturation, v.contrast, v.glow, v.vignette, v.accentVignette, v.chromatic, v.grain, v.letterbox,
				v.motionBlur, v.motionBlurTurning, v.lowHealthPulse),
			List.of(v.filter)).list().header((g, x, y, w, mx, my, fade, put) -> PickTile.grid(g, "FILTER", filters, x, y, w, fade, put));
		List<PickTile> hits = VisualsArt.hitTiles();
		List<PickTile> kills = VisualsArt.killTiles();
		List<PickTile> packs = VisualsArt.soundPackTiles();
		var hitSounds = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.HitSounds.class);
		section("Hit effects", Icon.FLAME, "Particles on what you hit, an effect where it dies, and a sound pack. Only on your screen.",
			List.of(v.hitAmount, v.killSound, v.hitFlash, v.damageNumbers, hitSounds.packVolume),
			List.of(v.hitBurst, v.killEffect, hitSounds.pack)).list().header((g, x, y, w, mx, my, fade, put) -> {
			float h = PickTile.grid(g, "HIT PARTICLES", hits, x, y, w, fade, put);
			h += PickTile.grid(g, "KILL EFFECT", kills, x, y + h, w, fade, put);
			return h + PickTile.grid(g, "SOUND PACK (HITS, CRITS AND KILLS)", packs, x, y + h, w, fade, put);
		});

		List<PickTile> jumps = VisualsArt.jumpTiles();
		List<PickTile> booms = VisualsArt.explosionTiles();
		List<PickTile> trails = VisualsArt.projectileTiles();
		section("Movement & world", Icon.COMPASS, "Jumps, landings, speed, explosions, projectiles and dropped items.",
			List.of(v.landingEffect, v.speedLines, v.explosionShake, v.explosionFlash, v.trailOnlyMine, v.pearlRipple, v.itemPhysics, v.rareItemGlow),
			List.of(v.jumpEffect, v.explosionStyle, v.projectileTrail)).list().header((g, x, y, w, mx, my, fade, put) -> {
			float h = PickTile.grid(g, "JUMP", jumps, x, y, w, fade, put);
			h += PickTile.grid(g, "EXPLOSIONS", booms, x, y + h, w, fade, put);
			return h + PickTile.grid(g, "PROJECTILE TRAILS", trails, x, y + h, w, fade, put);
		});

		nav.add(NavItem.header("CLIENT"));
		List<PickTile> ringTiles = new ArrayList<>();
		for (RingSection rs : RingSection.values()) {
			if (!rs.available()) {
				continue;
			}
			ringTiles.add(PickTile.icon(rs.title, rs.icon, 0xFFE6EEF6, () -> RingSection.isShown(rs), () -> {
				if (!RingSection.toggle(rs)) {
					dev.mordo.kairo.gui.Toasts.show("The ring holds 3 to 8 segments", dev.mordo.kairo.gui.Theme.accent());
				}
			}));
		}
		section("Ring", Icon.GRID, "Pick what the Right Shift ring shows (3 to 8). Number keys follow this order.",
			List.of(s.livingRing), List.of(s.ringSegments)).list().header((g, x, y, w, mx, my, fade, put) -> {
			float h = drawRingPreview(g, x, y, w, fade);
			return h + PickTile.grid(g, "SEGMENTS", ringTiles, x, y + h, w, fade, put);
		});
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			// Void has flat panels and fewer sections: colours, fonts, panels and motion together.
			section("Theme", Icon.SPARKLE, null, List.of(s.accent, s.accentEverywhere, s.pvpAccent, s.customFonts, s.hudFont, s.backgroundDim,
				s.animationSpeed, s.reduceMotion, s.reduceFlashing), List.of());
		} else {
			section("Theme", Icon.SPARKLE, null, List.of(s.accent, s.accentEverywhere, s.pvpAccent, s.customFonts, s.hudFont), List.of());
			section("Glass", Icon.LAYOUT, null, List.of(s.glassTint, s.blurStrength, s.backgroundDim), List.of());
			section("Motion", Icon.GAUGE, null, List.of(s.animationSpeed, s.reduceMotion, s.reduceFlashing), List.of());
		}
		section("Menus", Icon.WORLD, null, List.of(s.customMainMenu, s.restyleMenus, s.menuFx, s.hidePauseExtras, s.menuSounds), List.of());
		Section buttons = section("Buttons", Icon.POWER, null,
			List.of(s.buttonRadius, s.buttonAnimations, s.buttonGlow, s.buttonOutline, s.buttonColor, s.buttonClick), List.of());
		// A live sample at the top of the Buttons section: hover it, click it.
		dev.mordo.kairo.gui.widgets.ButtonPreview sample = new dev.mordo.kairo.gui.widgets.ButtonPreview("Click me", true);
		dev.mordo.kairo.gui.widgets.ButtonPreview second = new dev.mordo.kairo.gui.widgets.ButtonPreview("Options...", true);
		dev.mordo.kairo.gui.widgets.ButtonPreview disabled = new dev.mordo.kairo.gui.widgets.ButtonPreview("Disabled", false);
		s.buttonAnimations.onChange(value -> {
			sample.replay();
			second.replay();
			disabled.replay();
		});
		buttons.list().header((g, x, y, w, mx, my, fade, put) -> {
			float h = 92;
			dev.mordo.kairo.render.Shapes.rect(g, x, y, w, h, 14, dev.mordo.kairo.render.Colors.argb(0.05f * fade, 0xFFFFFF),
				dev.mordo.kairo.render.Colors.argb(0.10f * fade, 0xFFFFFF), 1);
			dev.mordo.kairo.render.font.Fonts.draw(g, dev.mordo.kairo.render.font.Typeface.MONO_BOLD, "PREVIEW  ·  CLICK THEM",
				x + 16, y + 16, 8.5f, dev.mordo.kairo.gui.Theme.text(0.45f, fade), dev.mordo.kairo.render.font.Fonts.Align.LEFT, 2f, false);
			float bw = Math.min(200, (w - 64) / 3);
			sample.bounds(x + 16, y + 36, bw, 30);
			second.bounds(x + 16 + bw + 16, y + 36, bw, 30);
			disabled.bounds(x + 16 + (bw + 16) * 2, y + 36, bw, 30);
			put.accept(sample);
			put.accept(second);
			put.accept(disabled);
			return h;
		});
		// Share your look: one code with your colours and every visual.
		Button copyLook = new Button("Copy my look", Icon.COPY, Button.Style.PRIMARY, LookShare::copy).textSize(12);
		Button pasteLook = new Button("Paste a look", Icon.LINK, Button.Style.GHOST, LookShare::paste).textSize(12);
		section("Share look", Icon.COPY, "Your colours, buttons and every visual as one code. Keybinds and HUD positions stay yours.",
			List.of(), List.of()).list().header((g, x, y, w, mx, my, fade, put) -> {
			float h = 120;
			dev.mordo.kairo.render.Shapes.rect(g, x, y, w, h, 14, dev.mordo.kairo.render.Colors.argb(0.05f * fade, 0xFFFFFF),
				dev.mordo.kairo.render.Colors.argb(0.10f * fade, 0xFFFFFF), 1);
			dev.mordo.kairo.render.font.Fonts.draw(g, dev.mordo.kairo.render.font.Typeface.OUTFIT_REGULAR,
				"Copy: puts your look code on the clipboard. Paste: wears the look code you copied.", x + 16, y + 24, 11.5f,
				dev.mordo.kairo.gui.Theme.text(0.6f, fade));
			float bw = Math.min(200, (w - 48) / 2);
			copyLook.bounds(x + 16, y + 56, bw, 36);
			pasteLook.bounds(x + 32 + bw, y + 56, bw, 36);
			put.accept(copyLook);
			put.accept(pasteLook);
			return h;
		});
		reset = new Button("Reset section", null, Button.Style.SUBTLE, () -> {
			for (Setting<?> setting : sections.get(selected).resets()) {
				setting.reset();
			}
		}).textSize(11.5f);
	}

	/** {@code listed} get rows; {@code extra} are only reset (they're picked with tiles). */
	private Section section(String name, Icon icon, String subtitle, List<Setting<?>> listed, List<Setting<?>> extra) {
		int index = sections.size();
		List<Setting<?>> resets = new ArrayList<>(listed);
		resets.addAll(extra);
		Section section = new Section(name, subtitle, resets, SettingsList.forSettings(host, null, listed, false));
		sections.add(section);
		nav.add(new NavItem(name, icon, null, () -> selected == index, () -> {
			selected = index;
			sections.get(index).list().resetScroll();
		}));
		return section;
	}

	/** A small copy of the ring with the chosen segments, so changes show right away. */
	private static float drawRingPreview(GuiGraphicsExtractor g, float x, float y, float w, float fade) {
		float h = 190;
		dev.mordo.kairo.render.Shapes.rect(g, x, y, w, h - 12, 14, dev.mordo.kairo.render.Colors.argb(0.05f * fade, 0xFFFFFF),
			dev.mordo.kairo.render.Colors.argb(0.10f * fade, 0xFFFFFF), 1);
		RingSection[] shown = RingSection.active();
		float cx = x + w / 2;
		float cy = y + (h - 12) / 2;
		float outer = 76;
		float inner = 40;
		int n = shown.length;
		for (int i = 0; i < n; i++) {
			float mid = (float) (-Math.PI / 2 + i * Math.PI * 2 / n);
			float half = (float) (Math.PI / n);
			dev.mordo.kairo.render.Shapes.arc(g, cx, cy, outer, outer - inner, mid - half + 0.04f, half * 2 - 0.08f,
				dev.mordo.kairo.render.Colors.multiplyAlpha(i % 2 == 0 ? 0x30FFFFFF : 0x22FFFFFF, fade));
			float lr = (outer + inner) / 2;
			dev.mordo.kairo.gui.IconRenderer.draw(g, shown[i].icon, cx + (float) Math.cos(mid) * lr, cy + (float) Math.sin(mid) * lr, 15,
				dev.mordo.kairo.render.Colors.multiplyAlpha(i == 0 ? dev.mordo.kairo.gui.Theme.accent() : 0xFFE6EEF6, fade), 1.8f);
		}
		dev.mordo.kairo.render.font.Fonts.draw(g, dev.mordo.kairo.render.font.Typeface.MONO_BOLD, n + " SEGMENTS", cx, cy, 8.5f,
			dev.mordo.kairo.gui.Theme.text(0.5f, fade), dev.mordo.kairo.render.font.Fonts.Align.CENTER, 1.5f, false);
		return h;
	}

	/** Shows a section by index (0 = Animations). */
	public void select(int index) {
		selected = Math.clamp(index, 0, sections.size() - 1);
		sections.get(selected).list().resetScroll();
	}

	@Override
	protected String navLabel() {
		return "VISUALS";
	}

	@Override
	protected List<NavItem> navItems() {
		return nav;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		Section section = sections.get(selected);
		drawTitle(g, "Visuals", section.name(), contentX, fade);
		float resetW = reset.preferredWidth();
		reset.bounds(contentX + contentW - resetW, py + 28, resetW, 32);
		put(reset, g, mx, my, fade, live);
		float top = py + 92;
		if (section.subtitle() != null) {
			dev.mordo.kairo.render.font.Fonts.draw(g, dev.mordo.kairo.render.font.Typeface.OUTFIT_REGULAR, section.subtitle(), contentX, py + 82, 11,
				dev.mordo.kairo.gui.Theme.text(0.5f, fade));
			top = py + 104;
		}
		section.list().render(g, contentX, top, contentW + 14, py + ph - 20 - top, mx, my, fade, live);
	}

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		return sections.get(selected).list().mouseScrolled(mx, my, amount);
	}
}
