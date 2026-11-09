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
		dev.mordo.kairo.render.VoidLook.extract(state); // Kairo Void "Your look: Outline" (your own player only)
	}

	/** Kairo Void "Your own nametag in F5": vanilla never shows your own name; this shows it in third person. */
	@Inject(method = "shouldShowName(Lnet/minecraft/world/entity/Avatar;D)Z", at = @At("HEAD"), cancellable = true)
	private void kairo$ownName(Avatar entity, double distance, org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
		net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
		if (entity == mc.player && dev.mordo.kairo.render.Visuals.on() && dev.mordo.kairo.core.VoidSettings.INSTANCE.ownNametag.isOn()
			&& !mc.options.getCameraType().isFirstPerson() && !entity.isInvisible()) {
			cir.setReturnValue(true);
		}
	}

	@Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
		at = @At("TAIL"))
	private void kairo$emoteBody(AvatarRenderState state, PoseStack poseStack, float bodyRot, float entityScale, CallbackInfo ci) {
		Emotes.applyBody(state, poseStack);
	}
}
