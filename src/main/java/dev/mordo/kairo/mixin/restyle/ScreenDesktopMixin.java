package dev.mordo.kairo.mixin.restyle;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A menu look with a desktop of its own (Kairo 98's, say) draws it instead of the title panorama. */
@Mixin(Screen.class)
abstract class ScreenDesktopMixin {
	@Shadow
	public int width;
	@Shadow
	public int height;

	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void kairo$desktop(GuiGraphicsExtractor g, float partialTick, CallbackInfo ci) {
		dev.mordo.kairo.api.MenuLook look = dev.mordo.kairo.api.Addons.activeLook();
		if (look == null) {
			return;
		}
		try {
			if (look.desktop(g, width, height)) {
				ci.cancel();
			}
		} catch (Throwable t) {
			dev.mordo.kairo.api.Addons.lookFailed(look, t);
		}
	}
}
