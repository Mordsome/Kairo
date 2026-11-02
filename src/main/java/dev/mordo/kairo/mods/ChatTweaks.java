package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.render.Easing;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Timestamps, stacked repeats, longer history, slide-in, search and copy. Only changes your own chat view. */
public final class ChatTweaks extends Module {
	public enum TimeFormat implements EnumSetting.Labeled {
		H24("14:05", "HH:mm"),
		H24_SECONDS("14:05:09", "HH:mm:ss"),
		H12("2:05 PM", "h:mm a");

		private final String label;
		final DateTimeFormatter formatter;

		TimeFormat(String label, String pattern) {
			this.label = label;
			this.formatter = DateTimeFormatter.ofPattern(pattern, Locale.ROOT);
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final BooleanSetting timestamps = add(new BooleanSetting("timestamps", "Timestamps", true));
	public final EnumSetting<TimeFormat> timeFormat = add(new EnumSetting<>("time_format", "Time format", TimeFormat.H24)
		.visibleWhen(timestamps::isOn));
	public final BooleanSetting stackRepeats = add(new BooleanSetting("stack", "Stack repeated messages", true)
		.description("Shows (x2) instead of repeating a line"));
	public final SliderSetting history = add(new SliderSetting("history", "History", 500, 100, 2000, 50).unit(" lines"));
	public final BooleanSetting slideIn = add(new BooleanSetting("slide_in", "Slide-in animation", true));
	public final BooleanSetting searchBar = add(new BooleanSetting("search", "Search bar", true)
		.description("A filter box in the top-right corner while chat is open"));
	public final BooleanSetting rightClickCopy = add(new BooleanSetting("copy", "Right-click to copy", true));
	public final BooleanSetting customBackground = add(new BooleanSetting("custom_background", "Custom background opacity", false));
	public final SliderSetting backgroundOpacity = add(new SliderSetting("bg_opacity", "Background opacity", 0.5, 0.0, 1.0, 0.05).percent()
		.visibleWhen(customBackground::isOn));

	private static final float SLIDE_MS = 160;
	private long lastMessageNanos;

	public ChatTweaks() {
		super("chat", "Chat", "Timestamps, stacking, search", Category.UTILITY, Icon.CHAT, true);
	}

	/** How many messages/lines to keep (vanilla keeps 100). */
	public int historyLimit(int vanilla) {
		return isEnabled() ? history.intValue() : vanilla;
	}

	public Component withTimestamp(Component message) {
		if (!isEnabled() || !timestamps.isOn()) {
			return message;
		}
		String time = LocalTime.now().format(timeFormat.get().formatter);
		MutableComponent stamp = Component.literal("[" + time + "] ").withStyle(ChatFormatting.DARK_GRAY);
		return stamp.append(message);
	}

	public Component withRepeatCount(Component message, int count) {
		return message.copy().append(Component.literal(" (x" + count + ")").withStyle(ChatFormatting.GRAY));
	}

	public void onMessageAdded() {
		lastMessageNanos = System.nanoTime();
	}

	/** How far (GUI pixels) to push the chat down while the newest line slides in. */
	public float slideOffset() {
		if (!isEnabled() || !slideIn.isOn() || lastMessageNanos == 0) {
			return 0;
		}
		float t = (System.nanoTime() - lastMessageNanos) / 1_000_000f / SLIDE_MS;
		return t >= 1 ? 0 : 9 * (1 - Easing.OUT_CUBIC.apply(t));
	}
}
