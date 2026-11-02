package dev.mordo.kairo.core.keybinds;

/**
 * Raw mouse button state and click history, fed by {@code MouseHandlerMixin}.
 * Only the player's own input is recorded; nothing here generates input.
 */
public final class InputTracker {
	private static final int MAX_BUTTONS = 16;
	private static final boolean[] MOUSE_DOWN = new boolean[MAX_BUTTONS];

	/** Ring buffers of click timestamps (ms) for the CPS counter. */
	private static final ClickHistory LEFT_CLICKS = new ClickHistory();
	private static final ClickHistory RIGHT_CLICKS = new ClickHistory();

	private InputTracker() {
	}

	private static final boolean[] KEY_DOWN = new boolean[512];

	/** Keyboard state from key events (works for real input and simulated input alike). */
	public static boolean isKeyDown(int scancode) {
		return scancode >= 0 && scancode < KEY_DOWN.length && KEY_DOWN[scancode];
	}

	public static void onKey(int scancode, boolean down) {
		if (scancode >= 0 && scancode < KEY_DOWN.length) {
			KEY_DOWN[scancode] = down;
		}
	}

	public static boolean isMouseDown(int button) {
		return button >= 0 && button < MAX_BUTTONS && MOUSE_DOWN[button];
	}

	/** Called for every mouse button event on the game window. */
	public static void onMouseButton(int button, boolean pressed, boolean inGame) {
		if (button >= 0 && button < MAX_BUTTONS) {
			MOUSE_DOWN[button] = pressed;
		}
		if (pressed && inGame) {
			long now = System.currentTimeMillis();
			if (button == 1) {
				LEFT_CLICKS.add(now);
			} else if (button == 3) {
				RIGHT_CLICKS.add(now);
			}
		}
	}

	/** Left clicks in the last second. */
	public static int leftCps() {
		return LEFT_CLICKS.countSince(System.currentTimeMillis() - 1000);
	}

	/** Right clicks in the last second. */
	public static int rightCps() {
		return RIGHT_CLICKS.countSince(System.currentTimeMillis() - 1000);
	}

	/** Fixed-size ring buffer: no allocation per click. */
	private static final class ClickHistory {
		private final long[] times = new long[64];
		private int head;

		void add(long time) {
			times[head] = time;
			head = (head + 1) % times.length;
		}

		int countSince(long cutoff) {
			int count = 0;
			for (long time : times) {
				if (time > cutoff) {
					count++;
				}
			}
			return count;
		}
	}
}
