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
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Optional;

/**
 * Kairo Void's combat effects, drawn in one screen pass over the world (after the other screen effects, before
 * the GUI):
 * <ul>
 *   <li>energy slashes: a crescent of light sweeps across the screen when you swing, with the air shimmering
 *   behind it; a second, crossing one on critical hits;</li>
 *   <li>impact shockwaves: a ripple that bends the picture where the hit lands, plus a white flash on the target;</li>
 *   <li>hit-stop: the picture holds for a few frames on strong hits (only the picture; the game keeps running);</li>
 *   <li>the totem pop blast: golden rays and a slow ripple from the middle of the screen.</li>
 * </ul>
 * Nothing here touches the game: no packets, no timing, no aim.
 */
public final class VoidFx {
	private static final int MAX_SLASHES = 4;
	private static final int MAX_WAVES = 4;
	/** Seconds a slash lives. */
	private static final float SLASH_LIFE = 0.42f;
	private static final float WAVE_LIFE = 0.5f;
	private static final float FLASH_LIFE = 0.16f;
	private static final float TOTEM_LIFE = 1.6f;
	private static final RandomSource RANDOM = RandomSource.create();
	private static final long START = System.nanoTime();

	private record Wave(long start, float x, float y, float strength) {
	}

	private static final Wave[] WAVES = new Wave[MAX_WAVES];
	private static int nextWave;

	private static int flashEntity = -1;
	private static long flashStart;
	private static long totemStart;

	private static long freezeUntil;
	private static boolean captureFreeze;
	private static boolean frozenValid;

	private static @Nullable GpuTexture copyTexture;
	private static @Nullable GpuTextureView copyView;
	private static @Nullable GpuTexture freezeTexture;
	private static @Nullable GpuTextureView freezeView;
	private static int width = -1;
	private static int height = -1;
	private static @Nullable MappableRingBuffer config;
	private static boolean failed;

	/** Automated screenshots pin the effect clock here (nanoseconds; 0 = real time). */
	public static long testClock;

	private VoidFx() {
	}

	private static long now() {
		return testClock != 0 ? testClock : System.nanoTime();
	}

	private static boolean on() {
		return Visuals.on() && !failed;
	}

	private static float seconds(long since) {
		return (now() - since) / 1e9f;
	}

	// ---- triggers ----

	/** The attack key was pressed (and the game will attack now). A slash unless this starts mining a block. */
	public static void onAttackKey(Minecraft mc) {
		LocalPlayer player = mc.player;
		if (!on() || player == null || mc.gameMode == null || player.isSpectator() || player.isHandsBusy()) {
			return;
		}
		HitResult hit = mc.hitResult;
		if (hit == null || hit instanceof BlockHitResult && hit.getType() == HitResult.Type.BLOCK) {
			return;
		}
		if (!VoidSettings.INSTANCE.slashes.isOn()) {
			return;
		}
		float strength = player.getAttackStrengthScale(0.5f);
		// Spam-clicks still get a blade, just a fainter one.
		HandAnimations.markEnergy(strength * strength);
	}

	/** Energy blades on your weapon are on (Kairo Void). */
	public static boolean energyBlades() {
		return on() && VoidSettings.INSTANCE.slashes.isOn();
	}

	public static float bladeSize() {
		return VoidSettings.INSTANCE.slashSize.floatValue();
	}

	public static float bladeBrightness() {
		return VoidSettings.INSTANCE.slashGlow.floatValue();
	}

	/** You attacked an entity (called before the game resolves the hit). */
	public static void onEntityAttack(LocalPlayer player, Entity target) {
		if (!on()) {
			return;
		}
		VoidSettings s = VoidSettings.INSTANCE;
		float strength = player.getAttackStrengthScale(0.5f);
		boolean full = strength > 0.9f;
		boolean crit = full && player.fallDistance > 0 && !player.onGround() && !player.onClimbable() && !player.isInWater()
			&& !player.isPassenger() && !player.isSprinting() && target instanceof LivingEntity;
		if (crit && s.slashes.isOn()) {
			HandAnimations.markCrit();
		}
		if (s.shockwaves.isOn()) {
			Vec3 centre = target.position().add(0, target.getBbHeight() * 0.55, 0);
			Vec3 ndc = Minecraft.getInstance().gameRenderer.projectPointToScreen(centre);
			if (Math.abs(ndc.x) < 1.2 && Math.abs(ndc.y) < 1.2) {
				float power = (full ? 1 : 0.45f) * (crit ? 1.35f : 1);
				WAVES[nextWave] = new Wave(now(), (float) (ndc.x * 0.5 + 0.5), (float) (ndc.y * 0.5 + 0.5), power);
				nextWave = (nextWave + 1) % MAX_WAVES;
			}
		}
		if (s.targetFlash.isOn() && !WorldFx.calm() && target instanceof LivingEntity) {
			flashEntity = target.getId();
			flashStart = now();
		}
		if (s.hitStop.isOn() && (full || crit)) {
			long length = (long) (s.hitStopLength.floatValue() * (crit ? 1.4f : 1f) * 1_000_000L);
			freezeUntil = now() + length;
			captureFreeze = true;
		}
	}

	/** An end crystal exploded at {@code at}: a big ripple from where it was. */
	public static void crystalPop(Vec3 at) {
		if (!on()) {
			return;
		}
		Vec3 ndc = Minecraft.getInstance().gameRenderer.projectPointToScreen(at);
		if (Math.abs(ndc.x) < 1.3 && Math.abs(ndc.y) < 1.3) {
			WAVES[nextWave] = new Wave(now(), (float) (ndc.x * 0.5 + 0.5), (float) (ndc.y * 0.5 + 0.5), 1.8f);
			nextWave = (nextWave + 1) % MAX_WAVES;
		}
	}

	/** Something you hit died at {@code at}: a ripple from there (with impact shockwaves on). */
	public static void onKill(Vec3 at) {
		if (!on() || !VoidSettings.INSTANCE.shockwaves.isOn()) {
			return;
		}
		Vec3 ndc = Minecraft.getInstance().gameRenderer.projectPointToScreen(at);
		if (Math.abs(ndc.x) < 1.2 && Math.abs(ndc.y) < 1.2) {
			WAVES[nextWave] = new Wave(now(), (float) (ndc.x * 0.5 + 0.5), (float) (ndc.y * 0.5 + 0.5), 1.4f);
			nextWave = (nextWave + 1) % MAX_WAVES;
		}
	}

	/** A totem of undying just saved you. */
	public static void onTotemPop() {
		if (on() && VoidSettings.INSTANCE.totemBlast.isOn() && !WorldFx.calm()) {
			totemStart = now();
		}
	}

	/** How white {@code entity} should be drawn right now (0 = normal). */
	public static float targetFlash(Entity entity) {
		if (flashEntity != entity.getId() || !on()) {
			return 0;
		}
		float t = seconds(flashStart);
		if (t > FLASH_LIFE) {
			flashEntity = -1;
			return 0;
		}
		float k = 1 - t / FLASH_LIFE;
		return k * k;
	}

	// ---- drawing ----

	private static boolean anythingAlive(long now) {
		for (Wave w : WAVES) {
			if (w != null && (now - w.start()) / 1e9f < WAVE_LIFE) {
				return true;
			}
		}
		return totemStart != 0 && (now - totemStart) / 1e9f < TOTEM_LIFE;
	}

	/** Runs after the screen effects, before the GUI. */
	public static void render() {
		if (!on()) {
			return;
		}
		long now = now();
		boolean freezing = now < freezeUntil;
		if (!freezing) {
			frozenValid = false;
		}
		if (!anythingAlive(now) && !freezing) {
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
			if (captureFreeze) {
				captureFreeze = false;
				pass(encoder, KairoPipelines.FX_COPY, mainView, linear, freezeView, false);
				frozenValid = true;
			}
			GpuTextureView source;
			if (freezing && frozenValid) {
				source = freezeView;
			} else {
				pass(encoder, KairoPipelines.FX_COPY, mainView, linear, copyView, false);
				source = copyView;
			}
			pass(encoder, KairoPipelines.VOID_FX, source, linear, mainView, true);
		} catch (RuntimeException e) {
			failed = true;
			Kairo.LOGGER.error("Void effects failed and are turned off for this session", e);
		}
	}

	private static void writeConfig(Std140Builder b) {
		long now = now();
		VoidSettings s = VoidSettings.INSTANCE;
		int accent = Theme.accent();
		b.putVec4((now - START) / 1e9f % 1000, (float) width / height, 0, 0);
		b.putVec4(((accent >> 16) & 0xFF) / 255f, ((accent >> 8) & 0xFF) / 255f, (accent & 0xFF) / 255f, 1);
		b.putVec4(1f / width, 1f / height, width, height);
		float totem = totemStart == 0 ? -1 : (now - totemStart) / 1e9f / TOTEM_LIFE;
		b.putVec4(totem, 0, 0, 0);
		for (Wave w : WAVES) {
			float age = w == null ? 2 : (now - w.start()) / 1e9f / WAVE_LIFE;
			if (w == null || age > 1) {
				b.putVec4(0, 0, 2, 0);
			} else {
				b.putVec4(w.x(), w.y(), age, w.strength());
			}
		}
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
		copyTexture = device.createTexture("Kairo void copy", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		copyView = device.createTextureView(copyTexture);
		freezeTexture = device.createTexture("Kairo void freeze", usage, GpuFormat.RGBA8_UNORM, w, h, 1, 1);
		freezeView = device.createTextureView(freezeTexture);
		frozenValid = false;
		if (config == null) {
			config = new MappableRingBuffer(() -> "Kairo void fx", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, 256);
		}
	}

	private static void close() {
		if (copyView != null) {
			copyView.close();
			copyTexture.close();
		}
		if (freezeView != null) {
			freezeView.close();
			freezeTexture.close();
		}
		copyView = freezeView = null;
		copyTexture = freezeTexture = null;
	}

	private static void pass(CommandEncoder encoder, com.mojang.renderpearl.api.pipeline.RenderPipeline pipeline, GpuTextureView input,
							 GpuSampler sampler, GpuTextureView output, boolean withConfig) {
		GpuBuffer buffer = null;
		if (withConfig) {
			buffer = config.currentBuffer();
			try (GpuBufferSlice.MappedView view = buffer.map(false, true)) {
				writeConfig(Std140Builder.intoBuffer(view.data()));
			}
		}
		try (RenderPass renderPass = encoder.createRenderPass(() -> "Kairo void fx", output, Optional.empty())) {
			renderPass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			RenderSystem.bindDefaultUniforms(renderPass);
			renderPass.setUniform("InSampler", input, sampler);
			if (buffer != null) {
				renderPass.setUniform("VoidConfig", buffer);
			}
			renderPass.draw(3, 1, 0, 0);
		}
		if (withConfig) {
			config.rotate();
		}
	}
}
