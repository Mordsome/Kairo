package dev.mordo.kairo.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.HurtEffects;
import dev.mordo.kairo.mods.TotemPops;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.core.particles.ParticleOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fewer particles on our own screen for totem pops and explosions. Only what is drawn changes;
 * the packets themselves are handled exactly as vanilla does.
 */
@Mixin(ClientPacketListener.class)
abstract class ClientPacketListenerMixin {
	@ModifyArg(method = "handleEntityEvent", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/particle/ParticleEngine;createTrackingEmitter(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/particles/ParticleOptions;I)V"),
		index = 2)
	private int kairo$totemParticles(int lifetime) {
		return Math.round(lifetime * ModuleManager.get(TotemPops.class).particleAmount());
	}

	/** Per explosion packet: over the per-second limit means no particles for this one. */
	@Unique
	private boolean kairo$explosionShown = true;

	@Inject(method = "handleExplosion", at = @At("HEAD"))
	private void kairo$explosionLimit(CallbackInfo ci) {
		kairo$explosionShown = ModuleManager.get(HurtEffects.class).allowExplosionParticles();
	}

	/** The big explosion emitter (white puff): hidden, or skipped with a chance matching the reduction. */
	@WrapWithCondition(method = "handleExplosion", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
	private boolean kairo$explosionEmitter(ClientLevel level, ParticleOptions particle, double x, double y, double z, double xa, double ya, double za) {
		HurtEffects effects = ModuleManager.get(HurtEffects.class);
		return kairo$explosionShown && effects.showExplosionFlash()
			&& ThreadLocalRandom.current().nextFloat() < effects.explosionAmount();
	}

	/** Our own explosion look (extra particles, camera shake, flash) on top of vanilla's. */
	@Inject(method = "handleExplosion", at = @At("TAIL"))
	private void kairo$explosionFx(net.minecraft.network.protocol.game.ClientboundExplodePacket packet, CallbackInfo ci) {
		dev.mordo.kairo.render.WorldFx.explosion(packet.center(), packet.radius());
	}

	/** Debris and smoke particles from the blocks the explosion hit. */
	@ModifyArg(method = "handleExplosion", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/ClientLevel;trackExplosionEffects(Lnet/minecraft/world/phys/Vec3;FILnet/minecraft/util/random/WeightedList;)V"),
		index = 2)
	private int kairo$explosionDebris(int blockCount) {
		return kairo$explosionShown ? Math.round(blockCount * ModuleManager.get(HurtEffects.class).explosionAmount()) : 0;
	}
}
