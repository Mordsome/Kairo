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
 * Held item outline: the item's silhouette in one flat colour (the shimmer colour, or rainbow), drawn just behind the
 * real one a dozen times, each shifted a little sideways on screen in a different direction. Together they make a rim
 * of the same width all the way round that follows the item's real shape, inner corners and gaps included (a
 * scaled-up copy would be thick at the tips and missing in the notches). The game's own entity outline isn't
 * processed for the hand, so this draws it directly.
 */
public final class HeldOutline {
	/** Rim width, as a fraction of the distance to the item (so it's the same on screen near or far). */
	private static final float WIDTH = 0.0085f;
	/** How many shifted copies make the rim (enough for it to read as one smooth line). */
	private static final int STEPS = 12;
	private static final float BEHIND = 0.035f;
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
			float behind = solid ? BEHIND_SOLID : BEHIND;
			Vector3f centre = min.add(max).mul(0.5f);
			poseStack.pushPose();
			layer.kairo$itemTransform().apply(access.kairo$displayContext().leftHand(), poseStack.last());
			poseStack.last().mulPose(layer.kairo$localTransform());
			// The copies sit straight back from the camera (which is at the origin of this space, looking down -z), so
			// they are always behind the real item; pushed back they would look smaller, so they grow by as much.
			Vector3f world = poseStack.last().pose().transformPosition(new Vector3f(centre));
			float distance = world.length();
			float compensate = 1;
			Vector3f back = new Vector3f();
			if (distance > 1e-3f) {
				compensate = (distance + behind) / distance;
				back.set(world).normalize().mul(behind);
			}
			// How far each copy is shifted sideways: the same width on screen however near the item is.
			float rim = WIDTH * (distance + behind);
			Map<Identifier, List<BakedQuad>> byAtlas = new HashMap<>();
			for (BakedQuad quad : quads) {
				byAtlas.computeIfAbsent(quad.materialInfo().sprite().atlasLocation(), k -> new java.util.ArrayList<>()).add(quad);
			}
			for (int step = 0; step < STEPS; step++) {
				double angle = step * Math.PI * 2 / STEPS;
				poseStack.pushPose();
				poseStack.last().pose().translateLocal(back.x + (float) Math.cos(angle) * rim, back.y + (float) Math.sin(angle) * rim, back.z);
				poseStack.translate(centre.x, centre.y, centre.z);
				poseStack.scale(compensate, compensate, compensate);
				poseStack.translate(-centre.x, -centre.y, -centre.z);
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
			poseStack.popPose();
		}
	}
}
