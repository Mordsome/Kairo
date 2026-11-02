package dev.mordo.kairo.sound;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/**
 * Built-in sounds, generated from maths at startup (no audio files shipped, nothing to license).
 * Each is a few short oscillators with an envelope. Game sounds are ~0.1–0.9 s; UI sounds are quieter and shorter.
 */
public final class Synth {
	private static final int RATE = PcmSound.RATE;
	private static final float TAU = (float) (Math.PI * 2);

	/** Choosable in the sound pickers, in display order: id -> display name. */
	public static final Map<String, String> GAME_SOUNDS = new LinkedHashMap<>();

	static {
		GAME_SOUNDS.put("beep", "Beep");
		GAME_SOUNDS.put("double_beep", "Double beep");
		GAME_SOUNDS.put("ding", "Ding");
		GAME_SOUNDS.put("chime", "Chime");
		GAME_SOUNDS.put("bell", "Bell");
		GAME_SOUNDS.put("click", "Click");
		GAME_SOUNDS.put("pop", "Pop");
		GAME_SOUNDS.put("thud", "Thud");
		GAME_SOUNDS.put("blip", "Blip");
		GAME_SOUNDS.put("zap", "Zap");
		GAME_SOUNDS.put("coin", "Coin");
		GAME_SOUNDS.put("crunch", "Crunch");
	}

	private Synth() {
	}

	/** Generates a sound by id (game or UI), or null for an unknown id. */
	public static PcmSound generate(String id) {
		float[] w = switch (id) {
			case "beep" -> tone(880, 0.14f, 0.004f, 0.05f, 0.55f);
			case "double_beep" -> concat(tone(1046, 0.07f, 0.003f, 0.02f, 0.5f), silence(0.045f), tone(1046, 0.07f, 0.003f, 0.025f, 0.5f));
			case "ding" -> bell(1568, 0.9f, 0.6f);
			case "chime" -> mix(bell(1318.5f, 0.7f, 0.42f), shift(bell(1975.5f, 0.6f, 0.36f), 0.09f));
			case "bell" -> bell(784, 1.1f, 0.6f);
			case "click" -> click(0.6f);
			case "pop" -> pop(0.7f);
			case "thud" -> thud();
			case "blip" -> square(1400, 0.06f, 0.35f);
			case "zap" -> sweep(300, 2400, 0.13f, 0.4f);
			case "coin" -> concat(square(988, 0.07f, 0.3f), squareDecay(1318.5f, 0.28f, 0.3f));
			case "crunch" -> crunch();
			// Sound packs (Hit Sounds → Sound pack): a hit, a critical hit and a kill each.
			case "arcade_hit" -> square(1200, 0.05f, 0.3f);
			case "arcade_crit" -> concat(square(1046.5f, 0.045f, 0.3f), square(1568, 0.07f, 0.3f));
			case "arcade_kill" -> concat(square(1046.5f, 0.06f, 0.28f), square(1318.5f, 0.06f, 0.28f), square(1568, 0.06f, 0.28f),
				squareDecay(2093, 0.3f, 0.28f));
			case "crystal_hit" -> bell(2093, 0.28f, 0.45f);
			case "crystal_crit" -> mix(bell(2349, 0.4f, 0.4f), shift(bell(3136, 0.35f, 0.3f), 0.05f));
			case "crystal_kill" -> mix(mix(bell(1318.5f, 0.9f, 0.4f), shift(bell(1661, 0.85f, 0.36f), 0.08f)),
				mix(shift(bell(1975.5f, 0.8f, 0.34f), 0.16f), shift(bell(2637, 0.9f, 0.3f), 0.24f)));
			case "heavy_hit" -> punch(0.8f, 1);
			case "heavy_crit" -> mix(punch(0.9f, 0.8f), crunch());
			case "heavy_kill" -> boom();
			case "void_hit" -> sweep(1800, 420, 0.09f, 0.35f);
			case "void_crit" -> mix(sweep(2400, 300, 0.14f, 0.35f), shift(sweep(1600, 500, 0.08f, 0.25f), 0.05f));
			case "void_kill" -> mix(whoosh(0.45f, 0.16f, true), shift(mix(bell(392, 1.1f, 0.5f), boom()), 0.3f));
			case "bubble_hit" -> pop(0.65f);
			case "bubble_crit" -> concat(pop(0.6f), pop(0.7f));
			case "bubble_kill" -> mix(mix(pop(0.6f), shift(pop(0.65f), 0.07f)), shift(bell(1568, 0.5f, 0.35f), 0.15f));
			// UI sounds: soft, short, close to the ear.
			case "ui_click" -> click(0.22f);
			case "ui_hover" -> tone(2200, 0.018f, 0.002f, 0.014f, 0.04f);
			case "ui_toggle_on" -> concat(tone(880, 0.035f, 0.002f, 0.02f, 0.12f), tone(1320, 0.05f, 0.002f, 0.04f, 0.12f));
			case "ui_toggle_off" -> concat(tone(1320, 0.035f, 0.002f, 0.02f, 0.11f), tone(880, 0.05f, 0.002f, 0.04f, 0.11f));
			case "ui_open" -> whoosh(0.22f, 0.12f, true);
			case "ui_close" -> whoosh(0.18f, 0.09f, false);
			case "ui_pvp_on" -> mix(bell(1046.5f, 0.45f, 0.18f), shift(bell(1568, 0.45f, 0.16f), 0.07f));
			case "ui_pvp_off" -> mix(bell(1568, 0.4f, 0.16f), shift(bell(1046.5f, 0.45f, 0.15f), 0.07f));
			default -> null;
		};
		return w == null ? null : PcmSound.fromFloats(w, RATE);
	}

	// ---- building blocks ----

	private static int samples(float seconds) {
		return Math.max(1, Math.round(seconds * RATE));
	}

	/** Sine with a linear attack and release. */
	private static float[] tone(float freq, float seconds, float attack, float release, float gain) {
		float[] w = new float[samples(seconds)];
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			w[i] = (float) Math.sin(TAU * freq * t) * envelope(t, seconds, attack, release) * gain;
		}
		return w;
	}

	/** A few inharmonic partials with exponential decay: bell / ding. */
	private static float[] bell(float freq, float seconds, float gain) {
		float[] ratios = {1, 2.76f, 5.4f, 8.93f};
		float[] amps = {1, 0.42f, 0.18f, 0.07f};
		float[] decay = {seconds * 0.32f, seconds * 0.16f, seconds * 0.08f, seconds * 0.04f};
		float[] w = new float[samples(seconds)];
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			float v = 0;
			for (int p = 0; p < ratios.length; p++) {
				v += (float) Math.sin(TAU * freq * ratios[p] * t) * amps[p] * (float) Math.exp(-t / decay[p]);
			}
			w[i] = v * gain * Math.min(1, t / 0.002f) * 0.6f;
		}
		return w;
	}

	/** Soft tick: a filtered noise burst plus a short high sine. */
	private static float[] click(float gain) {
		float seconds = 0.035f;
		float[] w = new float[samples(seconds)];
		Random random = new Random(7);
		float lp = 0;
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			lp += (random.nextFloat() * 2 - 1 - lp) * 0.35f;
			float noise = lp * (float) Math.exp(-t / 0.0025f);
			float tick = (float) Math.sin(TAU * 2600 * t) * (float) Math.exp(-t / 0.006f);
			w[i] = (noise * 0.9f + tick * 0.6f) * gain;
		}
		return w;
	}

	/** Bubble pop: a fast downward pitch sweep. */
	private static float[] pop(float gain) {
		float seconds = 0.1f;
		float[] w = new float[samples(seconds)];
		double phase = 0;
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			float f = 900 * (float) Math.exp(-t / 0.035f) + 160;
			phase += TAU * f / RATE;
			w[i] = (float) Math.sin(phase) * (float) Math.exp(-t / 0.03f) * Math.min(1, t / 0.0015f) * gain;
		}
		return w;
	}

	private static float[] thud() {
		float seconds = 0.22f;
		float[] w = new float[samples(seconds)];
		Random random = new Random(3);
		double phase = 0;
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			float f = 140 * (float) Math.exp(-t / 0.08f) + 55;
			phase += TAU * f / RATE;
			float body = (float) Math.sin(phase) * (float) Math.exp(-t / 0.07f);
			float hit = (random.nextFloat() * 2 - 1) * (float) Math.exp(-t / 0.004f) * 0.5f;
			w[i] = (body + hit) * 0.8f * Math.min(1, t / 0.001f);
		}
		return w;
	}

	/** A short, punchy hit: a falling low tone with a noise crack on top. */
	private static float[] punch(float gain, float pitch) {
		float seconds = 0.16f;
		float[] w = new float[samples(seconds)];
		Random random = new Random(19);
		double phase = 0;
		float lp = 0;
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			float f = (220 * (float) Math.exp(-t / 0.03f) + 70) * pitch;
			phase += TAU * f / RATE;
			lp += (random.nextFloat() * 2 - 1 - lp) * 0.5f;
			float body = (float) Math.sin(phase) * (float) Math.exp(-t / 0.05f);
			float crack = lp * (float) Math.exp(-t / 0.008f) * 0.7f;
			w[i] = (body + crack) * gain * Math.min(1, t / 0.001f);
		}
		return w;
	}

	/** A deep boom with a soft rumble. */
	private static float[] boom() {
		float seconds = 0.7f;
		float[] w = new float[samples(seconds)];
		Random random = new Random(23);
		double phase = 0;
		float lp = 0;
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			float f = 90 * (float) Math.exp(-t / 0.15f) + 38;
			phase += TAU * f / RATE;
			lp += (random.nextFloat() * 2 - 1 - lp) * 0.06f;
			float body = (float) Math.sin(phase) * (float) Math.exp(-t / 0.22f);
			float rumble = lp * 3 * (float) Math.exp(-t / 0.3f);
			w[i] = (body * 0.8f + rumble * 0.5f) * Math.min(1, t / 0.002f);
		}
		return w;
	}

	private static float[] crunch() {
		float seconds = 0.12f;
		float[] w = new float[samples(seconds)];
		Random random = new Random(11);
		float hold = 0;
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			if (i % 6 == 0) {
				hold = random.nextFloat() * 2 - 1; // crude sample-and-hold: gritty
			}
			w[i] = hold * (float) Math.exp(-t / 0.03f) * 0.55f;
		}
		return w;
	}

	/** Square-ish (odd harmonics, softened) for retro blips. */
	private static float[] square(float freq, float seconds, float gain) {
		float[] w = new float[samples(seconds)];
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			float v = (float) (Math.sin(TAU * freq * t) + Math.sin(3 * TAU * freq * t) / 3 + Math.sin(5 * TAU * freq * t) / 5);
			w[i] = v * envelope(t, seconds, 0.002f, 0.015f) * gain;
		}
		return w;
	}

	private static float[] squareDecay(float freq, float seconds, float gain) {
		float[] w = square(freq, seconds, gain);
		for (int i = 0; i < w.length; i++) {
			w[i] *= (float) Math.exp(-(i / (float) RATE) / (seconds * 0.35f));
		}
		return w;
	}

	private static float[] sweep(float from, float to, float seconds, float gain) {
		float[] w = new float[samples(seconds)];
		double phase = 0;
		for (int i = 0; i < w.length; i++) {
			float t = i / (float) RATE;
			float f = from * (float) Math.pow(to / from, t / seconds);
			phase += TAU * f / RATE;
			w[i] = (float) Math.sin(phase) * envelope(t, seconds, 0.003f, 0.04f) * gain;
		}
		return w;
	}

	/** Soft air: low-passed noise with a rising (or falling) cutoff and a bell-shaped envelope. */
	private static float[] whoosh(float seconds, float gain, boolean rising) {
		float[] w = new float[samples(seconds)];
		Random random = new Random(5);
		float lp = 0;
		for (int i = 0; i < w.length; i++) {
			float p = i / (float) w.length;
			float cutoff = rising ? 0.02f + 0.18f * p : 0.2f - 0.18f * p;
			lp += (random.nextFloat() * 2 - 1 - lp) * cutoff;
			float env = (float) Math.sin(Math.PI * p);
			w[i] = lp * env * env * gain * 3;
		}
		return w;
	}

	private static float envelope(float t, float length, float attack, float release) {
		float a = attack <= 0 ? 1 : Math.min(1, t / attack);
		float r = release <= 0 ? 1 : Math.min(1, (length - t) / release);
		return Math.max(0, Math.min(a, r));
	}

	private static float[] silence(float seconds) {
		return new float[samples(seconds)];
	}

	private static float[] concat(float[]... parts) {
		int n = 0;
		for (float[] p : parts) {
			n += p.length;
		}
		float[] out = new float[n];
		int o = 0;
		for (float[] p : parts) {
			System.arraycopy(p, 0, out, o, p.length);
			o += p.length;
		}
		return out;
	}

	/** Delays a sound by {@code seconds} (prepends silence). */
	private static float[] shift(float[] w, float seconds) {
		return concat(silence(seconds), w);
	}

	private static float[] mix(float[] a, float[] b) {
		float[] out = new float[Math.max(a.length, b.length)];
		for (int i = 0; i < out.length; i++) {
			out[i] = (i < a.length ? a[i] : 0) + (i < b.length ? b[i] : 0);
		}
		return out;
	}
}
