package dev.mordo.kairo.mixin;

import dev.mordo.kairo.mods.Screenshots;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.ClickEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Chat buttons from the client (screenshot Copy/Open/Delete) run locally instead of copying their text. */
@Mixin(Screen.class)
abstract class ScreenClickMixin {
	@Inject(method = "defaultHandleGameClickEvent", at = @At("HEAD"), cancellable = true)
	private static void kairo$gameClick(ClickEvent event, Minecraft mc, Screen screen, CallbackInfo ci) {
		if (event instanceof ClickEvent.CopyToClipboard copy && Screenshots.handleAction(copy.value())) {
			ci.cancel();
		}
	}

	@Inject(method = "defaultHandleClickEvent", at = @At("HEAD"), cancellable = true)
	private static void kairo$click(ClickEvent event, Minecraft mc, Screen screen, CallbackInfo ci) {
		if (event instanceof ClickEvent.CopyToClipboard copy && Screenshots.handleAction(copy.value())) {
			ci.cancel();
		}
	}
}
