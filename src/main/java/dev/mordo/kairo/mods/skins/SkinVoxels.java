package dev.mordo.kairo.mods.skins;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Builds 3D meshes from a skin's outer layer: every opaque pixel of the hat, jacket, sleeves and pants becomes a
 * small cube standing on the body part, coloured by sampling that pixel. Meshes are cached per skin texture.
 *
 * <p>Face orientation follows Minecraft's own box UV layout ({@code ModelPart.Cube}), so the cubes land exactly
 * where the flat layer's pixels would.
 */
public final class SkinVoxels {
	/** The six outer layers. */
	public enum Layer {
		HAT, JACKET, LEFT_SLEEVE, RIGHT_SLEEVE, LEFT_PANTS, RIGHT_PANTS
	}

	/** Quads in part-local model units (pixels): 4 corners xyz, normal xyz, uv; 17 floats each. */
	public record Mesh(float[] quads, int count) {
	}

	private record Box(int u, int v, float x, float y, float z, int w, int h, int d) {
	}

	private record Key(Identifier texture, boolean slim, float headDepth, float bodyDepth) {
	}

	private static final Map<Key, Mesh[]> CACHE = new HashMap<>();
	private static final Map<Identifier, Object> SOURCES = new HashMap<>();

	private SkinVoxels() {
	}

	private static Box box(Layer layer, boolean slim) {
		return switch (layer) {
			case HAT -> new Box(32, 0, -4, -8, -4, 8, 8, 8);
			case JACKET -> new Box(16, 32, -4, 0, -2, 8, 12, 4);
			case RIGHT_SLEEVE -> slim ? new Box(40, 32, -2, -2, -2, 3, 12, 4) : new Box(40, 32, -3, -2, -2, 4, 12, 4);
			case LEFT_SLEEVE -> slim ? new Box(48, 48, -1, -2, -2, 3, 12, 4) : new Box(48, 48, -1, -2, -2, 4, 12, 4);
			case RIGHT_PANTS -> new Box(0, 32, -2, 0, -2, 4, 12, 4);
			case LEFT_PANTS -> new Box(0, 48, -2, 0, -2, 4, 12, 4);
		};
	}

	/** Meshes for each layer of this skin, or null if its pixels can't be read (yet). */
	public static Mesh @Nullable [] meshes(Identifier texture, boolean slim, float headDepth, float bodyDepth) {
		Object source = currentSource(texture);
		if (source == null) {
			return null;
		}
		if (SOURCES.get(texture) != source) {
			// The texture object changed (skin finished downloading, resource reload): rebuild.
			SOURCES.put(texture, source);
			CACHE.keySet().removeIf(k -> k.texture().equals(texture));
		}
		Key key = new Key(texture, slim, headDepth, bodyDepth);
		Mesh[] cached = CACHE.get(key);
		if (cached != null) {
			return cached;
		}
		NativeImage image = pixels(texture, source);
		if (image == null) {
			return null;
		}
		try {
			Mesh[] meshes = new Mesh[Layer.values().length];
			for (Layer layer : Layer.values()) {
				meshes[layer.ordinal()] = build(image, box(layer, slim), layer == Layer.HAT ? headDepth : bodyDepth);
			}
			if (CACHE.size() > 256) {
				CACHE.clear();
			}
			CACHE.put(key, meshes);
			return meshes;
		} finally {
			if (!(source instanceof DynamicTexture)) {
				image.close();
			}
		}
	}

	private static @Nullable Object currentSource(Identifier texture) {
		AbstractTexture tex = Minecraft.getInstance().getTextureManager().getTexture(texture);
		return tex;
	}

	private static @Nullable NativeImage pixels(Identifier texture, Object source) {
		if (source instanceof DynamicTexture dynamic) {
			return dynamic.getPixels();
		}
		// Built-in skins (Steve, Alex...) come straight from the resources.
		try {
			Optional<net.minecraft.server.packs.resources.Resource> resource = Minecraft.getInstance().getResourceManager().getResource(texture);
			if (resource.isEmpty()) {
				return null;
			}
			try (InputStream in = resource.get().open()) {
				return NativeImage.read(in);
			}
		} catch (Exception e) {
			Kairo.LOGGER.debug("Couldn't read skin {} for 3D layers", texture, e);
			return null;
		}
	}

	// ---- mesh building ----

	private static Mesh build(NativeImage image, Box b, float depth) {
		float texW = image.getWidth();
		float texH = image.getHeight();
		if (texH < 64 && b.v() >= 32) {
			return new Mesh(new float[0], 0); // legacy 64x32 skins have no second layer down there
		}
		float minX = b.x();
		float minY = b.y();
		float minZ = b.z();
		float maxX = minX + b.w();
		float maxY = minY + b.h();
		float maxZ = minZ + b.d();
		float[] t0 = {minX, minY, minZ};
		float[] t1 = {maxX, minY, minZ};
		float[] t2 = {maxX, maxY, minZ};
		float[] t3 = {minX, maxY, minZ};
		float[] l0 = {minX, minY, maxZ};
		float[] l1 = {maxX, minY, maxZ};
		float[] l2 = {maxX, maxY, maxZ};
		float[] l3 = {minX, maxY, maxZ};
		int u0 = b.u();
		int u1 = u0 + b.d();
		int u2 = u1 + b.w();
		int u22 = u2 + b.w();
		int u3 = u2 + b.d();
		int u4 = u3 + b.w();
		int v0 = b.v();
		int v1 = v0 + b.d();
		int v2 = v1 + b.h();
		List<float[]> quads = new ArrayList<>();
		float[] center = {(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2};
		// Same faces and UV rectangles as ModelPart.Cube.
		face(quads, image, texW, texH, new float[][]{l1, l0, t0, t1}, u1, v0, u2, v1, center, depth);
		face(quads, image, texW, texH, new float[][]{t2, t3, l3, l2}, u2, v1, u22, v0, center, depth);
		face(quads, image, texW, texH, new float[][]{t0, l0, l3, t3}, u0, v1, u1, v2, center, depth);
		face(quads, image, texW, texH, new float[][]{t1, t0, t3, t2}, u1, v1, u2, v2, center, depth);
		face(quads, image, texW, texH, new float[][]{l1, t1, t2, l2}, u2, v1, u3, v2, center, depth);
		face(quads, image, texW, texH, new float[][]{l0, l1, l2, l3}, u3, v1, u4, v2, center, depth);
		float[] all = new float[quads.size() * 17];
		for (int i = 0; i < quads.size(); i++) {
			System.arraycopy(quads.get(i), 0, all, i * 17, 17);
		}
		return new Mesh(all, quads.size());
	}

	private static boolean opaque(NativeImage image, int x, int y) {
		if (x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
			return false;
		}
		return (image.getPixel(x, y) >>> 24) > 24;
	}

	/**
	 * One face of the box: the UV rectangle (pu0,pv0)-(pu1,pv1) maps onto the face corners like Minecraft does
	 * (corner 1 at (pu0,pv0), corner 0 at (pu1,pv0), corner 2 at (pu0,pv1)). Each opaque texel becomes a voxel.
	 */
	private static void face(List<float[]> out, NativeImage image, float texW, float texH, float[][] v,
							 int pu0, int pv0, int pu1, int pv1, float[] center, float depth) {
		int uMin = Math.min(pu0, pu1);
		int uMax = Math.max(pu0, pu1);
		int vMin = Math.min(pv0, pv1);
		int vMax = Math.max(pv0, pv1);
		if (uMax == uMin || vMax == vMin) {
			return;
		}
		float[] stepU = scale(sub(v[0], v[1]), 1f / (pu1 - pu0));
		float[] stepV = scale(sub(v[2], v[1]), 1f / (pv1 - pv0));
		// Outward normal: from the box centre towards the face centre.
		float[] faceCenter = scale(add(add(v[0], v[1]), add(v[2], v[3])), 0.25f);
		float[] n = normalize(sub(faceCenter, center));
		for (int py = vMin; py < vMax; py++) {
			for (int px = uMin; px < uMax; px++) {
				if (!opaque(image, px, py)) {
					continue;
				}
				float s = (px + 0.5f - pu0);
				float t = (py + 0.5f - pv0);
				float[] p = add(v[1], add(scale(stepU, s), scale(stepV, t)));
				float[] c = add(p, scale(n, 0.02f + depth / 2));
				float[] a = scale(stepU, 0.5f);
				float[] b = scale(stepV, 0.5f);
				float[] dn = scale(n, depth / 2);
				float u = (px + 0.5f) / texW;
				float w = (py + 0.5f) / texH;
				// Outer face.
				quad(out, c, dn, a, b, n, u, w);
				// Sides only where the neighbouring texel is empty (or off this face).
				boolean inRight = px + 1 < uMax && opaque(image, px + 1, py);
				boolean inLeft = px - 1 >= uMin && opaque(image, px - 1, py);
				boolean inDown = py + 1 < vMax && opaque(image, px, py + 1);
				boolean inUp = py - 1 >= vMin && opaque(image, px, py - 1);
				if (!inRight) {
					quad(out, c, a, b, dn, normalize(a), u, w);
				}
				if (!inLeft) {
					quad(out, c, scale(a, -1), dn, b, normalize(scale(a, -1)), u, w);
				}
				if (!inDown) {
					quad(out, c, b, dn, a, normalize(b), u, w);
				}
				if (!inUp) {
					quad(out, c, scale(b, -1), a, dn, normalize(scale(b, -1)), u, w);
				}
			}
		}
	}

	/** A face of a voxel centred at {@code c}: offset {@code off} to the face, spanned by half-vectors e1, e2. */
	private static void quad(List<float[]> out, float[] c, float[] off, float[] e1, float[] e2, float[] normal, float u, float v) {
		float[] q = new float[17];
		float[] fc = add(c, off);
		float[][] corners = {
			add(fc, add(scale(e1, -1), scale(e2, -1))),
			add(fc, add(e1, scale(e2, -1))),
			add(fc, add(e1, e2)),
			add(fc, add(scale(e1, -1), e2))
		};
		// Wind the corners so the face points along its normal.
		float[] cross = cross(sub(corners[1], corners[0]), sub(corners[2], corners[0]));
		if (dot(cross, normal) < 0) {
			float[] tmp = corners[1];
			corners[1] = corners[3];
			corners[3] = tmp;
		}
		for (int i = 0; i < 4; i++) {
			q[i * 3] = corners[i][0];
			q[i * 3 + 1] = corners[i][1];
			q[i * 3 + 2] = corners[i][2];
		}
		q[12] = normal[0];
		q[13] = normal[1];
		q[14] = normal[2];
		q[15] = u;
		q[16] = v;
		out.add(q);
	}

	private static float[] add(float[] a, float[] b) {
		return new float[]{a[0] + b[0], a[1] + b[1], a[2] + b[2]};
	}

	private static float[] sub(float[] a, float[] b) {
		return new float[]{a[0] - b[0], a[1] - b[1], a[2] - b[2]};
	}

	private static float[] scale(float[] a, float s) {
		return new float[]{a[0] * s, a[1] * s, a[2] * s};
	}

	private static float dot(float[] a, float[] b) {
		return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
	}

	private static float[] cross(float[] a, float[] b) {
		return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
	}

	private static float[] normalize(float[] a) {
		float len = (float) Math.sqrt(dot(a, a));
		return len < 1e-6f ? a : scale(a, 1 / len);
	}
}
