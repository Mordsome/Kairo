package dev.mordo.kairo.core;

import dev.mordo.kairo.core.settings.BooleanSetting;

/**
 * Options that only exist for PvP mode (right-click PvP Mode in the ring). Everything else on that page is a
 * PvP override of a mod's own setting; these have no normal value, they simply apply while PvP mode is on.
 */
public final class PvpSettings extends SettingHolder {
	public static final PvpSettings INSTANCE = new PvpSettings();

	public final BooleanSetting noViewBobbing = add(new BooleanSetting("no_view_bobbing", "No view bobbing", false)
		.description("The camera and hand stop swaying while you walk"));
	public final BooleanSetting hideWaypoints = add(new BooleanSetting("hide_waypoints", "Hide all waypoints", false)
		.description("Markers, beams and the death point disappear until PvP mode is off"));

	private PvpSettings() {
		super("pvp_mode");
	}

	/** True while PvP mode is on and {@code setting} is switched on. */
	public static boolean active(BooleanSetting setting) {
		return PvpMode.isActive() && setting.getNormal();
	}
}
