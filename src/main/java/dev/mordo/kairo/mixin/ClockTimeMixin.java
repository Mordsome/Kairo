package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.TimeChanger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Time Changer: the client's copy of the world clock reads the time you picked. This is the clock the sky and the
 * light are drawn from; the server's clock (the real time) is untouched.
 */
@Mixin(targets = "net.minecraft.client.ClientClockManager$ClientClockInstance")
abstract class ClockTimeMixin {
	@Inject(method = "totalTicks", at = @At("RETURN"), cancellable = true)
	private void kairo$time(CallbackInfoReturnable<Long> cir) {
		TimeChanger mod = ModuleManager.get(TimeChanger.class);
		if (mod.isEnabled()) {
			cir.setReturnValue(mod.ticks(cir.getReturnValue()));
		}
	}

	@Inject(method = "partialTick", at = @At("RETURN"), cancellable = true)
	private void kairo$partial(CallbackInfoReturnable<Float> cir) {
		TimeChanger mod = ModuleManager.get(TimeChanger.class);
		if (mod.isEnabled()) {
			cir.setReturnValue(mod.partialTick());
		}
	}
}
