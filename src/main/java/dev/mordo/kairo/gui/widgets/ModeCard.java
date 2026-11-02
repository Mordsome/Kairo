package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Bottom of the sidebar: current mode and how many mods are on. Click to flip PvP mode. */
public final class ModeCard extends Widget {
	private final Animation pvp = new Animation(PvpMode.isActive() ? 1 : 0);

	public ModeCard() {
		this.h = 60;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		pvp.animateTo(PvpMode.isActive() ? 1 : 0, 300);
		float on = pvp.value();
		int dot = Colors.lerp(Theme.accent(), Theme.pvpAccent(), on);
		int fill = Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover.value());
		int stroke = Colors.lerp(Theme.BORDER, Colors.withAlpha(dot, 0.35f), hover.value());
		Shapes.rect(g, x, y, w, h, 14, Colors.multiplyAlpha(fill, fade), Colors.multiplyAlpha(stroke, fade), 1);
		float dotX = x + 18;
		float top = y + 22;
		Shapes.shadow(g, dotX - 3.5f, top - 3.5f, 7, 7, 3.5f, 5, Colors.multiplyAlpha(dot, 0.7f * fade));
		Shapes.circle(g, dotX, top, 3.5f, Colors.multiplyAlpha(dot, fade));
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Normal mode", dotX + 11, top, 12, Theme.text(1 - on, fade));
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "PvP mode", dotX + 11, top, 12, Theme.text(on, fade));
		String count = ModuleManager.enabledCount() + " of " + ModuleManager.modules().size() + " mods on";
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, count, x + 18, y + 42, 10.5f, Theme.text(0.55f, fade));
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		PvpMode.toggle();
		return true;
	}
}
