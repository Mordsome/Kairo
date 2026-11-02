package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns focus, mouse capture and the open popup for one screen.
 *
 * <p>Pages lay widgets out and draw them through {@link #render} every frame; the host remembers which widgets
 * were on screen (and where) and routes the next input events to them. That keeps layout code simple:
 * whatever was drawn last frame is what you can click.
 */
public final class WidgetHost {
	private List<Widget> current = new ArrayList<>();
	private List<Widget> previous = new ArrayList<>();
	private @Nullable Widget focused;
	private @Nullable Widget captured;
	private @Nullable Popup popup;
	private float viewWidth = 1440;
	private float viewHeight = 810;

	/** Call once at the start of each frame. */
	public void beginFrame(float viewWidth, float viewHeight) {
		this.viewWidth = viewWidth;
		this.viewHeight = viewHeight;
		List<Widget> swap = previous;
		previous = current;
		current = swap;
		current.clear();
		if (popup != null && popup.isGone()) {
			popup = null;
		}
	}

	public void render(Widget widget, GuiGraphicsExtractor g, float mx, float my, float fade) {
		boolean hoverAllowed = popup == null || popup.isClosing();
		widget.render(g, mx, my, fade, hoverAllowed && (captured == null || captured == widget));
		current.add(widget);
	}

	/** Draws the popup on top of everything; call last. */
	public void renderPopup(GuiGraphicsExtractor g, float mx, float my) {
		if (popup != null) {
			popup.render(g, mx, my, 1, true);
		}
	}

	// ---- focus and popups ----

	public void focus(@Nullable Widget widget) {
		if (focused == widget) {
			return;
		}
		if (focused != null) {
			focused.onFocusChanged(false);
		}
		focused = widget;
		if (widget != null) {
			widget.onFocusChanged(true);
		}
	}

	public boolean isFocused(Widget widget) {
		return focused == widget;
	}

	public @Nullable Widget focused() {
		return focused;
	}

	public void openPopup(Popup newPopup, Widget anchor) {
		closePopup();
		newPopup.host = this;
		newPopup.placeNear(anchor, viewWidth, viewHeight);
		popup = newPopup;
	}

	public void closePopup() {
		if (popup != null && !popup.isClosing()) {
			popup.beginClose();
			if (focused == popup || (focused != null && popup.ownsFocus(focused))) {
				focus(null);
			}
		}
	}

	public boolean hasPopup() {
		return popup != null && !popup.isClosing();
	}

	/** Drops focus and popups (e.g. when the page changes). */
	public void reset() {
		focus(null);
		captured = null;
		if (popup != null) {
			popup.beginClose();
		}
	}

	// ---- input ----

	public boolean mouseClicked(float mx, float my, int button) {
		if (hasPopup()) {
			if (popup.contains(mx, my)) {
				popup.mouseClicked(mx, my, button);
				captured = popup;
			} else {
				closePopup();
			}
			return true;
		}
		// A widget that is listening for input (e.g. a keybind field) gets the click wherever it is.
		if (focused != null && focused.wantsAllClicks()) {
			return focused.mouseClicked(mx, my, button);
		}
		for (int i = previous.size() - 1; i >= 0; i--) {
			Widget widget = previous.get(i);
			if (widget.isEnabled() && widget.contains(mx, my) && widget.mouseClicked(mx, my, button)) {
				focus(widget.focusable() ? widget : null);
				captured = widget;
				return true;
			}
		}
		focus(null);
		return false;
	}

	public boolean mouseReleased(float mx, float my, int button) {
		Widget target = captured;
		captured = null;
		return target != null && target.mouseReleased(mx, my, button);
	}

	public boolean mouseDragged(float mx, float my, int button) {
		return captured != null && captured.mouseDragged(mx, my, button);
	}

	public boolean mouseScrolled(float mx, float my, float amount) {
		if (hasPopup()) {
			if (popup.contains(mx, my)) {
				popup.mouseScrolled(mx, my, amount);
			}
			return true; // nothing behind a popup scrolls
		}
		for (int i = previous.size() - 1; i >= 0; i--) {
			Widget widget = previous.get(i);
			if (widget.isEnabled() && widget.contains(mx, my) && widget.mouseScrolled(mx, my, amount)) {
				return true;
			}
		}
		return false;
	}

	/** Esc closes the popup first, then drops focus. Returns true if it used the key. */
	public boolean keyPressed(KeyEvent event) {
		if (focused != null && focused.keyPressed(event)) {
			return true;
		}
		if (hasPopup()) {
			if (event.key() == InputConstants.KEY_ESCAPE) {
				closePopup();
				return true;
			}
			return popup.keyPressed(event);
		}
		if (focused != null && event.key() == InputConstants.KEY_ESCAPE) {
			focus(null);
			return true;
		}
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return focused != null && focused.charTyped(event);
	}
}
