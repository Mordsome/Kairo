package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;

/**
 * "Orbit" main menu: the entries are glass planets circling the Kairo mark on a tilted ring in front of a
 * drifting starfield. The one at the front is the one you're on: click it (or Enter) to open it, click another to
 * swing it round to the front, drag or scroll to spin the whole system, ←/→ to step. Everything leans a little
 * towards the mouse.
 */
final class OrbitMenu {
	private static final int STARS = 140;
	private static final float SPIN_IDLE = 0.07f;

	private final float[][] stars = new float[STARS][4];
	private float angle = (float) (Math.PI / 2);
	private float target = Float.NaN;
	private float velocity;
	private long lastNanos;
	private long lastInteraction;
	private boolean dragging;
	private float dragStartX;
	private float dragLastX;

	private int hovered = -1;
	private float parallaxX;
	private float parallaxY;
	private final Animation pulse = new Animation(0);
	/** Planet centres and radii from the last frame (for clicks). */
	private final List<float[]> hits = new ArrayList<>();

	OrbitMenu() {
		Random random = new Random(1337);
		for (float[] s : stars) {
			s[0] = random.nextFloat();
			s[1] = random.nextFloat();
			s[2] = 0.3f + random.nextFloat() * 0.7f; // depth (parallax and size)
			s[3] = random.nextFloat() * 6.28f; // twinkle phase
		}
	}

	void reset() {
		lastNanos = 0;
		target = Float.NaN;
		velocity = 0;
		pulse.set(0).animateTo(1, 900);
	}

	private static float step(int count) {
		return (float) (Math.PI * 2 / Math.max(1, count));
	}

	/** The index of the planet nearest the front (bottom of the ellipse, angle π/2). */
	int frontIndex(int count) {
		float s = step(count);
		float rel = (float) (Math.PI / 2 - angle);
		return Math.floorMod(Math.round(rel / s), count);
	}

	private void spinTo(int index, int count) {
		float s = step(count);
		float desired = (float) (Math.PI / 2 - index * s);
		// Shortest way round.
		float delta = (float) Math.IEEEremainder(desired - angle, Math.PI * 2);
		target = angle + delta;
		velocity = 0;
		lastInteraction = System.currentTimeMillis();
	}

	/** Starfield and a deep vignette, in GUI pixels. */
	void drawBackground(GuiGraphicsExtractor g, float a) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, Colors.argb(0.55f, 0x03050A));
		Shapes.gradientRect(g, 0, 0, w, h * 0.35f, 0, Colors.argb(0.5f, 0x03050A), Colors.argb(0, 0x03050A));
		Shapes.gradientRect(g, 0, h * 0.6f, w, h * 0.4f, 0, Colors.argb(0, 0x03050A), Colors.argb(0.65f, 0x03050A));
		long now = System.currentTimeMillis();
		float drift = (now % 600000L) / 1000f;
		int accent = Theme.accent();
		for (float[] s : stars) {
			float x = (float) ((s[0] * w + drift * 4 * s[2] + parallaxX * 18 * s[2]) % w);
			if (x < 0) {
				x += w;
			}
			float y = s[1] * h + parallaxY * 12 * s[2];
			float tw = 0.5f + 0.5f * (float) Math.sin(now / 700.0 + s[3]);
			float size = 0.35f + s[2] * 0.6f;
			int color = s[3] > 5.6f ? accent : 0xFFFFFFFF;
			Shapes.circle(g, x, y, size, Colors.argb((0.25f + 0.55f * tw) * s[2] * a, color & 0xFFFFFF));
		}
	}

	void draw(GuiGraphicsExtractor g, List<MenuRow> rows, float viewW, float viewH, float mx, float my, float a, boolean live) {
		long now = System.nanoTime();
		float dt = lastNanos == 0 ? 0 : Math.min(0.05f, (now - lastNanos) / 1e9f);
		lastNanos = now;
		int count = rows.size();
		float cx = viewW / 2;
		float cy = viewH * 0.47f;
		parallaxX += ((mx - cx) / viewW - parallaxX) * Math.min(1, dt * 4);
		parallaxY += ((my - cy) / viewH - parallaxY) * Math.min(1, dt * 4);

		// Motion: free spin after a flick, easing to a target, or a slow idle drift.
		boolean idle = System.currentTimeMillis() - lastInteraction > 6000 && hovered < 0 && !dragging;
		if (!dragging) {
			if (Math.abs(velocity) > 0.02f) {
				angle += velocity * dt;
				velocity *= (float) Math.exp(-2.2 * dt);
				if (Math.abs(velocity) <= 0.25f) {
					velocity = 0;
					spinTo(frontIndex(count), count);
				}
			} else if (!Float.isNaN(target)) {
				angle += (target - angle) * (1 - (float) Math.exp(-7 * dt));
				if (Math.abs(target - angle) < 0.0005f) {
					angle = target;
					target = Float.NaN;
				}
			} else if (idle) {
				angle += SPIN_IDLE * dt;
			}
		}

		float rx = Math.min(viewW * 0.34f, 470);
		float ry = Math.min(viewH * 0.16f, 130);
		// The whole system leans towards the mouse.
		float tiltX = parallaxX * 26;
		float tiltY = parallaxY * 14;

		// Wordmark.
		float rise = Animation.motion((1 - a) * 12, 0);
		Fonts.draw(g, Typeface.MONO_BOLD, String.join(" ", ClientInfo.NAME.toUpperCase(java.util.Locale.ROOT).split("")), cx, 92 + rise, 11, Colors.multiplyAlpha(Theme.accent(), a), Fonts.Align.CENTER, 6, false);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Client for Minecraft " + ClientInfo.MC_VERSION, cx, 116 + rise, 12.5f, Theme.text(0.55f, a),
			Fonts.Align.CENTER, 0, false);

		// Orbit path: a dotted tilted ellipse.
		for (int i = 0; i < 160; i++) {
			double t = i * Math.PI * 2 / 160 + angle * 0.25;
			float depth = (float) Math.sin(t);
			float px = cx + tiltX + (float) Math.cos(t) * rx;
			float py = cy + tiltY + depth * ry;
			Shapes.circle(g, px, py, 0.9f + depth * 0.3f, Colors.argb((0.10f + 0.12f * (depth + 1)) * a, 0xFFFFFF));
		}

		// Planets, back to front; the core goes in between.
		List<Planet> planets = new ArrayList<>();
		float s = step(count);
		for (int i = 0; i < count; i++) {
			double t = angle + i * s;
			float depth = (float) Math.sin(t);
			planets.add(new Planet(i, cx + tiltX + (float) Math.cos(t) * rx, cy + tiltY + depth * ry, depth));
		}
		planets.sort(Comparator.comparingDouble(Planet::depth));
		hits.clear();
		for (int i = 0; i < count; i++) {
			hits.add(null);
		}
		// Hover: the topmost planet under the cursor.
		hovered = -1;
		if (live && !dragging) {
			for (int k = planets.size() - 1; k >= 0; k--) {
				Planet p = planets.get(k);
				float r = radius(p.depth());
				if (Math.hypot(mx - p.x(), my - p.y()) <= r) {
					hovered = p.index();
					break;
				}
			}
		}
		int front = frontIndex(count);
		boolean coreDrawn = false;
		for (Planet p : planets) {
			if (!coreDrawn && p.depth() > 0) {
				drawCore(g, cx + tiltX * 0.6f, cy + tiltY * 0.6f, a);
				coreDrawn = true;
			}
			drawPlanet(g, rows.get(p.index()), p, p.index() == front, p.index() == hovered, a);
			hits.set(p.index(), new float[]{p.x(), p.y(), radius(p.depth())});
		}
		if (!coreDrawn) {
			drawCore(g, cx + tiltX * 0.6f, cy + tiltY * 0.6f, a);
		}

		// The front entry's description, under the system.
		if (count > 0) {
			MenuRow row = rows.get(front);
			float dy = cy + ry + 92;
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, row.subtitle(), cx, dy, 13, Theme.text(0.6f, a), Fonts.Align.CENTER, 0, false);
			Fonts.draw(g, Typeface.MONO_MEDIUM, "drag or ←/→ to spin   ·   click the front planet or Enter", cx, dy + 26, 9, Theme.text(0.35f, a),
				Fonts.Align.CENTER, 0.6f, false);
		}
	}

	private static float radius(float depth) {
		return 46 * (0.62f + 0.38f * (depth + 1) / 2);
	}

	private void drawCore(GuiGraphicsExtractor g, float x, float y, float a) {
		long now = System.currentTimeMillis();
		float spin = (now % 20000L) / 20000f * (float) Math.PI * 2;
		float breathe = 0.5f + 0.5f * (float) Math.sin(now / 900.0);
		int accent = Theme.accent();
		int pvp = Theme.pvpAccent();
		float intro = pulse.value();
		float scale = Animation.motion(0.6f + 0.4f * intro, 1);
		float r = 46 * scale;
		Shapes.shadow(g, x - r, y - r, r * 2, r * 2, r, 26 + breathe * 10, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.35f), a));
		// Ring with the orange arc in its gap, slowly turning (the client's logo).
		float gap = 0.82f;
		Shapes.arc(g, x, y, r, 7 * scale, spin - (float) Math.PI / 2 + gap, (float) Math.PI * 2 - gap * 2, Colors.multiplyAlpha(accent, a));
		Shapes.arc(g, x, y, r, 7 * scale, spin - (float) Math.PI / 2 - 0.3f, 0.6f, Colors.multiplyAlpha(pvp, a));
		Shapes.circle(g, x, y, (14 + breathe * 2) * scale, Colors.multiplyAlpha(accent, a));
		// Two little moons.
		for (int i = 0; i < 2; i++) {
			double t = now / (i == 0 ? 1400.0 : 2300.0) + i * 2;
			float mr = (r + 14 + i * 9);
			Shapes.circle(g, x + (float) Math.cos(t) * mr, y + (float) Math.sin(t) * mr * 0.5f, 2 - i * 0.6f,
				Colors.multiplyAlpha(i == 0 ? 0xFFFFFFFF : pvp, a * 0.8f));
		}
	}

	private record Planet(int index, float x, float y, float depth) {
	}

	private void drawPlanet(GuiGraphicsExtractor g, MenuRow row, Planet planet, boolean front, boolean hover, float a) {
		float px = planet.x();
		float py = planet.y();
		float depth = planet.depth();
		float r = radius(depth) * (hover ? 1.08f : 1);
		float near = (depth + 1) / 2;
		float alpha = a * (0.45f + 0.55f * near) * (row.isEnabled() ? 1 : 0.5f);
		int accent = row.accentColor();
		int tint = Colors.lerp(Theme.glassTint(), Colors.withAlpha(Colors.lerp(Theme.glassTint() | 0xFF000000, accent, 0.25f), 0.6f), front || hover ? 1 : 0);
		int stroke = front ? Colors.withAlpha(accent, 0.9f) : hover ? Colors.withAlpha(accent, 0.6f) : 0x33FFFFFF;
		if (front) {
			Shapes.shadow(g, px - r, py - r, r * 2, r * 2, r, 18, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.35f), alpha));
		}
		Shapes.glassRect(g, px - r, py - r, r * 2, r * 2, r, tint, stroke, front ? 1.75f : 1.25f, alpha);
		IconRenderer.draw(g, row.icon(), px, py, r * 0.62f, Colors.multiplyAlpha(front || hover ? accent : Theme.text(0.9f), alpha), 1.9f);
		float labelSize = 11 + 5 * near;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, row.title(), px, py + r + 16, labelSize, Theme.text(1, alpha), Fonts.Align.CENTER, 0, true);
	}

	// ---- input (reference units) ----

	/** Returns the row to activate, or -1. Clicking a planet that isn't in front spins it there. */
	int click(float x, float y, int button, List<MenuRow> rows) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return -1;
		}

		dragStartX = x;
		dragLastX = x;
		dragging = false;
		lastInteraction = System.currentTimeMillis();
		return -2; // decided on release (a drag spins instead)
	}

	boolean drag(float x, float y) {
		if (!dragging && Math.abs(x - dragStartX) > 6) {
			dragging = true;
			target = Float.NaN;
		}
		if (dragging) {
			float dx = x - dragLastX;
			dragLastX = x;
			angle -= dx * 0.006f;
			velocity = -dx * 0.006f * 60;
			lastInteraction = System.currentTimeMillis();
		}
		return dragging;
	}

	/** On release: a click (not a drag) on a planet; returns the row to open, or -1. */
	int release(float x, float y, List<MenuRow> rows) {
		if (dragging) {
			dragging = false;
			velocity = Math.clamp(velocity, -9, 9);
			if (Math.abs(velocity) < 0.3f) {
				velocity = 0;
				spinTo(frontIndex(rows.size()), rows.size());
			}
			return -1;
		}
		int hit = hitAt(x, y);
		if (hit < 0) {
			return -1;
		}
		if (hit == frontIndex(rows.size()) && Float.isNaN(target)) {
			return hit;
		}
		UiSounds.hover();
		spinTo(hit, rows.size());
		return -1;
	}

	private int hitAt(float x, float y) {
		// Front-most first: planets were drawn back to front.
		int best = -1;
		float bestDepth = -9;
		for (int i = 0; i < hits.size(); i++) {
			float[] h = hits.get(i);
			if (h != null && Math.hypot(x - h[0], y - h[1]) <= h[2] && h[1] > bestDepth) {
				best = i;
				bestDepth = h[1];
			}
		}
		return best;
	}

	void scroll(float amount, int count) {
		int next = Math.floorMod(frontIndex(count) + (amount > 0 ? -1 : 1), count);
		UiSounds.hover();
		spinTo(next, count);
	}

	/** ←/→ step, Enter opens the front one. Returns the row to open, -1 for handled, -2 for not handled. */
	int key(KeyEvent event, List<MenuRow> rows) {
		int count = rows.size();
		if (event.isLeft() || event.isRight()) {
			int next = Math.floorMod(frontIndex(count) + (event.isRight() ? 1 : -1), count);
			UiSounds.hover();
			spinTo(next, count);
			return -1;
		}
		if (event.isConfirmation()) {
			return frontIndex(count);
		}
		return -2;
	}

}
