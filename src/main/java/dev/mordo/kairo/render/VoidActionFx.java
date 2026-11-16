package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.VoidSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Kairo Void's light that answers what you do, all custom geometry and only on your screen:
 * <ul>
 *     <li><b>footstep ripples</b>: a ring of light over the ground with every step, a big one when you land;</li>
 *     <li><b>rush streaks</b>: lines of light streaming past when you sprint-jump or fall fast, and a ring when you
 *     break into a sprint;</li>
 *     <li><b>block shatter</b>: a broken block bursts into shards of light in its own colour, inside a frame of light
 *     flying apart (in place of Minecraft's crumbs);</li>
 *     <li><b>place pulse</b>: a block you place lights up along its edges and sends a frame of light outwards;</li>
 *     <li><b>kill streak crown</b>: kills in a row build a crown of light over your head, one spike per kill;</li>
 *     <li><b>hit echo</b>: a ghost of what you hit, drawn in rings of light, slides back the way it was knocked.</li>
 * </ul>
 * Nothing flashes: everything eases in and fades out.
 */
public final class VoidActionFx {
	private record Ripple(Vec3 pos, long start, float size, float life, boolean landing) {
	}

	private record Frame(AABB box, long start, int color, boolean placed) {
	}

	private record Echo(Vec3 pos, long start, float width, float height, Vec3 push) {
	}

	private record Dash(Vec3 pos, long start, Vec3 dir) {
	}

	private static final List<Ripple> RIPPLES = new ArrayList<>();
	private static final List<Frame> FRAMES = new ArrayList<>();
	private static final List<Echo> ECHOES = new ArrayList<>();
	private static final List<Dash> DASHES = new ArrayList<>();
	/** How long a kill streak lasts after the last kill. */
	private static final float STREAK_SECONDS = 12;

	private static Vec3 lastFeet;
	private static double walked;
	private static boolean wasOnGround = true;
	private static double airTop;
	private static boolean wasSprinting;
	private static final Vector3f velocity = new Vector3f();
	private static int streak;
	private static long lastKill;

	private VoidActionFx() {
	}

	private static float age(long start) {
		return (System.nanoTime() - start) / 1e9f;
	}

	private static int color(VoidSettings s, float k, float t) {
		return GlowGeo.color(s.actionColor.get(), k, t);
	}

	// ---- events ----

	/** You got a kill: the streak grows (or starts again if the last one was too long ago). */
	public static void onKill() {
		if (!Visuals.on()) {
			return;
		}
		streak = age(lastKill) < STREAK_SECONDS && lastKill != 0 ? streak + 1 : 1;
		lastKill = System.nanoTime();
	}

	/** For the tests: a streak of this many kills, as if the last was just now. */
	public static void testStreak(int kills) {
		streak = kills;
		lastKill = kills > 0 ? System.nanoTime() : 0;
	}

	/** A hit of yours landed on something standing at {@code feet}; {@code push} is the way it gets knocked. */
	public static void onHit(Vec3 feet, float width, float height, Vec3 push) {
		if (Visuals.on() && VoidSettings.INSTANCE.hitEcho.isOn() && ECHOES.size() < 12) {
			ECHOES.add(new Echo(feet, System.nanoTime(), width, height, push));
		}
	}

	/** A block broke. True when Kairo drew it (and Minecraft's crumbs should be skipped). */
	public static boolean onBreak(BlockPos pos, BlockState state) {
		if (!Visuals.on() || !VoidSettings.INSTANCE.blockShatter.isOn() || state.isAir()) {
			return false;
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return false;
		}
		int map = state.getMapColor(mc.level, pos).col;
		int color = Colors.lerp(map == 0 ? 0xFFD6E6F5 : 0xFF000000 | map, 0xFFFFFFFF, 0.3f);
		LightParticles.shards(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, color, 16);
		if (FRAMES.size() < 24) {
			FRAMES.add(new Frame(new AABB(pos), System.nanoTime(), color, false));
		}
		return true;
	}

	/** You placed a block here. */
	public static void onPlace(BlockPos pos) {
		if (Visuals.on() && VoidSettings.INSTANCE.placePulse.isOn() && FRAMES.size() < 24) {
			FRAMES.add(new Frame(new AABB(pos), System.nanoTime(), 0, true));
		}
	}

	// ---- every frame ----

	/** Follows your movement: steps, landings, the start of a sprint, your speed. */
	static void track(LocalPlayer player, Vec3 feet, VoidSettings s, float dt) {
		if (lastFeet == null || lastFeet.distanceToSqr(feet) > 100) {
			lastFeet = feet;
			velocity.zero();
		}
		Vec3 moved = feet.subtract(lastFeet);
		lastFeet = feet;
		if (dt > 1e-4f) {
			Vector3f now = new Vector3f((float) moved.x, (float) moved.y, (float) moved.z).div(dt);
			velocity.lerp(now, Math.min(1, dt * 8));
		}
		boolean ground = player.onGround();
		boolean showing = !player.isSpectator() && !player.isInvisible();
		if (s.footsteps.isOn() && showing) {
			// Level movement counts as walking (the frame a step lands in isn't always one where you're "on the ground").
			if (Math.abs(moved.y) < 0.02) {
				walked += Math.sqrt(moved.x * moved.x + moved.z * moved.z);
			}
			if (ground) {
				float stride = player.isSprinting() ? 2.3f : 1.6f;
				if (walked >= stride) {
					walked = 0;
					ripple(feet, player.isSprinting() ? 1.25f : 0.95f, 0.9f, false);
				}
				if (!wasOnGround) {
					double drop = airTop - feet.y;
					if (drop > 1.1) {
						ripple(feet, (float) Math.min(4.5, 1.4 + drop * 0.45), 1.15f, true);
					}
					// A real jump or fall starts the stride again; a one-frame "not on the ground" doesn't.
					if (drop > 0.3) {
						walked = 0;
					}
				}
			}
		}
		if (ground) {
			airTop = feet.y;
		} else {
			airTop = wasOnGround ? feet.y : Math.max(airTop, feet.y);
		}
		wasOnGround = ground;
		boolean sprinting = player.isSprinting();
		if (sprinting && !wasSprinting && s.rushStreaks.isOn() && showing && DASHES.size() < 6) {
			Vec3 look = Vec3.directionFromRotation(0, player.getYRot());
			DASHES.add(new Dash(feet.add(0, 1.0, 0).add(look.scale(0.6)), System.nanoTime(), look));
		}
		wasSprinting = sprinting;
	}

	private static void ripple(Vec3 feet, float size, float life, boolean landing) {
		if (RIPPLES.size() < 40) {
			RIPPLES.add(new Ripple(feet.add(0, 0.04, 0), System.nanoTime(), size, life, landing));
		}
	}

	private static Vector3f rel(Vec3 p, Vec3 cam) {
		return new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
	}

	static void draw(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, Vector3f at, float t, VoidSettings s, boolean thirdPerson, boolean showYou) {
		Vector3f flatX = new Vector3f(1, 0, 0);
		Vector3f flatZ = new Vector3f(0, 0, 1);
		// Footstep ripples.
		for (Iterator<Ripple> it = RIPPLES.iterator(); it.hasNext(); ) {
			Ripple r = it.next();
			float k = age(r.start()) / r.life();
			if (k >= 1) {
				it.remove();
				continue;
			}
			Vector3f c = rel(r.pos(), cam);
			float reach = 1 - (1 - k) * (1 - k);
			float fade = (1 - k) * (1 - k);
			int col = color(s, k * 0.5f, t);
			float radius = r.size() * (0.18f + 0.82f * reach);
			GlowGeo.arc(pose, b, c, flatX, flatZ, radius, 0.02f + 0.06f * (1 - k), 0, Math.PI * 2, Colors.withAlpha(col, 0.85f * fade));
			GlowGeo.arc(pose, b, c, flatX, flatZ, radius * 0.6f, 0.015f + 0.03f * (1 - k), 0, Math.PI * 2,
				Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.5f), 0.5f * fade));
			if (r.landing()) {
				// A landing: spokes of light shooting out between the rings, and a soft glow on the ground.
				for (int i = 0; i < 10; i++) {
					double a = i * Math.PI * 2 / 10 + r.start() % 7;
					Vector3f from = GlowGeo.around(c, flatX, flatZ, radius * 0.7f, a);
					Vector3f to = GlowGeo.around(c, flatX, flatZ, radius * 1.15f, a);
					GlowGeo.flatLine(pose, b, from, to, new Vector3f(0, 1, 0), 0.05f, Colors.withAlpha(col, 0.8f * fade), Colors.withAlpha(col, 0));
				}
				GlowGeo.disc(pose, b, c, flatX, flatZ, radius, Colors.withAlpha(col, 0.2f * fade), 16);
			}
		}
		// The ring you leave behind when you break into a sprint.
		for (Iterator<Dash> it = DASHES.iterator(); it.hasNext(); ) {
			Dash d = it.next();
			float k = age(d.start()) / 0.55f;
			if (k >= 1) {
				it.remove();
				continue;
			}
			Vector3f c = rel(d.pos(), cam);
			Vector3f up = new Vector3f(0, 1, 0);
			Vector3f side = new Vector3f((float) -d.dir().z, 0, (float) d.dir().x);
			float fade = (1 - k) * (1 - k);
			int col = color(s, 0.2f, t);
			for (int ring = 0; ring < 2; ring++) {
				float kk = Math.max(0, k - ring * 0.18f);
				float radius = 0.5f + 2.2f * (1 - (1 - kk) * (1 - kk));
				GlowGeo.arc(pose, b, c, side, up, radius, 0.03f + 0.07f * (1 - kk), 0, Math.PI * 2, Colors.withAlpha(col, (ring == 0 ? 0.8f : 0.45f) * fade));
			}
		}
		if (s.rushStreaks.isOn() && showYou) {
			streaks(pose, b, at, t, s);
		}
		// Block frames: flying apart (broken) or lighting up and pulsing outwards (placed).
		for (Iterator<Frame> it = FRAMES.iterator(); it.hasNext(); ) {
			Frame f = it.next();
			float life = f.placed() ? 0.6f : 0.45f;
			float k = age(f.start()) / life;
			if (k >= 1) {
				it.remove();
				continue;
			}
			float fade = (1 - k) * (1 - k);
			int col = f.placed() ? color(s, 0.4f, t) : f.color();
			Vector3f lo = rel(new Vec3(f.box().minX, f.box().minY, f.box().minZ), cam);
			Vector3f hi = rel(new Vec3(f.box().maxX, f.box().maxY, f.box().maxZ), cam);
			if (f.placed()) {
				box(pose, b, lo, hi, 0.006f, 0.035f, Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.5f * (1 - k)), 0.95f * fade));
				box(pose, b, lo, hi, 0.5f * (1 - (1 - k) * (1 - k)), 0.03f * (1 - k) + 0.008f, Colors.withAlpha(col, 0.55f * fade));
			} else {
				box(pose, b, lo, hi, 0.02f + 0.4f * (1 - (1 - k) * (1 - k)), 0.04f * (1 - k) + 0.008f, Colors.withAlpha(col, 0.8f * fade));
			}
		}
		// Hit echoes.
		for (Iterator<Echo> it = ECHOES.iterator(); it.hasNext(); ) {
			Echo e = it.next();
			float k = age(e.start()) / 0.65f;
			if (k >= 1) {
				it.remove();
				continue;
			}
			float slide = 1 - (1 - k) * (1 - k);
			float fade = (1 - k) * (1 - k);
			Vector3f base = rel(e.pos().add(e.push().scale(0.25 + 1.1 * slide)), cam);
			int col = color(s, 0.6f, t);
			float radius = Math.max(0.25f, e.width() * 0.62f);
			int slices = 7;
			for (int i = 0; i < slices; i++) {
				float u = (i + 0.5f) / slices;
				// Narrower at the feet and the head, like a body.
				float shape = 0.55f + 0.45f * (float) Math.sin(u * Math.PI);
				Vector3f c = new Vector3f(base).add(0, e.height() * u, 0);
				GlowGeo.arc(pose, b, c, flatX, flatZ, radius * shape * (1 + 0.25f * slide), 0.055f, 0, Math.PI * 2,
					Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.3f), Math.min(1, 1.1f * fade)));
				GlowGeo.disc(pose, b, c, flatX, flatZ, radius * shape, Colors.withAlpha(col, 0.14f * fade), 12);
			}
			Vector3f top = new Vector3f(base).add(0, e.height(), 0);
			GlowGeo.line(pose, b, base, top, 0.06f, Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.5f), 0.6f * fade), Colors.withAlpha(col, 0.2f * fade));
		}
		if (s.killCrown.isOn() && showYou) {
			crown(pose, b, at, t, s, thirdPerson);
		}
	}

	/** Lines of light streaming past you along the way you're moving, the faster the more. */
	private static void streaks(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s) {
		float flat = (float) Math.sqrt(velocity.x * velocity.x + velocity.z * velocity.z);
		float strength = Math.max(Math.clamp((flat - 6.3f) / 2.2f, 0, 1), Math.clamp((Math.abs(velocity.y) - 10) / 9, 0, 1));
		if (strength < 0.03f || velocity.lengthSquared() < 1) {
			return;
		}
		Vector3f axis = new Vector3f(velocity).normalize();
		Vector3f side = Math.abs(axis.y) > 0.95f ? new Vector3f(1, 0, 0) : new Vector3f(axis).cross(0, 1, 0).normalize();
		Vector3f up = new Vector3f(side).cross(axis).normalize();
		Vector3f centre = new Vector3f(at).add(0, 1.0f, 0);
		Random random = new Random(11);
		for (int i = 0; i < 28; i++) {
			double angle = random.nextFloat() * Math.PI * 2;
			float radius = 1.7f + random.nextFloat() * 2.6f;
			float speed = 1.1f + random.nextFloat() * 1.4f;
			float along = 7 - ((t * speed + random.nextFloat()) % 1f) * 14;
			float length = (1.0f + random.nextFloat() * 1.8f) * (0.5f + 0.5f * strength);
			float alpha = strength * (1 - Math.abs(along) / 7) * 0.7f;
			Vector3f head = new Vector3f(centre).add(new Vector3f(side).mul((float) Math.cos(angle) * radius))
				.add(new Vector3f(up).mul((float) Math.sin(angle) * radius)).add(new Vector3f(axis).mul(along));
			Vector3f tail = new Vector3f(axis).mul(-length).add(head);
			int col = color(s, i / 28f, t);
			GlowGeo.line(pose, b, head, tail, 0.035f, Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.5f), alpha), Colors.withAlpha(col, 0));
		}
	}

	/** The kill streak crown: a ring over your head with one spike of light per kill (up to ten), fading when it ends. */
	private static void crown(PoseStack.Pose pose, VertexConsumer b, Vector3f at, float t, VoidSettings s, boolean thirdPerson) {
		float since = age(lastKill);
		if (streak < 2 || lastKill == 0 || since >= STREAK_SECONDS) {
			return;
		}
		float fade = Math.min(1, since / 0.3f) * Math.clamp((STREAK_SECONDS - since) / 2.5f, 0, 1);
		// Through your own eyes it floats wide above you, so you see it when you glance up.
		float radius = thirdPerson ? 0.36f : 2.3f;
		float tall = thirdPerson ? 0.24f : 0.55f;
		float width = thirdPerson ? 0.02f : 0.03f;
		Vector3f c = new Vector3f(at).add(0, thirdPerson ? 2.22f : 2.9f, 0);
		Vector3f flatX = new Vector3f(1, 0, 0);
		Vector3f flatZ = new Vector3f(0, 0, 1);
		int spikes = Math.min(streak, 10);
		int gold = Colors.lerp(color(s, 0.3f, t), 0xFFFFD86B, 0.55f);
		GlowGeo.arc(pose, b, c, flatX, flatZ, radius, width, 0, Math.PI * 2, Colors.withAlpha(gold, 0.9f * fade));
		GlowGeo.arc(pose, b, new Vector3f(c).sub(0, tall * 0.18f, 0), flatX, flatZ, radius * 1.04f, width * 0.6f, 0, Math.PI * 2, Colors.withAlpha(gold, 0.45f * fade));
		double step = Math.PI * 2 / spikes;
		for (int i = 0; i < spikes; i++) {
			double a = t * 0.5 + i * step;
			double half = Math.min(step * 0.42, thirdPerson ? 0.5 : 0.16);
			float height = tall * (i % 2 == 0 ? 1 : 0.68f) * (0.92f + 0.08f * (float) Math.sin(t * 3 + i));
			Vector3f left = GlowGeo.around(c, flatX, flatZ, radius, a - half);
			Vector3f right = GlowGeo.around(c, flatX, flatZ, radius, a + half);
			Vector3f tip = GlowGeo.around(c, flatX, flatZ, radius, a).add(0, height, 0);
			int bright = Colors.lerp(gold, 0xFFFFFFFF, 0.5f);
			float fill = thirdPerson ? 1 : 0.35f;
			GlowGeo.quad(pose, b, left, tip, tip, right, Colors.withAlpha(gold, 0.3f * fade * fill), Colors.withAlpha(bright, 0.6f * fade * fill),
				Colors.withAlpha(bright, 0.6f * fade * fill), Colors.withAlpha(gold, 0.3f * fade * fill));
			GlowGeo.line(pose, b, left, tip, width * 0.8f, Colors.withAlpha(bright, 0.9f * fade));
			GlowGeo.line(pose, b, right, tip, width * 0.8f, Colors.withAlpha(bright, 0.9f * fade));
			Vector3f[] axes = GlowGeo.facing(tip);
			GlowGeo.disc(pose, b, tip, axes[0], axes[1], width * 2.6f, Colors.withAlpha(bright, 0.9f * fade), 8);
		}
	}

	/** The twelve edges of a box grown by {@code grow} on every side. */
	private static void box(PoseStack.Pose pose, VertexConsumer b, Vector3f lo, Vector3f hi, float grow, float width, int color) {
		Vector3f[] c = new Vector3f[8];
		for (int i = 0; i < 8; i++) {
			c[i] = new Vector3f((i & 1) == 0 ? lo.x - grow : hi.x + grow, (i & 2) == 0 ? lo.y - grow : hi.y + grow, (i & 4) == 0 ? lo.z - grow : hi.z + grow);
		}
		int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
		for (int[] e : edges) {
			GlowGeo.line(pose, b, c[e[0]], c[e[1]], width, color);
		}
	}

	public static void clear() {
		RIPPLES.clear();
		FRAMES.clear();
		ECHOES.clear();
		DASHES.clear();
		lastFeet = null;
	}
}
