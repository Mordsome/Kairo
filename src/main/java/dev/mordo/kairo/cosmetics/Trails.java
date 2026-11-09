package dev.mordo.kairo.cosmetics;

import dev.mordo.kairo.render.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;

/** Particle trails behind you. Spawned only in your own game, so only you see them. */
final class Trails {
	private static float budget;
	private static double lastX;
	private static double lastZ;
	private static final RandomSource RANDOM = RandomSource.create();

	private Trails() {
	}

	static void tick(Cosmetics c) {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		if (player == null || mc.level == null || c.trail.get().none() || !c.isEnabled() || player.isSpectator() || player.isInvisible()) {
			return;
		}
		double dx = player.getX() - lastX;
		double dz = player.getZ() - lastZ;
		lastX = player.getX();
		lastZ = player.getZ();
		boolean moving = dx * dx + dz * dz > 0.0004;
		if (c.trailWhenMoving.isOn() && !moving) {
			return;
		}
		budget += (moving ? 1.2f : 0.35f) * c.trailDensity.floatValue();
		while (budget >= 1) {
			budget -= 1;
			spawn(mc, player, c.trail.get());
		}
	}

	private static void spawn(Minecraft mc, LocalPlayer player, Cosmetics.TrailStyle style) {
		double x = player.getX() + (RANDOM.nextDouble() - 0.5) * 0.6;
		double z = player.getZ() + (RANDOM.nextDouble() - 0.5) * 0.6;
		double y = player.getY() + 0.1 + RANDOM.nextDouble() * 0.5;
		double up = 0.02 + RANDOM.nextDouble() * 0.03;
		ParticleOptions particle = switch (style) {
			case HEARTS -> ParticleTypes.HEART;
			case SPARKLES -> ParticleTypes.END_ROD;
			case FLAMES -> ParticleTypes.FLAME;
			case SOUL_FLAMES -> ParticleTypes.SOUL_FIRE_FLAME;
			case NOTES -> ParticleTypes.NOTE;
			case SNOW -> ParticleTypes.SNOWFLAKE;
			case TOTEM -> ParticleTypes.TOTEM_OF_UNDYING;
			case ENCHANT -> ParticleTypes.ENCHANT;
			case CLOUDS -> ParticleTypes.CLOUD;
			case CHERRY -> ParticleTypes.CHERRY_LEAVES;
			case ELECTRIC -> ParticleTypes.ELECTRIC_SPARK;
			case RAINBOW -> new DustParticleOptions(Colors.hsv((System.currentTimeMillis() % 3000L) / 3000f, 0.8f, 1f) & 0xFFFFFF, 1.1f);
			case NONE -> null;
		};
		if (particle == null) {
			return;
		}
		if (style == Cosmetics.TrailStyle.NOTES) {
			// Note particles take their colour from the x speed.
			dev.mordo.kairo.render.LightParticles.add(mc.level, particle, x, y + 0.6, z, RANDOM.nextDouble(), 0, 0);
		} else if (style == Cosmetics.TrailStyle.HEARTS || style == Cosmetics.TrailStyle.TOTEM) {
			dev.mordo.kairo.render.LightParticles.add(mc.level, particle, x, y + 0.3, z, 0, up, 0);
		} else {
			dev.mordo.kairo.render.LightParticles.add(mc.level, particle, x, y, z, 0, up * 0.5, 0);
		}
	}
}
