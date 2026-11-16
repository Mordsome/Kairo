package dev.mordo.kairo.mixin;

import dev.mordo.kairo.mods.Reconnect;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.TransferState;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reconnect: remembers which server a connection is starting to, so the Disconnected screen can offer it again. */
@Mixin(ConnectScreen.class)
abstract class ConnectScreenMixin {
	@Inject(method = "startConnecting", at = @At("HEAD"))
	private static void kairo$rememberServer(Screen parent, Minecraft minecraft, ServerAddress address, ServerData server, boolean quickPlay,
											   TransferState transferState, CallbackInfo ci) {
		Reconnect.remember(server);
	}
}
