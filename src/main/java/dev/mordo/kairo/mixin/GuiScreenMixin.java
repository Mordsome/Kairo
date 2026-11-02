package dev.mordo.kairo.mixin;

import dev.mordo.kairo.gui.main.MainScreens;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Every screen change goes through here: swap in the client's main menu, worlds and servers screens. */
@Mixin(Gui.class)
abstract class GuiScreenMixin {
	@ModifyVariable(method = "setScreen", at = @At("HEAD"), argsOnly = true)
	private Screen kairo$replaceScreen(Screen screen) {
		return MainScreens.replace(screen);
	}
}
