package dev.mordo.kairo.mods;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;

/**
 * Mouse wheel scrolls tooltips that don't fit on screen (Shift + wheel for sideways).
 * The offset resets when a different tooltip shows up.
 */
public final class ScrollableTooltips extends Module {
	private static final float STEP = 24;
	private static final long ACTIVE_NANOS = 250_000_000L;

	public final SliderSetting speed = add(new SliderSetting("speed", "Scroll speed", 1.0, 0.25, 3.0, 0.05).multiplier());
	public final BooleanSetting smooth = add(new BooleanSetting("smooth", "Smooth scrolling", true));
	public final BooleanSetting horizontal = add(new BooleanSetting("horizontal", "Shift + wheel scrolls sideways", true));

	private float targetX;
	private float targetY;
	private float shownX;
	private float shownY;
	private int signature;
	private long lastDrawn;
	private long lastFrame;
	private boolean overflowX;
	private boolean overflowY;

	public ScrollableTooltips() {
		super("scrollable_tooltips", "Scrollable Tooltips", "Scroll long tooltips", Category.UTILITY, Icon.SCROLL, true);
	}

	/** Called with the tooltip's x first each frame (also detects a new tooltip). */
	public int adjustX(int x, int w, int h, int guiWidth, int guiHeight) {
		if (!isEnabled()) {
			return x;
		}
		long now = System.nanoTime();
		int sig = w * 7919 + h;
		if (sig != signature || now - lastDrawn > ACTIVE_NANOS) {
			signature = sig;
			targetX = targetY = shownX = shownY = 0;
		}
		lastDrawn = now;
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		float follow = smooth.isOn() ? 1 - (float) Math.exp(-18 * dt) : 1;
		shownX += (targetX - shownX) * follow;
		shownY += (targetY - shownY) * follow;

		overflowX = w + 8 > guiWidth;
		overflowY = h + 8 > guiHeight;
		if (!overflowX) {
			targetX = shownX = 0;
			return x;
		}
		return clampPosition(x, shownX, w, guiWidth, true);
	}

	public int adjustY(int y, int h, int guiHeight) {
		if (!isEnabled() || !overflowY) {
			return y;
		}
		return clampPosition(y, shownY, h, guiHeight, false);
	}

	/** Keeps the tooltip covering the screen: never scroll past its first or last line. */
	private int clampPosition(int pos, float offset, int size, int screen, boolean xAxis) {
		float min = screen - 4 - size - pos;
		float max = 4 - pos;
		if (xAxis) {
			targetX = Math.clamp(targetX, min, max);
		} else {
			targetY = Math.clamp(targetY, min, max);
		}
		return pos + Math.round(Math.clamp(offset, min, max));
	}

	/** Mouse wheel inside a screen. Returns true if a tall tooltip took the scroll. */
	public boolean onScroll(double dx, double dy) {
		if (!isEnabled() || System.nanoTime() - lastDrawn > ACTIVE_NANOS) {
			return false;
		}
		float amount = (float) (dy != 0 ? dy : dx) * STEP * speed.floatValue();
		boolean shift = dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_LSHIFT) || dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_RSHIFT);
		if (horizontal.isOn() && overflowX && (shift || dx != 0)) {
			targetX += amount;
			return true;
		}
		if (overflowY) {
			targetY += amount;
			return true;
		}
		return false;
	}
}
