package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ClientSettings;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * "Hide extra pause buttons": removes the icon row (bug report, feedback, friends, player reporting) and moves
 * everything above it down by one row, so Options and Save and Quit stay exactly where they were.
 */
@Mixin(PauseScreen.class)
abstract class PauseScreenMixin extends Screen {
	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void kairo$hideExtras(CallbackInfo ci) {
		if (!ClientSettings.INSTANCE.hidePauseExtras.isOn()) {
			return;
		}
		List<AbstractWidget> widgets = new ArrayList<>();
		List<AbstractWidget> icons = new ArrayList<>();
		for (GuiEventListener child : List.copyOf(children())) {
			if (child instanceof AbstractWidget widget) {
				if (child instanceof SpriteIconButton || child instanceof FriendsButton) {
					icons.add(widget);
				} else {
					widgets.add(widget);
				}
			}
		}
		if (icons.isEmpty()) {
			return;
		}
		int rowTop = Integer.MAX_VALUE;
		int rowBottom = Integer.MIN_VALUE;
		for (AbstractWidget icon : icons) {
			rowTop = Math.min(rowTop, icon.getY());
			rowBottom = Math.max(rowBottom, icon.getBottom());
		}
		// One row = from the top of the icon row to the top of the row below it.
		int nextTop = Integer.MAX_VALUE;
		for (AbstractWidget widget : widgets) {
			if (widget.getY() >= rowBottom) {
				nextTop = Math.min(nextTop, widget.getY());
			}
		}
		int step = nextTop == Integer.MAX_VALUE ? rowBottom - rowTop + 4 : nextTop - rowTop;
		for (AbstractWidget icon : icons) {
			removeWidget(icon);
		}
		for (AbstractWidget widget : widgets) {
			if (widget.getBottom() <= rowTop) {
				widget.setY(widget.getY() + step);
			}
		}
	}
}
