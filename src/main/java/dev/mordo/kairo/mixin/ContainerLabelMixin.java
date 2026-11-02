package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.InventoryGlass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Inventory mod: container titles ("Inventory", "Chest"...) are dark grey for a light panel; on glass they turn light. */
@Mixin(GuiGraphicsExtractor.class)
abstract class ContainerLabelMixin {
	@ModifyVariable(method = {
		"text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;IIIZ)V",
		"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/util/FormattedCharSequence;IIIZ)V",
		"text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIIZ)V"
	}, at = @At("HEAD"), argsOnly = true, ordinal = 2)
	private int kairo$lightLabels(int color) {
		if (color != 0xFF404040) {
			return color;
		}
		InventoryGlass glass = ModuleManager.get(InventoryGlass.class);
		return glass.active() ? InventoryGlass.labelColor(color) : color;
	}
}
