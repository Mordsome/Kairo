package dev.mordo.kairo.render.font;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.render.UiScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.EnumSet;

/**
 * Text in the client fonts (Outfit, JetBrains Mono), rendered at the exact pixel size so it is sharp
 * at any GUI scale. Falls back to the Minecraft font when client fonts are turned off or fail to load.
 *
 * <p>Sizes are em sizes in local units. Vertical positions are the centre of the capital letters,
 * which is what looks centred in buttons and labels.
 */
public final class Fonts {
	public enum Align {
		LEFT,
		CENTER,
		RIGHT
	}

	private static final EnumMap<Typeface, FontFace> FACES = new EnumMap<>(Typeface.class);
	private static final EnumSet<Typeface> FAILED = EnumSet.noneOf(Typeface.class);
	private static @Nullable GlyphAtlas atlas;

	private Fonts() {
	}

	static GlyphAtlas atlas() {
		if (atlas == null) {
			atlas = new GlyphAtlas();
		}
		return atlas;
	}

	static boolean atlasFull() {
		return atlas != null && atlas.isFull();
	}

	public static boolean customEnabled() {
		return ClientSettings.INSTANCE.customFonts.isOn();
	}

	/** The font at the pixel size this text will be drawn at, or null to use the vanilla font. */
	private static @Nullable SizedFont sized(Typeface typeface, float size) {
		if (!customEnabled() || FAILED.contains(typeface)) {
			return null;
		}
		if (atlas != null && atlas.isFull()) {
			// Rare: wipe and re-render what's needed. Glyphs are rendered again on demand.
			atlas.reset();
			FACES.values().forEach(FontFace::clearGlyphs);
		}
		FontFace face = FACES.get(typeface);
		if (face == null) {
			face = FontFace.load(typeface);
			if (face == null) {
				FAILED.add(typeface);
				return null;
			}
			FACES.put(typeface, face);
		}
		int pixels = Math.max(4, Math.round(size * UiScale.current()));
		return face.size(pixels);
	}

	// ---- measuring ----

	public static float width(Typeface typeface, String text, float size) {
		return width(typeface, text, size, 0);
	}

	/** Width in local units. {@code tracking} is extra space after each character, in local units. */
	public static float width(Typeface typeface, String text, float size, float tracking) {
		SizedFont font = sized(typeface, size);
		if (font == null) {
			return vanilla().width(text) * vanillaScale(size);
		}
		float pixelScale = UiScale.current();
		float width = 0;
		int previous = 0;
		int count = 0;
		for (int i = 0; i < text.length(); ) {
			int codepoint = text.codePointAt(i);
			i += Character.charCount(codepoint);
			Glyph glyph = font.glyph(codepoint);
			width += (font.face.kerning(font, previous, glyph.index()) + glyph.advance()) / pixelScale;
			previous = glyph.index();
			count++;
		}
		return width + tracking * Math.max(0, count - 1);
	}

	/** Height of capital letters in local units. */
	public static float capHeight(Typeface typeface, float size) {
		SizedFont font = sized(typeface, size);
		return font == null ? 7 * vanillaScale(size) : font.capHeight / UiScale.current();
	}

	/** Cuts {@code text} down with an ellipsis so it fits in {@code maxWidth}. */
	public static String ellipsize(Typeface typeface, String text, float size, float maxWidth) {
		if (width(typeface, text, size) <= maxWidth) {
			return text;
		}
		String ellipsis = "…";
		int end = text.length();
		while (end > 0 && width(typeface, text.substring(0, end) + ellipsis, size) > maxWidth) {
			end--;
		}
		return text.substring(0, end).stripTrailing() + ellipsis;
	}

	/** Greedy word wrap into lines no wider than {@code maxWidth}. Cache the result; it allocates. */
	public static java.util.List<String> wrap(Typeface typeface, String text, float size, float maxWidth) {
		java.util.List<String> lines = new java.util.ArrayList<>();
		StringBuilder line = new StringBuilder();
		for (String word : text.split(" ")) {
			String candidate = line.isEmpty() ? word : line + " " + word;
			if (!line.isEmpty() && width(typeface, candidate, size) > maxWidth) {
				lines.add(line.toString());
				line.setLength(0);
				line.append(word);
			} else {
				line.setLength(0);
				line.append(candidate);
			}
		}
		if (!line.isEmpty()) {
			lines.add(line.toString());
		}
		return lines;
	}

	// ---- drawing ----

	public static void draw(GuiGraphicsExtractor g, Typeface typeface, String text, float x, float centerY, float size, int color) {
		draw(g, typeface, text, x, centerY, size, color, Align.LEFT, 0, false);
	}

	public static void drawCentered(GuiGraphicsExtractor g, Typeface typeface, String text, float centerX, float centerY, float size, int color) {
		draw(g, typeface, text, centerX, centerY, size, color, Align.CENTER, 0, false);
	}

	/**
	 * Draws text with its capital letters vertically centred on {@code centerY}.
	 * {@code x} is the left edge, centre or right edge depending on {@code align}.
	 */
	public static void draw(GuiGraphicsExtractor g, Typeface typeface, String text, float x, float centerY, float size, int color,
							Align align, float tracking, boolean shadow) {
		if (text.isEmpty() || (color >>> 24) == 0) {
			return;
		}
		SizedFont font = sized(typeface, size);
		float width = width(typeface, text, size, tracking);
		float left = switch (align) {
			case LEFT -> x;
			case CENTER -> x - width / 2;
			case RIGHT -> x - width;
		};
		if (font == null) {
			drawVanilla(g, text, left, centerY, size, color, shadow);
			return;
		}
		float pixelScale = UiScale.current();
		float baseline = centerY + font.capHeight / pixelScale / 2;
		int shadowColor = (color & 0xFF000000) | ((color & 0xFCFCFC) >> 2);
		// Vanilla-style shadow: 1 unit for 9-unit text (1 GUI pixel for normal HUD text).
		float shadowOffset = shadow ? size / 9f : 0;
		g.guiRenderState.addGuiElement(new TextElement(font, text, left, baseline, width, pixelScale, tracking,
			color, shadowColor, shadowOffset, g.pose(), Minecraft.getInstance().getWindow().getGuiScale(),
			atlas().setup(), g.scissorStack.peek()));
	}

	// ---- vanilla fallback ----

	private static Font vanilla() {
		return Minecraft.getInstance().font;
	}

	/** Minecraft's capitals are 7 units tall; client fonts' are about 0.7 em. */
	private static float vanillaScale(float size) {
		return size / 10f;
	}

	private static void drawVanilla(GuiGraphicsExtractor g, String text, float left, float centerY, float size, int color, boolean shadow) {
		float scale = vanillaScale(size);
		g.pose().pushMatrix();
		g.pose().translate(left, centerY - 3.5f * scale);
		g.pose().scale(scale, scale);
		g.text(vanilla(), text, 0, 0, color, shadow);
		g.pose().popMatrix();
	}
}
