package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.mods.ChatTweaks;
import dev.mordo.kairo.mods.chat.ChatLines;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Chat screen: a search box that filters the chat, and right-click a message to copy it. */
@Mixin(ChatScreen.class)
abstract class ChatScreenMixin extends Screen {
	protected ChatScreenMixin(Component title) {
		super(title);
	}

	private ChatComponent kairo$chat() {
		return this.minecraft.gui.hud.getChat();
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void kairo$addSearch(CallbackInfo ci) {
		ChatTweaks chat = ModuleManager.get(ChatTweaks.class);
		if (!chat.isEnabled() || !chat.searchBar.isOn()) {
			return;
		}
		// Keyboard navigation (↑ for the last command, Tab) must never land in here: only a click focuses it.
		EditBox search = new EditBox(this.font, this.width - 154, 6, 148, 14, Component.literal("Search chat")) {
			@Override
			public net.minecraft.client.gui.@org.jspecify.annotations.Nullable ComponentPath nextFocusPath(
				net.minecraft.client.gui.navigation.FocusNavigationEvent event) {
				return null;
			}
		};
		search.setHint(Component.literal("Search chat…"));
		search.setMaxLength(64);
		search.setResponder(text -> ChatLines.filter(kairo$chat(), text));
		this.addRenderableWidget(search);
	}

	@Inject(method = "removed", at = @At("HEAD"))
	private void kairo$clearSearch(CallbackInfo ci) {
		ChatLines.clearFilter(kairo$chat());
	}

	@Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
	private void kairo$copyMessage(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
		ChatTweaks chat = ModuleManager.get(ChatTweaks.class);
		if (event.button() != InputConstants.MOUSE_BUTTON_RIGHT || !chat.isEnabled() || !chat.rightClickCopy.isOn()) {
			return;
		}
		GuiMessage message = ChatLines.messageAt(kairo$chat(), event.x(), event.y());
		if (message != null) {
			this.minecraft.keyboardHandler.setClipboard(message.content().getString());
			Toasts.show("Message copied", Theme.accent());
			cir.setReturnValue(true);
		}
	}
}
