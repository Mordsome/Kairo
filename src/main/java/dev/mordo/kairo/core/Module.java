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

	/** "Follow theme colours": every colour of this mod that can use the accent does (null if it has none). */
	public @org.jspecify.annotations.Nullable BooleanSetting followTheme;
	/** "Tinted background": its background takes the theme's dark tone (null if it has no background colour). */
	public @org.jspecify.annotations.Nullable BooleanSetting tintedBackground;

	/**
	 * Adds the theme switches to a mod with colours, after its own settings (so they show at the end of its page), and
	 * connects its colours to them. Called once by {@link ModuleManager#register}.
	 */
	final void addThemeSettings() {
		boolean accentable = false;
		boolean background = false;
		for (var setting : settings()) {
			if (setting instanceof dev.mordo.kairo.core.settings.ColorSetting color) {
				accentable |= color.accentRole() != dev.mordo.kairo.core.settings.ColorSetting.AccentRole.NONE
					|| color.themeRole() == dev.mordo.kairo.core.settings.ColorSetting.ThemeRole.TEXT;
				background |= color.themeRole() == dev.mordo.kairo.core.settings.ColorSetting.ThemeRole.BACKGROUND;
			}
		}
		if (accentable) {
			followTheme = add(new BooleanSetting("follow_theme", "Follow theme colours", false)
				.description("Its colours use your theme colour (from Visuals → Theme or your wallpaper), even ones you picked"));
		}
		if (background) {
			tintedBackground = add(new BooleanSetting("tinted_background", "Tinted background", true)
				.description("Its background takes a dark tone of your theme colour while Visuals → Theme → Tint backgrounds is on. Off: neutral grey"));
		}
		java.util.function.BooleanSupplier follow = followTheme != null ? followTheme::isOn : () -> false;
		java.util.function.BooleanSupplier tinted = tintedBackground != null ? tintedBackground::isOn : () -> true;
		for (var setting : settings()) {
			if (setting instanceof dev.mordo.kairo.core.settings.ColorSetting color) {
				color.bindTheme(follow, tinted);
			}
		}
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
