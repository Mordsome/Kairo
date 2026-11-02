package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.gui.widgets.WidgetHost;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

/**
 * One view inside the {@link MenuScreen} (the ring, the Mods panel, ...). Pages animate themselves in and out;
 * the menu keeps drawing a leaving page until it reports {@link #isHidden()}, so transitions overlap.
 */
public abstract class Page {
	protected final MenuScreen menu;
	protected final WidgetHost host;

	protected Page(MenuScreen menu) {
		this.menu = menu;
		this.host = menu.host();
	}

	/** Start the intro animation. */
	public abstract void show();

	/** Start the exit animation. */
	public abstract void hide();

	/** True once the exit animation has finished. */
	public abstract boolean isHidden();

	/**
	 * Draw the page in reference units. {@code interactive} is false while the page is leaving
	 * (it must not draw widgets through the host then).
	 */
	public abstract void render(GuiGraphicsExtractor g, float mx, float my, float viewWidth, float viewHeight, boolean interactive);

	/** Clicks that no widget took. */
	public boolean onClick(float mx, float my, int button) {
		return false;
	}

	/** Mouse dragged with a button held (after a click this page took). */
	public boolean onDrag(float mx, float my, int button) {
		return false;
	}

	public boolean onRelease(float mx, float my, int button) {
		return false;
	}

	public boolean onScroll(float mx, float my, float amount) {
		return false;
	}

	public boolean keyPressed(KeyEvent event) {
		return false;
	}

	public boolean charTyped(CharacterEvent event) {
		return false;
	}

	/** Esc: return true if the page handled it (e.g. went back a level); otherwise the menu closes. */
	public boolean onEscape() {
		return false;
	}
}
