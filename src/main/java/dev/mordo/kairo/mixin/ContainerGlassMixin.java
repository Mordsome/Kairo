package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.InventoryGlass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventory mod: the frosted panel goes down right after the screen's own background (blur, dim) and before the
 * container draws its (recoloured, mostly see-through) texture on top.
 */
@Mixin(Screen.class)
abstract class ContainerGlassMixin {
	@Inject(method = "extractBackground", at = @At("TAIL"))
	private void kairo$glassPanel(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
		InventoryGlass glass = ModuleManager.get(InventoryGlass.class);
		if (glass.activeFor(this) && this instanceof AbstractContainerScreenAccessor container) {
			glass.drawPanel(graphics, container.kairo$leftPos(), container.kairo$topPos(),
				container.kairo$imageWidth(), container.kairo$imageHeight());
		}
	}
}
