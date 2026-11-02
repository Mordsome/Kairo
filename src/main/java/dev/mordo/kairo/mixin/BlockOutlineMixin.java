package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.BlockOutline;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.BlockOutlineRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Block Outline mod: replaces the thin black outline (the high-contrast accessibility outline stays vanilla). */
@Mixin(LevelRenderer.class)
abstract class BlockOutlineMixin {
	@Shadow
	@Final
	private GameRenderer gameRenderer;

	@Inject(method = "submitBlockOutline", at = @At("HEAD"), cancellable = true)
	private void kairo$blockOutline(PoseStack poseStack, SubmitNodeCollector collector, LevelRenderState levelState, CallbackInfo ci) {
		BlockOutline mod = ModuleManager.get(BlockOutline.class);
		BlockOutlineRenderState state = levelState.blockOutlineRenderState;
		if (!mod.isEnabled() || state == null || state.highContrast()) {
			return;
		}
		ci.cancel();
		Vec3 camera = levelState.cameraRenderState.pos;
		BlockPos pos = state.pos();
		poseStack.pushPose();
		poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		if (mod.fill.isOn()) {
			mod.submitFill(poseStack, collector, state.shape());
		}
		RenderType type = gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
		float width = gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * mod.width.floatValue();
		collector.submitShapeOutline(poseStack, state.shape(), type, mod.outlineColor(), width, state.isTranslucent());
		poseStack.popPose();
	}
}
