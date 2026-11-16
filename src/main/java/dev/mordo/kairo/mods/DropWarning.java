package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Toasts;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;

import java.util.Set;

/**
 * Drop Warning: dropping something valuable (netherite, diamonds, golden apples, totems, elytra, shulker boxes,
 * enchanted gear...) asks first. The first drop is held back with a warning; dropping the same thing again within a
 * few seconds really drops it. Works for the drop key, Q over a slot, and throwing an item out of the inventory.
 * Only your own clicks are held back: nothing is sent, nothing is automated.
 */
public final class DropWarning extends Module {
	public final BooleanSetting netheriteDiamond = add(new BooleanSetting("netherite_diamond", "Netherite and diamonds", true)
		.description("Netherite and diamond items, gear and blocks, and ancient debris"));
	public final BooleanSetting food = add(new BooleanSetting("golden_food", "Golden apples and totems", true)
		.description("Golden apples, enchanted golden apples, golden carrots and totems of undying"));
	public final BooleanSetting rare = add(new BooleanSetting("rare", "Rare items", true)
		.description("Elytra, shulker boxes, beacons, nether stars, tridents, maces, heavy cores, dragon eggs and heads, end crystals"));
	public final BooleanSetting enchanted = add(new BooleanSetting("enchanted", "Anything enchanted", true)
		.description("Every enchanted item, including enchanted books"));
	public final SliderSetting window = add(new SliderSetting("window", "Drop again within", 3, 1, 8, 0.5).unit(" s")
		.description("How long you have to drop it a second time"));

	private static final Set<String> FOOD = Set.of("golden_apple", "enchanted_golden_apple", "golden_carrot", "totem_of_undying");
	private static final Set<String> RARE = Set.of("elytra", "beacon", "nether_star", "trident", "mace", "heavy_core", "dragon_egg",
		"dragon_head", "end_crystal", "wither_skeleton_skull", "conduit", "heart_of_the_sea", "enchanted_book", "netherite_upgrade_smithing_template");

	private String pendingKey = "";
	private long pendingAt;

	public DropWarning() {
		super("drop_warning", "Drop Warning", "Asks before you drop netherite, diamonds, golden apples and other valuables",
			Category.UTILITY, Icon.INFO, true);
	}

	/** Whether this stack is worth a warning, by the categories switched on. */
	public boolean valuable(ItemStack stack) {
		if (stack.isEmpty()) {
			return false;
		}
		String id = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
		if (netheriteDiamond.isOn() && (id.contains("netherite") || id.contains("diamond") || id.equals("ancient_debris"))) {
			return true;
		}
		if (food.isOn() && FOOD.contains(id)) {
			return true;
		}
		if (rare.isOn() && (RARE.contains(id) || id.endsWith("shulker_box"))) {
			return true;
		}
		return enchanted.isOn() && (stack.isEnchanted() || id.equals("enchanted_book"));
	}

	/**
	 * Called before a drop. True when it may go ahead; false when it's held back this time (and the warning shows).
	 * A second drop of the same kind of item within the window goes through.
	 */
	public boolean allow(ItemStack stack) {
		if (!isEnabled() || !valuable(stack)) {
			return true;
		}
		String key = BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + stack.getCount();
		long now = System.currentTimeMillis();
		if (key.equals(pendingKey) && now - pendingAt <= window.value() * 1000) {
			pendingKey = "";
			return true;
		}
		pendingKey = key;
		pendingAt = now;
		Toasts.show("Drop " + stack.getHoverName().getString() + "? Drop again to confirm", 0xFFFFB347);
		dev.mordo.kairo.sound.UiSounds.click();
		return false;
	}
}
