package dev.mordo.kairo.mixin.restyle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Checkbox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla checkboxes: rounded box, accent fill with a check mark when ticked. */
@Mixin(Checkbox.class)
abstract class CheckboxMixin {
	@WrapOperation(method = "extractContents", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIII)V"))
	private void kairo$box(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, int color, Operation<Void> original) {
		if (!VanillaStyle.enabled()) {
			original.call(g, pipeline, sprite, x, y, w, h, color);
			return;
		}
		VanillaStyle.checkbox(g, x, y, Math.min(w, h), ((Checkbox) (Object) this).selected(), ((AbstractWidget) (Object) this).isHoveredOrFocused());
	}
}
