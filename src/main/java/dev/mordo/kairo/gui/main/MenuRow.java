package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntSupplier;

/**
 * A big main-menu entry: glass row with an icon tile, title and a one-line description. On hover it slides right,
 * an accent bar grows on its left edge and an arrow fades in.
 */
public final class MenuRow extends Widget {
	private final Icon icon;
	private final String title;
	private String subtitle;
	private final Runnable action;
	public final Animation appear = new Animation(0);
	private final Animation press = new Animation(0);
	private IntSupplier accent = Theme::accent;
	private boolean wasHovered;
	/** Keyboard selection, drawn like hover. */
	public boolean selected;

	public MenuRow(Icon icon, String title, String subtitle, Runnable action) {
		this.icon = icon;
		this.title = title;
		this.subtitle = subtitle;
		this.action = action;
	}

	public MenuRow accent(IntSupplier accent) {
		this.accent = accent;
		return this;
	}

	public void setSubtitle(String subtitle) {
		this.subtitle = subtitle;
	}

	public Icon icon() {
		return icon;
	}

	public String title() {
		return title;
	}

	public String subtitle() {
		return subtitle;
	}

	public int accentColor() {
		return accent.getAsInt();
	}

	public void activate() {
		press.set(1).animateTo(0, 300, Easing.OUT_CUBIC);
		action.run();
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float a = fade * appear.value();
		if (a <= 0.002f) {
			return;
		}
		boolean hovered = hover.target() > 0;
		if (hovered && !wasHovered && enabled) {
			UiSounds.hover();
		}
		wasHovered = hovered;
		float hv = Math.max(hover.value(), selected ? 1 : 0) * (enabled ? 1 : 0);
		float alpha = a * (enabled ? 1 : 0.45f);
		float shift = Animation.motion(hv * 8 + (1 - appear.value()) * -24 - press.value() * 3, 0);
		float rx = x + shift;
		int accentColor = accent.getAsInt();

		if (hv > 0.01f) {
			Shapes.shadow(g, rx, y + 8, w, h, 16, 18, Colors.argb(0.28f * hv * alpha, 0));
		}
		int tint = Colors.lerp(Theme.glassTint(), Colors.withAlpha(Colors.lerp(Theme.glassTint() | 0xFF000000, accentColor, 0.18f), 0.5f), hv);
		int stroke = Colors.lerp(0x24FFFFFF, Colors.withAlpha(accentColor, 0.55f), hv);
		Shapes.glassRect(g, rx, y, w, h, 16, tint, stroke, 1.25f, alpha);

		// Accent bar growing from the middle of the left edge.
		float barH = (h - 26) * hv;
		if (barH > 0.5f) {
			Shapes.rect(g, rx + 7, y + (h - barH) / 2, 3, barH, 1.5f, Colors.multiplyAlpha(accentColor, alpha));
		}

		float tile = 38;
		float tx = rx + 20;
		float ty = y + (h - tile) / 2;
		Shapes.rect(g, tx, ty, tile, tile, 11, Colors.multiplyAlpha(Colors.lerp(0x12FFFFFF, Colors.withAlpha(accentColor, 0.2f), hv), alpha));
		int iconColor = Colors.lerp(Theme.text(0.85f), accentColor, hv);
		IconRenderer.draw(g, icon, tx + tile / 2, ty + tile / 2, 20, Colors.multiplyAlpha(iconColor, alpha), 1.9f);

		float textX = tx + tile + 16;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, title, textX, y + h / 2 - 8, 16, Theme.text(1, alpha));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, subtitle, 11.5f, w - (textX - rx) - 50),
			textX, y + h / 2 + 11, 11.5f, Theme.text(0.55f, alpha));

		float arrowX = rx + w - 28 - (1 - hv) * 8;
		IconRenderer.draw(g, Icon.ARROW_RIGHT, arrowX, y + h / 2, 16, Colors.multiplyAlpha(accentColor, alpha * hv), 2.2f);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		activate();
		return true;
	}
}
