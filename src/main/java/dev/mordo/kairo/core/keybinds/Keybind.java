package dev.mordo.kairo.core.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

/**
 * An immutable key or mouse button. Wraps vanilla's {@link InputConstants.Key}, so the stored names
 * ("key.keyboard.c", "key.mouse.4") are the same format vanilla uses in options.txt.
 * Note: since 26.x keyboard values are SDL scancodes, so always use {@link InputConstants} constants.
 */
public record Keybind(InputConstants.Key key) {
	public static final Keybind NONE = new Keybind(InputConstants.UNKNOWN);

	public static Keybind keyboard(int scancode) {
		return new Keybind(InputConstants.Type.KEYBOARD.getOrCreate(scancode));
	}

	public static Keybind mouse(int button) {
		return new Keybind(InputConstants.Type.MOUSE.getOrCreate(button));
	}

	public boolean isNone() {
		return key.equals(InputConstants.UNKNOWN);
	}

	public boolean isMouse() {
		return key.getType() == InputConstants.Type.MOUSE;
	}

	public boolean matchesKey(int scancode) {
		return !isNone() && !isMouse() && key.getValue() == scancode;
	}

	public boolean matchesMouse(int button) {
		return isMouse() && key.getValue() == button;
	}

	/** Physical state right now. */
	public boolean isDown() {
		if (isNone()) {
			return false;
		}
		return isMouse() ? InputTracker.isMouseDown(key.getValue()) : InputTracker.isKeyDown(key.getValue());
	}

	public Component displayName() {
		return isNone() ? Component.literal("None") : key.getDisplayName();
	}

	public String serialize() {
		return isNone() ? "none" : key.getName();
	}

	/** Returns null for unparseable input. */
	public static Keybind parse(String text) {
		if (text == null || text.isBlank() || text.equals("none")) {
			return NONE;
		}
		try {
			return new Keybind(InputConstants.getKey(text));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}
}
