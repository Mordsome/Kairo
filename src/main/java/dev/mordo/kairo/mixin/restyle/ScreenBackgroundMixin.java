package dev.mordo.kairo.mixin.restyle;

import dev.mordo.kairo.gui.VanillaStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Vanilla menus: a smooth dark tint over the blurred panorama / world instead of the patterned menu texture. */
@Mixin(Screen.class)
abstract class ScreenBackgroundMixin {
	@Inject(method = "extractMenuBackground(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIII)V", at = @At("HEAD"), cancellable = true)
	private void kairo$menuBackground(GuiGraphicsExtractor g, int x, int y, int w, int h, CallbackInfo ci) {
		if (VanillaStyle.enabled()) {
			VanillaStyle.menuBackground(g, x, y, w, h);
			ci.cancel();
		}
	}
}
