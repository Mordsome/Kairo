package dev.mordo.kairo.gui;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.render.Colors;

/** Shared UI colours. Accent and glass tint come from the Visuals settings. */
public final class Theme {
	public static final int TEXT = 0xFFF4F8FB;
	public static final int DARK_TEXT = 0xFF0C1018;
	public static final int SURFACE = 0x0AFFFFFF;        // white 4%
	public static final int SURFACE_HOVER = 0x14FFFFFF;  // white 8%
	public static final int BORDER = 0x17FFFFFF;         // white 9%
	public static final int BORDER_HOVER = 0x29FFFFFF;   // white 16%
	public static final int PANEL_BORDER = 0x2EFFFFFF;   // white 18%
	public static final int POPUP = 0xF2111720;
	public static final int TRACK = 0x29FFFFFF;          // white 16%

	private Theme() {
	}

	public static int accent() {
		return ClientSettings.INSTANCE.accent.argb();
	}

	public static int pvpAccent() {
		return ClientSettings.INSTANCE.pvpAccent.argb();
	}

	/** The accent of the mode the player is in (PvP accent while PvP mode is on). */
	public static int modeAccent() {
		return PvpMode.isActive() ? pvpAccent() : accent();
	}

	public static int glassTint() {
		return ClientSettings.INSTANCE.glassTint.argb();
	}

	public static int text(float alpha) {
		return Colors.withAlpha(TEXT, alpha);
	}

	/** Text colour at a fraction of full opacity, also multiplied by an outer fade. */
	public static int text(float opacity, float fade) {
		return Colors.withAlpha(TEXT, opacity * fade);
	}

	public static int fade(int argb, float fade) {
		return Colors.multiplyAlpha(argb, fade);
	}
}
