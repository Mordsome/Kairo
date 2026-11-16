package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.WeatherChanger;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Weather Changer: the client's world reports the rain and thunder you picked (the server's world is untouched). */
@Mixin(Level.class)
abstract class LevelWeatherMixin {
	@Inject(method = "getRainLevel", at = @At("RETURN"), cancellable = true)
	private void kairo$rain(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			WeatherChanger mod = ModuleManager.get(WeatherChanger.class);
			if (mod.active()) {
				cir.setReturnValue(mod.rain(cir.getReturnValue()));
			}
		}
	}

	@Inject(method = "getThunderLevel", at = @At("RETURN"), cancellable = true)
	private void kairo$thunder(float partialTicks, CallbackInfoReturnable<Float> cir) {
		if ((Object) this instanceof ClientLevel) {
			WeatherChanger mod = ModuleManager.get(WeatherChanger.class);
			if (mod.active()) {
				cir.setReturnValue(mod.thunder(cir.getReturnValue()));
			}
		}
	}
}
