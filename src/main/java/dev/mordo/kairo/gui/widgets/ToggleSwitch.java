package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntSupplier;

/** Animated on/off switch. The knob slides and the track fills with the accent colour. */
public final class ToggleSwitch extends Widget {
	public static final float WIDTH = 36;
	public static final float HEIGHT = 20;

	private final Binding<Boolean> value;
	private final Animation on;
	private final Animation press = new Animation(0);
	private IntSupplier accent = Theme::accent;

	public ToggleSwitch(Binding<Boolean> value) {
		this.value = value;
		this.on = new Animation(value.get() ? 1 : 0);
		this.w = WIDTH;
		this.h = HEIGHT;
	}

	/** Use a different "on" colour (the PvP accent in PvP sections). */
	public ToggleSwitch accent(IntSupplier accent) {
		this.accent = accent;
		return this;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		on.animateTo(value.get() ? 1 : 0, 240, Easing.UI);
		float t = on.value();
		float alpha = fade * (enabled ? 1 : 0.4f);
		int track = Colors.lerp(Theme.TRACK, accent.getAsInt(), t);
		track = Colors.brighten(track, hover.value() * 0.08f);
		Shapes.rect(g, x, y, w, h, h / 2, Colors.multiplyAlpha(track, alpha));
		float r = h / 2 - 3;
		float knobX = Animation.lerp(x + h / 2, x + w - h / 2, t);
		float squash = 1 + press.value() * 0.18f;
		Shapes.shadow(g, knobX - r, y + h / 2 - r + 1, r * 2, r * 2, r, 3, Colors.argb(0.25f * alpha, 0));
		Shapes.rect(g, knobX - r * squash, y + h / 2 - r, r * 2 * squash, r * 2, r, Colors.withAlpha(0xFFFFFF, alpha));
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		press.set(1).animateTo(0, 260, Easing.OUT_CUBIC);
		value.set(!value.get());
		dev.mordo.kairo.sound.UiSounds.toggle(value.get());
		return true;
	}
}
