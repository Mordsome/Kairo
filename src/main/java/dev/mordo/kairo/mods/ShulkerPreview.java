package dev.mordo.kairo.mods;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.mods.shulker.ShulkerContents;
import dev.mordo.kairo.mods.shulker.ShulkerTooltip;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Shows a shulker box's contents inside its tooltip, and above the hotbar while holding Shift. */
public final class ShulkerPreview extends Module {
	public final BooleanSetting compact = add(new BooleanSetting("compact", "Compact", false)
		.description("Merge identical stacks"));
	public final BooleanSetting hotbarWithShift = add(new BooleanSetting("hotbar_shift", "Hold Shift in hotbar", true)
		.description("Preview the held box above the hotbar while holding Shift"));

	public ShulkerPreview() {
		super("shulker_preview", "Shulker Preview", "See box contents in tooltips", Category.UTILITY, Icon.BOX, true);
	}

	@Override
	public void init() {
		ClientTooltipComponentCallback.EVENT.register(data -> data instanceof ShulkerContents contents ? new ShulkerTooltip(contents) : null);
		HudElementRegistry.addLast(ClientInfo.id("shulker_hotbar"), (g, delta) -> drawHotbarPreview(g));
	}

	/** Tooltip data for a stack, or null to leave the tooltip alone. */
	public @Nullable ShulkerContents tooltipFor(net.minecraft.world.item.ItemStack stack) {
		return isEnabled() ? ShulkerContents.of(stack, compact.isOn()) : null;
	}

	private void drawHotbarPreview(GuiGraphicsExtractor g) {
		Minecraft mc = Minecraft.getInstance();
		if (!isEnabled() || !hotbarWithShift.isOn() || mc.player == null || mc.gui.screen() != null || mc.gui.hud.isHidden()) {
			return;
		}
		boolean shift = dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_LSHIFT) || dev.mordo.kairo.core.keybinds.InputTracker.isKeyDown(InputConstants.KEY_RSHIFT);
		if (!shift) {
			return;
		}
		ShulkerContents contents = ShulkerContents.of(mc.player.getMainHandItem(), compact.isOn());
		if (contents == null) {
			return;
		}
		ShulkerTooltip tooltip = new ShulkerTooltip(contents);
		int w = tooltip.getWidth(mc.font);
		int h = tooltip.getHeight(mc.font);
		int x = (g.guiWidth() - w) / 2;
		int y = g.guiHeight() - 58 - h;
		tooltip.draw(g, mc.font, x, y);
	}
}
