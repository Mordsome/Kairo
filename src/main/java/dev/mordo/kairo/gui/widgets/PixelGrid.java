package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.settings.TextSetting;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Paint a square pixel grid stored as a string of '0'/'1' (row by row).
 * Left-drag paints, right-drag erases.
 */
public final class PixelGrid extends Widget {
	private static final float CELL = 11;

	private final TextSetting value;
	private final int size;
	private int paint = -1; // 1 = painting, 0 = erasing, -1 = idle

	public PixelGrid(TextSetting value, int size) {
		this.value = value;
		this.size = size;
		this.w = size * CELL + 2;
		this.h = size * CELL + 2;
	}

	private boolean filled(String bits, int i) {
		return i < bits.length() && bits.charAt(i) == '1';
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		String bits = value.get();
		Shapes.rect(g, x, y, w, h, 6, Colors.multiplyAlpha(0x33000000, fade), Colors.multiplyAlpha(Theme.BORDER, fade), 1);
		int center = size / 2;
		for (int row = 0; row < size; row++) {
			for (int col = 0; col < size; col++) {
				float cx = x + 1 + col * CELL;
				float cy = y + 1 + row * CELL;
				boolean on = filled(bits, row * size + col);
				int color = on ? Theme.accent()
					: (row == center || col == center) ? 0x14FFFFFF : 0x08FFFFFF;
				Shapes.rect(g, cx + 0.75f, cy + 0.75f, CELL - 1.5f, CELL - 1.5f, 2, Colors.multiplyAlpha(color, fade));
			}
		}
		int hoverCell = cellAt(mx, my);
		if (hoverCell >= 0) {
			float cx = x + 1 + (hoverCell % size) * CELL;
			float cy = y + 1 + (hoverCell / size) * CELL;
			Shapes.outline(g, cx + 0.25f, cy + 0.25f, CELL - 0.5f, CELL - 0.5f, 2.5f, Theme.text(0.7f, fade), 1);
		}
	}

	private int cellAt(float mx, float my) {
		int col = (int) Math.floor((mx - x - 1) / CELL);
		int row = (int) Math.floor((my - y - 1) / CELL);
		if (col < 0 || row < 0 || col >= size || row >= size) {
			return -1;
		}
		return row * size + col;
	}

	private void set(int cell, boolean on) {
		String bits = value.get();
		StringBuilder sb = new StringBuilder(bits.length() == size * size ? bits : "0".repeat(size * size));
		sb.setCharAt(cell, on ? '1' : '0');
		value.set(sb.toString());
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		int cell = cellAt(mx, my);
		if (cell < 0) {
			return false;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT) {
			paint = filled(value.get(), cell) ? 0 : 1; // first click decides paint or erase
		} else if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			paint = 0;
		} else {
			return false;
		}
		set(cell, paint == 1);
		return true;
	}

	@Override
	public boolean mouseDragged(float mx, float my, int button) {
		int cell = cellAt(mx, my);
		if (paint >= 0 && cell >= 0) {
			set(cell, paint == 1);
		}
		return paint >= 0;
	}

	@Override
	public boolean mouseReleased(float mx, float my, int button) {
		paint = -1;
		return true;
	}
}
