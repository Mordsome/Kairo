package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.FlashState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Stores the hit flash on each living entity's render state (see LivingEntityFlashMixin). */
@Mixin(LivingEntityRenderState.class)
abstract class LivingEntityRenderStateMixin implements FlashState {
	@Unique
	private float kairo$flash;

	@Override
	public float kairo$flash() {
		return kairo$flash;
	}

	@Override
	public void kairo$setFlash(float flash) {
		kairo$flash = flash;
	}
}
