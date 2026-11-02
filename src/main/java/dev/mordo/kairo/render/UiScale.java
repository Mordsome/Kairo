package dev.mordo.kairo.render;

import net.minecraft.client.Minecraft;

/**
 * How many physical screen pixels one local drawing unit covers, ignoring animation transforms.
 * Fonts use it to rasterise glyphs at the exact pixel size, and shapes to size their antialiasing margin.
 * Outside of our own screens it is just the GUI scale (local unit = 1 GUI pixel).
 */
public final class UiScale {
	private static final float[] STACK = new float[16];
	private static int depth;

	private UiScale() {
	}

	public static float current() {
		return depth == 0 ? Minecraft.getInstance().getWindow().getGuiScale() : STACK[depth - 1];
	}

	public static void push(float physicalPixelsPerUnit) {
		STACK[depth++] = physicalPixelsPerUnit;
	}

	public static void pop() {
		if (depth > 0) {
			depth--;
		}
	}

	/** Physical pixels per local unit for a UI designed at a reference height (e.g. 810). */
	public static float forReferenceHeight(float referenceHeight) {
		return Minecraft.getInstance().getWindow().getHeight() / referenceHeight;
	}
}
