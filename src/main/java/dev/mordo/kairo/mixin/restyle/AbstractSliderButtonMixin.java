package dev.mordo.kairo.mixin.restyle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla sliders: rounded track with an accent fill, and a small light handle. */
@Mixin(AbstractSliderButton.class)
abstract class AbstractSliderButtonMixin {
	@Shadow
	protected double value;

	private static final String BLIT = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V";

	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 0))
	private void kairo$track(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!VanillaStyle.enabled()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		VanillaStyle.sliderTrack(g, x, y, w, h, value, self.active, self.isHoveredOrFocused());
	}

	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE", target = BLIT, ordinal = 1))
	private void kairo$handle(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!VanillaStyle.enabled()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		VanillaStyle.sliderHandle(g, x, y, w, h, self.active, self.isHoveredOrFocused());
	}
}
