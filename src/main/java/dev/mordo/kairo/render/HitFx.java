package dev.mordo.kairo.render;

import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.GameVisuals.HitBurst;
import dev.mordo.kairo.core.GameVisuals.KillEffect;
import dev.mordo.kairo.gui.Theme;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Hit particles and kill effects for what you hit. Only particles and sounds on your own client: a burst when the
 * target's hurt animation starts after your attack, and an effect where it dies within a few seconds of your last hit.
 */
public final class HitFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static int targetId = -1;
	private static int pendingHit;
	private static int sinceHit;
	private static Vec3 lastPos = Vec3.ZERO;
	private static float lastHeight = 1.8f;
	private static float lastHealth = -1;
	private static int lightningIds = -1_000_000;
	private static final List<Effect> RUNNING = new ArrayList<>();

	/** A multi-tick effect. */
	private interface Step {
		void run(ClientLevel level, int tick);
	}

	private record Effect(Step step, int length, int[] age) {
	}

	private HitFx() {
	}

	public static void init() {
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && entity instanceof LivingEntity && player == Minecraft.getInstance().player) {
				VoidFx.onEntityAttack(Minecraft.getInstance().player, entity);
				if (entity.getId() != targetId) {
					lastHealth = ((LivingEntity) entity).getHealth();
				}
				targetId = entity.getId();
				pendingHit = 4;
				sinceHit = 0;
			}
			return InteractionResult.PASS;
		});
	}

	public static void tick(Minecraft mc) {
		ClientLevel level = mc.level;
		if (level == null) {
			targetId = -1;
			RUNNING.clear();
			return;
		}
		for (Iterator<Effect> it = RUNNING.iterator(); it.hasNext(); ) {
			Effect e = it.next();
			e.step().run(level, e.age()[0]);
			if (++e.age()[0] >= e.length()) {
				it.remove();
			}
		}
		if (targetId < 0) {
			return;
		}
		GameVisuals v = GameVisuals.INSTANCE;
		Entity entity = level.getEntity(targetId);
		sinceHit++;
		if (entity instanceof LivingEntity living && !living.isRemoved() && !living.isDeadOrDying()) {
			lastPos = living.position();
			lastHeight = living.getBbHeight();
			float health = living.getHealth();
			if (lastHealth >= 0 && health < lastHealth - 0.01f) {
				WorldFx.damage(living, lastHealth - health);
			}
			lastHealth = health;
			if (pendingHit > 0 && Visuals.live()) {
				pendingHit--;
				if (living.hurtTime >= living.hurtDuration - 3 && living.hurtTime > 0) {
					pendingHit = 0;
					WorldFx.onHit();
					// On the side facing you, so the body doesn't hide the particles.
					Vec3 centre = lastPos.add(0, lastHeight * 0.6, 0);
					Vec3 towards = mc.player != null ? mc.player.getEyePosition().subtract(centre).normalize() : Vec3.ZERO;
					burst(level, v.hitBurst.get(), centre.add(towards.scale(living.getBbWidth() * 0.75 + 0.15)), lastHeight, v.hitAmount.floatValue());
				}
			}
			if (sinceHit > 100) {
				targetId = -1;
			}
			return;
		}
		// Gone or dying within 5 s of your last hit: that's a kill.
		if (entity instanceof LivingEntity dying && lastHealth > 0 && sinceHit <= 100) {
			if (pendingHit > 0) {
				WorldFx.onHit();
			}
			WorldFx.damage(dying, lastHealth);
		}
		boolean killed = entity instanceof LivingEntity && lastHealth > 0 && sinceHit <= 100;
		lastHealth = -1;
		if (sinceHit <= 100 && v.killEffect.get() != KillEffect.NONE && Visuals.live()) {
			kill(level, v.killEffect.get(), lastPos, lastHeight);
		}
		if (killed) {
			packKill(lastPos.add(0, lastHeight * 0.5, 0));
			VoidFx.onKill(lastPos.add(0, lastHeight * 0.5, 0));
		}
		targetId = -1;
	}

	// ---- hit bursts ----

	private static void burst(ClientLevel level, HitBurst style, Vec3 c, float height, float amount) {
		if (style == HitBurst.NONE) {
			return;
		}
		int n = Math.max(3, Math.round(14 * amount));
		float spread = Math.max(0.35f, height * 0.3f);
		for (int i = 0; i < n; i++) {
			double vx = RANDOM.nextGaussian() * 0.25;
			double vy = RANDOM.nextDouble() * 0.3 + 0.05;
			double vz = RANDOM.nextGaussian() * 0.25;
			double x = c.x + RANDOM.nextGaussian() * spread * 0.4;
			double y = c.y + RANDOM.nextGaussian() * spread * 0.4;
			double z = c.z + RANDOM.nextGaussian() * spread * 0.4;
			ParticleOptions p = switch (style) {
				case SPARKS -> i % 3 == 0 ? ParticleTypes.ELECTRIC_SPARK : accentDust(1.6f);
				case CRITS -> i % 3 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT;
				case MAGIC -> i % 2 == 0 ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.WITCH;
				case HEARTS -> ParticleTypes.HEART;
				case STARS -> i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK;
				case FLAMES -> ParticleTypes.FLAME;
				case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
				case BLOOD -> new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
				case NONE -> ParticleTypes.CRIT;
			};
			float speed = switch (style) {
				case HEARTS -> 0.2f;
				case STARS, FLAMES, SOUL -> 0.35f;
				default -> 1f;
			};
			level.addParticle(p, x, y, z, vx * speed, vy * speed, vz * speed);
		}
	}

	private static DustParticleOptions accentDust(float size) {
		return new DustParticleOptions(Theme.accent() & 0xFFFFFF, size);
	}

	// ---- kill effects ----

	private static void kill(ClientLevel level, KillEffect effect, Vec3 at, float height) {
		boolean sound = GameVisuals.INSTANCE.killSound.isOn();
		Vec3 mid = at.add(0, height * 0.5, 0);
		switch (effect) {
			case LIGHTNING -> {
				if (WorldFx.calm()) {
					// Fewer flashes: a column of sparks where the bolt would strike, without the sky flash.
					for (int i = 0; i < 40; i++) {
						level.addParticle(ParticleTypes.ELECTRIC_SPARK, at.x + RANDOM.nextGaussian() * 0.2, at.y + RANDOM.nextDouble() * 3, at.z + RANDOM.nextGaussian() * 0.2,
							0, 0.05, 0);
					}
					return;
				}
				LightningBolt bolt = new LightningBolt(net.minecraft.world.entity.EntityTypes.LIGHTNING_BOLT, level);
				bolt.setVisualOnly(true);
				bolt.setId(lightningIds--); // never clashes with the server's ids
				bolt.snapTo(at.x, at.y, at.z);
				level.addEntity(bolt);
			}
			case FIREWORK -> {
				for (int i = 0; i < 70; i++) {
					Vec3 d = randomDir().scale(0.25 + RANDOM.nextDouble() * 0.1);
					level.addParticle(i % 3 == 0 ? accentDust(1.4f) : ParticleTypes.FIREWORK, mid.x, mid.y + 0.4, mid.z, d.x, d.y, d.z);
				}
				play(level, mid, SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, sound, 0.8f, 1.1f);
			}
			case SOUL -> {
				RUNNING.add(new Effect((lv, t) -> {
					for (int i = 0; i < 4; i++) {
						double a = RANDOM.nextDouble() * Math.PI * 2;
						double r = 0.3 + RANDOM.nextDouble() * 0.3;
						lv.addParticle(i % 2 == 0 ? ParticleTypes.SOUL : ParticleTypes.SCULK_SOUL,
							at.x + Math.cos(a) * r, at.y + t * 0.08, at.z + Math.sin(a) * r, 0, 0.06, 0);
					}
				}, 24, new int[1]));
				play(level, mid, SoundEvents.SOUL_ESCAPE.value(), sound, 1f, 0.9f);
			}
			case EXPLOSION -> {
				level.addParticle(ParticleTypes.EXPLOSION, mid.x, mid.y, mid.z, 0, 0, 0);
				for (int i = 0; i < 30; i++) {
					Vec3 d = randomDir().scale(0.15);
					level.addParticle(i % 2 == 0 ? ParticleTypes.POOF : ParticleTypes.CLOUD, mid.x, mid.y, mid.z, d.x, d.y, d.z);
				}
				play(level, mid, SoundEvents.GENERIC_EXPLODE.value(), sound, 0.35f, 1.4f);
			}
			case TOTEM -> {
				for (int i = 0; i < 60; i++) {
					Vec3 d = randomDir().scale(0.4);
					level.addParticle(ParticleTypes.TOTEM_OF_UNDYING, mid.x, mid.y, mid.z, d.x, d.y + 0.25, d.z);
				}
				play(level, mid, SoundEvents.TOTEM_USE, sound, 0.35f, 1.3f);
			}
			case HEARTS -> {
				RUNNING.add(new Effect((lv, t) -> {
					if (t % 2 == 0) {
						lv.addParticle(ParticleTypes.HEART, at.x + RANDOM.nextGaussian() * 0.4, at.y + height + t * 0.05, at.z + RANDOM.nextGaussian() * 0.4, 0, 0.1, 0);
					}
				}, 20, new int[1]));
				play(level, mid, SoundEvents.AMETHYST_BLOCK_CHIME, sound, 1f, 1.4f);
			}
			case BLOOD -> {
				var blood = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.defaultBlockState());
				for (int i = 0; i < 80; i++) {
					Vec3 d = randomDir().scale(0.3);
					level.addParticle(blood, mid.x, mid.y, mid.z, d.x, Math.abs(d.y) + 0.1, d.z);
				}
				play(level, mid, SoundEvents.HONEY_BLOCK_BREAK, sound, 1f, 0.7f);
			}
			case VORTEX -> {
				RUNNING.add(new Effect((lv, t) -> {
					for (int arm = 0; arm < 3; arm++) {
						double a = t * 0.45 + arm * Math.PI * 2 / 3;
						double r = 1.1 - t * 0.03;
						lv.addParticle(arm == 0 ? accentDust(1.2f) : ParticleTypes.END_ROD,
							at.x + Math.cos(a) * r, at.y + t * 0.09, at.z + Math.sin(a) * r, 0, 0.01, 0);
					}
				}, 34, new int[1]));
				play(level, mid, SoundEvents.ENDER_EYE_DEATH, sound, 1f, 0.8f);
			}
			case SHATTER -> {
				// Breaks apart into little cubes in your colour that tumble to the ground.
				var shard = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GLASS.defaultBlockState());
				for (int i = 0; i < 70; i++) {
					Vec3 d = randomDir().scale(0.18 + RANDOM.nextDouble() * 0.2);
					double x = at.x + RANDOM.nextGaussian() * 0.25;
					double y = at.y + RANDOM.nextDouble() * height;
					double z = at.z + RANDOM.nextGaussian() * 0.25;
					level.addParticle(i % 3 == 0 ? shard : accentDust(1.5f + RANDOM.nextFloat()), x, y, z, d.x, Math.abs(d.y) * 0.6 + 0.05, d.z);
				}
				play(level, mid, SoundEvents.GLASS_BREAK, sound, 0.7f, 1.3f);
			}
			case IMPLODE -> {
				// Everything is pulled into one point, then it bursts out with a ripple.
				RUNNING.add(new Effect((lv, t) -> {
					if (t < 12) {
						double r = 1.8 * (1 - t / 12.0) + 0.1;
						for (int i = 0; i < 10; i++) {
							Vec3 d = randomDir();
							lv.addParticle(i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : accentDust(1.3f), mid.x + d.x * r, mid.y + d.y * r, mid.z + d.z * r,
								-d.x * 0.12, -d.y * 0.12, -d.z * 0.12);
						}
					} else if (t == 12) {
						for (int i = 0; i < 50; i++) {
							Vec3 d = randomDir().scale(0.35);
							lv.addParticle(i % 3 == 0 ? ParticleTypes.END_ROD : accentDust(1.8f), mid.x, mid.y, mid.z, d.x, d.y, d.z);
						}
						VoidFx.crystalPop(mid);
						play(lv, mid, SoundEvents.BEACON_DEACTIVATE, sound, 0.9f, 1.6f);
					}
				}, 14, new int[1]));
				play(level, mid, SoundEvents.ENDER_EYE_DEATH, sound, 0.8f, 0.6f);
			}
			case ASCEND -> {
				// A column of light rises from where it fell.
				RUNNING.add(new Effect((lv, t) -> {
					// A spiral of light climbing round where it stood, with sparks streaming up the middle.
					for (int i = 0; i < 6; i++) {
						double a = t * 0.5 + i * Math.PI / 3;
						double r = 0.55 - t * 0.012;
						double y = at.y + t * 0.12 + i * 0.05;
						lv.addParticle(i % 2 == 0 ? ParticleTypes.END_ROD : accentDust(1.6f), at.x + Math.cos(a) * r, y, at.z + Math.sin(a) * r, 0, 0.08, 0);
					}
					lv.addParticle(ParticleTypes.FIREWORK, at.x + RANDOM.nextGaussian() * 0.1, at.y + 0.2, at.z + RANDOM.nextGaussian() * 0.1, 0, 0.35, 0);
				}, 30, new int[1]));
				play(level, mid, SoundEvents.BEACON_ACTIVATE, sound, 0.7f, 1.4f);
			}
			case NONE -> {
			}
		}
	}

	private static Vec3 randomDir() {
		Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
		return v.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : v.normalize();
	}

	private static void play(ClientLevel level, Vec3 at, SoundEvent sound, boolean on, float volume, float pitch) {
		if (!on) {
			return;
		}
		// A sound pack (Hit Sounds) has its own kill sound: that plays once, instead of the effect's own.
		if (dev.mordo.kairo.mods.HitSounds.packKillKey() != null) {
			return;
		}
		level.playLocalSound(at.x, at.y, at.z, sound, SoundSource.PLAYERS, volume, pitch, false);
	}

	/** The kill sound of the chosen sound pack, if any. */
	private static void packKill(Vec3 at) {
		String key = dev.mordo.kairo.mods.HitSounds.packKillKey();
		if (key != null) {
			Minecraft.getInstance().getSoundManager().play(dev.mordo.kairo.sound.CustomSoundInstance.world(key, at,
				dev.mordo.kairo.mods.HitSounds.packVolume(), 1));
		}
	}
}
