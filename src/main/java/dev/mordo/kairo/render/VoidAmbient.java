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
 *     <li><b>floating motes</b>: small lights hanging in the air close around you, drifting and slowly breathing.</li>
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
