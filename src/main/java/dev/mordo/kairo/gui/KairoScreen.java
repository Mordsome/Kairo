package dev.mordo.kairo.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Base for the client's own screens.
 * <ul>
 *     <li>Content is laid out in reference units ({@link RefSpace}); mouse input arrives converted.</li>
 *     <li>Closing can be animated: {@link #close(Screen)} starts it, the screen is swapped once
 *     {@link #isCloseAnimationDone()} says so.</li>
 *     <li>The game keeps running behind (not a pause screen) and vanilla's background is skipped.</li>
 * </ul>
 */
public abstract class KairoScreen extends Screen {
	/** GUI pixels per reference unit. */
	protected float unit = 1;
	/** Screen size in reference units. */
	protected float viewWidth = RefSpace.REF_HEIGHT * 16 / 9;
	protected float viewHeight = RefSpace.REF_HEIGHT;

	private boolean closing;
	private boolean swapScheduled;
	private @Nullable Screen next;

	protected KairoScreen(Component title) {
		super(title);
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	/** We draw our own backdrop, so skip vanilla's blur and gradient. */
	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a) {
	}

	@Override
	protected void init() {
		updateUnit();
	}

	/** Shown (again): a screen we left earlier can come back (e.g. after vanilla's Options), so forget the old close. */
	@Override
	public void added() {
		super.added();
		closing = false;
		swapScheduled = false;
		next = null;
	}

	/** GUI pixels per reference unit. */
	public float unit() {
		return unit;
	}

	private void updateUnit() {
		unit = RefSpace.unit(width, height);
		viewWidth = width / unit;
		viewHeight = height / unit;
	}

	@Override
	public final void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
		updateUnit();
		Minecraft mc = Minecraft.getInstance();
		// Exact (sub-pixel) mouse position rather than the rounded ints.
		float mx = (float) mc.mouseHandler.getScaledXPos(mc.getWindow()) / unit;
		float my = (float) mc.mouseHandler.getScaledYPos(mc.getWindow()) / unit;
		if (mc.level == null && drawsPanorama()) {
			// No world behind us (main menu and friends): the vanilla panorama is the backdrop, and what the blur sees.
			extractPanorama(g, partialTick);
		}
		drawBackground(g, partialTick);
		RefSpace.push(g);
		try {
			draw(g, mx, my, partialTick);
		} finally {
			RefSpace.pop(g);
		}
		Toasts.draw(g);

		if (closing && !swapScheduled && isCloseAnimationDone()) {
			swapScheduled = true;
			Screen target = next;
			// Swap on the next frame, not in the middle of extracting this one.
			mc.schedule(() -> {
				if (mc.gui.screen() == this) {
					mc.gui.setScreen(target);
				}
			});
		}
	}

	/** Whether to show the spinning panorama when there is no world. */
	protected boolean drawsPanorama() {
		return true;
	}

	/** Drawn in GUI space before the scaled content (e.g. the full-screen backdrop). */
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
	}

	/** Draw the screen. Coordinates and mouse position are in reference units. */
	protected abstract void draw(GuiGraphicsExtractor g, float mouseX, float mouseY, float partialTick);

	// ---- closing ----

	/** Esc and friends: animate out, then return to the game. */
	@Override
	public void onClose() {
		close(null);
	}

	/** Animates out, then shows {@code next} (null = back to the game). */
	public void close(@Nullable Screen next) {
		if (closing) {
			return;
		}
		closing = true;
		this.next = next;
		onCloseStarted();
	}

	protected boolean isClosing() {
		return closing;
	}

	/** Start the exit animations here. */
	protected void onCloseStarted() {
	}

	protected boolean isCloseAnimationDone() {
		return true;
	}

	// ---- input, in reference units ----

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		return !closing && onClick((float) event.x() / unit, (float) event.y() / unit, event.button());
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		return !closing && onRelease((float) event.x() / unit, (float) event.y() / unit, event.button());
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		return !closing && onDrag((float) event.x() / unit, (float) event.y() / unit, event.button(), (float) dx / unit, (float) dy / unit);
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		return !closing && onScroll((float) x / unit, (float) y / unit, (float) scrollY);
	}

	protected boolean onClick(float x, float y, int button) {
		return false;
	}

	protected boolean onRelease(float x, float y, int button) {
		return false;
	}

	protected boolean onDrag(float x, float y, int button, float dx, float dy) {
		return false;
	}

	protected boolean onScroll(float x, float y, float amount) {
		return false;
	}
}
