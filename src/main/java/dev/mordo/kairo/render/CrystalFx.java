package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.mordo.kairo.core.VoidSettings;
import dev.mordo.kairo.core.VoidSettings.CrystalStyle;
import dev.mordo.kairo.cosmetics.Geo;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EndCrystalRenderer;
import net.minecraft.client.renderer.entity.state.EndCrystalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionfc;
import org.joml.Vector3f;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * Kairo Void end crystals: the crystal itself recoloured and lit up, an aura that always faces you, rings and
 * scan lines around it depending on the style, and a pop of light when one explodes. Only the picture changes.
 */
public final class CrystalFx {
	private static final RandomSource RANDOM = RandomSource.create();

	private record Gone(Vec3 pos, long at) {
	}

	/** Crystals that just disappeared, so an explosion right there can be recognised as a crystal popping. */
	private static final Deque<Gone> RECENT = new ArrayDeque<>();

	private CrystalFx() {
	}

	public static CrystalStyle style() {
		return Visuals.on() ? VoidSettings.INSTANCE.crystalStyle.get() : CrystalStyle.VANILLA;
	}

	/** Tint for the crystal model (ARGB), or -1 for vanilla. */
	public static int tint(float age) {
		int accent = Theme.accent() | 0xFF000000;
		return switch (style()) {
			case VANILLA -> -1;
			case PRISM -> Colors.lerp(0xFFFFFFFF, accent, 0.45f);
			case VOID -> Colors.withAlpha(Colors.lerp(accent, 0xFF050208, 0.72f), 0.92f);
			case PLASMA -> Colors.lerp(accent, 0xFFFFFFFF, 0.55f + 0.2f * (float) Math.sin(age * 0.9));
			case HOLO -> Colors.withAlpha(Colors.lerp(accent, 0xFF8FFBFF, 0.5f), 0.45f + 0.25f * flicker(age));
		};
	}

	private static float flicker(float age) {
		if (WorldFx.calm()) {
			return 1; // Fewer flashes: no flicker
		}
		float n = (float) Math.sin(age * 7.1) * (float) Math.sin(age * 3.3 + 1.2);
		return n > 0.85f ? 0 : 1;
	}

	/** Centre of the crystal (above the entity's position), like vanilla's bob. */
	private static float centreY(float age) {
		return 2 + EndCrystalRenderer.getY(age);
	}

	/** The extra geometry: aura, rings, scan lines, arcs. Called from the renderer at the entity's origin. */
	public static void submit(EndCrystalRenderState state, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState camera) {
		CrystalStyle style = style();
		if (style == CrystalStyle.VANILLA) {
			return;
		}
		float age = state.ageInTicks;
		float glow = VoidSettings.INSTANCE.crystalGlow.floatValue();
		int accent = Theme.accent() | 0xFF000000;
		Quaternionfc look = camera.orientation;
		Vector3f right = look.transform(new Vector3f(1, 0, 0));
		Vector3f up = look.transform(new Vector3f(0, 1, 0));
		Vector3f c = new Vector3f(0, centreY(age), 0);
		float pulse = 0.85f + 0.15f * (float) Math.sin(age * 0.25);
		collector.submitCustomGeometry(poseStack, Geo.glowType(), (pose, b) -> {
			switch (style) {
				case PRISM -> {
					disc(pose, b, c, right, up, 1.5f * pulse, accent, 0.42f * glow, 0);
					ring(pose, b, c, tilted(0.5f, age * 0.05f), 1.05f, 0.05f, accent, 0.9f * glow, age * 0.08f);
					ring(pose, b, c, tilted(-0.6f, age * -0.04f + 1.7f), 1.2f, 0.04f, Colors.lerp(accent, 0xFFFFFFFF, 0.5f), 0.7f * glow, -age * 0.06f);
				}
				case VOID -> {
					// A dark heart with a bright accretion rim.
					disc(pose, b, c, right, up, 0.75f, 0xFF000000, 0.35f, 0.0f);
					annulus(pose, b, c, right, up, 0.68f, 0.92f, accent, 0.85f * glow);
					annulus(pose, b, c, right, up, 0.92f, 1.3f * pulse, accent, 0.22f * glow);
					ring(pose, b, c, tilted(1.25f, age * 0.03f), 1.05f, 0.05f, accent, 0.7f * glow, age * 0.12f);
				}
				case PLASMA -> {
					disc(pose, b, c, right, up, 1.9f * pulse, accent, 0.5f * glow, 0);
					disc(pose, b, c, right, up, 0.9f, 0xFFFFFFFF, 0.55f * glow, 0);
					arcs(pose, b, c, age, accent, glow);
				}
				case HOLO -> {
					disc(pose, b, c, right, up, 1.3f, accent, 0.25f * glow, 0);
					// Scan rings sliding up through it.
					for (int i = 0; i < 3; i++) {
						float k = (age * 0.03f + i / 3f) % 1f;
						Vector3f at = new Vector3f(0, centreY(age) - 0.9f + k * 1.8f, 0);
						ring(pose, b, at, new Vector3f[]{new Vector3f(1, 0, 0), new Vector3f(0, 0, 1)}, 0.9f - Math.abs(k - 0.5f) * 0.6f, 0.03f,
							Colors.lerp(accent, 0xFF8FFBFF, 0.5f), 0.9f * glow * (float) Math.sin(k * Math.PI), 0);
					}
				}
				case VANILLA -> {
				}
			}
		});
	}

	/** A soft flat disc of light lying on the ground at the pose's origin (rare dropped items). */
	public static void groundGlow(PoseStack poseStack, SubmitNodeCollector collector, float radius, int color, float alpha) {
		Vector3f c = new Vector3f(0, 0.02f, 0);
		Vector3f x = new Vector3f(1, 0, 0);
		Vector3f z = new Vector3f(0, 0, 1);
		collector.submitCustomGeometry(poseStack, Geo.glowType(), (pose, b) -> disc(pose, b, c, x, z, radius, color, alpha, 0));
	}

	/** Two axes for a ring tilted by {@code tilt} and turned by {@code spin} round the vertical. */
	private static Vector3f[] tilted(float tilt, float spin) {
		Vector3f a = new Vector3f((float) Math.cos(spin), 0, (float) Math.sin(spin));
		Vector3f bAxis = new Vector3f(-(float) Math.sin(spin) * (float) Math.cos(tilt), (float) Math.sin(tilt), (float) Math.cos(spin) * (float) Math.cos(tilt));
		return new Vector3f[]{a, bAxis};
	}

	/** A soft disc facing you: bright in the middle ({@code alpha}), fading to {@code edgeAlpha} at the rim. */
	private static void disc(PoseStack.Pose pose, VertexConsumer b, Vector3f c, Vector3f right, Vector3f up, float r, int color, float alpha, float edgeAlpha) {
		int segments = 24;
		int mid = Colors.withAlpha(color, Math.min(1, alpha));
		int rim = Colors.withAlpha(color, Math.min(1, edgeAlpha));
		for (int i = 0; i < segments; i++) {
			Vector3f p0 = around(c, right, up, r, i * Math.PI * 2 / segments);
			Vector3f p1 = around(c, right, up, r, (i + 1) * Math.PI * 2 / segments);
			quad(pose, b, c, p0, p1, c, mid, rim, rim, mid);
		}
	}

	/** A flat band facing you between two radii, bright on the inside edge. */
	private static void annulus(PoseStack.Pose pose, VertexConsumer b, Vector3f c, Vector3f right, Vector3f up, float r0, float r1, int color, float alpha) {
		int segments = 32;
		int inner = Colors.withAlpha(color, Math.min(1, alpha));
		int outer = Colors.withAlpha(color, 0);
		for (int i = 0; i < segments; i++) {
			double a0 = i * Math.PI * 2 / segments;
			double a1 = (i + 1) * Math.PI * 2 / segments;
			quad(pose, b, around(c, right, up, r0, a0), around(c, right, up, r1, a0), around(c, right, up, r1, a1), around(c, right, up, r0, a1),
				inner, outer, outer, inner);
		}
	}

	/** A thin ring in the plane of {@code axes}, with a brighter stretch that runs round it. */
	private static void ring(PoseStack.Pose pose, VertexConsumer b, Vector3f c, Vector3f[] axes, float r, float width, int color, float alpha, float run) {
		int segments = 40;
		for (int i = 0; i < segments; i++) {
			double a0 = i * Math.PI * 2 / segments;
			double a1 = (i + 1) * Math.PI * 2 / segments;
			float bright = 0.45f + 0.55f * (float) Math.pow(0.5 + 0.5 * Math.cos(a0 - run), 6);
			int col = Colors.withAlpha(color, Math.min(1, alpha * bright));
			quad(pose, b, around(c, axes[0], axes[1], r - width, a0), around(c, axes[0], axes[1], r + width, a0),
				around(c, axes[0], axes[1], r + width, a1), around(c, axes[0], axes[1], r - width, a1), col, col, col, col);
		}
	}

	/** Crackling arcs from the core: jagged lines that jump around every couple of frames. */
	private static void arcs(PoseStack.Pose pose, VertexConsumer b, Vector3f c, float age, int color, float glow) {
		long seed = (long) (age * 0.5f);
		RandomSource random = RandomSource.create(seed * 31);
		for (int arc = 0; arc < 4; arc++) {
			Vector3f dir = new Vector3f(random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1, random.nextFloat() * 2 - 1).normalize();
			Vector3f side = new Vector3f(dir).cross(0, 1, 0);
			if (side.lengthSquared() < 0.01f) {
				side.set(1, 0, 0);
			}
			side.normalize();
			Vector3f prev = new Vector3f(c);
			for (int s = 1; s <= 5; s++) {
				Vector3f next = new Vector3f(dir).mul(s * 0.32f).add(c)
					.add((random.nextFloat() - 0.5f) * 0.25f, (random.nextFloat() - 0.5f) * 0.25f, (random.nextFloat() - 0.5f) * 0.25f);
				float a = glow * (1 - s / 6f);
				int col = Colors.withAlpha(Colors.lerp(color, 0xFFFFFFFF, 0.6f), Math.min(1, a));
				Vector3f w = new Vector3f(side).mul(0.03f);
				quad(pose, b, new Vector3f(prev).sub(w), new Vector3f(prev).add(w), new Vector3f(next).add(w), new Vector3f(next).sub(w), col, col, col, col);
				prev = next;
			}
		}
	}

	private static Vector3f around(Vector3f c, Vector3f a, Vector3f bAxis, float r, double angle) {
		float cos = (float) Math.cos(angle) * r;
		float sin = (float) Math.sin(angle) * r;
		return new Vector3f(c.x + a.x * cos + bAxis.x * sin, c.y + a.y * cos + bAxis.y * sin, c.z + a.z * cos + bAxis.z * sin);
	}

	/** Both windings, so it shows from either side. */
	private static void quad(PoseStack.Pose pose, VertexConsumer b, Vector3f p0, Vector3f p1, Vector3f p2, Vector3f p3, int c0, int c1, int c2, int c3) {
		vertex(pose, b, p0, c0);
		vertex(pose, b, p1, c1);
		vertex(pose, b, p2, c2);
		vertex(pose, b, p3, c3);
		vertex(pose, b, p3, c3);
		vertex(pose, b, p2, c2);
		vertex(pose, b, p1, c1);
		vertex(pose, b, p0, c0);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer b, Vector3f v, int argb) {
		b.addVertex(pose, v.x, v.y, v.z).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0).setNormal(pose, 0, 1, 0);
	}

	// ---- crystal pop ----

	/** A crystal entity went away on this client (exploded, or just out of range). */
	public static void removed(Entity entity) {
		if (entity instanceof EndCrystal) {
			RECENT.addLast(new Gone(entity.position(), System.nanoTime()));
			while (RECENT.size() > 16) {
				RECENT.removeFirst();
			}
		}
	}

	/** An explosion happened: if a crystal was there, it popped. */
	public static void explosion(Vec3 centre) {
		if (!Visuals.on() || !VoidSettings.INSTANCE.crystalPop.isOn()) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		ClientLevel level = mc.level;
		if (level == null) {
			return;
		}
		boolean crystal = !level.getEntitiesOfClass(EndCrystal.class, new AABB(centre, centre).inflate(1.5)).isEmpty();
		long now = System.nanoTime();
		for (Gone gone : RECENT) {
			if (now - gone.at() < 500_000_000L && gone.pos().distanceToSqr(centre) < 4) {
				crystal = true;
			}
		}
		if (crystal) {
			pop(level, centre.add(0, 1, 0));
		}
	}

	private static void pop(ClientLevel level, Vec3 at) {
		int accent = Theme.accent();
		VoidFx.crystalPop(at);
		// A flat ring racing out along the ground, and sparks thrown up.
		for (int i = 0; i < 48; i++) {
			double a = i * Math.PI * 2 / 48;
			dev.mordo.kairo.render.LightParticles.add(level, new DustParticleOptions(accent & 0xFFFFFF, 2.2f), at.x, at.y - 0.6, at.z, Math.cos(a) * 0.7, 0.02, Math.sin(a) * 0.7);
		}
		for (int i = 0; i < 40; i++) {
			Vec3 d = new Vec3(RANDOM.nextGaussian(), Math.abs(RANDOM.nextGaussian()) + 0.3, RANDOM.nextGaussian()).normalize().scale(0.35 + RANDOM.nextDouble() * 0.3);
			dev.mordo.kairo.render.LightParticles.add(level, i % 2 == 0 ? ParticleTypes.END_ROD : ParticleTypes.ELECTRIC_SPARK, at.x, at.y, at.z, d.x, d.y, d.z);
		}
	}
}
