package dev.mordo.kairo.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The layers of an item's render state, so the held-item shimmer can turn their foil on. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] kairo$layers();

	@Accessor("displayContext")
	net.minecraft.world.item.ItemDisplayContext kairo$displayContext();

	@Accessor("activeLayerCount")
	int kairo$activeLayerCount();
}
