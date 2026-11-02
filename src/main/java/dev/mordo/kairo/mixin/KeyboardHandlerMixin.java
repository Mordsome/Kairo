package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.keybinds.InputTracker;
import dev.mordo.kairo.core.keybinds.KeybindManager;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Feeds key presses to the client's own keybinds and tracks held keys. Observes only; vanilla handling is untouched. */
@Mixin(KeyboardHandler.class)
abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void kairo$onKeyPress(long handle, int action, KeyEvent event, CallbackInfo ci) {
		if (handle != Minecraft.getInstance().getWindow().handle()) {
			return;
		}
		if (action == InputConstants.PRESS) {
			InputTracker.onKey(event.key(), true);
			KeybindManager.onKeyPressed(event.key());
		} else if (action == InputConstants.RELEASE) {
			InputTracker.onKey(event.key(), false);
		}
	}
}
