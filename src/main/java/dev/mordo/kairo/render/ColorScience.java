package dev.mordo.kairo.render;

/**
 * Perceptual colour maths for the theme: sRGB to CIELAB / LCh and back, and WCAG contrast. LCh keeps a colour's hue
 * while its lightness and colourfulness change, so "a dark tone of this red" stays the same red.
 */
public final class ColorScience {
	private ColorScience() {
	}

	// ---- sRGB <-> linear ----

	private static double toLinear(int channel) {
		double c = channel / 255.0;
		return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
	}

	private static int fromLinear(double linear) {
		double c = linear <= 0.0031308 ? linear * 12.92 : 1.055 * Math.pow(linear, 1 / 2.4) - 0.055;
		return (int) Math.round(Math.clamp(c, 0.0, 1.0) * 255);
	}

	// ---- CIELAB (D65) ----

	private static final double XN = 0.95047;
	private static final double YN = 1.0;
	private static final double ZN = 1.08883;

	private static double f(double t) {
		return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
	}

	private static double fInv(double t) {
		double cube = t * t * t;
		return cube > 216.0 / 24389 ? cube : (116 * t - 16) / (24389.0 / 27);
	}

	/** {L 0..100, a, b} of an ARGB colour (alpha ignored). */
	public static double[] lab(int argb) {
		double r = toLinear(argb >> 16 & 0xFF);
		double g = toLinear(argb >> 8 & 0xFF);
		double b = toLinear(argb & 0xFF);
		double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / XN;
		double y = (0.2126729 * r + 0.7151522 * g + 0.0721750 * b) / YN;
		double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / ZN;
		double fx = f(x);
		double fy = f(y);
		double fz = f(z);
		return new double[]{116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
	}

	/** Opaque RGB from Lab, or -1 if it falls outside sRGB (with {@code strict}); otherwise clamped. */
	private static int fromLab(double l, double a, double b, boolean strict) {
		double fy = (l + 16) / 116;
		double fx = fy + a / 500;
		double fz = fy - b / 200;
		double x = fInv(fx) * XN;
		double y = fInv(fy) * YN;
		double z = fInv(fz) * ZN;
		double rl = 3.2404542 * x - 1.5371385 * y - 0.4985314 * z;
		double gl = -0.9692660 * x + 1.8760108 * y + 0.0415560 * z;
		double bl = 0.0556434 * x - 0.2040259 * y + 1.0572252 * z;
		if (strict) {
			double eps = 0.001;
			if (rl < -eps || rl > 1 + eps || gl < -eps || gl > 1 + eps || bl < -eps || bl > 1 + eps) {
				return -1;
			}
		}
		return 0xFF000000 | fromLinear(rl) << 16 | fromLinear(gl) << 8 | fromLinear(bl);
	}

	/** {L 0..100, C chroma, h hue in degrees 0..360}. */
	public static double[] lch(int argb) {
		double[] lab = lab(argb);
		double c = Math.hypot(lab[1], lab[2]);
		double h = Math.toDegrees(Math.atan2(lab[2], lab[1]));
		return new double[]{lab[0], c, h < 0 ? h + 360 : h};
	}

	/**
	 * An opaque colour with this lightness and hue and as close to this chroma as sRGB allows (chroma is lowered until
	 * it fits, so the hue never shifts).
	 */
	public static int fromLch(double l, double c, double h) {
		l = Math.clamp(l, 0.0, 100.0);
		double rad = Math.toRadians(h);
		double cos = Math.cos(rad);
		double sin = Math.sin(rad);
		double lo = 0;
		double hi = Math.max(0, c);
		int best = fromLab(l, 0, 0, false);
		int full = fromLab(l, hi * cos, hi * sin, true);
		if (full != -1) {
			return full;
		}
		for (int i = 0; i < 18; i++) {
			double mid = (lo + hi) / 2;
			int rgb = fromLab(l, mid * cos, mid * sin, true);
			if (rgb == -1) {
				hi = mid;
			} else {
				lo = mid;
				best = rgb;
			}
		}
		return best;
	}

	/** Smallest angle between two hues, in degrees. */
	public static double hueDistance(double a, double b) {
		double d = Math.abs(a - b) % 360;
		return d > 180 ? 360 - d : d;
	}

	// ---- WCAG contrast ----

	/** WCAG relative luminance of an opaque colour, 0..1. */
	public static double luminance(int argb) {
		return 0.2126 * toLinear(argb >> 16 & 0xFF) + 0.7152 * toLinear(argb >> 8 & 0xFF) + 0.0722 * toLinear(argb & 0xFF);
	}

	/** WCAG contrast ratio between two opaque colours (1 to 21). */
	public static double contrast(int a, int b) {
		double la = luminance(a);
		double lb = luminance(b);
		return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
	}

	/** {@code top} (with its alpha) laid over an opaque {@code bottom}. */
	public static int over(int top, int bottom) {
		float a = Colors.alpha(top) / 255f;
		return Colors.lerp(bottom | 0xFF000000, top | 0xFF000000, a);
	}

	/**
	 * Raises (or lowers, on a light background) a text colour's lightness, keeping its hue, until it reaches
	 * {@code target} contrast against {@code background} when drawn at {@code alpha}. Gives up at pure white or black
	 * (the best that alpha allows). Returns the colour with its original alpha.
	 */
	public static int ensureContrast(int text, int background, float alpha, double target) {
		int bg = background | 0xFF000000;
		int keepAlpha = text & 0xFF000000;
		int fg = text | 0xFF000000;
		if (contrast(Colors.lerp(bg, fg, alpha), bg) >= target) {
			return text;
		}
		boolean lighten = luminance(bg) < 0.18;
		double[] lch = lch(fg);
		for (int step = 1; step <= 40; step++) {
			double l = lighten ? lch[0] + (100 - lch[0]) * step / 40.0 : lch[0] * (1 - step / 40.0);
			// Colour fades out as it nears white/black, so the end of the search really is white/black.
			double c = lch[1] * (1 - step / 40.0);
			int candidate = fromLch(l, c, lch[2]);
			if (contrast(Colors.lerp(bg, candidate, alpha), bg) >= target) {
				return keepAlpha | (candidate & 0xFFFFFF);
			}
		}
		return keepAlpha | (lighten ? 0xFFFFFF : 0x000000);
	}
}
