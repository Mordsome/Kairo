package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Kairo Void's light particles: sparks, shards, orbs, rings, petals and flakes of light drawn with Kairo's own geometry,
 * in place of Minecraft's pixel sprites. Kairo's own effects always go through {@link #add} (which falls back to a
 * normal particle when this is off); Minecraft's combat, death, potion and explosion particles are caught in
 * ParticleEngineMixin. Nothing flickers: everything fades in and out.
 */
public final class LightParticles {
	private LightParticles() {
	}

	enum Shape {SPARK, SHARD, ORB, RING, PETAL, FLAKE}

	private static final class P {
		double x, y, z;
		float vx, vy, vz;
		float age, life, size, spin, spinSpeed, gravity, drag;
		int color;
		Shape shape;
	}

	/** Minecraft's own particles that become light (ambient ones like torch flames, smoke and rain stay vanilla). */
	private static final Set<String> VANILLA = Set.of("crit", "enchanted_hit", "damage_indicator", "poof", "explosion",
		"explosion_emitter", "totem_of_undying", "heart", "angry_villager", "happy_villager", "entity_effect", "effect",
		"instant_effect", "witch", "firework", "electric_spark", "note", "glow", "wax_on", "wax_off", "scrape", "sonic_boom",
		"trial_spawner_detection", "dust_plume", "gust", "small_gust");

	private static final int MAX = 2400;
	private static final ArrayList<P> LIVE = new ArrayList<>();
	private static final Map<ParticleType<?>, String> NAMES = new IdentityHashMap<>();
	private static final Random RANDOM = new Random();
	private static long last = System.nanoTime();
	private static Level lastLevel;

	public static boolean active() {
		return Visuals.on() && VoidSettings.INSTANCE.lightParticles.isOn();
	}

	/** For Kairo's own effects: light when Light particles is on, a normal particle otherwise. */
	public static void add(Level level, ParticleOptions options, double x, double y, double z, double xa, double ya, double za) {
		if (active()) {
			spawn(options, x, y, z, xa, ya, za);
		} else {
			level.addParticle(options, x, y, z, xa, ya, za);
		}
	}

	/** From ParticleEngineMixin: true when this vanilla particle was turned into light (and vanilla's should be skipped). */
	public static boolean replaceVanilla(ParticleOptions options, double x, double y, double z, double xa, double ya, double za) {
		if (!active() || !VANILLA.contains(name(options.getType()))) {
			return false;
		}
		spawn(options, x, y, z, xa, ya, za);
		return true;
	}

	private static String name(ParticleType<?> type) {
		return NAMES.computeIfAbsent(type, t -> {
			Identifier id = BuiltInRegistries.PARTICLE_TYPE.getKey(t);
			return id == null ? "" : id.getPath();
		});
	}

	private static void spawn(ParticleOptions options, double x, double y, double z, double xa, double ya, double za) {
		if (LIVE.size() >= MAX) {
			LIVE.removeFirst();
		}
		String name = name(options.getType());
		P p = new P();
		p.x = x;
		p.y = y;
		p.z = z;
		// Vanilla speeds are per tick; ours are per second. Big values are often not speeds at all, so they're capped.
		Vector3f v = new Vector3f((float) xa, (float) ya, (float) za).mul(20);
		if (v.length() > 9) {
			v.normalize(9);
		}
		p.vx = v.x;
		p.vy = v.y;
		p.vz = v.z;
		p.spin = RANDOM.nextFloat() * 6.28f;
		p.spinSpeed = (RANDOM.nextFloat() - 0.5f) * 6;
		p.drag = 2.2f;
		int accent = Theme.accent() | 0xFF000000;
		switch (name) {
			case "end_rod" -> look(p, Shape.SPARK, 0xFFE9E2FF, 0.035f, 1.1f, 0.2f);
			case "firework" -> look(p, Shape.SPARK, RANDOM.nextBoolean() ? 0xFFFFFFFF : Colors.lerp(accent, 0xFFFFFFFF, 0.4f), 0.035f, 0.9f, 2.5f);
			case "electric_spark", "glow", "wax_on", "wax_off", "scrape" -> look(p, Shape.SPARK, 0xFF8FF3FF, 0.03f, 0.4f, 0);
			case "crit" -> look(p, Shape.SPARK, 0xFFFFF0C2, 0.04f, 0.45f, 4);
			case "enchanted_hit" -> look(p, Shape.SPARK, 0xFF9BD8FF, 0.04f, 0.45f, 2);
			case "damage_indicator" -> look(p, Shape.SHARD, 0xFFFF4D6A, 0.07f, 0.6f, 6);
			case "flame", "small_flame", "lava" -> rising(p, Shape.ORB, 0xFFFF9A3C, 0.06f, 0.8f);
			case "soul_fire_flame", "soul", "sculk_soul" -> rising(p, Shape.ORB, 0xFF5CF2FF, 0.06f, 1.0f);
			case "heart" -> rising(p, Shape.SHARD, 0xFFFF5C9A, 0.09f, 1.0f);
			case "angry_villager" -> rising(p, Shape.SHARD, 0xFFFF4A3D, 0.08f, 0.8f);
			case "happy_villager", "spore_blossom_air" -> rising(p, Shape.ORB, 0xFF7CFF8E, 0.045f, 1.0f);
			case "note" -> {
				rising(p, Shape.ORB, Colors.hsv((float) xa, 0.7f, 1f) | 0xFF000000, 0.07f, 1.0f);
				p.vx = p.vz = 0;
			}
			case "poof", "cloud", "large_smoke", "white_ash", "ash", "squid_ink", "dust_plume", "gust", "small_gust" ->
				look(p, Shape.RING, name.equals("squid_ink") ? 0xFF6B4DFF : 0xFFDDE6F0, 0.22f, 0.55f, 0);
			case "explosion" -> {
				look(p, Shape.RING, 0xFFFFD9A8, 1.1f, 0.55f, 0);
				burst(x, y, z, 6, 5, 0xFFFFC27A);
			}
			case "explosion_emitter", "sonic_boom" -> {
				look(p, Shape.RING, 0xFFFFE2B8, 3.0f, 0.75f, 0);
				burst(x, y, z, 26, 11, 0xFFFFC27A);
			}
			case "totem_of_undying" -> look(p, Shape.SHARD, RANDOM.nextBoolean() ? 0xFFFFD54A : 0xFF7CFF6B, 0.07f, 1.4f, 2);
			case "enchant", "witch", "reverse_portal", "portal", "trial_spawner_detection" -> look(p, Shape.SHARD, 0xFFB06BFF, 0.05f, 0.9f, 0);
			case "snowflake" -> look(p, Shape.FLAKE, 0xFFF2F8FF, 0.09f, 2.2f, 0.8f);
			case "cherry_leaves" -> look(p, Shape.PETAL, 0xFFFFB7D5, 0.08f, 3.0f, 0.5f);
			case "crimson_spore" -> look(p, Shape.ORB, 0xFFFF4A5E, 0.04f, 2.0f, 0);
			case "underwater" -> look(p, Shape.ORB, 0xFF7FE4FF, 0.025f, 1.6f, 0);
			case "firefly" -> look(p, Shape.ORB, 0xFFD8FF6B, 0.05f, 3.0f, 0);
			default -> {
				int color = accent;
				float size = 0.06f;
				Shape shape = Shape.ORB;
				if (options instanceof DustParticleOptions dust) {
					Vector3f c = dust.getColor();
					color = 0xFF000000 | ((int) (c.x * 255) << 16) | ((int) (c.y * 255) << 8) | (int) (c.z * 255);
					size = 0.04f * Math.max(0.6f, dust.getScale());
				} else if (options instanceof ColorParticleOption tinted) {
					color = 0xFF000000 | ((int) (tinted.getRed() * 255) << 16) | ((int) (tinted.getGreen() * 255) << 8) | (int) (tinted.getBlue() * 255);
				} else if (options instanceof BlockParticleOption block) {
					int map = block.getState().getBlock().defaultMapColor().col;
					color = map == 0 ? 0xFFEAF6FF : 0xFF000000 | map;
					shape = Shape.SHARD;
					size = 0.07f;
				}
				look(p, shape, color, size, 0.9f, shape == Shape.SHARD ? 6 : 0);
			}
		}
		LIVE.add(p);
	}

	/** Sparks flying out in every direction from one point. */
	private static void burst(double x, double y, double z, int count, float speed, int color) {
		for (int i = 0; i < count && LIVE.size() < MAX; i++) {
			P q = new P();
			q.x = x;
			q.y = y;
			q.z = z;
			Vector3f d = new Vector3f((float) RANDOM.nextGaussian(), (float) RANDOM.nextGaussian() * 0.7f + 0.3f, (float) RANDOM.nextGaussian())
				.normalize(speed * (0.5f + RANDOM.nextFloat() * 0.6f));
			q.vx = d.x;
			q.vy = d.y;
			q.vz = d.z;
			q.drag = 2.6f;
			look(q, Shape.SPARK, RANDOM.nextInt(3) == 0 ? 0xFFFFF4E0 : color, 0.06f, 0.7f, 3);
			LIVE.add(q);
		}
	}

	private static void look(P p, Shape shape, int color, float size, float life, float gravity) {
		p.shape = shape;
		p.color = color;
		p.size = size * (0.8f + RANDOM.nextFloat() * 0.4f);
		p.life = life * (0.8f + RANDOM.nextFloat() * 0.4f);
		p.gravity = gravity;
	}

	/** A soft light that drifts upwards. */
	private static void rising(P p, Shape shape, int color, float size, float life) {
		look(p, shape, color, size, life, -0.6f);
	}

	/** Moves everything on by the time since the last frame and drops what has faded out. */
	private static void update(Level level) {
		long now = System.nanoTime();
		float dt = Math.min(0.1f, (now - last) / 1e9f);
		last = now;
		if (level != lastLevel) {
			LIVE.clear();
			lastLevel = level;
		}
		for (int i = LIVE.size() - 1; i >= 0; i--) {
			P p = LIVE.get(i);
			p.age += dt;
			if (p.age >= p.life) {
				LIVE.remove(i);
				continue;
			}
			float keep = (float) Math.exp(-p.drag * dt);
			p.vx *= keep;
			p.vy = p.vy * keep - p.gravity * dt;
			p.vz *= keep;
			p.x += p.vx * dt;
			p.y += p.vy * dt;
			p.z += p.vz * dt;
			p.spin += p.spinSpeed * dt;
		}
	}

	/** Draws every light particle (called inside VoidWorldFx's glow geometry). */
	static void draw(PoseStack.Pose pose, VertexConsumer b, Vec3 cam, ClientLevel level) {
		update(level);
		boolean calm = WorldFx.calm();
		for (P p : LIVE) {
			float k = p.age / p.life;
			float alpha = Math.min(1, p.age / 0.06f) * (k < 0.55f ? 1 : 1 - (k - 0.55f) / 0.45f);
			Vector3f c = new Vector3f((float) (p.x - cam.x), (float) (p.y - cam.y), (float) (p.z - cam.z));
			if (c.lengthSquared() > 96 * 96) {
				continue;
			}
			Vector3f[] axes = GlowGeo.facing(c);
			Vector3f ax = axes[0];
			Vector3f ay = axes[1];
			int col = p.color;
			switch (p.shape) {
				case SPARK -> {
					Vector3f v = new Vector3f(p.vx, p.vy, p.vz);
					float speed = v.length();
					float len = Math.clamp(speed * 0.045f, p.size * 1.5f, 0.6f);
					Vector3f tail = speed < 1e-3f ? new Vector3f(c).sub(0, len, 0) : new Vector3f(v).normalize(-len).add(c);
					GlowGeo.line(pose, b, c, tail, p.size, Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.5f), alpha), Colors.withAlpha(col, 0));
					GlowGeo.disc(pose, b, c, ax, ay, p.size * 2.2f, Colors.withAlpha(col, 0.45f * alpha), 8);
				}
				case ORB -> {
					float pulse = 0.85f + 0.15f * (float) Math.sin(p.spin * 2);
					GlowGeo.disc(pose, b, c, ax, ay, p.size * 3f * pulse, Colors.withAlpha(col, 0.35f * alpha), 8);
					GlowGeo.disc(pose, b, c, ax, ay, p.size * 0.9f, Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.55f), 0.9f * alpha), 8);
				}
				case SHARD, PETAL -> {
					float cos = (float) Math.cos(p.spin);
					float sin = (float) Math.sin(p.spin);
					float h = p.size * (p.shape == Shape.PETAL ? 1.0f : 1.7f);
					float w = p.size * (p.shape == Shape.PETAL ? 0.7f : 0.55f);
					Vector3f up = new Vector3f(ax).mul(sin * h).add(new Vector3f(ay).mul(cos * h));
					Vector3f side = new Vector3f(ax).mul(cos * w).add(new Vector3f(ay).mul(-sin * w));
					Vector3f top = new Vector3f(c).add(up);
					Vector3f bottom = new Vector3f(c).sub(up);
					Vector3f left = new Vector3f(c).sub(side);
					Vector3f right = new Vector3f(c).add(side);
					int light = Colors.withAlpha(Colors.lerp(col, 0xFFFFFFFF, 0.45f), 0.95f * alpha);
					int deep = Colors.withAlpha(col, 0.7f * alpha);
					GlowGeo.quad(pose, b, top, right, bottom, left, light, deep, deep, deep);
					GlowGeo.disc(pose, b, c, ax, ay, p.size * 2.4f, Colors.withAlpha(col, 0.18f * alpha), 8);
				}
				case RING -> {
					float grow = 1 - (1 - k) * (1 - k);
					float r = p.size * (0.35f + 1.1f * grow);
					float a = (1 - k) * (1 - k) * (calm ? 0.35f : 0.8f);
					int edge = Colors.lerp(col, 0xFFFFFFFF, 0.35f);
					GlowGeo.arc(pose, b, c, ax, ay, r, Math.max(0.015f, Math.min(0.12f, p.size * 0.05f) * (1 - k * 0.6f)), 0, Math.PI * 2, Colors.withAlpha(edge, a));
					GlowGeo.arc(pose, b, c, ax, ay, r * 0.97f, Math.max(0.03f, p.size * 0.12f) * (1 - k), 0, Math.PI * 2, Colors.withAlpha(col, a * 0.3f));
					GlowGeo.disc(pose, b, c, ax, ay, r * 0.9f, Colors.withAlpha(col, a * 0.12f), 8);
				}
				case FLAKE -> {
					int lineCol = Colors.withAlpha(col, 0.85f * alpha);
					for (int arm = 0; arm < 3; arm++) {
						double a = p.spin + arm * Math.PI / 3;
						Vector3f d = new Vector3f(ax).mul((float) Math.cos(a) * p.size).add(new Vector3f(ay).mul((float) Math.sin(a) * p.size));
						GlowGeo.line(pose, b, new Vector3f(c).sub(d), new Vector3f(c).add(d), p.size * 0.18f, lineCol);
					}
					GlowGeo.disc(pose, b, c, ax, ay, p.size * 1.6f, Colors.withAlpha(col, 0.2f * alpha), 8);
				}
			}
		}
	}

	/** How many light particles are alive (for the tests). */
	public static int count() {
		return LIVE.size();
	}

	public static void clear() {
		LIVE.clear();
	}
}
