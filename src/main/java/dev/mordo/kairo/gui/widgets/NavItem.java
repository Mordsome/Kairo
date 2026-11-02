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
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Sidebar entry: icon, label and an optional count; highlighted with the accent when selected. */
public final class NavItem extends Widget {
	private final String label;
	private final @Nullable Icon icon;
	private final @Nullable Supplier<String> count;
	private final BooleanSupplier selected;
	private final Runnable action;
	private final Animation active = new Animation(0);

	public NavItem(String label, @Nullable Icon icon, @Nullable Supplier<String> count, BooleanSupplier selected, Runnable action) {
		this.label = label;
		this.icon = icon;
		this.count = count;
		this.selected = selected;
		this.action = action;
		this.h = 38;
	}

	private boolean header;

	/** A small group label between nav items (not clickable). */
	public static NavItem header(String label) {
		NavItem item = new NavItem(label, null, null, () -> false, () -> {
		});
		item.header = true;
		item.h = 24;
		return item;
	}

	public boolean isHeader() {
		return header;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		if (header) {
			Fonts.draw(g, Typeface.MONO_BOLD, label, x + 4, y + h / 2 + 3, 8, Theme.text(0.36f, fade), Fonts.Align.LEFT, 2f, false);
			return;
		}
		active.animateTo(selected.getAsBoolean() ? 1 : 0, 220);
		float on = active.value();
		int fill = Colors.lerp(Colors.lerp(0x00FFFFFF, Theme.SURFACE, hover.value()), Colors.withAlpha(Theme.accent(), 0.15f), on);
		int stroke = Colors.lerp(0x00FFFFFF, Colors.withAlpha(Theme.accent(), 0.28f), on);
		Shapes.rect(g, x, y, w, h, 10, Colors.multiplyAlpha(fill, fade), Colors.multiplyAlpha(stroke, fade), stroke == 0 ? 0 : 1);
		float cy = y + h / 2;
		float tx = x + 14;
		int textColor = Colors.lerp(Theme.text(0.72f), Theme.TEXT, Math.max(on, hover.value() * 0.6f));
		if (icon != null) {
			int iconColor = Colors.lerp(textColor, Theme.accent(), on);
			IconRenderer.draw(g, icon, tx + 8, cy, 16, Colors.multiplyAlpha(iconColor, fade), 2f);
			tx += 26;
		}
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, label, tx, cy, 12.5f, Colors.multiplyAlpha(textColor, fade));
		if (count != null) {
			Fonts.draw(g, Typeface.MONO_MEDIUM, count.get(), x + w - 14, cy, 10, Theme.text(0.45f, fade), Fonts.Align.RIGHT, 0, false);
		}
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT || header) {
			return false;
		}
		action.run();
		return true;
	}
}
