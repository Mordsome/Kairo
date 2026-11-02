package dev.mordo.kairo.render;

import dev.mordo.kairo.core.ClientSettings;

/**
 * A time-based tween of one float. Never tick-based: it reads the clock when asked, so it is smooth at any FPS.
 * Durations are divided by the global animation speed setting.
 *
 * <pre>
 * Animation hover = new Animation(0);
 * hover.animateTo(1, 300);          // in an event handler
 * float t = hover.value();          // while rendering
 * </pre>
 */
public final class Animation {
	private float from;
	private float to;
	private long startNanos;
	private float durationNanos;
	private Easing easing = Easing.UI;

	public Animation(float initial) {
		this.from = initial;
		this.to = initial;
	}

	public static long now() {
		return System.nanoTime();
	}

	public static float speed() {
		return ClientSettings.INSTANCE.animationSpeed.floatValue();
	}

	/** True when the player asked for fades only (no scale, slide or rotation). */
	public static boolean reduceMotion() {
		return ClientSettings.INSTANCE.reduceMotion.isOn();
	}

	/** Returns {@code moving} normally, or {@code rest} when reduce motion is on. */
	public static float motion(float moving, float rest) {
		return reduceMotion() ? rest : moving;
	}

	public Animation animateTo(float target, float durationMs) {
		return animateTo(target, durationMs, Easing.UI, 0);
	}

	public Animation animateTo(float target, float durationMs, Easing easing) {
		return animateTo(target, durationMs, easing, 0);
	}

	/** Starts from the current (possibly mid-flight) value, so retargeting never jumps. */
	public Animation animateTo(float target, float durationMs, Easing easing, float delayMs) {
		if (target == to) {
			return this; // already there or already heading there
		}
		this.from = value();
		this.to = target;
		this.easing = easing;
		float speed = speed();
		this.durationNanos = Math.max(1f, durationMs * 1_000_000f / speed);
		this.startNanos = now() + (long) (delayMs * 1_000_000f / speed);
		return this;
	}

	/** Jumps straight to a value. */
	public Animation set(float value) {
		this.from = value;
		this.to = value;
		this.durationNanos = 0;
		return this;
	}

	public float value() {
		if (durationNanos <= 0) {
			return to;
		}
		float t = (now() - startNanos) / durationNanos;
		if (t <= 0) {
			return from;
		}
		if (t >= 1) {
			return to;
		}
		return from + (to - from) * easing.apply(t);
	}

	/** Linear progress 0..1 of the current tween (useful for staggering). */
	public float progress() {
		if (durationNanos <= 0) {
			return 1;
		}
		return Math.clamp((now() - startNanos) / durationNanos, 0f, 1f);
	}

	public float target() {
		return to;
	}

	public boolean isDone() {
		return durationNanos <= 0 || now() - startNanos >= durationNanos;
	}

	public static float lerp(float a, float b, float t) {
		return a + (b - a) * t;
	}
}
