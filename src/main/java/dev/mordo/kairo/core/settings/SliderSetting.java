package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;

import java.util.Locale;
import java.util.function.DoubleFunction;

/** A number in [min, max], snapped to {@code step}. */
public class SliderSetting extends Setting<Double> {
	private final double min;
	private final double max;
	private final double step;
	private DoubleFunction<String> formatter;

	public SliderSetting(String id, String name, double defaultValue, double min, double max, double step) {
		super(id, name, defaultValue);
		if (min >= max) {
			throw new IllegalArgumentException("min >= max for " + id);
		}
		this.min = min;
		this.max = max;
		this.step = step;
		this.formatter = defaultFormatter(step);
	}

	/** e.g. {@code v -> Math.round(v * 100) + "%"} */
	public SliderSetting format(DoubleFunction<String> formatter) {
		this.formatter = formatter;
		return this;
	}

	public SliderSetting unit(String suffix) {
		DoubleFunction<String> base = defaultFormatter(step);
		this.formatter = v -> base.apply(v) + suffix;
		return this;
	}

	public SliderSetting percent() {
		this.formatter = v -> Math.round(v * 100) + "%";
		return this;
	}

	public SliderSetting multiplier() {
		this.formatter = v -> String.format(Locale.ROOT, "%.2fx", v);
		return this;
	}

	public double value() {
		return get();
	}

	public float floatValue() {
		return get().floatValue();
	}

	public int intValue() {
		return (int) Math.round(get());
	}

	public double min() {
		return min;
	}

	public double max() {
		return max;
	}

	public double step() {
		return step;
	}

	/** 0..1 position of a value inside the range. */
	public double toProgress(double value) {
		return (value - min) / (max - min);
	}

	public double fromProgress(double progress) {
		return min + Math.clamp(progress, 0, 1) * (max - min);
	}

	public String format(double value) {
		return formatter.apply(value);
	}

	@Override
	protected Double sanitize(Double value) {
		double v = value.isNaN() ? getDefault() : value;
		if (step > 0) {
			v = min + Math.round((v - min) / step) * step;
			// Kill float noise like 0.30000000000000004.
			v = Math.round(v * 1e6) / 1e6;
		}
		return Math.clamp(v, min, max);
	}

	@Override
	public JsonElement toJson(Double value) {
		return new JsonPrimitive(value);
	}

	@Override
	public Double fromJson(JsonElement json) {
		if (!json.isJsonPrimitive() || !json.getAsJsonPrimitive().isNumber()) {
			throw new IllegalArgumentException("expected number");
		}
		return json.getAsDouble();
	}

	private static DoubleFunction<String> defaultFormatter(double step) {
		if (step >= 1 || step <= 0) {
			return v -> Long.toString(Math.round(v));
		}
		int decimals = step >= 0.1 ? 1 : 2;
		String pattern = "%." + decimals + "f";
		return v -> String.format(Locale.ROOT, pattern, v);
	}
}
