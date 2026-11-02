package dev.mordo.kairo.gui.widgets;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.IntSupplier;

/** Track, fill and knob, with the formatted value on the right. Drag, click or scroll to change. */
public final class Slider extends Widget {
	private static final float VALUE_WIDTH = 52;

	private final SliderSetting meta;
	private final Binding<Double> value;
	private final Animation shown;
	private final Animation grab = new Animation(0);
	private boolean dragging;
	private IntSupplier accent = Theme::accent;

	public Slider(SliderSetting meta, Binding<Double> value) {
		this.meta = meta;
		this.value = value;
		this.shown = new Animation((float) meta.toProgress(value.get()));
		this.w = 240;
		this.h = 22;
	}

	public Slider accent(IntSupplier accent) {
		this.accent = accent;
		return this;
	}

	private float trackX0() {
		return x + 8;
	}

	private float trackX1() {
		return x + w - VALUE_WIDTH - 8;
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float target = (float) meta.toProgress(value.get());
		if (dragging) {
			shown.set(target);
		} else {
			shown.animateTo(target, 200, Easing.OUT_CUBIC);
		}
		float alpha = fade * (enabled ? 1 : 0.4f);
		float x0 = trackX0();
		float x1 = trackX1();
		float cy = y + h / 2;
		float knobX = Animation.lerp(x0, x1, shown.value());
		Shapes.rect(g, x0, cy - 2, x1 - x0, 4, 2, Colors.multiplyAlpha(Theme.TRACK, alpha));
		Shapes.rect(g, x0, cy - 2, knobX - x0, 4, 2, Colors.multiplyAlpha(accent.getAsInt(), alpha));
		grab.animateTo(dragging ? 1 : hover.value() > 0.5f ? 0.5f : 0, 150);
		float r = 6.5f + grab.value() * 2;
		Shapes.shadow(g, knobX - r, cy - r + 1, r * 2, r * 2, r, 3, Colors.argb(0.3f * alpha, 0));
		Shapes.circle(g, knobX, cy, r, Colors.withAlpha(0xFFFFFF, alpha));
		Fonts.draw(g, Typeface.MONO_MEDIUM, meta.format(value.get()), x + w, cy, 10.5f, Theme.text(0.85f, alpha),
			Fonts.Align.RIGHT, 0.2f, false);
	}

	@Override
	public boolean mouseClicked(float mx, float my, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT || mx > trackX1() + 10) {
			return false;
		}
		dragging = true;
		setFrom(mx);
		return true;
	}

	@Override
	public boolean mouseDragged(float mx, float my, int button) {
		if (dragging) {
			setFrom(mx);
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(float mx, float my, int button) {
		boolean was = dragging;
		dragging = false;
		return was;
	}

	@Override
	public boolean mouseScrolled(float mx, float my, float amount) {
		double step = meta.step() > 0 ? meta.step() : (meta.max() - meta.min()) / 50;
		value.set(value.get() + Math.signum(amount) * step);
		return true;
	}

	private void setFrom(float mx) {
		double progress = (mx - trackX0()) / (trackX1() - trackX0());
		value.set(meta.fromProgress(progress));
	}
}
