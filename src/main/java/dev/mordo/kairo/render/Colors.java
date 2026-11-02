package dev.mordo.kairo.render;

/** Small ARGB helpers. */
public final class Colors {
	private Colors() {
	}

	public static int alpha(int argb) {
		return argb >>> 24;
	}

	/** {@code rgb} with alpha 0..1. */
	public static int argb(float alpha, int rgb) {
		return (Math.clamp(Math.round(alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
	}

	public static int rgba(int r, int g, int b, float alpha) {
		return argb(alpha, (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF));
	}

	public static int withAlpha(int argb, float alpha) {
		return argb(alpha, argb);
	}

	public static int multiplyAlpha(int argb, float factor) {
		int a = Math.clamp(Math.round((argb >>> 24) * factor), 0, 255);
		return (a << 24) | (argb & 0xFFFFFF);
	}

	/** Component-wise blend, t = 0 gives {@code a}, t = 1 gives {@code b}. */
	public static int lerp(int a, int b, float t) {
		t = Math.clamp(t, 0f, 1f);
		int aa = a >>> 24, ar = a >> 16 & 0xFF, ag = a >> 8 & 0xFF, ab = a & 0xFF;
		int ba = b >>> 24, br = b >> 16 & 0xFF, bg = b >> 8 & 0xFF, bb = b & 0xFF;
		return Math.round(aa + (ba - aa) * t) << 24
			| Math.round(ar + (br - ar) * t) << 16
			| Math.round(ag + (bg - ag) * t) << 8
			| Math.round(ab + (bb - ab) * t);
	}

	/** Lighter/darker by mixing towards white/black. */
	public static int brighten(int argb, float amount) {
		return amount >= 0 ? lerp(argb, argb | 0xFFFFFF, amount) : lerp(argb, argb & 0xFF000000, -amount);
	}

	/** Hue 0..1, saturation 0..1, value 0..1 to opaque RGB. */
	public static int hsv(float hue, float sat, float val) {
		float h = (hue - (float) Math.floor(hue)) * 6;
		int sector = (int) h;
		float f = h - sector;
		float p = val * (1 - sat);
		float q = val * (1 - sat * f);
		float t = val * (1 - sat * (1 - f));
		float r;
		float gr;
		float b;
		switch (sector) {
			case 0 -> {
				r = val; gr = t; b = p;
			}
			case 1 -> {
				r = q; gr = val; b = p;
			}
			case 2 -> {
				r = p; gr = val; b = t;
			}
			case 3 -> {
				r = p; gr = q; b = val;
			}
			case 4 -> {
				r = t; gr = p; b = val;
			}
			default -> {
				r = val; gr = p; b = q;
			}
		}
		return 0xFF000000 | Math.round(r * 255) << 16 | Math.round(gr * 255) << 8 | Math.round(b * 255);
	}

	/** RGB to {hue, saturation, value}, each 0..1. */
	public static float[] toHsv(int argb) {
		float r = (argb >> 16 & 0xFF) / 255f;
		float g = (argb >> 8 & 0xFF) / 255f;
		float b = (argb & 0xFF) / 255f;
		float max = Math.max(r, Math.max(g, b));
		float min = Math.min(r, Math.min(g, b));
		float delta = max - min;
		float hue = 0;
		if (delta > 0) {
			if (max == r) {
				hue = ((g - b) / delta) % 6;
			} else if (max == g) {
				hue = (b - r) / delta + 2;
			} else {
				hue = (r - g) / delta + 4;
			}
			hue /= 6;
			if (hue < 0) {
				hue += 1;
			}
		}
		return new float[]{hue, max == 0 ? 0 : delta / max, max};
	}

	/** Without a blurred backdrop, a glass tint needs to be more opaque to read as a panel. */
	public static int opaqueTint(int tint) {
		float a = Math.min(1f, alpha(tint) / 255f * 1.5f + 0.25f);
		return argb(a, tint);
	}
}
