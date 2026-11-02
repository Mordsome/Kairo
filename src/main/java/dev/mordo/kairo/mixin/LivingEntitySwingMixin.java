package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.HandAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.SwingAnimationType;
import net.minecraft.world.item.component.SwingAnimation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Starts the client's own first-person animation clock whenever your player starts a swing. Read-only. */
@Mixin(LivingEntity.class)
abstract class LivingEntitySwingMixin {
	@Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z", at = @At("RETURN"))
	private void kairo$swing(InteractionHand hand, SwingAnimation animation, boolean send, CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && (Object) this == Minecraft.getInstance().player && animation.type() != SwingAnimationType.STAB) {
			HandAnimations.onSwing(hand);
		}
	}
}
