package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.SelfLook;
import dev.mordo.kairo.cosmetics.Cosmetics;
import dev.mordo.kairo.cosmetics.Geo;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Kairo Void "Your look": an outline (Minecraft's own glowing outline, in your colour), a glow (a soft shell of
 * light round your model) or a hologram (your skin as see-through light with scan lines). Only ever on your own
 * player, wherever it's drawn: F5, the inventory and the main menu.
 */
public final class VoidLook {
	private static final Map<Identifier, RenderType> HOLOGRAM_TYPES = new HashMap<>();

	private VoidLook() {
	}

	private static SelfLook look(AvatarRenderState state) {
		if (!Visuals.on() || !Cosmetics.isLocal(state) || state.isInvisible) {
			return SelfLook.NONE;
		}
		return VoidSettings.INSTANCE.selfLook.get();
	}

	private static int color() {
		VoidSettings.FxColor mode = VoidSettings.INSTANCE.auraColor.get();
		return GlowGeo.color(mode, 0.3f, System.nanoTime() / 1e9f) & 0xFFFFFF;
	}

	/** After the render state is filled in: the outline. */
	public static void extract(AvatarRenderState state) {
		if (look(state) == SelfLook.OUTLINE) {
			state.outlineColor = color();
		}
	}

	public static boolean hologram(AvatarRenderState state) {
		return look(state) == SelfLook.HOLOGRAM;
	}

	/** The hologram render type for this skin. */
	public static RenderType hologramType(Identifier skin) {
		return HOLOGRAM_TYPES.computeIfAbsent(skin, id -> RenderType.create("kairo_hologram",
			RenderSetup.builder(KairoPipelines.HOLOGRAM).withTexture("Sampler0", id).createRenderSetup()));
	}

	/** The hologram's colour, carried by the model tint. */
	public static int hologramTint() {
		return 0xFF000000 | color();
	}

	/** Glow: the model again, a little bigger, as a soft flat shell of light. */
	public static void glowShell(PoseStack poseStack, SubmitNodeCollector collector, PlayerModel model, AvatarRenderState state, int light, int overlay) {
		if (look(state) != SelfLook.GLOW) {
			return;
		}
		float breathe = Animation.reduceMotion() ? 0 : 0.012f * (float) Math.sin(state.ageInTicks * 0.12);
		poseStack.pushPose();
		// The model's origin is at the neck: grow round the middle of the body instead.
		poseStack.translate(0, 0.75f, 0);
		poseStack.scale(1.07f + breathe, 1.05f + breathe, 1.07f + breathe);
		poseStack.translate(0, -0.75f, 0);
		int tint = Colors.withAlpha(0xFF000000 | color(), 0.28f);
		collector.submitModel(model, state, poseStack, Geo.glowType(), 0xF000F0, overlay, tint, null, EntityRenderState.NO_OUTLINE);
		poseStack.popPose();
	}
}
