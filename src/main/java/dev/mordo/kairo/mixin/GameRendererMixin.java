package dev.mordo.kairo.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.HurtEffects;
import dev.mordo.kairo.mods.Zoom;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Game renderer hooks: hiding the hand while zoomed, hurt camera tilt strength. (Menu blur: GuiRendererMixin.) */
@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
	@Inject(method = "renderItemInHand", at = @At("HEAD"), cancellable = true)
	private void kairo$hideHandWhileZoomed(CallbackInfo ci) {
		if (ModuleManager.get(Zoom.class).shouldHideHand()) {
			ci.cancel();
		}
	}

	/** Screen effects (filters, glow, motion blur...) on the world, after vanilla's post effects, before the GUI. */
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;applyPostEffects()V", shift = At.Shift.AFTER))
	private void kairo$screenFx(CallbackInfo ci) {
		dev.mordo.kairo.render.ScreenFx.render();
		dev.mordo.kairo.render.VoidFx.render();
	}

	/** Kairo Void: cosmic sky, aurora, sunsets and depth fog, over the finished world and before the hand. */
	@Inject(method = "renderLevel", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/renderer/GameRenderer;render3dHud(Lnet/minecraft/client/renderer/state/level/CameraRenderState;Lnet/minecraft/client/renderer/state/level/PlayerRenderState;Lnet/minecraft/client/renderer/state/OptionsRenderState;Z)V"))
	private void kairo$voidSky(CallbackInfo ci) {
		var level = ((GameRenderer) (Object) this).gameRenderState().levelRenderState;
		dev.mordo.kairo.render.VoidSky.render(level.cameraRenderState, level.skyRenderState);
		dev.mordo.kairo.render.VoidNoir.render(level.cameraRenderState); // the cinematic kill, when one is playing
	}

	/** PvP mode option: no walking sway for the camera or the hand. */
	@Inject(method = "bobView", at = @At("HEAD"), cancellable = true)
	private void kairo$noBobInPvp(CallbackInfo ci) {
		if (dev.mordo.kairo.core.PvpSettings.active(dev.mordo.kairo.core.PvpSettings.INSTANCE.noViewBobbing)) {
			ci.cancel();
		}
	}

	/** Explosion camera shake (the view only; the player's rotation doesn't change). */
	@Inject(method = "bobHurt", at = @At("TAIL"))
	private void kairo$shake(net.minecraft.client.renderer.state.level.CameraRenderState cameraState, com.mojang.blaze3d.vertex.PoseStack poseStack, CallbackInfo ci) {
		dev.mordo.kairo.render.WorldFx.shake(poseStack);
	}

	/** Scales vanilla's damage tilt (which the player's own "Damage Tilt" option already controls). */
	@ModifyExpressionValue(method = "bobHurt", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/renderer/state/OptionsRenderState;damageTiltStrength:D"))
	private double kairo$hurtTilt(double strength) {
		HurtEffects effects = ModuleManager.get(HurtEffects.class);
		return effects.isEnabled() ? strength * effects.hurtTilt.value() : strength;
	}
}
