package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.Screenshots;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.function.Consumer;

/** Hands the "screenshot saved" message to the Screenshots mod (buttons, preview). */
@Mixin(Screenshot.class)
abstract class ScreenshotMixin {
	@ModifyVariable(method = "grab(Ljava/io/File;Ljava/lang/String;Lcom/mojang/blaze3d/pipeline/RenderTarget;ILjava/util/function/Consumer;)V",
		at = @At("HEAD"), argsOnly = true)
	private static Consumer<Component> kairo$wrapMessage(Consumer<Component> callback) {
		return ModuleManager.get(Screenshots.class).wrap(callback);
	}
}
