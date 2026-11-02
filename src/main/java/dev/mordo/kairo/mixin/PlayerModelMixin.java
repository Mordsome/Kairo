package dev.mordo.kairo.mixin;

import dev.mordo.kairo.cosmetics.Emotes;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Emotes: limb poses on top of the normal animation (your own player only). */
@Mixin(PlayerModel.class)
abstract class PlayerModelMixin {
	@Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
	private void kairo$emotePose(AvatarRenderState state, CallbackInfo ci) {
		Emotes.applyModel((PlayerModel) (Object) this, state);
	}
}
