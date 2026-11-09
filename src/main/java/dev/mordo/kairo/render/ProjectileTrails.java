package dev.mordo.kairo.render;

import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.GameVisuals.ProjectileTrail;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrownEnderpearl;
import net.minecraft.world.phys.Vec3;

/**
 * Trails behind flying arrows, tridents, ender pearls, snowballs, eggs and potions, and a ring where an ender pearl
 * lands. Particles on your own screen only; the projectiles themselves aren't touched.
 */
public final class ProjectileTrails {
	private static final RandomSource RANDOM = RandomSource.create();
	private static final double RANGE = 96;
	private static int tick;

	private ProjectileTrails() {
	}

	private static boolean tracked(Entity e) {
		return e instanceof AbstractArrow || e instanceof ThrowableItemProjectile;
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		GameVisuals v = GameVisuals.INSTANCE;
		if (level == null || player == null || !Visuals.live() || v.projectileTrail.get() == ProjectileTrail.NONE) {
			return;
		}
		tick++;
		ProjectileTrail style = v.projectileTrail.get();
		for (Entity e : level.entitiesForRendering()) {
			if (!tracked(e) || e.distanceToSqr(player) > RANGE * RANGE) {
				continue;
			}
			if (v.trailOnlyMine.isOn() && (!(e instanceof net.minecraft.world.entity.projectile.Projectile p) || p.getOwner() != player)) {
				continue;
			}
			Vec3 from = new Vec3(e.xo, e.yo, e.zo);
			Vec3 to = e.position();
			double moved = from.distanceToSqr(to);
			if (moved < 0.0025) {
				continue; // stuck in a block, or resting
			}
			int steps = (int) Math.clamp(Math.sqrt(moved) * 3, 2, 8);
			for (int i = 0; i < steps; i++) {
				Vec3 at = from.lerp(to, i / (double) steps).add(0, e.getBbHeight() * 0.5, 0);
				dev.mordo.kairo.render.LightParticles.add(level, particle(style, e, i), at.x, at.y, at.z, RANDOM.nextGaussian() * 0.004, RANDOM.nextGaussian() * 0.004, RANDOM.nextGaussian() * 0.004);
			}
		}
	}

	private static ParticleOptions particle(ProjectileTrail style, Entity e, int i) {
		return switch (style) {
			case ACCENT -> i % 4 == 0 ? ParticleTypes.END_ROD : new DustParticleOptions(Theme.accent() & 0xFFFFFF, 1.1f);
			case RAINBOW -> {
				float hue = ((tick * 3 + i * 9 + e.getId() * 37) % 360) / 360f;
				yield new DustParticleOptions(net.minecraft.util.Mth.hsvToRgb(hue, 0.8f, 1f) & 0xFFFFFF, 1.1f);
			}
			case FLAME -> i % 3 == 0 ? ParticleTypes.SMALL_FLAME : ParticleTypes.FLAME;
			case MAGIC -> i % 2 == 0 ? ParticleTypes.WITCH : ParticleTypes.ENCHANT;
			case CLOUD -> i % 2 == 0 ? ParticleTypes.CLOUD : ParticleTypes.WHITE_ASH;
			case SPARKLE -> i % 2 == 0 ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD;
			case NONE -> ParticleTypes.END_ROD;
		};
	}

	/** An entity left the client world: a ring where an ender pearl landed. */
	public static void removed(Entity e) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (!(e instanceof ThrownEnderpearl) || level == null || mc.player == null || !Visuals.live() || !GameVisuals.INSTANCE.pearlRipple.isOn()
			|| e.distanceToSqr(mc.player) > RANGE * RANGE) {
			return;
		}
		Vec3 at = e.position();
		DustParticleOptions dust = new DustParticleOptions(Theme.accent() & 0xFFFFFF, 1.4f);
		for (int i = 0; i < 28; i++) {
			double a = i * Math.PI * 2 / 28;
			double cos = Math.cos(a);
			double sin = Math.sin(a);
			dev.mordo.kairo.render.LightParticles.add(level, i % 4 == 0 ? ParticleTypes.REVERSE_PORTAL : dust, at.x + cos * 0.3, at.y + 0.1, at.z + sin * 0.3, cos * 0.22, 0.01, sin * 0.22);
		}
		VoidFx.crystalPop(at.add(0, 0.5, 0));
	}
}
