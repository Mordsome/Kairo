package dev.mordo.kairo.render;

import dev.mordo.kairo.core.Edition;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.render.TextureSetup;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

/**
 * Antialiased SDF shapes drawn through the GUI render state. All coordinates are in the caller's
 * current local space (the pose stack applies on top), colors are ARGB.
 *
 * <p>Angles are in radians, measured clockwise from +X on screen (so -PI/2 is straight up).
 */
public final class Shapes {
	private static final Matrix3x2fc IDENTITY = new Matrix3x2f();
	private static final float TWO_PI = (float) (Math.PI * 2);

	private Shapes() {
	}

	/** Antialiasing margin around a shape's quad, in local units (2 physical pixels). */
	private static float margin() {
		return 2f / Math.max(0.01f, UiScale.current());
	}

	// ---- rounded rectangles ----

	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color) {
		rect(g, x, y, w, h, radius, color, color, 0, 0);
	}

	public static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int fill, int stroke, float strokeWidth) {
		rect(g, x, y, w, h, radius, fill, fill, stroke, strokeWidth);
	}

	/** Vertical gradient from {@code top} to {@code bottom}. */
	public static void gradientRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom) {
		rect(g, x, y, w, h, radius, top, bottom, 0, 0);
	}

	/** Horizontal gradient from {@code left} to {@code right}. */
	public static void gradientRectH(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int left, int right) {
		if (w <= 0 || h <= 0) {
			return;
		}
		// A vertical gradient rotated by -90 degrees: the shape's "top" edge ends up on the left.
		float hw = h / 2;
		float hh = w / 2;
		float m = margin();
		submit(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), x + w / 2, y + h / 2, 0, -1,
			-hw - m, -hh - m, hw + m, hh + m,
			left, right, 0, 0, ShapeElement.RECT, hw, hh, radius, 0, 0, 1, 1);
	}

	public static void outline(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int color, float strokeWidth) {
		rect(g, x, y, w, h, radius, 0, 0, color, strokeWidth);
	}

	private static void rect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int top, int bottom, int stroke, float strokeWidth) {
		if (w <= 0 || h <= 0) {
			return;
		}
		float hw = w / 2;
		float hh = h / 2;
		float m = margin();
		submit(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), x + hw, y + hh, 1, 0,
			-hw - m, -hh - m, hw + m, hh + m,
			top, bottom, stroke, strokeWidth, ShapeElement.RECT, hw, hh, radius, 0, 0, 1, 1);
	}

	/** Soft drop shadow / glow: the rect's edge fades out over {@code softness} units each way. */
	public static void shadow(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float softness, int color) {
		float hw = w / 2;
		float hh = h / 2;
		float m = softness * 1.5f + margin();
		submit(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), x + hw, y + hh, 1, 0,
			-hw - m, -hh - m, hw + m, hh + m,
			color, color, 0, 0, ShapeElement.RECT | ShapeElement.FLAG_SOFT, hw, hh, radius, softness, 0, 1, 1);
	}

	// ---- circles, rings, arcs ----

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int color) {
		rect(g, cx - r, cy - r, r * 2, r * 2, r, color, color, 0, 0);
	}

	public static void circle(GuiGraphicsExtractor g, float cx, float cy, float r, int fill, int stroke, float strokeWidth) {
		rect(g, cx - r, cy - r, r * 2, r * 2, r, fill, fill, stroke, strokeWidth);
	}

	/** A circle outline centred on radius {@code r}. */
	public static void ring(GuiGraphicsExtractor g, float cx, float cy, float r, float thickness, int color) {
		sector(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), cx, cy, r - thickness / 2, r + thickness / 2,
			0, (float) Math.PI, 0, 0, color, 0, 0, 1, 1);
	}

	/** Arc from {@code start} sweeping {@code sweep} radians clockwise (butt ends). */
	public static void arc(GuiGraphicsExtractor g, float cx, float cy, float r, float thickness, float start, float sweep, int color) {
		if (sweep < 0) {
			start += sweep;
			sweep = -sweep;
		}
		if (sweep >= TWO_PI) {
			ring(g, cx, cy, r, thickness, color);
			return;
		}
		sector(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), cx, cy, r - thickness / 2, r + thickness / 2,
			start + sweep / 2, sweep / 2, 0, 0, color, 0, 0, 1, 1);
	}

	// ---- ring sectors (the radial menu) ----

	/**
	 * An annular sector centred on {@code midAngle}, {@code halfAngle} either side, with a constant
	 * {@code gap} (in local units) cut from both straight edges and corners rounded by {@code cornerRadius}.
	 */
	public static void sector(GuiGraphicsExtractor g, float cx, float cy, float innerR, float outerR, float midAngle, float halfAngle,
							  float gap, float cornerRadius, int fill, int stroke, float strokeWidth) {
		sector(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), cx, cy, innerR, outerR, midAngle, halfAngle,
			gap, cornerRadius, fill, stroke, strokeWidth, 1, 1);
	}

	/** Frosted-glass sector: shows the blurred world behind it under {@code tint}. */
	public static void glassSector(GuiGraphicsExtractor g, float cx, float cy, float innerR, float outerR, float midAngle, float halfAngle,
								   float gap, float cornerRadius, int tint, int stroke, float strokeWidth, float opacity, float saturation) {
		if (Edition.isVoid()) {
			sector(g, cx, cy, innerR, outerR, midAngle, halfAngle, gap, cornerRadius, Colors.multiplyAlpha(flat(tint), opacity), Colors.multiplyAlpha(flatStroke(stroke), opacity), strokeWidth);
			return;
		}
		if (!Blur.isAvailable()) {
			sector(g, cx, cy, innerR, outerR, midAngle, halfAngle, gap, cornerRadius, Colors.multiplyAlpha(Colors.opaqueTint(tint), opacity), Colors.multiplyAlpha(stroke, opacity), strokeWidth);
			return;
		}
		sector(g, KairoPipelines.GLASS, Blur.glassTexture(), cx, cy, innerR, outerR, midAngle, halfAngle,
			gap, cornerRadius, tint, stroke, strokeWidth, opacity, saturation);
	}

	private static void sector(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup textures, float cx, float cy,
							   float innerR, float outerR, float midAngle, float halfAngle, float gap, float cornerRadius,
							   int fill, int stroke, float strokeWidth, float opacity, float saturation) {
		float m = margin();
		float x0;
		float halfH;
		if (halfAngle < Math.PI / 2) {
			// Tight quad around the wedge, in the sector's own (rotated) space.
			x0 = innerR * (float) Math.cos(halfAngle) - m;
			halfH = outerR * (float) Math.sin(halfAngle) + m;
		} else {
			x0 = -outerR - m;
			halfH = outerR + m;
		}
		submit(g, pipeline, textures, cx, cy, (float) Math.cos(midAngle), (float) Math.sin(midAngle),
			x0, -halfH, outerR + m, halfH,
			fill, fill, stroke, strokeWidth, ShapeElement.SECTOR, innerR, outerR, halfAngle, gap / 2, cornerRadius, opacity, saturation);
	}

	// ---- lines ----

	/** A line with round caps. */
	public static void line(GuiGraphicsExtractor g, float x1, float y1, float x2, float y2, float thickness, int color) {
		float dx = x2 - x1;
		float dy = y2 - y1;
		float length = (float) Math.sqrt(dx * dx + dy * dy);
		float cos = length > 1e-4f ? dx / length : 1;
		float sin = length > 1e-4f ? dy / length : 0;
		float halfLen = length / 2;
		float ht = thickness / 2;
		float m = margin();
		submit(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), (x1 + x2) / 2, (y1 + y2) / 2, cos, sin,
			-halfLen - ht - m, -ht - m, halfLen + ht + m, ht + m,
			color, color, 0, 0, ShapeElement.SEGMENT, halfLen, ht, 0, 0, 0, 1, 1);
	}

	/**
	 * Filled triangle with slightly rounded corners, {@code size} wide and tall, its tip pointing at
	 * {@code angle} (0 = right, -PI/2 = up).
	 */
	public static void triangle(GuiGraphicsExtractor g, float cx, float cy, float size, float angle, int color) {
		float halfBase = size / 2;
		float height = size * 0.85f;
		float round = size * 0.08f;
		float m = margin() + round;
		// Rotate so the shape's "up" points along the angle.
		double rot = angle + Math.PI / 2;
		submit(g, KairoPipelines.SHAPE, TextureSetup.noTexture(), cx, cy, (float) Math.cos(rot), (float) Math.sin(rot),
			-halfBase - m, -height / 2 - m, halfBase + m, height / 2 + m,
			color, color, 0, 0, ShapeElement.TRIANGLE, halfBase - round, height - round * 2, round, 0, 0, 1, 1);
	}

	// ---- glass ----

	public static void glassRect(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, int tint,
								 int stroke, float strokeWidth, float opacity) {
		if (w <= 0 || h <= 0) {
			return;
		}
		if (Edition.isVoid()) {
			rect(g, x, y, w, h, radius, Colors.multiplyAlpha(flat(tint), opacity), Colors.multiplyAlpha(flatStroke(stroke), opacity), strokeWidth);
			if (w > 500 && h > 300 && dev.mordo.kairo.core.ClientSettings.INSTANCE.menuFx.isOn()) {
				panelLight(g, x, y, w, h, radius, opacity);
			}
			return;
		}
		if (!Blur.isAvailable()) {
			rect(g, x, y, w, h, radius, Colors.multiplyAlpha(Colors.opaqueTint(tint), opacity), Colors.multiplyAlpha(stroke, opacity), strokeWidth);
			return;
		}
		float hw = w / 2;
		float hh = h / 2;
		float m = margin();
		submit(g, KairoPipelines.GLASS, Blur.glassTexture(), x + hw, y + hh, 1, 0,
			-hw - m, -hh - m, hw + m, hh + m,
			tint, tint, stroke, strokeWidth, ShapeElement.RECT, hw, hh, radius, 0, 0, opacity, 1.4f);
		if (w > 500 && h > 300 && dev.mordo.kairo.core.ClientSettings.INSTANCE.menuFx.isOn()) {
			panelLight(g, x, y, w, h, radius, opacity);
		}
	}

	/**
	 * Full-screen blurred, dimmed and vignetted copy of the world (menu background).
	 * Drawn in GUI space regardless of the current pose.
	 */
	public static void backdrop(GuiGraphicsExtractor g, float opacity, float dim, float vignette) {
		if (Edition.isVoid()) {
			// Void: no blur, just a deep flat grey over the world.
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), Colors.argb(Math.clamp(opacity * (0.55f + dim * 0.6f), 0f, 0.94f), VOID_BACK));
			if (dev.mordo.kairo.core.ClientSettings.INSTANCE.menuFx.isOn()) {
				livingBackdrop(g, opacity);
			}
			return;
		}
		if (!Blur.isAvailable()) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), Colors.argb(Math.clamp(opacity * (0.35f + dim), 0f, 1f), 0x06080C));
		} else {
			float hw = g.guiWidth() / 2f;
			float hh = g.guiHeight() / 2f;
			// Shape space is GUI pixels around the centre; the shader normalises it by (hw, hh) for the vignette.
			g.guiRenderState.addGuiElement(new ShapeElement(KairoPipelines.GLASS, Blur.backgroundTexture(), IDENTITY, null,
				hw, hh, 1, 0, -hw, -hh, hw, hh,
				0xFFFFFFFF, 0xFFFFFFFF, 0, 0, ShapeElement.BACKDROP, vignette, dim, hw, hh, 0, opacity, 1));
		}
		if (dev.mordo.kairo.core.ClientSettings.INSTANCE.menuFx.isOn()) {
			livingBackdrop(g, opacity * 0.8f);
		}
	}

	// ---- Kairo Void: flat dark greys instead of glass ----

	/** Void's panel grey and backdrop. */
	public static final int VOID_PANEL = 0x141518;
	public static final int VOID_BACK = 0x0A0A0C;

	/**
	 * A glass tint turned into a solid Void panel: dark grey, shifted towards the tint by how strong the tint is, so
	 * accent highlights (hovered segments, selected tiles) still show while plain glass becomes flat grey.
	 */
	public static int flat(int tint) {
		float strength = Colors.alpha(tint) / 255f;
		int mixed = Colors.lerp(0xFF000000 | VOID_PANEL, 0xFF000000 | (tint & 0xFFFFFF), strength * 0.55f) & 0xFFFFFF;
		return Colors.argb(0.97f, mixed);
	}

	/** Void "Living menus": two slow clouds of accent light drifting behind the menu, and motes rising through them. */
	private static void livingBackdrop(GuiGraphicsExtractor g, float opacity) {
		float w = g.guiWidth();
		float h = g.guiHeight();
		double t = System.nanoTime() / 1e9;
		int accent = dev.mordo.kairo.gui.Theme.accent();
		int second = Colors.lerp(accent | 0xFF000000, 0xFF2EC5FF, 0.55f);
		float a = opacity * (dev.mordo.kairo.core.ClientSettings.INSTANCE.reduceMotion.isOn() ? 0.6f : 1f);
		float r = h * 0.55f;
		float x1 = w * (0.25f + 0.12f * (float) Math.sin(t * 0.11));
		float y1 = h * (0.3f + 0.1f * (float) Math.cos(t * 0.13));
		float x2 = w * (0.75f + 0.1f * (float) Math.cos(t * 0.09 + 1.3));
		float y2 = h * (0.72f + 0.1f * (float) Math.sin(t * 0.12 + 0.7));
		shadow(g, x1 - r / 2, y1 - r / 2, r, r, r / 2, r * 0.5f, Colors.withAlpha(accent, 0.13f * a));
		shadow(g, x2 - r / 2, y2 - r / 2, r, r, r / 2, r * 0.5f, Colors.withAlpha(second, 0.09f * a));
		for (int i = 0; i < 46; i++) {
			double s1 = hash(i * 1.37);
			double s2 = hash(i * 7.91 + 3);
			double s3 = hash(i * 3.13 + 11);
			float k = (float) ((t * (0.01 + 0.025 * s1) + s2) % 1.0);
			float mx = (float) (s3 * w + Math.sin(t * 0.4 + i) * 12);
			float my = h * (1.05f - k * 1.1f);
			float tw = 0.5f + 0.5f * (float) Math.sin(t * (1 + s1 * 2) + i);
			float alpha = 0.35f * tw * a * Math.min(1, k * 5) * Math.min(1, (1 - k) * 5);
			circle(g, mx, my, 0.7f + (float) s2 * 1.4f, Colors.withAlpha(i % 3 == 0 ? second : accent, alpha));
		}
	}

	private static double hash(double n) {
		double s = Math.sin(n * 12.9898) * 43758.5453;
		return s - Math.floor(s);
	}

	/** Void "Living menus": a short light running along the top edge of big panels. */
	private static void panelLight(GuiGraphicsExtractor g, float x, float y, float w, float h, float radius, float opacity) {
		int accent = dev.mordo.kairo.gui.Theme.accent();
		double t = System.nanoTime() / 1e9;
		float run = (float) ((t * 0.12) % 1.0);
		float lw = w * 0.22f;
		float lx = x + radius + run * (w - radius * 2 + lw) - lw;
		float x0 = Math.max(x + radius, lx);
		float x1 = Math.min(x + w - radius, lx + lw);
		if (x1 <= x0) {
			return;
		}
		float mid = (x0 + x1) / 2;
		gradientRectH(g, x0, y, mid - x0, 1.5f, 0, Colors.withAlpha(accent, 0), Colors.withAlpha(accent, 0.9f * opacity));
		gradientRectH(g, mid, y, x1 - mid, 1.5f, 0, Colors.withAlpha(accent, 0.9f * opacity), Colors.withAlpha(accent, 0));
		shadow(g, mid - lw * 0.25f, y - 2, lw * 0.5f, 4, 2, 8, Colors.withAlpha(accent, 0.25f * opacity));
	}

	/** Glass edges are bright white lines; Void keeps them as faint hairlines unless they carry a colour. */
	public static int flatStroke(int stroke) {
		int r = stroke >> 16 & 0xFF, gr = stroke >> 8 & 0xFF, b = stroke & 0xFF;
		boolean grey = Math.max(r, Math.max(gr, b)) - Math.min(r, Math.min(gr, b)) < 24;
		return grey ? Colors.multiplyAlpha(stroke, 0.55f) : stroke;
	}

	private static void submit(GuiGraphicsExtractor g, RenderPipeline pipeline, TextureSetup textures,
							   float ox, float oy, float cos, float sin, float lx0, float ly0, float lx1, float ly1,
							   int fillTop, int fillBottom, int stroke, float strokeWidth, int typeFlags,
							   float p0, float p1, float p2, float p3, float cornerRadius, float opacity, float saturation) {
		g.guiRenderState.addGuiElement(new ShapeElement(pipeline, textures, g.pose(), g.scissorStack.peek(),
			ox, oy, cos, sin, lx0, ly0, lx1, ly1, fillTop, fillBottom, stroke, strokeWidth, typeFlags,
			p0, p1, p2, p3, cornerRadius, opacity, saturation));
	}
}
