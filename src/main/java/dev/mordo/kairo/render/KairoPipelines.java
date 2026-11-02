package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import dev.mordo.kairo.core.ClientInfo;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Render pipelines used by the client. They compile on first use (26.x pipelines are compiled lazily by the
 * pipeline cache), and {@link #warmUp()} compiles them up front so the first menu open doesn't hitch.
 */
public final class KairoPipelines {
	/**
	 * Shape vertex format. Attribute names must be the vanilla semantic names, because BufferBuilder writes
	 * each attribute through its matching setter (setUv3, setLineWidth, ...). See {@link ShapeElement}.
	 */
	public static final VertexFormat SHAPE_FORMAT = VertexFormat.builder(0)
		.addAttribute(DefaultVertexFormat.POSITION_SEMANTIC_NAME, GpuFormat.RGB32_FLOAT)
		.addAttribute(DefaultVertexFormat.COLOR_SEMANTIC_NAME, GpuFormat.RGBA8_UNORM)
		.addAttribute(DefaultVertexFormat.UV0_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
		.addAttribute(DefaultVertexFormat.UV1_SEMANTIC_NAME, GpuFormat.RG16_SINT)
		.addAttribute(DefaultVertexFormat.UV2_SEMANTIC_NAME, GpuFormat.RG16_SINT)
		.addAttribute(DefaultVertexFormat.UV3_SEMANTIC_NAME, GpuFormat.RG32_FLOAT)
		.addAttribute(DefaultVertexFormat.LINE_WIDTH_SEMANTIC_NAME, GpuFormat.R32_FLOAT)
		.addAttribute(DefaultVertexFormat.NORMAL_SEMANTIC_NAME, GpuFormat.RGBA8_SNORM)
		.build();

	private static final ColorTargetState PREMULTIPLIED = new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);

	private static final RenderPipeline.Snippet SHAPE_SNIPPET = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withVertexShader(ClientInfo.id("core/shape"))
		.withFragmentShader(ClientInfo.id("core/shape"))
		.withColorTargetState(PREMULTIPLIED)
		.withVertexBinding(0, SHAPE_FORMAT)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.buildSnippet();

	/** Plain antialiased shapes. */
	public static final RenderPipeline SHAPE = RenderPipeline.builder(SHAPE_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/shape"))
		.build();

	/** Frosted glass shapes: Sampler0 is a blurred copy of the world. */
	public static final RenderPipeline GLASS = RenderPipeline.builder(SHAPE_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/glass"))
		.withShaderDefine("GLASS")
		.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
		.build();

	/** Glyphs from the client font atlas (single channel coverage). */
	public static final RenderPipeline TEXT = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/text"))
		.withBindGroupLayout(BindGroupLayouts.PROJECTION)
		.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
		.withBindGroupLayout(BindGroupLayouts.SAMPLER0)
		.withVertexShader(ClientInfo.id("core/text"))
		.withFragmentShader(ClientInfo.id("core/text"))
		.withColorTargetState(PREMULTIPLIED)
		.withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
		.withPrimitiveTopology(PrimitiveTopology.QUADS)
		.withCull(false)
		.build();

	static final BindGroupLayout BLUR_CONFIG = BindGroupLayout.builder()
		.withUniform("BlurConfig", UniformType.UNIFORM_BUFFER)
		.build();

	public static final RenderPipeline KAWASE_DOWN = blurPass("kawase_down");
	public static final RenderPipeline KAWASE_UP = blurPass("kawase_up");

	static final BindGroupLayout BACKGROUND_CONFIG = BindGroupLayout.builder()
		.withUniform("BackgroundConfig", UniformType.UNIFORM_BUFFER)
		.build();

	/** Custom menu background (image or GIF frame) drawn over the whole screen, before the GUI and the blur. */
	public static final RenderPipeline BACKGROUND = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/background"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/background"))
		.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
		.withBindGroupLayout(BACKGROUND_CONFIG)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();

	static final BindGroupLayout FX_CONFIG = BindGroupLayout.builder()
		.withUniform("FxConfig", UniformType.UNIFORM_BUFFER)
		.build();
	static final BindGroupLayout BLEND_CONFIG = BindGroupLayout.builder()
		.withUniform("BlendConfig", UniformType.UNIFORM_BUFFER)
		.build();

	/** Screen effects: copy of the frame, graded back onto the main target. */
	public static final RenderPipeline FX_COPY = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/fx_copy"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/fx_copy"))
		.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();
	public static final RenderPipeline FX_GRADE = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/fx_grade"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/fx_grade"))
		.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
		.withBindGroupLayout(FX_CONFIG)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();
	/** Motion blur: the history drawn over the frame with alpha blending. */
	public static final RenderPipeline FX_BLEND = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/fx_blend"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/fx_blend"))
		.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
		.withBindGroupLayout(BLEND_CONFIG)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(new ColorTargetState(com.mojang.renderpearl.api.pipeline.BlendFunction.TRANSLUCENT))
		.build();

	/** Bloom: the bright parts of the frame, at half size. */
	public static final RenderPipeline BLOOM_PREFILTER = blurPass("bloom_prefilter");
	/** Bloom: the blurred bright parts added back onto the frame. */
	public static final RenderPipeline BLOOM_ADD = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/bloom_add"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/bloom_add"))
		.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
		.withBindGroupLayout(BLEND_CONFIG)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(new ColorTargetState(BlendFunction.ADDITIVE))
		.build();

	static final BindGroupLayout VOID_CONFIG = BindGroupLayout.builder()
		.withUniform("VoidConfig", UniformType.UNIFORM_BUFFER)
		.build();

	/** Kairo Void's combat effects (slashes, shockwaves, totem blast) over a copy of the frame. */
	public static final RenderPipeline VOID_FX = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/void_fx"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/void_fx"))
		.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
		.withBindGroupLayout(VOID_CONFIG)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();

	static final BindGroupLayout SKY_INPUTS = BindGroupLayout.builder()
		.withUniform("DepthSampler", UniformType.COMBINED_IMAGE_SAMPLER)
		.withUniform("SkyConfig", UniformType.UNIFORM_BUFFER)
		.build();

	/** Kairo Void's cosmic sky, aurora, sunsets and depth fog, over a copy of the world (before the hand). */
	public static final RenderPipeline VOID_SKY = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/void_sky"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/void_sky"))
		.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
		.withBindGroupLayout(SKY_INPUTS)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();

	static final BindGroupLayout PANORAMA_CONFIG = BindGroupLayout.builder()
		.withUniform("PanoramaConfig", UniformType.UNIFORM_BUFFER)
		.build();

	/** Kairo Void's drawn menu background (space scene) in place of the panorama. */
	public static final RenderPipeline VOID_PANORAMA = RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/void_panorama"))
		.withVertexShader("core/screenquad")
		.withFragmentShader(ClientInfo.id("post/void_panorama"))
		.withBindGroupLayout(PANORAMA_CONFIG)
		.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
		.withColorTargetState(ColorTargetState.DEFAULT)
		.build();

	/** Held item outline: vanilla's entity vertex shader, our flat-colour fragment shader, item texture alpha as the shape. */
	public static final RenderPipeline ITEM_SILHOUETTE = RenderPipeline.builder(RenderPipelines.ENTITY_EMISSIVE_SNIPPET)
		.withLocation(ClientInfo.id("pipeline/item_silhouette"))
		.withFragmentShader(ClientInfo.id("core/silhouette"))
		.withShaderDefine("NO_OVERLAY")
		.withColorTargetState(ColorTargetState.DEFAULT)
		.withCull(false)
		.build();

	private KairoPipelines() {
	}

	private static RenderPipeline blurPass(String name) {
		return RenderPipeline.builder(RenderPipelines.GLOBALS_SNIPPET)
			.withLocation(ClientInfo.id("pipeline/" + name))
			.withVertexShader("core/screenquad")
			.withFragmentShader(ClientInfo.id("post/" + name))
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withBindGroupLayout(BLUR_CONFIG)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withColorTargetState(ColorTargetState.DEFAULT)
			.build();
	}

	/** Compiles every pipeline now (call once resources are loaded). Failures are logged by the cache. */
	public static void warmUp() {
		for (RenderPipeline pipeline : new RenderPipeline[]{SHAPE, GLASS, TEXT, KAWASE_DOWN, KAWASE_UP, BACKGROUND, FX_COPY, FX_GRADE, FX_BLEND, BLOOM_PREFILTER, BLOOM_ADD, ITEM_SILHOUETTE, VOID_FX, VOID_SKY, VOID_PANORAMA}) {
			com.mojang.blaze3d.systems.RenderSystem.getCompiledPipeline(pipeline);
		}
	}
}
