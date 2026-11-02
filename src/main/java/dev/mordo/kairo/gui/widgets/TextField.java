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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

import java.util.function.Consumer;
import java.util.function.Predicate;

/** Single-line text input: typing, cursor keys, select-all, copy and paste. */
public final class TextField extends Widget {
	private final StringBuilder text = new StringBuilder();
	private final String placeholder;
	private final @Nullable Icon icon;
	private int cursor;
	private boolean allSelected;
	private boolean focused;
	private int maxLength = 64;
	private Typeface typeface = Typeface.OUTFIT_MEDIUM;
	private float textSize = 12;
	private Predicate<Integer> allowedChar = c -> c >= 32 && c != 127;
	private Consumer<String> onChange = s -> {
	};
	private Consumer<String> onSubmit = s -> {
	};
	private final Animation focus = new Animation(0);

	public TextField(String placeholder, @Nullable Icon icon) {
		this.placeholder = placeholder;
		this.icon = icon;
		this.w = 220;
		this.h = 34;
	}

	public TextField maxLength(int maxLength) {
		this.maxLength = maxLength;
		return this;
	}

	public TextField mono(float size) {
		this.typeface = Typeface.MONO_MEDIUM;
		this.textSize = size;
		return this;
	}

	public TextField allow(Predicate<Integer> allowedChar) {
		this.allowedChar = allowedChar;
		return this;
	}

	public TextField onChange(Consumer<String> onChange) {
		this.onChange = onChange;
		return this;
	}

	public TextField onSubmit(Consumer<String> onSubmit) {
		this.onSubmit = onSubmit;
		return this;
	}

	public String text() {
		return text.toString();
	}

	/** Replaces the text without firing onChange. */
	public void setText(String value) {
		text.setLength(0);
		text.append(value, 0, Math.min(value.length(), maxLength));
		cursor = text.length();
		allSelected = false;
	}

	public boolean isFocused() {
		return focused;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public void onFocusChanged(boolean focused) {
		this.focused = focused;
		// SDL only sends typed characters while text input is switched on.
		Minecraft.getInstance().textInputManager().onTextInputFocusChange(this, focused);
		if (!focused) {
			allSelected = false;
		}
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		focus.animateTo(focused ? 1 : 0, 160);
		float f = focus.value();
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, Math.max(hover.value() * 0.6f, f));
		int stroke = Colors.lerp(Colors.lerp(Theme.BORDER, Theme.BORDER_HOVER, hover.value()), Colors.withAlpha(Theme.accent(), 0.7f), f);
		Shapes.rect(g, x, y, w, h, 10, Colors.multiplyAlpha(fill, fade), Colors.multiplyAlpha(stroke, fade), 1);

		float cy = y + h / 2;
		float tx = x + 12;
		if (icon != null) {
			IconRenderer.draw(g, icon, x + 19, cy, 15, Theme.text(0.55f, fade), 2f);
			tx = x + 34;
		}
		float right = x + w - 12;
		g.enableScissor((int) Math.floor(tx - 1), (int) Math.floor(y), (int) Math.ceil(right + 1), (int) Math.ceil(y + h));
		try {
			if (text.isEmpty()) {
				Fonts.draw(g, typeface, placeholder, tx, cy, textSize, Theme.text(0.4f, fade));
			}
			String value = text.toString();
			float cursorX = Fonts.width(typeface, value.substring(0, cursor), textSize);
			float scroll = Math.max(0, cursorX - (right - tx) + 2);
			if (allSelected && !text.isEmpty()) {
				float sw = Fonts.width(typeface, value, textSize);
				Shapes.rect(g, tx - scroll - 1, cy - textSize * 0.7f, sw + 2, textSize * 1.4f, 3, Colors.withAlpha(Theme.accent(), 0.35f * fade));
			}
			Fonts.draw(g, typeface, value, tx - scroll, cy, textSize, Theme.text(1, fade));
			boolean blinkOn = (System.currentTimeMillis() / 530) % 2 == 0;
			if (focused && blinkOn && !allSelected) {
				Shapes.rect(g, tx - scroll + cursorX, cy - textSize * 0.65f, 1.3f, textSize * 1.3f, 0.6f, Colors.withAlpha(Theme.accent(), fade));
			}
		} finally {
			g.disableScissor();
		}
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		allSelected = false;
		cursor = text.length();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!focused) {
			return false;
		}
		int key = event.key();
		if (event.isSelectAll()) {
			allSelected = true;
			return true;
		}
		if (event.isCopy()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(text.toString());
			return true;
		}
		if (event.isPaste()) {
			insert(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		if (key == InputConstants.KEY_RETURN || key == InputConstants.KEY_NUMPADENTER) {
			onSubmit.accept(text.toString());
			return true;
		}
		if (key == InputConstants.KEY_BACKSPACE) {
			if (allSelected) {
				clearAll();
			} else if (cursor > 0) {
				int start = text.offsetByCodePoints(cursor, -1);
				text.delete(start, cursor);
				cursor = start;
				changed();
			}
			return true;
		}
		if (key == InputConstants.KEY_DELETE) {
			if (allSelected) {
				clearAll();
			} else if (cursor < text.length()) {
				text.delete(cursor, text.offsetByCodePoints(cursor, 1));
				changed();
			}
			return true;
		}
		if (key == InputConstants.KEY_LEFT) {
			cursor = allSelected ? 0 : Math.max(0, cursor > 0 ? text.offsetByCodePoints(cursor, -1) : 0);
			allSelected = false;
			return true;
		}
		if (key == InputConstants.KEY_RIGHT) {
			cursor = allSelected ? text.length() : (cursor < text.length() ? text.offsetByCodePoints(cursor, 1) : cursor);
			allSelected = false;
			return true;
		}
		if (key == InputConstants.KEY_HOME) {
			cursor = 0;
			allSelected = false;
			return true;
		}
		if (key == InputConstants.KEY_END) {
			cursor = text.length();
			allSelected = false;
			return true;
		}
		// Swallow everything else so typing letters never triggers screen shortcuts (like P).
		return key != InputConstants.KEY_ESCAPE && key != InputConstants.KEY_TAB;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (!focused || !allowedChar.test(event.codepoint())) {
			return focused;
		}
		insert(event.codepointAsString());
		return true;
	}

	private void insert(String value) {
		if (allSelected) {
			text.setLength(0);
			cursor = 0;
			allSelected = false;
		}
		StringBuilder clean = new StringBuilder();
		value.codePoints().filter(allowedChar::test).forEach(clean::appendCodePoint);
		int room = maxLength - text.length();
		if (room <= 0 || clean.isEmpty()) {
			return;
		}
		String add = clean.length() > room ? clean.substring(0, room) : clean.toString();
		text.insert(cursor, add);
		cursor += add.length();
		changed();
	}

	private void clearAll() {
		text.setLength(0);
		cursor = 0;
		allSelected = false;
		changed();
	}

	private void changed() {
		onChange.accept(text.toString());
	}
}
