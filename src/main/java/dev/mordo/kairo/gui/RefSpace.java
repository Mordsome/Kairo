package dev.mordo.kairo.gui;

import dev.mordo.kairo.render.UiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The client's design space: the screen is {@value #REF_HEIGHT} reference units tall (the mockups are
 * 1440x810), so everything scales with screen height and looks the same at any resolution and GUI scale.
 * Very narrow windows fall back to scaling by width so nothing gets cut off.
 */
public final class RefSpace {
	public static final float REF_HEIGHT = 810f;
	private static final float MIN_REF_WIDTH = 1080f;

	private RefSpace() {
	}

	/** GUI pixels per reference unit. */
	public static float unit(float guiWidth, float guiHeight) {
		return Math.min(guiHeight / REF_HEIGHT, guiWidth / MIN_REF_WIDTH);
	}

	/** Enters reference space; returns the unit (GUI pixels per reference unit). Pair with {@link #pop}. */
	public static float push(GuiGraphicsExtractor g) {
		float unit = unit(g.guiWidth(), g.guiHeight());
		g.pose().pushMatrix();
		g.pose().scale(unit, unit);
		UiScale.push(unit * Minecraft.getInstance().getWindow().getGuiScale());
		return unit;
	}

	public static void pop(GuiGraphicsExtractor g) {
		UiScale.pop();
		g.pose().popMatrix();
	}
}
