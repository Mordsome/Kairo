package dev.mordo.kairo.render;

import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Kairo Void "Animated damage markers": a number punches in with a little overshoot and tilt while a slash of light
 * wipes behind it, drifts up and away, and breaks into slices as it goes. Hits on the same target within a moment
 * stack into one marker with a combo count; critical hits are bigger, in gold and your colour, with spikes and a
 * CRIT tag. Nothing strobes.
 */
public final class KineticNumbers {
	private static final float LIFE = 1.15f;
	private static final float COMBO_WINDOW = 0.7f;

	private static final class Marker {
		final Entity entity;
		final Vec3 offset;
		final float side;
		float total;
		int hits;
		boolean crit;
		long start;
		long last;

		Marker(Entity entity, Vec3 offset, float side) {
			this.entity = entity;
			this.offset = offset;
			this.side = side;
		}
	}

	private static final List<Marker> MARKERS = new ArrayList<>();
	private static int count;

	private KineticNumbers() {
	}

	public static boolean active() {
		return Visuals.on() && VoidSettings.INSTANCE.kineticNumbers.isOn();
	}

	public static void add(Entity entity, float amount, boolean crit) {
		long now = System.nanoTime();
		for (Marker m : MARKERS) {
			if (m.entity == entity && (now - m.last) / 1e9f < COMBO_WINDOW) {
				m.total += amount;
				m.hits++;
				m.crit |= crit;
				m.start = now; // punch in again, bigger
				m.last = now;
				return;
			}
		}
		count++;
		float side = count % 2 == 0 ? 1 : -1;
		Marker m = new Marker(entity, new Vec3(0, entity.getBbHeight() + 0.25, 0), side);
		m.total = amount;
		m.hits = 1;
		m.crit = crit;
		m.start = now;
		m.last = now;
		MARKERS.add(m);
		if (MARKERS.size() > 16) {
			MARKERS.removeFirst();
		}
	}

	public static void draw(GuiGraphicsExtractor g) {
		if (MARKERS.isEmpty()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		var camera = mc.gameRenderer.mainCamera();
		Vec3 eye = camera.position();
		Vector3fc forward = camera.forwardVector();
		long now = System.nanoTime();
		int accent = Theme.accent() | 0xFF000000;
		boolean still = Animation.reduceMotion();
		for (Iterator<Marker> it = MARKERS.iterator(); it.hasNext(); ) {
			Marker m = it.next();
			float age = (now - m.start) / 1e9f;
			if (age > LIFE || m.entity.isRemoved() && age > 0.6f) {
				it.remove();
				continue;
			}
			float rise = still ? age * 0.3f : age * 0.55f;
			Vec3 pos = m.entity.position().add(m.offset).add(0, rise, 0);
			double depth = (pos.x - eye.x) * forward.x() + (pos.y - eye.y) * forward.y() + (pos.z - eye.z) * forward.z();
			if (depth < 0.3) {
				continue;
			}
			Vec3 ndc = mc.gameRenderer.projectPointToScreen(pos);
			if (Math.abs(ndc.x) > 1.1 || Math.abs(ndc.y) > 1.1) {
				continue;
			}
			float sx = (float) ((ndc.x * 0.5 + 0.5) * g.guiWidth());
			float sy = (float) ((1 - (ndc.y * 0.5 + 0.5)) * g.guiHeight());
			// Away to one side as it rises.
			sx += m.side * (still ? 0 : age * 18);
			boolean big = m.crit || m.total >= 6;
			float base = (float) Math.clamp(28 / depth, 8, 20) * (big ? 1.3f : 1) * (1 + Math.min(4, m.hits - 1) * 0.08f);
			float punch = still ? 1 : age < 0.14f ? 1.9f - 0.9f * ease(age / 0.14f) : age < 0.24f ? 1 - 0.08f * (float) Math.sin((age - 0.14f) / 0.1f * Math.PI) : 1;
			float size = base * punch;
			float fadeIn = Math.min(1, age / 0.06f);
			float end = Math.clamp((age - 0.8f) / (LIFE - 0.8f), 0f, 1f);
			float alpha = fadeIn * (1 - end);
			int color = m.crit ? Colors.lerp(0xFFFFD25A, accent, 0.25f) : big ? Colors.lerp(0xFFFFFFFF, accent, 0.35f) : 0xFFFFFFFF;
			String text = format(m.total);
			float tw = Fonts.width(Typeface.OUTFIT_BOLD, text, size);
			float tilt = still ? 0 : m.side * (float) Math.toRadians(7) * (1 - ease(Math.min(1, age / 0.3f)));

			g.pose().pushMatrix();
			g.pose().translate(sx, sy);
			g.pose().rotate(tilt);
			// The slash of light wiping behind the number.
			float wipe = ease(Math.clamp((age - 0.03f) / 0.22f, 0f, 1f));
			if (wipe > 0 && end < 1) {
				float sw = (tw + size * 1.2f) * wipe;
				float slant = size * 0.35f;
				int slash = Colors.withAlpha(accent, 0.55f * alpha * (1 - Math.max(0, age - 0.35f) * 1.5f));
				Shapes.gradientRectH(g, -tw / 2 - size * 0.6f, size * 0.1f - slant * 0.2f, sw, size * 0.16f, size * 0.08f, Colors.withAlpha(accent, 0), slash);
			}
			if (m.crit && end < 1) {
				// Spikes and a CRIT tag.
				float spike = ease(Math.min(1, age / 0.18f));
				for (int i = 0; i < 6; i++) {
					double a = i * Math.PI / 3 + 0.3;
					float r0 = size * 0.75f;
					float r1 = size * (0.9f + 0.55f * spike);
					Shapes.line(g, (float) Math.cos(a) * r0, (float) Math.sin(a) * r0, (float) Math.cos(a) * r1, (float) Math.sin(a) * r1,
						Math.max(0.8f, size * 0.07f * (1 - age)), Colors.withAlpha(color, 0.8f * alpha * (1 - age)));
				}
				Fonts.draw(g, Typeface.MONO_BOLD, "CRIT", 0, -size * 0.85f, size * 0.32f, Colors.withAlpha(accent, alpha), Fonts.Align.CENTER, size * 0.04f, true);
			}
			if (end <= 0) {
				Fonts.draw(g, Typeface.OUTFIT_BOLD, text, 0, 0, size, Colors.withAlpha(color, alpha), Fonts.Align.CENTER, 0, true);
			} else {
				// Breaking apart: three slices drifting away from each other.
				for (int s = 0; s < 3; s++) {
					float top = -size * 0.55f + s * size * 0.37f;
					float shift = (s - 1) * end * size * 0.6f * (s % 2 == 0 ? 1 : -1);
					// The clip follows the pose (it's in this marker's own space).
					int x0 = (int) Math.floor(-tw - 40);
					int y0 = (int) Math.floor(top);
					int y1 = (int) Math.ceil(top + size * 0.37f);
					g.enableScissor(x0, y0, (int) Math.ceil(tw + 40), y1);
					Fonts.draw(g, Typeface.OUTFIT_BOLD, text, shift, 0, size, Colors.withAlpha(color, alpha), Fonts.Align.CENTER, 0, true);
					g.disableScissor();
				}
			}
			if (m.hits > 1) {
				Fonts.draw(g, Typeface.MONO_BOLD, "×" + m.hits, tw / 2 + size * 0.15f, size * 0.25f, size * 0.38f, Colors.withAlpha(accent, alpha),
					Fonts.Align.LEFT, 0, true);
			}
			g.pose().popMatrix();
		}
	}

	private static String format(float amount) {
		return amount == Math.floor(amount) ? String.valueOf((int) amount) : String.format(Locale.ROOT, "%.1f", amount);
	}

	private static float ease(float t) {
		return 1 - (1 - t) * (1 - t) * (1 - t);
	}
}
