package dev.mordo.kairo.render.font;

/**
 * A rasterised glyph. All metrics are in physical pixels at the size it was rendered.
 * {@code width == 0} means nothing to draw (spaces).
 */
record Glyph(int index, float advance, int left, int top, int width, int height, float u0, float v0, float u1, float v1) {
	boolean isEmpty() {
		return width == 0 || height == 0;
	}
}
