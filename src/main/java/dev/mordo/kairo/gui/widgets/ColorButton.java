package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Colour swatch plus hex code; opens the colour picker. */
public final class ColorButton extends Widget {
	private final boolean alpha;
	private final ColorSetting meta;
	private final Binding<Integer> value;
	private final WidgetHost host;

	public ColorButton(ColorSetting meta, Binding<Integer> value, WidgetHost host) {
		this.alpha = meta.hasAlpha();
		this.meta = meta;
		this.value = value;
		this.host = host;
		this.w = 132;
		this.h = 30;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float a = fade * (enabled ? 1 : 0.4f);
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover.value());
		int stroke = Colors.lerp(Theme.BORDER, Theme.BORDER_HOVER, hover.value());
		Shapes.rect(g, x, y, w, h, 8, Colors.multiplyAlpha(fill, a), Colors.multiplyAlpha(stroke, a), 1);
		float s = h - 10;
		boolean accent = meta.usesAccent();
		boolean moving = accent || meta.mode() != ColorSetting.Mode.STATIC;
		drawSwatch(g, x + 5, y + 5, s, s, 5, moving ? meta.argb() : value.get(), a);
		String label = accent ? "Accent" : moving ? meta.mode().label : ColorSetting.toHex(value.get(), alpha);
		Fonts.draw(g, moving ? Typeface.OUTFIT_SEMIBOLD : Typeface.MONO_MEDIUM, label, x + s + 14, y + h / 2, moving ? 11.5f : 10.5f, Theme.text(0.9f, a));
	}

	/** A colour swatch over a small checkerboard, so transparency is visible. */
	static void drawSwatch(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int argb, float fade) {
		Shapes.rect(g, x, y, w, h, radius, Colors.argb(fade, 0xD8D8D8));
		float cell = h / 2;
		for (int row = 0; row < 2; row++) {
			for (int col = 0; col * cell < w; col++) {
				if ((row + col) % 2 == 0) {
					float cw = Math.min(cell, w - col * cell);
					Shapes.rect(g, x + col * cell, y + row * cell, cw, cell, 0, Colors.argb(fade, 0x9A9A9A));
				}
			}
		}
		Shapes.rect(g, x, y, w, h, radius, Colors.multiplyAlpha(argb, fade), Colors.argb(0.25f * fade, 0xFFFFFF), 1);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		host.openPopup(new ColorPicker(value, alpha, meta), this);
		return true;
	}
}
