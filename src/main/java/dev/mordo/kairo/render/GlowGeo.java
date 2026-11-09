package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Vector3f;

/**
 * Small building blocks for Kairo Void's world effects: glowing quads, lines that always face you (or lie in a
 * plane), rings and discs, all full-bright and see-through, and the colours the effects can use. Coordinates are
 * camera-relative (the camera sits at 0, 0, 0).
 */
public final class GlowGeo {
	private GlowGeo() {
	}

	// ---- colours ----

	/** A colour for an effect: {@code t} is a position 0..1 along it (rainbow and two-tone use it). */
	public static int color(VoidSettings.FxColor mode, float t, float time) {
		int accent = Theme.accent() | 0xFF000000;
		return switch (mode) {
			case ACCENT -> accent;
			case RAINBOW -> Colors.hsv((t + time * 0.08f) % 1f, 0.7f, 1f);
			case DUO -> Colors.lerp(accent, 0xFFFFFFFF, 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2 + time * 1.5));
			case CUSTOM -> VoidSettings.INSTANCE.fxCustomColor.argb() | 0xFF000000;
		};
	}

	// ---- shapes ----

	/** A quad, both sides. */
	public static void quad(PoseStack.Pose pose, VertexConsumer b, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int c0, int c1, int c2, int c3) {
		vertex(pose, b, p0, c0);
		vertex(pose, b, p1, c1);
		vertex(pose, b, p2, c2);
		vertex(pose, b, p3, c3);
		vertex(pose, b, p3, c3);
		vertex(pose, b, p2, c2);
		vertex(pose, b, p1, c1);
		vertex(pose, b, p0, c0);
	}

	public static void quad(PoseStack.Pose pose, VertexConsumer b, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int c) {
		quad(pose, b, p0, p1, p2, p3, c, c, c, c);
	}

	/** A line from a to b that always faces you, {@code width} thick, fading from colour a to colour b. */
	public static void line(PoseStack.Pose pose, VertexConsumer b, Vector3f from, Vector3f to, float width, int ca, int cb) {
		Vector3f dir = new Vector3f(to).sub(from);
		Vector3f mid = new Vector3f(from).add(to).mul(0.5f);
		Vector3f side = dir.cross(mid, new Vector3f());
		if (side.lengthSquared() < 1e-10f) {
			return;
		}
		side.normalize(width / 2);
		quad(pose, b, new Vector3f(from).sub(side), new Vector3f(from).add(side), new Vector3f(to).add(side), new Vector3f(to).sub(side), ca, ca, cb, cb);
	}

	public static void line(PoseStack.Pose pose, VertexConsumer b, Vector3f from, Vector3f to, float width, int c) {
		line(pose, b, from, to, width, c, c);
	}

	/** A line lying in the plane with this normal (for rings and marks on the ground). */
	public static void flatLine(PoseStack.Pose pose, VertexConsumer b, Vector3f from, Vector3f to, Vector3f normal, float width, int ca, int cb) {
		Vector3f dir = new Vector3f(to).sub(from);
		Vector3f side = dir.cross(normal, new Vector3f());
		if (side.lengthSquared() < 1e-10f) {
			return;
		}
		side.normalize(width / 2);
		quad(pose, b, new Vector3f(from).sub(side), new Vector3f(from).add(side), new Vector3f(to).add(side), new Vector3f(to).sub(side), ca, ca, cb, cb);
	}

	/** Part of a ring (from {@code a0} to {@code a1} radians) in the plane of axes x and y, as a flat band. */
	public static void arc(PoseStack.Pose pose, VertexConsumer b, Vector3f c, Vector3f x, Vector3f y, float r, float width, double a0, double a1, int color) {
		int segments = Math.max(2, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI * 2) * 48));
		for (int i = 0; i < segments; i++) {
			double s0 = a0 + (a1 - a0) * i / segments;
			double s1 = a0 + (a1 - a0) * (i + 1) / segments;
			quad(pose, b, around(c, x, y, r - width / 2, s0), around(c, x, y, r + width / 2, s0), around(c, x, y, r + width / 2, s1),
				around(c, x, y, r - width / 2, s1), color);
		}
	}

	/** A soft disc in the plane of axes x and y: {@code color} in the middle, fading to nothing at the rim. */
	public static void disc(PoseStack.Pose pose, VertexConsumer b, Vector3f c, Vector3f x, Vector3f y, float r, int color) {
		disc(pose, b, c, x, y, r, color, 20);
	}

	/** The same with fewer segments, for small or numerous lights. */
	public static void disc(PoseStack.Pose pose, VertexConsumer b, Vector3f c, Vector3f x, Vector3f y, float r, int color, int segments) {
		int rim = color & 0xFFFFFF;
		for (int i = 0; i < segments; i++) {
			Vector3f p0 = around(c, x, y, r, i * Math.PI * 2 / segments);
			Vector3f p1 = around(c, x, y, r, (i + 1) * Math.PI * 2 / segments);
			quad(pose, b, c, p0, p1, c, color, rim, rim, color);
		}
	}

	public static Vector3f around(Vector3f c, Vector3f x, Vector3f y, float r, double angle) {
		float cos = (float) Math.cos(angle) * r;
		float sin = (float) Math.sin(angle) * r;
		return new Vector3f(c.x + x.x * cos + y.x * sin, c.y + x.y * cos + y.y * sin, c.z + x.z * cos + y.z * sin);
	}

	/** Two axes across the view direction at {@code at} (for things that face you). */
	public static Vector3f[] facing(Vector3f at) {
		Vector3f view = new Vector3f(at).normalize();
		if (view.lengthSquared() < 1e-6f) {
			view.set(0, 0, 1);
		}
		Vector3f x = new Vector3f(view).cross(0, 1, 0);
		if (x.lengthSquared() < 1e-4f) {
			x.set(1, 0, 0);
		}
		x.normalize();
		Vector3f y = new Vector3f(x).cross(view).normalize();
		return new Vector3f[]{x, y};
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer b, Vector3f v, int argb) {
		b.addVertex(pose, v.x, v.y, v.z).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0).setNormal(pose, 0, 1, 0);
	}
}
