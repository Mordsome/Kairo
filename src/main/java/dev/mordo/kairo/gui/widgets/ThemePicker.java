package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.api.Addons;
import dev.mordo.kairo.api.HudLook;
import dev.mordo.kairo.api.MenuLook;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.hud.HudFx;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Themes as pictures: one tile per theme, each showing a small live scene drawn by that theme itself (a menu panel
 * with a button, part of the ring, a HUD box), so you pick by how it looks. A theme is a menu look and a HUD look
 * with the same id (an addon can add either or both); picking one sets both, Kairo's own first.
 */
public final class ThemePicker {
	/** One theme: Kairo's own (both looks null) or an addon's. */
	public record Entry(String id, String name, @Nullable MenuLook menu, @Nullable HudLook hud) {
		/** Picks this theme: its menu look and its HUD look (Kairo's own for whichever it doesn't have). */
		public void apply() {
			ClientSettings s = ClientSettings.INSTANCE;
			s.menuLook.set(menu != null ? id : Addons.DEFAULT_LOOK);
			s.hudLook.set(hud != null ? id : Addons.DEFAULT_LOOK);
		}

		/** True if this is the theme in use: the menus wear it (or, for a HUD-only theme, the HUD does). */
		public boolean chosen() {
			ClientSettings s = ClientSettings.INSTANCE;
			String menuId = s.menuLook.get();
			if (menu != null) {
				return menuId.equals(id);
			}
			if (hud != null) {
				return Addons.activeLook() == null && s.hudLook.get().equals(id);
			}
			return Addons.activeLook() == null && Addons.activeHudLook() == null;
		}
	}

	private ThemePicker() {
	}

	/** True once an addon adds a theme (otherwise there is only Kairo's own and nothing to pick). */
	public static boolean available() {
		return Addons.hasMenuLooks() || Addons.hasHudLooks();
	}

	/** Kairo's own theme first, then every addon theme (menu and HUD looks with the same id are one theme). */
	public static List<Entry> entries() {
		Map<String, Entry> byId = new LinkedHashMap<>();
		for (MenuLook look : Addons.menuLooks()) {
			byId.put(look.id(), new Entry(look.id(), look.name(), look, null));
		}
		for (HudLook look : Addons.hudLooks()) {
			Entry menu = byId.get(look.id());
			byId.put(look.id(), menu != null ? new Entry(look.id(), menu.name(), menu.menu(), look) : new Entry(look.id(), look.name(), null, look));
		}
		List<Entry> all = new ArrayList<>();
		all.add(new Entry(Addons.DEFAULT_LOOK, Edition.isVoid() ? "Kairo Void" : "Kairo", null, null));
		all.addAll(byId.values());
		return all;
	}

	/** The picture grid, for the top of a settings list. */
	public static SettingsList.Section section() {
		List<Tile> tiles = new ArrayList<>();
		for (Entry entry : entries()) {
			tiles.add(new Tile(entry));
		}
		return (g, x, y, w, mx, my, fade, put) -> {
			Fonts.draw(g, Typeface.MONO_BOLD, "THEMES", x + 2, y + 8, 8.5f, Theme.text(0.45f, fade), Fonts.Align.LEFT, 2f, false);
			float top = y + 22;
			float gap = 10;
			int cols = Math.max(2, (int) ((w + gap) / (210 + gap)));
			float tw = (w - gap * (cols - 1)) / cols;
			float th = (tw - 16) * 0.58f + 40;
			for (int i = 0; i < tiles.size(); i++) {
				Tile tile = tiles.get(i);
				tile.bounds(x + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th);
				put.accept(tile);
			}
			int rows = (tiles.size() + cols - 1) / cols;
			return 22 + rows * (th + gap) + 8;
		};
	}

	/** A theme's tile: its scene, its name, and a tick when it's the one in use. */
	private static final class Tile extends Widget {
		private final Entry entry;
		private final Animation pick = new Animation(0);

		Tile(Entry entry) {
			this.entry = entry;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			boolean on = entry.chosen();
			pick.animateTo(on ? 1 : 0, 200);
			float p = pick.value();
			float hv = hover.value();
			int accent = Theme.accent();
			// The tile itself stays plain (Kairo's own box), so every scene sits on the same ground.
			int stroke = Colors.lerp(Colors.argb(0.10f + 0.18f * hv, 0xFFFFFF), Colors.withAlpha(accent, 0.9f), p);
			Shapes.rect(g, x, y, w, h, 12, Colors.multiplyAlpha(Colors.argb(0.05f + 0.04f * hv, 0xFFFFFF), fade),
				Colors.multiplyAlpha(stroke, fade), 1 + p);
			float px = x + 8;
			float py = y + 8;
			float pw = w - 16;
			float ph = h - 40;
			scene(g, entry, px, py, pw, ph, fade);
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, entry.name(), 12.5f, w - 40), x + 12, y + h - 16, 12.5f,
				Theme.text(1, fade));
			if (p > 0.01f) {
				Shapes.circle(g, x + w - 16, y + h - 16, 7 * p, Colors.multiplyAlpha(accent, fade));
				IconRenderer.draw(g, Icon.CHECK, x + w - 16, y + h - 16, 9 * p, Colors.multiplyAlpha(Theme.DARK_TEXT, fade), 3f);
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) {
				return false;
			}
			UiSounds.click();
			entry.apply();
			return true;
		}
	}

	/** A small scene drawn by the theme: sky and ground, a menu panel with a button, part of the ring, a HUD box. */
	public static void scene(GuiGraphicsExtractor g, Entry entry, float x, float y, float w, float h, float fade) {
		int accent = Theme.accent();
		// Something for glass to show: a sky over grass.
		Shapes.gradientRect(g, x, y, w, h * 0.62f, 8, Colors.argb(fade, 0x5B7FB0), Colors.argb(fade, 0x9DB7D8));
		Shapes.gradientRect(g, x, y + h * 0.62f, w, h * 0.38f, 0, Colors.argb(fade, 0x4E7A3A), Colors.argb(fade, 0x2F4D24));
		Shapes.rect(g, x, y + h - 8, w, 8, 8, Colors.argb(fade, 0x2F4D24));

		// A menu panel with two lines of text and a button.
		float panelW = w * 0.52f;
		float panelH = h - 16;
		float panelX = x + 8;
		float panelY = y + 8;
		panel(g, entry, panelX, panelY, panelW, panelH, 9, Colors.argb(0.10f, 0xFFFFFF), Theme.PANEL_BORDER, 1, fade);
		Shapes.rect(g, panelX + 10, panelY + 12, panelW * 0.55f, 4, 2, Theme.text(0.9f, fade));
		Shapes.rect(g, panelX + 10, panelY + 21, panelW * 0.38f, 3, 1.5f, Theme.text(0.45f, fade));
		float buttonH = Math.min(18, panelH * 0.24f);
		panel(g, entry, panelX + 10, panelY + panelH - buttonH - 10, panelW - 20, buttonH, 6, Colors.withAlpha(accent, 0.55f), Colors.withAlpha(accent, 0.9f), 1, fade);
		panel(g, entry, panelX + 10, panelY + panelH - buttonH * 2 - 16, panelW - 20, buttonH, 6, Colors.argb(0.08f, 0xFFFFFF), Theme.PANEL_BORDER, 1, fade);

		// Part of the ring: four segments round a centre, one of them picked.
		float outer = Math.min(w * 0.2f, h * 0.36f);
		float cx = x + w * 0.79f;
		float cy = y + h * 0.40f;
		for (int i = 0; i < 4; i++) {
			float mid = (float) (-Math.PI / 2 + i * Math.PI / 2);
			boolean picked = i == 1;
			sector(g, entry, cx, cy, outer * 0.42f, outer, mid, (float) (Math.PI / 4), 3, 4,
				picked ? Colors.withAlpha(accent, 0.6f) : Colors.argb(0.10f, 0xFFFFFF), picked ? Colors.withAlpha(accent, 0.9f) : Theme.PANEL_BORDER, 1, fade);
		}

		// A HUD box with a reading in it.
		float boxW = w * 0.3f;
		float boxH = 14;
		float bx = x + w - boxW - 8;
		float by = y + h - boxH - 8;
		g.pose().pushMatrix();
		try {
			hudBox(g, entry, bx, by, boxW, boxH, accent, fade);
		} finally {
			g.pose().popMatrix();
		}
		Fonts.draw(g, Typeface.MONO_MEDIUM, "144 FPS", bx + boxW / 2, by + boxH / 2, 8, Theme.text(1, fade), Fonts.Align.CENTER, 0.2f, false);
	}

	private static void panel(GuiGraphicsExtractor g, Entry entry, float x, float y, float w, float h, float r, int tint, int stroke, float sw, float fade) {
		if (entry.menu() != null) {
			try {
				entry.menu().panel(g, x, y, w, h, r, tint, stroke, sw, fade);
				return;
			} catch (Throwable t) {
				Addons.lookFailed(entry.menu(), t);
			}
		}
		if (Edition.isVoid()) {
			Shapes.rect(g, x, y, w, h, r, Colors.multiplyAlpha(Shapes.flat(tint), fade), Colors.multiplyAlpha(Shapes.flatStroke(stroke), fade), sw);
		} else {
			Shapes.kairoGlass(g, x, y, w, h, r, tint, stroke, sw, fade);
		}
	}

	private static void sector(GuiGraphicsExtractor g, Entry entry, float cx, float cy, float inner, float outer, float mid, float half, float gap,
							   float corner, int tint, int stroke, float sw, float fade) {
		if (entry.menu() != null) {
			try {
				entry.menu().sector(g, cx, cy, inner, outer, mid, half, gap, corner, tint, stroke, sw, fade);
				return;
			} catch (Throwable t) {
				Addons.lookFailed(entry.menu(), t);
			}
		}
		if (Edition.isVoid()) {
			Shapes.sector(g, cx, cy, inner, outer, mid, half, gap, corner, Colors.multiplyAlpha(Shapes.flat(tint), fade),
				Colors.multiplyAlpha(Shapes.flatStroke(stroke), fade), sw);
		} else {
			Shapes.kairoGlassSector(g, cx, cy, inner, outer, mid, half, gap, corner, tint, stroke, sw, fade);
		}
	}

	private static void hudBox(GuiGraphicsExtractor g, Entry entry, float x, float y, float w, float h, int accent, float fade) {
		int fill = Colors.argb(0.45f * fade, 0x000000);
		if (entry.hud() != null) {
			try {
				entry.hud().box(g, x, y, w, h, 3, fill, accent, 1.3f, 0);
				return;
			} catch (Throwable t) {
				Addons.hudLookFailed(entry.hud(), t);
			}
		}
		HudFx.kairoBox(g, x, y, w, h, 3, fill, 1.3f, 0);
	}
}
