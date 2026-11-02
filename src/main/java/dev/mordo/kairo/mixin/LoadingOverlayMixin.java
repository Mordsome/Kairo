package dev.mordo.kairo.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.gui.LoadingArt;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The Kairo loading screen: our colours, wordmark and progress line; Minecraft keeps the timing and fades. */
@Mixin(LoadingOverlay.class)
abstract class LoadingOverlayMixin {
	@Shadow
	private float currentProgress;

	@Unique
	private boolean kairo$logoDrawn;

	/** Mojang red (or black) becomes deep navy, everywhere the overlay uses it. */
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
	private int kairo$background(int original) {
		return LoadingArt.BACKGROUND;
	}

	@Inject(method = "extractRenderState", at = @At("HEAD"))
	private void kairo$frameStart(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		kairo$logoDrawn = false;
	}

	/** The two halves of the Mojang Studios logo become the Kairo wordmark (drawn once). */
	@WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
	private void kairo$logo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y, float u, float v,
							  int w, int h, int srcW, int srcH, int texW, int texH, int color, Operation<Void> original) {
		if (kairo$logoDrawn) {
			return;
		}
		kairo$logoDrawn = true;
		float alpha = (color >>> 24) / 255f;
		LoadingArt.drawLogo(g, g.guiWidth() / 2, g.guiHeight() / 2, h, alpha);
	}

	@Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
	private void kairo$progress(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float fade, CallbackInfo ci) {
		int inset = (x1 - x0) / 6;
		LoadingArt.drawProgress(g, x0 + inset, x1 - inset, (y0 + y1) / 2, currentProgress, fade);
		ci.cancel();
	}
}
