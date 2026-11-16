package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.cosmetics.Geo;
import dev.mordo.kairo.mixin.ItemStackRenderStateAccessor;
import dev.mordo.kairo.mixin.LayerRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Vector3f;

/**
 * Kairo Void's weapon aura: rings of light and little runes turning round the blade of the sword, axe or mace in your
 * main hand (first person). They show your attack cooldown, and only that (the same thing the crosshair indicator
 * shows): the rings draw themselves closed and warm from grey to your colour as the attack charges, and when it's
 * ready lights run round them. Drawn in the item's own space, so it follows every hand animation.
 */
public final class WeaponAura {
	private WeaponAura() {
	}

	public static boolean wanted(ItemStack stack) {
		return Visuals.on() && VoidSettings.INSTANCE.weaponAura.isOn()
			&& (stack.is(ItemTags.SWORDS) || stack.is(ItemTags.AXES) || stack.is(Items.MACE));
	}

	/** Draws the aura for {@code item}, about to be drawn with {@code poseStack}. */
	public static void draw(PoseStack poseStack, SubmitNodeCollector collector, ItemStackRenderState item) {
		var access = (ItemStackRenderStateAccessor) (Object) item;
		var layers = access.kairo$layers();
		if (access.kairo$activeLayerCount() < 1 || layers.length < 1) {
			return;
		}
		var layer = (LayerRenderStateAccessor) (Object) layers[0];
		if (layer.kairo$specialRenderer() != null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		float charge = mc.player == null ? 1 : mc.player.getAttackStrengthScale(0);
		float t = (System.nanoTime() / 1e9f) % 3600 * (Animation.reduceMotion() ? 0.5f : 1f);
		VoidSettings s = VoidSettings.INSTANCE;
		int accent = GlowGeo.color(s.actionColor.get(), 0.3f, t);
		int col = Colors.lerp(0xFF8A93A0, accent, charge * charge);
		int bright = Colors.lerp(col, 0xFFFFFFFF, 0.55f);
		boolean ready = charge >= 0.999f;
		poseStack.pushPose();
		layer.kairo$itemTransform().apply(access.kairo$displayContext().leftHand(), poseStack.last());
		poseStack.last().mulPose(layer.kairo$localTransform());
		collector.submitCustomGeometry(poseStack, Geo.glowType(), (pose, b) -> {
			// The item's picture lies in the x-y plane (0..1), the blade running up the diagonal. The rings go round it.
			float d = 0.70710677f;
			Vector3f across = new Vector3f(d, -d, 0);
			Vector3f deep = new Vector3f(0, 0, 1);
			float[] where = {0.52f, 0.7f, 0.86f};
			float[] size = {0.2f, 0.16f, 0.11f};
			for (int i = 0; i < where.length; i++) {
				Vector3f c = new Vector3f(where[i], where[i], 0.5f);
				double turn = t * (1.4 + i * 0.5) * (i % 2 == 0 ? 1 : -1);
				// Each ring draws itself closed as the attack charges.
				double sweep = Math.PI * 2 * Math.clamp(charge * 1.15f - i * 0.05f, 0.05f, 1f);
				GlowGeo.arc(pose, b, c, across, deep, size[i], 0.016f, turn, turn + sweep, Colors.withAlpha(col, 0.85f));
				// Runes: short ticks standing out from the ring.
				int ticks = 5 + i;
				for (int k = 0; k < ticks; k++) {
					double a = turn + k * Math.PI * 2 / ticks;
					if ((a - turn) > sweep) {
						continue;
					}
					Vector3f from = GlowGeo.around(c, across, deep, size[i] * 1.08f, a);
					Vector3f to = GlowGeo.around(c, across, deep, size[i] * (k % 2 == 0 ? 1.34f : 1.22f), a);
					GlowGeo.flatLine(pose, b, from, to, new Vector3f(d, d, 0), 0.012f, Colors.withAlpha(bright, 0.8f), Colors.withAlpha(col, 0.1f));
				}
				if (ready) {
					// Ready: a light runs round each ring.
					Vector3f bead = GlowGeo.around(c, across, deep, size[i], -turn * 1.7 + i);
					GlowGeo.disc(pose, b, bead, across, new Vector3f(d, d, 0), 0.035f, Colors.withAlpha(bright, 0.95f), 8);
					GlowGeo.disc(pose, b, bead, deep, new Vector3f(d, d, 0), 0.035f, Colors.withAlpha(bright, 0.95f), 8);
				}
			}
		});
		poseStack.popPose();
	}
}
