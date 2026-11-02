package dev.mordo.kairo.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Colors;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The outline around the block you're looking at: colour, thickness, rainbow, and an optional see-through fill. */
public final class BlockOutline extends Module {
	public final BooleanSetting accentColor = add(new BooleanSetting("accent_color", "Use accent color", true)
		.description("Follows the client accent (orange in PvP mode)"));
	public final ColorSetting color = add(new ColorSetting("color", "Outline color", 0xE6FFFFFF).accentable()
		.visibleWhen(() -> !accentColor.isOn()));
	public final SliderSetting width = add(new SliderSetting("width", "Thickness", 1.5, 0.5, 4, 0.25).multiplier());
	public final BooleanSetting rainbow = add(new BooleanSetting("rainbow", "Rainbow", false)
		.description("The colour cycles through the rainbow"));
	public final BooleanSetting fill = add(new BooleanSetting("fill", "Fill", true)
		.description("A see-through overlay on the block"));
	public final SliderSetting fillOpacity = add(new SliderSetting("fill_opacity", "Fill opacity", 0.14, 0.02, 0.6, 0.01).percent()
		.visibleWhen(fill::isOn));
	public final BooleanSetting pulse = add(new BooleanSetting("pulse", "Pulse", false)
		.description("The fill breathes in and out")
		.visibleWhen(fill::isOn));

	public BlockOutline() {
		super("block_outline", "Block Outline", "Colour, thickness and a fill for the block you look at",
			Category.VISUAL, Icon.CUBE, true);
	}

	/** The outline colour right now (accent, custom or rainbow), as ARGB. */
	public int outlineColor() {
		if (rainbow.isOn()) {
			float hue = (System.currentTimeMillis() % 4000L) / 4000f;
			return Colors.withAlpha(Colors.hsv(hue, 0.7f, 1f), 0.95f);
		}
		return accentColor.isOn() ? Colors.withAlpha(Theme.modeAccent(), 0.95f) : color.argb();
	}

	public int fillColor() {
		float alpha = fillOpacity.floatValue();
		if (pulse.isOn()) {
			alpha *= 0.55f + 0.45f * (float) (Math.sin(System.currentTimeMillis() / 1000.0 * 3.2) * 0.5 + 0.5);
		}
		return Colors.withAlpha(outlineColor(), alpha);
	}

	/** Submits the see-through fill for every box of the shape (in block-local coordinates). */
	public void submitFill(PoseStack poseStack, SubmitNodeCollector collector, VoxelShape shape) {
		int argb = fillColor();
		RenderType type = RenderTypes.debugFilledBox();
		collector.submitCustomGeometry(poseStack, type, (pose, buffer) -> {
			for (AABB box : shape.toAabbs()) {
				box(pose, buffer, box.inflate(0.002), argb);
			}
		});
	}

	private static void box(PoseStack.Pose pose, VertexConsumer b, AABB a, int argb) {
		float x0 = (float) a.minX;
		float y0 = (float) a.minY;
		float z0 = (float) a.minZ;
		float x1 = (float) a.maxX;
		float y1 = (float) a.maxY;
		float z1 = (float) a.maxZ;
		// Each face both ways round, so it shows whatever the culling.
		quad(pose, b, argb, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
		quad(pose, b, argb, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
		quad(pose, b, argb, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
		quad(pose, b, argb, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
		quad(pose, b, argb, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
		quad(pose, b, argb, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
	}

	private static void quad(PoseStack.Pose pose, VertexConsumer b, int argb,
							 float ax, float ay, float az, float bx, float by, float bz,
							 float cx, float cy, float cz, float dx, float dy, float dz) {
		b.addVertex(pose, ax, ay, az).setColor(argb);
		b.addVertex(pose, bx, by, bz).setColor(argb);
		b.addVertex(pose, cx, cy, cz).setColor(argb);
		b.addVertex(pose, dx, dy, dz).setColor(argb);
		b.addVertex(pose, dx, dy, dz).setColor(argb);
		b.addVertex(pose, cx, cy, cz).setColor(argb);
		b.addVertex(pose, bx, by, bz).setColor(argb);
		b.addVertex(pose, ax, ay, az).setColor(argb);
	}
}
