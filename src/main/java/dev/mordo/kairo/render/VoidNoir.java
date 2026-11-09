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
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Kairo Void "Cinematic kill": for a moment after you get a kill the world becomes a drawing (white with black
 * outlines by default). It always fades in and out (never an instant flash); with Fewer flashes it is a calm grey.
 */
public final class VoidNoir {
	private static final float FADE_IN = 0.3f;
	private static final float FADE_OUT = 0.45f;

	private static long start;
	/** Where the kill was (world), or null for the middle of the screen. */
	private static net.minecraft.world.phys.@Nullable Vec3 killPos;
	/** The wave right now: how far it reaches (in screen heights) and how bright its front is. */
	private static float waveRadius;
	private static float waveFront;
	private static @Nullable GpuTexture copyTexture;
	private static @Nullable GpuTextureView copyView;
	private static int width = -1;
	private static int height = -1;
	private static @Nullable MappableRingBuffer config;
	private static boolean failed;
	/** The visual tour can hold it at full strength (negative = normal). */
	public static float testAmount = -1;

	private VoidNoir() {
	}

	/** You got a kill (the drawing spreads from the middle of the screen). */
	public static void onKill() {
		onKill(null);
	}

	/** You got a kill at {@code pos}: the drawing spreads out from there and later draws back into it. */
	public static void onKill(net.minecraft.world.phys.@Nullable Vec3 pos) {
		if (Visuals.on() && VoidSettings.INSTANCE.noirKill.isOn()) {
			start = System.nanoTime();
			killPos = pos;
		}
	}

	/** Sharp: a quick wipe. Smooth (and always with Fewer flashes): a slow, soft one. */
	private static boolean sharp() {
		return VoidSettings.INSTANCE.noirSpeed.get() == VoidSettings.NoirSpeed.SHARP && !WorldFx.calm();
	}

	/** 0..1: how much of the drawing shows right now. */
	public static float amount() {
		if (testAmount >= 0) {
			waveRadius = 4;
			waveFront = 0;
			return testAmount;
		}
		if (start == 0) {
			return 0;
		}
		float t = (System.nanoTime() - start) / 1e9f;
		boolean sharp = sharp();
		float spread = sharp ? 0.3f : 0.8f;
		float back = sharp ? 0.28f : 0.7f;
		float length = Math.max(VoidSettings.INSTANCE.noirLength.floatValue() * (sharp ? 0.7f : 1f), spread + back + 0.1f);
		if (t >= length) {
			start = 0;
			return 0;
		}
		// Out from the kill like a shockwave of ink (ease out), then drawn back into it at the end (ease in).
		float grow = Math.min(1, t / spread);
		float shrink = Math.clamp((length - t) / back, 0f, 1f);
		float reach = 2.6f;
		if (grow < 1) {
			waveRadius = reach * (float) Math.pow(grow, 1.7);
			waveFront = 1 - grow * 0.5f;
		} else if (shrink < 1) {
			waveRadius = reach * shrink * shrink;
			waveFront = 0.7f * shrink;
		} else {
			waveRadius = reach;
			waveFront = 0;
		}
		// A quick fade at the very start and end so nothing pops.
		return Math.min(smooth(Math.min(1, t / 0.08f)), smooth(Math.min(1, (length - t) / 0.08f)));
	}

	/** The kill point on screen (0..1, axis 0 = x, 1 = y), or the middle when it's behind you or unknown. */
	private static float waveCentre(CameraRenderState camera, int axis) {
		if (killPos == null || testAmount >= 0) {
			return 0.5f;
		}
		org.joml.Vector4f clip = new org.joml.Vector4f((float) (killPos.x - camera.pos.x), (float) (killPos.y - camera.pos.y), (float) (killPos.z - camera.pos.z), 1);
		new org.joml.Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix).transform(clip);
		if (clip.w <= 0.05f) {
			return 0.5f;
		}
		float v = (axis == 0 ? clip.x : clip.y) / clip.w;
		return Math.clamp(v * 0.5f + 0.5f, -0.5f, 1.5f);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	/** Called after the world (and the Void sky) is drawn, before the hand. */
	public static void render(CameraRenderState camera) {
		float amount = amount();
		if (amount <= 0.001f || failed || !Visuals.on()) {
			return;
		}
		try {
			RenderTarget main = Minecraft.getInstance().gameRenderer.mainRenderTarget();
			GpuTextureView mainView = main.getColorTextureView();
			GpuTextureView depthView = main.getDepthTextureView();
			if (mainView == null || depthView == null) {
				return;
			}
			ensureTargets(main.width, main.height);
			CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
			GpuSampler linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
			GpuSampler nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
			try (RenderPass pass = encoder.createRenderPass(() -> "Kairo noir copy", copyView, Optional.empty())) {
				pass.setPipeline(RenderSystem.getCompiledPipeline(KairoPipelines.FX_COPY));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("InSampler", mainView, linear);
				pass.draw(3, 1, 0, 0);
			}
			GpuBuffer buffer = config.currentBuffer();
			int accent = Theme.accent();
			try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
				Std140Builder.intoBuffer(view.data())
					.putVec4(amount, VoidSettings.INSTANCE.noirStyle.get().ordinal(), WorldFx.calm() ? 1 : 0, 0)
					.putVec4(((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f, (accent & 0xFF) / 255f, 1)
					.putVec4(0.05f, camera.depthFar, 1f / width, 1f / height)
					.putVec4(waveCentre(camera, 0), waveCentre(camera, 1), waveRadius, waveFront);
			}
			try (RenderPass pass = encoder.createRenderPass(() -> "Kairo noir", mainView, Optional.empty())) {
				pass.setPipeline(RenderSystem.getCompiledPipeline(KairoPipelines.VOID_NOIR));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("InSampler", copyView, nearest);
				pass.setUniform("DepthSampler", depthView, nearest);
				pass.setUniform("NoirConfig", buffer);
				pass.draw(3, 1, 0, 0);
			}
			config.rotate();
		} catch (RuntimeException e) {
			failed = true;
			Kairo.LOGGER.error("Cinematic kill failed and is turned off for this session", e);
		}
	}

	private static void ensureTargets(int w, int h) {
		if (w == width && h == height && copyView != null) {
			return;
		}
		if (copyView != null) {
			copyView.close();
			copyTexture.close();
		}
		width = w;
		height = h;
		GpuDevice device = RenderSystem.getDevice();
		copyTexture = device.createTexture("Kairo noir copy", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		copyView = device.createTextureView(copyTexture);
		if (config == null) {
			config = new MappableRingBuffer(() -> "Kairo noir", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 64);
		}
	}
}
