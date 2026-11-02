package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ClientInfo;
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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * The Simple main menu style (Kairo Void): over the drawn space scene, just the name and a centred column of plain
 * words. Pointing at one lights it in your colour with a line growing under it.
 */
final class SimpleMenu {
	private final List<Word> words = new ArrayList<>();
	private List<MenuRow> builtFor = List.of();

	/** One menu entry as a plain word. */
	private static final class Word extends Widget {
		private final MenuRow row;
		final Animation appear = new Animation(0);
		boolean selected;

		Word(MenuRow row) {
			this.row = row;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			float a = fade * appear.value() * (enabled ? 1 : 0.4f);
			if (a <= 0.002f) {
				return;
			}
			float h = Math.max(hover.value(), selected ? 1 : 0);
			int accent = row.accentColor();
			float cx = x + w / 2;
			float cy = y + this.h / 2;
			float rise = Animation.motion((1 - appear.value()) * 10, 0);
			float size = 20 + 1.5f * h;
			int color = Colors.lerp(Theme.text(0.72f), Colors.lerp(accent | 0xFF000000, 0xFFFFFFFF, 0.35f), h);
			if (h > 0.01f) {
				float tw = Fonts.width(Typeface.OUTFIT_MEDIUM, row.title(), size);
				Shapes.shadow(g, cx - tw / 2 - 10, cy - 12 + rise, tw + 20, 24, 12, 14, Colors.withAlpha(accent, 0.18f * h * a));
				float lw = (tw + 24) * h;
				Shapes.gradientRectH(g, cx - lw / 2, cy + 16 + rise, lw / 2, 1.5f, 0, Colors.withAlpha(accent, 0), Colors.withAlpha(accent, a));
				Shapes.gradientRectH(g, cx, cy + 16 + rise, lw / 2, 1.5f, 0, Colors.withAlpha(accent, a), Colors.withAlpha(accent, 0));
				float off = tw / 2 + 18 + 4 * (1 - h);
				Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "›", cx - off, cy + rise, 18, Colors.withAlpha(accent, a * h), Fonts.Align.CENTER, 0, false);
				Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "‹", cx + off, cy + rise, 18, Colors.withAlpha(accent, a * h), Fonts.Align.CENTER, 0, false);
			}
			Fonts.draw(g, Typeface.OUTFIT_MEDIUM, row.title(), cx, cy + rise, size, Colors.multiplyAlpha(color, a), Fonts.Align.CENTER, 0.4f * h, true);
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !enabled) {
				return false;
			}
			UiSounds.click();
			row.activate();
			return true;
		}
	}

	void reset() {
		builtFor = List.of();
	}

	private void sync(List<MenuRow> rows) {
		if (rows == builtFor) {
			return;
		}
		builtFor = rows;
		words.clear();
		for (int i = 0; i < rows.size(); i++) {
			Word word = new Word(rows.get(i));
			word.appear.set(0).animateTo(1, 520, Easing.UI, 160 + i * 60);
			words.add(word);
		}
	}

	/** The darkening behind the column, so the words read over the bright parts of the scene. */
	void drawBackground(GuiGraphicsExtractor g) {
		float w = g.guiWidth();
		float h = g.guiHeight();
		Shapes.shadow(g, w * 0.3f, h * 0.18f, w * 0.4f, h * 0.68f, h * 0.2f, h * 0.2f, Colors.argb(0.55f, 0x05040A));
	}

	void draw(GuiGraphicsExtractor g, List<MenuRow> rows, int keyboardIndex, float viewWidth, float viewHeight, float fade,
			  BiConsumer<Widget, Boolean> put) {
		sync(rows);
		float cx = viewWidth / 2;
		float top = viewHeight * 0.26f;
		int accent = Theme.accent();
		float rise = Animation.motion((1 - fade) * 14, 0);
		Fonts.draw(g, Typeface.MONO_BOLD, "MINECRAFT " + ClientInfo.MC_VERSION, cx, top + rise, 9, Colors.multiplyAlpha(accent, fade),
			Fonts.Align.CENTER, 3f, false);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, ClientInfo.NAME, cx, top + 46 + rise, 64, Theme.text(1, fade), Fonts.Align.CENTER, 0, false);
		float lineW = 90;
		Shapes.gradientRectH(g, cx - lineW, top + 88 + rise, lineW, 2, 0, Colors.withAlpha(accent, 0), Colors.withAlpha(accent, fade));
		Shapes.gradientRectH(g, cx, top + 88 + rise, lineW, 2, 0, Colors.withAlpha(accent, fade), Colors.withAlpha(accent, 0));
		float y = top + 128;
		for (int i = 0; i < words.size(); i++) {
			Word word = words.get(i);
			word.selected = i == keyboardIndex;
			word.setEnabled(rows.get(i).isEnabled());
			word.bounds(cx - 170, y, 340, 40);
			put.accept(word, true);
			y += 48;
		}
	}
}
