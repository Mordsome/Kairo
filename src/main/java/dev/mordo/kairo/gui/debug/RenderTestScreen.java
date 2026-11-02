package dev.mordo.kairo.gui.debug;

import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.KairoScreen;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Developer screen proving the render foundation: blur, glass, SDF shapes, fonts, icons, easing.
 * Opened from Settings > About ("Render test").
 */
public final class RenderTestScreen extends KairoScreen {
	private static final int TEXT = 0xFFF4F8FB;
	private static final int ACCENT = 0xFF7FD8FF;
	private static final int PVP = 0xFFFF9A52;

	private final Animation open = new Animation(0);
	private final Animation[] easingBars = {new Animation(0), new Animation(0), new Animation(0)};
	private final Easing[] easings = {Easing.UI, Easing.OUT_BACK, Easing.LINEAR};
	private final String[] easingNames = {"UI (0.2, 0.9, 0.25, 1)", "Out back", "Linear"};
	private final Animation[] hover = new Animation[5];
	private long lastFlip;
	private boolean barsRight;

	public RenderTestScreen() {
		super(Component.literal("Render test"));
		for (int i = 0; i < hover.length; i++) {
			hover[i] = new Animation(0);
		}
	}

	@Override
	protected void init() {
		super.init();
		open.set(0).animateTo(1, 550);
	}

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		Shapes.backdrop(g, Math.min(1, open.value() * 2.2f), 0.30f, 0.6f);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float partialTick) {
		long now = System.currentTimeMillis();
		if (now - lastFlip > 1400) {
			lastFlip = now;
			barsRight = !barsRight;
			for (int i = 0; i < easingBars.length; i++) {
				easingBars[i].animateTo(barsRight ? 1 : 0, 900, easings[i]);
			}
		}
		float fade = open.value();

		// Title + FPS
		Fonts.draw(g, Typeface.OUTFIT_BOLD, "Render test", 40, 40, 30, TEXT);
		Fonts.draw(g, Typeface.MONO_BOLD, "PHASE 2 · SDF · BLUR · FONTS", 40, 70, 10, ACCENT, Fonts.Align.LEFT, 1.6f, false);
		Fonts.draw(g, Typeface.MONO_MEDIUM, Minecraft.getInstance().getFps() + " FPS", viewWidth - 40, 40, 12, TEXT, Fonts.Align.RIGHT, 0.5f, false);

		drawTextColumn(g);
		drawRing(g, mx, my, fade);
		drawShapes(g);
		drawEasing(g);
	}

	private void drawTextColumn(GuiGraphicsExtractor g) {
		float y = 120;
		Shapes.glassRect(g, 30, 100, 360, 420, 22, 0x700C1018, 0x4DFFFFFF, 1.25f, open.value());
		Typeface[] faces = {Typeface.OUTFIT_REGULAR, Typeface.OUTFIT_MEDIUM, Typeface.OUTFIT_SEMIBOLD, Typeface.OUTFIT_BOLD};
		float[] sizes = {12, 16, 24};
		for (Typeface face : faces) {
			for (float size : sizes) {
				y += size * 0.9f + 4;
				Fonts.draw(g, face, "Kairo " + (int) size, 50, y, size, TEXT);
			}
			y += 6;
		}
		y += 14;
		Fonts.draw(g, Typeface.MONO_BOLD, "RIGHT SHIFT MENU", 50, y, 9, ACCENT, Fonts.Align.LEFT, 1.8f, false);
		y += 18;
		Fonts.draw(g, Typeface.MONO_MEDIUM, "26.3 · v0.1  0123456789", 50, y, 11, TEXT, Fonts.Align.LEFT, 0.4f, true);
	}

	private void drawRing(GuiGraphicsExtractor g, float mx, float my, float fade) {
		float cx = viewWidth / 2;
		float cy = 430;
		float inner = 140 * 0.62f;
		float outer = 262 * 0.62f;
		float dx = mx - cx;
		float dy = my - cy;
		float dist = (float) Math.sqrt(dx * dx + dy * dy);
		int hovered = -1;
		if (dist > inner && dist < outer + 10) {
			float angle = (float) Math.atan2(dy, dx);
			float fromTop = (float) ((angle + Math.PI / 2 + Math.PI * 2 + Math.PI / 5) % (Math.PI * 2));
			hovered = (int) (fromTop / (Math.PI * 2 / 5));
		}
		// Faint dotted circle.
		for (int i = 0; i < 90; i++) {
			float a = (float) (i * Math.PI * 2 / 90);
			Shapes.circle(g, cx + (float) Math.cos(a) * 300 * 0.62f, cy + (float) Math.sin(a) * 300 * 0.62f, 1.1f, 0x30FFFFFF);
		}
		for (int i = 0; i < 5; i++) {
			hover[i].animateTo(i == hovered ? 1 : 0, 300);
			float h = hover[i].value();
			float stagger = Math.clamp((open.progress() * 550 - i * 50) / 550f, 0f, 1f);
			float t = Easing.UI.apply(stagger);
			float mid = (float) (-Math.PI / 2 + i * Math.PI * 2 / 5) + (1 - t) * (float) Math.toRadians(-10);
			float push = h * 9 * 0.62f;
			float scale = Animation.lerp(0.8f, 1f, t);
			float px = cx + (float) Math.cos(mid) * push;
			float py = cy + (float) Math.sin(mid) * push;
			int tint = i == 2 ? 0x66D65C18 : (h > 0.01f ? 0x5746AAE6 : 0x700C1018);
			int stroke = i == 2 ? 0xFFFF9A52 : (h > 0.5f ? ACCENT : 0x4DFFFFFF);
			g.pose().pushMatrix();
			g.pose().translate(px, py);
			g.pose().scale(scale, scale);
			Shapes.glassSector(g, 0, 0, inner, outer + h * 8 * 0.62f, mid, (float) (Math.PI / 5), 10 * 0.62f, 6 * 0.62f,
				tint, stroke, 1.25f, t, 1.4f);
			float labelR = (inner + outer) / 2;
			float lx = (float) Math.cos(mid) * labelR;
			float ly = (float) Math.sin(mid) * labelR;
			Icon icon = new Icon[]{Icon.GRID, Icon.SLIDERS, Icon.SWORD, Icon.SPARKLE, Icon.LAYOUT}[i];
			IconRenderer.draw(g, icon, lx, ly - 10, 16, (TEXT & 0xFFFFFF) | ((int) (t * 255) << 24));
			Fonts.drawCentered(g, Typeface.OUTFIT_SEMIBOLD, new String[]{"Mods", "Settings", "PvP Mode", "Visuals", "Layout"}[i],
				lx, ly + 10, 13, (TEXT & 0xFFFFFF) | ((int) (t * 255) << 24));
			g.pose().popMatrix();
		}
		Shapes.glassSector(g, cx, cy, 0, 122 * 0.62f, 0, (float) Math.PI, 0, 0, 0x700C1018, 0x4DFFFFFF, 1.25f, fade, 1.4f);
		Shapes.ring(g, cx, cy, 113 * 0.62f, 1, 0x26FFFFFF);
		Fonts.draw(g, Typeface.MONO_BOLD, "RIGHT SHIFT MENU", cx, cy - 22, 7, ACCENT, Fonts.Align.CENTER, 1.4f, false);
		Fonts.drawCentered(g, Typeface.OUTFIT_SEMIBOLD, "Menu", cx, cy, 20, TEXT);
		Fonts.drawCentered(g, Typeface.OUTFIT_REGULAR, "Hover a section", cx, cy + 20, 9, 0xB3F4F8FB);
	}

	private void drawShapes(GuiGraphicsExtractor g) {
		float x = viewWidth - 400;
		float y = 100;
		Shapes.shadow(g, x, y, 170, 90, 18, 14, 0x80000000);
		Shapes.rect(g, x, y, 170, 90, 18, 0xFF1A2230);
		Shapes.gradientRect(g, x + 190, y, 170, 90, 18, ACCENT, 0xFF2B6CB0);
		Shapes.rect(g, x, y + 110, 170, 90, 8, 0x22FFFFFF, ACCENT, 2);
		Shapes.outline(g, x + 190, y + 110, 170, 90, 45, PVP, 1.25f);
		Shapes.circle(g, x + 30, y + 250, 22, ACCENT);
		Shapes.circle(g, x + 90, y + 250, 22, 0x33FFFFFF, TEXT, 1.25f);
		Shapes.ring(g, x + 150, y + 250, 20, 3, PVP);
		float spin = (System.nanoTime() % 3_000_000_000L) / 3e9f * (float) Math.PI * 2;
		Shapes.arc(g, x + 210, y + 250, 20, 4, spin, (float) Math.PI * 1.3f, ACCENT);
		for (int i = 0; i < 6; i++) {
			float a = spin + i * (float) Math.PI / 6;
			Shapes.line(g, x + 300, y + 250, x + 300 + (float) Math.cos(a) * 28, y + 250 + (float) Math.sin(a) * 28, 0.5f + i * 0.6f, TEXT);
		}
		// Icons
		Icon[] icons = Icon.values();
		for (int i = 0; i < icons.length; i++) {
			float ix = x + 18 + (i % 9) * 40;
			float iy = y + 320 + (i / 9) * 40;
			IconRenderer.draw(g, icons[i], ix, iy, 26, TEXT);
		}
	}

	private void drawEasing(GuiGraphicsExtractor g) {
		float x = 470;
		float y = viewHeight - 150;
		float w = viewWidth - 940;
		for (int i = 0; i < easingBars.length; i++) {
			float by = y + i * 40;
			Shapes.rect(g, x, by, w, 6, 3, 0x26FFFFFF);
			float t = easingBars[i].value();
			Shapes.circle(g, x + t * w, by + 3, 9, i == 0 ? ACCENT : (i == 1 ? PVP : TEXT));
			Fonts.draw(g, Typeface.OUTFIT_MEDIUM, easingNames[i], x, by - 12, 10, 0xB3F4F8FB);
		}
	}
}
