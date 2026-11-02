package dev.mordo.kairo.render.font;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.TextureUtil;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Size_Metrics;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;

import java.io.InputStream;
import java.nio.ByteBuffer;

/**
 * One TTF file opened with FreeType (the same library Minecraft ships). Glyphs are rendered on demand
 * at the exact pixel size they are drawn at, which is what keeps text sharp at every GUI scale.
 */
final class FontFace {
	private final Typeface typeface;
	private @Nullable ByteBuffer data;
	private @Nullable FT_Face face;
	private boolean hasKerning;
	private int currentPixelSize = -1;
	private final Int2ObjectOpenHashMap<SizedFont> sizes = new Int2ObjectOpenHashMap<>();

	private FontFace(Typeface typeface) {
		this.typeface = typeface;
	}

	/** Returns null if the font can't be loaded (the caller falls back to the vanilla font). */
	static @Nullable FontFace load(Typeface typeface) {
		FontFace font = new FontFace(typeface);
		try (InputStream in = Minecraft.getInstance().getResourceManager().open(ClientInfo.id("font/" + typeface.file()))) {
			font.data = TextureUtil.readResource(in);
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer faceBuffer = stack.mallocPointer(1);
					FreeTypeUtil.assertError(FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), font.data, 0L, faceBuffer), "Loading " + typeface.file());
					font.face = FT_Face.create(faceBuffer.get());
				}
				FreeTypeUtil.assertError(FreeType.FT_Select_Charmap(font.face, FreeType.FT_ENCODING_UNICODE), "Unicode charmap");
				font.hasKerning = FreeType.FT_HAS_KERNING(font.face);
			}
			return font;
		} catch (Exception e) {
			Kairo.LOGGER.error("Couldn't load font {}", typeface.file(), e);
			if (font.data != null) {
				MemoryUtil.memFree(font.data);
			}
			return null;
		}
	}

	SizedFont size(int pixelSize) {
		SizedFont sized = sizes.get(pixelSize);
		if (sized == null) {
			setPixelSize(pixelSize);
			FT_Size_Metrics metrics = face.size().metrics();
			float ascent = metrics.ascender() / 64f;
			float descent = -metrics.descender() / 64f;
			sized = new SizedFont(this, pixelSize, ascent, descent);
			sizes.put(pixelSize, sized);
		}
		return sized;
	}

	private void setPixelSize(int pixelSize) {
		if (currentPixelSize != pixelSize) {
			FreeType.FT_Set_Pixel_Sizes(face, 0, pixelSize);
			currentPixelSize = pixelSize;
		}
	}

	/** Renders one glyph into the atlas. Returns null if the font doesn't have it. */
	@Nullable Glyph render(SizedFont sized, int codepoint, GlyphAtlas atlas) {
		int index = FreeType.FT_Get_Char_Index(face, codepoint);
		if (index == 0) {
			return null;
		}
		setPixelSize(sized.pixelSize);
		if (FreeTypeUtil.checkError(FreeType.FT_Load_Glyph(face, index, FreeType.FT_LOAD_RENDER | FreeType.FT_LOAD_TARGET_LIGHT), "Glyph")) {
			return null;
		}
		FT_GlyphSlot slot = face.glyph();
		FT_Bitmap bitmap = slot.bitmap();
		float advance = slot.linearHoriAdvance() / 65536f;
		int w = bitmap.width();
		int h = bitmap.rows();
		if (w == 0 || h == 0 || bitmap.pixel_mode() != FreeType.FT_PIXEL_MODE_GRAY) {
			return new Glyph(index, advance, 0, 0, 0, 0, 0, 0, 0, 0);
		}

		// Copy into an image with a 1px empty border, so linear filtering fades to nothing at the edges.
		int pitch = bitmap.pitch();
		ByteBuffer pixels = bitmap.buffer(Math.abs(pitch) * h);
		int packed;
		try (NativeImage image = new NativeImage(NativeImage.Format.LUMINANCE, w + 2, h + 2, true)) {
			long dst = image.getPointer();
			long src = MemoryUtil.memAddress(pixels);
			for (int row = 0; row < h; row++) {
				MemoryUtil.memCopy(src + (long) row * pitch, dst + (long) (row + 1) * (w + 2) + 1, w);
			}
			packed = atlas.add(image);
		}
		if (packed < 0) {
			return null; // atlas full; Fonts resets it and tries again next frame
		}
		int ax = (packed & 0xFFFF) + 1;
		int ay = (packed >>> 16) + 1;
		float inv = 1f / GlyphAtlas.SIZE;
		return new Glyph(index, advance, slot.bitmap_left(), slot.bitmap_top(), w, h,
			ax * inv, ay * inv, (ax + w) * inv, (ay + h) * inv);
	}

	/** Kerning between two glyph indices, in pixels at the given size. */
	float kerning(SizedFont sized, int left, int right) {
		if (!hasKerning || left == 0 || right == 0) {
			return 0;
		}
		setPixelSize(sized.pixelSize);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			FT_Vector vector = FT_Vector.malloc(stack);
			if (FreeType.FT_Get_Kerning(face, left, right, FreeType.FT_KERNING_UNFITTED, vector) != 0) {
				return 0;
			}
			return vector.x() / 64f;
		}
	}

	/** Forgets all rendered glyphs (after the atlas was wiped). */
	void clearGlyphs() {
		for (SizedFont sized : sizes.values()) {
			sized.clear();
		}
	}

	Typeface typeface() {
		return typeface;
	}
}
