package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.background.PaletteExtractor;
import dev.mordo.kairo.render.background.WallpaperColors;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * "From your wallpaper": the colours of the main menu background as clickable swatches, with a small sample of how
 * a panel and its text look in the theme. Used in Visuals → Theme, the background picker and the first-run setup.
 */
public final class WallpaperSwatches {
	private final List<Dot> dots = new ArrayList<>();
	private final boolean card;

	/** {@code card}: draw it on its own rounded card (in settings lists); false for a bare row. */
	public WallpaperSwatches(boolean card) {
		this.card = card;
		for (int i = 0; i < PaletteExtractor.MAX_SWATCHES; i++) {
			dots.add(new Dot(i));
		}
	}

	/** As a settings list header. */
	public SettingsList.Section section() {
		return (g, x, y, w, mx, my, fade, put) -> render(g, x, y, w, fade, put);
	}

	/** Draws the row at (x, y), {@code w} wide; returns the height used. */
	public float render(GuiGraphicsExtractor g, float x, float y, float w, float fade, Consumer<Widget> put) {
		List<Integer> colours = WallpaperColors.swatches();
		float h = card ? 104 : 74;
		float pad = card ? 16 : 0;
		if (card) {
			Shapes.rect(g, x, y, w, h, 14, Colors.argb(0.05f * fade, 0xFFFFFF), Colors.argb(0.10f * fade, 0xFFFFFF), 1);
		}
		String source = WallpaperColors.isCustom() ? "YOUR BACKGROUND"
			: Edition.isVoid() ? "THE VOID SCENE" : "MINECRAFT'S PANORAMA";
		Fonts.draw(g, Typeface.MONO_BOLD, "FROM YOUR WALLPAPER  ·  " + source, x + pad, y + pad + 4, 8.5f, Theme.text(0.45f, fade),
			Fonts.Align.LEFT, 2f, false);
		boolean following = ClientSettings.INSTANCE.wallpaperColors.isOn();
		String hint = WallpaperColors.loading() ? "Reading the colours…"
			: colours.isEmpty() ? "No colours found in this picture"
			: following ? "Click one: it becomes your theme colour and follows your background"
			: "Click one to make it your theme colour";
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, hint, x + pad, y + pad + 22, 11, Theme.text(0.55f, fade));

		float size = 30;
		float gap = 12;
		float dy = y + pad + 38;
		for (int i = 0; i < dots.size(); i++) {
			Dot dot = dots.get(i);
			dot.color = i < colours.size() ? colours.get(i) : 0;
			dot.bounds(x + pad + i * (size + gap), dy, size, size);
			if (dot.color != 0 || WallpaperColors.loading()) {
				put.accept(dot);
			}
		}

		// A sample of the theme: a panel in its tone, main and secondary text, the accent.
		float sw = Math.min(190, w - pad * 2 - dots.size() * (size + gap) - 12);
		if (sw > 120) {
			float sx = x + w - pad - sw;
			float sy = y + pad + 16;
			float sh = h - pad * 2 - 16;
			int panel = Theme.panelColor();
			Shapes.rect(g, sx, sy, sw, sh, 10, Colors.multiplyAlpha(panel, fade), Colors.argb(0.14f * fade, 0xFFFFFF), 1);
			Shapes.circle(g, sx + 16, sy + sh / 2, 5, Colors.multiplyAlpha(Theme.accent(), fade));
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Panel text", sx + 28, sy + sh / 2 - 7, 12, Theme.text(1, fade));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Secondary text", sx + 28, sy + sh / 2 + 8, 10.5f, Theme.text(0.55f, fade));
		}
		return h;
	}

	/** One swatch. */
	private static final class Dot extends Widget {
		private final int index;
		int color;

		Dot(int index) {
			this.index = index;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			float cx = x + w / 2;
			float cy = y + h / 2;
			float hv = hover.value();
			if (color == 0) {
				float pulse = 0.06f + 0.04f * (float) Math.sin(System.currentTimeMillis() / 200.0 + index);
				Shapes.circle(g, cx, cy, w / 2 - 2, Colors.argb(pulse * fade, 0xFFFFFF));
				return;
			}
			boolean picked = WallpaperColors.pickedIndex() == index;
			float r = w / 2 - 2 + hv * 1.5f;
			Shapes.shadow(g, cx - r, cy - r, r * 2, r * 2, r, 8 + 6 * hv, Colors.multiplyAlpha(Colors.withAlpha(color, 0.3f + 0.3f * hv), fade));
			Shapes.circle(g, cx, cy, r, Colors.multiplyAlpha(color, fade));
			if (picked) {
				Shapes.ring(g, cx, cy, r + 4, 1.8f, Colors.multiplyAlpha(Theme.TEXT, fade));
				IconRenderer.draw(g, Icon.CHECK, cx, cy, r * 1.1f, Colors.multiplyAlpha(Theme.DARK_TEXT, fade), 2.6f);
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || color == 0 || !contains(mx, my)) {
				return false;
			}
			dev.mordo.kairo.sound.UiSounds.click();
			WallpaperColors.pick(index);
			return true;
		}
	}
}
