package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.ShulkerPreview;
import dev.mordo.kairo.mods.shulker.ShulkerContents;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.component.TooltipProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.function.Consumer;

/** Shulker Preview: gives shulker box items a tooltip image (the contents grid). Client display only. */
@Mixin(ItemStack.class)
abstract class ItemStackMixin {
	@Inject(method = "getTooltipImage", at = @At("RETURN"), cancellable = true)
	private void kairo$shulkerPreview(CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		if (cir.getReturnValue().isEmpty()) {
			ShulkerContents contents = ModuleManager.get(ShulkerPreview.class).tooltipFor((ItemStack) (Object) this);
			if (contents != null) {
				cir.setReturnValue(Optional.of(contents));
			}
		}
	}

	/** While the grid is shown, drop vanilla's "64 x Diamond ..." text list for the same contents. */
	@Inject(method = "addToTooltip(Lnet/minecraft/core/component/DataComponentType;Lnet/minecraft/world/item/component/TooltipProvider$Getter;Lnet/minecraft/world/item/Item$TooltipContext;Lnet/minecraft/world/item/component/TooltipDisplay;Ljava/util/function/Consumer;Lnet/minecraft/world/item/TooltipFlag;)V",
		at = @At("HEAD"), cancellable = true)
	private void kairo$hideContainerList(DataComponentType<?> type, TooltipProvider.Getter<?> getter, Item.TooltipContext context,
										   TooltipDisplay display, Consumer<Component> lines, TooltipFlag flag, CallbackInfo ci) {
		if (type == DataComponents.CONTAINER && ModuleManager.get(ShulkerPreview.class).tooltipFor((ItemStack) (Object) this) != null) {
			ci.cancel();
		}
	}
}
