package dev.mordo.kairo.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.InventoryGlass;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Inventory mod: the creative menu's tabs become glass tabs that belong to the glass panel. */
@Mixin(CreativeModeInventoryScreen.class)
abstract class CreativeTabsMixin {
	@WrapOperation(method = "extractTabButton", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
	private void kairo$glassTab(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y, int w, int h,
								  Operation<Void> original, @Local(argsOnly = true) CreativeModeTab tab) {
		InventoryGlass glass = ModuleManager.get(InventoryGlass.class);
		if (!glass.isEnabled()) {
			original.call(g, pipeline, sprite, x, y, w, h);
			return;
		}
		boolean selected = tab == CreativeModeInventoryScreenAccessor.kairo$selectedTab();
		glass.drawTab(g, x, y, w, h, tab.row() == CreativeModeTab.Row.TOP, selected);
	}
}
