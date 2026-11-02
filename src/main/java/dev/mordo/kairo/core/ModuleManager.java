package dev.mordo.kairo.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Registry of every module, plus the client-wide settings holder. */
public final class ModuleManager {
	private static final Map<String, Module> MODULES = new LinkedHashMap<>();
	private static final List<Module> MODULE_LIST = new ArrayList<>();
	private static final List<SettingHolder> HOLDERS = new ArrayList<>();

	private ModuleManager() {
	}

	public static <M extends Module> M register(M module) {
		if (MODULES.putIfAbsent(module.id(), module) != null) {
			throw new IllegalStateException("Duplicate module id " + module.id());
		}
		MODULE_LIST.add(module);
		HOLDERS.add(module);
		return module;
	}

	/** Non-module setting holders (client settings, keybinds). */
	public static <H extends SettingHolder> H registerHolder(H holder) {
		HOLDERS.add(holder);
		return holder;
	}

	public static List<Module> modules() {
		return Collections.unmodifiableList(MODULE_LIST);
	}

	public static List<SettingHolder> allHolders() {
		return Collections.unmodifiableList(HOLDERS);
	}

	public static Module get(String id) {
		return MODULES.get(id);
	}

	@SuppressWarnings("unchecked")
	public static <M extends Module> M get(Class<M> type) {
		for (Module module : MODULE_LIST) {
			if (type.isInstance(module)) {
				return (M) module;
			}
		}
		throw new IllegalArgumentException("No module of type " + type.getName());
	}

	public static List<Module> byCategory(Category category) {
		List<Module> result = new ArrayList<>();
		for (Module module : MODULE_LIST) {
			if (module.category() == category) {
				result.add(module);
			}
		}
		return result;
	}

	public static int enabledCount() {
		int count = 0;
		for (Module module : MODULE_LIST) {
			if (module.isEnabled()) {
				count++;
			}
		}
		return count;
	}

	/** Fires onEnable/onDisable for any module whose effective state changed. */
	public static void syncAll() {
		for (Module module : MODULE_LIST) {
			module.syncActiveState();
		}
	}

	public static void tick() {
		for (Module module : MODULE_LIST) {
			if (module.isEnabled()) {
				module.tick();
			}
		}
	}
}
