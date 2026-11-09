package dev.mordo.kairo.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/** Draws wings, hats and bandanas on your own player, following the body parts' pose (and emotes). */
final class CosmeticsLayer extends RenderLayer<AvatarRenderState, PlayerModel> {
	private final Cosmetics cosmetics;

	CosmeticsLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent, Cosmetics cosmetics) {
		super(parent);
		this.cosmetics = cosmetics;
	}

	private interface Drawer {
		void draw(Geo geo);
	}

	@Override
	public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
		Cosmetics c = cosmetics;
		// Kairo Void "Your look: Glow" (only ever your own player).
		dev.mordo.kairo.render.VoidLook.glowShell(poseStack, collector, getParentModel(), state, light, LivingEntityRenderer.getOverlayCoords(state, 0));
		if (!c.isEnabled() || state.isInvisible || state.isSpectator || !Cosmetics.isLocal(state)) {
			return;
		}
		float time = state.ageInTicks / 20f;
		float moving = Math.min(1, state.walkAnimationSpeed * 1.5f);
		int overlay = LivingEntityRenderer.getOverlayCoords(state, 0);
		PlayerModel model = getParentModel();
		boolean helmet = !state.headEquipment.isEmpty();

		if (!c.wings.get().none() && model.body.visible) {
			part(poseStack, collector, model, model.body, light, overlay, false, geo -> Wings.draw(geo, c, time, moving));
		}
		boolean headBandana = switch (c.bandana.get()) {
			case HEADBAND, NINJA, PIRATE, FACE_MASK -> true;
			default -> false;
		};
		if (headBandana && model.head.visible) {
			// Over a helmet everything on the head grows a little so it isn't hidden inside it.
			part(poseStack, collector, model, model.head, light, overlay, helmet, geo -> Headwear.headBandana(geo, c, time, moving));
		}
		if (!c.hat.get().none() && model.head.visible) {
			if (c.hat.get() == Cosmetics.HatStyle.CAT_EARS) {
				for (int side = -1; side <= 1; side += 2) {
					int s = side;
					hatPart(poseStack, collector, model, light, overlay, helmet, c, ps -> CatEars.pose(ps, c, s, time, moving),
						geo -> Headwear.catEar(geo, c, s));
				}
			} else {
				hatPart(poseStack, collector, model, light, overlay, helmet, c, ps -> {
				}, geo -> Headwear.hat(geo, c, time));
			}
		}
		if ((c.bandana.get() == Cosmetics.BandanaStyle.SCARF || c.bandana.get() == Cosmetics.BandanaStyle.NECKERCHIEF) && model.body.visible) {
			part(poseStack, collector, model, model.body, light, overlay, !state.chestEquipment.isEmpty(),
				geo -> Headwear.bodyBandana(geo, c, time, moving));
		}
	}

	/**
	 * The hat on the head, moved by the fit settings: height, forward, size (around the top of the head) and tilt,
	 * then {@code extra} (per-ear pose for cat ears).
	 */
	private static void hatPart(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, int light, int overlay, boolean grow,
								Cosmetics c, java.util.function.Consumer<PoseStack> extra, Drawer drawer) {
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		model.head.translateAndRotate(poseStack);
		if (grow) {
			poseStack.scale(1.14f, 1.14f, 1.14f);
		}
		// Pivot at the top centre of the head (y = -8 px).
		poseStack.translate(0, -8 / 16f, 0);
		poseStack.translate(0, -c.hatHeight.floatValue() / 16f, -c.hatForward.floatValue() / 16f);
		poseStack.rotate(com.mojang.math.Axis.XP, (float) Math.toRadians(-c.hatTilt.floatValue()));
		float size = c.hatSize.floatValue();
		poseStack.scale(size, size, size);
		poseStack.translate(0, 8 / 16f, 0);
		extra.accept(poseStack);
		collector.submitCustomGeometry(poseStack, Geo.renderType(), (pose, buffer) -> drawer.draw(new Geo(pose, buffer, light, overlay)));
		poseStack.popPose();
	}

	private static void part(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, ModelPart part, int light, int overlay,
							 boolean grow, Drawer drawer) {
		poseStack.pushPose();
		model.root().translateAndRotate(poseStack);
		part.translateAndRotate(poseStack);
		if (grow) {
			poseStack.scale(1.14f, 1.14f, 1.14f);
		}
		collector.submitCustomGeometry(poseStack, Geo.renderType(), (pose, buffer) -> drawer.draw(new Geo(pose, buffer, light, overlay)));
		poseStack.popPose();
	}
}
