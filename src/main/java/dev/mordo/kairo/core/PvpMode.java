package dev.mordo.kairo.core;

import dev.mordo.kairo.core.config.ConfigManager;
import dev.mordo.kairo.core.settings.Setting;

import java.util.ArrayList;
import java.util.List;

/**
 * Global normal/PvP state. Settings read this in {@link Setting#get()} to decide whether their PvP override applies.
 * Flipping it never touches the normal values, so switching back restores everything exactly.
 */
public final class PvpMode {
	private static volatile boolean active;
	/** Set only while a settings preview draws (render thread): pretend to be in this mode. */
	private static Boolean previewing;
	private static final List<Listener> LISTENERS = new ArrayList<>();

	private PvpMode() {
	}

	public static boolean isActive() {
		Boolean preview = previewing;
		return preview != null ? preview : active;
	}

	/** The real mode, ignoring any preview in progress. */
	public static boolean isActiveForReal() {
		return active;
	}

	/**
	 * Runs {@code draw} as if PvP mode were {@code pvp}, so a settings preview can show the PvP values without
	 * switching modes. Only reads happen inside; nothing is saved or synced.
	 */
	public static void preview(boolean pvp, Runnable draw) {
		Boolean before = previewing;
		previewing = pvp;
		try {
			draw.run();
		} finally {
			previewing = before;
		}
	}

	public static void toggle() {
		set(!active);
	}

	public static void set(boolean on) {
		setInternal(on, true);
	}

	/** Used by the config loader: restores the saved state without scheduling another save. */
	public static void restore(boolean on) {
		setInternal(on, false);
	}

	private static void setInternal(boolean on, boolean save) {
		if (active == on) {
			return;
		}
		active = on;
		// Effective values of overridden settings just changed: let listeners and modules react.
		for (SettingHolder holder : ModuleManager.allHolders()) {
			for (Setting<?> setting : holder.settings()) {
				if (setting.hasPvpOverride()) {
					setting.notifyEffectiveChanged();
				}
			}
		}
		ModuleManager.syncAll();
		for (Listener listener : LISTENERS) {
			listener.onPvpModeChanged(on);
		}
		if (save) {
			ConfigManager.markDirty();
		}
	}

	public static void addListener(Listener listener) {
		LISTENERS.add(listener);
	}

	@FunctionalInterface
	public interface Listener {
		void onPvpModeChanged(boolean active);
	}
}
