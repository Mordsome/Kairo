package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.cosmetics.Capes;
import dev.mordo.kairo.cosmetics.Cosmetics;
import dev.mordo.kairo.cosmetics.Emotes;
import dev.mordo.kairo.mods.Skins3D;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Player rendering hooks: 3D skin layers and your cape once the render state is ready, and emotes' whole-body
 * moves (flips, spins, sitting) after the body's rotation.
 */
@Mixin(AvatarRenderer.class)
abstract class AvatarRendererMixin {
	@Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
		at = @At("TAIL"))
	private void kairo$skinLayers(Avatar entity, AvatarRenderState state, float partialTicks, CallbackInfo ci) {
		ModuleManager.get(Skins3D.class).apply(state);
		Cosmetics cosmetics = Cosmetics.get();
		if (cosmetics.isEnabled() && Cosmetics.isLocal(state)) {
			Capes.apply(cosmetics, state);
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void kairo$emoteBody(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		Emotes.applyBody(state, poseStack);
	}
}
