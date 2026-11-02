package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

/** One choice out of an enum. Enums can implement {@link Labeled} for nicer display names. */
public class EnumSetting<E extends Enum<E>> extends Setting<E> {
	private final E[] values;

	public EnumSetting(String id, String name, E defaultValue) {
		super(id, name, defaultValue);
		this.values = defaultValue.getDeclaringClass().getEnumConstants();
	}

	public E[] values() {
		return values;
	}

	public void cycle(int direction) {
		int index = Math.floorMod(get().ordinal() + direction, values.length);
		setEffective(values[index]);
	}

	public static String label(Enum<?> value) {
		if (value instanceof Labeled labeled) {
			return labeled.label();
		}
		String n = value.name().toLowerCase().replace('_', ' ');
		return Character.toUpperCase(n.charAt(0)) + n.substring(1);
	}

	@Override
	public JsonElement toJson(E value) {
		return new JsonPrimitive(value.name());
	}

	@Override
	public E fromJson(JsonElement json) {
		return Enum.valueOf(getDefault().getDeclaringClass(), json.getAsString());
	}

	public interface Labeled {
		String label();
	}
}
