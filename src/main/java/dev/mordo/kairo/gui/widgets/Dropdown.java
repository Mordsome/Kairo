package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Shows the current choice; clicking opens a list popup. Scrolling over it cycles through the options. */
public final class Dropdown<E extends Enum<E>> extends Widget {
	private final E[] values;
	private final Binding<E> value;
	private final WidgetHost host;
	private final Animation chevron = new Animation(0);
	private ListPopup popup;

	public Dropdown(E[] values, Binding<E> value, WidgetHost host) {
		this.values = values;
		this.value = value;
		this.host = host;
		this.w = 180;
		this.h = 30;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		boolean open = popup != null && !popup.isClosing();
		chevron.animateTo(open ? 1 : 0, 200);
		float alpha = fade * (enabled ? 1 : 0.4f);
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, Math.max(hover.value(), open ? 1 : 0));
		int stroke = Colors.lerp(Theme.BORDER, Theme.BORDER_HOVER, hover.value());
		Shapes.rect(g, x, y, w, h, 8, Colors.multiplyAlpha(fill, alpha), Colors.multiplyAlpha(stroke, alpha), 1);
		String label = Fonts.ellipsize(Typeface.OUTFIT_MEDIUM, EnumSetting.label(value.get()), 11.5f, w - 40);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, label, x + 12, y + h / 2, 11.5f, Theme.text(1, alpha));
		// Chevron that flips when open.
		float cx = x + w - 16;
		float cy = y + h / 2;
		float flip = 1 - 2 * chevron.value();
		int color = Theme.text(0.6f, alpha);
		Shapes.line(g, cx - 4, cy - 2 * flip, cx, cy + 2 * flip, 1.6f, color);
		Shapes.line(g, cx, cy + 2 * flip, cx + 4, cy - 2 * flip, 1.6f, color);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		popup = new ListPopup();
		host.openPopup(popup, this);
		return true;
	}

	@Override
	public boolean mouseScrolled(float mx, float my, float amount) {
		int i = value.get().ordinal() + (amount > 0 ? -1 : 1);
		value.set(values[Math.floorMod(i, values.length)]);
		return true;
	}

	private final class ListPopup extends Popup {
		private static final float ROW = 28;
		private final Animation[] rowHover = new Animation[values.length];

		ListPopup() {
			for (int i = 0; i < rowHover.length; i++) {
				rowHover[i] = new Animation(0);
			}
			this.w = Math.max(Dropdown.this.w, 160);
			this.h = values.length * ROW + 8;
		}

		@Override
		public void placeNear(Widget anchor, float viewWidth, float viewHeight) {
			super.placeNear(anchor, viewWidth, viewHeight);
			x = Math.clamp(anchor.x, 12, viewWidth - w - 12); // left-aligned with the button
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			float a = open.value();
			float drop = Animation.motion((1 - a) * -6, 0);
			Shapes.shadow(g, x, y + 6 + drop, w, h, 10, 16, Colors.argb(0.45f * a, 0));
			Shapes.rect(g, x, y + drop, w, h, 10, Colors.multiplyAlpha(Theme.POPUP, a), Colors.argb(0.14f * a, 0xFFFFFF), 1);
			for (int i = 0; i < values.length; i++) {
				float ry = y + 4 + i * ROW + drop;
				boolean over = mx >= x && mx <= x + w && my >= ry && my < ry + ROW;
				rowHover[i].animateTo(over ? 1 : 0, 120);
				boolean selected = values[i] == value.get();
				if (rowHover[i].value() > 0.01f) {
					Shapes.rect(g, x + 4, ry, w - 8, ROW, 7, Colors.argb(0.07f * rowHover[i].value() * a, 0xFFFFFF));
				}
				int color = selected ? Colors.multiplyAlpha(Theme.accent(), a) : Theme.text(0.9f, a);
				Fonts.draw(g, Typeface.OUTFIT_MEDIUM, EnumSetting.label(values[i]), x + 14, ry + ROW / 2, 11.5f, color);
				if (selected) {
					IconRenderer.draw(g, Icon.CHECK, x + w - 18, ry + ROW / 2, 13, color, 2.2f);
				}
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			int i = (int) ((my - y - 4) / ROW);
			if (i >= 0 && i < values.length) {
				value.set(values[i]);
			}
			host.closePopup();
			return true;
		}
	}
}
