package dev.mordo.kairo.hud;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.gui.layout.LayoutScreen;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.UiScale;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Draws every enabled {@link HudElement} as one layer on top of the vanilla HUD. */
public final class HudManager {
	private static final List<HudElement> ELEMENTS = new ArrayList<>();
	private static KeystrokesHud keystrokes;
	private static CpsHud cps;

	private HudManager() {
	}

	public static void init() {
		for (Module module : ModuleManager.modules()) {
			if (module instanceof HudElement element) {
				ELEMENTS.add(element);
			}
		}
		keystrokes = ModuleManager.get(KeystrokesHud.class);
		cps = ModuleManager.get(CpsHud.class);
		// Normal and PvP layouts can differ: slide elements across instead of jumping.
		PvpMode.addListener(active -> startGlide());
		HudElementRegistry.addLast(ClientInfo.id("hud"), (g, delta) -> {
			Minecraft mc = Minecraft.getInstance();
			// The layout editor draws the elements itself (with outlines and dragging).
			if (mc.player != null && !mc.gui.hud.isHidden() && !(mc.gui.screen() instanceof LayoutScreen)) {
				renderAll(g, false);
			}
		});
	}

	public static List<HudElement> elements() {
		return Collections.unmodifiableList(ELEMENTS);
	}

	/** On, and not stepping aside (CPS hides while the Keystrokes mouse buttons show it, if the player wants that). */
	public static boolean isShown(HudElement element) {
		return element.isEnabled() && !isCovered(element);
	}

	/** CPS is on but hidden because Keystrokes shows the CPS (in the mode the player is in). */
	public static boolean isCovered(HudElement element) {
		return element == cps && cps.coveredByKeystrokes();
	}

	public static void renderAll(GuiGraphicsExtractor g, boolean preview) {
		for (HudElement element : ELEMENTS) {
			if (isShown(element)) {
				prepare(element, preview);
				float[] p = place(element, g.guiWidth(), g.guiHeight());
				render(g, element, p[0], p[1], p[2], preview);
			} else {
				element.drawnLastFrame = false;
			}
		}
	}

	// ---- vanilla effect icons ----

	private static long effectsFrame = -1;
	private static float effectIconsBottomCache;
	private static float effectIconsWidth;

	/**
	 * Bottom edge (GUI px) of the effect icons vanilla draws in the top-right corner (0 if none), worked out once
	 * per frame: beneficial effects take the first row, harmful ones a second row, 25 px per icon.
	 */
	private static float effectIconsBottom() {
		Minecraft mc = Minecraft.getInstance();
		long frame = mc.getFrameTimeNs();
		if (frame == effectsFrame) {
			return effectIconsBottomCache;
		}
		effectsFrame = frame;
		int good = 0;
		int bad = 0;
		if (mc.player != null && !(mc.gui.screen() instanceof LayoutScreen)) {
			for (var effect : mc.player.getActiveEffects()) {
				if (effect.showIcon()) {
					if (effect.getEffect().value().isBeneficial()) {
						good++;
					} else {
						bad++;
					}
				}
			}
		}
		effectIconsWidth = Math.max(good, bad) * 25 + 2;
		// Harmful effects always use the second row, even when the first is empty.
		effectIconsBottomCache = bad > 0 ? 52 : good > 0 ? 26 : 0;
		return effectIconsBottomCache;
	}

	// ---- gliding between layouts ----

	private static final float GLIDE_MS = 340;
	private static final float[] PLACE = new float[3];
	private static long glideStart;
	private static boolean gliding;

	/**
	 * Makes every element that is on screen now slide to wherever its (new) position is.
	 * Called when PvP mode flips and when the layout editor resets positions.
	 */
	public static void startGlide() {
		if (Animation.reduceMotion()) {
			return;
		}
		for (HudElement element : ELEMENTS) {
			element.glides = element.drawnLastFrame;
			element.fromX = element.lastX;
			element.fromY = element.lastY;
			element.fromScale = element.lastScale;
		}
		glideStart = Animation.now();
		gliding = true;
	}

	/**
	 * Where to draw an element this frame: x, y and scale in GUI pixels, mid-glide if one is running.
	 * Call after {@link #prepare}. Returns a shared array (read it right away).
	 */
	public static float[] place(HudElement element, float guiWidth, float guiHeight) {
		float x = x(element, guiWidth);
		float y = y(element, guiHeight);
		float s = scale(element);
		// Step out of the way of vanilla's potion effect icons (top-right corner).
		float effectsBottom = effectIconsBottom();
		if (effectsBottom > 0 && y < effectsBottom && x + element.width() * s > guiWidth - effectIconsWidth) {
			y = effectsBottom + 2;
		}
		if (gliding) {
			float t = (Animation.now() - glideStart) / 1e6f / (GLIDE_MS / Animation.speed());
			if (t >= 1) {
				gliding = false;
			} else if (element.glides) {
				float e = Easing.UI.apply(Math.max(0, t));
				x = Animation.lerp(element.fromX, x, e);
				y = Animation.lerp(element.fromY, y, e);
				s = Animation.lerp(element.fromScale, s, e);
			}
		}
		element.lastX = x;
		element.lastY = y;
		element.lastScale = s;
		element.drawnLastFrame = true;
		PLACE[0] = x;
		PLACE[1] = y;
		PLACE[2] = s;
		return PLACE;
	}

	/** Runs layout for this frame (call before reading width/height or position). */
	public static void prepare(HudElement element, boolean preview) {
		if (element == cps && isCpsAttached()) {
			keystrokes.layout(preview);
		}
		element.layout(preview);
	}

	/** Draws an element with its top-left at (x, y) in GUI pixels. */
	public static void render(GuiGraphicsExtractor g, HudElement element, float x, float y, float scale, boolean preview) {
		g.pose().pushMatrix();
		g.pose().translate(x, y);
		g.pose().scale(scale, scale);
		UiScale.push(Minecraft.getInstance().getWindow().getGuiScale() * scale);
		try {
			element.draw(g, preview);
		} finally {
			UiScale.pop();
			g.pose().popMatrix();
		}
	}

	/**
	 * Draws an element centred on (cx, cy) inside a screen using reference units ({@code unit} GUI pixels each),
	 * at its real on-screen size, shrunk only if it wouldn't fit in maxW x maxH.
	 */
	public static void renderPreview(GuiGraphicsExtractor g, HudElement element, float cx, float cy, float maxW, float maxH, float unit) {
		prepare(element, false);
		float scale = scale(element);
		float w = element.width() * scale / unit;
		float h = element.height() * scale / unit;
		float fit = Math.min(1, Math.min(maxW / Math.max(w, 1), maxH / Math.max(h, 1)));
		float local = scale * fit / unit; // reference units per HUD unit
		g.pose().pushMatrix();
		g.pose().translate(cx - w * fit / 2, cy - h * fit / 2);
		g.pose().scale(local, local);
		UiScale.push(Minecraft.getInstance().getWindow().getGuiScale() * scale * fit);
		try {
			element.draw(g, false);
		} finally {
			UiScale.pop();
			g.pose().popMatrix();
		}
	}

	// ---- positions (the CPS counter can sit right under Keystrokes) ----

	public static boolean isCpsAttached() {
		return cps.attachToKeystrokes.isOn() && keystrokes.isEnabled();
	}

	public static boolean isAttached(HudElement element) {
		return element == cps && isCpsAttached();
	}

	public static float x(HudElement element, float guiWidth) {
		return isAttached(element) ? keystrokes.screenX(guiWidth) : element.screenX(guiWidth);
	}

	public static float y(HudElement element, float guiHeight) {
		if (isAttached(element)) {
			return keystrokes.screenY(guiHeight) + (keystrokes.height() + keystrokes.gap.floatValue()) * scale(keystrokes);
		}
		return element.screenY(guiHeight);
	}

	public static float scale(HudElement element) {
		return isAttached(element) ? keystrokes.scaleValue() : element.scaleValue();
	}

	/** Width CPS should use when attached (the Keystrokes box width), else 0. */
	static float attachedWidth() {
		return isCpsAttached() ? keystrokes.width() : 0;
	}
}
