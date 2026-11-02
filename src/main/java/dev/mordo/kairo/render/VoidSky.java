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
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.SkyRenderState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.material.FogType;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Kairo Void's sky and depth fog, drawn over the finished world just before your hand: at night a cosmic sky
 * (nebula clouds, a turning galaxy, a ringed planet, shooting stars) and aurora curtains; richer sunrises and
 * sunsets; and distant land fading into a sky-coloured haze. One screen pass that reads the world's depth to tell
 * sky from land and to know how far away each pixel is.
 */
public final class VoidSky {
	private static @Nullable GpuTexture copyTexture;
	private static @Nullable GpuTextureView copyView;
	private static int width = -1;
	private static int height = -1;
	private static @Nullable MappableRingBuffer config;
	private static boolean failed;
	private static final long START = System.nanoTime();
	/** Automated screenshots can pin the sky's own clock (seconds; negative = real time). */
	public static float testTime = -1;

	private VoidSky() {
	}

	private static boolean wanted(SkyRenderState sky, CameraRenderState camera) {
		if (!Visuals.on() || failed || camera.fogType != FogType.NONE) {
			return false;
		}
		VoidSettings s = VoidSettings.INSTANCE;
		boolean overworld = sky.skybox == DimensionType.Skybox.OVERWORLD;
		boolean skyFx = overworld && (s.cosmicSky.isOn() || s.aurora.isOn() || s.sunsets.isOn());
		return skyFx || s.depthFog.floatValue() > 0.001f || Atmospheres.current() != VoidSettings.Atmosphere.NONE;
	}

	/** Called after the world is drawn and before the hand. */
	public static void render(CameraRenderState camera, SkyRenderState sky) {
		if (!wanted(sky, camera)) {
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
			copy(encoder, mainView, linear);

			GpuBuffer buffer = config.currentBuffer();
			try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
				write(Std140Builder.intoBuffer(view.data()), camera, sky);
			}
			try (RenderPass pass = encoder.createRenderPass(() -> "Kairo void sky", mainView, Optional.empty())) {
				pass.setPipeline(RenderSystem.getCompiledPipeline(KairoPipelines.VOID_SKY));
				RenderSystem.bindDefaultUniforms(pass);
				pass.setUniform("InSampler", copyView, linear);
				pass.setUniform("DepthSampler", depthView, nearest);
				pass.setUniform("SkyConfig", buffer);
				pass.draw(3, 1, 0, 0);
			}
			config.rotate();
		} catch (RuntimeException e) {
			failed = true;
			Kairo.LOGGER.error("Void sky failed and is turned off for this session", e);
		}
	}

	private static void copy(CommandEncoder encoder, GpuTextureView from, GpuSampler sampler) {
		try (RenderPass pass = encoder.createRenderPass(() -> "Kairo void sky copy", copyView, Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(KairoPipelines.FX_COPY));
			RenderSystem.bindDefaultUniforms(pass);
			pass.setUniform("InSampler", from, sampler);
			pass.draw(3, 1, 0, 0);
		}
	}

	private static void write(Std140Builder b, CameraRenderState camera, SkyRenderState sky) {
		VoidSettings s = VoidSettings.INSTANCE;
		// Clip space back to camera-relative world space (no translation: the camera sits at 0).
		Matrix4f inverse = new Matrix4f(camera.projectionMatrix).mul(camera.viewRotationMatrix).invert();
		b.putMat4f(inverse);
		boolean overworld = sky.skybox == DimensionType.Skybox.OVERWORLD;
		// Same rotations vanilla uses for the sun and stars.
		Vector3f sun = new Vector3f(0, 1, 0).rotateX(sky.sunAngle).rotateY((float) Math.toRadians(-90));
		float time = testTime >= 0 ? testTime : (System.nanoTime() - START) / 1e9f % 3600;
		b.putVec4(sun.x, sun.y, sun.z, time);
		Vector3f sc = sky.skyColor != null ? new Vector3f(sky.skyColor) : new Vector3f(0.5f, 0.7f, 1f);
		float night = overworld ? Math.clamp(sky.starBrightness * 2.2f, 0, 1) : 0;
		b.putVec4(sc.x, sc.y, sc.z, night);
		Vector4fc glow = sky.sunriseAndSunsetColor;
		if (glow != null && overworld) {
			b.putVec4(glow.x(), glow.y(), glow.z(), glow.w());
		} else {
			b.putVec4(0, 0, 0, 0);
		}
		var fog = camera.fogData;
		b.putVec4(fog.color.x, fog.color.y, fog.color.z, Math.max(16, fog.renderDistanceEnd));
		int accent = Theme.accent();
		b.putVec4(((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f, (accent & 0xFF) / 255f, overworld ? sky.rainBrightness : 1);
		// Flags: cosmic, planet, shooting stars, aurora.
		b.putVec4(overworld && s.cosmicSky.isOn() ? 1 : 0, s.planet.isOn() ? 1 : 0, s.shootingStars.isOn() ? 1 : 0, overworld && s.aurora.isOn() ? 1 : 0);
		// Sunsets, depth fog, quality (0 low .. 3 insane), sky angle for the stars.
		int quality = switch (s.quality.get()) {
			case LOW -> 0;
			case MEDIUM -> 1;
			case HIGH, CUSTOM -> 2;
			case INSANE -> 3;
		};
		b.putVec4(overworld && s.sunsets.isOn() ? 1 : 0, s.depthFog.floatValue(), quality, sky.starAngle);
		b.putVec4((float) camera.pos.y, 0.05f, camera.depthFar, (float) width / height);
		// Atmosphere: sky gradient, fog, colour grade and extras, all scaled by its strength.
		VoidSettings.Atmosphere atmosphere = Atmospheres.current();
		Atmospheres.Look look = Atmospheres.look(atmosphere);
		float k = atmosphere == VoidSettings.Atmosphere.NONE ? 0 : Atmospheres.strength();
		b.putVec4(look.skyLow()[0], look.skyLow()[1], look.skyLow()[2], look.skyAmount() * k);
		b.putVec4(look.skyHigh()[0], look.skyHigh()[1], look.skyHigh()[2], look.cosmic() * k);
		b.putVec4(look.fog()[0], look.fog()[1], look.fog()[2], look.fogDensity() * k);
		b.putVec4(look.grade()[0], look.grade()[1], look.grade()[2], 1 + (look.saturation() - 1) * k);
		float snow = s.snowGround.isOn() ? look.snow() * k : 0;
		b.putVec4(snow, look.caustics() * k, look.feature(), Atmospheres.flash() * k);
		b.putVec4(k, (float) (camera.pos.x % 4096.0), (float) (camera.pos.z % 4096.0), 0);
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
		copyTexture = device.createTexture("Kairo void sky copy", GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING,
			GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		copyView = device.createTextureView(copyTexture);
		if (config == null) {
			config = new MappableRingBuffer(() -> "Kairo void sky", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 64 + 16 * 16);
		}
	}
}
