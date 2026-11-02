package dev.mordo.kairo.mixin;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.cuboid.ItemTransform;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** One layer of an item's render state: its quads and transforms, for the held item outline. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("quads")
	ItemQuads kairo$quads();

	@Accessor("itemTransform")
	ItemTransform kairo$itemTransform();

	@Accessor("localTransform")
	Matrix4f kairo$localTransform();

	@Accessor("specialRenderer")
	net.minecraft.client.renderer.special.SpecialModelRenderer<Object> kairo$specialRenderer();
}