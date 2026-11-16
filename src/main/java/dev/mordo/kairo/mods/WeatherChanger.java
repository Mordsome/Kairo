package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Easing;

/**
 * Weather Changer: the weather you see, whatever the world's weather is. Only your own picture and sound change
 * (clear skies in a storm, or rain on a sunny day); the real weather and everything it does stay the server's.
 * Whether rain falls as snow still follows the biome.
 */
public final class WeatherChanger extends Module {
	public enum Weather implements EnumSetting.Labeled {
		CLEAR("Clear"),
		RAIN("Rain"),
		THUNDER("Thunderstorm");

		private final String label;

		Weather(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Weather> weather = add(new EnumSetting<>("weather", "Weather", Weather.CLEAR)
		.description("Rain falls as snow in cold biomes. A thunderstorm darkens the sky; lightning itself only comes from the server"));
	public final SliderSetting fade = add(new SliderSetting("fade", "Change over", 2.0, 0, 10, 0.5).unit(" s")
		.description("How long the weather takes to change when you switch this"));

	private final Animation amount = new Animation(0);
	private final Animation rain = new Animation(0);
	private final Animation thunder = new Animation(0);

	public WeatherChanger() {
		super("weather_changer", "Weather Changer", "Clear skies, rain or a storm whenever you like (only what you see)", Category.VISUAL,
			Icon.GLOBE, false);
		weather.onChange(w -> aim());
	}

	private void aim() {
		float ms = fade.floatValue() * 1000;
		rain.animateTo(weather.get() == Weather.CLEAR ? 0 : 1, ms, Easing.IN_OUT_CUBIC);
		thunder.animateTo(weather.get() == Weather.THUNDER ? 1 : 0, ms, Easing.IN_OUT_CUBIC);
	}

	@Override
	protected void onEnable() {
		rain.set(weather.get() == Weather.CLEAR ? 0 : 1);
		thunder.set(weather.get() == Weather.THUNDER ? 1 : 0);
		amount.animateTo(1, fade.floatValue() * 1000, Easing.IN_OUT_CUBIC);
	}

	@Override
	protected void onDisable() {
		amount.animateTo(0, fade.floatValue() * 1000, Easing.IN_OUT_CUBIC);
	}

	/** True while it changes the weather at all (also while fading back out after being switched off). */
	public boolean active() {
		return amount.value() > 0.001f;
	}

	public float rain(float real) {
		return Animation.lerp(real, rain.value(), amount.value());
	}

	public float thunder(float real) {
		return Animation.lerp(real, thunder.value(), amount.value());
	}
}
