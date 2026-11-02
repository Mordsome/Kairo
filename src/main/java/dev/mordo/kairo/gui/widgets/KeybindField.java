package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.Locale;

/**
 * Click, then press a key (or a middle/side mouse button) to bind it.
 * Esc cancels, Backspace or Delete unbinds.
 */
public final class KeybindField extends Widget {
	private final KeybindSetting setting;
	private boolean listening;

	public KeybindField(KeybindSetting setting) {
		this.setting = setting;
		this.w = 128;
		this.h = 30;
	}

	@Override
	public boolean focusable() {
		return true;
	}

	@Override
	public boolean wantsAllClicks() {
		return listening;
	}

	@Override
	public void onFocusChanged(boolean focused) {
		if (!focused) {
			listening = false;
		}
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float pulse = listening ? 0.55f + 0.45f * (float) Math.sin(System.nanoTime() / 1.6e8) : 0;
		int fill = listening ? Colors.withAlpha(Theme.accent(), 0.14f) : Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover.value());
		int stroke = listening ? Colors.withAlpha(Theme.accent(), 0.5f + 0.5f * pulse) : Colors.lerp(Theme.BORDER, Theme.BORDER_HOVER, hover.value());
		Shapes.rect(g, x, y, w, h, 8, Colors.multiplyAlpha(fill, fade), Colors.multiplyAlpha(stroke, fade), 1);
		String label;
		int color;
		if (listening) {
			label = "Press a key…";
			color = Colors.multiplyAlpha(Theme.accent(), fade);
		} else if (setting.get().isNone()) {
			label = "None";
			color = Theme.text(0.4f, fade);
		} else {
			label = setting.get().displayName().getString().toUpperCase(Locale.ROOT);
			color = Theme.text(1, fade);
		}
		Typeface face = listening ? Typeface.OUTFIT_MEDIUM : Typeface.MONO_BOLD;
		float size = listening ? 11.5f : 10;
		label = Fonts.ellipsize(face, label, size, w - 16);
		Fonts.draw(g, face, label, x + w / 2, y + h / 2, size, color, Fonts.Align.CENTER, listening ? 0 : 1f, false);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (!listening) {
			if (button == InputConstants.MOUSE_BUTTON_LEFT && contains(mx, my)) {
				listening = true;
				return true;
			}
			return false;
		}
		// While listening: middle and side buttons bind; left/right click just cancels.
		if (button != InputConstants.MOUSE_BUTTON_LEFT && button != InputConstants.MOUSE_BUTTON_RIGHT) {
			setting.set(Keybind.mouse(button));
		}
		listening = false;
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (!listening) {
			return false;
		}
		int key = event.key();
		if (key == InputConstants.KEY_BACKSPACE || key == InputConstants.KEY_DELETE) {
			setting.set(Keybind.NONE);
		} else if (key != InputConstants.KEY_ESCAPE) {
			setting.set(Keybind.keyboard(key));
		}
		listening = false;
		return true;
	}
}
