package dev.mordo.kairo.gui;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.render.ColorScience;
import dev.mordo.kairo.render.Colors;

import java.util.HashMap;
import java.util.Map;

/**
 * Shared UI colours. The accent and glass tint come from the Visuals settings; with Visuals → Theme → "Tint
 * backgrounds" and "Tint text" on, panels take a dark tone of the theme colour and text a very light tint of it
 * (secondary text a softer one). {@link #refresh()} works these out whenever the theme changes; text is kept at
 * WCAG 4.5:1 contrast or better against the panels by raising its lightness when a tint would lower it.
 */
public final class Theme {
	/** The untinted text colour. */
	public static final int BASE_TEXT = 0xFFF4F8FB;
	private static final int BASE_POPUP = 0xF2111720;
	/** Roughly what a glass panel shows through it (the blurred, dimmed world or menu background). */
	private static final int GLASS_BACKDROP = 0xFF2A2E35;
	public static final double MIN_CONTRAST = 4.5;

	/** Main text colour (tinted with the theme while "Tint text" is on). */
	public static int TEXT = BASE_TEXT;
	/** Secondary text colour (a softer tint), used by {@link #text(float)} below 80% opacity. */
	public static int TEXT_SOFT = BASE_TEXT;
	public static final int DARK_TEXT = 0xFF0C1018;
	public static final int SURFACE = 0x0AFFFFFF;        // white 4%
	public static final int SURFACE_HOVER = 0x14FFFFFF;  // white 8%
	public static final int BORDER = 0x17FFFFFF;         // white 9%
	public static final int BORDER_HOVER = 0x29FFFFFF;   // white 16%
	public static final int PANEL_BORDER = 0x2EFFFFFF;   // white 18%
	/** Dropdown and colour picker backgrounds (tinted with the theme). */
	public static int POPUP = BASE_POPUP;
	public static final int TRACK = 0x29FFFFFF;          // white 16%

	/** Contrast of the main and secondary text against a panel, worked out by the last refresh (for tests and checks). */
	public static double mainContrast = 21;
	public static double softContrast = 21;

	private static long key = Long.MIN_VALUE;
	private static float bgStrength;
	private static double toneChroma;
	private static double toneHue;
	/** {tinted rgb, 1 if it's a dark near-grey background colour} per input colour. */
	private static final Map<Integer, int[]> TINTED = new HashMap<>();
	/** What glass shows through it right now (a bright wallpaper in the menus makes it brighter). */
	private static int backdrop = GLASS_BACKDROP;
	/**
	 * Least opacity for dark backgrounds while a bright wallpaper is behind the menus (0 = no minimum), so secondary
	 * text on glass keeps 4.5:1. Applies whether or not "Tint backgrounds" is on.
	 */
	private static float glassFloor;

	private Theme() {
	}

	public static int accent() {
		refresh();
		return ClientSettings.INSTANCE.accent.argb();
	}

	public static int pvpAccent() {
		return ClientSettings.INSTANCE.pvpAccent.argb();
	}

	/** The accent of the mode the player is in (PvP accent while PvP mode is on). */
	public static int modeAccent() {
		return PvpMode.isActive() ? pvpAccent() : accent();
	}

	/** The glass (or Void panel) tint, tinted with the theme. */
	public static int glassTint() {
		refresh();
		return ClientSettings.INSTANCE.glassTint.argb();
	}

	public static int text(float alpha) {
		refresh();
		return Colors.withAlpha(alpha >= 0.8f ? TEXT : TEXT_SOFT, alpha);
	}

	/** Text colour at a fraction of full opacity, also multiplied by an outer fade. */
	public static int text(float opacity, float fade) {
		refresh();
		return Colors.withAlpha(opacity >= 0.8f ? TEXT : TEXT_SOFT, opacity * fade);
	}

	public static int fade(int argb, float fade) {
		return Colors.multiplyAlpha(argb, fade);
	}

	// ---- theme tints ----

	/**
	 * A dark, near-neutral background colour (panel, box, button fill) in a dark tone of the theme colour, keeping its
	 * lightness and transparency. Coloured or light colours (highlights, accents) come back unchanged, and so does
	 * everything while "Tint backgrounds" is off.
	 */
	public static int tintBackground(int argb) {
		refresh();
		if (bgStrength <= 0 && glassFloor <= 0) {
			return argb;
		}
		int rgb = argb | 0xFF000000;
		int[] info = TINTED.get(rgb);
		if (info == null) {
			info = analyse(rgb);
			TINTED.put(rgb, info);
		}
		int out = (argb & 0xFF000000) | (info[0] & 0xFFFFFF);
		int alpha = Colors.alpha(argb);
		if (glassFloor > 0 && info[1] == 1 && alpha > 0 && alpha < glassFloor * 255) {
			out = Colors.withAlpha(out, glassFloor);
		}
		return out;
	}

	/** {tinted rgb, 1 if a background}: only dark, almost grey colours are backgrounds; highlights stay as they are. */
	private static int[] analyse(int rgb) {
		double[] lch = ColorScience.lch(rgb);
		if (lch[0] > 40 || lch[1] > 14) {
			return new int[]{rgb, 0};
		}
		return new int[]{bgStrength > 0 ? tone(lch[0]) : rgb, 1};
	}

	/** The theme's dark tone at (at least) this lightness. */
	private static int tone(double lightness) {
		return ColorScience.fromLch(Math.max(lightness, 9 * bgStrength), toneChroma, toneHue);
	}

	/** Void's flat panel grey, tinted. */
	public static int voidPanel(int panelRgb) {
		return tintBackground(0xFF000000 | panelRgb) & 0xFFFFFF;
	}

	/** Works out the tints for the current theme colour and settings (cheap when nothing changed). */
	public static void refresh() {
		ClientSettings s = ClientSettings.INSTANCE;
		if (s == null) {
			return; // still starting up
		}
		int accent = s.accent.argb() | 0xFF000000;
		float bg = s.tintBackgrounds.isOn() ? s.backgroundTint.floatValue() : 0;
		float tx = s.tintText.isOn() ? s.textTint.floatValue() : 0;
		int glass = s.glassTint.getNormal();
		// In the menus (no world), glass shows the menu background: a bright wallpaper needs more opaque glass.
		boolean menus = net.minecraft.client.Minecraft.getInstance() != null && net.minecraft.client.Minecraft.getInstance().level == null;
		double wall = menus && !Edition.isVoid() ? dev.mordo.kairo.render.background.WallpaperColors.brightness() : -1;
		float dim = dev.mordo.kairo.core.MainMenuSettings.INSTANCE == null ? 0 : dev.mordo.kairo.core.MainMenuSettings.INSTANCE.backgroundDim.floatValue();
		long k = ((long) accent << 32) ^ ((long) Float.floatToIntBits(bg) * 31 + Float.floatToIntBits(tx)) * 1_000_003L ^ glass * 7919L
			^ Double.doubleToLongBits(wall) * 131L ^ (long) Float.floatToIntBits(dim) << 7;
		if (k == key) {
			return;
		}
		key = k;
		TINTED.clear();
		double[] lch = ColorScience.lch(accent);
		bgStrength = bg;
		toneHue = lch[2];
		toneChroma = Math.min(lch[1] * 0.5, 24) * bg;
		glassFloor = 0;
		backdrop = GLASS_BACKDROP;
		if (wall > 0) {
			// The picture after the menu's darkening (the player's, or the extra a bright picture gets).
			float darken = Math.max(dim, dev.mordo.kairo.render.background.WallpaperColors.readableDim());
			double seen = Math.max(wall * Math.pow(1 - darken, 2.2), ColorScience.luminance(GLASS_BACKDROP));
			int grey = (int) Math.round(Math.pow(seen, 1 / 2.2) * 255);
			backdrop = 0xFF000000 | grey << 16 | grey << 8 | grey;
			// Raise the glass opacity until secondary text (about 55% white) on it reaches 4.5:1.
			int glassRgb = bg > 0 ? tone(ColorScience.lch(glass | 0xFF000000)[0]) : glass | 0xFF000000;
			float a = Colors.alpha(glass) / 255f;
			float start = a;
			while (a < 0.95f) {
				int panel = Colors.lerp(backdrop, glassRgb, a);
				if (ColorScience.contrast(Colors.lerp(panel, BASE_TEXT, 0.55f), panel) >= MIN_CONTRAST) {
					break;
				}
				a += 0.025f;
			}
			glassFloor = a > start ? a : 0;
		}
		POPUP = tintBackground(BASE_POPUP);

		// Text: a very light tint of the colour, and a softer one for secondary text.
		int light = ColorScience.fromLch(96, Math.min(lch[1] * 0.3, 16), lch[2]);
		int main = Colors.lerp(BASE_TEXT, light, tx);
		int soft = Colors.lerp(BASE_TEXT, light, tx * 0.6f);
		int panel = panelColor();
		if (tx > 0 && ColorScience.luminance(panel) < 0.18) {
			main = ColorScience.ensureContrast(main, panel, 1f, MIN_CONTRAST);
			soft = ColorScience.ensureContrast(soft, panel, 0.6f, MIN_CONTRAST);
		} else if (tx > 0) {
			// A light custom glass: a tint can't help there, keep the plain colours.
			main = BASE_TEXT;
			soft = BASE_TEXT;
		}
		TEXT = main;
		TEXT_SOFT = soft;
		mainContrast = ColorScience.contrast(TEXT | 0xFF000000, panel);
		softContrast = ColorScience.contrast(Colors.lerp(panel, TEXT_SOFT | 0xFF000000, 0.6f), panel);
	}

	/** What the text sits on: Void's flat panel, or the glass tint over a typical blurred backdrop. */
	public static int panelColor() {
		if (Edition.isVoid()) {
			return 0xFF000000 | voidPanel(dev.mordo.kairo.render.Shapes.VOID_PANEL);
		}
		return ColorScience.over(ClientSettings.INSTANCE.glassTint.argb(), backdrop);
	}
}
