package dev.mordo.kairo.hud;

/** Test-only: where HudManager last drew an element (the fields are package-private). */
public final class HudProbeAccess {
	private HudProbeAccess() {
	}

	public static float lastX(HudElement element) {
		return element.lastX;
	}

	public static float lastY(HudElement element) {
		return element.lastY;
	}

	public static float lastScale(HudElement element) {
		return element.lastScale;
	}
}
