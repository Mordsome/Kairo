package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2fc;
import org.jspecify.annotations.Nullable;

/**
 * One SDF shape quad, submitted to the GUI render state.
 *
 * <p>Attribute packing (matches shape.vsh):
 * <pre>
 * Position.xy  GUI position          Position.z  param 0
 * Color        fill / glass tint      UV0         position in shape space
 * UV1          stroke color (packed)  UV2         type|flags, stroke width * 64
 * UV3          params 1, 2            LineWidth   param 3
 * Normal       corner radius / 64, opacity, saturation - 1
 * </pre>
 * The quad is given in shape space (lx/ly) and transformed by the pose once, here, on submission.
 */
final class ShapeElement implements GuiElementRenderState {
	static final int RECT = 0;
	static final int SECTOR = 1;
	static final int SEGMENT = 2;
	static final int BACKDROP = 3;
	static final int TRIANGLE = 4;
	static final int FLAG_SOFT = 16;

	private final RenderPipeline pipeline;
	private final TextureSetup textureSetup;
	private final @Nullable ScreenRectangle scissor;
	private final @Nullable ScreenRectangle bounds;

	// Corners in GUI space, order: top-left, bottom-left, bottom-right, top-right.
	private final float x0, y0, x1, y1, x2, y2, x3, y3;
	// Matching corners in shape space.
	private final float u0, v0, u1, v1, u2, v2, u3, v3;

	private final int fillTop;
	private final int fillBottom;
	private final int strokeHi;
	private final int strokeLo;
	private final int typeFlags;
	private final int strokeFixed;
	private final float p0, p1, p2, p3;
	private final float cornerRadius, opacity, saturation;

	/**
	 * @param lx0 shape-space quad left   @param ly0 top
	 * @param lx1 shape-space quad right  @param ly1 bottom
	 * @param rotCos/rotSin rotation from shape space to the caller's space, applied before the pose
	 * @param ox/oy origin of shape space in the caller's space
	 */
	ShapeElement(
		RenderPipeline pipeline, TextureSetup textureSetup, Matrix3x2fc pose, @Nullable ScreenRectangle scissor,
		float ox, float oy, float rotCos, float rotSin,
		float lx0, float ly0, float lx1, float ly1,
		int fillTop, int fillBottom, int stroke, float strokeWidth,
		int typeFlags, float p0, float p1, float p2, float p3,
		float cornerRadius, float opacity, float saturation
	) {
		this.pipeline = pipeline;
		this.textureSetup = textureSetup;
		this.scissor = scissor;

		this.u0 = lx0; this.v0 = ly0;
		this.u1 = lx0; this.v1 = ly1;
		this.u2 = lx1; this.v2 = ly1;
		this.u3 = lx1; this.v3 = ly0;

		// shape space -> caller space (rotate + translate) -> GUI space (pose)
		float cx0 = ox + lx0 * rotCos - ly0 * rotSin, cy0 = oy + lx0 * rotSin + ly0 * rotCos;
		float cx1 = ox + lx0 * rotCos - ly1 * rotSin, cy1 = oy + lx0 * rotSin + ly1 * rotCos;
		float cx2 = ox + lx1 * rotCos - ly1 * rotSin, cy2 = oy + lx1 * rotSin + ly1 * rotCos;
		float cx3 = ox + lx1 * rotCos - ly0 * rotSin, cy3 = oy + lx1 * rotSin + ly0 * rotCos;
		float m00 = pose.m00(), m01 = pose.m01(), m10 = pose.m10(), m11 = pose.m11(), m20 = pose.m20(), m21 = pose.m21();
		this.x0 = m00 * cx0 + m10 * cy0 + m20; this.y0 = m01 * cx0 + m11 * cy0 + m21;
		this.x1 = m00 * cx1 + m10 * cy1 + m20; this.y1 = m01 * cx1 + m11 * cy1 + m21;
		this.x2 = m00 * cx2 + m10 * cy2 + m20; this.y2 = m01 * cx2 + m11 * cy2 + m21;
		this.x3 = m00 * cx3 + m10 * cy3 + m20; this.y3 = m01 * cx3 + m11 * cy3 + m21;

		this.fillTop = fillTop;
		this.fillBottom = fillBottom;
		this.strokeHi = ((stroke >> 16 & 0xFF) << 8) | (stroke >> 8 & 0xFF);
		this.strokeLo = ((stroke & 0xFF) << 8) | (stroke >>> 24);
		this.typeFlags = typeFlags;
		this.strokeFixed = Math.clamp(Math.round(strokeWidth * 64f), 0, Short.MAX_VALUE);
		this.p0 = p0;
		this.p1 = p1;
		this.p2 = p2;
		this.p3 = p3;
		this.cornerRadius = Math.clamp(cornerRadius / 64f, 0f, 1f);
		this.opacity = Math.clamp(opacity, 0f, 1f);
		this.saturation = Math.clamp(saturation - 1f, -1f, 1f);
		this.bounds = computeBounds();
	}

	private @Nullable ScreenRectangle computeBounds() {
		float minX = Math.min(Math.min(x0, x1), Math.min(x2, x3));
		float maxX = Math.max(Math.max(x0, x1), Math.max(x2, x3));
		float minY = Math.min(Math.min(y0, y1), Math.min(y2, y3));
		float maxY = Math.max(Math.max(y0, y1), Math.max(y2, y3));
		int left = (int) Math.floor(minX);
		int top = (int) Math.floor(minY);
		ScreenRectangle rect = new ScreenRectangle(left, top, (int) Math.ceil(maxX) - left, (int) Math.ceil(maxY) - top);
		return scissor != null ? scissor.intersection(rect) : rect;
	}

	@Override
	public void buildVertices(VertexConsumer vc) {
		vertex(vc, x0, y0, u0, v0, fillTop);
		vertex(vc, x1, y1, u1, v1, fillBottom);
		vertex(vc, x2, y2, u2, v2, fillBottom);
		vertex(vc, x3, y3, u3, v3, fillTop);
	}

	private void vertex(VertexConsumer vc, float x, float y, float u, float v, int color) {
		vc.addVertex(x, y, p0)
			.setColor(color)
			.setUv(u, v)
			.setUv1(strokeHi, strokeLo)
			.setUv2(typeFlags, strokeFixed)
			.setUv3(p1, p2)
			.setLineWidth(p3)
			.setNormal(cornerRadius, opacity, saturation);
	}

	@Override
	public RenderPipeline pipeline() {
		return pipeline;
	}

	@Override
	public TextureSetup textureSetup() {
		return textureSetup;
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
