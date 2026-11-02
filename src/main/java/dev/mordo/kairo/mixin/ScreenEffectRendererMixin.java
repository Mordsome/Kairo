package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.HurtEffects;
import dev.mordo.kairo.mods.TotemPops;
import net.minecraft.client.renderer.ScreenEffectRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Screen overlays: totem pop animation size/length, fire overlay height/opacity. All purely visual. */
@Mixin(ScreenEffectRenderer.class)
abstract class ScreenEffectRendererMixin {
	/** Shorter pop: play the same curve faster (ItemActivationMixin starts the timer shorter to match). */
	@ModifyVariable(method = "renderItemActivationAnimation", at = @At("STORE"), name = "scale")
	private float kairo$popProgress(float progress) {
		float duration = ModuleManager.get(TotemPops.class).duration();
		return duration >= 0.999f ? progress : 1 - (1 - progress) / duration;
	}

	@Inject(method = "renderItemActivationAnimation", at = @At(value = "INVOKE",
		target = "Lcom/mojang/blaze3d/vertex/PoseStack;scale(FFF)V", shift = At.Shift.AFTER))
	private void kairo$popScale(PlayerRenderState state, PoseStack poseStack, float partialTicks, SubmitNodeCollector collector, CallbackInfo ci) {
		float scale = ModuleManager.get(TotemPops.class).scale();
		if (scale < 0.999f) {
			poseStack.scale(scale, scale, scale);
		}
	}

	@Inject(method = "submitFire", at = @At("HEAD"))
	private static void kairo$lowerFire(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.pushPose();
		poseStack.translate(0, -ModuleManager.get(HurtEffects.class).fireDrop(), 0);
	}

	@Inject(method = "submitFire", at = @At("TAIL"))
	private static void kairo$lowerFireEnd(PoseStack poseStack, SubmitNodeCollector collector, TextureAtlasSprite sprite, CallbackInfo ci) {
		poseStack.popPose();
	}

	@ModifyArg(method = "buildFireQuad", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/ScreenEffectRenderer;buildSpriteQuad(Lcom/mojang/blaze3d/vertex/VertexConsumer;Lorg/joml/Matrix4f;Lnet/minecraft/client/renderer/texture/TextureAtlasSprite;FFFFFI)V"),
		index = 8)
	private static int kairo$fireOpacity(int color) {
		float alpha = ModuleManager.get(HurtEffects.class).fireAlpha();
		if (alpha >= 0.999f) {
			return color;
		}
		int a = Math.round((color >>> 24) * alpha);
		return (a << 24) | (color & 0xFFFFFF);
	}
}
