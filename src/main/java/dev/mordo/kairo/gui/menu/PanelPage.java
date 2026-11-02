package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ModeCard;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.List;

/**
 * The large frosted glass panel used by Mods, Visuals and Settings: a sidebar (back button, section nav,
 * mode card) and a content area. Opens with a quick scale 0.94 to 1 plus fade.
 */
public abstract class PanelPage extends Page {
	protected static final float SIDEBAR_W = 236;
	protected static final float RADIUS = 28;
	protected static final float CONTENT_PAD = 28;

	protected final Animation appear = new Animation(0);
	private boolean hidden = true;
	private final Button back;
	private final ModeCard modeCard = new ModeCard();

	// Panel and content rectangles (reference units), updated every frame.
	protected float px;
	protected float py;
	protected float pw;
	protected float ph;
	protected float contentX;
	protected float contentW;

	protected PanelPage(MenuScreen menu) {
		super(menu);
		back = new Button("Back to ring", Icon.ARROW_LEFT, Button.Style.SUBTLE, () -> menu.navigate(menu.ring()));
	}

	/** The small label above the sidebar nav (e.g. "CATEGORIES"). */
	protected abstract String navLabel();

	protected abstract List<NavItem> navItems();

	/** Draws everything right of the sidebar. {@code fade} is the panel opacity. */
	protected abstract void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean interactive);

	@Override
	public void show() {
		hidden = false;
		appear.animateTo(1, 340, Easing.UI, 70); // a beat later, so the ring is already on its way out
	}

	@Override
	public void hide() {
		hidden = true;
		appear.animateTo(0, 180, Easing.IN_CUBIC);
	}

	@Override
	public boolean isHidden() {
		return hidden && appear.isDone();
	}

	/** Widgets only take input when the panel is fully open and not leaving. */
	protected boolean canInteract(boolean interactive) {
		return interactive && !hidden && appear.value() > 0.97f;
	}

	protected void put(Widget widget, GuiGraphicsExtractor g, float mx, float my, float fade, boolean interactive) {
		if (interactive) {
			host.render(widget, g, mx, my, fade);
		} else {
			widget.render(g, mx, my, fade, false);
		}
	}

	@Override
	public void render(GuiGraphicsExtractor g, float mx, float my, float viewWidth, float viewHeight, boolean interactive) {
		float a = appear.value();
		if (a <= 0.002f) {
			return;
		}
		boolean live = canInteract(interactive);
		pw = Math.clamp(viewWidth * 0.8f, 820, 1500);
		ph = Math.clamp(viewHeight * 0.8f, 500, 780);
		px = (viewWidth - pw) / 2;
		py = (viewHeight - ph) / 2;
		contentX = px + SIDEBAR_W + CONTENT_PAD;
		contentW = pw - SIDEBAR_W - CONTENT_PAD * 2;

		float scale = Animation.motion(0.94f + 0.06f * a, 1);
		g.pose().pushMatrix();
		g.pose().translate(viewWidth / 2, viewHeight / 2);
		g.pose().scale(scale, scale);
		g.pose().translate(-viewWidth / 2, -viewHeight / 2);

		Shapes.shadow(g, px, py + 18, pw, ph, RADIUS, 40, Colors.argb(0.35f * a, 0));
		Shapes.glassRect(g, px, py, pw, ph, RADIUS, Theme.glassTint(), Theme.PANEL_BORDER, 1.25f, a);
		renderSidebar(g, mx, my, a, live);
		Shapes.rect(g, px + SIDEBAR_W, py + 18, 1, ph - 36, 0, Colors.argb(0.08f * a, 0xFFFFFF));
		renderContent(g, mx, my, a, live);
		g.pose().popMatrix();
	}

	private void renderSidebar(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float x = px + 18;
		float w = SIDEBAR_W - 36;
		back.bounds(x, py + 18, w, 36);
		put(back, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.MONO_BOLD, navLabel(), x + 4, py + 82, 8.5f, Theme.text(0.42f, fade), Fonts.Align.LEFT, 2f, false);
		float y = py + 98;
		float cardTop = py + ph - 18 - 60;
		// Long lists (Visuals) get slightly shorter rows so they always end above the mode card.
		List<NavItem> items = navItems();
		float natural = 0;
		for (NavItem item : items) {
			natural += (item.isHeader() ? 24 : 38) + 4;
		}
		float squeeze = Math.clamp((cardTop - 10 - y) / Math.max(1, natural), 0.7f, 1f);
		g.enableScissor((int) Math.floor(x - 4), (int) Math.floor(y - 4), (int) Math.ceil(x + w + 4), (int) Math.floor(cardTop - 6));
		try {
			for (NavItem item : items) {
				float ih = (item.isHeader() ? 24 : 38) * squeeze;
				item.bounds(x, y, w, ih);
				if (y + ih <= cardTop - 6) {
					put(item, g, mx, my, fade, live);
				}
				y += ih + 4 * squeeze;
			}
		} finally {
			g.disableScissor();
		}
		modeCard.bounds(x, cardTop, w, 60);
		put(modeCard, g, mx, my, fade, live);
	}

	/** Big title with a dimmer subtitle next to it, at the top of the content area. */
	protected void drawTitle(GuiGraphicsExtractor g, String title, String subtitle, float x, float fade) {
		float cy = py + 44;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, title, x, cy, 30, Theme.text(1, fade));
		float tw = Fonts.width(Typeface.OUTFIT_BOLD, title, 30);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, subtitle, x + tw + 12, cy + 4, 12, Theme.text(0.5f, fade));
	}
}
