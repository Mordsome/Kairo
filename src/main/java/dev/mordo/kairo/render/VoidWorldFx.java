package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.FxColor;
import dev.mordo.kairo.cosmetics.Geo;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Kairo Void's light drawn into the world, all of it custom geometry (no particles), only on your screen:
 * <ul>
 *     <li>an aura around you (orbiting cubes, light ribbons, prism shards or halo rings) and line glyphs;</li>
 *     <li>a fading trail of light behind you;</li>
 *     <li>an impact of light where your hits land;</li>
 *     <li>an animated overlay on the block you look at.</li>
 * </ul>
 * The aura, glyphs and trail are only ever drawn around your own player. Nothing here flashes: with Fewer flashes
 * the impacts lose their bright cores, with Reduce motion things move slower.
 */
public final class VoidWorldFx {
	private record Impact(Vec3 pos, long start, boolean crit, long seed, float size) {
	}

	private record TrailPoint(Vec3 pos, long time, Vec3 side) {
	}

	private static final List<Impact> IMPACTS = new ArrayList<>();
	private static final Deque<TrailPoint> TRAIL = new ArrayDeque<>();
	private static final long START = System.nanoTime();

	private VoidWorldFx() {
	}

	private static float time() {
		return (System.nanoTime() - START) / 1e9f * (Animation.reduceMotion() ? 0.5f : 1f);
	}

	private static long lastFrame = System.nanoTime();

	/** Seconds since the last frame (capped, so a pause doesn't make things jump). */
	private static float frameTime() {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		return dt;
	}

	/** True while the block overlay takes the place of the block outline. */
	public static boolean overlayReplacesOutline() {
		return Visuals.on() && VoidSettings.INSTANCE.blockOverlay.get() != VoidSettings.BlockOverlay.NONE;
	}

	/** A hit of yours landed at {@code pos}. */
	public static void onHit(Vec3 pos, float targetHeight, boolean crit) {
		if (!Visuals.on() || VoidSettings.INSTANCE.hitImpact.get() == VoidSettings.HitImpact.NONE) {
			return;
		}
		IMPACTS.add(new Impact(pos, System.nanoTime(), crit, System.nanoTime() ^ 0x5DEECE66DL, Math.clamp(targetHeight / 1.8f, 0.6f, 1.6f)));
		while (IMPACTS.size() > 12) {
			IMPACTS.removeFirst();
		}
	}

	/** Fabric's level render event: submit everything for this frame. */
	public static void submit(LevelRenderContext context) {
		if (!Visuals.on()) {
			TRAIL.clear();
			IMPACTS.clear();
			LightParticles.clear();
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null) {
			return;
		}
		Vec3 cam = context.levelState().cameraRenderState.pos;
		SubmitNodeCollector collector = context.submitNodeCollector();
		PoseStack poseStack = context.poseStack();
		float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(true);
		Vec3 feet = player.getPosition(partial);
		Vector3f at = new Vector3f((float) (feet.x - cam.x), (float) (feet.y - cam.y), (float) (feet.z - cam.z));
		VoidSettings s = VoidSettings.INSTANCE;
		float t = time();
		boolean thirdPerson = !mc.options.getCameraType().isFirstPerson();
		boolean showYou = !player.isInvisible() && !player.isSpectator() && (thirdPerson || s.youInFirstPerson.isOn());
		// Through your own eyes everything around you spreads out, so it surrounds you without sitting in your face.
		float spread = thirdPerson ? 1 : 2.3f;
		float dt = frameTime();

		updateTrail(player, feet, s);
		collector.submitCustomGeometry(poseStack, Geo.glowType(), (pose, b) -> {
			if (showYou) {
				switch (s.aura.get()) {
					case CUBES -> cubes(pose, b, at, t, s, spread);
					case RIBBONS -> ribbons(pose, b, at, t, s, spread);
					case SHARDS -> shards(pose, b, at, t, s, spread);
					case HALO -> halo(pose, b, at, t, s, spread);
					case NONE -> {
					}
				}
				if (s.glyphs.isOn()) {
					glyphs(pose, b, at, t, s, thirdPerson);
				}
			}
			VoidAmbient.draw(pose, b, cam, mc.level, context.levelState().skyRenderState, t, s);
			if (s.playerTrail.get() != VoidSettings.TrailStyle.NONE && (thirdPerson || s.youInFirstPerson.isOn())) {
				trail(pose, b, cam, s);
			}
			impacts(pose, b, cam, s);
			LightParticles.draw(pose, b, cam, mc.level);
			if (s.blockOverlay.get() != VoidSettings.BlockOverlay.NONE) {
				blockOverlay(pose, b, context.levelState().blockOutlineRenderState, cam, t, s, dt);
			}
		});
	}

	// ---- aura ----

	private static void cubes(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s, float spread) {
		float size = s.auraSize.floatValue();
		float speed = s.auraSpeed.floatValue();
		int count = 8;
		for (int i = 0; i < count; i++) {
			float k = i / (float) count;
			double orbit = t * speed * (0.55 + 0.25 * Math.sin(i * 1.7)) * (i % 2 == 0 ? 1 : -1) + i * Math.PI * 2 / count;
			float radius = (0.85f + 0.2f * (float) Math.sin(i * 2.3)) * size * spread;
			float y = (0.25f + 1.55f * ((i * 0.618f) % 1f) + 0.12f * (float) Math.sin(t * speed * 1.3 + i)) * size;
			Vector3f c = new Vector3f(at).add((float) Math.cos(orbit) * radius, y, (float) Math.sin(orbit) * radius);
			float half = (0.065f + 0.025f * (float) Math.sin(i * 3.1)) * size;
			int color = GlowGeo.color(s.auraColor.get(), k, t);
			cube(pose, b, c, half, t * speed * (0.8f + k), t * speed * (0.5f + k * 0.7f), color);
		}
	}

	/** A cube of light: bright edges, faint faces, a soft glow round it. */
	private static void cube(PoseStack.Pose pose, VertexConsumer b, Vector3f c, float half, float spinA, float spinB, int color) {
		Vector3f[] corners = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			Vector3f v = new Vector3f((i & 1) == 0 ? -half : half, (i & 2) == 0 ? -half : half, (i & 4) == 0 ? -half : half);
			v.rotateY(spinA).rotateX(spinB).add(c);
			corners[i] = v;
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		int edge = Colors.withAlpha(color, 0.95f);
		for (int[] e : edges) {
			GlowGeo.line(pose, b, corners[e[0]], corners[e[1]], half * 0.16f, edge);
		}
		int face = Colors.withAlpha(color, 0.12f);
		int[][] faces = {{0, 1, 3, 2}, {4, 5, 7, 6}, {0, 1, 5, 4}, {2, 3, 7, 6}, {0, 2, 6, 4}, {1, 3, 7, 5}};
		for (int[] f : faces) {
			GlowGeo.quad(pose, b, corners[f[0]], corners[f[1]], corners[f[2]], corners[f[3]], face);
		}
		Vector3f[] axes = GlowGeo.facing(c);
		GlowGeo.disc(pose, b, c, axes[0], axes[1], half * 3.2f, Colors.withAlpha(color, 0.16f));
	}

	private static void ribbons(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s, float spread) {
		float size = s.auraSize.floatValue();
		float speed = s.auraSpeed.floatValue();
		int steps = 40;
		for (int r = 0; r < 3; r++) {
			Vector3f prevIn = null;
			Vector3f prevOut = null;
			int prevColor = 0;
			for (int i = 0; i <= steps; i++) {
				float u = i / (float) steps;
				double angle = t * speed * 1.4 * (1 + 0.2 * r) + u * Math.PI * 2 * 1.25 + r * Math.PI * 2 / 3;
				float radius = (0.72f + 0.1f * (float) Math.sin(t * speed + u * 6 + r)) * size * spread;
				float y = (0.05f + u * 1.85f) * size;
				Vector3f centre = new Vector3f(at).add((float) Math.cos(angle) * radius, y, (float) Math.sin(angle) * radius);
				float width = 0.07f * size * (float) Math.sin(u * Math.PI) * (spread > 1 ? 1.6f : 1);
				Vector3f out = new Vector3f(0, width, 0).add(centre);
				Vector3f in = new Vector3f(0, -width, 0).add(centre);
				// Bright at the leading (top) end, fading behind.
				float alpha = (float) Math.sin(u * Math.PI) * (0.25f + 0.75f * u);
				int color = Colors.withAlpha(GlowGeo.color(s.auraColor.get(), u * 0.6f + r / 3f, t), 0.85f * alpha);
				if (prevIn != null) {
					GlowGeo.quad(pose, b, prevIn, prevOut, out, in, prevColor, prevColor, color, color);
				}
				prevIn = in;
				prevOut = out;
				prevColor = color;
			}
		}
	}

	private static void shards(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s, float spread) {
		float size = s.auraSize.floatValue();
		float speed = s.auraSpeed.floatValue();
		int count = 9;
		for (int i = 0; i < count; i++) {
			float k = i / (float) count;
			double orbit = t * speed * 0.35 + i * Math.PI * 2 / count;
			float radius = (0.95f + 0.15f * (float) Math.sin(i * 1.3 + t * 0.4)) * size * spread;
			float y = (0.3f + 1.4f * ((i * 0.381f) % 1f) + 0.15f * (float) Math.sin(t * speed + i * 2)) * size;
			Vector3f c = new Vector3f(at).add((float) Math.cos(orbit) * radius, y, (float) Math.sin(orbit) * radius);
			int color = GlowGeo.color(s.auraColor.get(), k, t);
			// A long crystal: two pyramids base to base, turning slowly.
			float len = 0.22f * size;
			float wid = 0.06f * size;
			float spin = t * speed * (0.6f + k);
			Vector3f top = new Vector3f(0, len, 0).rotateZ(0.35f).rotateY(spin).add(c);
			Vector3f bottom = new Vector3f(0, -len, 0).rotateZ(0.35f).rotateY(spin).add(c);
			Vector3f[] ring = new Vector3f[4];
			for (int j = 0; j < 4; j++) {
				ring[j] = new Vector3f((float) Math.cos(j * Math.PI / 2) * wid, 0, (float) Math.sin(j * Math.PI / 2) * wid).rotateZ(0.35f).rotateY(spin).add(c);
			}
			int face = Colors.withAlpha(color, 0.35f);
			int light = Colors.withAlpha(Colors.lerp(color, 0xFFFFFFFF, 0.5f), 0.55f);
			for (int j = 0; j < 4; j++) {
				Vector3f a = ring[j];
				Vector3f n = ring[(j + 1) % 4];
				GlowGeo.quad(pose, b, top, a, n, top, j % 2 == 0 ? light : face);
				GlowGeo.quad(pose, b, bottom, a, n, bottom, j % 2 == 0 ? face : light);
				GlowGeo.line(pose, b, top, a, 0.008f * size, Colors.withAlpha(color, 0.9f));
				GlowGeo.line(pose, b, bottom, a, 0.008f * size, Colors.withAlpha(color, 0.9f));
			}
		}
	}

	private static void halo(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s, float spread) {
		float size = s.auraSize.floatValue();
		float speed = s.auraSpeed.floatValue();
		Vector3f mid = new Vector3f(at).add(0, 1.0f * size, 0);
		for (int r = 0; r < 3; r++) {
			float tilt = 0.45f + r * 0.5f;
			float spin = t * speed * (0.4f + r * 0.15f) * (r % 2 == 0 ? 1 : -1);
			Vector3f x = new Vector3f((float) Math.cos(spin), 0, (float) Math.sin(spin));
			Vector3f y = new Vector3f(-(float) Math.sin(spin) * (float) Math.cos(tilt), (float) Math.sin(tilt), (float) Math.cos(spin) * (float) Math.cos(tilt));
			float radius = (0.85f + r * 0.12f) * size * spread;
			int color = GlowGeo.color(s.auraColor.get(), r / 3f, t);
			GlowGeo.arc(pose, b, mid, x, y, radius, 0.018f * size, 0, Math.PI * 2, Colors.withAlpha(color, 0.55f));
			// Bright nodes running round each ring.
			for (int n = 0; n < 3; n++) {
				double a = t * speed * 1.6 + n * Math.PI * 2 / 3 + r;
				Vector3f p = GlowGeo.around(mid, x, y, radius, a);
				Vector3f[] axes = GlowGeo.facing(p);
				GlowGeo.disc(pose, b, p, axes[0], axes[1], 0.07f * size, Colors.withAlpha(Colors.lerp(color, 0xFFFFFFFF, 0.6f), 0.9f));
			}
		}
		// And a halo over your head (not in first person, where it would sit right above your eyes).
		if (spread > 1) {
			return;
		}
		Vector3f head = new Vector3f(at).add(0, 2.15f * size, 0);
		int color = GlowGeo.color(s.auraColor.get(), 0.5f, t);
		GlowGeo.arc(pose, b, head, new Vector3f(1, 0, 0), new Vector3f(0, 0, 1), 0.28f * size, 0.045f * size, 0, Math.PI * 2, Colors.withAlpha(color, 0.85f));
	}

	// ---- line glyphs ----

	private static void glyphs(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s, boolean thirdPerson) {
		float size = s.auraSize.floatValue();
		if (!thirdPerson) {
			firstPersonGlyphs(pose, b, at, t, s, size);
			return;
		}
		Vector3f up = new Vector3f(0, 1, 0);
		Vector3f x = new Vector3f(1, 0, 0);
		Vector3f z = new Vector3f(0, 0, 1);
		// Around your feet: a ring of ticks, broken arcs and angular runes turning both ways.
		Vector3f ground = new Vector3f(at).add(0, 0.04f, 0);
		ring(pose, b, ground, x, z, up, 0.95f * size, t * 0.35f, t, s, 0, 24);
		ring(pose, b, ground, x, z, up, 0.68f * size, -t * 0.5f, t, s, 1, 16);
		// Round your waist, slightly tilted, and a smaller one over your head.
		float tilt = 0.18f;
		Vector3f wy = new Vector3f(0, (float) -Math.sin(tilt), (float) Math.cos(tilt));
		Vector3f wn = new Vector3f(0, (float) Math.cos(tilt), (float) Math.sin(tilt));
		ring(pose, b, new Vector3f(at).add(0, 1.05f * size, 0), x, wy, wn, 0.78f * size, -t * 0.28f, t, s, 2, 20);
		ring(pose, b, new Vector3f(at).add(0, 2.2f * size, 0), x, z, up, 0.32f * size, t * 0.9f, t, s, 3, 10);
	}

	/**
	 * Glyphs through your own eyes: wide rings round your feet you see when you look ahead or down, a broken ring
	 * turning just below eye level a few blocks out, and a big slow ring high above you. Nothing closer than ~2 blocks.
	 */
	private static void firstPersonGlyphs(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s, float size) {
		Vector3f up = new Vector3f(0, 1, 0);
		Vector3f x = new Vector3f(1, 0, 0);
		Vector3f z = new Vector3f(0, 0, 1);
		Vector3f ground = new Vector3f(at).add(0, 0.04f, 0);
		ring(pose, b, ground, x, z, up, 2.3f * size, t * 0.22f, t, s, 0, 30);
		ring(pose, b, ground, x, z, up, 3.2f * size, -t * 0.15f, t, s, 1, 40);
		float tilt = 0.12f;
		Vector3f wy = new Vector3f(0, (float) -Math.sin(tilt), (float) Math.cos(tilt));
		Vector3f wn = new Vector3f(0, (float) Math.cos(tilt), (float) Math.sin(tilt));
		ring(pose, b, new Vector3f(at).add(0, 0.75f, 0), x, wy, wn, 3.9f * size, -t * 0.12f, t, s, 2, 26);
		ring(pose, b, new Vector3f(at).add(0, 6.0f, 0), x, z, up, 3.6f * size, t * 0.1f, t, s, 3, 22);
	}

	/**
	 * One glyph ring: {@code marks} pieces round a circle, each a tick, a chevron, a bracket or a little angular
	 * rune. Pieces fade in and out slowly in turn, so the ring always seems to be writing itself.
	 */
	private static void ring(PoseStack.Pose pose, VertexConsumer b, Vector3f c, Vector3f x, Vector3f y, Vector3f n, float r,
							 float turn, float t, VoidSettings s, int seed, int marks) {
		Random random = new Random(seed * 7919L);
		float w = 0.03f + 0.012f * r;
		// A thin broken circle under the marks.
		for (int i = 0; i < 4; i++) {
			double a0 = turn + i * Math.PI / 2 + 0.12;
			int col = Colors.withAlpha(GlowGeo.color(s.glyphColor.get(), i / 4f, t), 0.5f);
			GlowGeo.arc(pose, b, c, x, y, r, w * 0.55f, a0, a0 + Math.PI / 2 - 0.24, col);
		}
		for (int i = 0; i < marks; i++) {
			double a = turn + i * Math.PI * 2 / marks;
			float life = 0.5f + 0.5f * (float) Math.sin(t * 0.9 + i * 1.7 + seed);
			float alpha = 0.25f + 0.75f * life * life;
			int col = Colors.withAlpha(GlowGeo.color(s.glyphColor.get(), i / (float) marks, t), alpha);
			Vector3f p = GlowGeo.around(c, x, y, r, a);
			Vector3f radial = new Vector3f(p).sub(c).normalize();
			Vector3f along = new Vector3f(n).cross(radial).normalize();
			float m = 0.1f + r * 0.14f;
			switch (random.nextInt(4)) {
				case 0 -> // tick
					GlowGeo.flatLine(pose, b, new Vector3f(radial).mul(-m * 0.6f).add(p), new Vector3f(radial).mul(m * 0.6f).add(p), n, w, col, col);
				case 1 -> { // chevron pointing along the ring
					Vector3f tip = new Vector3f(along).mul(m * 0.6f).add(p);
					GlowGeo.flatLine(pose, b, new Vector3f(radial).mul(m * 0.5f).add(p), tip, n, w, col, col);
					GlowGeo.flatLine(pose, b, new Vector3f(radial).mul(-m * 0.5f).add(p), tip, n, w, col, col);
				}
				case 2 -> { // bracket
					Vector3f a1 = new Vector3f(radial).mul(m * 0.6f).add(p);
					Vector3f a2 = new Vector3f(radial).mul(-m * 0.6f).add(p);
					GlowGeo.flatLine(pose, b, a1, a2, n, w, col, col);
					GlowGeo.flatLine(pose, b, a1, new Vector3f(along).mul(m * 0.4f).add(a1), n, w, col, col);
					GlowGeo.flatLine(pose, b, a2, new Vector3f(along).mul(m * 0.4f).add(a2), n, w, col, col);
				}
				default -> { // rune: a short angular zigzag
					Vector3f prev = new Vector3f(radial).mul(-m * 0.6f).add(p);
					for (int k = 1; k <= 3; k++) {
						float sx = (random.nextFloat() - 0.5f) * m;
						Vector3f next = new Vector3f(radial).mul(-m * 0.6f + k * m * 0.4f).add(new Vector3f(along).mul(sx)).add(p);
						GlowGeo.flatLine(pose, b, prev, next, n, w, col, col);
						prev = next;
					}
				}
			}
		}
	}

	// ---- trail ----

	private static void updateTrail(LocalPlayer player, Vec3 feet, VoidSettings s) {
		long now = System.nanoTime();
		long keep = (long) (s.trailLength.floatValue() * 1e9);
		while (!TRAIL.isEmpty() && now - TRAIL.peekFirst().time() > keep) {
			TRAIL.removeFirst();
		}
		if (s.playerTrail.get() == VoidSettings.TrailStyle.NONE) {
			TRAIL.clear();
			return;
		}
		TrailPoint last = TRAIL.peekLast();
		if (last == null || last.pos().distanceToSqr(feet) > 0.0016) {
			Vec3 move = last == null ? Vec3.directionFromRotation(0, player.getYRot()) : feet.subtract(last.pos());
			Vec3 side = new Vec3(-move.z, 0, move.x);
			side = side.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : side.normalize();
			TRAIL.addLast(new TrailPoint(feet, now, side));
		}
		while (TRAIL.size() > 400) {
			TRAIL.removeFirst();
		}
	}

	private static void trail(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, VoidSettings s) {
		if (TRAIL.size() < 2) {
			return;
		}
		long now = System.nanoTime();
		float keep = s.trailLength.floatValue();
		VoidSettings.TrailStyle style = s.playerTrail.get();
		float t = time();
		TrailPoint prev = null;
		int i = 0;
		int n = TRAIL.size();
		for (TrailPoint p : TRAIL) {
			if (prev != null) {
				float age0 = (now - prev.time()) / 1e9f / keep;
				float age1 = (now - p.time()) / 1e9f / keep;
				float a0 = (1 - Math.min(1, age0));
				float a1 = (1 - Math.min(1, age1));
				float u0 = (i - 1) / (float) n;
				float u1 = i / (float) n;
				FxColor mode = style == VoidSettings.TrailStyle.PRISM ? FxColor.RAINBOW : s.trailColor.get();
				int c0 = GlowGeo.color(mode, u0, t);
				int c1 = GlowGeo.color(mode, u1, t);
				Vector3f p0 = rel(prev.pos(), cam);
				Vector3f p1 = rel(p.pos(), cam);
				switch (style) {
					case RIBBON, PRISM -> {
						// A sheet of light from your knees to your shoulders, fading out behind you.
						GlowGeo.quad(pose, b, new Vector3f(p0).add(0, 0.15f, 0), new Vector3f(p0).add(0, 1.55f, 0),
							new Vector3f(p1).add(0, 1.55f, 0), new Vector3f(p1).add(0, 0.15f, 0),
							Colors.withAlpha(c0, 0.05f * a0), Colors.withAlpha(c0, 0.35f * a0 * a0), Colors.withAlpha(c1, 0.35f * a1 * a1),
							Colors.withAlpha(c1, 0.05f * a1));
					}
					case COMET -> {
						Vector3f q0 = new Vector3f(p0).add(0, 1.0f, 0);
						Vector3f q1 = new Vector3f(p1).add(0, 1.0f, 0);
						GlowGeo.line(pose, b, q0, q1, 0.04f * a0 + 0.01f, Colors.withAlpha(Colors.lerp(c0, 0xFFFFFFFF, 0.5f), a0), Colors.withAlpha(Colors.lerp(c1, 0xFFFFFFFF, 0.5f), a1));
						GlowGeo.line(pose, b, q0, q1, 0.35f * a0 + 0.05f, Colors.withAlpha(c0, 0.18f * a0), Colors.withAlpha(c1, 0.18f * a1));
					}
					case TWIN -> {
						for (int side = -1; side <= 1; side += 2) {
							Vector3f o0 = new Vector3f((float) prev.side().x, 0, (float) prev.side().z).mul(0.32f * side).add(p0).add(0, 0.85f, 0);
							Vector3f o1 = new Vector3f((float) p.side().x, 0, (float) p.side().z).mul(0.32f * side).add(p1).add(0, 0.85f, 0);
							GlowGeo.line(pose, b, o0, o1, 0.035f, Colors.withAlpha(c0, 0.85f * a0), Colors.withAlpha(c1, 0.85f * a1));
						}
					}
					case NONE -> {
					}
				}
			}
			prev = p;
			i++;
		}
	}

	private static Vector3f rel(Vec3 p, Vec3 cam) {
		return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
	}

	// ---- hit impacts ----

	private static void impacts(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, VoidSettings s) {
		if (IMPACTS.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		boolean calm = WorldFx.calm();
		float time = time();
		for (Iterator<Impact> it = IMPACTS.iterator(); it.hasNext(); ) {
			Impact im = it.next();
			float length = im.crit() ? 0.6f : 0.45f;
			float age = (now - im.start()) / 1e9f / length;
			if (age >= 1) {
				it.remove();
				continue;
			}
			Vector3f c = rel(im.pos(), cam);
			Vector3f[] axes = GlowGeo.facing(c);
			Vector3f x = axes[0];
			Vector3f y = axes[1];
			float size = im.size() * (im.crit() ? 1.45f : 1f);
			float fade = 1 - age;
			float ease = 1 - (1 - age) * (1 - age) * (1 - age);
			FxColor mode = s.hitImpactColor.get();
			int color = GlowGeo.color(mode, 0.2f, time);
			int white = Colors.lerp(color, 0xFFFFFFFF, 0.7f);
			Random random = new Random(im.seed());
			// A soft core (a calmer, dimmer one with Fewer flashes).
			float core = (calm ? 0.25f : 0.7f) * fade * fade;
			GlowGeo.disc(pose, b, c, x, y, 0.35f * size * (0.6f + ease * 0.6f), Colors.withAlpha(white, core));
			switch (s.hitImpact.get()) {
				case SHARDS -> {
					int count = im.crit() ? 14 : 9;
					for (int i = 0; i < count; i++) {
						double a = random.nextDouble() * Math.PI * 2;
						float dist = (0.15f + 0.65f * ease) * size * (0.6f + random.nextFloat() * 0.6f);
						Vector3f dir = new Vector3f(x).mul((float) Math.cos(a)).add(new Vector3f(y).mul((float) Math.sin(a)));
						Vector3f tip = new Vector3f(dir).mul(dist).add(c);
						Vector3f tail = new Vector3f(dir).mul(dist * 0.55f).add(c);
						Vector3f side = new Vector3f(dir).cross(new Vector3f(c).normalize()).normalize(0.035f * size * fade);
						int col = GlowGeo.color(mode, i / (float) count, time);
						GlowGeo.quad(pose, b, tail, new Vector3f(tail).add(side), tip, new Vector3f(tail).sub(side),
							Colors.withAlpha(col, 0.2f * fade), Colors.withAlpha(col, 0.9f * fade), Colors.withAlpha(white, fade), Colors.withAlpha(col, 0.9f * fade));
					}
				}
				case SIGIL -> {
					float r = (0.25f + 0.25f * ease) * size;
					double spin = age * (im.crit() ? 3 : 2);
					GlowGeo.arc(pose, b, c, x, y, r, 0.025f * size, spin, spin + Math.PI * 2, Colors.withAlpha(color, 0.9f * fade));
					GlowGeo.arc(pose, b, c, x, y, r * 1.25f, 0.012f * size, -spin, -spin + Math.PI * 1.5, Colors.withAlpha(white, 0.7f * fade));
					for (int tri = 0; tri < 2; tri++) {
						for (int k = 0; k < 3; k++) {
							double a0 = -spin + tri * Math.PI / 3 + k * Math.PI * 2 / 3;
							double a1 = a0 + Math.PI * 2 / 3;
							GlowGeo.line(pose, b, GlowGeo.around(c, x, y, r * 0.9f, a0), GlowGeo.around(c, x, y, r * 0.9f, a1), 0.016f * size,
								Colors.withAlpha(color, 0.85f * fade));
						}
					}
				}
				case CROSS -> {
					int slashes = im.crit() ? 3 : 2;
					for (int k = 0; k < slashes; k++) {
						double a = Math.PI / 4 + k * Math.PI / 2 + (k == 2 ? Math.PI / 8 : 0) + random.nextGaussian() * 0.1;
						Vector3f dir = new Vector3f(x).mul((float) Math.cos(a)).add(new Vector3f(y).mul((float) Math.sin(a)));
						float drawOn = Math.min(1, age * 4);
						float len = 0.6f * size;
						Vector3f from = new Vector3f(dir).mul(-len).add(c);
						Vector3f to = new Vector3f(dir).mul(-len + 2 * len * drawOn).add(c);
						GlowGeo.line(pose, b, from, to, 0.05f * size * fade, Colors.withAlpha(color, 0.2f * fade), Colors.withAlpha(white, fade));
					}
				}
				case STAR -> {
					int spikes = im.crit() ? 10 : 7;
					for (int i = 0; i < spikes; i++) {
						double a = i * Math.PI * 2 / spikes + random.nextGaussian() * 0.15;
						Vector3f dir = new Vector3f(x).mul((float) Math.cos(a)).add(new Vector3f(y).mul((float) Math.sin(a)));
						float len = (0.25f + 0.45f * ease) * size * (i % 2 == 0 ? 1 : 0.6f);
						Vector3f side = new Vector3f(dir).cross(new Vector3f(c).normalize()).normalize(0.05f * size * fade);
						Vector3f tip = new Vector3f(dir).mul(len).add(c);
						GlowGeo.quad(pose, b, new Vector3f(c).add(side), tip, tip, new Vector3f(c).sub(side),
							Colors.withAlpha(white, 0.9f * fade), Colors.withAlpha(color, 0.0f), Colors.withAlpha(color, 0.0f), Colors.withAlpha(white, 0.9f * fade));
					}
				}
				case RINGS -> {
					for (int k = 0; k < (im.crit() ? 3 : 2); k++) {
						float local = Math.clamp(age * 1.4f - k * 0.18f, 0f, 1f);
						if (local <= 0) {
							continue;
						}
						float r = (0.1f + 0.75f * (1 - (1 - local) * (1 - local))) * size;
						GlowGeo.arc(pose, b, c, x, y, r, 0.03f * size * (1 - local) + 0.005f, 0, Math.PI * 2,
							Colors.withAlpha(k == 0 ? white : color, 0.85f * (1 - local)));
					}
				}
				case NONE -> {
				}
			}
		}
	}

	// ---- block overlay ----

	/** Where the overlay is drawn right now (world space): it glides towards the block you look at. */
	private static AABB overlayBox;
	private static float overlayShown;
	private static float overlayFade = 1;

	private static int fade(int color, float alpha) {
		return Colors.withAlpha(color, alpha * overlayFade);
	}

	private static void blockOverlay(PoseStack.Pose pose, VertexConsumer b, @org.jspecify.annotations.Nullable BlockOutlineRenderState outline,
									 Vec3 cam, float t, VoidSettings s, float dt) {
		boolean glide = s.overlayGlide.isOn();
		AABB target = outline == null || outline.shape().isEmpty() ? null : outline.shape().bounds().move(outline.pos()).inflate(0.003);
		if (target != null) {
			if (overlayBox == null || !glide || overlayShown <= 0 || overlayBox.getCenter().distanceToSqr(target.getCenter()) > 64) {
				overlayBox = target;
			} else {
				// Slide and reshape towards the new block (a little quicker with Reduce motion, so it settles sooner).
				double k = 1 - Math.exp(-dt * (Animation.reduceMotion() ? 30 : 16));
				overlayBox = new AABB(Mth.lerp(k, overlayBox.minX, target.minX), Mth.lerp(k, overlayBox.minY, target.minY), Mth.lerp(k, overlayBox.minZ, target.minZ),
					Mth.lerp(k, overlayBox.maxX, target.maxX), Mth.lerp(k, overlayBox.maxY, target.maxY), Mth.lerp(k, overlayBox.maxZ, target.maxZ));
			}
			overlayShown = glide ? Math.min(1, overlayShown + dt * 7) : 1;
		} else {
			overlayShown = glide ? Math.max(0, overlayShown - dt * 5) : 0;
		}
		if (overlayBox == null || overlayShown <= 0) {
			return;
		}
		overlayFade = overlayShown * overlayShown * (3 - 2 * overlayShown);
		AABB box = overlayBox;
		Vector3f lo = new Vector3f((float) (box.minX - cam.x), (float) (box.minY - cam.y), (float) (box.minZ - cam.z));
		Vector3f hi = new Vector3f((float) (box.maxX - cam.x), (float) (box.maxY - cam.y), (float) (box.maxZ - cam.z));
		Vector3f size = new Vector3f(hi).sub(lo);
		int color = GlowGeo.color(s.blockOverlayColor.get(), 0.3f, t);
		float w = 0.012f + 0.004f * Math.min(1, Mth.sqrt((float) cam.distanceToSqr(box.getCenter())) / 6);
		switch (s.blockOverlay.get()) {
			case BRACKETS -> {
				float breathe = 0.5f + 0.5f * (float) Math.sin(t * 3);
				float out = 0.02f + 0.03f * breathe;
				Vector3f l = new Vector3f(lo).sub(out, out, out);
				Vector3f h = new Vector3f(hi).add(out, out, out);
				float arm = Math.min(Math.min(size.x, size.y), size.z) * 0.28f;
				for (int i = 0; i < 8; i++) {
					Vector3f corner = new Vector3f((i & 1) == 0 ? l.x : h.x, (i & 2) == 0 ? l.y : h.y, (i & 4) == 0 ? l.z : h.z);
					float sx = (i & 1) == 0 ? 1 : -1;
					float sy = (i & 2) == 0 ? 1 : -1;
					float sz = (i & 4) == 0 ? 1 : -1;
					int col = fade(color, 0.75f + 0.25f * breathe);
					GlowGeo.line(pose, b, corner, new Vector3f(corner).add(sx * arm, 0, 0), w * 1.4f, col);
					GlowGeo.line(pose, b, corner, new Vector3f(corner).add(0, sy * arm, 0), w * 1.4f, col);
					GlowGeo.line(pose, b, corner, new Vector3f(corner).add(0, 0, sz * arm), w * 1.4f, col);
				}
			}
			case SCAN -> {
				boxEdges(pose, b, lo, hi, w * 0.7f, fade(color, 0.45f));
				// A band of light sweeping up the sides, then over the top.
				float k = (t * 0.6f) % 1f;
				float y = lo.y + size.y * k;
				int band = fade(Colors.lerp(color, 0xFFFFFFFF, 0.2f), 0.8f * (float) Math.sin(k * Math.PI));
				int clear = color & 0xFFFFFF;
				float h = Math.min(size.y * 0.4f, 0.35f);
				float y0 = Math.max(lo.y, y - h);
				sideBand(pose, b, lo, hi, y0, y, clear, band);
				boxRing(pose, b, lo, hi, y, w * 2f, fade(Colors.lerp(color, 0xFFFFFFFF, 0.4f), (float) Math.sin(k * Math.PI)));
			}
			case HOLOGRAM -> {
				boxEdges(pose, b, lo, hi, w, fade(color, 0.9f));
				int grid = fade(Colors.lerp(color, 0xFFFFFFFF, 0.25f), 0.45f);
				for (int i = 1; i < 4; i++) {
					float f = i / 4f;
					boxRing(pose, b, lo, hi, lo.y + size.y * f, w * 0.8f, grid);
					// Vertical grid lines on each side.
					float gx = lo.x + size.x * f;
					float gz = lo.z + size.z * f;
					GlowGeo.line(pose, b, new Vector3f(gx, lo.y, lo.z), new Vector3f(gx, hi.y, lo.z), w * 0.8f, grid);
					GlowGeo.line(pose, b, new Vector3f(gx, lo.y, hi.z), new Vector3f(gx, hi.y, hi.z), w * 0.8f, grid);
					GlowGeo.line(pose, b, new Vector3f(lo.x, lo.y, gz), new Vector3f(lo.x, hi.y, gz), w * 0.8f, grid);
					GlowGeo.line(pose, b, new Vector3f(hi.x, lo.y, gz), new Vector3f(hi.x, hi.y, gz), w * 0.8f, grid);
					GlowGeo.line(pose, b, new Vector3f(gx, hi.y, lo.z), new Vector3f(gx, hi.y, hi.z), w * 0.8f, grid);
					GlowGeo.line(pose, b, new Vector3f(lo.x, hi.y, gz), new Vector3f(hi.x, hi.y, gz), w * 0.8f, grid);
				}
				int fill = fade(color, 0.14f);
				sideBand(pose, b, lo, hi, lo.y, hi.y, fill, fill);
			}
			case PULSE -> {
				boxEdges(pose, b, lo, hi, w, fade(color, 0.8f));
				for (int k = 0; k < 2; k++) {
					float p = (t * 0.8f + k * 0.5f) % 1f;
					float grow = 0.02f + p * 0.22f;
					Vector3f l = new Vector3f(lo).sub(grow, grow, grow);
					Vector3f h = new Vector3f(hi).add(grow, grow, grow);
					boxEdges(pose, b, l, h, w * (1 - p) + 0.003f, fade(color, 0.6f * (1 - p) * (1 - p)));
				}
			}
			case NONE -> {
			}
		}
	}

	private static void boxEdges(PoseStack.Pose pose, VertexConsumer b, Vector3f lo, Vector3f hi, float w, int color) {
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f((i & 1) == 0 ? lo.x : hi.x, (i & 2) == 0 ? lo.y : hi.y, (i & 4) == 0 ? lo.z : hi.z);
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] e : edges) {
			GlowGeo.line(pose, b, c[e[0]], c[e[1]], w, color);
		}
	}

	/** A horizontal loop round the four sides at height y. */
	private static void boxRing(PoseStack.Pose pose, VertexConsumer b, Vector3f lo, Vector3f hi, float y, float w, int color) {
		Vector3f a = new Vector3f(lo.x, y, lo.z);
		Vector3f bb = new Vector3f(hi.x, y, lo.z);
		Vector3f c = new Vector3f(hi.x, y, hi.z);
		Vector3f d = new Vector3f(lo.x, y, hi.z);
		GlowGeo.line(pose, b, a, bb, w, color);
		GlowGeo.line(pose, b, bb, c, w, color);
		GlowGeo.line(pose, b, c, d, w, color);
		GlowGeo.line(pose, b, d, a, w, color);
	}

	/** The four sides between heights y0 and y1, coloured from bottom to top. */
	private static void sideBand(PoseStack.Pose pose, VertexConsumer b, Vector3f lo, Vector3f hi, float y0, float y1, int bottom, int top) {
		float[][] sides = {{lo.x, lo.z, hi.x, lo.z}, {hi.x, lo.z, hi.x, hi.z}, {hi.x, hi.z, lo.x, hi.z}, {lo.x, hi.z, lo.x, lo.z}};
		for (float[] sd : sides) {
			GlowGeo.quad(pose, b, new Vector3f(sd[0], y0, sd[1]), new Vector3f(sd[2], y0, sd[3]), new Vector3f(sd[2], y1, sd[3]), new Vector3f(sd[0], y1, sd[1]),
				bottom, bottom, top, top);
		}
	}
}
