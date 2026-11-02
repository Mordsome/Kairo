package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.Hotbar;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hotbar mod: the slim (or hidden) XP bar. The locator and mount jump bars stay vanilla. */
@Mixin(ExperienceBar.class)
abstract class ExperienceBarMixin {
	@Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
	private void kairo$styledXpBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
		Hotbar hotbar = ModuleManager.get(Hotbar.class);
		if (hotbar.styledXp()) {
			hotbar.renderer().xpBar(graphics);
			ci.cancel();
		}
	}
}
