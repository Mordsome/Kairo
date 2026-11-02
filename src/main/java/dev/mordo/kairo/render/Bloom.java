package dev.mordo.kairo.render;

import com.mojang.blaze3d.buffers.Std140Builder;
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
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Bloom (Visuals → Bloom): the bright parts of the frame (sky, fire, lava, torches, glowing effects) are picked out,
 * blurred wide at low resolution and added back on top, so light bleeds softly into what's around it.
 *
 * <pre>
 * frame -> bright parts (1/2) -> 1/4 -> 1/8 -> 1/16 -> 1/8 -> 1/4 -> 1/2 -> added onto the frame
 * </pre>
 */
final class Bloom {
	private static final int LEVELS = 4;
	private static final int PASSES = 1 + (LEVELS - 1) * 2 + 1;
	private static final Target[] DOWN = new Target[LEVELS];
	private static final Target[] UP = new Target[LEVELS - 1];
	private static final MappableRingBuffer[] CONFIGS = new MappableRingBuffer[PASSES];
	private static int width = -1;
	private static int height = -1;

	private record Target(GpuTexture texture, GpuTextureView view, int width, int height) {
		void close() {
			view.close();
			texture.close();
		}
	}

	private Bloom() {
	}

	/** Adds bloom of {@code strength} (0..1) onto {@code main}. */
	static void apply(CommandEncoder encoder, GpuTextureView main, int w, int h, float strength) {
		ensure(w, h);
		GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		float spread = Math.clamp(h / 1080f, 0.75f, 1.6f);
		int pass = 0;
		blur(encoder, linear, KairoPipelines.BLOOM_PREFILTER, main, w, h, DOWN[0], 0.5f, pass++);
		for (int i = 1; i < LEVELS; i++) {
			blur(encoder, linear, KairoPipelines.KAWASE_DOWN, DOWN[i - 1].view(), DOWN[i - 1].width(), DOWN[i - 1].height(), DOWN[i], spread, pass++);
		}
		Target from = DOWN[LEVELS - 1];
		for (int i = LEVELS - 2; i >= 0; i--) {
			blur(encoder, linear, KairoPipelines.KAWASE_UP, from.view(), from.width(), from.height(), UP[i], spread * 1.3f, pass++);
			from = UP[i];
		}
		MappableRingBuffer config = CONFIGS[pass];
		GpuBuffer buffer = config.currentBuffer();
		try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
			Std140Builder.intoBuffer(view.data()).putVec4(strength * 3.2f, 0, 0, 0);
		}
		try (RenderPass renderPass = encoder.createRenderPass(() -> "Kairo bloom add", main, Optional.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(KairoPipelines.BLOOM_ADD));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("InSampler", from.view(), linear);
			renderPass.setUniform("BlendConfig", buffer);
			renderPass.draw(3, 1, 0, 0);
		}
		config.rotate();
	}

	private static void blur(CommandEncoder encoder, GpuSampler sampler, RenderPipeline pipeline, GpuTextureView input, int inW, int inH, Target output,
							 float offset, int index) {
		MappableRingBuffer config = CONFIGS[index];
		GpuBuffer buffer = config.currentBuffer();
		try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
			Std140Builder.intoBuffer(view.data()).putVec2(0.5f / inW, 0.5f / inH).putFloat(offset);
		}
		try (RenderPass renderPass = encoder.createRenderPass(() -> "Kairo bloom", output.view(), Optional.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("InSampler", input, sampler);
			renderPass.setUniform("BlurConfig", buffer);
			renderPass.draw(3, 1, 0, 0);
		}
		config.rotate();
	}

	private static void ensure(int w, int h) {
		if (w == width && h == height && DOWN[0] != null) {
			return;
		}
		close();
		width = w;
		height = h;
		GpuDevice device = RenderSystem.getDevice();
		for (int i = 0; i < LEVELS; i++) {
			DOWN[i] = create(device, "down" + i, w >> (i + 1), h >> (i + 1));
		}
		for (int i = 0; i < LEVELS - 1; i++) {
			UP[i] = create(device, "up" + i, w >> (i + 1), h >> (i + 1));
		}
		for (int i = 0; i < PASSES; i++) {
			if (CONFIGS[i] == null) {
				int n = i;
				CONFIGS[i] = new MappableRingBuffer(() -> "Kairo bloom config " + n, GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 16);
			}
		}
	}

	private static Target create(GpuDevice device, String name, int w, int h) {
		w = Math.max(1, w);
		h = Math.max(1, h);
		GpuTexture texture = device.createTexture("Kairo bloom " + name, GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		return new Target(texture, device.createTextureView(texture), w, h);
	}

	private static void close() {
		for (Target[] list : new Target[][]{DOWN, UP}) {
			for (int i = 0; i < list.length; i++) {
				@Nullable Target t = list[i];
				if (t != null) {
					t.close();
					list[i] = null;
				}
			}
		}
	}
}
