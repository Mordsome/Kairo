package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.render.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;

/** Your own latency to the server (as shown in the tab list), coloured from green to red. */
public final class PingHud extends HudElement {
	public final BooleanSetting showLabel = add(new BooleanSetting("show_label", "Show label", true));
	public final BooleanSetting colorByValue = add(new BooleanSetting("color_by_value", "Color by latency", true));

	private static final int[] STEPS = {50, 100, 150, 250};
	private static final int[] COLORS = {0xFF55FF7F, 0xFFB6F25A, 0xFFFFD45C, 0xFFFF9A52, 0xFFFF5555};

	private int ping = -1;
	private String text = "0";

	public PingHud() {
		super("ping", "Ping", "Colored by latency", Icon.SIGNAL, true, Anchor.TOP_RIGHT, -0.004, 0.008);
	}

	private static int currentPing() {
		Minecraft mc = Minecraft.getInstance();
		ClientPacketListener connection = mc.getConnection();
		if (connection == null || mc.player == null) {
			return 0;
		}
		PlayerInfo info = connection.getPlayerInfo(mc.player.getUUID());
		return info == null ? 0 : info.getLatency();
	}

	@Override
	protected void layout(boolean preview) {
		int current = preview ? 24 : currentPing();
		if (current != ping) {
			ping = current;
			text = Integer.toString(ping);
		}
		width = HudText.width(text) + HudText.width(showLabel.isOn() ? " ms" : "ms") + pad() * 2;
		height = HudText.LINE + pad() * 2;
	}

	private int color() {
		if (!colorByValue.isOn()) {
			return valueColor.argb();
		}
		for (int i = 0; i < STEPS.length; i++) {
			if (ping < STEPS[i]) {
				return COLORS[i];
			}
		}
		return COLORS[COLORS.length - 1];
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		drawBackground(g);
		float after = HudText.draw(g, text, pad(), pad(), color(), shadow());
		HudText.draw(g, showLabel.isOn() ? " ms" : "ms", after, pad(), Colors.withAlpha(textColor.argb(), 0.85f), shadow());
	}
}
