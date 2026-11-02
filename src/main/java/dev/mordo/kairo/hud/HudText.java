package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * HUD text in the chosen HUD font (Minecraft by default, like a classic PvP HUD, or Outfit).
 * Units are HUD units (1 = one GUI pixel before the element's scale); lines are 9 tall like vanilla.
 */
public final class HudText {
	public static final float LINE = 9;
	private static final float CLIENT_SIZE = 9.5f;

	private HudText() {
	}

	private static boolean client() {
		return ClientSettings.INSTANCE.hudFont.get() == ClientSettings.HudFont.CLIENT;
	}

	public static float width(String text) {
		return client() ? Fonts.width(Typeface.OUTFIT_SEMIBOLD, text, CLIENT_SIZE) : Minecraft.getInstance().font.width(text);
	}

	/** Draws with the line's top at {@code y} (glyphs occupy y..y+8, like vanilla). Returns the x after the text. */
	public static float draw(GuiGraphicsExtractor g, String text, float x, float y, int color, boolean shadow) {
		if (client()) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, text, x, y + 3.5f, CLIENT_SIZE, color, Fonts.Align.LEFT, 0, shadow);
		} else {
			g.pose().pushMatrix();
			g.pose().translate(x, y);
			g.text(Minecraft.getInstance().font, text, 0, 0, color, shadow);
			g.pose().popMatrix();
		}
		return x + width(text);
	}

	public static void drawCentered(GuiGraphicsExtractor g, String text, float cx, float y, int color, boolean shadow) {
		draw(g, text, cx - width(text) / 2, y, color, shadow);
	}
}
