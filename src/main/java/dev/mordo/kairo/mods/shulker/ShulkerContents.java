package dev.mordo.kairo.mods.shulker;

import net.minecraft.core.component.DataComponents;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.level.block.ShulkerBoxBlock;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * What a shulker box item holds, read from its container data component (the same data vanilla shows
 * as text in the tooltip). Used as tooltip data so it can be drawn as a grid.
 */
public record ShulkerContents(List<ItemStack> items, int tint, boolean compact) implements TooltipComponent {
	/** Default purple for undyed boxes. */
	private static final int UNDYED = 0xFF8E6A8E;

	public static boolean isShulkerBox(ItemStack stack) {
		return stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof ShulkerBoxBlock;
	}

	/** Null if the stack isn't a shulker box or is empty. */
	public static @Nullable ShulkerContents of(ItemStack stack, boolean compact) {
		if (!isShulkerBox(stack)) {
			return null;
		}
		ItemContainerContents container = stack.get(DataComponents.CONTAINER);
		if (container == null) {
			return null;
		}
		List<ItemStack> items = new ArrayList<>(27);
		container.itemCopies().forEach(items::add);
		while (items.size() < 27) {
			items.add(ItemStack.EMPTY);
		}
		if (items.stream().allMatch(ItemStack::isEmpty)) {
			return null;
		}
		if (compact) {
			items = merge(items);
		}
		DyeColor color = ((ShulkerBoxBlock) ((BlockItem) stack.getItem()).getBlock()).getColor();
		int tint = color == null ? UNDYED : 0xFF000000 | color.getTextureDiffuseColor();
		return new ShulkerContents(items, tint, compact);
	}

	/** Identical stacks combined into one entry with the total count. */
	private static List<ItemStack> merge(List<ItemStack> items) {
		List<ItemStack> merged = new ArrayList<>();
		outer:
		for (ItemStack stack : items) {
			if (stack.isEmpty()) {
				continue;
			}
			for (ItemStack existing : merged) {
				if (ItemStack.isSameItemSameComponents(existing, stack)) {
					existing.setCount(existing.getCount() + stack.getCount());
					continue outer;
				}
			}
			merged.add(stack.copy());
		}
		return merged;
	}
}
