package dev.mordo.kairo.cosmetics;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.ClientInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;

/**
 * Tiny geometry kit for the cosmetics: coloured boxes, quads and triangles in model pixels (1/16 block), drawn with
 * a plain white texture so the vertex colour is the colour. Normals are worked out from the shape, so everything
 * is lit like the player model.
 */
public final class Geo {
	private static final Identifier WHITE = ClientInfo.id("cosmetics/white");
	private static boolean whiteReady;

	private final PoseStack.Pose pose;
	private final VertexConsumer buffer;
	private final int light;
	private final int overlay;

	Geo(PoseStack.Pose pose, VertexConsumer buffer, int light, int overlay) {
		this.pose = pose;
		this.buffer = buffer;
		this.light = light;
		this.overlay = overlay;
	}

	/**
	 * The render type every cosmetic uses: translucent (colours can have alpha) with back faces culled. Shapes are
	 * written with both windings, so exactly one copy of each face is drawn and the two never fight.
	 */
	public static RenderType renderType() {
		if (!whiteReady) {
			NativeImage image = new NativeImage(4, 4, false);
			for (int y = 0; y < 4; y++) {
				for (int x = 0; x < 4; x++) {
					image.setPixel(x, y, 0xFFFFFFFF);
				}
			}
			Minecraft.getInstance().getTextureManager().register(WHITE, new DynamicTexture(() -> "kairo cosmetics white", image));
			whiteReady = true;
		}
		return RenderTypes.entityTranslucentCull(WHITE);
	}

	/** Like {@link #renderType()} but without culling and unlit (glows, trails). */
	public static RenderType glowType() {
		renderType();
		return RenderTypes.entityTranslucentEmissive(WHITE);
	}

	private void vertex(float x, float y, float z, int argb, Vector3f n, int lightCoords) {
		buffer.addVertex(pose, x / 16f, y / 16f, z / 16f)
			.setColor(argb)
			.setUv(0.5f, 0.5f)
			.setOverlay(overlay)
			.setLight(lightCoords)
			.setNormal(pose, n.x, n.y, n.z);
	}

	private static Vector3f normal(float[] a, float[] b, float[] c) {
		Vector3f u = new Vector3f(b[0] - a[0], b[1] - a[1], b[2] - a[2]);
		Vector3f v = new Vector3f(c[0] - a[0], c[1] - a[1], c[2] - a[2]);
		Vector3f n = u.cross(v);
		return n.lengthSquared() < 1e-8f ? new Vector3f(0, -1, 0) : n.normalize();
	}

	/** One-sided quad (corners in order). */
	void quad(float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd, boolean glow) {
		Vector3f n = normal(a, b, c);
		int l = glow ? 0xF000F0 : light;
		vertex(a[0], a[1], a[2], ca, n, l);
		vertex(b[0], b[1], b[2], cb, n, l);
		vertex(c[0], c[1], c[2], cc, n, l);
		vertex(d[0], d[1], d[2], cd, n, l);
	}

	/** Quad visible from both sides (wing membranes, flat details). */
	void quad2(float[] a, float[] b, float[] c, float[] d, int ca, int cb, int cc, int cd, boolean glow) {
		quad(a, b, c, d, ca, cb, cc, cd, glow);
		quad(d, c, b, a, cd, cc, cb, ca, glow);
	}

	/** Triangle visible from both sides (drawn as a quad with a repeated corner). */
	void tri2(float[] a, float[] b, float[] c, int ca, int cb, int cc, boolean glow) {
		quad2(a, b, c, c, ca, cb, cc, cc, glow);
	}

	/** Axis-aligned box from (x0, y0, z0) to (x1, y1, z1), one colour. */
	void box(float x0, float y0, float z0, float x1, float y1, float z1, int argb, boolean glow) {
		float[] p000 = {x0, y0, z0};
		float[] p100 = {x1, y0, z0};
		float[] p110 = {x1, y1, z0};
		float[] p010 = {x0, y1, z0};
		float[] p001 = {x0, y0, z1};
		float[] p101 = {x1, y0, z1};
		float[] p111 = {x1, y1, z1};
		float[] p011 = {x0, y1, z1};
		// Both sides of each face, so culling can never hide one whichever way the model is turned.
		quad2(p000, p100, p110, p010, argb, argb, argb, argb, glow); // -z
		quad2(p101, p001, p011, p111, argb, argb, argb, argb, glow); // +z
		quad2(p001, p000, p010, p011, argb, argb, argb, argb, glow); // -x
		quad2(p100, p101, p111, p110, argb, argb, argb, argb, glow); // +x
		quad2(p001, p101, p100, p000, argb, argb, argb, argb, glow); // -y (top)
		quad2(p010, p110, p111, p011, argb, argb, argb, argb, glow); // +y (bottom)
	}

	/** A box of size (w, h, d) centred on (cx, cy, cz). */
	void cube(float cx, float cy, float cz, float w, float h, float d, int argb, boolean glow) {
		box(cx - w / 2, cy - h / 2, cz - d / 2, cx + w / 2, cy + h / 2, cz + d / 2, argb, glow);
	}

	/**
	 * A box stretched between two points (a thin "stick"): width {@code w} and depth {@code d} across it. Used for
	 * wing bones, feathers, horns and other angled pieces.
	 */
	void stick(float[] from, float[] to, float w, float d, int argb, boolean glow) {
		Vector3f dir = new Vector3f(to[0] - from[0], to[1] - from[1], to[2] - from[2]);
		if (dir.lengthSquared() < 1e-6f) {
			return;
		}
		dir.normalize();
		Vector3f side = new Vector3f(dir).cross(Math.abs(dir.z) > 0.9f ? new Vector3f(0, 1, 0) : new Vector3f(0, 0, 1)).normalize().mul(w / 2);
		Vector3f up = new Vector3f(dir).cross(side).normalize().mul(d / 2);
		float[][] a = new float[4][];
		float[][] b = new float[4][];
		int[][] signs = {{-1, -1}, {1, -1}, {1, 1}, {-1, 1}};
		for (int i = 0; i < 4; i++) {
			float sx = signs[i][0];
			float sy = signs[i][1];
			a[i] = new float[]{from[0] + side.x * sx + up.x * sy, from[1] + side.y * sx + up.y * sy, from[2] + side.z * sx + up.z * sy};
			b[i] = new float[]{to[0] + side.x * sx + up.x * sy, to[1] + side.y * sx + up.y * sy, to[2] + side.z * sx + up.z * sy};
		}
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) % 4;
			quad2(a[i], a[j], b[j], b[i], argb, argb, argb, argb, glow);
		}
		quad2(a[0], a[1], a[2], a[3], argb, argb, argb, argb, glow);
		quad2(b[3], b[2], b[1], b[0], argb, argb, argb, argb, glow);
	}
}
