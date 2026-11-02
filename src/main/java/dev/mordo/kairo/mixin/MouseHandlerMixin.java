package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.keybinds.InputTracker;
import dev.mordo.kairo.core.keybinds.KeybindManager;
import dev.mordo.kairo.mods.ScrollableTooltips;
import dev.mordo.kairo.mods.Zoom;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouse hooks: tracks the player's own buttons (CPS, mouse keybinds), scales turning speed while zoomed,
 * and lets Zoom / Scrollable Tooltips take the scroll wheel when they need it.
 */
@Mixin(MouseHandler.class)
abstract class MouseHandlerMixin {
	@Inject(method = "onButton", at = @At("HEAD"))
	private void kairo$onButton(long handle, MouseButtonInfo info, int action, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (handle != mc.getWindow().handle()) {
			return;
		}
		boolean pressed = action == InputConstants.PRESS;
		boolean inGame = mc.gui.screen() == null && mc.gui.overlay() == null;
		InputTracker.onMouseButton(info.button(), pressed, inGame);
		if (pressed) {
			KeybindManager.onMousePressed(info.button());
		}
	}

	@Inject(method = "onScroll", at = @At("HEAD"), cancellable = true)
	private void kairo$onScroll(long handle, double xoffset, double yoffset, CallbackInfo ci) {
		Minecraft mc = Minecraft.getInstance();
		if (handle != mc.getWindow().handle() || mc.gui.overlay() != null) {
			return;
		}
		if (mc.gui.screen() == null) {
			if (ModuleManager.get(Zoom.class).onScroll(yoffset)) {
				ci.cancel(); // zooming: the hotbar slot stays put
			}
		} else if (ModuleManager.get(ScrollableTooltips.class).onScroll(xoffset, yoffset)) {
			ci.cancel();
		}
	}

	@ModifyVariable(method = "turnPlayer", at = @At("STORE"), name = "sensitivityMod")
	private double kairo$zoomSensitivity(double sensitivityMod) {
		return sensitivityMod * ModuleManager.get(Zoom.class).sensitivityFactor();
	}
}
