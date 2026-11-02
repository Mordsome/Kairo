package dev.mordo.kairo.mixin.restyle;

import dev.mordo.kairo.gui.ButtonFx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Button click effects that fly over the screen (sparks, shockwave, confetti), drawn after everything else. */
@Mixin(Screen.class)
abstract class ScreenFxMixin {
	@Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
	private void kairo$clickOverlay(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		ButtonFx.drawOverlay(graphics);
	}
}
