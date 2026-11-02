package dev.mordo.kairo.mixin.restyle;

import dev.mordo.kairo.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla lists (options, packs, controls...): no extra list texture, thin lines instead of the separator art. */
@Mixin(AbstractSelectionList.class)
abstract class SelectionListMixin {
	@Inject(method = "extractListBackground", at = @At("HEAD"), cancellable = true)
	private void kairo$listBackground(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (VanillaStyle.enabled()) {
			ci.cancel(); // the screen's own tint already covers it
		}
	}

	@Inject(method = "extractListSeparators", at = @At("HEAD"), cancellable = true)
	private void kairo$separators(GuiGraphicsExtractor g, CallbackInfo ci) {
		if (VanillaStyle.enabled()) {
			AbstractWidget self = (AbstractWidget) (Object) this;
			VanillaStyle.listSeparators(g, self.getX(), self.getY(), self.getRight(), self.getBottom());
			ci.cancel();
		}
	}
}
