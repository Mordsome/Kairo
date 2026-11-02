package dev.mordo.kairo.render.background;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.KairoPipelines;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Kairo Void's menu background instead of Minecraft's panorama (when no image or GIF is picked): a drawn space scene
 * with nebulae, stars, a galaxy, a ringed planet and drifting crystal shards. See post/void_panorama.fsh.
 */
public final class VoidPanorama {
	private static boolean requested;
	private static boolean failed;
	private static @Nullable MappableRingBuffer config;
	private static final long START = System.nanoTime();

	private VoidPanorama() {
	}

	/** True when Void draws its own scene in place of the vanilla panorama. */
	public static boolean replaces() {
		return Edition.isVoid() && !failed && MainMenuSettings.INSTANCE.background.get().equals(MainMenuSettings.VANILLA_BACKGROUND);
	}

	public static void request() {
		requested = true;
	}

	/** Draws the scene into the frame if a screen asked for it this frame (right before the GUI). */
	public static void renderIfRequested() {
		if (!requested) {
			return;
		}
		requested = false;
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			GpuTextureView target = main.getColorTextureView();
			if (target == null) {
				return;
			}
			if (config == null) {
				config = new MappableRingBuffer(() -> "Kairo void panorama", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 32);
			}
			int accent = Theme.accent();
			MainMenuSettings s = MainMenuSettings.INSTANCE;
			GpuBuffer buffer = config.currentBuffer();
			try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
				Std140Builder.intoBuffer(view.data())
					.putVec4((System.nanoTime() - START) / 1e9f % 7200, (float) main.width / main.height, s.backgroundDim.floatValue() * 0.6f,
						Animation.reduceMotion() ? 0.15f : 1)
					.putVec4(((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f, (accent & 0xFF) / 255f, 1);
			}
			try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> "Kairo void panorama", target, Optional.empty())) {
				pass.setPipeline(RenderSystem.getCompiledPipeline(KairoPipelines.VOID_PANORAMA));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("PanoramaConfig", buffer);
				pass.draw(3, 1, 0, 0);
			}
			config.rotate();
		} catch (RuntimeException e) {
			failed = true;
			Kairo.LOGGER.error("Void panorama failed; using Minecraft's", e);
		}
	}
}
