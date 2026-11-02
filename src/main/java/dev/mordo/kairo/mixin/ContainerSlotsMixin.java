package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.InventoryGlass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory mod: the client's slot cells, drawn under the hover highlight and the items. */
@Mixin(AbstractContainerScreen.class)
abstract class ContainerSlotsMixin {
	@Inject(method = "extractSlotHighlightBack", at = @At("HEAD"))
	private void kairo$slotCells(GuiGraphicsExtractor graphics, CallbackInfo ci) {
		InventoryGlass glass = ModuleManager.get(InventoryGlass.class);
		if (glass.activeFor(this)) {
			glass.drawSlots(graphics, ((AbstractContainerScreen<?>) (Object) this).getMenu().slots);
		}
	}
}
