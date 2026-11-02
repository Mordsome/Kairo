package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.mixin.ItemStackRenderStateAccessor;
import dev.mordo.kairo.mixin.LayerRenderStateAccessor;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.resources.Identifier;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Held item outline: a slightly bigger copy of the item, in one flat colour (the shimmer colour, or rainbow), drawn
 * just behind the real one, so only its rim shows around the item's shape. The game's own entity outline isn't
 * processed for the hand, so this draws it directly.
 */
public final class HeldOutline {
	private static final float GROW = 1.04f;
	private static final float BEHIND = 0.035f;
	private static final float GROW_SOLID = 1.07f;
	private static final float BEHIND_SOLID = 0.07f;
	private static final Map<Identifier, RenderType> TYPES = new HashMap<>();

	private HeldOutline() {
	}

	private static RenderType type(Identifier atlas) {
		return TYPES.computeIfAbsent(atlas, id -> {
			RenderPipeline pipeline = KairoPipelines.ITEM_SILHOUETTE;
			RenderSetup setup = RenderSetup.builder(pipeline).withTexture("Sampler0", id).createRenderSetup();
			return RenderType.create("kairo_item_silhouette", setup);
		});
	}

	/** Draws the outline for {@code item}, about to be drawn with {@code poseStack}. */
	public static void draw(PoseStack poseStack, SubmitNodeCollector collector, ItemStackRenderState item) {
		var access = (ItemStackRenderStateAccessor) (Object) item;
		var layers = access.kairo$layers();
		int color = HeldGlow.color() | 0xFF000000;
		for (int i = 0; i < access.kairo$activeLayerCount() && i < layers.length; i++) {
			var layer = (LayerRenderStateAccessor) (Object) layers[i];
			if (layer.kairo$specialRenderer() != null) {
				continue; // shields, tridents...: no plain quads to copy
			}
			List<BakedQuad> quads = layer.kairo$quads().all();
			if (quads.isEmpty()) {
				continue;
			}
			// Centre of the model, to grow it around.
			Vector3f min = new Vector3f(Float.MAX_VALUE);
			Vector3f max = new Vector3f(-Float.MAX_VALUE);
			for (BakedQuad quad : quads) {
				for (int v = 0; v < 4; v++) {
					Vector3fc p = quad.position(v);
					min.min(p);
					max.max(p);
				}
			}
			// Flat items (swords, tools) are thin; blocks and 3D models are about as deep as they are wide.
			boolean solid = (max.z - min.z) > 0.25f * Math.max(max.x - min.x, max.y - min.y);
			float grow = solid ? GROW_SOLID : GROW;
			float growDepth = solid ? GROW_SOLID : 1.6f;
			float behind = solid ? BEHIND_SOLID : BEHIND;
			Vector3f centre = min.add(max).mul(0.5f);
			poseStack.pushPose();
			layer.kairo$itemTransform().apply(access.kairo$displayContext().leftHand(), poseStack.last());
			poseStack.last().mulPose(layer.kairo$localTransform());
			// Push it straight back from the camera (which sits at the origin of this space), further than it grows,
			// so the copy is always behind the real item and only its rim shows...
			Vector3f world = poseStack.last().pose().transformPosition(new Vector3f(centre));
			float distance = world.length();
			if (distance > 1e-3f) {
				// Further away looks smaller: grow it that much more, so the rim keeps its width.
				float compensate = (distance + behind) / distance;
				grow *= compensate;
				growDepth *= solid ? compensate : 1;
				world.normalize().mul(behind);
				poseStack.last().pose().translateLocal(world.x, world.y, world.z);
			}
			// ...and grow it around its centre.
			poseStack.translate(centre.x, centre.y, centre.z);
			poseStack.scale(grow, grow, growDepth);
			poseStack.translate(-centre.x, -centre.y, -centre.z);
			Map<Identifier, List<BakedQuad>> byAtlas = new HashMap<>();
			for (BakedQuad quad : quads) {
				byAtlas.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), k -> new java.util.ArrayList<>()).add(quad);
			}
			for (var entry : byAtlas.entrySet()) {
				List<BakedQuad> list = entry.getValue();
				collector.submitCustomGeometry(poseStack, type(entry.getKey()), (pose, buffer) -> {
					QuadInstance instance = new QuadInstance();
					instance.setColor(color);
					instance.setLightCoords(0xF000F0);
					instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
					for (BakedQuad quad : list) {
						buffer.putBakedQuad(pose, quad, instance);
					}
				});
			}
			poseStack.popPose();
		}
	}
}
