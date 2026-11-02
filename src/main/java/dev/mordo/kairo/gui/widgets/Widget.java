package dev.mordo.kairo.gui.widgets;

import dev.mordo.kairo.render.Animation;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * A UI element in reference units. Layout is set every frame by the owner ({@link #bounds}),
 * then {@link WidgetHost#render} draws it and remembers it for input on the next events.
 */
public abstract class Widget {
	public float x;
	public float y;
	public float w;
	public float h;
	protected boolean enabled = true;
	protected final Animation hover = new Animation(0);

	public Widget bounds(float x, float y, float w, float h) {
		this.x = x;
		this.y = y;
		this.w = w;
		this.h = h;
		return this;
	}

	public boolean contains(float px, float py) {
		return px >= x && px <= x + w && py >= y && py <= y + h;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public boolean isEnabled() {
		return enabled;
	}

	/** Called by the host; updates hover then draws. {@code fade} is the overall opacity 0..1. */
	public final void render(GuiGraphicsExtractor g, float mx, float my, float fade, boolean hoverAllowed) {
		hover.animateTo(enabled && hoverAllowed && contains(mx, my) ? 1 : 0, 140);
		draw(g, mx, my, fade);
	}

	protected abstract void draw(GuiGraphicsExtractor g, float mx, float my, float fade);

	/** Natural width for row layouts. */
	public float preferredWidth() {
		return w;
	}

	public float preferredHeight() {
		return h;
	}

	/** True if a click on this widget should give it keyboard focus. */
	public boolean focusable() {
		return false;
	}

	public void onFocusChanged(boolean focused) {
	}

	/** While focused, receive every click, even outside the widget (used while listening for a key). */
	public boolean wantsAllClicks() {
		return false;
	}

	public boolean mouseClicked(float mx, float my, int button) {
		return false;
	}

	public boolean mouseReleased(float mx, float my, int button) {
		return false;
	}

	public boolean mouseDragged(float mx, float my, int button) {
		return false;
	}

	public boolean mouseScrolled(float mx, float my, float amount) {
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return false;
	}
}
