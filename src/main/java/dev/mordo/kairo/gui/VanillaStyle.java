package dev.mordo.kairo.gui;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * The client's look for Minecraft's own menus (options, world creation, pause menu...): dark glass buttons with a
 * thin border that lights up in the accent colour, rounded text fields, sliders and checkboxes, and a calmer
 * background. Text and layout stay vanilla. Visuals → "Restyle other menus" turns it off.
 */
public final class VanillaStyle {
	private static final int FILL = 0x9E0E131B;
	private static final int FILL_INACTIVE = 0x5A0E131B;
	private static final int BORDER = 0x30FFFFFF;
	private static final int BORDER_INACTIVE = 0x14FFFFFF;

	private VanillaStyle() {
	}

	public static boolean enabled() {
		return ClientSettings.INSTANCE.restyleMenus.isOn() && Minecraft.getInstance().isGameLoadFinished();
	}

	private static float radius(int h) {
		return Math.min(ClientSettings.INSTANCE.buttonRadius.floatValue(), h / 2f);
	}

	/** A Minecraft button in the client look; {@code fx} carries its hover fade and click effect. */
	public static void button(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered, float alpha,
							  ButtonFx.State fx) {
		ClientSettings s = ClientSettings.INSTANCE;
		int accent = Theme.accent();
		int base = s.buttonColor.argb();
		float hv = active ? ButtonFx.hover(fx, hovered) : ButtonFx.hover(fx, false);
		int fill = !active ? Colors.multiplyAlpha(base, 0.57f) : Colors.lerp(base, Colors.lerp(base, Colors.withAlpha(accent, 0.62f), 0.32f), hv);
		int border = !active ? BORDER_INACTIVE : Colors.lerp(BORDER, Colors.withAlpha(accent, 0.85f), hv);
		if (!s.buttonOutline.isOn()) {
			border = Colors.multiplyAlpha(border, hv);
		}
		float r = radius(h);
		if (hv > 0.01f && active && s.buttonGlow.isOn()) {
			Shapes.shadow(g, x, y + 1, w, h, r, 6, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.25f * hv), alpha));
		}
		Shapes.rect(g, x, y, w, h, r, Colors.multiplyAlpha(fill, alpha), Colors.multiplyAlpha(border, alpha), 1);
		if (active) {
			ButtonFx.drawOnButton(g, fx, x, y, w, h, r, alpha);
		}
	}

	public static void textField(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean focused, boolean active) {
		int border = !active ? BORDER_INACTIVE : focused ? Colors.withAlpha(Theme.accent(), 0.9f) : BORDER;
		Shapes.rect(g, x, y, w, h, radius(h), Theme.tintBackground(active ? 0xB0080B11 : FILL_INACTIVE), border, 1);
	}

	public static void sliderTrack(GuiGraphicsExtractor g, int x, int y, int w, int h, double value, boolean active, boolean hovered) {
		int accent = Theme.accent();
		Shapes.rect(g, x, y, w, h, radius(h), Theme.tintBackground(active ? FILL : FILL_INACTIVE), hovered && active ? Colors.withAlpha(accent, 0.6f) : BORDER, 1);
		float filled = (float) ((w - 8) * value + 4);
		if (active && filled > 2) {
			Shapes.rect(g, x + 1, y + 1, filled, h - 2, radius(h) - 1, Colors.withAlpha(accent, hovered ? 0.32f : 0.22f));
		}
	}

	public static void sliderHandle(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean active, boolean hovered) {
		int color = !active ? 0x66FFFFFF : hovered ? 0xFFFFFFFF : 0xE6F4F8FB;
		Shapes.shadow(g, x, y + 1, w, h, 3, 4, 0x55000000);
		Shapes.rect(g, x + 1, y + 1, w - 2, h - 2, Math.min(3, (w - 2) / 2f), color);
	}

	public static void checkbox(GuiGraphicsExtractor g, int x, int y, int size, boolean selected, boolean hovered) {
		int accent = Theme.accent();
		int fill = selected ? accent : Theme.tintBackground(FILL);
		int border = selected ? accent : hovered ? Colors.withAlpha(accent, 0.8f) : BORDER;
		Shapes.rect(g, x, y, size, size, Math.min(4, ClientSettings.INSTANCE.buttonRadius.floatValue()), fill, border, 1);
		if (selected) {
			IconRenderer.draw(g, Icon.CHECK, x + size / 2f, y + size / 2f, size * 0.8f, Theme.DARK_TEXT, 3f);
		}
	}

	/** Replaces the dirt-like menu texture: a dark tint so the blurred panorama/world shows through. */
	public static void menuBackground(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		boolean inWorld = Minecraft.getInstance().level != null;
		int top = inWorld ? 0x8C06090E : 0x7006090E;
		int bottom = inWorld ? 0xB306090E : 0xA306090E;
		Shapes.gradientRect(g, x, y, w, h, 0, Theme.tintBackground(top), Theme.tintBackground(bottom));
	}

	public static void scrollbar(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean track) {
		float bw = Math.min(4, w);
		float bx = x + (w - bw) / 2f;
		if (track) {
			Shapes.rect(g, bx, y + 2, bw, h - 4, bw / 2, 0x14FFFFFF);
		} else {
			Shapes.rect(g, bx, y + 2, bw, h - 4, bw / 2, 0x80FFFFFF);
		}
	}

	/** Thin lines instead of the vanilla header/footer separator textures. */
	public static void listSeparators(GuiGraphicsExtractor g, int x, int top, int right, int bottom) {
		int line = 0x1FFFFFFF;
		Shapes.rect(g, x, top - 1, right - x, 1, 0, line);
		Shapes.rect(g, x, bottom, right - x, 1, 0, line);
	}
}
