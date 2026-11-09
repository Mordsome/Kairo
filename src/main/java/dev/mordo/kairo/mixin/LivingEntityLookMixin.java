package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.VoidLook;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Kairo Void "Your look: Hologram": your own player's body drawn with the hologram shader, in your colour. */
@Mixin(LivingEntityRenderer.class)
abstract class LivingEntityLookMixin {
	@Shadow
	public abstract Identifier getTextureLocation(LivingEntityRenderState state);

	@Inject(method = "getRenderType", at = @At("HEAD"), cancellable = true)
	private void kairo$hologram(LivingEntityRenderState state, boolean visible, boolean translucent, boolean glowing,
								CallbackInfoReturnable<RenderType> cir) {
		if (visible && state instanceof AvatarRenderState avatar && VoidLook.hologram(avatar)) {
			cir.setReturnValue(VoidLook.hologramType(getTextureLocation(state)));
		}
	}

	@Inject(method = "getModelTint", at = @At("HEAD"), cancellable = true)
	private void kairo$hologramTint(LivingEntityRenderState state, CallbackInfoReturnable<Integer> cir) {
		if (state instanceof AvatarRenderState avatar && VoidLook.hologram(avatar)) {
			cir.setReturnValue(VoidLook.hologramTint());
		}
	}
}
