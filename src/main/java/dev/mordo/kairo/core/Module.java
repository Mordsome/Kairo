package dev.mordo.kairo.core;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.gui.Icon;

/**
 * Base class for every feature. A module has an id, display info, a category, an "enabled" switch and settings.
 *
 * <p>"enabled" is itself a setting, so it can carry a PvP-mode override like anything else
 * (e.g. "hide Coordinates in PvP" is just enabled=false in the PvP layer).
 */
public abstract class Module extends SettingHolder {
	private final String name;
	private final String description;
	private final Category category;
	private final Icon icon;
	public final BooleanSetting enabled;

	/** Last state we told the module about, so onEnable/onDisable fire exactly once per change. */
	private boolean active;

	protected Module(String id, String name, String description, Category category, Icon icon, boolean enabledByDefault) {
		super(id);
		this.name = name;
		this.description = description;
		this.category = category;
		this.icon = icon;
		this.enabled = add(new BooleanSetting("enabled", "Enabled", enabledByDefault));
		this.enabled.onChange(value -> syncActiveState());
	}

	public String name() {
		return name;
	}

	public String description() {
		return description;
	}

	public Category category() {
		return category;
	}

	public Icon icon() {
		return icon;
	}

	public boolean isEnabled() {
		return enabled.get();
	}

	/** Flips whatever layer is in effect (the PvP override while PvP mode is on and one exists). */
	public void toggle() {
		enabled.toggle();
	}

	/** Called when the effective enabled state may have changed (setting change, PvP flip, config load). */
	public final void syncActiveState() {
		boolean now = isEnabled();
		if (now == active) {
			return;
		}
		active = now;
		if (now) {
			onEnable();
		} else {
			onDisable();
		}
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/**
	 * Something to open when the mod's card is clicked (instead of its settings; the gear still opens those),
	 * or null for settings.
	 */
	public @org.jspecify.annotations.Nullable Runnable openAction() {
		return null;
	}

	/** Called once after all modules are registered and the config is loaded. */
	public void init() {
	}

	/** Client tick (20/s), only while enabled. */
	public void tick() {
	}
}
