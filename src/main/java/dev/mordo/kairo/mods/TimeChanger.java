package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;

/**
 * Time Changer: the time of day you see, whatever time the world really has. Only your own picture changes (the sky,
 * the light, the sun and moon); the world's real time, mobs, crops and beds are the server's business and stay as
 * they are. The moon keeps its real phase.
 */
public final class TimeChanger extends Module {
	public enum Mode implements EnumSetting.Labeled {
		FIXED("One time"),
		CYCLE("Fast day and night");

		private final String label;

		Mode(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Mode> mode = add(new EnumSetting<>("mode", "Mode", Mode.FIXED)
		.description("One time holds the sky still at the hour you pick. Fast day and night runs a whole day in a few minutes"));
	public final SliderSetting hour = add(new SliderSetting("hour", "Time of day", 12, 0, 24, 0.25).unit(" h")
		.description("6 is sunrise, 12 noon, 18 sunset, 0 midnight")
		.visibleWhen(() -> mode.get() == Mode.FIXED));
	public final SliderSetting dayLength = add(new SliderSetting("day_length", "A day takes", 5, 1, 60, 1).unit(" min")
		.visibleWhen(() -> mode.get() == Mode.CYCLE));

	private final long started = System.nanoTime();

	public TimeChanger() {
		super("time_changer", "Time Changer", "Day, night or sunset whenever you like (only what you see)", Category.VISUAL, Icon.CLOCK, false);
	}

	/** The time of day to show, in ticks (0 = sunrise at 6:00, 6000 = noon), with its fraction. */
	private double timeOfDay() {
		if (mode.get() == Mode.CYCLE) {
			double seconds = (System.nanoTime() - started) / 1e9;
			return seconds / (dayLength.value() * 60) * 24000 % 24000;
		}
		return ((hour.value() - 6 + 24) % 24) * 1000;
	}

	/** In place of the world clock's ticks: the same day (so the moon keeps its phase) at the time we show. */
	public long ticks(long real) {
		return Math.floorDiv(real, 24000L) * 24000L + (long) Math.floor(timeOfDay());
	}

	/** The part of a tick already gone (keeps a fast day gliding instead of stepping). */
	public float partialTick() {
		double time = timeOfDay();
		return (float) (time - Math.floor(time));
	}
}
