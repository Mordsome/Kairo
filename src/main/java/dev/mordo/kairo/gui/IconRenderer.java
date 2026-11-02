package dev.mordo.kairo.gui;

import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Draws {@link Icon}s as strokes on a 24x24 grid (Lucide-style line icons), scaled to any size.
 * Everything is vector, so icons stay crisp at every scale.
 */
public final class IconRenderer {
	private static final float PI = (float) Math.PI;

	private GuiGraphicsExtractor g;
	private float ox;
	private float oy;
	private float s;
	private float stroke;
	private int color;

	private static final IconRenderer INSTANCE = new IconRenderer();

	private IconRenderer() {
	}

	/** Draws {@code icon} centred on (cx, cy), {@code size} units wide. */
	public static void draw(GuiGraphicsExtractor g, Icon icon, float cx, float cy, float size, int color) {
		draw(g, icon, cx, cy, size, color, 2f);
	}

	public static void draw(GuiGraphicsExtractor g, Icon icon, float cx, float cy, float size, int color, float strokeOn24) {
		IconRenderer r = INSTANCE;
		r.g = g;
		r.s = size / 24f;
		r.ox = cx - size / 2;
		r.oy = cy - size / 2;
		r.stroke = strokeOn24 * r.s;
		r.color = color;
		r.paint(icon);
		r.g = null;
	}

	// ---- primitives on the 24x24 grid ----

	private void line(float x1, float y1, float x2, float y2) {
		Shapes.line(g, ox + x1 * s, oy + y1 * s, ox + x2 * s, oy + y2 * s, stroke, color);
	}

	private void poly(float... pts) {
		for (int i = 0; i + 3 < pts.length; i += 2) {
			line(pts[i], pts[i + 1], pts[i + 2], pts[i + 3]);
		}
	}

	/** Outline centred on the rect's edge (Shapes strokes inside, so grow by half the stroke). */
	private void rect(float x, float y, float w, float h, float r) {
		float half = stroke / s / 2;
		Shapes.outline(g, ox + (x - half) * s, oy + (y - half) * s, (w + half * 2) * s, (h + half * 2) * s, (r + half) * s, color, stroke);
	}

	private void circle(float cx, float cy, float r) {
		Shapes.ring(g, ox + cx * s, oy + cy * s, r * s, stroke, color);
	}

	private void dot(float cx, float cy, float r) {
		Shapes.circle(g, ox + cx * s, oy + cy * s, r * s, color);
	}

	private void arc(float cx, float cy, float r, float startDeg, float sweepDeg) {
		Shapes.arc(g, ox + cx * s, oy + cy * s, r * s, stroke, startDeg * PI / 180, sweepDeg * PI / 180, color);
	}

	private void paint(Icon icon) {
		switch (icon) {
			case GRID -> {
				rect(3, 3, 7, 7, 1.5f);
				rect(14, 3, 7, 7, 1.5f);
				rect(3, 14, 7, 7, 1.5f);
				rect(14, 14, 7, 7, 1.5f);
			}
			case SLIDERS -> {
				line(3, 8, 11.5f, 8);
				circle(15, 8, 2.5f);
				line(18.5f, 8, 21, 8);
				line(3, 16, 5.5f, 16);
				circle(9, 16, 2.5f);
				line(12.5f, 16, 21, 16);
			}
			case SWORD -> {
				poly(14.5f, 17.5f, 3, 6, 3, 3, 6, 3, 17.5f, 14.5f);
				line(13, 19, 19, 13);
				line(16, 16, 20, 20);
				line(19, 21, 21, 19);
			}
			case SPARKLE -> {
				poly(12, 3, 13.8f, 10.2f, 21, 12, 13.8f, 13.8f, 12, 21, 10.2f, 13.8f, 3, 12, 10.2f, 10.2f, 12, 3);
				line(19, 3, 19, 6);
				line(17.5f, 4.5f, 20.5f, 4.5f);
			}
			case LAYOUT -> {
				rect(3, 4, 18, 16, 2.5f);
				line(3, 9.5f, 21, 9.5f);
				line(9.5f, 9.5f, 9.5f, 20);
			}
			case KEYBOARD -> {
				rect(2, 6, 20, 12, 2);
				dot(6.5f, 10, 0.9f);
				dot(10, 10, 0.9f);
				dot(14, 10, 0.9f);
				dot(17.5f, 10, 0.9f);
				line(7.5f, 14, 16.5f, 14);
			}
			case MOUSE -> {
				rect(6, 3, 12, 18, 6);
				line(12, 7, 12, 10);
			}
			case CROSSHAIR -> {
				circle(12, 12, 8.5f);
				line(12, 2, 12, 6.5f);
				line(12, 17.5f, 12, 22);
				line(2, 12, 6.5f, 12);
				line(17.5f, 12, 22, 12);
			}
			case COMPASS -> {
				circle(12, 12, 9.5f);
				poly(16, 8, 13.5f, 13.5f, 8, 16, 10.5f, 10.5f, 16, 8);
			}
			case SIGNAL -> {
				line(4, 20, 4, 17);
				line(9, 20, 9, 13);
				line(14, 20, 14, 9);
				line(19, 20, 19, 4);
			}
			case GAUGE -> {
				arc(12, 14, 9, 150, 240);
				line(12, 14, 16.5f, 9);
				dot(12, 14, 1.4f);
			}
			case SHIELD -> poly(12, 2.5f, 20, 5.5f, 20, 11.5f, 17.5f, 17, 12, 21.5f, 6.5f, 17, 4, 11.5f, 4, 5.5f, 12, 2.5f);
			case TOTEM -> {
				rect(8, 2.5f, 8, 7, 2);
				dot(10.5f, 6, 0.9f);
				dot(13.5f, 6, 0.9f);
				rect(7, 11, 10, 9, 2);
				poly(7, 13, 3, 11, 3, 15, 7, 16.5f);
				poly(17, 13, 21, 11, 21, 15, 17, 16.5f);
			}
			case HEART -> {
				arc(8, 9, 4.5f, 140, 220);
				arc(16, 9, 4.5f, 180, 220);
				poly(4.6f, 12.1f, 12, 20.5f, 19.4f, 12.1f);
			}
			case FLAME -> {
				arc(12, 15, 6, -10, 200);
				poly(6.1f, 13.9f, 8.5f, 8, 12, 2.5f, 14, 7, 17.5f, 10, 17.9f, 13.9f);
			}
			case SEARCH -> {
				circle(10.5f, 10.5f, 6.5f);
				line(15.5f, 15.5f, 20.5f, 20.5f);
			}
			case SUN -> {
				circle(12, 12, 4);
				for (int i = 0; i < 8; i++) {
					float a = i * PI / 4;
					float c = (float) Math.cos(a);
					float sn = (float) Math.sin(a);
					line(12 + c * 7.5f, 12 + sn * 7.5f, 12 + c * 9.5f, 12 + sn * 9.5f);
				}
			}
			case PIN -> {
				arc(12, 10, 7, 150, 240);
				poly(5.9f, 13.5f, 12, 21.5f, 18.1f, 13.5f);
				circle(12, 10, 2.5f);
			}
			case CHAT -> {
				rect(3, 4, 18, 13, 3);
				poly(8, 17, 7, 21, 12, 17);
			}
			case SCROLL -> {
				line(8, 6, 20, 6);
				line(8, 12, 20, 12);
				line(8, 18, 20, 18);
				dot(4, 6, 1.1f);
				dot(4, 12, 1.1f);
				dot(4, 18, 1.1f);
			}
			case BOX -> {
				poly(12, 2.5f, 20.5f, 7, 20.5f, 17, 12, 21.5f, 3.5f, 17, 3.5f, 7, 12, 2.5f);
				poly(3.5f, 7, 12, 11.5f, 20.5f, 7);
				line(12, 11.5f, 12, 21.5f);
			}
			case GEAR -> {
				circle(12, 12, 3);
				circle(12, 12, 7);
				for (int i = 0; i < 8; i++) {
					float a = i * PI / 4 + PI / 8;
					float c = (float) Math.cos(a);
					float sn = (float) Math.sin(a);
					line(12 + c * 7.5f, 12 + sn * 7.5f, 12 + c * 9.5f, 12 + sn * 9.5f);
				}
			}
			case ARROW_LEFT -> {
				poly(14, 5, 7, 12, 14, 19);
			}
			case CHECK -> poly(4.5f, 12.5f, 9.5f, 17.5f, 19.5f, 6.5f);
			case CLOSE -> {
				line(6, 6, 18, 18);
				line(18, 6, 6, 18);
			}
			case PLUS -> {
				line(12, 5, 12, 19);
				line(5, 12, 19, 12);
			}
			case TRASH -> {
				line(3.5f, 6, 20.5f, 6);
				poly(9, 6, 9, 3.5f, 15, 3.5f, 15, 6);
				poly(5.5f, 6, 6.5f, 20.5f, 17.5f, 20.5f, 18.5f, 6);
				line(10, 10.5f, 10, 16.5f);
				line(14, 10.5f, 14, 16.5f);
			}
			case EYE -> {
				arc(12, 18.5f, 11, 215, 110);
				arc(12, 5.5f, 11, 35, 110);
				circle(12, 12, 3);
			}
			case EYE_OFF -> {
				arc(12, 18.5f, 11, 215, 110);
				arc(12, 5.5f, 11, 35, 110);
				line(4, 4, 20, 20);
			}
			case USER -> {
				circle(12, 8, 4);
				arc(12, 21, 8, 200, 140);
			}
			case FOLDER -> poly(3, 19, 3, 5, 9.5f, 5, 11.5f, 7.5f, 21, 7.5f, 21, 19, 3, 19);
			case INFO -> {
				circle(12, 12, 9.5f);
				line(12, 11, 12, 16.5f);
				dot(12, 7.5f, 1.2f);
			}
			case PLAY -> poly(7, 4.5f, 19, 12, 7, 19.5f, 7, 4.5f);
			case VOLUME -> {
				poly(4, 9.5f, 7.5f, 9.5f, 12, 5, 12, 19, 7.5f, 14.5f, 4, 14.5f, 4, 9.5f);
				arc(13, 12, 4, -50, 100);
				arc(13, 12, 7.5f, -50, 100);
			}
			case GLOBE -> {
				circle(12, 12, 9.5f);
				line(2.5f, 12, 21.5f, 12);
				arc(5, 12, 10, -60, 120);
				arc(19, 12, 10, 120, 120);
			}
			case WORLD -> {
				// Mountains under a sun: a world.
				rect(2.5f, 3.5f, 19, 17, 3);
				poly(2.5f, 17.5f, 8.5f, 10.5f, 12.5f, 14.5f, 15, 12, 21.5f, 18);
				circle(15.5f, 8, 1.6f);
			}
			case POWER -> {
				arc(12, 13, 8, -55, 290);
				line(12, 3, 12, 11.5f);
			}
			case REFRESH -> {
				arc(12, 12, 8, -20, 290);
				poly(20.5f, 4.5f, 20.2f, 9.3f, 15.5f, 8.7f);
			}
			case LINK -> {
				rect(2.5f, 8.5f, 10, 7, 3.5f);
				rect(11.5f, 8.5f, 10, 7, 3.5f);
			}
			case PENCIL -> {
				poly(4, 20, 5, 15.5f, 16, 4.5f, 19.5f, 8, 8.5f, 19, 4, 20);
				line(13.5f, 7, 17, 10.5f);
			}
			case IMAGE -> {
				rect(3, 4, 18, 16, 2.5f);
				circle(8.5f, 9.5f, 1.8f);
				poly(3.5f, 18, 10, 12, 14, 15.5f, 16.5f, 13.5f, 20.5f, 17);
			}
			case NOTE -> {
				poly(9, 17.5f, 9, 4.5f, 20, 2.5f, 20, 15.5f);
				circle(6.5f, 17.5f, 2.5f);
				circle(17.5f, 15.5f, 2.5f);
			}
			case SERVER -> {
				rect(3, 3.5f, 18, 7, 2);
				rect(3, 13.5f, 18, 7, 2);
				dot(7, 7, 1);
				dot(7, 17, 1);
			}
			case ARROW_RIGHT -> poly(10, 5, 17, 12, 10, 19);
			case CLOCK -> {
				circle(12, 12, 9.5f);
				poly(12, 6.5f, 12, 12, 15.5f, 14);
			}
			case CAMERA -> {
				poly(3, 8.5f, 3, 19, 21, 19, 21, 8.5f, 16.5f, 8.5f, 15, 5.5f, 9, 5.5f, 7.5f, 8.5f, 3, 8.5f);
				circle(12, 13.5f, 3.5f);
			}
			case COPY -> {
				rect(8.5f, 8.5f, 12, 12, 2);
				poly(5.5f, 15.5f, 3.5f, 15.5f, 3.5f, 3.5f, 15.5f, 3.5f, 15.5f, 5.5f);
			}
			case UNDO -> {
				poly(9, 4.5f, 4.5f, 9, 9, 13.5f);
				line(4.5f, 9, 14, 9);
				arc(14, 14, 5, -90, 180);
				line(14, 19, 8, 19);
			}
			case REDO -> {
				poly(15, 4.5f, 19.5f, 9, 15, 13.5f);
				line(19.5f, 9, 10, 9);
				arc(10, 14, 5, 90, 180);
				line(10, 19, 16, 19);
			}
			case VIEW_LIST -> {
				rect(3.5f, 4.5f, 17, 4, 1.2f);
				rect(3.5f, 10, 17, 4, 1.2f);
				rect(3.5f, 15.5f, 17, 4, 1.2f);
			}
			case HANGER -> {
				arc(12, 6, 2.2f, 180, 270);
				line(12, 8.2f, 12, 10);
				poly(12, 10, 3, 17.5f, 21, 17.5f, 12, 10);
			}
			case VIEW_COMPACT -> {
				for (int i = 0; i < 5; i++) {
					line(4, 4.5f + i * 3.75f, 20, 4.5f + i * 3.75f);
				}
			}
			case VIEW_LARGE -> {
				rect(3.5f, 3.5f, 7.5f, 17, 1.5f);
				rect(13, 3.5f, 7.5f, 17, 1.5f);
			}
			case VIEW_TINY -> {
				for (int r = 0; r < 3; r++) {
					for (int c = 0; c < 3; c++) {
						rect(3.5f + c * 6, 3.5f + r * 6, 4, 4, 1);
					}
				}
			}
			case CUBE -> {
				poly(12, 3, 20, 7.5f, 20, 16.5f, 12, 21, 4, 16.5f, 4, 7.5f, 12, 3);
				poly(4, 7.5f, 12, 12, 20, 7.5f);
				line(12, 12, 12, 21);
			}
			case FOOD -> {
				circle(14, 10, 5.5f);
				line(10.2f, 13.8f, 5.5f, 18.5f);
				line(4, 17, 7, 20);
			}
			case HOTBAR -> {
				rect(2.5f, 13, 19, 7, 1.5f);
				line(8.8f, 13, 8.8f, 20);
				line(15.2f, 13, 15.2f, 20);
				line(4, 8, 11, 8);
				line(13, 8, 20, 8);
			}
		}
	}
}
