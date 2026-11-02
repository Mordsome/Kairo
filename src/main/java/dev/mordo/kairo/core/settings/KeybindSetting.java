package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.mordo.kairo.core.keybinds.Keybind;

/**
 * A rebindable key or mouse button. Stored as "key:67", "mouse:3" or "none".
 * Keybinds are never PvP-overridden (your controls shouldn't change under you).
 */
public class KeybindSetting extends Setting<Keybind> {
	/** What the key does when pressed in-game (null for hold-only binds read via {@link #isDown()}). */
	private Runnable pressAction;

	public KeybindSetting(String id, String name, Keybind defaultValue) {
		super(id, name, defaultValue);
	}

	public KeybindSetting onPress(Runnable action) {
		this.pressAction = action;
		return this;
	}

	public Runnable pressAction() {
		return pressAction;
	}

	public boolean isBound() {
		return !get().isNone();
	}

	/** Physical state right now (for hold-style binds like Zoom). */
	public boolean isDown() {
		return get().isDown();
	}

	@Override
	public boolean supportsPvpOverride() {
		return false;
	}

	@Override
	public JsonElement toJson(Keybind value) {
		return new JsonPrimitive(value.serialize());
	}

	@Override
	public Keybind fromJson(JsonElement json) {
		Keybind parsed = Keybind.parse(json.getAsString());
		if (parsed == null) {
			throw new IllegalArgumentException("bad keybind " + json);
		}
		return parsed;
	}
}
