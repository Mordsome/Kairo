package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class BooleanSetting extends Setting<Boolean> {
	public BooleanSetting(String id, String name, boolean defaultValue) {
		super(id, name, defaultValue);
	}

	public boolean isOn() {
		return get();
	}

	public void toggle() {
		setEffective(!get());
	}

	@Override
	public JsonElement toJson(Boolean value) {
		return new JsonPrimitive(value);
	}

	@Override
	public Boolean fromJson(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isBoolean()) {
			throw new IllegalArgumentException("expected boolean");
		}
		return json.getAsBoolean();
	}
}
