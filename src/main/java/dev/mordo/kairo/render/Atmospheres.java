package dev.mordo.kairo.render;

import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.Atmosphere;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Kairo Void atmospheres: how each one recolours the sky, fog and light (read by {@link VoidSky}), plus the
 * client-side weather that goes with it: snow, ash, petals, spores, the storm's rain and lightning, and fireflies.
 * Everything is only on your screen; the server's weather and time are untouched.
 */
public final class Atmospheres {
	/** Sky, fog and grade for one atmosphere (colours 0..1). */
	public record Look(float[] skyLow, float[] skyHigh, float skyAmount, float cosmic, float[] fog, float fogDensity,
					   float[] grade, float saturation, float snow, float caustics, int feature) {
	}

	public static final int FEATURE_NONE = 0;
	public static final int FEATURE_BLOOD_MOON = 1;
	public static final int FEATURE_SYNTH_SUN = 2;

	private static final RandomSource RANDOM = RandomSource.create();
	private static int lightningIds = -2_000_000;
	private static float flash;
	private static int nextStrike = 80;
	private static boolean stormRain;
	private static float savedRain;
	private static float savedThunder;

	private Atmospheres() {
	}

	private static float[] c(float r, float g, float b) {
		return new float[]{r, g, b};
	}

	public static Atmosphere current() {
		return Visuals.on() ? VoidSettings.INSTANCE.atmosphere.get() : Atmosphere.NONE;
	}

	public static float strength() {
		return VoidSettings.INSTANCE.atmosphereStrength.floatValue();
	}

	public static Look look(Atmosphere a) {
		return switch (a) {
			case BLIZZARD -> new Look(c(0.86f, 0.9f, 0.95f), c(0.68f, 0.74f, 0.83f), 0.92f, 0, c(0.86f, 0.9f, 0.95f), 0.032f,
				c(0.93f, 0.98f, 1.07f), 0.62f, 1, 0, FEATURE_NONE);
			case GALAXY -> new Look(c(0.16f, 0.07f, 0.32f), c(0.02f, 0.01f, 0.07f), 0.96f, 1, c(0.22f, 0.1f, 0.38f), 0.007f,
				c(0.82f, 0.76f, 1.12f), 1.2f, 0, 0, FEATURE_NONE);
			case BLOOD_MOON -> new Look(c(0.48f, 0.05f, 0.07f), c(0.1f, 0.01f, 0.03f), 0.92f, 0.35f, c(0.36f, 0.04f, 0.05f), 0.012f,
				c(1.15f, 0.68f, 0.68f), 0.9f, 0, 0, FEATURE_BLOOD_MOON);
			case INFERNO -> new Look(c(1.0f, 0.46f, 0.12f), c(0.32f, 0.07f, 0.04f), 0.92f, 0, c(0.78f, 0.32f, 0.1f), 0.011f,
				c(1.2f, 0.9f, 0.7f), 1.1f, 0, 0, FEATURE_NONE);
			case SAKURA -> new Look(c(1.0f, 0.78f, 0.87f), c(0.74f, 0.62f, 0.95f), 0.78f, 0, c(1.0f, 0.82f, 0.9f), 0.008f,
				c(1.08f, 0.98f, 1.05f), 0.95f, 0, 0, FEATURE_NONE);
			case TOXIC -> new Look(c(0.56f, 0.76f, 0.2f), c(0.14f, 0.24f, 0.07f), 0.88f, 0, c(0.42f, 0.62f, 0.15f), 0.016f,
				c(0.86f, 1.12f, 0.74f), 1.0f, 0, 0, FEATURE_NONE);
			case ABYSS -> new Look(c(0.04f, 0.22f, 0.38f), c(0.0f, 0.04f, 0.11f), 0.96f, 0, c(0.03f, 0.19f, 0.31f), 0.02f,
				c(0.6f, 0.9f, 1.25f), 0.9f, 0, 1, FEATURE_NONE);
			case SYNTHWAVE -> new Look(c(1.0f, 0.34f, 0.6f), c(0.13f, 0.04f, 0.33f), 0.93f, 0.4f, c(0.6f, 0.15f, 0.55f), 0.008f,
				c(1.08f, 0.86f, 1.16f), 1.3f, 0, 0, FEATURE_SYNTH_SUN);
			case STORM -> new Look(c(0.34f, 0.36f, 0.41f), c(0.14f, 0.15f, 0.19f), 0.86f, 0, c(0.3f, 0.32f, 0.36f), 0.012f,
				c(0.82f, 0.86f, 0.95f), 0.7f, 0, 0, FEATURE_NONE);
			case NONE -> new Look(c(0, 0, 0), c(0, 0, 0), 0, 0, c(0, 0, 0), 0, c(1, 1, 1), 1, 0, 0, FEATURE_NONE);
		};
	}

	/** 0..1, a lightning flash lighting up the storm. */
	public static float flash() {
		return dev.mordo.kairo.core.ClientSettings.INSTANCE.reduceFlashing.isOn() ? flash * 0.15f : flash;
	}

	// ---- per tick ----

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		LocalPlayer player = mc.player;
		Atmosphere a = current();
		boolean storm = a == Atmosphere.STORM && level != null;
		if (storm != stormRain) {
			stormRain = storm;
			if (level != null) {
				if (storm) {
					savedRain = level.getRainLevel(1);
					savedThunder = level.getThunderLevel(1);
				} else {
					level.setRainLevel(savedRain);
					level.setThunderLevel(savedThunder);
				}
			}
		}
		flash *= 0.82f;
		if (level == null || player == null) {
			return;
		}
		VoidSettings s = VoidSettings.INSTANCE;
		float k = strength();
		if (storm) {
			level.setRainLevel(Math.max(savedRain, k));
			level.setThunderLevel(Math.max(savedThunder, k));
			if (--nextStrike <= 0) {
				nextStrike = 60 + RANDOM.nextInt(160);
				strike(level, player);
			}
		}
		if (s.fireflies.isOn() && Visuals.on() && isNight(level)) {
			fireflies(level, player);
		}
		if (a == Atmosphere.NONE || !s.atmosphereParticles.isOn()) {
			return;
		}
		switch (a) {
			case BLIZZARD -> spawn(level, player, ParticleTypes.SNOWFLAKE, Math.round(70 * k), 18, 0.32, -0.18, 0.14);
			case GALAXY -> {
				spawn(level, player, ParticleTypes.REVERSE_PORTAL, Math.round(4 * k), 14, 0, 0.01, 0);
				spawn(level, player, ParticleTypes.END_ROD, Math.round(2 * k), 16, 0, 0.004, 0);
			}
			case BLOOD_MOON -> spawn(level, player, ParticleTypes.CRIMSON_SPORE, Math.round(14 * k), 16, 0, 0, 0);
			case INFERNO -> {
				spawn(level, player, ParticleTypes.WHITE_ASH, Math.round(18 * k), 18, 0.02, -0.02, 0);
				spawn(level, player, ParticleTypes.ASH, Math.round(14 * k), 18, 0, 0, 0);
				spawnLow(level, player, ParticleTypes.SMALL_FLAME, Math.round(3 * k), 10, 0.06);
			}
			case SAKURA -> spawn(level, player, ParticleTypes.CHERRY_LEAVES, Math.round(10 * k), 16, 0, 0, 0);
			case TOXIC -> {
				spawn(level, player, new DustParticleOptions(0x8CFF3A, 1.2f), Math.round(10 * k), 14, 0, 0.01, 0);
				spawn(level, player, ParticleTypes.SPORE_BLOSSOM_AIR, Math.round(6 * k), 14, 0, 0, 0);
			}
			case ABYSS -> spawn(level, player, ParticleTypes.UNDERWATER, Math.round(40 * k), 14, 0, 0, 0);
			case SYNTHWAVE -> {
				spawn(level, player, new DustParticleOptions(0xFF3DC8, 1.0f), Math.round(6 * k), 16, 0, 0.02, 0);
				spawn(level, player, ParticleTypes.END_ROD, Math.round(2 * k), 16, 0, 0.004, 0);
			}
			case STORM, NONE -> {
			}
		}
	}

	private static boolean isNight(ClientLevel level) {
		long t = level.getDefaultClockTime() % 24000;
		return t > 12800 && t < 23200;
	}

	/** Particles in the open air around you (never under a roof). */
	private static void spawn(ClientLevel level, LocalPlayer player, ParticleOptions type, int count, double radius,
							  double vx, double vy, double vz) {
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * radius;
			double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * radius;
			double y = player.getY() + RANDOM.nextDouble() * 14 - 3;
			BlockPos pos = BlockPos.containing(x, y, z);
			if (level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.getX(), pos.getZ()) > y) {
				continue;
			}
			level.addParticle(type, x, y, z, vx, vy, vz);
		}
	}

	/** Particles near the ground, rising (embers). */
	private static void spawnLow(ClientLevel level, LocalPlayer player, ParticleOptions type, int count, double radius, double up) {
		for (int i = 0; i < count; i++) {
			double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * radius;
			double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * radius;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
			if (Math.abs(top - player.getY()) > 8) {
				continue;
			}
			level.addParticle(type, x, top + 0.1, z, 0, up, 0);
		}
	}

	private static void fireflies(ClientLevel level, LocalPlayer player) {
		for (int i = 0; i < 3; i++) {
			double x = player.getX() + (RANDOM.nextDouble() * 2 - 1) * 14;
			double z = player.getZ() + (RANDOM.nextDouble() * 2 - 1) * 14;
			int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) Math.floor(x), (int) Math.floor(z));
			if (Math.abs(top - player.getY()) > 6) {
				continue;
			}
			BlockState ground = level.getBlockState(BlockPos.containing(x, top - 1, z));
			BlockState above = level.getBlockState(BlockPos.containing(x, top, z));
			boolean plants = !above.isAir() || ground.getBlock().getDescriptionId().contains("grass");
			if (plants && RANDOM.nextFloat() < 0.6f) {
				level.addParticle(ParticleTypes.FIREFLY, x, top + 0.4 + RANDOM.nextDouble() * 1.4, z, 0, 0, 0);
			}
		}
	}

	/** A lightning bolt somewhere out in the storm: visual only (never hits anything), with its thunder. */
	private static void strike(ClientLevel level, LocalPlayer player) {
		double angle = RANDOM.nextDouble() * Math.PI * 2;
		double distance = 30 + RANDOM.nextDouble() * 60;
		double x = player.getX() + Math.cos(angle) * distance;
		double z = player.getZ() + Math.sin(angle) * distance;
		int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(x), (int) Math.floor(z));
		if (dev.mordo.kairo.core.ClientSettings.INSTANCE.reduceFlashing.isOn()) {
			// Fewer flashes: only the distant thunder, no bolt lighting up the sky.
			level.playLocalSound(x, y, z, net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, net.minecraft.sounds.SoundSource.WEATHER, 2f, 0.8f, false);
			return;
		}
		LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT, level);
		bolt.setVisualOnly(true);
		bolt.setId(lightningIds--);
		bolt.snapTo(x, y, z);
		level.addEntity(bolt);
		flash = 1;
	}
}
