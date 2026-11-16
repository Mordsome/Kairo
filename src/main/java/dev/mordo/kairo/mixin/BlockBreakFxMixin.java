package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.VoidActionFx;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Kairo Void "Block shatter": a broken block bursts into light instead of Minecraft's crumbs (only the picture). */
@Mixin(ClientLevel.class)
abstract class BlockBreakFxMixin {
	@Inject(method = "addDestroyBlockEffect", at = @At("HEAD"), cancellable = true)
	private void kairo$shatter(BlockPos pos, BlockState state, CallbackInfo ci) {
		if (VoidActionFx.onBreak(pos, state)) {
			ci.cancel();
		}
	}
}
