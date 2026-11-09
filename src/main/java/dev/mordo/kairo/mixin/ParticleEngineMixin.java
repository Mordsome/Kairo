package dev.mordo.kairo.mixin;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kairo Void: the energy slash replaces vanilla's grey sweep particle (only the picture; the sweep still hits), and with
 * Light particles some of Minecraft's particles are drawn as light instead.
 */
@Mixin(ParticleEngine.class)
abstract class ParticleEngineMixin {
	@Inject(method = "createParticle", at = @At("HEAD"), cancellable = true)
	private void kairo$noSweep(ParticleOptions options, double x, double y, double z, double xa, double ya, double za,
								 CallbackInfoReturnable<Particle> cir) {
		// Light particles: Minecraft's combat, death, potion and explosion particles drawn as Kairo light instead.
		if (dev.mordo.kairo.render.LightParticles.replaceVanilla(options, x, y, z, xa, ya, za)) {
			cir.setReturnValue(null);
			return;
		}
		if (options.getType() == ParticleTypes.SWEEP_ATTACK && dev.mordo.kairo.render.Visuals.on()
			&& dev.mordo.kairo.core.VoidSettings.INSTANCE.slashes.isOn()) {
			cir.setReturnValue(null);
		}
	}

	@org.spongepowered.asm.mixin.Shadow
	public abstract void createTrackingEmitter(net.minecraft.world.entity.Entity entity, ParticleOptions particle, int lifeTime);

	/** Kairo Void "Crit sparks": more crit and magic sparks on hits. */
	@Inject(method = "createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;)V", at = @At("HEAD"))
	private void kairo$moreCrits(net.minecraft.world.entity.Entity entity, ParticleOptions particle,
								   org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (!dev.mordo.kairo.render.Visuals.on()) {
			return;
		}
		if (particle.getType() != ParticleTypes.CRIT && particle.getType() != ParticleTypes.ENCHANTED_HIT) {
			return;
		}
		int extra = Math.round(dev.mordo.kairo.core.VoidSettings.INSTANCE.particleBoost.floatValue()) - 1;
		for (int i = 0; i < extra; i++) {
			createTrackingEmitter(entity, particle, 3);
		}
	}
}
