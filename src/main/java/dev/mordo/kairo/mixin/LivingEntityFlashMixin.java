package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.FlashState;
import dev.mordo.kairo.render.VoidFx;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Kairo Void: what you hit flashes white for a moment (the white overlay creepers use when they're about to blow). */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityFlashMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
		at = @At("TAIL"))
	private void kairo$flash(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		float flash = VoidFx.targetFlash(entity);
		((FlashState) state).kairo$setFlash(flash);
		if (flash > 0.05f) {
			// The red hurt tint would hide the white; it comes back as the flash fades.
			state.hasRedOverlay = false;
		}
	}

	@Inject(method = "getOverlayCoords", at = @At("HEAD"), cancellable = true)
	private static void kairo$whiteOverlay(LivingEntityRenderState state, float whiteOverlayProgress, CallbackInfoReturnable<Integer> cir) {
		float flash = ((FlashState) state).kairo$flash();
		if (flash > 0) {
			cir.setReturnValue(OverlayTexture.pack(OverlayTexture.u(Math.max(whiteOverlayProgress, flash)), OverlayTexture.v(state.hasRedOverlay)));
		}
	}
}
