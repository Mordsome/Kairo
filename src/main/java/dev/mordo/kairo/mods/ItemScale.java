package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;

/**
 * Smaller (or bigger) items in your hands, and where they sit, so they cover less of the screen. First person
 * only; purely visual.
 */
public final class ItemScale extends Module {
	public final SliderSetting mainHand = add(new SliderSetting("main_hand", "Main hand size", 0.7, 0.3, 1.5, 0.05).multiplier());
	public final SliderSetting offHand = add(new SliderSetting("off_hand", "Offhand size", 0.7, 0.3, 1.5, 0.05).multiplier()
		.description("Totems, shields, maps in the other hand"));
	public final SliderSetting sideways = add(new SliderSetting("x", "Sideways", 0.0, -0.4, 0.4, 0.01)
		.description("Positive moves items towards the screen edge"));
	public final SliderSetting height = add(new SliderSetting("y", "Height", 0.0, -0.4, 0.4, 0.01));
	public final SliderSetting depth = add(new SliderSetting("z", "Depth", 0.0, -0.4, 0.4, 0.01)
		.description("Positive pushes items further away"));

	public ItemScale() {
		super("item_scale", "Item Scale", "Smaller items in your hands", Category.VISUAL, Icon.SWORD, false);
	}

	public float scaleFor(InteractionHand hand) {
		return (hand == InteractionHand.MAIN_HAND ? mainHand : offHand).floatValue();
	}

	/** +1 when the hand is drawn on the right of the screen, -1 on the left. */
	public static float side(InteractionHand hand) {
		Minecraft mc = Minecraft.getInstance();
		HumanoidArm main = mc.player != null ? mc.player.getMainArm() : HumanoidArm.RIGHT;
		HumanoidArm arm = hand == InteractionHand.MAIN_HAND ? main : main.getOpposite();
		return arm == HumanoidArm.RIGHT ? 1 : -1;
	}
}
