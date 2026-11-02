package dev.mordo.kairo.render;

/** Easing curves: map linear progress 0..1 to eased progress. */
@FunctionalInterface
public interface Easing {
	float apply(float t);

	Easing LINEAR = t -> t;
	/** The default UI curve: fast out, soft settle. Same as CSS cubic-bezier(0.2, 0.9, 0.25, 1). */
	Easing UI = cubicBezier(0.2f, 0.9f, 0.25f, 1f);
	Easing OUT_CUBIC = t -> 1 - (1 - t) * (1 - t) * (1 - t);
	Easing IN_CUBIC = t -> t * t * t;
	Easing IN_OUT_CUBIC = t -> t < 0.5f ? 4 * t * t * t : 1 - (float) Math.pow(-2 * t + 2, 3) / 2;
	Easing OUT_QUART = t -> 1 - (float) Math.pow(1 - t, 4);
	/** Slight overshoot, for small pops (toggle knobs, pills). */
	Easing OUT_BACK = t -> {
		float c1 = 1.70158f;
		float c3 = c1 + 1;
		float u = t - 1;
		return 1 + c3 * u * u * u + c1 * u * u;
	};

	/** CSS-style cubic bezier from (0,0) to (1,1) with control points (x1,y1), (x2,y2). */
	static Easing cubicBezier(float x1, float y1, float x2, float y2) {
		return t -> {
			if (t <= 0) {
				return 0;
			}
			if (t >= 1) {
				return 1;
			}
			// Solve x(s) = t for s with Newton's method, falling back to bisection.
			float s = t;
			for (int i = 0; i < 8; i++) {
				float x = bezier(s, x1, x2) - t;
				if (Math.abs(x) < 1e-5f) {
					return bezier(s, y1, y2);
				}
				float dx = bezierDerivative(s, x1, x2);
				if (Math.abs(dx) < 1e-6f) {
					break;
				}
				s -= x / dx;
			}
			float lo = 0;
			float hi = 1;
			s = t;
			for (int i = 0; i < 20; i++) {
				float x = bezier(s, x1, x2);
				if (Math.abs(x - t) < 1e-5f) {
					break;
				}
				if (x < t) {
					lo = s;
				} else {
					hi = s;
				}
				s = (lo + hi) / 2;
			}
			return bezier(s, y1, y2);
		};
	}

	private static float bezier(float s, float p1, float p2) {
		float u = 1 - s;
		return 3 * u * u * s * p1 + 3 * u * s * s * p2 + s * s * s;
	}

	private static float bezierDerivative(float s, float p1, float p2) {
		float u = 1 - s;
		return 3 * u * u * p1 + 6 * u * s * (p2 - p1) + 3 * s * s * (1 - p2);
	}
}
