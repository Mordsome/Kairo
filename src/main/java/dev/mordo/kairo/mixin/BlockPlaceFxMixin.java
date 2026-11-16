package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.VoidActionFx;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Kairo Void "Place pulse": notices a block you placed by comparing the two spots it can land in (next to the block
 * you clicked, or in its place) before and after the click. Only looks; changes nothing about placing.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class BlockPlaceFxMixin {
	@Unique
	private @Nullable BlockState kairo$beside;
	@Unique
	private @Nullable BlockState kairo$clicked;

	@Inject(method = "useItemOn", at = @At("HEAD"))
	private void kairo$beforePlace(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (player.getItemInHand(hand).getItem() instanceof BlockItem) {
			kairo$clicked = player.level().getBlockState(hit.getBlockPos());
			kairo$beside = player.level().getBlockState(hit.getBlockPos().relative(hit.getDirection()));
		} else {
			kairo$clicked = null;
			kairo$beside = null;
		}
	}

	@Inject(method = "useItemOn", at = @At("RETURN"))
	private void kairo$afterPlace(LocalPlayer player, InteractionHand hand, BlockHitResult hit, CallbackInfoReturnable<InteractionResult> cir) {
		if (kairo$clicked == null || kairo$beside == null || !cir.getReturnValue().consumesAction()) {
			return;
		}
		BlockPos beside = hit.getBlockPos().relative(hit.getDirection());
		if (player.level().getBlockState(beside) != kairo$beside && !player.level().getBlockState(beside).isAir()) {
			VoidActionFx.onPlace(beside);
		} else if (player.level().getBlockState(hit.getBlockPos()) != kairo$clicked && !player.level().getBlockState(hit.getBlockPos()).isAir()) {
			VoidActionFx.onPlace(hit.getBlockPos());
		}
	}
}
