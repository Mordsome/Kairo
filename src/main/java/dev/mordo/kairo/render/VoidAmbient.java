package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.AuroraColor;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Kairo Void's light in the world around you, seen best through your own eyes:
 * <ul>
 *     <li><b>light ribbons</b>: long ribbons of light hanging and twisting in the air, like an aurora you walk through.
 *     They belong to places in the world (not to you), so you pass them as you travel;</li>
 *     <li><b>floating motes</b>: small lights hanging in the air close around you, drifting and slowly breathing;</li>
 *     <li><b>starfall</b>: comets of light streaking down around you and landing in rings of light;</li>
 *     <li><b>sky crystals</b>: huge turning crystals of light hovering over the land, with orbiting rings and a beam;</li>
 *     <li><b>pulse grid</b>: waves of light sweeping out from you over the land, lighting its shape up as a mesh;</li>
 *     <li><b>light rivers</b>: streams of light flowing over the ground that bend aside round your feet;</li>
 *     <li><b>sky shapes</b>: vast wire shapes, gyroscopes and gates of light far up in the sky;</li>
 *     <li><b>rifts</b>: tears in the sky with light leaning in round them.</li>
 * </ul>
 * Both fade out close to you (never in your face) and far away, are brightest at night, and never flash.
 */
public final class VoidAmbient {
	private VoidAmbient() {
	}

	/** Ribbons live in cells of this many blocks; one cell can hold up to two. */
	private static final int CELL = 26;
	private static final int REACH = 2;
	/** Ground height under each ribbon, worked out once per cell. */
	private static final Map<Long, Float> GROUND = new HashMap<>();
	private static ClientLevel groundLevel;

	static void draw(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, SkyRenderState sky, float t, VoidSettings s) {
		if (level == null) {
			return;
		}
		if (level != groundLevel) {
			GROUND.clear();
			groundLevel = level;
		}
		float night = Math.clamp(sky.starBrightness * 2.2f, 0, 1);
		boolean overworld = sky.skybox == net.minecraft.world.level.dimension.DimensionType.Skybox.OVERWORLD;
		// Brightest at night; in daylight fainter (or off); always on where there's no day (the Nether, the End).
		float light = !overworld ? 0.8f : s.ribbonsByDay.isOn() ? 0.45f + 0.55f * night : night;
		if (s.worldRibbons.isOn() && light > 0.01f) {
			ribbons(pose, b, cam, level, t, s, light);
		}
		if (s.motes.isOn()) {
			motes(pose, b, cam, level, t, s, 0.55f + 0.45f * (overworld ? night : 1));
		}
		float skyLight = !overworld ? 0.85f : 0.5f + 0.5f * night;
		if (s.starfall.isOn()) {
			starfall(pose, b, cam, level, t, s, skyLight);
		}
		if (s.skyCrystals.isOn()) {
			crystals(pose, b, cam, level, t, s, skyLight);
		}
		float groundLight = !overworld ? 0.85f : 0.6f + 0.4f * night;
		if (s.pulseGrid.isOn()) {
			pulseGrid(pose, b, cam, level, t, s, groundLight);
		}
		if (s.lightRivers.isOn()) {
			rivers(pose, b, cam, level, t, s, groundLight);
		}
		if (s.skyShapes.isOn()) {
			skyShapes(pose, b, cam, level, t, s, skyLight);
		}
		if (s.rift.isOn()) {
			rifts(pose, b, cam, level, t, s, skyLight);
		}
	}

	/**
	 * How far the far-off shapes can be seen: Minecraft's fog hides everything towards the edge of your render
	 * distance, so they fade out before it.
	 */
	private static float sight() {
		return Math.clamp(net.minecraft.client.Minecraft.getInstance().options.getEffectiveRenderDistance() * 16 * 0.85f, 70, 210);
	}

	// ---- palette ----

	/** A colour along the palette (k from 0 to 1, wrapping). */
	static int palette(AuroraColor mode, float k, float time) {
		k = k - (float) Math.floor(k);
		float wave = 0.5f + 0.5f * (float) Math.sin(k * Math.PI * 2);
		int accent = Theme.accent() | 0xFF000000;
		return switch (mode) {
			case CLASSIC -> Colors.lerp(0xFF3DFFA8, 0xFF9B5CFF, wave);
			case ACCENT -> Colors.lerp(accent, Colors.lerp(accent, 0xFFFFFFFF, 0.55f), wave);
			case RAINBOW -> Colors.hsv((k + time * 0.03f) % 1f, 0.65f, 1f) | 0xFF000000;
			case SUNSET -> Colors.lerp(0xFFFF5E7E, 0xFFFFB347, wave);
		};
	}

	// ---- ribbons ----

	private static void ribbons(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int cx = Math.floorDiv((int) Math.floor(cam.x), CELL);
		int cz = Math.floorDiv((int) Math.floor(cam.z), CELL);
		float amount = s.ribbonAmount.floatValue();
		for (int dx = -REACH; dx <= REACH; dx++) {
			for (int dz = -REACH; dz <= REACH; dz++) {
				long seed = (cx + dx) * 341873128712L + (cz + dz) * 132897987541L;
				Random random = new Random(seed);
				for (int n = 0; n < 2; n++) {
					float chance = random.nextFloat();
					if (chance > (n == 0 ? 0.25f + amount * 0.75f : amount - 0.45f)) {
						continue;
					}
					double ox = (cx + dx) * CELL + random.nextFloat() * CELL;
					double oz = (cz + dz) * CELL + random.nextFloat() * CELL;
					float ground = ground(level, seed * 31 + n, ox, oz, cam.y);
					if (Float.isNaN(ground)) {
						continue;
					}
					double oy = ground + 5 + random.nextFloat() * 9;
					ribbon(pose, b, cam, t, s, light, random, ox, oy, oz);
				}
			}
		}
	}

	/** The ground height at a ribbon, remembered once its chunk is loaded; NaN while it isn't. */
	private static float ground(ClientLevel level, long key, double x, double z, double camY) {
		Float known = GROUND.get(key);
		if (known != null) {
			return known;
		}
		int bx = (int) Math.floor(x);
		int bz = (int) Math.floor(z);
		if (!level.hasChunkAt(new BlockPos(bx, level.getMinY(), bz))) {
			return Float.NaN;
		}
		int height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
		// No ground (void, a hole to the bottom): hang it around where you are.
		float y = height <= level.getMinY() + 1 ? (float) camY : height;
		if (GROUND.size() > 4096) {
			GROUND.clear();
		}
		GROUND.put(key, y);
		return y;
	}

	/**
	 * One ribbon: a curtain of light a few blocks tall winding through the air, twisting as it goes, bright along its
	 * lower edge and fading upwards, with light running along it.
	 */
	private static void ribbon(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, float t, VoidSettings s, float light, Random random,
							   double ox, double oy, double oz) {
		float length = 18 + random.nextFloat() * 18;
		float height = 2.2f + random.nextFloat() * 2.8f;
		double heading = random.nextFloat() * Math.PI * 2;
		float phase = random.nextFloat() * 100;
		float colorShift = random.nextFloat();
		float dirX = (float) Math.cos(heading);
		float dirZ = (float) Math.sin(heading);
		int steps = 44;
		Vector3f prevLow = null;
		Vector3f prevHigh = null;
		int prevLowCol = 0;
		int prevHighCol = 0;
		float run = (t * 0.07f + phase) % 1f;
		for (int i = 0; i <= steps; i++) {
			float u = i / (float) steps;
			float along = (u - 0.5f) * length;
			// The path weaves sideways and up and down, and slowly breathes.
			float sway = (float) Math.sin(u * Math.PI * 2.3 + t * 0.22 + phase) * 3.2f;
			float lift = (float) Math.sin(u * Math.PI * 1.4 + t * 0.17 + phase * 1.7) * 1.8f;
			float px = (float) (ox - cam.x) + dirX * along - dirZ * sway;
			float pz = (float) (oz - cam.z) + dirZ * along + dirX * sway;
			float py = (float) (oy - cam.y) + lift;
			// Twist: the curtain leans from straight up towards the side and back.
			float twist = (float) Math.sin(u * 3.1 + t * 0.3 + phase) * 0.9f;
			Vector3f upDir = new Vector3f(-dirZ * (float) Math.sin(twist), (float) Math.cos(twist), dirX * (float) Math.sin(twist));
			Vector3f low = new Vector3f(px, py, pz);
			Vector3f high = new Vector3f(upDir).mul(height).add(low);
			// Faded at the two ends, close to you and far away.
			float dist = low.length();
			float near = Math.clamp((dist - 4) / 6, 0, 1);
			float far = 1 - Math.clamp((dist - 46) / 16, 0, 1);
			float ends = (float) Math.sin(u * Math.PI);
			float d = (u - run) * 7;
			float pulse = (float) Math.exp(-d * d);
			float a = light * near * far * ends * Math.min(1, s.auroraStrength.floatValue());
			int col = palette(s.ribbonColor.get(), colorShift + u * 0.8f, t);
			int bright = Colors.lerp(col, 0xFFFFFFFF, 0.35f * pulse);
			int lowCol = Colors.withAlpha(bright, (0.42f + 0.4f * pulse) * a);
			int highCol = Colors.withAlpha(col, 0);
			if (prevLow != null && a + Colors.alpha(prevLowCol) / 255f > 0.004f) {
				GlowGeo.quad(pose, b, prevLow, prevHigh, high, low, prevLowCol, prevHighCol, highCol, lowCol);
				// A thin bright edge along the bottom.
				GlowGeo.line(pose, b, prevLow, low, 0.06f + 0.05f * pulse, Colors.withAlpha(prevLowCol, Math.min(1, Colors.alpha(prevLowCol) / 255f * 1.6f)),
					Colors.withAlpha(lowCol, Math.min(1, Colors.alpha(lowCol) / 255f * 1.6f)));
			}
			prevLow = low;
			prevHigh = high;
			prevLowCol = lowCol;
			prevHighCol = highCol;
		}
	}

	// ---- starfall ----

	/**
	 * Comets of light falling all around you. Every 12-block cell of the world has its own rhythm: now and then one
	 * streaks down into it at a slant and lands in a ring of light that ripples out over the ground, with a spike
	 * of light standing where it hit for a moment. Worked out from the time alone, so nothing has to be remembered.
	 */
	private static void starfall(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int size = 12;
		int cx = Math.floorDiv((int) Math.floor(cam.x), size);
		int cz = Math.floorDiv((int) Math.floor(cam.z), size);
		float amount = s.starfallAmount.floatValue();
		Vector3f flatX = new Vector3f(1, 0, 0);
		Vector3f flatZ = new Vector3f(0, 0, 1);
		for (int dx = -3; dx <= 3; dx++) {
			for (int dz = -3; dz <= 3; dz++) {
				long cell = (cx + dx) * 918273645L ^ (cz + dz) * 192837465L;
				Random rhythm = new Random(cell);
				float period = 2.2f + rhythm.nextFloat() * 3.6f;
				double clock = t + rhythm.nextFloat() * period;
				long turn = (long) Math.floor(clock / period);
				float u = (float) (clock - turn * period);
				Random random = new Random(cell * 31 + turn * 1000003L);
				if (random.nextFloat() > 0.15f + amount * 0.85f) {
					continue;
				}
				float fall = 0.7f + random.nextFloat() * 0.5f;
				float after = 1.4f;
				if (u > fall + after) {
					continue;
				}
				double lx = (cx + dx) * size + random.nextFloat() * size;
				double lz = (cz + dz) * size + random.nextFloat() * size;
				float ground = ground(level, cell * 17 + turn, lx, lz, cam.y - 2);
				if (Float.isNaN(ground)) {
					continue;
				}
				Vector3f land = new Vector3f((float) (lx - cam.x), (float) (ground + 0.06 - cam.y), (float) (lz - cam.z));
				float dist = land.length();
				float a = light * Math.clamp((dist - 2.5f) / 4, 0, 1) * (1 - Math.clamp((dist - 34) / 10, 0, 1));
				int col = palette(s.ribbonColor.get(), random.nextFloat(), t);
				float slantX = (random.nextFloat() - 0.5f) * 14;
				float slantZ = (random.nextFloat() - 0.5f) * 14;
				if (a < 0.01f) {
					continue;
				}
				if (u < fall) {
					// Falling: faster and faster, a bright head and a long fading tail.
					float k = u / fall;
					float at = k * k;
					Vector3f start = new Vector3f(land).add(slantX, 42, slantZ);
					Vector3f head = new Vector3f(start).lerp(land, at);
					Vector3f tail = new Vector3f(start).lerp(land, Math.max(0, at - 0.24f));
					int hot = Colors.lerp(col, 0xFFFFFFFF, 0.6f);
					GlowGeo.line(pose, b, head, tail, 0.32f, Colors.withAlpha(hot, a), Colors.withAlpha(col, 0));
					GlowGeo.line(pose, b, head, tail, 1.1f, Colors.withAlpha(col, 0.28f * a), Colors.withAlpha(col, 0));
					Vector3f[] axes = GlowGeo.facing(head);
					GlowGeo.disc(pose, b, head, axes[0], axes[1], 1.1f, Colors.withAlpha(col, 0.45f * a), 10);
					GlowGeo.disc(pose, b, head, axes[0], axes[1], 0.3f, Colors.withAlpha(hot, 0.95f * a), 8);
				} else {
					// Landed: rings ripple out over the ground and a spike of light sinks back into it.
					float q = (u - fall) / after;
					float fadeOut = (1 - q) * (1 - q);
					float reach = 1 - (1 - q) * (1 - q);
					GlowGeo.arc(pose, b, land, flatX, flatZ, 0.4f + 3.4f * reach, 0.04f + 0.12f * (1 - q), 0, Math.PI * 2, Colors.withAlpha(col, 0.8f * fadeOut * a));
					GlowGeo.arc(pose, b, land, flatX, flatZ, 0.2f + 1.9f * reach, 0.03f + 0.07f * (1 - q), 0, Math.PI * 2,
						Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.5f), 0.6f * fadeOut * a));
					GlowGeo.disc(pose, b, land, flatX, flatZ, 0.6f + 2.4f * reach, Colors.withAlpha(col, 0.16f * fadeOut * a), 16);
					Vector3f tip = new Vector3f(land).add(0, 3.2f * (1 - q) * (1 - q), 0);
					GlowGeo.line(pose, b, land, tip, 0.16f * (1 - q), Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.5f), 0.8f * fadeOut * a), Colors.withAlpha(col, 0));
				}
			}
		}
	}

	// ---- sky crystals ----

	/**
	 * Huge crystals of light hovering over the land, here and there in the world: each one turns slowly and bobs,
	 * with rings orbiting it, a beam running down to the ground and a broken circle of light turning underneath.
	 */
	private static void crystals(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int size = 44;
		int cx = Math.floorDiv((int) Math.floor(cam.x), size);
		int cz = Math.floorDiv((int) Math.floor(cam.z), size);
		Vector3f flatX = new Vector3f(1, 0, 0);
		Vector3f flatZ = new Vector3f(0, 0, 1);
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				long seed = (cx + dx) * 668265263L ^ (cz + dz) * 374761393L;
				Random random = new Random(seed);
				if (random.nextFloat() > 0.6f) {
					continue;
				}
				double ox = (cx + dx) * size + 6 + random.nextFloat() * (size - 12);
				double oz = (cz + dz) * size + 6 + random.nextFloat() * (size - 12);
				float ground = ground(level, seed * 13 + 7, ox, oz, cam.y - 2);
				if (Float.isNaN(ground)) {
					continue;
				}
				float phase = random.nextFloat() * 40;
				float tall = 2.6f + random.nextFloat() * 2.4f;
				float wide = 0.9f + random.nextFloat() * 0.7f;
				float hover = 7 + random.nextFloat() * 7 + (float) Math.sin(t * 0.4 + phase) * 0.6f;
				float spin = t * 0.22f * (random.nextBoolean() ? 1 : -1) + phase;
				int col = palette(s.ribbonColor.get(), random.nextFloat(), t);
				Vector3f foot = new Vector3f((float) (ox - cam.x), (float) (ground + 0.07 - cam.y), (float) (oz - cam.z));
				Vector3f c = new Vector3f(foot).add(0, hover, 0);
				float dist = c.length();
				float a = light * Math.clamp((dist - 5) / 6, 0, 1) * (1 - Math.clamp((dist - 74) / 22, 0, 1));
				if (a < 0.01f) {
					continue;
				}
				// The crystal: a long point up, a shorter one down, four faces each.
				Vector3f top = new Vector3f(c).add(0, tall, 0);
				Vector3f bottom = new Vector3f(c).sub(0, tall * 0.62f, 0);
				Vector3f[] waist = new Vector3f[4];
				for (int j = 0; j < 4; j++) {
					double angle = spin + j * Math.PI / 2;
					waist[j] = new Vector3f(c).add((float) Math.cos(angle) * wide, 0, (float) Math.sin(angle) * wide);
				}
				int bright = Colors.lerp(col, 0xFFFFFFFF, 0.55f);
				for (int j = 0; j < 4; j++) {
					Vector3f p = waist[j];
					Vector3f n = waist[(j + 1) % 4];
					GlowGeo.quad(pose, b, top, p, n, top, Colors.withAlpha(bright, 0.5f * a), Colors.withAlpha(j % 2 == 0 ? bright : col, 0.3f * a),
						Colors.withAlpha(col, 0.16f * a), Colors.withAlpha(bright, 0.5f * a));
					GlowGeo.quad(pose, b, bottom, p, n, bottom, Colors.withAlpha(col, 0.34f * a), Colors.withAlpha(j % 2 == 0 ? col : bright, 0.2f * a),
						Colors.withAlpha(col, 0.12f * a), Colors.withAlpha(col, 0.34f * a));
					int edge = Colors.withAlpha(bright, 0.85f * a);
					GlowGeo.line(pose, b, top, p, 0.06f, edge);
					GlowGeo.line(pose, b, bottom, p, 0.06f, edge);
					GlowGeo.line(pose, b, p, n, 0.05f, edge);
				}
				Vector3f[] axes = GlowGeo.facing(c);
				GlowGeo.disc(pose, b, c, axes[0], axes[1], tall * 1.7f, Colors.withAlpha(col, 0.12f * a), 16);
				// Two rings orbiting it, tilted and turning against each other.
				for (int ring = 0; ring < 2; ring++) {
					float turn = t * (0.5f + ring * 0.2f) * (ring == 0 ? 1 : -1) + phase;
					float tilt = 0.5f + ring * 0.7f;
					Vector3f rx = new Vector3f((float) Math.cos(turn), 0, (float) Math.sin(turn));
					Vector3f ry = new Vector3f(-(float) Math.sin(turn) * (float) Math.cos(tilt), (float) Math.sin(tilt), (float) Math.cos(turn) * (float) Math.cos(tilt));
					float radius = wide * (2.1f + ring * 0.7f);
					GlowGeo.arc(pose, b, c, rx, ry, radius, 0.06f, 0, Math.PI * 2, Colors.withAlpha(col, 0.55f * a));
					Vector3f bead = GlowGeo.around(c, rx, ry, radius, t * 1.3 + ring * 2);
					Vector3f[] beadAxes = GlowGeo.facing(bead);
					GlowGeo.disc(pose, b, bead, beadAxes[0], beadAxes[1], 0.22f, Colors.withAlpha(bright, 0.9f * a), 8);
				}
				// A beam down to the ground, and a broken circle of light turning where it lands.
				GlowGeo.line(pose, b, bottom, foot, 0.14f, Colors.withAlpha(bright, 0.5f * a), Colors.withAlpha(col, 0.12f * a));
				GlowGeo.line(pose, b, bottom, foot, 0.6f, Colors.withAlpha(col, 0.12f * a), Colors.withAlpha(col, 0));
				for (int part = 0; part < 4; part++) {
					double a0 = -t * 0.3 + phase + part * Math.PI / 2;
					GlowGeo.arc(pose, b, foot, flatX, flatZ, 2.4f, 0.09f, a0, a0 + 1.15, Colors.withAlpha(col, 0.6f * a));
					GlowGeo.arc(pose, b, foot, flatX, flatZ, 1.5f, 0.05f, -a0, -a0 + 0.8, Colors.withAlpha(bright, 0.45f * a));
				}
				GlowGeo.disc(pose, b, foot, flatX, flatZ, 2.6f, Colors.withAlpha(col, 0.14f * a), 16);
			}
		}
	}

	// ---- pulse grid ----

	private static final int GRID_REACH = 18;
	private static final int GRID_SIDE = GRID_REACH * 2 + 2;
	private static final float[] GRID_HEIGHTS = new float[GRID_SIDE * GRID_SIDE];
	private static int gridX = Integer.MIN_VALUE;
	private static int gridZ;
	private static long gridRead;

	/**
	 * A grid of light that sweeps out from you over the land in waves, like a sonar scan: where the wave passes, the
	 * ground's own shape lights up as a mesh (hills, steps, the tops of trees), then goes dark again behind it.
	 */
	private static void pulseGrid(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int px = (int) Math.floor(cam.x);
		int pz = (int) Math.floor(cam.z);
		long now = System.nanoTime();
		if (px != gridX || pz != gridZ || now - gridRead > 500_000_000L) {
			gridX = px;
			gridZ = pz;
			gridRead = now;
			for (int ix = 0; ix < GRID_SIDE; ix++) {
				for (int iz = 0; iz < GRID_SIDE; iz++) {
					GRID_HEIGHTS[ix * GRID_SIDE + iz] = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px - GRID_REACH + ix, pz - GRID_REACH + iz);
				}
			}
		}
		float period = 3.4f;
		for (int wave = 0; wave < 2; wave++) {
			float phase = ((t / period) + wave * 0.5f) % 1f;
			float reach = phase * GRID_REACH;
			float band = 2.4f;
			float strength = light * (1 - phase) * (wave == 0 ? 1 : 0.6f);
			if (strength < 0.02f) {
				continue;
			}
			int from = (int) Math.max(0, Math.floor(GRID_REACH - reach - band));
			int to = (int) Math.min(GRID_SIDE - 2, Math.ceil(GRID_REACH + reach + band));
			for (int ix = from; ix <= to; ix++) {
				for (int iz = from; iz <= to; iz++) {
					float wx = px - GRID_REACH + ix + 0.5f;
					float wz = pz - GRID_REACH + iz + 0.5f;
					float dx = (float) (wx - cam.x);
					float dz = (float) (wz - cam.z);
					float dist = (float) Math.sqrt(dx * dx + dz * dz);
					float w = 1 - Math.abs(dist - reach) / band;
					if (w <= 0 || dist > GRID_REACH) {
						continue;
					}
					float h = GRID_HEIGHTS[ix * GRID_SIDE + iz];
					if (Math.abs(h - cam.y) > 14) {
						continue;
					}
					float a = strength * w * w;
					int col = Colors.withAlpha(palette(s.ribbonColor.get(), dist * 0.04f, t), Math.min(1, 0.95f * a));
					Vector3f p = new Vector3f(dx, (float) (h + 0.06 - cam.y), dz);
					float hx = GRID_HEIGHTS[(ix + 1) * GRID_SIDE + iz];
					float hz = GRID_HEIGHTS[ix * GRID_SIDE + iz + 1];
					if (Math.abs(hx - h) <= 3) {
						GlowGeo.line(pose, b, p, new Vector3f(dx + 1, (float) (hx + 0.06 - cam.y), dz), 0.065f, col);
					}
					if (Math.abs(hz - h) <= 3) {
						GlowGeo.line(pose, b, p, new Vector3f(dx, (float) (hz + 0.06 - cam.y), dz + 1), 0.065f, col);
					}
				}
			}
		}
	}

	// ---- light rivers ----

	/**
	 * Streams of light flowing over the ground: winding paths that follow the land, with light running along them.
	 * They belong to places in the world, and bend aside round your feet as you walk through them.
	 */
	private static void rivers(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int size = 34;
		int cx = Math.floorDiv((int) Math.floor(cam.x), size);
		int cz = Math.floorDiv((int) Math.floor(cam.z), size);
		Vector3f up = new Vector3f(0, 1, 0);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long seed = (cx + dx) * 2654435761L ^ (cz + dz) * 40503L * 65537L;
				Random random = new Random(seed);
				if (random.nextFloat() > 0.75f) {
					continue;
				}
				double ox = (cx + dx) * size + random.nextFloat() * size;
				double oz = (cz + dz) * size + random.nextFloat() * size;
				double heading = random.nextFloat() * Math.PI * 2;
				float length = 34 + random.nextFloat() * 22;
				float phase = random.nextFloat() * 60;
				float dirX = (float) Math.cos(heading);
				float dirZ = (float) Math.sin(heading);
				int col = palette(s.ribbonColor.get(), random.nextFloat(), t);
				int steps = 44;
				Vector3f prev = null;
				float prevHeight = 0;
				int prevCol = 0;
				for (int i = 0; i <= steps; i++) {
					float u = i / (float) steps;
					float along = (u - 0.5f) * length;
					float sway = (float) Math.sin(u * 5.5 + phase) * 4.5f + (float) Math.sin(u * 13 + phase * 2) * 1.1f;
					double x = ox + dirX * along - dirZ * sway;
					double z = oz + dirZ * along + dirX * sway;
					// Round your feet: pushed aside, the closer the further.
					double fx = x - cam.x;
					double fz = z - cam.z;
					double near = Math.sqrt(fx * fx + fz * fz);
					if (near < 3 && near > 1e-3) {
						double pushOut = (3 - near) * 0.75;
						x += fx / near * pushOut;
						z += fz / near * pushOut;
					}
					int bx = (int) Math.floor(x);
					int bz = (int) Math.floor(z);
					float height = level.getHeight(Heightmap.Types.MOTION_BLOCKING, bx, bz);
					Vector3f p = new Vector3f((float) (x - cam.x), (float) (height + 0.07 - cam.y), (float) (z - cam.z));
					float dist = p.length();
					float far = 1 - Math.clamp((dist - 40) / 12, 0, 1);
					float flow = 0.5f + 0.5f * (float) Math.sin(u * 46 - t * 3.2 + phase);
					float a = light * far * (float) Math.sin(u * Math.PI) * (0.38f + 0.62f * flow * flow * flow);
					int c = Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.35f * flow), Math.min(1, a));
					boolean usable = Math.abs(height - cam.y) < 18;
					if (prev != null && usable && Math.abs(height - prevHeight) <= 1.5f && (a > 0.004f || Colors.alpha(prevCol) > 1)) {
						GlowGeo.flatLine(pose, b, prev, p, up, 0.7f, Colors.withAlpha(prevCol, Colors.alpha(prevCol) / 255f * 0.45f),
							Colors.withAlpha(c, Colors.alpha(c) / 255f * 0.45f));
						GlowGeo.flatLine(pose, b, prev, p, up, 0.2f, prevCol, c);
					}
					prev = usable ? p : null;
					prevHeight = height;
					prevCol = c;
				}
			}
		}
	}

	// ---- sky shapes ----

	/** The twelve corners of an icosahedron and the thirty edges between them. */
	private static final Vector3f[] ICO = new Vector3f[12];
	private static final int[][] ICO_EDGES = new int[30][];

	static {
		float phi = 1.618034f;
		int n = 0;
		for (int a = -1; a <= 1; a += 2) {
			for (int c = -1; c <= 1; c += 2) {
				ICO[n++] = new Vector3f(0, a, c * phi).normalize();
				ICO[n++] = new Vector3f(a, c * phi, 0).normalize();
				ICO[n++] = new Vector3f(c * phi, 0, a).normalize();
			}
		}
		int e = 0;
		for (int i = 0; i < 12; i++) {
			for (int j = i + 1; j < 12; j++) {
				if (ICO[i].distance(ICO[j]) < 1.1f && e < 30) {
					ICO_EDGES[e++] = new int[]{i, j};
				}
			}
		}
	}

	/**
	 * Vast shapes of light far up in the sky, a few to every horizon: a slowly tumbling wire polyhedron, a gyroscope
	 * of rings turning inside each other, or a great gate. They stay where they are in the world as you travel.
	 */
	private static void skyShapes(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int size = 96;
		float sight = sight();
		int cx = Math.floorDiv((int) Math.floor(cam.x), size);
		int cz = Math.floorDiv((int) Math.floor(cam.z), size);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long seed = (cx + dx) * 1099511628211L ^ (cz + dz) * 16777619L;
				Random random = new Random(seed);
				if (random.nextFloat() > 0.8f) {
					continue;
				}
				double ox = (cx + dx) * size + 20 + random.nextFloat() * (size - 40);
				double oz = (cz + dz) * size + 20 + random.nextFloat() * (size - 40);
				// As high above the land as it was where you first saw it from.
				float ground = ground(level, seed * 7 + 3, cam.x, cam.z, cam.y);
				if (Float.isNaN(ground)) {
					continue;
				}
				float phase = random.nextFloat() * 50;
				float big = 7 + random.nextFloat() * 6;
				int kind = random.nextInt(3);
				double oy = ground + 34 + random.nextFloat() * 22 + Math.sin(t * 0.12 + phase) * 2;
				Vector3f c = new Vector3f((float) (ox - cam.x), (float) (oy - cam.y), (float) (oz - cam.z));
				float dist = c.length();
				float a = light * (1 - Math.clamp((dist - sight * 0.7f) / (sight * 0.3f), 0, 1));
				if (a < 0.01f) {
					continue;
				}
				int col = palette(s.ribbonColor.get(), random.nextFloat(), t);
				int bright = Colors.lerp(col, 0xFFFFFFFF, 0.5f);
				float line = 0.16f + dist * 0.0022f;
				Vector3f[] axes = GlowGeo.facing(c);
				GlowGeo.disc(pose, b, c, axes[0], axes[1], big * 1.5f, Colors.withAlpha(col, 0.07f * a), 20);
				if (kind == 0) {
					// A wire polyhedron tumbling slowly, with a smaller one turning the other way inside.
					for (int shell = 0; shell < 2; shell++) {
						float r = shell == 0 ? big : big * 0.45f;
						float turn = (t * 0.07f + phase) * (shell == 0 ? 1 : -2.2f);
						Vector3f[] v = new Vector3f[12];
						for (int i = 0; i < 12; i++) {
							v[i] = new Vector3f(ICO[i]).rotateY(turn).rotateX(turn * 0.6f + 0.4f).mul(r).add(c);
						}
						int cc = Colors.withAlpha(shell == 0 ? col : bright, (shell == 0 ? 0.75f : 0.9f) * a);
						for (int[] edge : ICO_EDGES) {
							if (edge != null) {
								GlowGeo.line(pose, b, v[edge[0]], v[edge[1]], line, cc);
							}
						}
					}
				} else if (kind == 1) {
					// A gyroscope: rings inside rings, each turning about its own axis.
					for (int ring = 0; ring < 4; ring++) {
						float turn = t * (0.1f + ring * 0.05f) * (ring % 2 == 0 ? 1 : -1) + phase + ring;
						float tilt = 0.4f + ring * 0.75f;
						Vector3f rx = new Vector3f((float) Math.cos(turn), 0, (float) Math.sin(turn));
						Vector3f ry = new Vector3f(-(float) Math.sin(turn) * (float) Math.cos(tilt), (float) Math.sin(tilt), (float) Math.cos(turn) * (float) Math.cos(tilt));
						float r = big * (1 - ring * 0.2f);
						GlowGeo.arc(pose, b, c, rx, ry, r, line * 1.2f, 0, Math.PI * 2, Colors.withAlpha(ring % 2 == 0 ? col : bright, 0.75f * a));
						Vector3f bead = GlowGeo.around(c, rx, ry, r, t * 0.4 + ring * 1.7);
						Vector3f[] beadAxes = GlowGeo.facing(bead);
						GlowGeo.disc(pose, b, bead, beadAxes[0], beadAxes[1], line * 4, Colors.withAlpha(bright, 0.9f * a), 8);
					}
				} else {
					// A gate: a great ring standing upright with broken arcs turning round it and a shimmer inside.
					float heading = phase;
					Vector3f rx = new Vector3f((float) Math.cos(heading), 0, (float) Math.sin(heading));
					Vector3f ry = new Vector3f(0, 1, 0);
					GlowGeo.arc(pose, b, c, rx, ry, big, line * 1.6f, 0, Math.PI * 2, Colors.withAlpha(bright, 0.85f * a));
					GlowGeo.disc(pose, b, c, rx, ry, big, Colors.withAlpha(col, 0.16f * a), 28);
					for (int part = 0; part < 6; part++) {
						double a0 = t * 0.15 * (part % 2 == 0 ? 1 : -1) + part * Math.PI / 3;
						float r = big * (part % 2 == 0 ? 1.14f : 0.84f);
						GlowGeo.arc(pose, b, c, rx, ry, r, line, a0, a0 + 0.7, Colors.withAlpha(col, 0.7f * a));
					}
				}
			}
		}
	}

	// ---- rift ----

	/**
	 * A tear in the sky, at a few fixed places in the world: a jagged crack of light with a dark gap opening and
	 * closing down its middle, rings of light leaning in round it and shards slowly circling.
	 */
	private static void rifts(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int size = 130;
		float sight = sight();
		int cx = Math.floorDiv((int) Math.floor(cam.x), size);
		int cz = Math.floorDiv((int) Math.floor(cam.z), size);
		for (int dx = -1; dx <= 1; dx++) {
			for (int dz = -1; dz <= 1; dz++) {
				long seed = (cx + dx) * 6364136223846793005L ^ (cz + dz) * 1442695040888963407L;
				Random random = new Random(seed);
				if (random.nextFloat() > 0.6f) {
					continue;
				}
				double ox = (cx + dx) * size + 25 + random.nextFloat() * (size - 50);
				double oz = (cz + dz) * size + 25 + random.nextFloat() * (size - 50);
				float ground = ground(level, seed * 5 + 1, cam.x, cam.z, cam.y);
				if (Float.isNaN(ground)) {
					continue;
				}
				float tall = 17 + random.nextFloat() * 9;
				double oy = ground + 22 + random.nextFloat() * 12 + tall / 2;
				double heading = random.nextFloat() * Math.PI;
				float phase = random.nextFloat() * 30;
				Vector3f c = new Vector3f((float) (ox - cam.x), (float) (oy - cam.y), (float) (oz - cam.z));
				float dist = c.length();
				float a = light * (1 - Math.clamp((dist - sight * 0.7f) / (sight * 0.3f), 0, 1));
				if (a < 0.01f) {
					continue;
				}
				int col = palette(s.ribbonColor.get(), random.nextFloat(), t);
				int bright = Colors.lerp(col, 0xFFFFFFFF, 0.7f);
				Vector3f side = new Vector3f((float) Math.cos(heading), 0, (float) Math.sin(heading));
				int points = 15;
				float open = 0.75f + 0.35f * (float) Math.sin(t * 0.7 + phase);
				Vector3f prevLeft = null;
				Vector3f prevRight = null;
				for (int i = 0; i < points; i++) {
					float u = i / (float) (points - 1);
					float taper = (float) Math.sin(u * Math.PI);
					float jag = (random.nextFloat() - 0.5f) * 3.8f * taper + (float) Math.sin(t * 1.3 + i * 1.9 + phase) * 0.25f * taper;
					Vector3f mid = new Vector3f(side).mul(jag).add(c).add(0, (u - 0.5f) * tall, 0);
					float gap = taper * open * 1.05f;
					Vector3f left = new Vector3f(side).mul(-gap).add(mid);
					Vector3f right = new Vector3f(side).mul(gap).add(mid);
					if (prevLeft != null) {
						// The dark inside, then its two bright edges and their glow.
						GlowGeo.quad(pose, b, prevLeft, prevRight, right, left, Colors.withAlpha(0xFF05010D, 0.75f * a));
						for (int edge = 0; edge < 2; edge++) {
							Vector3f p0 = edge == 0 ? prevLeft : prevRight;
							Vector3f p1 = edge == 0 ? left : right;
							GlowGeo.line(pose, b, p0, p1, 0.28f, Colors.withAlpha(bright, 0.95f * a));
							GlowGeo.line(pose, b, p0, p1, 1.5f, Colors.withAlpha(col, 0.25f * a));
							GlowGeo.line(pose, b, p0, p1, 4f, Colors.withAlpha(col, 0.07f * a));
						}
					}
					prevLeft = left;
					prevRight = right;
				}
				// Light leaning in round it, and shards circling.
				for (int ring = 0; ring < 3; ring++) {
					float turn = t * (0.12f + ring * 0.05f) + phase + ring * 2;
					float tilt = 0.25f + ring * 0.3f;
					Vector3f rx = new Vector3f((float) Math.cos(turn), 0, (float) Math.sin(turn));
					Vector3f ry = new Vector3f(-(float) Math.sin(turn) * (float) Math.sin(tilt), (float) Math.cos(tilt) * 0.35f, (float) Math.cos(turn) * (float) Math.sin(tilt));
					float r = 4.5f + ring * 2.4f;
					for (int part = 0; part < 3; part++) {
						double a0 = part * Math.PI * 2 / 3;
						GlowGeo.arc(pose, b, c, rx, new Vector3f(ry).normalize(), r, 0.16f, a0, a0 + 1.5, Colors.withAlpha(col, 0.5f * a));
					}
				}
				for (int shard = 0; shard < 9; shard++) {
					double angle = t * (0.25 + (shard % 3) * 0.08) + shard * Math.PI * 2 / 9;
					float r = 4 + (shard % 4) * 1.5f;
					Vector3f p = new Vector3f(c).add((float) Math.cos(angle) * r, (float) Math.sin(t * 0.3 + shard * 1.7) * tall * 0.3f, (float) Math.sin(angle) * r);
					Vector3f tip = new Vector3f(p).add(0, 0.9f, 0);
					Vector3f base = new Vector3f(p).sub(0, 0.9f, 0);
					GlowGeo.line(pose, b, tip, base, 0.35f, Colors.withAlpha(bright, 0.85f * a), Colors.withAlpha(col, 0.3f * a));
				}
			}
		}
	}

	// ---- motes ----

	/**
	 * Lights hanging in the air in a box around you. Each belongs to a spot in the world (cells of 5 blocks), drifts a
	 * little round it and breathes in and out; none closer than 1.5 blocks, none inside blocks.
	 */
	private static void motes(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level, float t, VoidSettings s, float light) {
		int size = 5;
		int cx = Math.floorDiv((int) Math.floor(cam.x), size);
		int cy = Math.floorDiv((int) Math.floor(cam.y), size);
		int cz = Math.floorDiv((int) Math.floor(cam.z), size);
		float amount = s.moteAmount.floatValue();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		for (int dx = -3; dx <= 3; dx++) {
			for (int dy = -1; dy <= 2; dy++) {
				for (int dz = -3; dz <= 3; dz++) {
					long seed = (cx + dx) * 73856093L ^ (cy + dy) * 19349663L ^ (cz + dz) * 83492791L;
					Random random = new Random(seed);
					if (random.nextFloat() > amount * 0.75f) {
						continue;
					}
					float phase = random.nextFloat() * 50;
					double x = (cx + dx) * size + random.nextFloat() * size + Math.sin(t * 0.21 + phase) * 0.7;
					double y = (cy + dy) * size + random.nextFloat() * size + Math.sin(t * 0.17 + phase * 1.3) * 0.5;
					double z = (cz + dz) * size + random.nextFloat() * size + Math.cos(t * 0.19 + phase) * 0.7;
					at.set(x, y, z);
					if (!level.getBlockState(at).isAir() && level.getBlockState(at).isSolid()) {
						continue;
					}
					Vector3f c = new Vector3f((float) (x - cam.x), (float) (y - cam.y), (float) (z - cam.z));
					float dist = c.length();
					float near = Math.clamp((dist - 1.5f) / 2, 0, 1);
					float far = 1 - Math.clamp((dist - 12) / 5, 0, 1);
					float breathe = 0.5f + 0.5f * (float) Math.sin(t * 0.6 + phase);
					float a = light * near * far * (0.25f + 0.75f * breathe * breathe);
					if (a < 0.01f) {
						continue;
					}
					int col = palette(s.ribbonColor.get(), random.nextFloat(), t);
					Vector3f[] axes = GlowGeo.facing(c);
					float r = 0.05f + random.nextFloat() * 0.04f;
					GlowGeo.disc(pose, b, c, axes[0], axes[1], r * 4, Colors.withAlpha(col, 0.3f * a), 10);
					GlowGeo.disc(pose, b, c, axes[0], axes[1], r, Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.6f), 0.95f * a), 8);
				}
			}
		}
	}
}
