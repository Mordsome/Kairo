package dev.mordo.kairo.cosmetics;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.KairoScreen;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The emote wheel (B by default). Emotes come in pages (your favourites, dances, reactions, poses, moves): the tabs
 * above the ring, the mouse wheel or Tab switch pages. Hold the key, point at an emote and let go to play it; or tap
 * the key and click one (tap again to close). Right-click an emote to add it to or remove it from your favourites.
 */
public final class EmoteWheelScreen extends KairoScreen {
	private static final float OUTER = 270;
	private static final float INNER = 128;
	private static final int MAX_SEGMENTS = 16;
	private static final float TAB_H = 30;

	private record Page(String title, List<Emotes.Emote> emotes) {
	}

	/** The page you were on last time (kept while the game runs). */
	private static String lastPage = "Favourites";

	private final List<Page> pages = new ArrayList<>();
	private int page;
	private final Animation appear = new Animation(0);
	private final Animation swap = new Animation(1);
	private final Animation[] hover = new Animation[MAX_SEGMENTS];
	private int hovered = -1;
	private int lastHovered = -1;
	private int hoveredTab = -1;
	/** When it opened: holding the key longer than a tap means "let go to pick". */
	private final long openedAt = System.nanoTime();
	private boolean released;

	public EmoteWheelScreen() {
		super(Component.literal("Emotes"));
		for (int i = 0; i < hover.length; i++) {
			hover[i] = new Animation(0);
		}
		buildPages();
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i).title().equals(lastPage)) {
				page = i;
			}
		}
	}

	private void buildPages() {
		String was = pages.isEmpty() ? lastPage : pages.get(page).title();
		pages.clear();
		List<Emotes.Emote> favourites = Emotes.favourites();
		if (!favourites.isEmpty()) {
			pages.add(new Page("Favourites", favourites));
		}
		for (Emotes.Kind kind : Emotes.Kind.values()) {
			pages.add(new Page(kind.label(), Emotes.of(kind)));
		}
		page = 0;
		for (int i = 0; i < pages.size(); i++) {
			if (pages.get(i).title().equals(was)) {
				page = i;
			}
		}
	}

	/** The emote key: opens the wheel in game. */
	public static void open() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player != null && mc.gui.screen() == null && Cosmetics.get().isEnabled()) {
			mc.gui.setScreen(new EmoteWheelScreen());
		}
	}

	/** Shows a page by index (tests). */
	public void showPage(int index) {
		turnTo(Math.clamp(index, 0, pages.size() - 1));
	}

	private List<Emotes.Emote> emotes() {
		return pages.get(page).emotes();
	}

	private void turnTo(int index) {
		if (index == page) {
			return;
		}
		page = index;
		lastPage = pages.get(page).title();
		swap.set(0).animateTo(1, 280, Easing.UI);
		hovered = -1;
		UiSounds.hover();
	}

	@Override
	protected void init() {
		super.init();
		appear.animateTo(1, 260, Easing.UI);
		UiSounds.open();
	}

	@Override
	protected void onCloseStarted() {
		appear.animateTo(0, 160, Easing.IN_CUBIC);
	}

	@Override
	protected boolean isCloseAnimationDone() {
		return appear.isDone();
	}

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		Shapes.backdrop(g, appear.value(), ClientSettings.INSTANCE.backgroundDim.floatValue() * 0.6f, 0.3f);
	}

	private float half() {
		return (float) (Math.PI / emotes().size());
	}

	private float mid(int i) {
		return (float) (-Math.PI / 2 + i * Math.PI * 2 / emotes().size());
	}

	// ---- tabs ----

	private float tabWidth(int i) {
		return Fonts.width(Typeface.OUTFIT_SEMIBOLD, pages.get(i).title(), 12) + (pages.get(i).title().equals("Favourites") ? 46 : 28);
	}

	private float tabsLeft() {
		float total = 0;
		for (int i = 0; i < pages.size(); i++) {
			total += tabWidth(i) + (i > 0 ? 8 : 0);
		}
		return viewWidth / 2 - total / 2;
	}

	private float tabsTop() {
		return Math.max(14, viewHeight / 2 - OUTER - 58);
	}

	private int tabAt(float mx, float my) {
		float top = tabsTop();
		if (my < top || my > top + TAB_H) {
			return -1;
		}
		float x = tabsLeft();
		for (int i = 0; i < pages.size(); i++) {
			float w = tabWidth(i);
			if (mx >= x && mx <= x + w) {
				return i;
			}
			x += w + 8;
		}
		return -1;
	}

	private void drawTabs(GuiGraphicsExtractor g, float mx, float my, float a) {
		hoveredTab = tabAt(mx, my);
		float x = tabsLeft();
		float top = tabsTop() - Animation.motion((1 - a) * 10, 0);
		int accent = Theme.accent();
		for (int i = 0; i < pages.size(); i++) {
			float w = tabWidth(i);
			boolean on = i == page;
			boolean over = i == hoveredTab;
			if (on) {
				Shapes.shadow(g, x, top, w, TAB_H, TAB_H / 2, 10, Colors.withAlpha(accent, 0.35f * a));
			}
			int tint = on ? Colors.withAlpha(Colors.lerp(accent | 0xFF000000, 0xFF000000, 0.3f), 0.5f) : Theme.glassTint();
			Shapes.glassRect(g, x, top, w, TAB_H, TAB_H / 2, tint, on ? accent : over ? 0x60FFFFFF : 0x30FFFFFF, 1.1f, a);
			float tx = x + 14;
			if (pages.get(i).title().equals("Favourites")) {
				IconRenderer.draw(g, Icon.HEART, tx + 7, top + TAB_H / 2, 12, Colors.withAlpha(on ? 0xFFFFFFFF : accent, a), 2f);
				tx += 18;
			}
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, pages.get(i).title(), tx, top + TAB_H / 2, 12, Colors.withAlpha(0xFFF4F8FB, a * (on || over ? 1 : 0.7f)));
			x += w + 8;
		}
		if (hoveredTab >= 0) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		}
	}

	// ---- the wheel ----

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float partialTick) {
		float a = appear.value();
		float cx = viewWidth / 2;
		float cy = viewHeight / 2;
		List<Emotes.Emote> list = emotes();
		hovered = hit(mx - cx, my - cy);
		if (hovered != lastHovered && hovered >= 0) {
			UiSounds.hover();
		}
		lastHovered = hovered;
		if (hovered >= 0) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		}
		drawTabs(g, mx, my, a);
		float s = swap.value();
		float scale = Animation.motion((0.85f + 0.15f * a) * (0.92f + 0.08f * s), 1);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		g.pose().rotate(Animation.motion((1 - s) * -0.25f, 0));
		int accent = Theme.accent();
		Emotes.Emote playing = Emotes.current();
		float sa = a * (0.25f + 0.75f * s);
		for (int i = 0; i < list.size() && i < MAX_SEGMENTS; i++) {
			hover[i].animateTo(i == hovered ? 1 : 0, 220);
			float h = hover[i].value();
			float m = mid(i);
			float push = 8 * h;
			Emotes.Emote emote = list.get(i);
			boolean on = emote == playing;
			int tint = Colors.lerp(Theme.glassTint(), Colors.withAlpha(Colors.lerp(accent | 0xFF000000, 0xFF000000, 0.28f), 0.34f), Math.max(h, on ? 0.6f : 0));
			int stroke = Colors.lerp(0x40FFFFFF, accent, h);
			Shapes.glassSector(g, (float) Math.cos(m) * push, (float) Math.sin(m) * push, INNER, OUTER + 6 * h, m, half(), list.size() > 10 ? 4 : 6, 6,
				tint, stroke, 1.25f, sa, 1.4f);
			float lr = (INNER + OUTER) / 2 + push;
			float lx = (float) Math.cos(m) * lr;
			float ly = (float) Math.sin(m) * lr;
			int text = Colors.withAlpha(0xFFF4F8FB, sa);
			IconRenderer.draw(g, emote.icon(), lx, ly - 10, 18, Colors.multiplyAlpha(h > 0.5f || on ? accent : 0xFFF4F8FB, sa), 1.8f);
			float labelSize = list.size() > 11 ? 10.5f : 11.5f;
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, emote.label(), lx, ly + 12, labelSize, text, Fonts.Align.CENTER, 0, false);
			if (Emotes.isFavourite(emote)) {
				float hr = OUTER - 16 + push;
				IconRenderer.draw(g, Icon.HEART, (float) Math.cos(m) * hr, (float) Math.sin(m) * hr, 9, Colors.multiplyAlpha(accent, sa * 0.9f), 2f);
			}
		}
		g.pose().popMatrix();

		// Centre: what you're pointing at.
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		float cs = Animation.motion(0.85f + 0.15f * a, 1);
		g.pose().scale(cs, cs);
		Shapes.glassSector(g, 0, 0, 0, INNER - 14, 0, (float) Math.PI, 0, 0, Theme.glassTint(), 0x40FFFFFF, 1.25f, a, 1.4f);
		Emotes.Emote over = hovered >= 0 ? list.get(hovered) : null;
		String title = over != null ? over.label() : pages.get(page).title();
		String sub = over != null ? (over.loops() ? "Until you move" : "Plays once") : list.size() + " emotes";
		String kicker = over != null ? (Emotes.isFavourite(over) ? "FAVOURITE" : over.kind().label().toUpperCase(java.util.Locale.ROOT)) : "EMOTES";
		Fonts.draw(g, Typeface.MONO_BOLD, kicker, 0, -26, 8.5f, Colors.multiplyAlpha(accent, a), Fonts.Align.CENTER, 2f, false);
		Fonts.drawCentered(g, Typeface.OUTFIT_SEMIBOLD, title, 0, 0, title.length() > 11 ? 19 : 24, Colors.withAlpha(0xFFF4F8FB, a));
		Fonts.drawCentered(g, Typeface.OUTFIT_REGULAR, sub, 0, 24, 11.5f, Colors.withAlpha(0xFFF4F8FB, a * 0.65f));
		g.pose().popMatrix();
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, (released ? "Click one" : "Point and let go") + "  ·  scroll or Tab for more  ·  right-click: favourite", cx, viewHeight - 40, 11,
			Theme.text(0.6f, a), Fonts.Align.CENTER, 0, true);
	}

	private int hit(float dx, float dy) {
		int n = emotes().size();
		float r = (float) Math.sqrt(dx * dx + dy * dy);
		if (n == 0 || r < INNER - 4 || r > OUTER + 20) {
			return -1;
		}
		double fromTop = Math.atan2(dy, dx) + Math.PI / 2 + half();
		fromTop = ((fromTop % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
		return (int) (fromTop / (half() * 2)) % n;
	}

	@Override
	protected boolean onClick(float x, float y, int button) {
		int tab = tabAt(x, y);
		if (tab >= 0) {
			UiSounds.click();
			turnTo(tab);
			return true;
		}
		int i = hit(x - viewWidth / 2, y - viewHeight / 2);
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			if (i < 0) {
				onClose();
				return true;
			}
			Emotes.Emote emote = emotes().get(i);
			boolean was = Emotes.isFavourite(emote);
			if (!Emotes.toggleFavourite(emote)) {
				dev.mordo.kairo.gui.Toasts.show("Up to " + Emotes.MAX_FAVOURITES + " favourites", Theme.accent());
			} else {
				UiSounds.toggle(!was);
				buildPages();
			}
			return true;
		}
		if (i < 0) {
			onClose();
			return true;
		}
		UiSounds.click();
		Emotes.Emote emote = emotes().get(i);
		close(null);
		Emotes.play(emote);
		return true;
	}

	@Override
	protected boolean onScroll(float x, float y, float amount) {
		if (amount != 0 && pages.size() > 1) {
			turnTo(Math.floorMod(page + (amount < 0 ? 1 : -1), pages.size()));
			return true;
		}
		return false;
	}

	/** Letting go of the emote key after holding it plays the emote you're pointing at. */
	@Override
	public boolean keyReleased(KeyEvent event) {
		if (!released && Cosmetics.get().emoteKey.get().matchesKey(event.key())) {
			released = true;
			boolean held = System.nanoTime() - openedAt > 250_000_000L;
			if (held) {
				if (hovered >= 0) {
					UiSounds.click();
					Emotes.Emote emote = emotes().get(hovered);
					close(null);
					Emotes.play(emote);
				} else {
					onClose();
				}
			}
			return true;
		}
		return super.keyReleased(event);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (Cosmetics.get().emoteKey.get().matchesKey(event.key())) {
			// While the key is still held from opening, these are the keyboard's auto-repeats: ignore them. Only a
			// fresh press after letting go (tap mode) closes the wheel.
			if (released) {
				onClose();
			}
			return true;
		}
		if (event.isCycleFocus()) {
			turnTo(Math.floorMod(page + (event.hasShiftDown() ? -1 : 1), pages.size()));
			return true;
		}
		return super.keyPressed(event);
	}
}
