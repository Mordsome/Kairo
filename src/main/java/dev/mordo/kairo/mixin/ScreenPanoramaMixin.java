package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.background.MenuBackground;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every menu without a world (vanilla's and the client's) shows the chosen image or GIF instead of the panorama. */
@Mixin(Screen.class)
abstract class ScreenPanoramaMixin {
	@Inject(method = "extractPanorama", at = @At("HEAD"), cancellable = true)
	private void kairo$customBackground(GuiGraphicsExtractor g, float partialTick, CallbackInfo ci) {
		if (MenuBackground.replacesPanorama()) {
			MenuBackground.request();
			ci.cancel();
		} else if (dev.mordo.kairo.render.background.VoidPanorama.replaces()) {
			dev.mordo.kairo.render.background.VoidPanorama.request();
			ci.cancel();
		}
	}
}
