package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Rounded button with an optional icon. */
public final class Button extends Widget {
	public enum Style {
		/** Accent fill, dark text. */
		PRIMARY,
		/** Faint white fill. */
		SUBTLE,
		/** No fill until hovered (icon buttons). */
		GHOST,
		/** Red-ish, for deleting. */
		DANGER
	}

	private String label;
	private @Nullable Icon icon;
	private final Style style;
	private final Runnable action;
	private float textSize = 12;

	public Button(String label, @Nullable Icon icon, Style style, Runnable action) {
		this.label = label;
		this.icon = icon;
		this.style = style;
		this.action = action;
		this.h = 34;
		this.w = preferredWidth();
	}

	public Button textSize(float size) {
		this.textSize = size;
		return this;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setIcon(@Nullable Icon icon) {
		this.icon = icon;
	}

	@Override
	public float preferredWidth() {
		float iconW = icon != null ? 16 + (label.isEmpty() ? 0 : 8) : 0;
		float textW = label.isEmpty() ? 0 : Fonts.width(Typeface.OUTFIT_SEMIBOLD, label, textSize);
		return Math.max(h, iconW + textW + 28);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float hv = hover.value();
		float alpha = fade * (enabled ? 1 : 0.4f);
		int fill;
		int stroke;
		int fg;
		switch (style) {
			case PRIMARY -> {
				fill = Colors.brighten(Theme.accent(), hv * 0.15f);
				stroke = 0;
				fg = Theme.DARK_TEXT;
			}
			case DANGER -> {
				fill = Colors.lerp(0x26FF5A5A, 0x40FF5A5A, hv);
				stroke = 0x40FF7A7A;
				fg = 0xFFFFB3B3;
			}
			case GHOST -> {
				fill = Colors.lerp(0x00FFFFFF, Theme.SURFACE_HOVER, hv);
				stroke = 0;
				fg = Colors.lerp(Theme.text(0.65f), Theme.TEXT, hv);
			}
			default -> {
				fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hv);
				stroke = Colors.lerp(Theme.BORDER, Theme.BORDER_HOVER, hv);
				fg = Theme.TEXT;
			}
		}
		float radius = Math.min(10, h / 2);
		Shapes.rect(g, x, y, w, h, radius, Colors.multiplyAlpha(fill, alpha), Colors.multiplyAlpha(stroke, alpha), stroke == 0 ? 0 : 1);
		float contentW = (icon != null ? 16 : 0) + (icon != null && !label.isEmpty() ? 8 : 0)
			+ (label.isEmpty() ? 0 : Fonts.width(Typeface.OUTFIT_SEMIBOLD, label, textSize));
		float cx = x + (w - contentW) / 2;
		float cy = y + h / 2;
		int color = Colors.multiplyAlpha(fg, alpha);
		if (icon != null) {
			IconRenderer.draw(g, icon, cx + 8, cy, 16, color, 2f);
			cx += 24;
		}
		if (!label.isEmpty()) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, label, cx, cy, textSize, color);
		}
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		dev.mordo.kairo.sound.UiSounds.click();
		action.run();
		return true;
	}
}
