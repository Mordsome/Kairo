package dev.mordo.kairo.render.font;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.mordo.kairo.render.KairoPipelines;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * A run of text in a client font, submitted to the GUI render state.
 * Each glyph's top-left is snapped to the physical pixel grid, so text at rest is pixel-exact.
 */
final class TextElement implements GuiElementRenderState {
	private final SizedFont font;
	private final String text;
	private final float x;
	private final float baseline;
	private final float pixelScale;
	private final float tracking;
	private final int color;
	private final int shadowColor;
	private final float shadowOffset;
	private final float guiScale;
	private final float m00, m01, m10, m11, m20, m21;
	private final TextureSetup textures;
	private final @Nullable ScreenRectangle scissor;
	private final @Nullable ScreenRectangle bounds;

	TextElement(SizedFont font, String text, float x, float baseline, float width, float pixelScale, float tracking,
				int color, int shadowColor, float shadowOffset, Matrix3x2fc pose, float guiScale,
				TextureSetup textures, @Nullable ScreenRectangle scissor) {
		this.font = font;
		this.text = text;
		this.x = x;
		this.baseline = baseline;
		this.pixelScale = pixelScale;
		this.tracking = tracking;
		this.color = color;
		this.shadowColor = shadowColor;
		this.shadowOffset = shadowOffset;
		this.guiScale = guiScale;
		this.m00 = pose.m00();
		this.m01 = pose.m01();
		this.m10 = pose.m10();
		this.m11 = pose.m11();
		this.m20 = pose.m20();
		this.m21 = pose.m21();
		this.textures = textures;
		this.scissor = scissor;

		float top = baseline - font.ascent / pixelScale;
		float bottom = baseline + font.descent / pixelScale + shadowOffset;
		this.bounds = computeBounds(x, top, x + width + shadowOffset, bottom);
	}

	private @Nullable ScreenRectangle computeBounds(float left, float top, float right, float bottom) {
		float ax = m00 * left + m10 * top + m20, ay = m01 * left + m11 * top + m21;
		float bx = m00 * right + m10 * top + m20, by = m01 * right + m11 * top + m21;
		float cx = m00 * left + m10 * bottom + m20, cy = m01 * left + m11 * bottom + m21;
		float dx = m00 * right + m10 * bottom + m20, dy = m01 * right + m11 * bottom + m21;
		int minX = (int) Math.floor(Math.min(Math.min(ax, bx), Math.min(cx, dx))) - 1;
		int minY = (int) Math.floor(Math.min(Math.min(ay, by), Math.min(cy, dy))) - 1;
		int maxX = (int) Math.ceil(Math.max(Math.max(ax, bx), Math.max(cx, dx))) + 1;
		int maxY = (int) Math.ceil(Math.max(Math.max(ay, by), Math.max(cy, dy))) + 1;
		ScreenRectangle rect = new ScreenRectangle(minX, minY, maxX - minX, maxY - minY);
		return scissor != null ? scissor.intersection(rect) : rect;
	}

	@Override
	public void buildVertices(VertexConsumer vc) {
		if (shadowOffset != 0) {
			emit(vc, shadowOffset, shadowColor);
		}
		emit(vc, 0, color);
	}

	private void emit(VertexConsumer vc, float offset, int argb) {
		float pen = x + offset;
		int previous = 0;
		int i = 0;
		int length = text.length();
		while (i < length) {
			int codepoint = text.codePointAt(i);
			i += Character.charCount(codepoint);
			Glyph glyph = font.glyph(codepoint);
			pen += font.face.kerning(font, previous, glyph.index()) / pixelScale;
			if (!glyph.isEmpty()) {
				quad(vc, glyph, pen + glyph.left() / pixelScale, baseline + offset - glyph.top() / pixelScale, argb);
			}
			pen += glyph.advance() / pixelScale + tracking;
			previous = glyph.index();
		}
	}

	private void quad(VertexConsumer vc, Glyph glyph, float lx, float ly, int argb) {
		// Snap the glyph's top-left to a physical pixel; at rest the quad is then exactly glyph-sized.
		float gx = Math.round((m00 * lx + m10 * ly + m20) * guiScale) / guiScale;
		float gy = Math.round((m01 * lx + m11 * ly + m21) * guiScale) / guiScale;
		float w = glyph.width() / pixelScale;
		float h = glyph.height() / pixelScale;
		float wx = m00 * w, wy = m01 * w;
		float hx = m10 * h, hy = m11 * h;
		vc.addVertex(gx, gy, 0).setUv(glyph.u0(), glyph.v0()).setColor(argb);
		vc.addVertex(gx + hx, gy + hy, 0).setUv(glyph.u0(), glyph.v1()).setColor(argb);
		vc.addVertex(gx + wx + hx, gy + wy + hy, 0).setUv(glyph.u1(), glyph.v1()).setColor(argb);
		vc.addVertex(gx + wx, gy + wy, 0).setUv(glyph.u1(), glyph.v0()).setColor(argb);
	}

	@Override
	public RenderPipeline pipeline() {
		return KairoPipelines.TEXT;
	}

	@Override
	public TextureSetup textureSetup() {
		return textures;
	}

	@Override
	public @Nullable ScreenRectangle scissorArea() {
		return scissor;
	}

	@Override
	public @Nullable ScreenRectangle bounds() {
		return bounds;
	}
}
