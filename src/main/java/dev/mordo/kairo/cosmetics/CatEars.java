package dev.mordo.kairo.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

/**
 * Cat ear fit and life: spacing, outward angle and size from the settings, plus (optional) a slow idle sway, a quick
 * flick every few seconds (sometimes both ears together) and ears lying back while you run.
 */
final class CatEars {
	private static final float PERIOD = 2.8f;
	private static final float FLICK = 0.42f;

	private CatEars() {
	}

	/** Moves the pose so the ear on {@code side} (-1 or 1) is placed and animated around its base. */
	static void pose(PoseStack ps, Cosmetics c, int side, float time, float moving) {
		float bx = side * 2.6f / 16f;
		float by = -8 / 16f;
		float bz = -2 / 16f;
		ps.translate(side * c.earSpacing.floatValue() / 16f, 0, 0);
		ps.translate(bx, by, bz);
		float size = c.earSize.floatValue();
		ps.scale(size, size, size);

		float outward = c.earAngle.floatValue();
		float back = 0;
		if (c.earTwitch.isOn()) {
			outward += (float) Math.sin(time * 1.4f + side * 1.1f) * 2.5f;
			back += moving * 16;
			float flick = flick(time, side);
			outward += flick * 24;
			back += Math.abs(flick) * 14;
		}
		ps.rotate(Axis.ZP, (float) Math.toRadians(side * outward));
		ps.rotate(Axis.XP, (float) Math.toRadians(-back));
		ps.translate(-bx, -by, -bz);
	}

	/** -1..1: a short damped wiggle a few times a minute, at a random moment in each slot. */
	private static float flick(float time, int side) {
		int slot = (int) Math.floor(time / PERIOD);
		boolean together = hash(slot * 31 + 7) > 0.55f;
		float offset = hash(together ? slot * 17 : slot * 17 + (side > 0 ? 3 : 11)) * (PERIOD - FLICK);
		float local = time - slot * PERIOD - offset;
		if (local <= 0 || local >= FLICK) {
			return 0;
		}
		float k = local / FLICK;
		return (float) Math.sin(k * Math.PI * 3) * (1 - k);
	}

	private static float hash(int n) {
		int h = n * 374761393 + 668265263;
		h = (h ^ (h >>> 13)) * 1274126177;
		return ((h ^ (h >>> 16)) & 0xFFFF) / 65535f;
	}
}
