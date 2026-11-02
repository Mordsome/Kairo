package dev.mordo.kairo.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.mods.ItemScale;
import dev.mordo.kairo.render.HandAnimations;
import net.minecraft.client.renderer.FirstPersonHandsAndItemsRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Item Scale: moves each hand's item, and scales it around its own centre right before it's drawn. */
@Mixin(FirstPersonHandsAndItemsRenderer.class)
abstract class FirstPersonItemMixin {
	/** Position offset, in view space, right after the hand's pose is pushed (so it moves with the swing). */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;pushPose()V", shift = At.Shift.AFTER, ordinal = 0))
	private void kairo$offset(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float pitch,
								InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack,
								SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		ItemScale mod = ModuleManager.get(ItemScale.class);
		if (mod.isEnabled() && !stack.isEmpty()) {
			poseStack.translate(mod.sideways.floatValue() * ItemScale.side(hand), mod.height.floatValue(), -mod.depth.floatValue());
		}
	}

	/** Our attack animations replace vanilla's whack swing (the third swingArm call: crossbow, bundle, whack). */
	@org.spongepowered.asm.mixin.injection.Redirect(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/FirstPersonHandsAndItemsRenderer;swingArm(FLcom/mojang/blaze3d/vertex/PoseStack;ILnet/minecraft/world/entity/HumanoidArm;)V",
		ordinal = 2))
	private void kairo$swing(FirstPersonHandsAndItemsRenderer self, float animation, PoseStack poseStack, int invert, net.minecraft.world.entity.HumanoidArm arm) {
		if (!HandAnimations.active()) {
			((FirstPersonItemMixin) (Object) self).kairo$vanillaSwing(animation, poseStack, invert, arm);
		}
	}

	@org.spongepowered.asm.mixin.gen.Invoker("swingArm")
	abstract void kairo$vanillaSwing(float animation, PoseStack poseStack, int invert, net.minecraft.world.entity.HumanoidArm arm);

	/** Our animation, right before the item is drawn (only in the plain holding branch: not using it, not spin-attacking). */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V",
		ordinal = 1))
	private void kairo$animate(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float pitch,
								  InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack,
								  SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		var avatar = player.avatarRenderState;
		if (!HandAnimations.active() || avatar == null || avatar.isAutoSpinAttack
			|| (avatar.isUsingItem && state.useItemRemainingTicks > 0 && avatar.useItemHand == hand)) {
			return;
		}
		var swing = avatar.currentSwing;
		if (swing != null && swing.hand() == hand && swing.animation().type() == net.minecraft.world.item.SwingAnimationType.STAB) {
			return;
		}
		HandAnimations.apply(poseStack, collector, hand, ItemScale.side(hand));
	}

	/** Held item look: the enchantment shimmer on every layer of the held item. */
	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void kairo$shimmer(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float pitch,
								  InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack,
								  SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		var shine = dev.mordo.kairo.render.Visuals.live() ? dev.mordo.kairo.core.GameVisuals.INSTANCE.heldShine.get() : dev.mordo.kairo.core.GameVisuals.HeldShine.NONE;
		if (shine != dev.mordo.kairo.core.GameVisuals.HeldShine.SHIMMER && shine != dev.mordo.kairo.core.GameVisuals.HeldShine.BOTH) {
			return;
		}
		var renderState = hand == InteractionHand.MAIN_HAND ? state.mainHandRenderState : state.offHandRenderState;
		var access = (ItemStackRenderStateAccessor) (Object) renderState;
		var layers = access.kairo$layers();
		for (int i = 0; i < access.kairo$activeLayerCount() && i < layers.length; i++) {
			layers[i].setFoilType(net.minecraft.client.renderer.item.ItemStackRenderState.FoilType.STANDARD);
		}
	}



	@Inject(method = "submitArmWithItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/item/ItemStackRenderState;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;III)V"))
	private void kairo$scale(PlayerRenderState player, FirstPersonHandsAndItemsRenderState state, float partialTicks, float pitch,
							   InteractionHand hand, float attack, ItemStack stack, float inverseArmHeight, PoseStack poseStack,
							   SubmitNodeCollector collector, int lightCoords, CallbackInfo ci) {
		ItemScale mod = ModuleManager.get(ItemScale.class);
		if (mod.isEnabled()) {
			float s = mod.scaleFor(hand);
			poseStack.scale(s, s, s);
		}
		// The outline last, after every change to the item's pose (Item Scale included), so it lines up with the item.
		var shine = dev.mordo.kairo.render.Visuals.live() ? dev.mordo.kairo.core.GameVisuals.INSTANCE.heldShine.get() : dev.mordo.kairo.core.GameVisuals.HeldShine.NONE;
		if (shine == dev.mordo.kairo.core.GameVisuals.HeldShine.OUTLINE || shine == dev.mordo.kairo.core.GameVisuals.HeldShine.BOTH) {
			dev.mordo.kairo.render.HeldOutline.draw(poseStack, collector, hand == InteractionHand.MAIN_HAND ? state.mainHandRenderState : state.offHandRenderState);
		}
	}
}
