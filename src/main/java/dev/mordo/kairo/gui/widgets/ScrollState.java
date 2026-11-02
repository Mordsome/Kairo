package dev.mordo.kairo.gui.widgets;

import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/** Smooth scrolling for a content area: wheel moves a target, the shown offset eases towards it. */
public final class ScrollState {
	private static final float STEP = 70;

	private final Animation offset = new Animation(0);
	private float target;
	private float contentHeight;
	private float viewHeight;

	public void setSizes(float contentHeight, float viewHeight) {
		this.contentHeight = contentHeight;
		this.viewHeight = viewHeight;
		float clamped = Math.clamp(target, 0f, maxScroll());
		if (clamped != target) {
			target = clamped;
			offset.animateTo(target, 200, Easing.OUT_CUBIC);
		}
	}

	public float maxScroll() {
		return Math.max(0, contentHeight - viewHeight);
	}

	public void scroll(float wheel) {
		target = Math.clamp(target - wheel * STEP, 0f, maxScroll());
		offset.animateTo(target, 260, Easing.OUT_CUBIC);
	}

	/** Jumps straight to an offset (clamped), no animation. */
	public void jumpTo(float offsetValue) {
		target = Math.clamp(offsetValue, 0f, maxScroll());
		offset.set(target);
	}

	public void reset() {
		target = 0;
		offset.set(0);
	}

	/** Current offset to subtract from content y positions. */
	public float value() {
		return offset.value();
	}

	/** Thin scrollbar on the right edge of the view, only when there is something to scroll. */
	public void drawBar(GuiGraphicsExtractor g, float right, float top, float fade) {
		float max = maxScroll();
		if (max <= 0.5f) {
			return;
		}
		float barH = Math.max(28, viewHeight * viewHeight / contentHeight);
		float barY = top + (viewHeight - barH) * (value() / max);
		Shapes.rect(g, right - 3, barY, 3, barH, 1.5f, Colors.argb(0.22f * fade, 0xFFFFFF));
	}
}
