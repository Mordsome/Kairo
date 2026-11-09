package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.GameVisuals.ExplosionStyle;
import dev.mordo.kairo.core.GameVisuals.JumpEffect;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Movement and world effects on your own screen: jump and landing particles, explosion particles, camera shake and
 * flash, the hit flash, speed lines and floating damage numbers. None of it changes the game, only what's drawn.
 */
public final class WorldFx {
	private static final RandomSource RANDOM = RandomSource.create();
	private static boolean wasOnGround = true;
	private static double peakY;
	private static long flashStart;
	private static float flashAmount;
	private static long shakeStart;
	private static float shakeAmount;
	private static long hitStart;
	private static float speedLines;

	private record DamageNumber(Entity entity, Vec3 offset, String text, int color, long start, boolean big) {
	}

	private static final List<DamageNumber> NUMBERS = new ArrayList<>();

	private WorldFx() {
	}

	public static void init() {
		HudElementRegistry.addLast(ClientInfo.id("damage_numbers"), (g, delta) -> drawNumbers(g));
	}

	// ---- per tick ----

	public static void tick(Minecraft mc) {
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null) {
			NUMBERS.clear();
			return;
		}
		GameVisuals v = GameVisuals.INSTANCE;
		boolean onGround = player.onGround();
		if (!onGround) {
			peakY = wasOnGround ? player.getY() : Math.max(peakY, player.getY());
		}
		if (Visuals.live() && v.jumpEffect.get() != JumpEffect.NONE && !player.isSpectator() && !player.getAbilities().flying) {
			if (wasOnGround && !onGround && player.getDeltaMovement().y > 0.2) {
				jump(level, player.position(), v.jumpEffect.get(), 1);
			} else if (!wasOnGround && onGround && v.landingEffect.isOn()) {
				double drop = peakY - player.getY();
				if (drop > 1.3) {
					jump(level, player.position(), v.jumpEffect.get(), (float) Math.min(3, 1 + drop / 3));
				}
			}
		}
		wasOnGround = onGround;

		// Speed lines: from about sprint speed upwards (elytra, falling, riding).
		// Real distance moved this tick (the stored velocity already has friction taken off).
		double dx = player.getX() - player.xo;
		double dz = player.getZ() - player.zo;
		double fall = Math.max(0, (player.yo - player.getY()) - 0.6);
		double speed = Math.sqrt(dx * dx + dz * dz + fall * fall);
		float target = Visuals.live() ? (float) Math.clamp((speed - 0.26) / 0.5, 0, 1) * v.speedLines.floatValue() : 0;
		speedLines += (target - speedLines) * 0.25f;
	}

	// ---- jumps and landings ----

	private static void jump(ClientLevel level, Vec3 feet, JumpEffect effect, float scale) {
		int n = Math.round(12 * scale);
		for (int i = 0; i < n; i++) {
			double a = i * Math.PI * 2 / n + RANDOM.nextDouble() * 0.3;
			double cos = Math.cos(a);
			double sin = Math.sin(a);
			double r = 0.3;
			double x = feet.x + cos * r;
			double y = feet.y + 0.05;
			double z = feet.z + sin * r;
			double out = 0.08 * scale;
			switch (effect) {
				case DUST -> dev.mordo.kairo.render.LightParticles.add(level, ParticleTypes.POOF, x, y, z, cos * out * 0.6, 0.02, sin * out * 0.6);
				case RING -> dev.mordo.kairo.render.LightParticles.add(level, accentDust(1.2f), x, y + 0.1, z, cos * out * 3, 0, sin * out * 3);
				case SPARKLES -> dev.mordo.kairo.render.LightParticles.add(level, i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.FIREWORK, x, y, z, cos * out * 0.3, 0.08, sin * out * 0.3);
				case FLAMES -> dev.mordo.kairo.render.LightParticles.add(level, ParticleTypes.FLAME, x, y, z, cos * out * 0.5, 0.03, sin * out * 0.5);
				case HEARTS -> {
					if (i % 3 == 0) {
						dev.mordo.kairo.render.LightParticles.add(level, ParticleTypes.HEART, x, y + 0.3, z, 0, 0.05, 0);
					}
				}
				case CLOUD -> dev.mordo.kairo.render.LightParticles.add(level, ParticleTypes.CLOUD, x, y, z, cos * out * 0.5, 0.01, sin * out * 0.5);
				case SOUL -> dev.mordo.kairo.render.LightParticles.add(level, i % 2 == 0 ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL, x, y, z, cos * out * 0.4, 0.04, sin * out * 0.4);
				case NONE -> {
				}
			}
		}
	}

	private static DustParticleOptions accentDust(float size) {
		return new DustParticleOptions(Theme.accent() & 0xFFFFFF, size);
	}

	// ---- explosions ----

	/** Called when the client is told about an explosion. */
	public static void explosion(Vec3 center, float radius) {
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null || !Visuals.live()) {
			return;
		}
		CrystalFx.explosion(center);
		GameVisuals v = GameVisuals.INSTANCE;
		double distance = mc.player.position().distanceTo(center);
		float near = (float) Math.clamp(1 - distance / (radius * 7 + 10), 0, 1);
		if (v.explosionShake.floatValue() > 0 && near > 0) {
			shakeAmount = Math.max(currentShake(), v.explosionShake.floatValue() * near);
			shakeStart = System.nanoTime();
		}
		if (v.explosionFlash.floatValue() > 0 && near > 0) {
			flashAmount = Math.max(currentFlash(), v.explosionFlash.floatValue() * near);
			flashStart = System.nanoTime();
		}
		ExplosionStyle style = v.explosionStyle.get();
		if (style == ExplosionStyle.VANILLA || dev.mordo.kairo.core.PvpMode.isActiveForReal()) {
			return;
		}
		float r = Math.max(1, radius);
		int n = Math.round(50 * Math.min(2, r / 3));
		for (int i = 0; i < n; i++) {
			Vec3 d = randomDir();
			double speed = 0.25 + RANDOM.nextDouble() * 0.35 * r / 3;
			ParticleOptions p;
			Vec3 vel = d.scale(speed);
			switch (style) {
				case SHOCKWAVE -> {
					// A flat ring racing outwards, plus a little smoke.
					double a = i * Math.PI * 2 / n;
					vel = new Vec3(Math.cos(a), 0.02, Math.sin(a)).scale(0.55 * r / 3 + 0.3);
					p = i % 3 == 0 ? accentDust(2f) : i % 3 == 1 ? ParticleTypes.CLOUD : ParticleTypes.POOF;
				}
				case INFERNO -> p = i % 4 == 0 ? ParticleTypes.LAVA : i % 4 == 1 ? ParticleTypes.LARGE_SMOKE : ParticleTypes.FLAME;
				case FIREWORK -> p = i % 3 == 0 ? accentDust(1.6f) : ParticleTypes.FIREWORK;
				case MAGIC -> p = i % 3 == 0 ? ParticleTypes.WITCH : i % 3 == 1 ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD;
				case VOID -> {
					p = i % 2 == 0 ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.SQUID_INK;
					vel = vel.scale(0.6);
				}
				default -> p = ParticleTypes.POOF;
			}
			dev.mordo.kairo.render.LightParticles.add(level, p, center.x, center.y + 0.5, center.z, vel.x, vel.y, vel.z);
		}
	}

	private static Vec3 randomDir() {
		Vec3 v = new Vec3(RANDOM.nextGaussian(), RANDOM.nextGaussian(), RANDOM.nextGaussian());
		return v.lengthSqr() < 1e-6 ? new Vec3(0, 1, 0) : v.normalize();
	}

	private static float decay(long start, float amount, float seconds) {
		if (amount <= 0) {
			return 0;
		}
		float t = (System.nanoTime() - start) / 1e9f;
		return t > seconds * 3 ? 0 : amount * (float) Math.exp(-t / seconds);
	}

	private static float currentShake() {
		return decay(shakeStart, shakeAmount, 0.22f);
	}

	private static float currentFlash() {
		return decay(flashStart, flashAmount, 0.12f);
	}

	/** Camera shake: rotates the view pose only (your aim doesn't move). */
	public static void shake(PoseStack poseStack) {
		float s = currentShake();
		if (s <= 0.001f) {
			return;
		}
		double t = System.nanoTime() / 1e9;
		poseStack.rotate(Axis.XP, (float) Math.toRadians(Math.sin(t * 47) * 2.2 * s));
		poseStack.rotate(Axis.YP, (float) Math.toRadians(Math.sin(t * 39 + 1.7) * 2.2 * s));
		poseStack.rotate(Axis.ZP, (float) Math.toRadians(Math.sin(t * 31 + 3.1) * 1.5 * s));
	}

	// ---- hits ----

	public static void onHit() {
		if (GameVisuals.INSTANCE.hitFlash.isOn() && Visuals.live()) {
			hitStart = System.nanoTime();
		}
	}

	public static void damage(Entity entity, float amount) {
		if (!GameVisuals.INSTANCE.damageNumbers.isOn() || amount < 0.05f || !Visuals.live()) {
			return;
		}
		if (KineticNumbers.active()) {
			KineticNumbers.add(entity, amount, VoidFx.lastWasCrit());
			return;
		}
		String text = amount == Math.floor(amount) ? String.valueOf((int) amount) : String.format(Locale.ROOT, "%.1f", amount);
		boolean big = amount >= 6;
		int color = big ? 0xFFFFB347 : 0xFFFFFFFF;
		Vec3 offset = new Vec3(RANDOM.nextGaussian() * 0.25, entity.getBbHeight() + 0.2, RANDOM.nextGaussian() * 0.25);
		NUMBERS.add(new DamageNumber(entity, offset, "-" + text, color, System.nanoTime(), big));
		if (NUMBERS.size() > 24) {
			NUMBERS.remove(0);
		}
	}

	// ---- values for the screen effects pass ----

	public static float speedLines() {
		return speedLines;
	}

	public static float flash() {
		// Fewer flashes: the explosion flash is a faint glow instead.
		return currentFlash() * (calm() ? 0.15f : 1);
	}

	public static float hitFlash() {
		return GameVisuals.INSTANCE.hitFlash.isOn() ? decay(hitStart, hitStart == 0 ? 0 : 1, 0.2f) * (calm() ? 0.3f : 1) : 0;
	}

	/** Settings → Fewer flashes (also offered in the setup's safety step). */
	public static boolean calm() {
		return dev.mordo.kairo.core.ClientSettings.INSTANCE.reduceFlashing.isOn();
	}

	// ---- damage numbers ----

	private static void drawNumbers(GuiGraphicsExtractor g) {
		KineticNumbers.draw(g);
		if (NUMBERS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		var camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Vector3fc forward = camera.forwardVector();
		long now = System.nanoTime();
		for (Iterator<DamageNumber> it = NUMBERS.iterator(); it.hasNext(); ) {
			DamageNumber n = it.next();
			float age = (now - n.start()) / 1e9f;
			if (age > 1.2f) {
				it.remove();
				continue;
			}
			Vec3 pos = n.entity().position().add(n.offset()).add(0, age * 0.7, 0);
			double depth = (pos.x - eye.x) * forward.x() + (pos.y - eye.y) * forward.y() + (pos.z - eye.z) * forward.z();
			if (depth < 0.3) {
				continue;
			}
			Vec3 ndc = mc.gameRenderer.projectPointToScreen(pos);
			if (Math.abs(ndc.x) > 1.1 || Math.abs(ndc.y) > 1.1) {
				continue;
			}
			float sx = (float) ((ndc.x * 0.5 + 0.5) * g.guiWidth());
			float sy = (float) ((1 - (ndc.y * 0.5 + 0.5)) * g.guiHeight());
			float pop = age < 0.12f ? 0.6f + age / 0.12f * 0.6f : Math.max(1, 1.2f - (age - 0.12f) * 2);
			float alpha = age > 0.8f ? 1 - (age - 0.8f) / 0.4f : 1;
			float size = (float) Math.clamp(26 / depth, 7, 18) * pop * (n.big() ? 1.25f : 1);
			Fonts.draw(g, Typeface.OUTFIT_BOLD, n.text(), sx, sy, size, Colors.withAlpha(n.color(), alpha), Fonts.Align.CENTER, 0, true);
		}
	}
}
