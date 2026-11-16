package dev.mordo.kairo.mods;

import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Reconnect: a button on Minecraft's "Disconnected" screen (kicked, timed out, server closed, couldn't connect) that
 * joins the same server again, exactly as picking it in the server list would. Optionally it presses itself after a
 * countdown; that is off by default, because some servers don't allow rejoining automatically.
 */
public final class Reconnect extends Module {
	public final BooleanSetting auto = add(new BooleanSetting("auto", "Reconnect by itself", false)
		.description("Presses Reconnect for you after a countdown. Off by default: some servers don't allow rejoining automatically, so check their rules"));
	public final SliderSetting seconds = add(new SliderSetting("seconds", "Countdown", 10, 3, 60, 1).unit(" s")
		.visibleWhen(() -> auto.isOn()));

	/** The server last connected to (also when the connection never got through). */
	private static @Nullable ServerData last;

	public Reconnect() {
		super("reconnect", "Reconnect", "A Reconnect button when you get disconnected from a server", Category.UTILITY, Icon.REFRESH, true);
	}

	/** From ConnectScreenMixin: a connection to this server is starting. */
	public static void remember(@Nullable ServerData server) {
		last = server;
	}

	@Override
	public void init() {
		ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
			ServerData server = last;
			if (!(screen instanceof DisconnectedScreen) || !isEnabled() || server == null) {
				return;
			}
			// Under the lowest button Minecraft put there (Back to server list), the same size.
			AbstractWidget lowest = null;
			for (AbstractWidget widget : Screens.getWidgets(screen)) {
				if (lowest == null || widget.getY() + widget.getHeight() > lowest.getY() + lowest.getHeight()) {
					lowest = widget;
				}
			}
			int w = lowest != null ? lowest.getWidth() : 200;
			int x = lowest != null ? lowest.getX() : (width - w) / 2;
			int y = lowest != null ? lowest.getY() + lowest.getHeight() + 4 : height / 2 + 40;
			Button button = Button.builder(Component.literal("Reconnect"), b -> connect(client, server)).bounds(x, y, w, 20).build();
			Screens.getWidgets(screen).add(button);
			if (auto.isOn()) {
				long opened = System.currentTimeMillis();
				boolean[] done = {false};
				ScreenEvents.afterTick(screen).register(s -> {
					if (done[0] || !auto.isOn()) {
						button.setMessage(Component.literal("Reconnect"));
						return;
					}
					long left = Math.round(seconds.value()) - (System.currentTimeMillis() - opened) / 1000;
					if (left <= 0) {
						done[0] = true;
						connect(client, server);
					} else {
						button.setMessage(Component.literal("Reconnect (" + left + ")"));
					}
				});
			}
		});
	}

	private static void connect(Minecraft client, ServerData server) {
		Screen back = new JoinMultiplayerScreen(new TitleScreen());
		ConnectScreen.startConnecting(back, client, ServerAddress.parseString(server.ip), server, false, null);
	}
}
