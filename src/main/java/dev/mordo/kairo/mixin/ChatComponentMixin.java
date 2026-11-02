package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.ChatTweaks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.function.Predicate;

/** Chat tweaks on your own chat display: history length, timestamps, stacking repeats, slide-in, background. */
@Mixin(ChatComponent.class)
abstract class ChatComponentMixin {
	@Shadow
	@Final
	private List<GuiMessage> allMessages;
	@Shadow
	@Final
	private List<GuiMessage.Line> trimmedMessages;

	@Unique
	private String kairo$lastRaw = "";
	@Unique
	private int kairo$repeats = 1;

	@ModifyConstant(method = {"addMessageToDisplayQueue", "addMessageToQueue"}, constant = @Constant(intValue = 100))
	private int kairo$history(int vanilla) {
		return ModuleManager.get(ChatTweaks.class).historyLimit(vanilla);
	}

	@ModifyVariable(method = "addMessage", at = @At("HEAD"), argsOnly = true)
	private Component kairo$decorate(Component contents) {
		ChatTweaks chat = ModuleManager.get(ChatTweaks.class);
		if (!chat.isEnabled()) {
			return contents;
		}
		chat.onMessageAdded();
		String raw = contents.getString();
		if (chat.stackRepeats.isOn() && raw.equals(kairo$lastRaw) && !allMessages.isEmpty()) {
			// Same as the last message: replace it with one line that counts the repeats.
			GuiMessage previous = allMessages.removeFirst();
			trimmedMessages.removeIf(line -> line.parent() == previous);
			kairo$repeats++;
			contents = chat.withRepeatCount(contents, kairo$repeats);
		} else {
			kairo$lastRaw = raw;
			kairo$repeats = 1;
		}
		return chat.withTimestamp(contents);
	}

	@Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
		at = @At(value = "INVOKE", target = "Lorg/joml/Matrix3x2fStack;pushMatrix()Lorg/joml/Matrix3x2fStack;", shift = At.Shift.AFTER))
	private void kairo$slideIn(GuiGraphicsExtractor graphics, net.minecraft.client.gui.Font font, int ticks, int mouseX, int mouseY,
								 ChatComponent.DisplayMode mode, boolean changeCursor, CallbackInfo ci) {
		float offset = ModuleManager.get(ChatTweaks.class).slideOffset();
		if (offset > 0) {
			graphics.pose().translate(0, offset);
		}
	}

	@ModifyVariable(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V",
		at = @At("STORE"), name = "backgroundOpacity")
	private float kairo$background(float opacity) {
		ChatTweaks chat = ModuleManager.get(ChatTweaks.class);
		return chat.isEnabled() && chat.customBackground.isOn() ? chat.backgroundOpacity.floatValue() : opacity;
	}

}
