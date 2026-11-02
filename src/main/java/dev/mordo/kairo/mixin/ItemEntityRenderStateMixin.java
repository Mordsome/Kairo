package dev.mordo.kairo.mixin;

import dev.mordo.kairo.render.ItemPhysics;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Remembers whether a dropped item is on the ground and how rare it is (item physics). */
@Mixin(ItemEntityRenderState.class)
abstract class ItemEntityRenderStateMixin implements ItemPhysics.State {
	@Unique
	private boolean kairo$onGround;
	@Unique
	private int kairo$rarity;

	@Override
	public void kairo$setPhysics(boolean onGround, int rarityColor) {
		kairo$onGround = onGround;
		kairo$rarity = rarityColor;
	}

	@Override
	public boolean kairo$onGround() {
		return kairo$onGround;
	}

	@Override
	public int kairo$rarityColor() {
		return kairo$rarity;
	}
}
