package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mordo.kairo.core.GameVisuals;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.ItemEntityRenderState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.phys.AABB;

/**
 * Item physics (Visuals): dropped items lie flat on the ground (blocks sit on it), each turned its own way, and
 * tumble while they fall, instead of bobbing and spinning in the air. Plus an optional glow under rare items.
 * Only how they're drawn changes; where they are and how you pick them up stay vanilla.
 */
public final class ItemPhysics {
	/** What the renderer remembers about each dropped item (added to its render state). */
	public interface State {
		void kairo$setPhysics(boolean onGround, int rarityColor);

		boolean kairo$onGround();

		int kairo$rarityColor();
	}

	private ItemPhysics() {
	}

	public static boolean on() {
		return Visuals.live() && GameVisuals.INSTANCE.itemPhysics.isOn();
	}

	public static void extract(ItemEntity entity, ItemEntityRenderState state) {
		Rarity rarity = entity.getItem().getRarity();
		// The usual rarity colours: yellow, aqua, light purple.
		int color = switch (rarity) {
			case UNCOMMON -> 0xFFFFFF55;
			case RARE -> 0xFF55FFFF;
			case EPIC -> 0xFFFF55FF;
			default -> 0;
		};
		((State) state).kairo$setPhysics(entity.onGround() || entity.isInWater(), color);
	}

	private static boolean flat(ItemEntityRenderState state) {
		return state.item.getModelBoundingBox().getZsize() <= 0.0625;
	}

	/** Replaces the hover-and-bob lift. */
	public static void lift(PoseStack ps, float x, float y, float z, ItemEntityRenderState state) {
		if (!on()) {
			ps.translate(x, y, z);
			return;
		}
		AABB box = state.item.getModelBoundingBox();
		if (flat(state)) {
			// Lying down, the stack's depth becomes its height.
			float depth = (float) box.getZsize();
			float stack = depth * 1.5f * Math.max(0, state.count - 1) / 2f;
			ps.translate(0, 0.015f + depth / 2 + stack, 0);
		} else {
			ps.translate(0, (float) -box.minY, 0);
		}
	}

	/** Replaces the endless spin: a fixed turn of its own, tumbling while in the air. */
	public static void turn(PoseStack ps, Axis axis, float angle, ItemEntityRenderState state) {
		if (!on()) {
			ps.rotate(axis, angle);
			return;
		}
		ps.rotate(Axis.YP, state.bobOffset * 3.7f);
		if (!((State) state).kairo$onGround()) {
			ps.rotate(Axis.XP, state.ageInTicks * 0.35f);
			ps.rotate(Axis.ZP, state.ageInTicks * 0.2f);
		}
		if (flat(state)) {
			ps.rotate(Axis.XP, (float) (Math.PI / 2));
		}
	}

	/** A soft glow on the ground under rare items. */
	public static void glow(ItemEntityRenderState state, PoseStack ps, SubmitNodeCollector collector) {
		int color = ((State) state).kairo$rarityColor();
		if (color == 0 || !Visuals.live() || !GameVisuals.INSTANCE.rareItemGlow.isOn() || state.item.isEmpty()) {
			return;
		}
		float pulse = 0.85f + 0.15f * (float) Math.sin(state.ageInTicks * 0.12f + state.bobOffset);
		CrystalFx.groundGlow(ps, collector, 0.5f * pulse, color, 0.55f);
	}
}
