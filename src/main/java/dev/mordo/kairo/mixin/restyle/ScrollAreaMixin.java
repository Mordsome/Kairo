package dev.mordo.kairo.mixin.restyle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractScrollArea;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla scrollbars: a faint rounded track and a soft thumb instead of the black and grey sprites. */
@Mixin(AbstractScrollArea.class)
abstract class ScrollAreaMixin {
	@WrapOperation(method = "extractScrollbar", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void kairo$scrollbar(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!VanillaStyle.enabled()) {
			original.call(g, pipeline, sprite, x, y, w, h);
			return;
		}
		// The track spans the whole area; anything shorter is the thumb.
		boolean track = h >= ((AbstractWidget) (Object) this).getHeight();
		VanillaStyle.scrollbar(g, x, y, w, h, track);
	}
}
