package dev.mordo.kairo.gui;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/**
 * Small animated notification pill at the top of the screen ("PvP mode ON").
 * Drawn by the HUD in-game, and by our own screens on top of their content.
 */
public final class Toasts {
	private static final float IN_MS = 280;
	private static final float HOLD_MS = 1400;
	private static final float OUT_MS = 320;

	private record Toast(String text, int dotColor, long startNanos) {
	}

	private static @Nullable Toast current;

	private Toasts() {
	}

	public static void register() {
		HudElementRegistry.addLast(ClientInfo.id("toasts"), (g, delta) -> {
			if (!(Minecraft.getInstance().gui.screen() instanceof KairoScreen)) {
				draw(g);
			}
		});
	}

	public static void pvpMode(boolean on) {
		ClientSettings s = ClientSettings.INSTANCE;
		show(on ? "PvP mode ON" : "PvP mode OFF", on ? s.pvpAccent.argb() : s.accent.argb());
	}

	public static void show(String text, int dotColor) {
		current = new Toast(text, dotColor, System.nanoTime());
	}

	public static void draw(GuiGraphicsExtractor g) {
		Toast toast = current;
		if (toast == null) {
			return;
		}
		float speed = Animation.speed();
		float elapsed = (System.nanoTime() - toast.startNanos()) / 1_000_000f * speed;
		if (elapsed > IN_MS + HOLD_MS + OUT_MS) {
			current = null;
			return;
		}
		float in = Easing.UI.apply(Math.clamp(elapsed / IN_MS, 0f, 1f));
		float out = Easing.IN_CUBIC.apply(Math.clamp((elapsed - IN_MS - HOLD_MS) / OUT_MS, 0f, 1f));
		float alpha = in * (1 - out);
		float slide = Animation.motion((1 - in) * -14 + out * -8, 0);

		RefSpace.push(g);
		try {
			float viewWidth = g.guiWidth() / RefSpace.unit(g.guiWidth(), g.guiHeight());
			float textSize = 12;
			float textWidth = Fonts.width(Typeface.OUTFIT_SEMIBOLD, toast.text(), textSize);
			float w = textWidth + 46;
			float h = 32;
			float x = (viewWidth - w) / 2;
			float y = 28 + slide;
			Shapes.shadow(g, x, y + 4, w, h, h / 2, 12, Colors.argb(0.35f * alpha, 0));
			Shapes.rect(g, x, y, w, h, h / 2, Colors.argb(0.78f * alpha, 0x0C1018), Colors.argb(0.18f * alpha, 0xFFFFFF), 1.25f);
			float dotX = x + 18;
			float cy = y + h / 2;
			Shapes.shadow(g, dotX - 4, cy - 4, 8, 8, 4, 5, Colors.multiplyAlpha(toast.dotColor(), 0.6f * alpha));
			Shapes.circle(g, dotX, cy, 3.5f, Colors.multiplyAlpha(toast.dotColor(), alpha));
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, toast.text(), dotX + 12, cy, textSize, Colors.argb(alpha, 0xF4F8FB));
		} finally {
			RefSpace.pop(g);
		}
	}
}
