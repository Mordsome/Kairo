package dev.mordo.kairo.render;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.MappableRingBuffer;

import java.util.Optional;

/**
 * Dual Kawase blur of the rendered world, for menu backdrops and frosted glass.
 *
 * <p>Screens call {@link #request()} while their frame is being extracted. Later in the same frame,
 * after the world is rendered and before the GUI is drawn, {@code GameRendererMixin} calls
 * {@link #renderIfRequested()}, which runs the passes. Nothing runs on frames without a request.
 *
 * <pre>
 * world -> 1/2 -> 1/4 -> 1/8 -> 1/16          (downsample)
 *   1/16 -> 1/8 -> 1/4 -> glass (1/2)         strong blur for glass panels
 *   1/8  ->        1/4 -> background (1/2)    softer blur for the full-screen backdrop
 * </pre>
 */
public final class Blur {
	private static final int LEVELS = 4;
	private static final int PASSES = LEVELS + 3 + 2;
	private static final int UBO_SIZE = 16; // vec2 HalfTexel + float Offset, std140-padded

	private static final Target[] DOWN = new Target[LEVELS];
	private static Target up3;
	private static Target up2;
	private static Target glass;
	private static Target background;
	private static TextureSetup glassSetup;
	private static TextureSetup backgroundSetup;
	private static final MappableRingBuffer[] CONFIGS = new MappableRingBuffer[PASSES];

	private static int width = -1;
	private static int height = -1;
	private static boolean requested;
	private static boolean failed;

	private Blur() {
	}

	private record Target(GpuTexture texture, GpuTextureView view, int width, int height) {
		void close() {
			view.close();
			texture.close();
		}
	}

	public static boolean isAvailable() {
		return !failed;
	}

	/** Asks for the blur to be rendered this frame. */
	public static void request() {
		requested = true;
		ensureTargets();
	}

	/** Blurred world for glass panels. Using it requests the blur for this frame. */
	public static TextureSetup glassTexture() {
		request();
		return glassSetup;
	}

	/** Softer blurred world for the full-screen backdrop. Using it requests the blur for this frame. */
	public static TextureSetup backgroundTexture() {
		request();
		return backgroundSetup;
	}

	/** Runs the blur passes if a screen asked for them this frame. Called just before the GUI is drawn. */
	public static void renderIfRequested() {
		if (!requested || failed) {
			return;
		}
		requested = false;
		try {
			render();
		} catch (RuntimeException e) {
			failed = true;
			Kairo.LOGGER.error("Menu blur failed, falling back to plain tinted panels", e);
		}
	}

	private static void ensureTargets() {
		if (failed) {
			return;
		}
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		if (main.width == width && main.height == height && glassSetup != null) {
			return;
		}
		try {
			closeTargets();
			width = main.width;
			height = main.height;
			GpuDevice device = RenderSystem.getDevice();
			for (int i = 0; i < LEVELS; i++) {
				DOWN[i] = create(device, "down" + i, width >> (i + 1), height >> (i + 1));
			}
			up3 = create(device, "up3", width >> 3, height >> 3);
			up2 = create(device, "up2", width >> 2, height >> 2);
			glass = create(device, "glass", width >> 1, height >> 1);
			background = create(device, "background", width >> 1, height >> 1);
			GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			glassSetup = TextureSetup.singleTexture(glass.view(), linear);
			backgroundSetup = TextureSetup.singleTexture(background.view(), linear);
			for (int i = 0; i < PASSES; i++) {
				if (CONFIGS[i] == null) {
					int pass = i;
					CONFIGS[i] = new MappableRingBuffer(() -> "Kairo blur config " + pass,
						GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, UBO_SIZE);
				}
			}
		} catch (RuntimeException e) {
			failed = true;
			Kairo.LOGGER.error("Couldn't create blur targets, falling back to plain tinted panels", e);
		}
	}

	private static Target create(GpuDevice device, String name, int w, int h) {
		w = Math.max(1, w);
		h = Math.max(1, h);
		GpuTexture texture = device.createTexture("Kairo blur " + name,
			GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		return new Target(texture, device.createTextureView(texture), w, h);
	}

	private static void closeTargets() {
		for (int i = 0; i < LEVELS; i++) {
			if (DOWN[i] != null) {
				DOWN[i].close();
				DOWN[i] = null;
			}
		}
		for (Target t : new Target[]{up3, up2, glass, background}) {
			if (t != null) {
				t.close();
			}
		}
		up3 = up2 = glass = background = null;
		glassSetup = backgroundSetup = null;
	}

	private static void render() {
		RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
		GpuTextureView source = main.getColorTextureView();
		if (source == null || glassSetup == null) {
			return;
		}
		float strength = ClientSettings.INSTANCE.blurStrength.floatValue();
		// Keep the blur about the same size on screen at 1080p and 1440p.
		float offset = strength * Math.clamp(height / 1080f, 0.75f, 1.6f);
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);

		int pass = 0;
		pass(encoder, linear, KairoPipelines.KAWASE_DOWN, source, main.width, main.height, DOWN[0], offset, pass++);
		for (int i = 1; i < LEVELS; i++) {
			pass(encoder, linear, KairoPipelines.KAWASE_DOWN, DOWN[i - 1], DOWN[i], offset, pass++);
		}
		pass(encoder, linear, KairoPipelines.KAWASE_UP, DOWN[3], up3, offset * 1.2f, pass++);
		pass(encoder, linear, KairoPipelines.KAWASE_UP, up3, up2, offset * 1.2f, pass++);
		pass(encoder, linear, KairoPipelines.KAWASE_UP, up2, glass, offset * 1.2f, pass++);
		pass(encoder, linear, KairoPipelines.KAWASE_UP, DOWN[2], up2, offset, pass++);
		pass(encoder, linear, KairoPipelines.KAWASE_UP, up2, background, offset, pass);
	}

	private static void pass(CommandEncoder encoder, GpuSampler sampler, RenderPipeline pipeline, Target input, Target output, float offset, int index) {
		pass(encoder, sampler, pipeline, input.view(), input.width(), input.height(), output, offset, index);
	}

	private static void pass(CommandEncoder encoder, GpuSampler sampler, RenderPipeline pipeline, GpuTextureView input, int inW, int inH,
							 Target output, float offset, int index) {
		MappableRingBuffer config = CONFIGS[index];
		GpuBuffer buffer = config.currentBuffer();
		try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
			Std140Builder.intoBuffer(view.data())
				.putVec2(0.5f / inW, 0.5f / inH)
				.putFloat(offset);
		}
		try (RenderPass renderPass = encoder.createRenderPass(() -> "Kairo blur", output.view(), Optional.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("InSampler", input, sampler);
			renderPass.setUniform("BlurConfig", buffer);
			renderPass.draw(3, 1, 0, 0);
		}
		config.rotate();
	}
}
