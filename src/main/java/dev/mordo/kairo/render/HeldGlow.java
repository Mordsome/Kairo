package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.cosmetics.Geo;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Held item "Glow": a soft, slowly pulsing halo around the item in your hand, in the shimmer colour (your accent
 * when the shimmer is vanilla). Three crossed discs whose colour fades out at the edge, so it reads as a glowing
 * blob from any angle.
 */
public final class HeldGlow {
	private static final int SEGMENTS = 28;

	private HeldGlow() {
	}

	/** The glow's colour: the chosen shimmer colour, or the accent. */
	public static int color() {
		GameVisuals v = GameVisuals.INSTANCE;
		return switch (v.glintColor.get()) {
			case RAINBOW -> Colors.hsv((System.currentTimeMillis() % 6000L) / 6000f, 0.75f, 1f);
			case CUSTOM -> v.glintCustom.argb() | 0xFF000000;
			default -> Theme.accent() | 0xFF000000;
		};
	}

	/**
	 * Draws the halo for the item about to be drawn with {@code poseStack} (the hand's pose, before the item's own
	 * display transform). {@code side} is +1 for the right hand, -1 for the left.
	 */
	public static void draw(PoseStack poseStack, SubmitNodeCollector collector, float side) {
		// Up along the blade from the grip (vanilla's first-person handheld transform applied to a point on the item).
		org.joml.Vector3f c = new org.joml.Vector3f(0.18f * 0.68f, 0.18f * 0.68f, 0);
		new org.joml.Quaternionf().rotationXYZ(0, (float) Math.toRadians(-90), (float) Math.toRadians(25)).transform(c);
		c.add(1.13f / 16, 3.2f / 16, 1.13f / 16);
		float cx = c.x * side;
		float cy = c.y;
		float cz = c.z;
		float t = System.nanoTime() / 1e9f;
		float pulse = 0.8f + 0.2f * (float) Math.sin(t * 2.4f);
		int color = color();
		float radius = 0.55f * pulse;
		collector.submitCustomGeometry(poseStack, Geo.glowType(), (pose, buffer) -> {
			int inner = Colors.withAlpha(color, 0.5f * pulse);
			int mid = Colors.withAlpha(color, 0.2f * pulse);
			int outer = Colors.withAlpha(color, 0);
			for (int plane = 0; plane < 3; plane++) {
				ring(pose, buffer, plane, cx, cy, cz, 0, radius * 0.35f, inner, mid);
				ring(pose, buffer, plane, cx, cy, cz, radius * 0.35f, radius, mid, outer);
			}
		});
	}

	private static void ring(PoseStack.Pose pose, VertexConsumer b, int plane, float cx, float cy, float cz, float r0, float r1, int c0, int c1) {
		for (int i = 0; i < SEGMENTS; i++) {
			double a0 = i * Math.PI * 2 / SEGMENTS;
			double a1 = (i + 1) * Math.PI * 2 / SEGMENTS;
			float[] p00 = point(plane, cx, cy, cz, a0, r0);
			float[] p01 = point(plane, cx, cy, cz, a1, r0);
			float[] p10 = point(plane, cx, cy, cz, a0, r1);
			float[] p11 = point(plane, cx, cy, cz, a1, r1);
			// Both windings, so each disc shows from either side.
			vertex(pose, b, p00, c0);
			vertex(pose, b, p10, c1);
			vertex(pose, b, p11, c1);
			vertex(pose, b, p01, c0);
			vertex(pose, b, p01, c0);
			vertex(pose, b, p11, c1);
			vertex(pose, b, p10, c1);
			vertex(pose, b, p00, c0);
		}
	}

	/** A point on a circle in plane 0 (xy), 1 (yz) or 2 (xz). */
	private static float[] point(int plane, float cx, float cy, float cz, double a, float r) {
		float u = (float) Math.cos(a) * r;
		float v = (float) Math.sin(a) * r;
		return switch (plane) {
			case 0 -> new float[]{cx + u, cy + v, cz};
			case 1 -> new float[]{cx, cy + u, cz + v};
			default -> new float[]{cx + u, cy, cz + v};
		};
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer b, float[] p, int argb) {
		b.addVertex(pose, p[0], p[1], p[2]).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0).setNormal(pose, 0, 1, 0);
	}
}