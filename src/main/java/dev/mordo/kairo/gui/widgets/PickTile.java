package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/** A choice in a tile grid: a small (optionally animated) picture, a name, and a tick when it's the current one. */
public final class PickTile extends Widget {
	/** Draws the picture into the square (x, y, size); {@code t} loops 0..1 while the tile is hovered or chosen. */
	public interface Art {
		void draw(GuiGraphicsExtractor g, float x, float y, float size, float t, float fade, boolean on);
	}

	private final String label;
	private final Art art;
	private final BooleanSupplier selected;
	private final Runnable action;
	private final Animation pick = new Animation(0);
	private float phase;
	private long lastFrame;

	public PickTile(String label, Art art, BooleanSupplier selected, Runnable action) {
		this.label = label;
		this.art = art;
		this.selected = selected;
		this.action = action;
	}

	/** A tile whose picture is an icon in a colour. */
	public static PickTile icon(String label, Icon icon, int color, BooleanSupplier selected, Runnable action) {
		return new PickTile(label, (g, x, y, s, t, fade, on) ->
			IconRenderer.draw(g, icon, x + s / 2, y + s / 2, s * 0.5f, Colors.multiplyAlpha(color, fade), 1.9f), selected, action);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		boolean on = selected.getAsBoolean();
		pick.animateTo(on ? 1 : 0, 200);
		float p = pick.value();
		float hv = hover.value();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		if (hv > 0.05f || on) {
			phase = (phase + dt / 1.4f) % 1f;
		} else {
			phase = 0;
		}
		int accent = Theme.accent();
		int fill = Colors.lerp(Colors.argb(0.05f + 0.04f * hv, 0xFFFFFF), Colors.withAlpha(accent, 0.16f), p);
		int stroke = Colors.lerp(Colors.argb(0.10f + 0.15f * hv, 0xFFFFFF), Colors.withAlpha(accent, 0.85f), p);
		Shapes.rect(g, x, y, w, h, 12, Colors.multiplyAlpha(fill, fade), Colors.multiplyAlpha(stroke, fade), 1 + p * 0.75f);
		float size = h - 14;
		float ax = x + 7;
		float ay = y + 7;
		Shapes.rect(g, ax, ay, size, size, 9, Colors.multiplyAlpha(Colors.lerp(0x14FFFFFF, Colors.withAlpha(accent, 0.18f), p), fade));
		art.draw(g, ax, ay, size, phase, fade, on);
		float tx = ax + size + 10;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, label, 12.5f, w - (tx - x) - 22), tx, y + h / 2, 12.5f,
			Theme.text(1, fade));
		if (p > 0.01f) {
			Shapes.circle(g, x + w - 14, y + 14, 6.5f * p, Colors.multiplyAlpha(accent, fade));
			IconRenderer.draw(g, Icon.CHECK, x + w - 14, y + 14, 8.5f * p, Colors.multiplyAlpha(Theme.DARK_TEXT, fade), 3f);
		}
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) {
			return false;
		}
		UiSounds.click();
		action.run();
		return true;
	}

	/**
	 * Lays out a titled grid of tiles for a {@link SettingsList.Section} and returns the height used.
	 */
	public static float grid(GuiGraphicsExtractor g, String title, List<PickTile> tiles, float x, float y, float w, float fade, Consumer<Widget> put) {
		Fonts.draw(g, Typeface.MONO_BOLD, title, x + 2, y + 8, 8.5f, Theme.text(0.45f, fade), Fonts.Align.LEFT, 2f, false);
		float top = y + 22;
		float gap = 8;
		int cols = Math.max(2, (int) ((w + gap) / (150 + gap)));
		float tw = (w - gap * (cols - 1)) / cols;
		float th = 50;
		for (int i = 0; i < tiles.size(); i++) {
			PickTile tile = tiles.get(i);
			tile.bounds(x + (i % cols) * (tw + gap), top + (i / cols) * (th + gap), tw, th);
			put.accept(tile);
		}
		int rows = (tiles.size() + cols - 1) / cols;
		return 22 + rows * (th + gap) + 10;
	}
}
