package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.render.VoidNametags;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Kairo Void nametags: every name line goes through {@link VoidNametags} with vanilla's own arguments (including
 * whether it shows through walls), so only the look changes.
 */
@Mixin(EntityRenderer.class)
abstract class NametagMixin {
	@Redirect(method = "submitNameDisplay(Lnet/minecraft/client/renderer/entity/state/EntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;"
		+ "Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;I)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitNameTag(Lcom/mojang/blaze3d/vertex/PoseStack;"
			+ "Lnet/minecraft/world/phys/Vec3;ILnet/minecraft/network/chat/Component;ZILnet/minecraft/client/renderer/state/level/CameraRenderState;)V"))
	private void kairo$nametag(SubmitNodeCollector collector, PoseStack poseStack, Vec3 attachment, int offsetY, Component text, boolean seeThrough,
							   int light, CameraRenderState camera, EntityRenderState state, PoseStack outerPose, SubmitNodeCollector outerCollector,
							   CameraRenderState outerCamera, int outerOffset) {
		VoidNametags.submit(collector, poseStack, attachment, offsetY, text, seeThrough, light, camera, state);
	}
}
