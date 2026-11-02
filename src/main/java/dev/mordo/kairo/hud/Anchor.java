package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.settings.EnumSetting;

/** Nine-point anchor. A HUD element's offset is measured from this point, as a fraction of the screen. */
public enum Anchor implements EnumSetting.Labeled {
	TOP_LEFT(0, 0, "Top left"),
	TOP_CENTER(0.5f, 0, "Top"),
	TOP_RIGHT(1, 0, "Top right"),
	MIDDLE_LEFT(0, 0.5f, "Left"),
	CENTER(0.5f, 0.5f, "Center"),
	MIDDLE_RIGHT(1, 0.5f, "Right"),
	BOTTOM_LEFT(0, 1, "Bottom left"),
	BOTTOM_CENTER(0.5f, 1, "Bottom"),
	BOTTOM_RIGHT(1, 1, "Bottom right");

	/** Where on the screen (0..1) and on the element (0..1) the anchor sits. */
	public final float fx;
	public final float fy;
	private final String label;

	Anchor(float fx, float fy, String label) {
		this.fx = fx;
		this.fy = fy;
		this.label = label;
	}

	@Override
	public String label() {
		return label;
	}
}
