package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.render.CrystalFx;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kairo Void crystal styles: the crystal recoloured and lit, plus the aura and rings drawn around it. */
@Mixin(EndCrystalRenderer.class)
abstract class EndCrystalRendererMixin {
	private static final String SUBMIT = "submit(Lnet/minecraft/client/renderer/entity/state/EndCrystalRenderState;"
		+ "Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;"
		+ "Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V";

	@Inject(method = SUBMIT, at = @At("HEAD"))
	private void kairo$aura(EndCrystalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera, CallbackInfo ci) {
		CrystalFx.submit(state, poseStack, collector, camera);
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	@Redirect(method = SUBMIT, at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/SubmitNodeCollector;submitModel(Lnet/minecraft/client/model/Model;Ljava/lang/Object;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/resources/Identifier;III)V"))
	private void kairo$tint(SubmitNodeCollector collector, Model model, Object state, PoseStack poseStack, Identifier texture, int light, int overlay, int outline) {
		int tint = CrystalFx.tint(((EndCrystalRenderState) state).ageInTicks);
		if (tint == -1) {
			collector.submitModel(model, state, poseStack, texture, light, overlay, outline);
		} else {
			collector.submitModel(model, state, poseStack, RenderTypes.entityTranslucentEmissive(texture), 0xF000F0, overlay, tint, null, outline);
		}
	}
}
