package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Two-way "Normal | PvP" switch with a sliding thumb (accent on the left, PvP accent on the right).
 * The binding decides what it flips: the real PvP mode, or just what a preview shows.
 */
public final class ModeSwitch extends Widget {
	private static final String LEFT = "Normal";
	private static final String RIGHT = "PvP";

	private final Binding<Boolean> pvp;
	private final Animation thumb;
	private float textSize = 11.5f;

	public ModeSwitch(Binding<Boolean> pvp) {
		this.pvp = pvp;
		this.thumb = new Animation(pvp.get() ? 1 : 0);
		this.h = 34;
		this.w = preferredWidth();
	}

	public ModeSwitch textSize(float size) {
		this.textSize = size;
		return this;
	}

	private float segmentWidth() {
		return Math.max(Fonts.width(Typeface.OUTFIT_SEMIBOLD, LEFT, textSize), Fonts.width(Typeface.OUTFIT_SEMIBOLD, RIGHT, textSize)) + 24;
	}

	@Override
	public float preferredWidth() {
		return segmentWidth() * 2 + 6;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		thumb.animateTo(pvp.get() ? 1 : 0, 260, Easing.UI);
		float t = thumb.value();
		float radius = Math.min(10, h / 2);
		Shapes.rect(g, x, y, w, h, radius, Colors.multiplyAlpha(Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover.value()), fade),
			Colors.multiplyAlpha(Theme.BORDER, fade), 1);

		float seg = (w - 6) / 2;
		float thumbX = Animation.lerp(x + 3, x + 3 + seg, t);
		int thumbColor = Colors.lerp(Theme.accent(), Theme.pvpAccent(), t);
		Shapes.rect(g, thumbX, y + 3, seg, h - 6, radius - 2, Colors.multiplyAlpha(thumbColor, fade));

		float cy = y + h / 2;
		// Selected label in dark text on the thumb, the other one in soft white.
		int left = Colors.lerp(Theme.DARK_TEXT, Theme.text(0.65f), t);
		int right = Colors.lerp(Theme.text(0.65f), Theme.DARK_TEXT, t);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, LEFT, x + 3 + seg / 2, cy, textSize, Colors.multiplyAlpha(left, fade), Fonts.Align.CENTER, 0, false);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, RIGHT, x + 3 + seg * 1.5f, cy, textSize, Colors.multiplyAlpha(right, fade), Fonts.Align.CENTER, 0, false);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		// Clicking a side picks it.
		boolean wantPvp = mx >= x + w / 2;
		if (wantPvp == pvp.get()) {
			return true;
		}
		dev.mordo.kairo.sound.UiSounds.click();
		pvp.set(wantPvp);
		return true;
	}
}
