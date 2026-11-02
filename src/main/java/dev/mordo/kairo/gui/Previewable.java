package dev.mordo.kairo.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A module that can show a live preview at the top of its settings page. */
public interface Previewable {
	/**
	 * Draws the preview centred on (cx, cy) in reference units, within maxW x maxH.
	 * {@code unit} is GUI pixels per reference unit.
	 */
	void drawPreview(GuiGraphicsExtractor g, float cx, float cy, float maxW, float maxH, float unit);
}
