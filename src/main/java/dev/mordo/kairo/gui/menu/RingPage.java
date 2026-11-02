package dev.mordo.kairo.gui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.core.keybinds.ClientKeybinds;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.layout.LayoutScreen;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;

/**
 * The Right Shift radial menu. Five frosted-glass segments around a glass disc that describes
 * whatever is hovered. All geometry is in reference units (screen = 810 tall).
 */
public final class RingPage extends Page {
	// ---- geometry (reference units, designed at 1440x810) ----
	private static final float OUTER_R = 262;
	private static final float INNER_R = 140;
	private static final float CENTER_R = 122;
	private static final float CENTER_LINE_R = 113;
	private static final float DOTS_R = 300;
	private static final float GAP = 10;
	private static final float CORNER = 7;
	private static final float HOVER_PUSH = 9;
	private static final float HOVER_GROW = 8;
	private static final float LABEL_R = (INNER_R + OUTER_R) / 2;
	private static final int DOT_COUNT = 120;

	// ---- colours ----
	private static final int TEXT = 0xFFF4F8FB;
	private static final int OUTLINE = 0x4DFFFFFF;
	private static final int PVP_TINT = Colors.rgba(214, 92, 24, 0.40f);
	private static final int PVP_HOVER_TINT = Colors.rgba(214, 92, 24, 0.52f);
	private static final int DARK_TEXT = 0xFF0C1018;

	// ---- timings (ms, scaled by the animation speed setting) ----
	private static final float SEGMENT_IN = 550;
	private static final float SEGMENT_STAGGER = 50;
	private static final float OUT = 200;

	private final Animation center = new Animation(0);
	private final Animation chrome = new Animation(0);
	private final Animation[] appear = new Animation[RingSection.values().length];
	private final Animation[] hover = new Animation[RingSection.values().length];
	/** The segments shown (Visuals → Ring); read again each frame. */
	private RingSection[] sections = RingSection.active();
	private final Animation pvp = new Animation(PvpMode.isActive() ? 1 : 0);
	private final Animation pillPop = new Animation(1);
	private final Animation fx = new Animation(dev.mordo.kairo.core.VoidSettings.INSTANCE.master.isOn() ? 1 : 0);
	private final Animation fxPop = new Animation(1);
	private final Animation infoSwap = new Animation(1);

	private int selected = -1;
	private int shownInfo = -1;
	private int previousInfo = -1;
	private float lastMouseX = Float.NaN;
	private float lastMouseY = Float.NaN;
	private boolean hidden = true;
	private float viewWidth = 1440;
	private float viewHeight = 810;
	private final Map<String, List<String>> wrapCache = new HashMap<>();

	// ---- spinning ring (easter egg, see Settings → About) ----
	/** Where the ring rests, kept between openings: it lands where you leave it. */
	private static float spin;
	private static float spinVelocity;
	private boolean pressing;
	private boolean dragged;
	private int pressHit = -1;
	private float lastPointerAngle;
	private float dragTravel;
	private long lastDragNanos;
	private long lastFrameNanos;
	private int lastTickSector;

	RingPage(MenuScreen menu) {
		super(menu);
		for (int i = 0; i < appear.length; i++) {
			appear[i] = new Animation(0);
			hover[i] = new Animation(0);
		}
	}

	@Override
	public void show() {
		hidden = false;
		selected = -1;
		sections = RingSection.active();
		lastMouseX = Float.NaN;
		pvp.set(PvpMode.isActive() ? 1 : 0);
		fx.set(dev.mordo.kairo.core.VoidSettings.INSTANCE.master.isOn() ? 1 : 0);
		center.animateTo(1, SEGMENT_IN, Easing.UI);
		chrome.animateTo(1, 420, Easing.UI, 80);
		for (int i = 0; i < appear.length; i++) {
			appear[i].animateTo(1, SEGMENT_IN, Easing.UI, i * SEGMENT_STAGGER);
		}
	}

	@Override
	public void hide() {
		hidden = true;
		center.animateTo(0, OUT, Easing.IN_CUBIC);
		chrome.animateTo(0, OUT * 0.75f, Easing.IN_CUBIC);
		for (int i = 0; i < appear.length; i++) {
			appear[i].animateTo(0, OUT, Easing.IN_CUBIC, (appear.length - 1 - i) * 10);
		}
	}

	@Override
	public boolean isHidden() {
		if (!hidden || !center.isDone()) {
			return false;
		}
		for (Animation a : appear) {
			if (!a.isDone()) {
				return false;
			}
		}
		return true;
	}

	// ---- drawing ----

	@Override
	public void render(GuiGraphicsExtractor g, float mx, float my, float viewWidth, float viewHeight, boolean interactive) {
		this.viewWidth = viewWidth;
		this.viewHeight = viewHeight;
		float cx = viewWidth / 2;
		float cy = viewHeight / 2;
		sections = RingSection.active();
		HINT_KEYS[2] = "1–" + sections.length;
		updateSpin();
		if (spinning()) {
			lastMouseX = Float.NaN; // keep re-checking what's under the cursor while it turns
		}
		updateSelection(mx, my, cx, cy, interactive && !hidden);
		if (selected >= 0 && interactive && !hidden) {
			g.requestCursor(CursorTypes.POINTING_HAND);
		}
		if (alive()) {
			// The living ring: it moves. In Kairo Void it's just the ring; Kairo keeps the mode pill and key hints.
			drawAliveBehind(g, cx, cy);
			for (int i = 0; i < sections.length; i++) {
				drawSegment(g, sections[i], i, cx, cy);
			}
			drawCenter(g, cx, cy);
			drawAliveFront(g, cx, cy);
			if (!dev.mordo.kairo.core.Edition.isVoid()) {
				drawChrome(g);
			}
			return;
		}
		drawDots(g, cx, cy);
		for (int i = 0; i < sections.length; i++) {
			drawSegment(g, sections[i], i, cx, cy);
		}
		drawCenter(g, cx, cy);
		drawChrome(g);
	}

	// ---- the living ring ----

	private static boolean alive() {
		return ClientSettings.INSTANCE.livingRing.isOn();
	}

	private static float seconds() {
		return (System.nanoTime() % 3_600_000_000_000L) / 1e9f;
	}

	/** How far segment {@code i} breathes out right now: a slow wave running round the ring. */
	private float breathe(int i) {
		if (!alive() || ClientSettings.INSTANCE.reduceMotion.isOn()) {
			return 0;
		}
		double phase = seconds() * 2.0 - i * Math.PI * 2 / sections.length;
		return 3.5f * (float) (0.5 + 0.5 * Math.sin(phase));
	}

	/** Behind the segments: a soft glow under the hovered one. */
	private void drawAliveBehind(GuiGraphicsExtractor g, float cx, float cy) {
		float c = center.value();
		int accent = ClientSettings.INSTANCE.accent.argb();
		for (int i = 0; i < sections.length; i++) {
			float h = hover[i].value() * appear[i].value();
			if (h <= 0.01f) {
				continue;
			}
			float mid = midAngle(i) + spin;
			float push = HOVER_PUSH * h + breathe(i);
			for (int layer = 0; layer < 5; layer++) {
				float grow = 6 + layer * 7;
				Shapes.sector(g, cx + (float) Math.cos(mid) * push, cy + (float) Math.sin(mid) * push, INNER_R - grow * 0.4f, OUTER_R + HOVER_GROW + grow,
					mid, halfAngle(), GAP, CORNER + grow * 0.5f, Colors.withAlpha(accent, 0.05f * h * c), 0, 0);
			}
		}
	}

	/** In front: lights orbiting the rim, and a pulse leaving the centre. */
	private void drawAliveFront(GuiGraphicsExtractor g, float cx, float cy) {
		float c = center.value();
		if (c <= 0.01f) {
			return;
		}
		int accent = ClientSettings.INSTANCE.accent.argb();
		int hot = Colors.lerp(accent | 0xFF000000, 0xFFFFFFFF, 0.55f);
		float t = seconds();
		boolean still = ClientSettings.INSTANCE.reduceMotion.isOn();
		float scale = Animation.motion(0.6f + 0.4f * c, 1);
		// Two comets round the outside, one round the inside going the other way, each with a fading tail.
		comet(g, cx, cy, (OUTER_R + 14) * scale, 2.2f, still ? 0 : t * 0.9f, 0.9f, hot, c);
		comet(g, cx, cy, (OUTER_R + 14) * scale, 2.2f, still ? (float) Math.PI : t * 0.9f + (float) Math.PI, 0.9f, accent, c * 0.8f);
		comet(g, cx, cy, (INNER_R - 9) * scale, 1.6f, still ? 1 : -t * 1.3f, 0.7f, accent, c * 0.7f);
		// A ring of light leaving the centre every couple of seconds.
		float k = still ? 0.5f : (t % 2.4f) / 2.4f;
		float r = CENTER_R * scale * (0.85f + k * 0.35f);
		Shapes.ring(g, cx, cy, r, 1.4f, Colors.withAlpha(accent, 0.35f * (1 - k) * (1 - k) * c));
	}

	private static void comet(GuiGraphicsExtractor g, float cx, float cy, float radius, float thickness, float head, float length, int color, float alpha) {
		int pieces = 14;
		for (int i = 0; i < pieces; i++) {
			float k = i / (float) pieces;
			float start = head - length * (k + 1f / pieces);
			Shapes.arc(g, cx, cy, radius, thickness * (1 - k * 0.6f), start, length / pieces + 0.004f,
				Colors.withAlpha(color, alpha * (1 - k) * (1 - k)));
		}
		float hx = cx + (float) Math.cos(head) * radius;
		float hy = cy + (float) Math.sin(head) * radius;
		Shapes.shadow(g, hx - 3, hy - 3, 6, 6, 3, 7, Colors.withAlpha(color, 0.8f * alpha));
	}

	private void updateSelection(float mx, float my, float cx, float cy, boolean active) {
		// The mouse only takes over selection when it actually moves, so keyboard picks stick.
		boolean moved = Float.isNaN(lastMouseX) || Math.abs(mx - lastMouseX) > 0.5f || Math.abs(my - lastMouseY) > 0.5f;
		if (moved && active) {
			lastMouseX = mx;
			lastMouseY = my;
			selected = hitTest(mx - cx, my - cy);
		}
		for (int i = 0; i < hover.length; i++) {
			hover[i].animateTo(i == selected ? 1 : 0, 300);
		}
		if (selected != shownInfo) {
			previousInfo = shownInfo;
			shownInfo = selected;
			infoSwap.set(0).animateTo(1, 240, Easing.OUT_CUBIC);
		}
	}

	private float halfAngle() {
		return (float) (Math.PI / sections.length);
	}

	private float step() {
		return (float) (Math.PI * 2 / sections.length);
	}

	/** Centre angle of segment {@code index}, clockwise from +X (the first sits straight up). */
	private float midAngle(int index) {
		return (float) (-Math.PI / 2 + index * Math.PI * 2 / sections.length);
	}

	private int hitTest(float dx, float dy) {
		float r = (float) Math.sqrt(dx * dx + dy * dy);
		if (r < INNER_R - 2 || r > OUTER_R + HOVER_GROW + HOVER_PUSH + 4) {
			return -1;
		}
		double fromTop = Math.atan2(dy, dx) - spin + Math.PI / 2 + halfAngle();
		fromTop = ((fromTop % (Math.PI * 2)) + Math.PI * 2) % (Math.PI * 2);
		return (int) (fromTop / (halfAngle() * 2)) % sections.length;
	}

	private void drawDots(GuiGraphicsExtractor g, float cx, float cy) {
		int color = Colors.argb(0.17f * center.value(), 0xFFFFFF);
		if (Colors.alpha(color) == 0) {
			return;
		}
		for (int i = 0; i < DOT_COUNT; i++) {
			double a = i * Math.PI * 2 / DOT_COUNT + spin * 0.35;
			Shapes.circle(g, cx + (float) Math.cos(a) * DOTS_R, cy + (float) Math.sin(a) * DOTS_R, 1.3f, color);
		}
	}

	private void drawSegment(GuiGraphicsExtractor g, RingSection section, int i, float cx, float cy) {
		float a = appear[i].value();
		if (a <= 0.002f) {
			return;
		}
		ClientSettings s = ClientSettings.INSTANCE;
		float h = hover[i].value();
		float pvpOn = section == RingSection.PVP ? pvp.value() : 0;
		float rotation = Animation.motion((float) Math.toRadians(alive() ? -35 : -10) * (1 - a), 0);
		float scale = Animation.motion((alive() ? 0.6f : 0.8f) + (alive() ? 0.4f : 0.2f) * a, 1);
		float mid = midAngle(i) + spin;
		float push = HOVER_PUSH * h + breathe(i);

		// Hover fill follows the chosen accent (a little darker so labels stay readable).
		int hoverTint = Colors.withAlpha(Colors.lerp(s.accent.argb() | 0xFF000000, 0xFF000000, 0.28f), 0.34f);
		int tint = Colors.lerp(s.glassTint.argb(), hoverTint, h);
		int stroke = Colors.lerp(OUTLINE, s.accent.argb(), h);
		if (pvpOn > 0) {
			tint = Colors.lerp(tint, Colors.lerp(PVP_TINT, PVP_HOVER_TINT, h), pvpOn);
			stroke = Colors.lerp(stroke, s.pvpAccent.argb(), pvpOn);
		}

		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().rotate(rotation);
		g.pose().scale(scale, scale);
		Shapes.glassSector(g, (float) Math.cos(mid) * push, (float) Math.sin(mid) * push,
			INNER_R, OUTER_R + HOVER_GROW * h, mid, halfAngle(), GAP, CORNER, tint, stroke, 1.25f, a, 1.4f);
		g.pose().popMatrix();

		// The label follows its segment (rotation, push) but stays upright.
		float labelR = (LABEL_R + push + HOVER_GROW * h / 2) * scale;
		float lx = cx + (float) Math.cos(mid + rotation) * labelR;
		float ly = cy + (float) Math.sin(mid + rotation) * labelR;
		int textColor = Colors.withAlpha(TEXT, a);
		boolean pill = section == RingSection.PVP || section == RingSection.EFFECTS;
		float iconY = pill ? -24 : -14;

		g.pose().pushMatrix();
		g.pose().translate(lx, ly);
		g.pose().scale(scale, scale);
		float bob = alive() && !ClientSettings.INSTANCE.reduceMotion.isOn() ? (float) Math.sin(seconds() * 2.4 + i * 1.3) * 2 * (0.4f + 0.6f * h) : 0;
		if (alive() && h > 0.01f) {
			Shapes.shadow(g, -14, iconY - 14 + bob, 28, 28, 14, 10, Colors.withAlpha(s.accent.argb(), 0.35f * h * a));
		}
		IconRenderer.draw(g, section.icon, 0, iconY + bob, 26, alive() ? Colors.lerp(textColor, Colors.withAlpha(0xFFFFFF, a), h) : textColor, 1.8f);
		Fonts.drawCentered(g, Typeface.OUTFIT_SEMIBOLD, section.title, 0, iconY + 28, 17, textColor);
		if (section == RingSection.PVP) {
			drawPvpPill(g, 0, iconY + 50, a);
		} else if (section == RingSection.EFFECTS) {
			drawPill(g, 0, iconY + 50, a, fx.value(), fxPop.value(), s.accent.argb());
		}
		g.pose().popMatrix();
	}

	/** An OFF / ON pill in {@code color}. */
	private static void drawPill(GuiGraphicsExtractor g, float x, float y, float alpha, float on, float popValue, int color) {
		float pop = Animation.motion(0.86f + 0.14f * popValue, 1);
		float w = 40;
		float h = 18;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(pop, pop);
		int bg = Colors.lerp(0x29FFFFFF, color | 0xFF000000, on);
		Shapes.rect(g, -w / 2, -h / 2, w, h, h / 2, Colors.multiplyAlpha(bg, alpha));
		Fonts.draw(g, Typeface.MONO_BOLD, "OFF", 0, 0, 9, Colors.withAlpha(TEXT, alpha * (1 - on)), Fonts.Align.CENTER, 1.2f, false);
		Fonts.draw(g, Typeface.MONO_BOLD, "ON", 0, 0, 9, Colors.withAlpha(0xFFFFFFFF, alpha * on), Fonts.Align.CENTER, 1.2f, false);
		g.pose().popMatrix();
	}

	/** OFF (white 16%) / ON (PvP accent, dark text) pill under the PvP label. */
	private void drawPvpPill(GuiGraphicsExtractor g, float x, float y, float alpha) {
		float on = pvp.value();
		float pop = Animation.motion(0.86f + 0.14f * pillPop.value(), 1);
		int pvpAccent = ClientSettings.INSTANCE.pvpAccent.argb();
		float w = 40;
		float h = 18;
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(pop, pop);
		int bg = Colors.lerp(0x29FFFFFF, pvpAccent, on);
		Shapes.rect(g, -w / 2, -h / 2, w, h, h / 2, Colors.multiplyAlpha(bg, alpha));
		Fonts.draw(g, Typeface.MONO_BOLD, "OFF", 0, 0, 9, Colors.withAlpha(TEXT, alpha * (1 - on)), Fonts.Align.CENTER, 1.2f, false);
		Fonts.draw(g, Typeface.MONO_BOLD, "ON", 0, 0, 9, Colors.withAlpha(DARK_TEXT, alpha * on), Fonts.Align.CENTER, 1.2f, false);
		g.pose().popMatrix();
	}

	private void drawCenter(GuiGraphicsExtractor g, float cx, float cy) {
		float c = center.value();
		if (c <= 0.002f) {
			return;
		}
		float scale = Animation.motion(0.6f + 0.4f * c, 1);
		g.pose().pushMatrix();
		g.pose().translate(cx, cy);
		g.pose().scale(scale, scale);
		Shapes.glassSector(g, 0, 0, 0, CENTER_R, 0, (float) Math.PI, 0, 0,
			ClientSettings.INSTANCE.glassTint.argb(), OUTLINE, 1.25f, c, 1.4f);
		Shapes.ring(g, 0, 0, CENTER_LINE_R, 1, Colors.argb(0.13f * c, 0xFFFFFF));
		float t = infoSwap.value();
		if (Math.abs(spinVelocity) > 7 || (pressing && dragged && Math.abs(spinVelocity) > 4)) {
			drawInfo(g, -2, c, 0); // spinning fast
		} else {
			if (t < 1) {
				drawInfo(g, previousInfo, c * (1 - t), Animation.motion(-7 * t, 0));
			}
			drawInfo(g, shownInfo, c * t, Animation.motion(7 * (1 - t), 0));
		}
		g.pose().popMatrix();
	}

	/** Kicker, title and description for a section (or the idle text for -1), centred on the disc. */
	private void drawInfo(GuiGraphicsExtractor g, int index, float alpha, float dy) {
		if (alpha <= 0.002f) {
			return;
		}
		ClientSettings s = ClientSettings.INSTANCE;
		String kicker;
		String title;
		String description;
		int kickerColor = s.accent.argb();
		if (index == -2) {
			kicker = "SPINNING";
			title = "Wheee!";
			description = "Let it land on something";
		} else if (index < 0) {
			kicker = "RIGHT SHIFT MENU";
			title = "Menu";
			description = "Hover a section";
		} else {
			RingSection section = sections[index];
			kicker = section.kicker;
			title = section.title;
			description = section.description();
			if (section == RingSection.PVP) {
				kickerColor = Colors.lerp(kickerColor, s.pvpAccent.argb(), pvp.value());
			}
		}
		List<String> lines = wrapCache.computeIfAbsent(description,
			d -> Fonts.wrap(Typeface.OUTFIT_REGULAR, d, 11.5f, 188));
		float block = lines.size() * 15;
		float top = -12 - block / 2 + dy;
		Fonts.draw(g, Typeface.MONO_BOLD, kicker, 0, top - 22, 8.5f, Colors.multiplyAlpha(kickerColor, alpha), Fonts.Align.CENTER, 2f, false);
		Fonts.drawCentered(g, Typeface.OUTFIT_SEMIBOLD, title, 0, top + 4, 28, Colors.withAlpha(TEXT, alpha));
		for (int i = 0; i < lines.size(); i++) {
			Fonts.drawCentered(g, Typeface.OUTFIT_REGULAR, lines.get(i), 0, top + 32 + i * 15, 11.5f, Colors.withAlpha(TEXT, alpha * 0.72f));
		}
	}

	private void drawChrome(GuiGraphicsExtractor g) {
		float a = chrome.value();
		if (a <= 0.002f) {
			return;
		}
		ClientSettings s = ClientSettings.INSTANCE;
		float slide = Animation.motion((1 - a) * 10, 0);

		// Top-left: current mode.
		float on = pvp.value();
		float textSize = 11.5f;
		float wNormal = Fonts.width(Typeface.OUTFIT_SEMIBOLD, "Normal mode", textSize);
		float wPvp = Fonts.width(Typeface.OUTFIT_SEMIBOLD, "PvP mode", textSize);
		float pillW = 40 + Animation.lerp(wNormal, wPvp, on);
		float pillH = 28;
		float px = 26;
		float py = 26 - slide;
		int dot = Colors.lerp(s.accent.argb(), s.pvpAccent.argb(), on);
		Shapes.glassRect(g, px, py, pillW, pillH, pillH / 2, s.glassTint.argb(), 0x2EFFFFFF, 1.25f, a);
		float dotX = px + 16;
		float midY = py + pillH / 2;
		Shapes.shadow(g, dotX - 3.5f, midY - 3.5f, 7, 7, 3.5f, 5, Colors.multiplyAlpha(dot, 0.7f * a));
		Shapes.circle(g, dotX, midY, 3.5f, Colors.multiplyAlpha(dot, a));
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Normal mode", dotX + 11, midY, textSize, Colors.withAlpha(TEXT, a * (1 - on)));
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "PvP mode", dotX + 11, midY, textSize, Colors.withAlpha(TEXT, a * on));

		// Top-right: version.
		Fonts.draw(g, Typeface.MONO_MEDIUM, ClientInfo.MC_VERSION + " · " + ClientInfo.SHORT_VERSION, viewWidth - 28, midY, 10,
			Colors.withAlpha(TEXT, a * 0.75f), Fonts.Align.RIGHT, 0.8f, false);

		drawHints(g, viewHeight - 44 + slide, a);
	}

	private static final String[] HINT_KEYS = {"RSHIFT", "P", "1–6"};
	private static final String[] HINT_LABELS = {"Close", "Toggle PvP mode", "Jump to section"};
	private static final float HINT_CAP_SIZE = 8.5f;
	private static final float HINT_LABEL_SIZE = 10.5f;
	private static final float HINT_GAP = 10;
	private static final float HINT_H = 26;

	private void drawHints(GuiGraphicsExtractor g, float y, float alpha) {
		HINT_KEYS[0] = keyCapLabel(ClientKeybinds.INSTANCE.openMenu.get());
		float total = HINT_GAP * (HINT_KEYS.length - 1);
		for (int i = 0; i < HINT_KEYS.length; i++) {
			total += hintWidth(i);
		}
		float x = (viewWidth - total) / 2;
		int tint = ClientSettings.INSTANCE.glassTint.argb();
		for (int i = 0; i < HINT_KEYS.length; i++) {
			float w = hintWidth(i);
			float capW = keyCapWidth(i);
			Shapes.glassRect(g, x, y, w, HINT_H, 7, tint, 0x1FFFFFFF, 1f, alpha);
			Shapes.rect(g, x + 5, y + 5, capW, HINT_H - 10, 4, Colors.argb(0.10f * alpha, 0xFFFFFF), Colors.argb(0.22f * alpha, 0xFFFFFF), 1f);
			Fonts.draw(g, Typeface.MONO_BOLD, HINT_KEYS[i], x + 5 + capW / 2, y + HINT_H / 2, HINT_CAP_SIZE, Colors.withAlpha(TEXT, alpha),
				Fonts.Align.CENTER, 1, false);
			Fonts.draw(g, Typeface.OUTFIT_MEDIUM, HINT_LABELS[i], x + 5 + capW + 8, y + HINT_H / 2, HINT_LABEL_SIZE, Colors.withAlpha(TEXT, alpha * 0.85f));
			x += w + HINT_GAP;
		}
	}

	private static float keyCapWidth(int i) {
		return Fonts.width(Typeface.MONO_BOLD, HINT_KEYS[i], HINT_CAP_SIZE, 1) + 12;
	}

	private static float hintWidth(int i) {
		return 6 + keyCapWidth(i) + 8 + Fonts.width(Typeface.OUTFIT_MEDIUM, HINT_LABELS[i], HINT_LABEL_SIZE) + 11;
	}

	/** "Right Shift" -> "RSHIFT", "C" -> "C". */
	private static String keyCapLabel(Keybind bind) {
		String name = bind.displayName().getString().toUpperCase(Locale.ROOT);
		return name.replace("RIGHT ", "R").replace("LEFT ", "L").replace(" ", "");
	}

	// ---- input ----

	@Override
	public boolean onClick(float x, float y, int button) {
		if (hidden) {
			return false;
		}
		float dx = x - viewWidth / 2;
		float dy = y - viewHeight / 2;
		if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
			int hitRight = hitTest(dx, dy);
			if (hitRight >= 0 && sections[hitRight] == RingSection.PVP) {
				playClick();
				menu.navigate(menu.pvp()); // PvP mode options
			} else {
				menu.onClose();
			}
			return true;
		}
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		int hit = hitTest(dx, dy);
		if (hit >= 0 && spinEnabled()) {
			// Could be a click or the start of a spin: decide on release.
			pressing = true;
			dragged = false;
			pressHit = hit;
			dragTravel = 0;
			lastPointerAngle = (float) Math.atan2(dy, dx);
			lastDragNanos = System.nanoTime();
			spinVelocity = 0; // grabbing the ring stops it
			return true;
		}
		if (hit >= 0) {
			activate(sections[hit]);
			return true;
		}
		if (dx * dx + dy * dy > DOTS_R * DOTS_R) {
			menu.onClose(); // clicking empty space outside the ring closes the menu
			return true;
		}
		return false;
	}

	// ---- spinning ----

	private static boolean spinEnabled() {
		return ClientSettings.INSTANCE.ringSpin.isOn();
	}

	private boolean spinning() {
		return (pressing && dragged) || Math.abs(spinVelocity) > 0.05f;
	}

	@Override
	public boolean onDrag(float x, float y, int button) {
		if (!pressing) {
			return false;
		}
		float angle = (float) Math.atan2(y - viewHeight / 2, x - viewWidth / 2);
		float delta = (float) Math.IEEEremainder(angle - lastPointerAngle, Math.PI * 2);
		lastPointerAngle = angle;
		dragTravel += Math.abs(delta);
		if (dragTravel > 0.06f) {
			dragged = true;
		}
		if (dragged) {
			spin += delta;
			long now = System.nanoTime();
			float dt = Math.max(1e-3f, (now - lastDragNanos) / 1e9f);
			lastDragNanos = now;
			// Smoothed angular speed, so letting go flicks it.
			spinVelocity = spinVelocity * 0.5f + (delta / dt) * 0.5f;
		}
		return true;
	}

	@Override
	public boolean onRelease(float x, float y, int button) {
		if (!pressing) {
			return false;
		}
		pressing = false;
		if (!dragged) {
			spinVelocity = 0;
			activate(sections[pressHit]);
		} else if (System.nanoTime() - lastDragNanos > 80_000_000L) {
			spinVelocity = 0; // held still before letting go: no flick
		}
		spinVelocity = Math.clamp(spinVelocity, -45f, 45f);
		return true;
	}

	@Override
	public boolean onScroll(float x, float y, float amount) {
		float dx = x - viewWidth / 2;
		float dy = y - viewHeight / 2;
		if (!spinEnabled() || hidden || dx * dx + dy * dy > DOTS_R * DOTS_R) {
			return false;
		}
		spinVelocity = Math.clamp(spinVelocity - amount * 3.5f, -45f, 45f);
		return true;
	}

	/** Momentum, friction, and a gentle snap so a segment ends up centred at the top. */
	private void updateSpin() {
		long now = System.nanoTime();
		float dt = lastFrameNanos == 0 ? 0 : Math.min(0.05f, (now - lastFrameNanos) / 1e9f);
		lastFrameNanos = now;
		if (!spinEnabled()) {
			spinVelocity = 0;
			spin = wrapNear(spin, 0) * (float) Math.exp(-10 * dt); // settle back to normal
			if (Math.abs(spin) < 1e-3f) {
				spin = 0;
			}
			return;
		}
		if (pressing) {
			return;
		}
		if (Math.abs(spinVelocity) > 0.9f) {
			spin += spinVelocity * dt;
			spinVelocity *= (float) Math.exp(-1.1 * dt);
		} else {
			spinVelocity = 0;
			float target = Math.round(spin / step()) * step();
			spin += (target - spin) * (1 - (float) Math.exp(-12 * dt));
			if (Math.abs(target - spin) < 1e-4f) {
				spin = (float) Math.IEEEremainder(target, Math.PI * 2);
			}
		}
		// A soft tick each time a segment passes the top, like a prize wheel.
		int sector = (int) Math.floor((spin + halfAngle()) / step());
		if (sector != lastTickSector) {
			lastTickSector = sector;
			if (Math.abs(spinVelocity) > 1) {
				dev.mordo.kairo.sound.UiSounds.hover();
			}
		}
	}

	/** {@code angle} moved by whole turns to be as close as possible to {@code reference}. */
	private static float wrapNear(float angle, float reference) {
		return reference + (float) Math.IEEEremainder(angle - reference, Math.PI * 2);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (hidden) {
			return false;
		}
		if (event.key() == InputConstants.KEY_P) {
			togglePvp();
			return true;
		}
		OptionalInt digit = InputConstants.getKey(event).getNumericKeyValue();
		if (digit.isPresent() && digit.getAsInt() >= 1 && digit.getAsInt() <= sections.length) {
			selected = digit.getAsInt() - 1;
			activate(sections[selected]);
			return true;
		}
		if (event.isCycleFocus()) {
			moveSelection(event.hasShiftDown() ? -1 : 1);
			return true;
		}
		if (event.isRight() || event.isDown()) {
			moveSelection(1);
			return true;
		}
		if (event.isLeft() || event.isUp()) {
			moveSelection(-1);
			return true;
		}
		if ((event.isConfirmation() || event.isSelection()) && selected >= 0 && selected < sections.length) {
			activate(sections[selected]);
			return true;
		}
		return false;
	}

	private void moveSelection(int direction) {
		int n = sections.length;
		selected = selected < 0 ? (direction > 0 ? 0 : n - 1) : Math.floorMod(selected + direction, n);
	}

	private void activate(RingSection section) {
		if (section != RingSection.PVP && section != RingSection.EFFECTS) {
			playClick();
		}
		switch (section) {
			case PVP -> togglePvp();
			case EFFECTS -> {
				dev.mordo.kairo.render.Visuals.toggle();
				fx.animateTo(dev.mordo.kairo.core.VoidSettings.INSTANCE.master.isOn() ? 1 : 0, 320);
				fxPop.set(0).animateTo(1, 480, Easing.OUT_BACK);
			}
			case MODS -> menu.navigate(menu.mods());
			case SETTINGS -> menu.navigate(menu.settings());
			case VISUALS -> menu.navigate(menu.visualsPage());
			case COSMETICS -> menu.navigate(menu.cosmetics());
			case LAYOUT -> {
				if (net.minecraft.client.Minecraft.getInstance().player == null) {
					dev.mordo.kairo.gui.Toasts.show("Join a world to edit the HUD layout", dev.mordo.kairo.gui.Theme.accent());
				} else {
					menu.close(new LayoutScreen());
				}
			}
			case PVP_OPTIONS -> menu.navigate(menu.pvp());
			case WAYPOINTS -> {
				if (net.minecraft.client.Minecraft.getInstance().player == null) {
					dev.mordo.kairo.gui.Toasts.show("Join a world to see your waypoints", dev.mordo.kairo.gui.Theme.accent());
				} else {
					menu.close(new dev.mordo.kairo.mods.waypoints.WaypointsScreen(null));
				}
			}
			case EMOTES -> {
				if (net.minecraft.client.Minecraft.getInstance().player == null) {
					dev.mordo.kairo.gui.Toasts.show("Join a world to emote", dev.mordo.kairo.gui.Theme.accent());
				} else {
					menu.close(new dev.mordo.kairo.cosmetics.EmoteWheelScreen());
				}
			}
			case SCREENSHOTS -> dev.mordo.kairo.gui.FilePicker.openFolder(
				net.minecraft.client.Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots"));
		}
	}

	private void togglePvp() {
		PvpMode.toggle();
		pvp.animateTo(PvpMode.isActive() ? 1 : 0, 320);
		pillPop.set(0).animateTo(1, 480, Easing.OUT_BACK); // the PvP chime plays from PvpMode's listener
	}

	private static void playClick() {
		dev.mordo.kairo.sound.UiSounds.click();
	}
}
