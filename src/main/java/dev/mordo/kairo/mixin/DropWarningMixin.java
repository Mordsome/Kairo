package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.DropWarning;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Drop Warning: holds back the first drop of something valuable (the drop key, and throws from an inventory). */
@Mixin(MultiPlayerGameMode.class)
abstract class DropWarningMixin {
	@Inject(method = "dropItem", at = @At("HEAD"), cancellable = true)
	private void kairo$dropKey(LocalPlayer player, boolean fullStack, CallbackInfo ci) {
		if (!ModuleManager.get(DropWarning.class).allow(player.getMainHandItem())) {
			ci.cancel();
		}
	}

	@Inject(method = "handleContainerInput", at = @At("HEAD"), cancellable = true)
	private void kairo$throw(int containerId, int slotId, int button, ContainerInput input, Player player, CallbackInfo ci) {
		AbstractContainerMenu menu = player.containerMenu;
		ItemStack stack;
		if (input == ContainerInput.THROW && slotId >= 0 && slotId < menu.slots.size()) {
			stack = menu.slots.get(slotId).getItem(); // Q (or Ctrl+Q) over a slot
		} else if (input == ContainerInput.PICKUP && slotId == AbstractContainerMenu.SLOT_CLICKED_OUTSIDE) {
			stack = menu.getCarried(); // clicking outside the window with an item held
		} else {
			return;
		}
		if (!ModuleManager.get(DropWarning.class).allow(stack)) {
			ci.cancel();
		}
	}
}
