package dev.mordo.kairo.core;

import dev.mordo.kairo.core.config.ConfigManager;
import dev.mordo.kairo.core.settings.Setting;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Anything that owns a list of settings and is saved to the profile (modules, client settings). */
public abstract class SettingHolder {
	private final String id;
	private final List<Setting<?>> settings = new ArrayList<>();
	private final List<Setting<?>> settingsView = Collections.unmodifiableList(settings);

	protected SettingHolder(String id) {
		this.id = id;
	}

	public String id() {
		return id;
	}

	protected <S extends Setting<?>> S add(S setting) {
		for (Setting<?> existing : settings) {
			if (existing.id().equals(setting.id())) {
				throw new IllegalStateException("Duplicate setting id '" + setting.id() + "' in " + id);
			}
		}
		setting.setDirtyHook(ConfigManager::markDirty);
		settings.add(setting);
		return setting;
	}

	public List<Setting<?>> settings() {
		return settingsView;
	}

	public Setting<?> setting(String settingId) {
		for (Setting<?> setting : settings) {
			if (setting.id().equals(settingId)) {
				return setting;
			}
		}
		return null;
	}

	/** True if any setting has a PvP-mode override (shows the PVP tag on mod cards). */
	public boolean hasPvpOverrides() {
		for (Setting<?> setting : settings) {
			if (setting.hasPvpOverride()) {
				return true;
			}
		}
		return false;
	}

	public void resetAll() {
		for (Setting<?> setting : settings) {
			setting.reset();
		}
	}
}
