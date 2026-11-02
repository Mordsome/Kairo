package dev.mordo.kairo.render.font;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.jspecify.annotations.Nullable;

/** A font at one pixel size, with its glyph cache. Metrics are in physical pixels. */
final class SizedFont {
	private static final Glyph MISSING = new Glyph(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);

	final FontFace face;
	final int pixelSize;
	final float ascent;
	final float descent;
	/** Height of a capital letter, for vertically centring labels. */
	final float capHeight;

	/** Fast path for Latin-1, the general map for everything else. */
	private final Glyph[] latin = new Glyph[256];
	private final Int2ObjectOpenHashMap<Glyph> others = new Int2ObjectOpenHashMap<>();

	SizedFont(FontFace face, int pixelSize, float ascent, float descent) {
		this.face = face;
		this.pixelSize = pixelSize;
		this.ascent = ascent;
		this.descent = descent;
		Glyph h = face.render(this, 'H', Fonts.atlas());
		this.capHeight = h != null && !h.isEmpty() ? h.top() : ascent * 0.7f;
		if (h != null) {
			latin['H'] = h;
		}
	}

	/** The glyph for a codepoint, rendering it on first use. Never null (missing glyphs draw nothing). */
	Glyph glyph(int codepoint) {
		Glyph glyph = codepoint < 256 ? latin[codepoint] : others.get(codepoint);
		if (glyph != null) {
			return glyph;
		}
		@Nullable Glyph rendered = face.render(this, codepoint, Fonts.atlas());
		if (rendered == null) {
			if (Fonts.atlasFull()) {
				return MISSING; // not cached: retried once the atlas has been reset
			}
			// The font simply doesn't have it.
			rendered = codepoint == '?' ? MISSING : glyph('?');
		}
		if (codepoint < 256) {
			latin[codepoint] = rendered;
		} else {
			others.put(codepoint, rendered);
		}
		return rendered;
	}

	void clear() {
		java.util.Arrays.fill(latin, null);
		others.clear();
	}
}
