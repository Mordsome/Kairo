package dev.mordo.kairo.mixin;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.gui.main.MainMenuSettingsScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.FriendsButton;
import net.minecraft.client.gui.components.SpriteIconButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Minecraft's own title screen (when the player picked that style): a way back to the main menu settings, and
 * optionally without the small icon row (friends, language, accessibility) under Realms. When that row goes, the
 * Options / Quit row moves up into its place.
 */
@Mixin(TitleScreen.class)
abstract class TitleScreenMixin extends Screen {
	protected TitleScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void kairo$settingsButton(CallbackInfo ci) {
		TitleScreen self = (TitleScreen) (Object) this;
		if (MainMenuSettings.INSTANCE.hideTitleIcons.isOn()) {
			kairo$hideIcons();
		}
		addRenderableWidget(Button.builder(Component.literal(ClientInfo.NAME + " menu"),
				b -> Minecraft.getInstance().gui.setScreen(new MainMenuSettingsScreen(self)))
			.bounds(width - 96, 6, 90, 20)
			.build());
	}

	private void kairo$hideIcons() {
		List<AbstractWidget> icons = new ArrayList<>();
		List<AbstractWidget> others = new ArrayList<>();
		for (GuiEventListener child : List.copyOf(children())) {
			if (child instanceof SpriteIconButton || child instanceof FriendsButton) {
				icons.add((AbstractWidget) child);
			} else if (child instanceof AbstractWidget widget) {
				others.add(widget);
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
			removeWidget(icon);
		}
		// Only the row right under the icons (Options / Quit) moves up; the copyright line at the bottom stays.
		int nextTop = Integer.MAX_VALUE;
		for (AbstractWidget widget : others) {
			if (widget.getY() >= rowBottom && widget.getY() < rowBottom + 30) {
				nextTop = Math.min(nextTop, widget.getY());
			}
		}
		if (nextTop == Integer.MAX_VALUE) {
			return;
		}
		int step = nextTop - rowTop;
		for (AbstractWidget widget : others) {
			if (widget.getY() >= nextTop && widget.getY() < nextTop + 20) {
				widget.setY(widget.getY() - step);
			}
		}
	}
}
