package dev.mordo.kairo.gui.main;

import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.KairoScreen;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.WidgetHost;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Shared frame for the main menu and the screens it opens (worlds, servers): the panorama (or a blurred copy of
 * it), a fade/slide intro and outro, a header with a back button, an optional confirm dialog, and input routing
 * through a {@link WidgetHost}.
 */
public abstract class MainMenuBase extends KairoScreen {
	protected final WidgetHost host = new WidgetHost();
	protected final Animation intro = new Animation(0);
	protected final @Nullable Screen parent;
	private final Button back;
	private @Nullable ConfirmCard confirm;

	protected MainMenuBase(Component title, @Nullable Screen parent) {
		super(title);
		this.parent = parent;
		this.back = new Button("", Icon.ARROW_LEFT, Button.Style.GHOST, this::onClose);
	}

	/** Every time the screen is shown (first time, or coming back from another screen): play the intro. */
	@Override
	public void added() {
		super.added();
		confirm = null;
		intro.set(0).animateTo(1, 420, Easing.UI);
		onShown();
	}

	protected void onShown() {
	}

	@Override
	public void removed() {
		host.reset();
	}

	/** Esc / back: animate out, then the parent screen. */
	@Override
	public void onClose() {
		if (confirm != null) {
			confirm = null;
			return;
		}
		UiSounds.close();
		close(parent);
	}

	@Override
	protected void onCloseStarted() {
		host.reset();
		intro.animateTo(0, 200, Easing.IN_CUBIC);
	}

	@Override
	protected boolean isCloseAnimationDone() {
		return intro.isDone();
	}

	/** Opens another screen after this one animates out. */
	protected void go(Screen next) {
		UiSounds.click();
		close(next);
	}

	/** Blurred, dimmed backdrop (sub-screens) or just a soft gradient over the sharp panorama (title). */
	protected boolean blurredBackdrop() {
		return true;
	}

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		if (blurredBackdrop()) {
			Shapes.backdrop(g, 1, 0.25f, 0.6f);
		}
	}

	@Override
	protected final void draw(GuiGraphicsExtractor g, float mx, float my, float partialTick) {
		host.beginFrame(viewWidth, viewHeight);
		drawContent(g, mx, my, intro.value(), confirm == null && !isClosing());
		host.renderPopup(g, mx, my);
		if (confirm != null) {
			confirm.render(g, mx, my, viewWidth, viewHeight);
			if (confirm.isGone()) {
				confirm = null;
			}
		}
	}

	/** Draw the screen. {@code live}: widgets should take input (false while a dialog is open or closing). */
	protected abstract void drawContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live);

	/** Puts a widget on screen; only live widgets take input. */
	protected void put(dev.mordo.kairo.gui.widgets.Widget widget, GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		if (live) {
			host.render(widget, g, mx, my, fade);
		} else {
			widget.render(g, mx, my, fade, false);
		}
	}

	/** Back button, big title and a small subtitle, from (x, y). Returns the bottom y. */
	protected float drawHeader(GuiGraphicsExtractor g, float x, float y, String title, String subtitle, float mx, float my, float fade, boolean live) {
		float slide = Animation.motion((1 - fade) * -10, 0);
		back.bounds(x - 8, y + slide + 2, 38, 38);
		put(back, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, title, x + 40, y + 20 + slide, 30, Theme.text(1, fade));
		float tw = Fonts.width(Typeface.OUTFIT_BOLD, title, 30);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, subtitle, x + 40 + tw + 14, y + 24 + slide, 12.5f, Theme.text(0.55f, fade));
		return y + 52;
	}

	/** Shows a confirm dialog; {@code onConfirm} runs if the player accepts. */
	protected void confirm(String title, String message, String confirmLabel, boolean danger, Runnable onConfirm) {
		host.reset();
		confirm = new ConfirmCard(title, message, confirmLabel, danger, onConfirm);
	}

	// ---- input ----

	@Override
	protected boolean onClick(float x, float y, int button) {
		if (confirm != null) {
			confirm.mouseClicked(x, y, button);
			return true;
		}
		return host.mouseClicked(x, y, button) || onContentClick(x, y, button);
	}

	protected boolean onContentClick(float x, float y, int button) {
		return false;
	}

	@Override
	protected boolean onRelease(float x, float y, int button) {
		return host.mouseReleased(x, y, button);
	}

	@Override
	protected boolean onDrag(float x, float y, int button, float dx, float dy) {
		return host.mouseDragged(x, y, button);
	}

	@Override
	protected boolean onScroll(float x, float y, float amount) {
		return confirm == null && (host.mouseScrolled(x, y, amount) || onContentScroll(x, y, amount));
	}

	protected boolean onContentScroll(float x, float y, float amount) {
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isClosing()) {
			return true;
		}
		if (confirm != null) {
			if (event.isEscape()) {
				confirm.dismiss();
			} else if (event.isConfirmation()) {
				confirm.accept();
			}
			return true;
		}
		if (host.keyPressed(event) || onKey(event)) {
			return true;
		}
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	protected boolean onKey(KeyEvent event) {
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return confirm == null && !isClosing() && host.charTyped(event);
	}

	/** "2 minutes ago", "Yesterday", "12 Mar 2026". */
	protected static String timeAgo(long epochMillis) {
		if (epochMillis <= 0) {
			return "Never played";
		}
		long seconds = (System.currentTimeMillis() - epochMillis) / 1000;
		if (seconds < 60) {
			return "Just now";
		}
		if (seconds < 3600) {
			long m = seconds / 60;
			return m + (m == 1 ? " minute ago" : " minutes ago");
		}
		if (seconds < 86400) {
			long h = seconds / 3600;
			return h + (h == 1 ? " hour ago" : " hours ago");
		}
		long days = seconds / 86400;
		if (days == 1) {
			return "Yesterday";
		}
		if (days < 30) {
			return days + " days ago";
		}
		return java.time.format.DateTimeFormatter.ofPattern("d MMM yyyy")
			.format(java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault()));
	}

	/** A soft glowing accent dot (used as a bullet). */
	protected static void glowDot(GuiGraphicsExtractor g, float cx, float cy, float r, int color, float alpha) {
		Shapes.shadow(g, cx - r, cy - r, r * 2, r * 2, r, r * 1.6f, Colors.multiplyAlpha(color, 0.6f * alpha));
		Shapes.circle(g, cx, cy, r, Colors.multiplyAlpha(color, alpha));
	}
}
