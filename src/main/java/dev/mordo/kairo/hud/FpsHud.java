package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Frames per second, coloured green / yellow / red. */
public final class FpsHud extends HudElement {
	public final BooleanSetting showLabel = add(new BooleanSetting("show_label", "Show label", true));
	public final BooleanSetting colorByValue = add(new BooleanSetting("color_by_value", "Color by value", true)
		.description("Green above the good threshold, red below the bad one"));
	public final SliderSetting goodThreshold = add(new SliderSetting("good", "Good above", 120, 30, 360, 5)
		.visibleWhen(colorByValue::isOn));
	public final SliderSetting badThreshold = add(new SliderSetting("bad", "Bad below", 50, 10, 240, 5)
		.visibleWhen(colorByValue::isOn));
	public final SliderSetting updateInterval = add(new SliderSetting("interval", "Update every", 500, 100, 2000, 50).unit("ms"));

	private int fps = -1;
	private String text = "0";
	private long lastUpdate;

	public FpsHud() {
		super("fps", "FPS", "Frames per second", Icon.GAUGE, true, Anchor.TOP_RIGHT, -0.004, 0.045);
	}

	@Override
	protected void layout(boolean preview) {
		long now = System.currentTimeMillis();
		if (fps < 0 || now - lastUpdate >= updateInterval.value()) {
			lastUpdate = now;
			int current = preview ? 144 : Minecraft.getInstance().getFps();
			if (current != fps) {
				fps = current;
				text = Integer.toString(fps);
			}
		}
		float w = HudText.width(text) + (showLabel.isOn() ? HudText.width(" FPS") : 0);
		width = w + pad() * 2;
		height = HudText.LINE + pad() * 2;
	}

	private int color() {
		if (!colorByValue.isOn()) {
			return valueColor.argb();
		}
		if (fps >= goodThreshold.value()) {
			return 0xFF55FF7F;
		}
		return fps < badThreshold.value() ? 0xFFFF5555 : 0xFFFFD45C;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, boolean preview) {
		drawBackground(g);
		float after = HudText.draw(g, text, pad(), pad(), color(), shadow());
		if (showLabel.isOn()) {
			HudText.draw(g, " FPS", after, pad(), textColor.argb(), shadow());
		}
	}
}
