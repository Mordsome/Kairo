package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.TotemPops;
import net.minecraft.client.player.ItemActivation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/** Totem Pops: a shorter pop animation timer (the renderer plays the curve faster to match). */
@Mixin(ItemActivation.class)
abstract class ItemActivationMixin {
	@ModifyConstant(method = "activate", constant = @Constant(intValue = 40))
	private int kairo$popLength(int ticks) {
		return Math.max(1, Math.round(ticks * ModuleManager.get(TotemPops.class).duration()));
	}

	/** Kairo Void: the totem pop blast (golden rays and a slow ripple). */
	@org.spongepowered.asm.mixin.injection.Inject(method = "activate", at = @org.spongepowered.asm.mixin.injection.At("HEAD"))
	private void kairo$blast(net.minecraft.world.item.ItemStack stack, net.minecraft.util.RandomSource random,
							   org.spongepowered.asm.mixin.injection.callback.CallbackInfo ci) {
		if (stack.has(net.minecraft.core.component.DataComponents.DEATH_PROTECTION)) {
			dev.mordo.kairo.render.VoidFx.onTotemPop();
		}
	}
}
