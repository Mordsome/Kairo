package dev.mordo.kairo.gui;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.ClientSettings.ClickEffect;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.WeakHashMap;

/**
 * Animation state for Minecraft's own buttons (Visuals → Buttons): a smooth hover fade, a short fade-in when a
 * menu opens, and click effects. Effects that belong to the button (ripple, shine, rainbow, pop) are drawn with
 * it; the rest (sparks, shockwave, confetti) are an overlay on top of the screen, so they still play when the
 * click opens another menu.
 */
public final class ButtonFx {
	/** Per-button state, keyed weakly by the button object. */
	public static final class State {
		float hover;
		long lastNanos;
		long firstSeen;
		long clickAt;
		float clickX;
		float clickY;
	}

	private static final Map<Object, State> STATES = new WeakHashMap<>();
	private static final List<Particle> PARTICLES = new ArrayList<>();
	private static final Random RANDOM = new Random();
	private static final long INTRO_NANOS = 220_000_000L;
	private static final long CLICK_NANOS = 520_000_000L;

	private ButtonFx() {
	}

	public static State state(Object button) {
		return STATES.computeIfAbsent(button, k -> new State());
	}

	private static boolean animated() {
		return ClientSettings.INSTANCE.buttonAnimations.isOn() && !ClientSettings.INSTANCE.reduceMotion.isOn();
	}

	/** Hover amount 0..1, eased when animations are on. */
	public static float hover(State s, boolean hovered) {
		long now = System.nanoTime();
		float dt = s.lastNanos == 0 ? 1 : Math.min(0.1f, (now - s.lastNanos) / 1e9f);
		s.lastNanos = now;
		float target = hovered ? 1 : 0;
		if (!animated()) {
			s.hover = target;
		} else {
			s.hover += (target - s.hover) * (1 - (float) Math.exp(-(hovered ? 16 : 9) * dt));
		}
		return s.hover;
	}

	/** Fade-in when a button first appears; {@code delayFraction} 0..1 staggers rows top to bottom. */
	public static float intro(State s, float delayFraction) {
		long now = System.nanoTime();
		if (s.firstSeen == 0) {
			s.firstSeen = now;
		}
		if (!animated()) {
			return 1;
		}
		long delay = (long) (Math.clamp(delayFraction, 0, 1) * 110_000_000L);
		float t = Math.clamp((now - s.firstSeen - delay) / (float) INTRO_NANOS, 0, 1);
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}

	public static void restartIntro(State s) {
		s.firstSeen = System.nanoTime();
	}

	/** 0 right at a click, 1 once the click effect is over (or there was none). */
	public static float clickProgress(State s) {
		if (s.clickAt == 0) {
			return 1;
		}
		return Math.clamp((System.nanoTime() - s.clickAt) / (float) CLICK_NANOS, 0, 1);
	}

	/** A button was clicked at (x, y), GUI pixels. */
	public static void click(State s, float x, float y) {
		click(s, x, y, x, y);
	}

	/** A click at (x, y) in the button's own drawing units, and (overlayX, overlayY) in GUI pixels for the overlay. */
	public static void click(State s, float x, float y, float overlayX, float overlayY) {
		s.clickAt = System.nanoTime();
		s.clickX = x;
		s.clickY = y;
		spawnOverlay(overlayX, overlayY);
	}

	/** Scale for the Pop effect (1 = normal). */
	public static float popScale(State s) {
		if (ClientSettings.INSTANCE.buttonClick.get() != ClickEffect.POP) {
			return 1;
		}
		float t = clickProgress(s);
		if (t >= 1) {
			return 1;
		}
		// Quick squash, then a little overshoot.
		return t < 0.25f ? 1 - 0.07f * (t / 0.25f) : 0.93f + 0.07f * (float) (1 + Math.sin((t - 0.25f) / 0.75f * Math.PI) * 0.6 * (1 - t));
	}

	/** Effects drawn on the button itself, clipped to it. */
	public static void drawOnButton(GuiGraphicsExtractor g, State s, int x, int y, int w, int h, float radius, float alpha) {
		ClickEffect effect = ClientSettings.INSTANCE.buttonClick.get();
		float t = clickProgress(s);
		if (t >= 1 || effect == ClickEffect.NONE) {
			return;
		}
		int accent = Theme.accent();
		switch (effect) {
			case RIPPLE -> {
				g.enableScissor(x, y, x + w, y + h);
				float maxR = (float) Math.hypot(Math.max(s.clickX - x, x + w - s.clickX), Math.max(s.clickY - y, y + h - s.clickY));
				float r = maxR * (1 - (1 - t) * (1 - t));
				Shapes.circle(g, s.clickX, s.clickY, r, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.35f * (1 - t)), alpha));
				g.disableScissor();
			}
			case SHINE -> {
				g.enableScissor(x, y, x + w, y + h);
				float band = h * 1.2f;
				float cx = x - band + (w + band * 2) * t;
				for (int i = 0; i < 6; i++) {
					float off = (i - 2.5f) * band / 6;
					Shapes.line(g, cx + off - h * 0.5f, y + h, cx + off + h * 0.5f, y, band / 6,
						Colors.argb(0.16f * alpha * (1 - Math.abs(i - 2.5f) / 3f), 0xFFFFFF));
				}
				g.disableScissor();
			}
			case RAINBOW -> {
				int color = Colors.hsv((t * 1.5f) % 1f, 0.75f, 1f);
				Shapes.outline(g, x - 0.5f, y - 0.5f, w + 1, h + 1, radius + 0.5f, Colors.multiplyAlpha(Colors.withAlpha(color, 1 - t * t), alpha), 1.5f);
			}
			default -> {
			}
		}
	}

	// ---- overlay effects ----

	private record Particle(float x0, float y0, float vx, float vy, float gravity, long born, long life, int color, float size,
							float spin, int kind) {
	}

	private static final int SPARK = 0;
	private static final int RING = 1;
	private static final int CONFETTI = 2;

	private static void spawnOverlay(float x, float y) {
		ClickEffect effect = ClientSettings.INSTANCE.buttonClick.get();
		long now = System.nanoTime();
		int accent = Theme.accent();
		switch (effect) {
			case SPARKS -> {
				for (int i = 0; i < 16; i++) {
					double a = RANDOM.nextDouble() * Math.PI * 2;
					float speed = 50 + RANDOM.nextFloat() * 90;
					int color = Colors.lerp(accent, 0xFFFFFFFF, RANDOM.nextFloat() * 0.6f);
					PARTICLES.add(new Particle(x, y, (float) Math.cos(a) * speed, (float) Math.sin(a) * speed - 20, 160,
						now, 380_000_000L + RANDOM.nextInt(260_000_000), color, 0.8f + RANDOM.nextFloat() * 0.9f, 0, SPARK));
				}
			}
			case SHOCKWAVE -> {
				PARTICLES.add(new Particle(x, y, 0, 0, 0, now, 480_000_000L, accent, 46, 0, RING));
				PARTICLES.add(new Particle(x, y, 0, 0, 0, now + 70_000_000L, 420_000_000L, 0xFFFFFFFF, 30, 0, RING));
			}
			case CONFETTI -> {
				int[] colors = {0xFFFF5A5F, 0xFFFFC94D, 0xFF8CE05A, 0xFF7FD8FF, 0xFFB06BFF, 0xFFFF9A52};
				for (int i = 0; i < 28; i++) {
					double a = -Math.PI / 2 + (RANDOM.nextDouble() - 0.5) * 2.2;
					float speed = 70 + RANDOM.nextFloat() * 110;
					PARTICLES.add(new Particle(x, y, (float) Math.cos(a) * speed, (float) Math.sin(a) * speed, 260,
						now, 800_000_000L + RANDOM.nextInt(400_000_000), colors[RANDOM.nextInt(colors.length)],
						1.5f + RANDOM.nextFloat() * 1.5f, (RANDOM.nextFloat() - 0.5f) * 20, CONFETTI));
				}
			}
			default -> {
			}
		}
		if (PARTICLES.size() > 400) {
			PARTICLES.subList(0, PARTICLES.size() - 400).clear();
		}
	}

	/** Draws the overlay effects on top of the current screen (GUI pixels). */
	public static void drawOverlay(GuiGraphicsExtractor g) {
		if (PARTICLES.isEmpty()) {
			return;
		}
		long now = System.nanoTime();
		g.nextStratum();
		Iterator<Particle> it = PARTICLES.iterator();
		while (it.hasNext()) {
			Particle p = it.next();
			if (now < p.born()) {
				continue;
			}
			float age = (now - p.born()) / 1e9f;
			float t = (now - p.born()) / (float) p.life();
			if (t >= 1) {
				it.remove();
				continue;
			}
			float x = p.x0() + p.vx() * age;
			float y = p.y0() + p.vy() * age + 0.5f * p.gravity() * age * age;
			float fade = 1 - t * t;
			switch (p.kind()) {
				case SPARK -> {
					float drag = (float) Math.exp(-age * 2.5);
					float sx = p.x0() + p.vx() * (1 - drag) / 2.5f;
					float sy = p.y0() + p.vy() * (1 - drag) / 2.5f + 0.5f * p.gravity() * age * age * 0.4f;
					Shapes.circle(g, sx, sy, p.size() * (1 - t * 0.5f), Colors.multiplyAlpha(p.color(), fade));
				}
				case RING -> {
					float r = p.size() * (1 - (1 - t) * (1 - t));
					Shapes.ring(g, p.x0(), p.y0(), r, 1.5f * (1 - t) + 0.4f, Colors.multiplyAlpha(Colors.withAlpha(p.color(), 0.85f), fade));
				}
				case CONFETTI -> {
					g.pose().pushMatrix();
					g.pose().translate(x, y);
					g.pose().rotate(p.spin() * age);
					Shapes.rect(g, -p.size(), -p.size() * 0.5f, p.size() * 2, p.size(), 0.3f, Colors.multiplyAlpha(p.color(), fade));
					g.pose().popMatrix();
				}
				default -> {
				}
			}
		}
	}

	/** The mouse position in GUI pixels. */
	public static float[] cursorGui() {
		Minecraft mc = Minecraft.getInstance();
		var window = mc.getWindow();
		double sx = mc.mouseHandler.xpos() * window.getGuiScaledWidth() / Math.max(1, window.getScreenWidth());
		double sy = mc.mouseHandler.ypos() * window.getGuiScaledHeight() / Math.max(1, window.getScreenHeight());
		return new float[]{(float) sx, (float) sy};
	}

}
