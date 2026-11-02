package dev.mordo.kairo.gui.widgets;

import dev.mordo.kairo.core.settings.Setting;
import org.jspecify.annotations.Nullable;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Function;

/** Lets a module give one of its settings a special editor (e.g. the crosshair pixel grid). */
public final class CustomWidgets {
	private static final Map<Setting<?>, Function<WidgetHost, Widget>> FACTORIES = new IdentityHashMap<>();

	private CustomWidgets() {
	}

	public static void register(Setting<?> setting, Function<WidgetHost, Widget> factory) {
		FACTORIES.put(setting, factory);
	}

	static @Nullable Widget create(Setting<?> setting, WidgetHost host) {
		Function<WidgetHost, Widget> factory = FACTORIES.get(setting);
		return factory == null ? null : factory.apply(host);
	}

	static boolean has(Setting<?> setting) {
		return FACTORIES.containsKey(setting);
	}
}
