package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/** A small modal card ("Delete this world?") with Cancel and a confirm button. Dims what's behind it. */
final class ConfirmCard {
	private static final float W = 420;

	private final String title;
	private final List<String> lines;
	private final Runnable onConfirm;
	private final Button cancel;
	private final Button ok;
	private final Animation open = new Animation(0);
	private boolean closing;

	ConfirmCard(String title, String message, String confirmLabel, boolean danger, Runnable onConfirm) {
		this.title = title;
		this.lines = Fonts.wrap(Typeface.OUTFIT_REGULAR, message, 12.5f, W - 56);
		this.onConfirm = onConfirm;
		this.cancel = new Button("Cancel", null, Button.Style.SUBTLE, this::dismiss).textSize(12);
		this.ok = new Button(confirmLabel, null, danger ? Button.Style.DANGER : Button.Style.PRIMARY, this::accept).textSize(12);
		open.animateTo(1, 260, Easing.UI);
	}

	void dismiss() {
		if (!closing) {
			closing = true;
			open.animateTo(0, 160, Easing.IN_CUBIC);
		}
	}

	void accept() {
		if (!closing) {
			dismiss();
			onConfirm.run();
		}
	}

	boolean isGone() {
		return closing && open.isDone();
	}

	void render(GuiGraphicsExtractor g, float mx, float my, float viewW, float viewH) {
		float a = open.value();
		Shapes.rect(g, 0, 0, viewW, viewH, 0, Colors.argb(0.45f * a, 0x05070A));
		float h = 92 + lines.size() * 18 + 56;
		float x = (viewW - W) / 2;
		float y = (viewH - h) / 2 + Animation.motion((1 - a) * 14, 0);
		Shapes.shadow(g, x, y + 10, W, h, 22, 30, Colors.argb(0.5f * a, 0));
		Shapes.glassRect(g, x, y, W, h, 22, Theme.glassTint(), Theme.PANEL_BORDER, 1.25f, a);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, title, x + 28, y + 40, 20, Theme.text(1, a));
		for (int i = 0; i < lines.size(); i++) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, lines.get(i), x + 28, y + 72 + i * 18, 12.5f, Theme.text(0.65f, a));
		}
		float by = y + h - 28 - 36;
		float okW = ok.preferredWidth() + 12;
		float cancelW = cancel.preferredWidth() + 8;
		ok.bounds(x + W - 28 - okW, by, okW, 36);
		cancel.bounds(ok.x - 10 - cancelW, by, cancelW, 36);
		cancel.render(g, mx, my, a, !closing);
		ok.render(g, mx, my, a, !closing);
	}

	void mouseClicked(float mx, float my, int button) {
		if (closing || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return;
		}
		if (ok.contains(mx, my)) {
			ok.mouseClicked(mx, my, button);
		} else if (cancel.contains(mx, my)) {
			cancel.mouseClicked(mx, my, button);
		}
	}
}
