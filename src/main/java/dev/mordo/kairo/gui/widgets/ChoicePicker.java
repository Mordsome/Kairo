package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.settings.Choice;
import dev.mordo.kairo.core.settings.ChoiceSetting;
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
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Editor for a {@link ChoiceSetting}: a dropdown (with group headers, scrolling for long lists of files) and, if the
 * setting has them, a play button (preview) and an "add" button (import a file through the system file dialog).
 */
public final class ChoicePicker extends Widget {
	private static final float FIELD_W = 176;
	private static final float BUTTON = 30;
	private static final float GAP = 6;

	private final ChoiceSetting setting;
	private final Binding<String> value;
	private final WidgetHost host;
	private final Animation chevron = new Animation(0);
	private final Animation playHover = new Animation(0);
	private final Animation addHover = new Animation(0);
	private @Nullable ListPopup popup;

	public ChoicePicker(ChoiceSetting setting, Binding<String> value, WidgetHost host) {
		this.setting = setting;
		this.value = value;
		this.host = host;
		this.w = preferredWidth();
		this.h = 30;
	}

	@Override
	public float preferredWidth() {
		return FIELD_W + (setting.preview() != null ? BUTTON + GAP : 0) + (setting.browse() != null ? BUTTON + GAP : 0);
	}

	private float fieldX() {
		return x + w - FIELD_W;
	}

	private float playX() {
		return x + (setting.browse() != null ? BUTTON + GAP : 0);
	}

	private float addX() {
		return x;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		boolean open = popup != null && !popup.isClosing();
		chevron.animateTo(open ? 1 : 0, 200);
		float alpha = fade * (enabled ? 1 : 0.4f);
		float fx = fieldX();
		boolean overField = mx >= fx && mx <= fx + FIELD_W && my >= y && my <= y + h && hover.target() > 0;
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, Math.max(overField ? hover.value() : 0, open ? 1 : 0));
		Shapes.rect(g, fx, y, FIELD_W, h, 8, Colors.multiplyAlpha(fill, alpha), Colors.multiplyAlpha(Theme.BORDER, alpha), 1);
		String label = Fonts.ellipsize(Typeface.OUTFIT_MEDIUM, setting.label(value.get()), 11.5f, FIELD_W - 40);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, label, fx + 12, y + h / 2, 11.5f, Theme.text(1, alpha));
		float cx = fx + FIELD_W - 16;
		float cy = y + h / 2;
		float flip = 1 - 2 * chevron.value();
		int chevronColor = Theme.text(0.6f, alpha);
		Shapes.line(g, cx - 4, cy - 2 * flip, cx, cy + 2 * flip, 1.6f, chevronColor);
		Shapes.line(g, cx, cy + 2 * flip, cx + 4, cy - 2 * flip, 1.6f, chevronColor);

		if (setting.preview() != null) {
			iconButton(g, playX(), Icon.PLAY, playHover, mx, my, alpha);
		}
		if (setting.browse() != null) {
			iconButton(g, addX(), Icon.PLUS, addHover, mx, my, alpha);
		}
	}

	private void iconButton(GuiGraphicsExtractor g, float bx, Icon icon, Animation anim, float mx, float my, float alpha) {
		boolean over = hover.target() > 0 && mx >= bx && mx <= bx + BUTTON && my >= y && my <= y + h;
		anim.animateTo(over ? 1 : 0, 140);
		int fill = Colors.lerp(Theme.SURFACE, Colors.withAlpha(Theme.accent(), 0.22f), anim.value());
		Shapes.rect(g, bx, y, BUTTON, h, 8, Colors.multiplyAlpha(fill, alpha), Colors.multiplyAlpha(Theme.BORDER, alpha), 1);
		int color = Colors.lerp(Theme.text(0.75f), Theme.accent(), anim.value());
		IconRenderer.draw(g, icon, bx + BUTTON / 2, y + h / 2, icon == Icon.PLAY ? 12 : 14, Colors.multiplyAlpha(color, alpha), 2.2f);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (setting.preview() != null && mx >= playX() && mx <= playX() + BUTTON) {
			setting.preview().accept(value.get());
			return true;
		}
		if (setting.browse() != null && mx >= addX() && mx <= addX() + BUTTON) {
			UiSounds.click();
			setting.browse().accept(value::set);
			return true;
		}
		if (mx >= fieldX()) {
			UiSounds.click();
			popup = new ListPopup(setting.options());
			host.openPopup(popup, this);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseScrolled(float mx, float my, float amount) {
		if (mx < fieldX()) {
			return false;
		}
		List<Choice> options = setting.options();
		int current = indexOf(options, value.get());
		int next = Math.floorMod(current + (amount > 0 ? -1 : 1), options.size());
		value.set(options.get(next).id());
		return true;
	}

	private static int indexOf(List<Choice> options, String id) {
		for (int i = 0; i < options.size(); i++) {
			if (options.get(i).id().equals(id)) {
				return i;
			}
		}
		return 0;
	}

	/** The list: rows plus small group headers ("BUILT-IN", "YOUR FILES"), scrolling when tall. */
	private final class ListPopup extends Popup {
		private static final float ROW = 28;
		private static final float HEADER = 24;
		private static final float MAX_H = 300;

		private record Line(@Nullable Choice choice, String header, float y) {
		}

		private final List<Line> lines = new ArrayList<>();
		private final List<Animation> rowHover = new ArrayList<>();
		private final float contentH;
		private final ScrollState scroll = new ScrollState();

		ListPopup(List<Choice> options) {
			String group = "";
			float cy = 4;
			for (Choice c : options) {
				if (!c.group().isEmpty() && !c.group().equals(group)) {
					group = c.group();
					lines.add(new Line(null, group, cy));
					cy += HEADER;
				}
				lines.add(new Line(c, "", cy));
				rowHover.add(new Animation(0));
				cy += ROW;
			}
			contentH = cy + 4;
			this.w = Math.max(FIELD_W, 200);
			this.h = Math.min(contentH, MAX_H);
			scroll.setSizes(contentH, h);
			// Start scrolled to the selected item.
			for (Line line : lines) {
				if (line.choice() != null && line.choice().id().equals(value.get())) {
					scroll.jumpTo(line.y() - h / 2);
				}
			}
		}

		@Override
		public void placeNear(Widget anchor, float viewWidth, float viewHeight) {
			super.placeNear(anchor, viewWidth, viewHeight);
			x = Math.clamp(fieldX() + FIELD_W - w, 12, viewWidth - w - 12);
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			float a = open.value();
			float drop = Animation.motion((1 - a) * -6, 0);
			Shapes.shadow(g, x, y + 6 + drop, w, h, 10, 16, Colors.argb(0.45f * a, 0));
			Shapes.rect(g, x, y + drop, w, h, 10, Colors.multiplyAlpha(Theme.POPUP, a), Colors.argb(0.14f * a, 0xFFFFFF), 1);
			g.enableScissor((int) Math.floor(x), (int) Math.floor(y + drop + 2), (int) Math.ceil(x + w), (int) Math.ceil(y + drop + h - 2));
			try {
				float off = scroll.value();
				int row = 0;
				for (Line line : lines) {
					float ry = y + line.y() - off + drop;
					if (line.choice() == null) {
						if (ry + HEADER > y && ry < y + h) {
							Fonts.draw(g, Typeface.MONO_BOLD, line.header(), x + 14, ry + HEADER / 2 + 2, 7.5f, Theme.text(0.4f, a),
								Fonts.Align.LEFT, 1.6f, false);
						}
						continue;
					}
					Animation hv = rowHover.get(row++);
					boolean over = mx >= x && mx <= x + w && my >= ry && my < ry + ROW && my >= y && my <= y + h;
					hv.animateTo(over ? 1 : 0, 120);
					if (ry + ROW < y || ry > y + h) {
						continue;
					}
					boolean selected = line.choice().id().equals(value.get());
					if (hv.value() > 0.01f) {
						Shapes.rect(g, x + 4, ry, w - 8, ROW, 7, Colors.argb(0.07f * hv.value() * a, 0xFFFFFF));
					}
					int color = selected ? Colors.multiplyAlpha(Theme.accent(), a) : Theme.text(0.9f, a);
					String text = Fonts.ellipsize(Typeface.OUTFIT_MEDIUM, line.choice().label(), 11.5f, w - 46);
					Fonts.draw(g, Typeface.OUTFIT_MEDIUM, text, x + 14, ry + ROW / 2, 11.5f, color);
					if (selected) {
						IconRenderer.draw(g, Icon.CHECK, x + w - 18, ry + ROW / 2, 13, color, 2.2f);
					}
				}
			} finally {
				g.disableScissor();
			}
			if (contentH > h) {
				scroll.drawBar(g, x + w - 4, y + 4, a);
			}
		}

		@Override
		public boolean mouseScrolled(float mx, float my, float amount) {
			scroll.scroll(amount);
			return true;
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			float local = my - y + scroll.value();
			for (Line line : lines) {
				if (line.choice() != null && local >= line.y() && local < line.y() + ROW) {
					value.set(line.choice().id());
					UiSounds.click();
					break;
				}
			}
			host.closePopup();
			return true;
		}
	}
}
