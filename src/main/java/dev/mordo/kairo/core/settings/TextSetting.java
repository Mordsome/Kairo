package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

public class TextSetting extends Setting<String> {
	private final int maxLength;

	public TextSetting(String id, String name, String defaultValue, int maxLength) {
		super(id, name, defaultValue);
		this.maxLength = maxLength;
	}

	public int maxLength() {
		return maxLength;
	}

	@Override
	protected String sanitize(String value) {
		return value.length() > maxLength ? value.substring(0, maxLength) : value;
	}

	@Override
	public JsonElement toJson(String value) {
		return new JsonPrimitive(value);
	}

	@Override
	public String fromJson(JsonElement json) {
		return json.getAsString();
	}
}
