package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.audio.SoundBuffer;
import dev.mordo.kairo.sound.SoundLibrary;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.CompletableFuture;

/** Serves the client's own sounds (built-in and the player's files) from memory instead of from resource packs. */
@Mixin(SoundBufferLibrary.class)
abstract class SoundBufferLibraryMixin {
	@Inject(method = "getCompleteBuffer", at = @At("HEAD"), cancellable = true)
	private void kairo$customBuffer(Identifier path, CallbackInfoReturnable<CompletableFuture<SoundBuffer>> cir) {
		SoundBuffer buffer = SoundLibrary.bufferFor(path);
		if (buffer != null) {
			cir.setReturnValue(CompletableFuture.completedFuture(buffer));
		}
	}

	/** The engine is dropping all OpenAL buffers (reload / device change); ours go with them. */
	@Inject(method = "clear", at = @At("HEAD"))
	private void kairo$clear(CallbackInfo ci) {
		SoundLibrary.onBuffersCleared();
	}
}
