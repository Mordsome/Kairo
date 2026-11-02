package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.ClientSettings.ModsLayout;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * A mod in the Mods panel, in one of four layouts (Cards, Large tiles, List, Tiny tiles): icon tile (accent-tinted
 * when on), name, PVP tag, description, toggle and gear. Lifts and brightens on hover. Clicking anywhere but the
 * toggle opens the mod's settings (or the mod's own screen, like Waypoints).
 */
public final class ModCard extends Widget {
	private final Module module;
	private final ToggleSwitch toggle;
	private final Runnable openSettings;
	public final Animation appear = new Animation(0);
	private final Animation on;
	private final Animation gearHover = new Animation(0);
	private List<String> descriptionLines = List.of();
	private float wrappedFor = -1;
	private int wrappedLines;
	private ModsLayout drawnLayout = ModsLayout.CARDS;

	public ModCard(Module module, Runnable openSettings) {
		this.module = module;
		this.openSettings = openSettings;
		// In PvP mode the switch edits the PvP layer, so it wears the PvP accent.
		this.toggle = new ToggleSwitch(Binding.effective(module.enabled)).accent(Theme::modeAccent);
		this.on = new Animation(module.isEnabled() ? 1 : 0);
	}

	public Module module() {
		return module;
	}

	private static ModsLayout layout() {
		return ClientSettings.INSTANCE.modsLayout.get();
	}

	/** Gear position for the current layout; tiny tiles have none (the whole tile opens settings). */
	private float gearX() {
		return switch (drawnLayout) {
			case LIST -> toggle.x - 14 - 26;
			default -> x + w - 14 - 26;
		};
	}

	private float gearY() {
		return switch (drawnLayout) {
			case LIST -> y + (h - 26) / 2;
			default -> y + h - 12 - 26;
		};
	}

	private boolean hasGear() {
		return drawnLayout != ModsLayout.TINY;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float a = appear.value() * fade;
		if (a <= 0.002f) {
			return;
		}
		drawnLayout = layout();
		on.animateTo(module.isEnabled() ? 1 : 0, 220);
		float hv = hover.value();
		float lift = Animation.motion(hv * 2 + (1 - appear.value()) * -10, 0);
		float cy = y - lift;
		float radius = switch (drawnLayout) {
			case LIST -> 12;
			case TINY -> 14;
			default -> 16;
		};
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hv);
		int stroke = Colors.lerp(Theme.BORDER, Theme.BORDER_HOVER, hv);
		if (hv > 0.01f) {
			Shapes.shadow(g, x, cy + 6, w, h, radius, 14, Colors.argb(0.22f * hv * a, 0));
		}
		Shapes.rect(g, x, cy, w, h, radius, Colors.multiplyAlpha(fill, a), Colors.multiplyAlpha(stroke, a), 1);

		switch (drawnLayout) {
			case CARDS -> drawCard(g, cy, a);
			case LARGE -> drawLarge(g, cy, a);
			case LIST -> drawList(g, cy, a);
			case TINY -> drawTiny(g, cy, a);
		}
		toggle.render(g, mx, my, a, hv > 0);

		if (hasGear()) {
			boolean overGear = mx >= gearX() && mx <= gearX() + 26 && my >= gearY() && my <= gearY() + 26;
			gearHover.animateTo(overGear ? 1 : 0, 140);
			float gh = gearHover.value();
			if (gh > 0.01f) {
				Shapes.circle(g, gearX() + 13, gearY() - lift + 13, 13, Colors.argb(0.08f * gh * a, 0xFFFFFF));
			}
			IconRenderer.draw(g, Icon.GEAR, gearX() + 13, gearY() - lift + 13, 15, Theme.text(0.45f + 0.5f * Math.max(gh, hv * 0.4f), a), 2f);
		}
	}

	private void iconTile(GuiGraphicsExtractor g, float tx, float ty, float tile, float iconSize, float radius, float a) {
		float t = on.value();
		int tileFill = Colors.lerp(0x0FFFFFFF, Colors.withAlpha(Theme.accent(), 0.18f), t);
		int iconColor = Colors.lerp(Theme.text(0.7f), Theme.accent(), t);
		Shapes.rect(g, tx, ty, tile, tile, radius, Colors.multiplyAlpha(tileFill, a),
			Colors.multiplyAlpha(Colors.lerp(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0.3f), t), a), 1);
		IconRenderer.draw(g, module.icon(), tx + tile / 2, ty + tile / 2, iconSize, Colors.multiplyAlpha(iconColor, a), 1.9f);
	}

	/** Name (ellipsized to {@code maxW}) with the PVP tag after it; returns nothing, draws at (textX, cy). */
	private void name(GuiGraphicsExtractor g, float textX, float cy, float size, float maxW, float a, Fonts.Align align) {
		boolean pvpTag = module.hasPvpOverrides();
		float tagW = pvpTag ? Fonts.width(Typeface.MONO_BOLD, "PVP", 7.5f, 1) + 10 : 0;
		String name = Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, module.name(), size, maxW - (pvpTag ? tagW + 6 : 0));
		float nameW = Fonts.width(Typeface.OUTFIT_SEMIBOLD, name, size);
		float startX = align == Fonts.Align.CENTER ? textX - (nameW + (pvpTag ? tagW + 6 : 0)) / 2 : textX;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, name, startX, cy, size, Theme.text(1, a));
		if (pvpTag) {
			float tagX = startX + nameW + 6;
			Shapes.rect(g, tagX, cy - 7, tagW, 14, 4, Colors.multiplyAlpha(Colors.withAlpha(Theme.pvpAccent(), 0.18f), a));
			Fonts.draw(g, Typeface.MONO_BOLD, "PVP", tagX + tagW / 2, cy, 7.5f, Colors.multiplyAlpha(Theme.pvpAccent(), a),
				Fonts.Align.CENTER, 1, false);
		}
	}

	private List<String> description(float width, int maxLines) {
		if (width != wrappedFor || maxLines != wrappedLines) {
			wrappedFor = width;
			wrappedLines = maxLines;
			List<String> lines = Fonts.wrap(Typeface.OUTFIT_REGULAR, module.description(), 11, width);
			if (lines.size() > maxLines) {
				List<String> cut = new java.util.ArrayList<>(lines.subList(0, maxLines));
				cut.set(maxLines - 1, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, cut.get(maxLines - 1) + "…", 11, width));
				lines = cut;
			}
			descriptionLines = lines;
		}
		return descriptionLines;
	}

	// ---- layouts ----

	private void drawCard(GuiGraphicsExtractor g, float cy, float a) {
		iconTile(g, x + 16, cy + 16, 40, 20, 11, a);
		float textX = x + 68;
		name(g, textX, cy + 27, 14, w - 68 - 16 - ToggleSwitch.WIDTH - 10, a, Fonts.Align.LEFT);
		List<String> lines = description(w - 68 - 16 - 30, 2);
		for (int i = 0; i < lines.size(); i++) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, lines.get(i), textX, cy + 49 + i * 14, 11, Theme.text(0.58f, a));
		}
		toggle.bounds(x + w - 16 - ToggleSwitch.WIDTH, cy + 17, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
	}

	private void drawLarge(GuiGraphicsExtractor g, float cy, float a) {
		iconTile(g, x + 20, cy + 20, 56, 28, 15, a);
		name(g, x + 92, cy + 38, 17, w - 92 - 20 - ToggleSwitch.WIDTH - 10, a, Fonts.Align.LEFT);
		Fonts.draw(g, Typeface.MONO_BOLD, module.category().displayName().toUpperCase(java.util.Locale.ROOT), x + 92, cy + 60, 7.5f,
			Theme.text(0.4f, a), Fonts.Align.LEFT, 1.6f, false);
		List<String> lines = description(w - 40 - 40, 3);
		for (int i = 0; i < lines.size(); i++) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, lines.get(i), x + 20, cy + 98 + i * 15, 11.5f, Theme.text(0.6f, a));
		}
		toggle.bounds(x + w - 20 - ToggleSwitch.WIDTH, cy + 24, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
	}

	private void drawList(GuiGraphicsExtractor g, float cy, float a) {
		iconTile(g, x + 12, cy + (h - 32) / 2, 32, 17, 9, a);
		toggle.bounds(x + w - 16 - ToggleSwitch.WIDTH, cy + (h - ToggleSwitch.HEIGHT) / 2, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		float textX = x + 56;
		float nameMax = Math.min(220, (w - 56) * 0.35f);
		name(g, textX, cy + h / 2, 13.5f, nameMax, a, Fonts.Align.LEFT);
		float descX = textX + nameMax + 16;
		float descW = gearX() - 16 - descX;
		if (descW > 40) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, module.description(), 11.5f, descW),
				descX, cy + h / 2, 11.5f, Theme.text(0.55f, a));
		}
	}

	private void drawTiny(GuiGraphicsExtractor g, float cy, float a) {
		float tile = 42;
		float t = on.value();
		if (t > 0.01f) {
			// A soft accent glow behind enabled mods' icons.
			Shapes.shadow(g, x + (w - tile) / 2, cy + 12, tile, tile, 12, 10, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.35f * t), a));
		}
		iconTile(g, x + (w - tile) / 2, cy + 12, tile, 21, 12, a);
		name(g, x + w / 2, cy + 70, 11.5f, w - 14, a, Fonts.Align.CENTER);
		float tw = ToggleSwitch.WIDTH * 0.8f;
		float th = ToggleSwitch.HEIGHT * 0.8f;
		toggle.bounds(x + (w - tw) / 2, cy + h - th - 10, tw, th);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (toggle.contains(mx, my)) {
			return toggle.mouseClicked(mx, my, button);
		}
		boolean onGear = hasGear() && mx >= gearX() - 4 && mx <= gearX() + 30 && my >= gearY() - 4 && my <= gearY() + 30;
		if (!onGear && module.openAction() != null) {
			dev.mordo.kairo.sound.UiSounds.click();
			module.openAction().run();
			return true;
		}
		openSettings.run();
		return true;
	}
}
