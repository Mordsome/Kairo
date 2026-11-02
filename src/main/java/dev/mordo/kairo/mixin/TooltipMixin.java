package dev.mordo.kairo.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.ScrollableTooltips;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Scrollable Tooltips: offsets where an oversized tooltip is drawn. Works for every screen. */
@Mixin(GuiGraphicsExtractor.class)
abstract class TooltipMixin {
	@ModifyVariable(method = "tooltip", at = @At("STORE"), name = "x")
	private int kairo$scrollX(int x, @Local(name = "w") int w, @Local(name = "h") int h) {
		GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
		return ModuleManager.get(ScrollableTooltips.class).adjustX(x, w, h, self.guiWidth(), self.guiHeight());
	}

	@ModifyVariable(method = "tooltip", at = @At("STORE"), name = "y")
	private int kairo$scrollY(int y, @Local(name = "h") int h) {
		GuiGraphicsExtractor self = (GuiGraphicsExtractor) (Object) this;
		return ModuleManager.get(ScrollableTooltips.class).adjustY(y, h, self.guiHeight());
	}
}
