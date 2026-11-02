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
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * Screen effects on the world (and your hand), after vanilla's own post effects and before the GUI: motion blur,
 * then one grading pass (filter, glow, chromatic aberration, vignette, grain, cinema bars). Nothing runs while
 * every effect is off.
 */
public final class ScreenFx {
	private static @Nullable GpuTexture copyTexture;
	private static @Nullable GpuTextureView copyView;
	private static @Nullable GpuTexture historyTexture;
	private static @Nullable GpuTextureView historyView;
	private static int width = -1;
	private static int height = -1;
	private static boolean historyValid;
	private static boolean failed;
	private static MappableRingBuffer fxConfig;
	private static MappableRingBuffer blendConfig;
	private static final long START = System.nanoTime();

	private ScreenFx() {
	}

	/** Runs the effects on the main target. Called right after vanilla's post effects. */
	public static void render() {
		if (failed || !Visuals.live()) {
			historyValid = false;
			return;
		}
		GameVisuals v = GameVisuals.INSTANCE;
		boolean motion = v.motionBlur.floatValue() > 0.001f;
		boolean bloom = v.glow.floatValue() > 0.001f && !bloomFailed;
		float pulse = healthPulse();
		float speed = WorldFx.speedLines();
		float flash = WorldFx.flash();
		float hit = WorldFx.hitFlash();
		boolean grade = pulse > 0 || hasGrade(v) || speed > 0.01f || flash > 0.005f || hit > 0.005f;
		float turn = turning();
		if (!motion) {
			historyValid = false;
		}
		if (!motion && !grade && !bloom) {
			return;
		}
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			GpuTextureView mainView = main.getColorTextureView();
			if (mainView == null) {
				return;
			}
			ensureTargets(main.width, main.height);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			if (bloom) {
				try {
					Bloom.apply(encoder, mainView, main.width, main.height, v.glow.floatValue());
				} catch (RuntimeException e) {
					bloomFailed = true;
					Kairo.LOGGER.error("Bloom failed and is turned off for this session", e);
				}
			}
			if (motion) {
				// With "only when turning", the smear follows how fast the camera turns: still frames stay sharp.
				float amount = v.motionBlurTurning.isOn() ? turn : 1;
				float opacity = Math.min(0.92f, v.motionBlur.floatValue() * 0.85f) * amount;
				if (historyValid && opacity > 0.01f) {
					pass(encoder, KairoPipelines.FX_BLEND, historyView, linear, mainView, "BlendConfig", blendConfig,
						b -> b.putVec4(opacity, 0, 0, 0));
				}
				pass(encoder, KairoPipelines.FX_COPY, mainView, linear, historyView, null, null, null);
				historyValid = true;
			}
			if (grade) {
				pass(encoder, KairoPipelines.FX_COPY, mainView, linear, copyView, null, null, null);
				int accent = Theme.accent();
				float time = (System.nanoTime() - START) / 1e9f;
				pass(encoder, KairoPipelines.FX_GRADE, copyView, linear, mainView, "FxConfig", fxConfig, b -> b
					.putVec4(v.saturation.floatValue(), v.contrast.floatValue(), v.filterStrength.floatValue(), v.filter.get().ordinal())
					.putVec4(v.vignette.floatValue(), v.chromatic.floatValue(), v.grain.floatValue(), bloomFailed ? v.glow.floatValue() : 0)
					.putVec4(v.letterbox.floatValue(), time % 1000, pulse, v.accentVignette.isOn() ? 1 : 0)
					.putVec4(((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f, (accent & 0xFF) / 255f, 1)
					.putVec4(1f / width, 1f / height, width, height)
					.putVec4(speed, flash, hit, 0));
			}
		} catch (RuntimeException e) {
			failed = true;
			Kairo.LOGGER.error("Screen effects failed and are turned off for this session", e);
		}
	}

	private static boolean hasGrade(GameVisuals v) {
		return v.filter.get() != GameVisuals.Filter.NONE || Math.abs(v.saturation.floatValue() - 1f) > 0.01f
			|| Math.abs(v.contrast.floatValue() - 1f) > 0.01f || (bloomFailed && v.glow.floatValue() > 0) || v.vignette.floatValue() > 0
			|| v.chromatic.floatValue() > 0 || v.grain.floatValue() > 0 || v.letterbox.floatValue() > 0;
	}

	private static boolean bloomFailed;
	private static float lastYaw = Float.NaN;
	private static float lastPitch;
	private static long lastTurnNanos;
	private static float turnAmount;

	/** 0..1: how fast the camera is turning right now (smoothed), for "motion blur only when turning". */
	private static float turning() {
		var camera = Minecraft.getInstance().getCameraEntity();
		long now = System.nanoTime();
		if (camera == null) {
			lastYaw = Float.NaN;
			return 0;
		}
		float yaw = camera.getYRot();
		float pitch = camera.getXRot();
		float dt = Math.max(1e-3f, (now - lastTurnNanos) / 1e9f);
		float target = 0;
		if (!Float.isNaN(lastYaw) && dt < 0.25f) {
			float degrees = Math.abs(net.minecraft.util.Mth.wrapDegrees(yaw - lastYaw)) + Math.abs(pitch - lastPitch);
			target = Math.clamp(degrees / dt / 220f, 0, 1);
		}
		lastYaw = yaw;
		lastPitch = pitch;
		lastTurnNanos = now;
		float k = 1 - (float) Math.exp(-dt * (target > turnAmount ? 25 : 9));
		turnAmount += (target - turnAmount) * k;
		return turnAmount;
	}

	/** 0..1 red pulse at the screen edges while health is at 8 or less (option). */
	private static float healthPulse() {
		Minecraft mc = Minecraft.getInstance();
		if (!GameVisuals.INSTANCE.lowHealthPulse.isOn() || mc.player == null || mc.player.isCreative() || mc.player.isSpectator()) {
			return 0;
		}
		float health = mc.player.getHealth();
		if (health > 8 || health <= 0) {
			return 0;
		}
		float danger = 1 - health / 8f;
		// Fewer flashes: a steady red edge instead of a beating one.
		float beat = WorldFx.calm() ? 0.6f : (float) (0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * (4 + danger * 5)));
		return (0.35f + 0.65f * beat) * (0.4f + 0.6f * danger);
	}

	private static void ensureTargets(int w, int h) {
		if (w == width && h == height && copyView != null) {
			return;
		}
		close();
		width = w;
		height = h;
		GpuDevice device = RenderSystem.getDevice();
		int usage = GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING;
		copyTexture = device.createTexture("Kairo fx copy", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		copyView = device.createTextureView(copyTexture);
		historyTexture = device.createTexture("Kairo fx history", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		historyView = device.createTextureView(historyTexture);
		historyValid = false;
		if (fxConfig == null) {
			fxConfig = new MappableRingBuffer(() -> "Kairo fx config", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 96);
			blendConfig = new MappableRingBuffer(() -> "Kairo fx blend", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 16);
		}
	}

	private static void close() {
		if (copyView != null) {
			copyView.close();
			copyTexture.close();
		}
		if (historyView != null) {
			historyView.close();
			historyTexture.close();
		}
		copyView = historyView = null;
		copyTexture = historyTexture = null;
	}

	private static void pass(CommandEncoder encoder, RenderPipeline pipeline, GpuTextureView input, GpuSampler sampler, GpuTextureView output,
							 @Nullable String uniformName, @Nullable MappableRingBuffer config, @Nullable Consumer<Std140Builder> writer) {
		GpuBuffer buffer = null;
		if (config != null && writer != null) {
			buffer = config.currentBuffer();
			try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
				writer.accept(Std140Builder.intoBuffer(view.data()));
			}
		}
		try (RenderPass renderPass = encoder.createRenderPass(() -> "Kairo screen fx", output, Optional.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("InSampler", input, sampler);
			if (buffer != null) {
				renderPass.setUniform(uniformName, buffer);
			}
			renderPass.draw(3, 1, 0, 0);
		}
		if (config != null) {
			config.rotate();
		}
	}
}
