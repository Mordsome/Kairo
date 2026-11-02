package dev.mordo.kairo.mods.shulker;

import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.world.item.ItemStack;

/** Draws {@link ShulkerContents} as a 9-wide slot grid tinted with the box colour, with item counts. */
public final class ShulkerTooltip implements ClientTooltipComponent {
	private static final int COLUMNS = 9;
	private static final int SLOT = 18;

	private final ShulkerContents contents;

	public ShulkerTooltip(ShulkerContents contents) {
		this.contents = contents;
	}

	private int rows() {
		return Math.max(1, (contents.items().size() + COLUMNS - 1) / COLUMNS);
	}

	@Override
	public int getHeight(Font font) {
		return rows() * SLOT + 6;
	}

	@Override
	public int getWidth(Font font) {
		return COLUMNS * SLOT + 2;
	}

	@Override
	public void extractImage(Font font, int x, int y, int w, int h, GuiGraphicsExtractor g) {
		draw(g, font, x, y + 2);
	}

	/** Also used for the hotbar preview. */
	public void draw(GuiGraphicsExtractor g, Font font, int x, int y) {
		int tint = contents.tint();
		int width = COLUMNS * SLOT + 2;
		int height = rows() * SLOT + 2;
		Shapes.rect(g, x - 1, y - 1, width, height, 3, Colors.withAlpha(Colors.lerp(tint, 0xFF000000, 0.45f), 0.85f),
			Colors.withAlpha(tint, 0.9f), 1);
		for (int i = 0; i < contents.items().size(); i++) {
			int sx = x + (i % COLUMNS) * SLOT;
			int sy = y + (i / COLUMNS) * SLOT;
			Shapes.rect(g, sx + 1, sy + 1, SLOT - 2, SLOT - 2, 2, Colors.withAlpha(Colors.lerp(tint, 0xFF000000, 0.65f), 0.75f));
			ItemStack stack = contents.items().get(i);
			if (!stack.isEmpty()) {
				g.item(stack, sx + 1, sy + 1);
				String count = stack.getCount() > 1 ? Integer.toString(stack.getCount()) : null;
				g.itemDecorations(font, stack, sx + 1, sy + 1, count);
			}
		}
	}
}
