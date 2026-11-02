package dev.mordo.kairo.gui.widgets;

import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Easing;

/** A floating widget drawn above everything (dropdown lists, colour picker). Clicking outside closes it. */
public abstract class Popup extends Widget {
	protected final Animation open = new Animation(0);
	protected WidgetHost host;
	private boolean closing;

	/** True if {@code widget} lives inside this popup (so closing the popup drops its focus). */
	protected boolean ownsFocus(Widget widget) {
		return false;
	}

	protected Popup() {
		open.animateTo(1, 180, Easing.UI);
	}

	void beginClose() {
		closing = true;
		open.animateTo(0, 120, Easing.OUT_CUBIC);
	}

	boolean isClosing() {
		return closing;
	}

	boolean isGone() {
		return closing && open.isDone();
	}

	/** Places the popup next to an anchor rect, flipping above it if there's no room below. */
	public void placeNear(Widget anchor, float viewWidth, float viewHeight) {
		float px = Math.clamp(anchor.x + anchor.w - w, 12, viewWidth - w - 12);
		float below = anchor.y + anchor.h + 6;
		float py = below + h <= viewHeight - 12 ? below : anchor.y - h - 6;
		bounds(px, Math.max(12, py), w, h);
	}
}
