package dev.mordo.kairo.gui.widgets;

import dev.mordo.kairo.gui.ButtonFx;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.VanillaStyle;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** A sample Minecraft-menu button for the Buttons settings: hover it and click it to try the current look. */
public final class ButtonPreview extends Widget {
	private final String label;
	private final ButtonFx.State fx = new ButtonFx.State();
	private final boolean activeButton;

	public ButtonPreview(String label, boolean active) {
		this.label = label;
		this.activeButton = active;
		this.h = 30;
		this.w = 200;
	}

	/** Plays the intro again (after the animation setting changes). */
	public void replay() {
		ButtonFx.restartIntro(fx);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float intro = ButtonFx.intro(fx, 0);
		float pop = ButtonFx.popScale(fx);
		float cx = x + w / 2;
		float cy = y + h / 2;
		g.pose().pushMatrix();
		g.pose().translate(cx, cy + (1 - intro) * 5);
		g.pose().scale(pop, pop);
		g.pose().translate(-cx, -cy);
		float alpha = fade * intro;
		VanillaStyle.button(g, Math.round(x), Math.round(y), Math.round(w), Math.round(h), activeButton,
			activeButton && hover.value() > 0.5f, alpha, fx);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, label, cx, cy, 12, Colors.multiplyAlpha(activeButton ? Theme.TEXT : 0x80F4F8FB, alpha),
			Fonts.Align.CENTER, 0, false);
		g.pose().popMatrix();
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (!activeButton || !contains(mx, my)) {
			return false;
		}
		float[] gui = ButtonFx.cursorGui();
		ButtonFx.click(fx, mx, my, gui[0], gui[1]);
		dev.mordo.kairo.sound.UiSounds.click();
		return true;
	}
}
