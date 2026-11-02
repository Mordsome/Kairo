package dev.mordo.kairo.cosmetics;

import dev.mordo.kairo.cosmetics.Cosmetics.WingStyle;
import dev.mordo.kairo.render.Colors;

/**
 * Wing models, built in the body's space (pixels; y down, the back is +z). Each wing is laid out in its own plane:
 * {@code s} runs outwards along the span, {@code t} downwards; the plane sweeps back from the hinge by the flap
 * angle. Feathered (angel, phoenix, seraph), membrane (dragon, bat, demon), energy, skeletal and crystal share that layout.
 */
final class Wings {
	private Wings() {
	}

	/**
	 * Flap phase, advanced a little each frame. (Using sin(time * speed) made the wings jump to a different point in
	 * the flap whenever the speed changed, i.e. when you started or stopped walking.)
	 */
	private static double phase;
	private static float smoothMoving;
	private static long lastFrame;

	/** Draws both wings. {@code time} in seconds, {@code moving} 0..1 (faster flapping while walking). */
	static void draw(Geo geo, Cosmetics c, float time, float moving) {
		WingStyle style = c.wings.get();
		float size = c.wingSize.floatValue();
		float speed = c.wingFlap.floatValue();
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		// Ease into the faster walking flap instead of switching at once.
		smoothMoving += (moving - smoothMoving) * Math.min(1, dt * 4);
		phase = (phase + dt * (2.2f + smoothMoving * 3.5f) * speed) % (Math.PI * 2000);
		float flap = speed <= 0 ? 0 : (float) Math.sin(phase);
		float sweep = (float) Math.toRadians(28 + flap * 14);
		float lift = flap * 0.6f;
		for (int side = -1; side <= 1; side += 2) {
			Wing w = new Wing(geo, c, side, sweep, size, time, lift, 0, 0);
			switch (style) {
				case ANGEL -> w.feathered(false);
				case PHOENIX -> w.feathered(true);
				case DRAGON -> w.membrane(false, false);
				case BAT -> w.membrane(true, false);
				case DEMON -> w.membrane(false, true);
				case SERAPH -> {
					// Three pairs: a small one angled up, the main pair, a small one angled down.
					new Wing(geo, c, side, sweep + 0.3f, size * 0.7f, time + 0.25f, lift, -1.0f, -2.5f).feathered(false);
					w.feathered(false);
					new Wing(geo, c, side, sweep + 0.35f, size * 0.6f, time + 0.5f, lift, 0.5f, 3.5f).feathered(false);
				}
				case ENERGY -> w.energy();
				case CRYSTAL -> w.crystal();
				case SKELETAL -> w.skeletal();
				case NONE -> {
				}
			}
		}
	}

	private static final class Wing {
		private final Geo geo;
		private final Cosmetics c;
		private final int side;
		private final float cos;
		private final float sin;
		private final float size;
		private final float time;
		private final float lift;
		private final boolean glow;
		private final float[] hinge;
		private final float tiltCos;
		private final float tiltSin;
		/**
		 * Offset (pixels) off the wing's plane, towards the back. Every overlapping piece gets its own, so no two faces
		 * share a depth (shared depths flicker against each other as the view moves).
		 */
		private float layer;

		/** {@code tilt}: turns the wing's plane (negative = up); {@code drop}: moves the hinge down. */
		Wing(Geo geo, Cosmetics c, int side, float sweep, float size, float time, float lift, float tilt, float drop) {
			this.geo = geo;
			this.c = c;
			this.side = side;
			this.cos = (float) Math.cos(sweep);
			this.sin = (float) Math.sin(sweep);
			this.size = size;
			this.time = time;
			this.lift = lift;
			this.glow = c.wingGlow.isOn();
			// Player's right is -x in model space; hinges sit on the upper back.
			this.hinge = new float[]{side * 1.2f, 2.5f + drop, 2.3f};
			this.tiltCos = (float) Math.cos(tilt);
			this.tiltSin = (float) Math.sin(tilt);
		}

		/** A point of the wing plane in body space. */
		private float[] p(float s, float t) {
			s *= size;
			t *= size;
			t -= lift * s * 0.15f;
			float rs = s * tiltCos - t * tiltSin;
			float rt = s * tiltSin + t * tiltCos;
			s = rs;
			t = rt;
			return new float[]{hinge[0] + side * cos * s - side * sin * layer, hinge[1] + t, hinge[2] + sin * s + cos * layer};
		}

		private int color1(float k) {
			return c.wingRainbow.isOn() ? Colors.hsv((time * 0.15f + k * 0.35f) % 1f, 0.7f, 1f) : c.wingColor.argb() | 0xFF000000;
		}

		private int color2(float k) {
			return c.wingRainbow.isOn() ? Colors.hsv((time * 0.15f + k * 0.35f + 0.15f) % 1f, 0.8f, 1f) : c.wingColor2.argb() | 0xFF000000;
		}

		private int mix(float k) {
			return Colors.lerp(color1(k), color2(k), k);
		}

		// ---- feathered (angel, phoenix) ----

		void feathered(boolean fire) {
			float span = 16;
			int n = 10;
			layer = 0;
			float[] prev = p(0, 0);
			// The arm: an arc that rises then falls towards the tip.
			for (int i = 1; i <= 12; i++) {
				float s = span * i / 12f;
				float[] next = p(s, -7 * (float) Math.sin(Math.PI * s / span * 0.85));
				geo.stick(prev, next, 1.6f * size, 1.2f * size, fire ? 0xFFFFB000 : color1(0), glow || fire);
				prev = next;
			}
			for (int i = 0; i < n; i++) {
				float k = i / (float) (n - 1);
				float s = span * (0.12f + 0.85f * k);
				float t = -7 * (float) Math.sin(Math.PI * s / span * 0.85);
				float len = 6 + 9 * k;
				int col = fire ? Colors.lerp(0xFFFF4A1A, 0xFFFFE07A, k) : mix(k);
				// Long flight feathers, angled down and out (each on its own layer).
				layer = 0.2f + i * 0.16f;
				geo.stick(p(s, t), p(s + 2 + k * 3, t + len), 2.6f * size, 0.5f * size, col, glow || fire);
				// Shorter covert feathers over them.
				int cover = fire ? Colors.lerp(0xFFFF8A00, 0xFFFFF2B0, k) : Colors.lerp(color1(k), 0xFFFFFFFF, 0.25f);
				layer = -0.45f - i * 0.16f;
				geo.stick(p(s - 0.5f, t + 0.5f), p(s + 1, t + len * 0.5f), 2.8f * size, 0.6f * size, cover, glow || fire);
			}
		}

		// ---- membrane (dragon, bat, demon) ----

		void membrane(boolean bat, boolean demon) {
			float[] shoulder = p(0, 0);
			float[] elbow = p(7, -6);
			float[] wrist = p(13, -4);
			float[][] tips = bat
				? new float[][]{p(18, 1), p(16, 7), p(12, 11), p(7, 12), p(2, 10)}
				: new float[][]{p(20, 0), p(17, 8), p(12, 13), p(6, 14), p(1, 11)};
			int bone = demon ? 0xFF1A0A0A : Colors.lerp(color2(0), 0xFF000000, 0.35f);
			int skin = demon ? 0xF0A0141C : Colors.withAlpha(color1(0.5f), 0.92f);
			int edge = demon ? 0xF02A0000 : Colors.withAlpha(color2(1), 0.95f);
			geo.stick(shoulder, elbow, 1.6f * size, 1.4f * size, bone, glow);
			geo.stick(elbow, wrist, 1.4f * size, 1.2f * size, bone, glow);
			for (float[] tip : tips) {
				geo.stick(wrist, tip, 0.9f * size, 0.8f * size, bone, glow);
			}
			// Membrane between the fingers, scalloped towards the wrist between tips. Each piece sits on its own layer.
			float[] prevTip = elbow;
			for (int i = 0; i <= tips.length; i++) {
				float[] tip = i < tips.length ? tips[i] : shoulder;
				float[] mid = lerp(lerp(prevTip, tip, 0.5f), wrist, bat ? 0.35f : 0.2f);
				float off = 0.06f + i * 0.09f;
				geo.tri2(nudge(wrist, off), nudge(prevTip, off), nudge(mid, off), skin, edge, edge, glow);
				geo.tri2(nudge(wrist, off + 0.045f), nudge(mid, off + 0.045f), nudge(tip, off + 0.045f), skin, edge, edge, glow);
				prevTip = tip;
			}
			geo.tri2(nudge(shoulder, -0.12f), nudge(elbow, -0.12f), nudge(wrist, -0.12f), skin, skin, skin, glow);
			if (demon) {
				// Spikes on the wrist and tips.
				geo.stick(wrist, p(15, -9), 0.9f * size, 0.9f * size, 0xFF2A0505, glow);
				for (float[] tip : tips) {
					float[] out = new float[]{tip[0] + side * 1.2f * size, tip[1] + 1.5f * size, tip[2]};
					geo.stick(tip, out, 0.6f * size, 0.6f * size, 0xFF2A0505, glow);
				}
			}
		}

		// ---- energy: glowing line-art wings with a pulse running out along them ----

		void energy() {
			float span = 17;
			float[] prev = p(0, 0);
			float[] prevTip = null;
			for (int i = 1; i <= 12; i++) {
				float s = span * i / 12f;
				float[] next = p(s, -7 * (float) Math.sin(Math.PI * s / span * 0.85));
				float pulse = pulse(i / 12f);
				geo.stick(prev, next, 0.8f * size, 0.8f * size, Colors.lerp(color1(0), 0xFFFFFFFF, pulse * 0.6f), true);
				prev = next;
			}
			int n = 8;
			for (int i = 0; i < n; i++) {
				float k = i / (float) (n - 1);
				float s = span * (0.12f + 0.85f * k);
				float t = -7 * (float) Math.sin(Math.PI * s / span * 0.85);
				float len = 6 + 9 * k;
				float[] root = p(s, t);
				float[] tip = p(s + 2 + k * 3, t + len);
				float pulse = pulse(k);
				int line = Colors.lerp(mix(k), 0xFFFFFFFF, pulse * 0.7f);
				geo.stick(root, tip, 0.45f * size, 0.45f * size, line, true);
				// A faint fill between this feather and the last one.
				if (prevTip != null) {
					int fill = Colors.withAlpha(mix(k), 0.18f + 0.25f * pulse);
					float off = -0.3f - i * 0.12f;
					geo.tri2(nudge(root, off), nudge(prevTip, off), nudge(tip, off), fill, fill, fill, true);
				}
				prevTip = tip;
			}
		}

		/** 0..1 brightness of a wave that travels from the shoulder to the tip. */
		private float pulse(float k) {
			float wave = (float) Math.sin(time * 4 - k * 6);
			return Math.max(0, wave) * Math.max(0, wave);
		}

		// ---- skeletal: dragon bones with a ghostly membrane ----

		void skeletal() {
			float[] shoulder = p(0, 0);
			float[] elbow = p(7, -6);
			float[] wrist = p(13, -4);
			float[][] tips = {p(20, 0), p(17, 8), p(12, 13), p(6, 14), p(1, 11)};
			int bone = c.wingRainbow.isOn() ? color1(0) : 0xFFE8E2D0;
			int joint = 0xFFC9C1AA;
			geo.stick(shoulder, elbow, 1.5f * size, 1.5f * size, bone, glow);
			geo.stick(elbow, wrist, 1.3f * size, 1.3f * size, bone, glow);
			for (float[] knuckle : new float[][]{elbow, wrist}) {
				geo.cube(knuckle[0], knuckle[1], knuckle[2], 2.2f * size, 2.2f * size, 2.2f * size, joint, glow);
			}
			for (float[] tip : tips) {
				// Two finger bones with a knuckle between them.
				float[] mid = lerp(wrist, tip, 0.55f);
				geo.stick(wrist, mid, 0.8f * size, 0.8f * size, bone, glow);
				geo.stick(mid, tip, 0.6f * size, 0.6f * size, bone, glow);
				geo.cube(mid[0], mid[1], mid[2], 1.1f * size, 1.1f * size, 1.1f * size, joint, glow);
			}
			// Ghostly membrane in the wing colour, breathing slowly.
			float breathe = 0.16f + 0.08f * (float) Math.sin(time * 1.6f);
			int ghost = Colors.withAlpha(color2(0.5f), breathe);
			float[] prevTip = elbow;
			for (int i = 0; i <= tips.length; i++) {
				float[] tip = i < tips.length ? tips[i] : shoulder;
				float off = -0.4f - i * 0.12f;
				geo.tri2(nudge(wrist, off), nudge(prevTip, off), nudge(tip, off), ghost, ghost, ghost, true);
				prevTip = tip;
			}
		}

		// ---- crystal ----

		void crystal() {
			float[] root = p(0, 0);
			float[][] dirs = {{17, -10}, {19, -3}, {16, 4}, {11, 9}, {6, 11}};
			for (int i = 0; i < dirs.length; i++) {
				float k = i / (float) (dirs.length - 1);
				float shimmer = (float) (0.75 + 0.25 * Math.sin(time * 3 + i));
				int col = Colors.withAlpha(Colors.lerp(color1(k), color2(k), k), 0.85f * shimmer);
				float[] tip = nudge(p(dirs[i][0], dirs[i][1]), i * 0.35f);
				float[] mid = lerp(nudge(root, i * 0.35f), tip, 0.55f);
				geo.stick(nudge(root, i * 0.35f), mid, 2.8f * size, 1.6f * size, col, true);
				geo.stick(mid, tip, 1.4f * size, 0.9f * size, Colors.lerp(col, 0xFFFFFFFF, 0.35f), true);
			}
		}

		/** A point moved {@code amount} pixels off the wing plane (towards the back). */
		private float[] nudge(float[] point, float amount) {
			return new float[]{point[0] - side * sin * amount, point[1], point[2] + cos * amount};
		}

		private static float[] lerp(float[] a, float[] b, float t) {
			return new float[]{a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t, a[2] + (b[2] - a[2]) * t};
		}
	}
}
