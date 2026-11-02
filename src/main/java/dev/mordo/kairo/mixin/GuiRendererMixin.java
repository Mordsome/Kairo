package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.Blur;
import net.minecraft.client.gui.render.GuiRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Runs the menu blur at the start of the GUI pass: after the world (or the title screen panorama, which the
 * GUI renderer draws first) and before any GUI element, so glass panels blur whatever is behind the menus.
 */
@Mixin(GuiRenderer.class)
abstract class GuiRendererMixin {
	@Inject(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/render/GuiRenderer;prepare()V"))
	private void kairo$blurBeforeGui(CallbackInfo ci) {
		dev.mordo.kairo.render.background.MenuBackground.renderIfRequested(); // custom image/GIF backdrop first
		dev.mordo.kairo.render.background.VoidPanorama.renderIfRequested(); // or Void's drawn space scene
		Blur.renderIfRequested();
	}
}
