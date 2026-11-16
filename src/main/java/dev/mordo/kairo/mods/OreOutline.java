package dev.mordo.kairo.mods;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.ArrayList;
import java.util.List;

/**
 * Ore Outline: a thin outline in each ore's own colour around ores you can already see, like an outlined-ores
 * resource pack. No glow, nothing through walls: only ores with a face open to air (or water, glass...) are outlined,
 * and the outline is drawn like Minecraft's own block outline, so blocks in front hide it just as they hide the ore.
 * Some servers have rules even about this kind of pack: check them before you use it.
 */
public final class OreOutline extends Module {
	public enum Which implements EnumSetting.Labeled {
		ALL("All ores"),
		VALUABLE("Diamonds, emeralds, gold and debris");

		private final String label;

		Which(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final EnumSetting<Which> which = add(new EnumSetting<>("which", "Ores", Which.ALL));
	public final SliderSetting range = add(new SliderSetting("range", "Range", 16, 6, 32, 1).unit(" blocks")
		.description("How far from you ores are outlined"));
	public final SliderSetting width = add(new SliderSetting("width", "Line thickness", 1.0, 0.5, 3.0, 0.25).multiplier());

	private record Ore(BlockPos pos, int color) {
	}

	/** Ores found by the last look around (refreshed a few times a second, never per frame). */
	private volatile List<Ore> found = List.of();
	private int ticks;

	public OreOutline() {
		super("ore_outline", "Ore Outline", "A thin outline round ores you can see, in each ore's colour (no glow, nothing through walls)",
			Category.VISUAL, Icon.CUBE, false);
	}

	@Override
	public void init() {
		LevelRenderEvents.COLLECT_SUBMITS.register(this::submit);
	}

	/** The outline colour for an ore block, or 0 when it isn't one (or isn't wanted). */
	private int colorOf(BlockState state) {
		String id = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
		boolean valuableOnly = which.get() == Which.VALUABLE;
		if (id.equals("ancient_debris")) {
			return 0xFFB5764A;
		}
		if (!id.endsWith("_ore")) {
			return 0;
		}
		if (id.contains("diamond")) {
			return 0xFF4DF0E6;
		}
		if (id.contains("emerald")) {
			return 0xFF3DE36B;
		}
		if (id.contains("gold")) {
			return 0xFFFFD54A;
		}
		if (valuableOnly) {
			return 0;
		}
		if (id.contains("iron")) {
			return 0xFFE3B79A;
		}
		if (id.contains("copper")) {
			return 0xFFE8834F;
		}
		if (id.contains("redstone")) {
			return 0xFFFF3B3B;
		}
		if (id.contains("lapis")) {
			return 0xFF3D6BFF;
		}
		if (id.contains("quartz")) {
			return 0xFFF2EDE4;
		}
		if (id.contains("coal")) {
			return 0xFF5A5A5A;
		}
		return 0xFFD0D0D0;
	}

	@Override
	public void tick() {
		if (++ticks % 5 != 0) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null || mc.player == null) {
			found = List.of();
			return;
		}
		int r = Math.round(range.floatValue());
		BlockPos centre = mc.player.blockPosition();
		List<Ore> ores = new ArrayList<>();
		BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
		BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
		for (int dx = -r; dx <= r; dx++) {
			for (int dy = -r; dy <= r; dy++) {
				for (int dz = -r; dz <= r; dz++) {
					if (dx * dx + dy * dy + dz * dz > r * r) {
						continue;
					}
					at.set(centre.getX() + dx, centre.getY() + dy, centre.getZ() + dz);
					BlockState state = level.getBlockState(at);
					int color = colorOf(state);
					if (color == 0) {
						continue;
					}
					// Only ores you could see: at least one face open to something that doesn't hide it.
					boolean open = false;
					for (Direction side : Direction.values()) {
						next.setWithOffset(at, side);
						if (!level.getBlockState(next).canOcclude()) {
							open = true;
							break;
						}
					}
					if (open) {
						ores.add(new Ore(at.immutable(), color));
					}
				}
			}
		}
		found = ores;
	}

	@Override
	protected void onDisable() {
		found = List.of();
	}

	private void submit(LevelRenderContext context) {
		List<Ore> ores = found;
		if (!isEnabled() || ores.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		Vec3 camera = context.levelState().cameraRenderState.pos;
		PoseStack poseStack = context.poseStack();
		// The same lines as Minecraft's block outline, so they are hidden by whatever is in front, like the ore itself.
		RenderType type = mc.gameRenderer.useImprovedTransparency() ? RenderTypes.linesTranslucentNoDepthWrite() : RenderTypes.linesTranslucent();
		float line = mc.gameRenderer.gameRenderState().windowRenderState.appropriateLineWidth * width.floatValue();
		VoxelShape cube = net.minecraft.world.phys.shapes.Shapes.block();
		for (Ore ore : ores) {
			poseStack.pushPose();
			poseStack.translate(ore.pos().getX() - camera.x, ore.pos().getY() - camera.y, ore.pos().getZ() - camera.z);
			context.submitNodeCollector().submitShapeOutline(poseStack, cube, type, ore.color(), line, false);
			poseStack.popPose();
		}
	}
}
