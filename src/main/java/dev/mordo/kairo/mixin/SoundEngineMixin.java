package dev.mordo.kairo.mixin;

import dev.mordo.kairo.sound.SoundReplacer;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Swaps chosen sounds (totem pop, hurt, hit) for the player's pick right before they play. Audio only. */
@Mixin(SoundEngine.class)
abstract class SoundEngineMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void kairo$replace(SoundInstance instance, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		SoundInstance replacement = SoundReplacer.replace(instance);
		if (replacement != null) {
			cir.setReturnValue(((SoundEngine) (Object) this).play(replacement));
		}
	}
}
