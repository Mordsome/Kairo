package dev.mordo.kairo.mixin.restyle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Vanilla text fields: rounded dark field, accent border while focused. */
@Mixin(EditBox.class)
abstract class EditBoxMixin {
	@WrapOperation(method = "extractWidgetRenderState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void kairo$field(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h, Operation<Void> original) {
		if (!VanillaStyle.enabled()) {
			original.call(g, pipeline, sprite, x, y, w, h);
			return;
		}
		AbstractWidget self = (AbstractWidget) (Object) this;
		VanillaStyle.textField(g, x, y, w, h, self.isFocused(), self.active);
	}
}
