package dev.mordo.kairo.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.mordo.kairo.core.GameVisuals;
import dev.mordo.kairo.core.GameVisuals.MiningStyle;
import dev.mordo.kairo.core.GameVisuals.SwingStyle;
import dev.mordo.kairo.cosmetics.Geo;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.InteractionHand;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * First-person attack and mining animations. Runs on its own clock (started by your swings), so an animation can be
 * slower or longer than vanilla's without touching the game's own swing timing, cooldowns or packets.
 *
 * <p>Transforms are applied in the hand's space after vanilla's arm placement: x right, y up, -z forward;
 * {@code side} is +1 for the right hand and -1 for the left.
 */
public final class HandAnimations {
	private static final float VANILLA_SWING_MS = 300; // 6 ticks
	private static final int TRAIL_SAMPLES = 14;

	private static long swingStart = -1;
	private static InteractionHand swingHand = InteractionHand.MAIN_HAND;
	private static double miningPhase;
	private static boolean miningActive;
	private static long lastFrame = -1;
	private static long inspectStart = -1;
	private static net.minecraft.world.item.@org.jspecify.annotations.Nullable ItemStack inspectItem;

	/** Automated screenshots pin the animation clock here (nanoseconds; 0 = real time). */
	public static long testClock;

	private HandAnimations() {
	}

	private static long now() {
		return testClock != 0 ? testClock : System.nanoTime();
	}

	/** True when our animations replace vanilla's whack swing. */
	public static boolean active() {
		return GameVisuals.INSTANCE.animatesHands();
	}

	// ---- inspect ----

	/** Starts the inspect animation for the item in your main hand (the inspect key). */
	public static void inspect() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null || mc.gui.screen() != null || mc.player.getMainHandItem().isEmpty()) {
			return;
		}
		if (inspecting() && inspectProgress() < 0.6f) {
			return; // pressing again mid-way doesn't restart it
		}
		inspectStart = now();
		inspectItem = mc.player.getMainHandItem();
	}

	private static float inspectSeconds() {
		return switch (GameVisuals.INSTANCE.inspectStyle.get()) {
			case LOOK_OVER -> 2.6f;
			case TWIRL -> 1.6f;
			case TOSS -> 1.5f;
			case FLOURISH -> 2.0f;
		};
	}

	private static float inspectProgress() {
		return inspectStart < 0 ? -1 : (now() - inspectStart) / 1e9f / inspectSeconds();
	}

	public static boolean inspecting() {
		float p = inspectProgress();
		return p >= 0 && p < 1;
	}

	private static void cancelInspect() {
		inspectStart = -1;
		inspectItem = null;
	}

	private static void inspectPose(PoseStack ps, float p, float side) {
		float e = keys(p, 0, 0, 0.14f, 1, 0.86f, 1, 1, 0);
		switch (GameVisuals.INSTANCE.inspectStyle.get()) {
			case LOOK_OVER -> {
				// Bring it up and in, turn it to show one side, then the other, then put it back.
				ps.translate(side * -0.26f * e, 0.1f * e, 0.08f * e);
				turn(ps, side);
				float yaw = keys(p, 0, 0, 0.2f, 0, 0.4f, 70, 0.55f, 70, 0.74f, -55, 0.88f, -55, 1, 0);
				float roll = keys(p, 0, 0, 0.2f, 22, 0.6f, 8, 1, 0);
				ps.rotate(Axis.YP, rad(side * yaw));
				ps.rotate(Axis.ZP, rad(side * roll));
				ps.rotate(Axis.XP, rad(-18 * e));
				unturn(ps, side);
			}
			case TWIRL -> {
				float q = Math.clamp((p - 0.12f) / 0.76f, 0, 1);
				ps.translate(side * -0.14f * e, 0.12f * e, 0.02f * e);
				turn(ps, side);
				ps.translate(0, 0.2f, 0);
				ps.rotate(Axis.ZP, rad(side * -720 * smooth(q)));
				ps.translate(0, -0.2f, 0);
				unturn(ps, side);
			}
			case TOSS -> {
				// Flicked up, spins end over end, caught.
				float q = Math.clamp((p - 0.18f) / 0.62f, 0, 1);
				float height = 0.7f * 4 * q * (1 - q);
				ps.translate(side * -0.12f * e, height + 0.05f * e, -0.06f * e);
				turn(ps, side);
				ps.translate(0, 0.2f, 0);
				ps.rotate(Axis.XP, rad(-360 * q * 2));
				ps.translate(0, -0.2f, 0);
				unturn(ps, side);
			}
			case FLOURISH -> {
				// Swung round the hand in a figure, like a butterfly knife.
				float q = Math.clamp((p - 0.12f) / 0.76f, 0, 1);
				float s = (float) Math.sin(q * Math.PI * 2);
				ps.translate(side * (-0.2f * e - 0.12f * s), 0.1f * e + 0.06f * (float) Math.sin(q * Math.PI * 4), 0);
				turn(ps, side);
				ps.rotate(Axis.YP, rad(side * 540 * smooth(q)));
				ps.rotate(Axis.ZP, rad(side * 30 * s));
				unturn(ps, side);
			}
		}
	}

	/** Called when the local player starts a swing (vanilla's timing decides when that can happen). */
	public static void onSwing(InteractionHand hand) {
		cancelInspect();
		float p = swingProgress();
		if (swingStart < 0 || p < 0 || p >= 0.5f) {
			swingStart = now();
			swingHand = hand;
			// Void energy blade: only swings that came from the attack key (not mining or placing).
			boolean fresh = now() - energyMarked < 150_000_000L;
			energySwing = fresh;
			energyCrit = fresh && energyCritMarked;
			energyPower = energyMarkedPower;
			energyMarked = 0;
			energyCritMarked = false;
		}
	}

	private static long energyMarked;
	private static boolean energyCritMarked;
	private static float energyMarkedPower = 1;
	private static boolean energySwing;
	private static boolean energyCrit;
	private static float energyPower = 1;

	/** The attack key was pressed: the swing that follows gets an energy blade. {@code power} 0..1 is the attack charge. */
	public static void markEnergy(float power) {
		energyMarked = now();
		energyMarkedPower = power;
	}

	/** This attack is a critical hit: the blade burns brighter and throws light further past the tip. */
	public static void markCrit() {
		energyCritMarked = true;
		float p = swingProgress();
		if (energySwing && p >= 0 && p < 0.3f) {
			energyCrit = true;
		}
	}

	private static float swingMillis() {
		return VANILLA_SWING_MS * GameVisuals.INSTANCE.swingDuration.floatValue();
	}

	/** 0..1 through the current attack swing, or -1 when there's none. */
	private static float swingProgress() {
		if (swingStart < 0) {
			return -1;
		}
		float p = (now() - swingStart) / 1_000_000f / swingMillis();
		return p >= 1 ? -1 : p;
	}

	private static boolean mining() {
		Minecraft mc = Minecraft.getInstance();
		return mc.gameMode != null && mc.gameMode.isDestroying() && GameVisuals.INSTANCE.miningStyle.get() != MiningStyle.VANILLA;
	}

	/** Advances the mining loop once per frame; it finishes its current swing after you stop mining. */
	private static void advance() {
		long now = now();
		if (now == lastFrame) {
			return;
		}
		float dt = lastFrame < 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		boolean holding = mining();
		if (holding) {
			cancelInspect();
		}
		if (holding && !miningActive) {
			miningActive = true;
			miningPhase = 0;
		}
		if (miningActive) {
			double before = miningPhase;
			miningPhase += dt / GameVisuals.INSTANCE.miningDuration.floatValue();
			if (!holding && Math.floor(miningPhase) > Math.floor(before)) {
				miningActive = false;
				miningPhase = 0;
			}
		}
	}

	/**
	 * Applies this frame's animation for one hand. Called right before the held item is drawn, in place of vanilla's
	 * whack swing (which is skipped while {@link #active()}).
	 */
	public static void apply(PoseStack poseStack, SubmitNodeCollector collector, InteractionHand hand, float side) {
		advance();
		if (hand == InteractionHand.MAIN_HAND && inspecting()) {
			Minecraft mc = Minecraft.getInstance();
			if (mc.player == null || mc.player.getMainHandItem() != inspectItem || mc.player.isUsingItem()) {
				cancelInspect();
			} else {
				inspectPose(poseStack, inspectProgress(), side);
				return;
			}
		}
		if (hand == InteractionHand.MAIN_HAND && miningActive) {
			mining(poseStack, GameVisuals.INSTANCE.miningStyle.get(), (float) (miningPhase - Math.floor(miningPhase)), side);
			return;
		}
		float p = swingProgress();
		if (p < 0 || hand != swingHand) {
			return;
		}
		SwingStyle style = GameVisuals.INSTANCE.swingStyle.get();
		if (GameVisuals.INSTANCE.swingTrail.isOn()) {
			trail(poseStack, collector, style, p, side);
		}
		if (energySwing && VoidFx.energyBlades()) {
			energyBlade(poseStack, collector, style, p, side);
		}
		swing(poseStack, style, p, side);
	}

	// ---- attack styles ----

	private static void swing(PoseStack ps, SwingStyle style, float p, float side) {
		float s = (float) Math.sin(p * Math.PI);
		switch (style) {
			case VANILLA -> vanilla(ps, p, side);
			case SMOOTH -> vanilla(ps, smooth(p), side);
			case SLASH -> {
				// Pull back right, sweep hard across to the left, drift back.
				float a = keys(p, 0, 0, 0.18f, 30, 0.5f, -115, 1, 0);
				ps.translate(side * -0.12f * s, 0.06f * s, -0.12f * s);
				turn(ps, side);
				ps.rotate(Axis.YP, rad(side * a));
				ps.rotate(Axis.ZP, rad(side * -65 * s));
				ps.rotate(Axis.XP, rad(-20 * s));
				unturn(ps, side);
			}
			case CHOP -> {
				float a = keys(p, 0, 0, 0.3f, 45, 0.55f, -100, 1, 0);
				float lift = keys(p, 0, 0, 0.3f, 0.14f, 0.55f, -0.05f, 1, 0);
				ps.translate(side * -0.1f * s, lift, -0.1f * s);
				turn(ps, side);
				ps.rotate(Axis.XP, rad(a));
				ps.rotate(Axis.ZP, rad(side * -10 * s));
				unturn(ps, side);
			}
			case STAB -> {
				float k = keys(p, 0, 0, 0.3f, 1, 0.55f, 1, 1, 0);
				ps.translate(side * -0.2f * k, 0.12f * k, -0.45f * k);
				turn(ps, side);
				ps.rotate(Axis.XP, rad(-75 * k));
				ps.rotate(Axis.YP, rad(side * 25 * k));
				unturn(ps, side);
			}
			case SPIN -> {
				ps.translate(side * -0.08f * s, 0.12f * s, -0.15f * s);
				ps.translate(0, 0.2f, 0);
				ps.rotate(Axis.ZP, rad(side * -360 * smooth(p)));
				ps.translate(0, -0.2f, 0);
			}
			case FLIP -> {
				ps.translate(side * -0.06f * s, 0.2f * s, -0.12f * s);
				turn(ps, side);
				ps.translate(0, 0.18f, 0);
				ps.rotate(Axis.XP, rad(-360 * smooth(p)));
				ps.translate(0, -0.18f, 0);
				unturn(ps, side);
			}
			case SWIRL -> {
				float t = (float) (p * Math.PI * 2);
				ps.translate(side * -0.25f * (float) Math.sin(t), 0.12f * (float) Math.sin(2 * t), -0.15f * s);
				turn(ps, side);
				ps.rotate(Axis.ZP, rad(side * 40 * (float) Math.sin(t)));
				ps.rotate(Axis.XP, rad(-55 * s));
				unturn(ps, side);
			}
			case PUNCH -> {
				float k = keys(p, 0, 0, 0.25f, 1, 1, 0);
				ps.translate(side * -0.12f * k, 0.05f * k, -0.28f * k);
				turn(ps, side);
				ps.rotate(Axis.XP, rad(-40 * k));
				ps.rotate(Axis.ZP, rad(side * -15 * k));
				unturn(ps, side);
			}
			case SWEEP -> {
				// A low wide arc from the right, rising to the upper left.
				float a = keys(p, 0, 0, 0.55f, 1, 1, 0);
				ps.translate(side * (0.1f - 0.45f * a), -0.1f * s + 0.25f * a * a, -0.2f * s);
				ps.rotate(Axis.ZP, rad(side * (-30 - 60 * a)));
				ps.rotate(Axis.YP, rad(side * -50 * a));
			}
		}
	}

	private static void vanilla(PoseStack ps, float p, float side) {
		float sq = (float) Math.sin(Math.sqrt(p) * Math.PI);
		ps.translate(side * -0.4f * sq, 0.2f * (float) Math.sin(Math.sqrt(p) * Math.PI * 2), -0.2f * (float) Math.sin(p * Math.PI));
		float y = (float) Math.sin(p * p * Math.PI);
		ps.rotate(Axis.YP, rad(side * (45 - 20 * y)));
		ps.rotate(Axis.ZP, rad(side * -20 * sq));
		ps.rotate(Axis.XP, rad(-80 * sq));
		ps.rotate(Axis.YP, rad(side * -45));
	}

	// ---- mining styles ----

	private static void mining(PoseStack ps, MiningStyle style, float c, float side) {
		float t = (float) (c * Math.PI * 2);
		switch (style) {
			case VANILLA, SMOOTH -> vanilla(ps, smooth(c), side);
			case SIDEWAYS -> {
				// The tool lies on its side and sweeps left and right like a pendulum.
				float w = (float) Math.sin(t);
				ps.translate(side * (-0.1f - 0.14f * w), 0.04f + 0.03f * (float) Math.cos(2 * t), -0.12f);
				ps.rotate(Axis.ZP, rad(side * -72));
				ps.rotate(Axis.XP, rad(-25 + 35 * w));
			}
			case HAMMER -> {
				float a = keys(c, 0, 0, 0.45f, 40, 0.58f, -85, 0.7f, -60, 0.8f, -78, 1, 0);
				ps.translate(side * -0.08f, 0.1f * Math.max(0, a) / 40f, -0.08f);
				turn(ps, side);
				ps.rotate(Axis.XP, rad(a));
				unturn(ps, side);
			}
			case DRILL -> {
				float jitter = (float) Math.sin(c * Math.PI * 2 * 6);
				ps.translate(side * (-0.18f + 0.012f * jitter), 0.1f + 0.012f * (float) Math.cos(c * Math.PI * 2 * 7), -0.22f - 0.05f * (float) Math.sin(t));
				ps.rotate(Axis.XP, rad(-80));
				ps.rotate(Axis.YP, rad(side * 360 * c));
			}
			case CIRCLE -> {
				ps.translate(side * -0.14f * (float) Math.cos(t), 0.08f * (float) Math.sin(t), -0.1f);
				turn(ps, side);
				ps.rotate(Axis.XP, rad(-40 - 30 * (float) Math.sin(t)));
				ps.rotate(Axis.ZP, rad(side * 20 * (float) Math.cos(t)));
				unturn(ps, side);
			}
			case SAW -> {
				float w = (float) Math.sin(t);
				ps.translate(side * -0.12f, 0.02f, -0.15f - 0.12f * w);
				turn(ps, side);
				ps.rotate(Axis.XP, rad(-50));
				ps.rotate(Axis.ZP, rad(side * -20));
				unturn(ps, side);
			}
		}
	}

	// ---- trail ----

	/** The blade tip and a point halfway down it, in the hand's space (vanilla handheld first-person transform). */
	private static final Vector3f TIP = handheld(0.42f, 0.42f);
	private static final Vector3f MID = handheld(0.0f, 0.0f);

	private static Vector3f handheld(float mx, float my) {
		// Item models span -0.5..0.5; first-person handheld = translate (1.13, 3.2, 1.13)/16, rotate (0, -90, 25), scale 0.68.
		Vector3f v = new Vector3f(mx * 0.68f, my * 0.68f, 0);
		new Quaternionf().rotationXYZ(0, rad(-90), rad(25)).transform(v);
		return v.add(1.13f / 16, 3.2f / 16, 1.13f / 16);
	}

	private static void trail(PoseStack ps, SubmitNodeCollector collector, SwingStyle style, float p, float side) {
		float span = 0.45f;
		Vector3f[] tips = new Vector3f[TRAIL_SAMPLES];
		Vector3f[] mids = new Vector3f[TRAIL_SAMPLES];
		PoseStack scratch = new PoseStack();
		for (int i = 0; i < TRAIL_SAMPLES; i++) {
			float q = Math.max(0, p - span * i / (TRAIL_SAMPLES - 1));
			scratch.setIdentity();
			swing(scratch, style, q, side);
			Matrix4f m = scratch.last().pose();
			tips[i] = m.transformPosition(new Vector3f(TIP.x * side, TIP.y, TIP.z));
			mids[i] = m.transformPosition(new Vector3f(MID.x * side, MID.y, MID.z));
		}
		float fadeIn = Math.min(1, p * 6) * Math.min(1, (1 - p) * 3);
		boolean rainbow = GameVisuals.INSTANCE.swingTrailRainbow.isOn();
		int accent = Theme.accent();
		float hue = (System.currentTimeMillis() % 3000) / 3000f;
		collector.submitCustomGeometry(ps, Geo.glowType(), (pose, buffer) -> {
			for (int i = 0; i < TRAIL_SAMPLES - 1; i++) {
				float a0 = (1 - i / (float) (TRAIL_SAMPLES - 1)) * 0.75f * fadeIn;
				float a1 = (1 - (i + 1) / (float) (TRAIL_SAMPLES - 1)) * 0.75f * fadeIn;
				int c0 = rainbow ? Colors.hsv(hue + i * 0.05f, 0.7f, 1) : accent;
				int c1 = rainbow ? Colors.hsv(hue + (i + 1) * 0.05f, 0.7f, 1) : accent;
				ribbon(pose, buffer, mids[i], tips[i], tips[i + 1], mids[i + 1], c0, a0, c1, a1);
			}
		});
	}

	// ---- Kairo Void energy blade ----

	/**
	 * A blade of light swept along with the sword: a soft glow over the blade, a white-hot edge reaching past the tip,
	 * and on critical hits a brighter, longer spill of light. Drawn in the hand's space, so it stays on the weapon.
	 */
	private static void energyBlade(PoseStack ps, SubmitNodeCollector collector, SwingStyle style, float p, float side) {
		float size = VoidFx.bladeSize();
		float glow = VoidFx.bladeBrightness() * (0.5f + 0.5f * energyPower);
		boolean crit = energyCrit;
		float life = Math.min(1, p * 10) * (float) Math.pow(Math.max(0, 1 - p), 0.7);
		int accent = Theme.accent() | 0xFF000000;
		int hot = Colors.lerp(accent, 0xFFFFFFFF, 0.65f);
		// The blade runs along the item's diagonal; "across" is the other diagonal, in the sprite's own plane.
		float from = -0.08f;
		float to = 0.5f + 0.06f * size;
		float wide = 0.15f * (0.8f + 0.4f * size) * (crit ? 1.35f : 1);
		Vector3f[] axis = {handheld(from, from), handheld(to, to)};
		Vector3f[] outer = {handheld(from + wide, from - wide), handheld(to + wide * 0.4f, to - wide * 0.4f)};
		Vector3f[] outer2 = {handheld(from - wide, from + wide), handheld(to - wide * 0.4f, to + wide * 0.4f)};
		Vector3f tipPoint = handheld(to + 0.06f * size, to + 0.06f * size);

		// The short streak the tip leaves behind it.
		int samples = 10;
		float span = 0.09f;
		Vector3f edge = handheld(0.4f, 0.4f);
		Vector3f tip = handheld(to, to);
		Vector3f[] edges = new Vector3f[samples];
		Vector3f[] tips = new Vector3f[samples];
		PoseStack scratch = new PoseStack();
		for (int i = 0; i < samples; i++) {
			float q = Math.max(0, p - span * i / (samples - 1));
			scratch.setIdentity();
			swing(scratch, style, q, side);
			Matrix4f m = scratch.last().pose();
			edges[i] = m.transformPosition(new Vector3f(edge.x * side, edge.y, edge.z));
			tips[i] = m.transformPosition(new Vector3f(tip.x * side, tip.y, tip.z));
		}
		float core = wide * 0.22f;
		Vector3f[] raw = {axis[0], axis[1], outer[0], outer[1], outer2[0], outer2[1], tipPoint,
			handheld(from + core, from - core), handheld(to + core * 0.5f, to - core * 0.5f),
			handheld(from - core, from + core), handheld(to - core * 0.5f, to + core * 0.5f)};
		Vector3f[] mirrored = new Vector3f[raw.length];
		for (int i = 0; i < raw.length; i++) {
			mirrored[i] = new Vector3f(raw[i].x * side, raw[i].y, raw[i].z);
		}
		// The aura is drawn in the swung pose, so it sits on the sword.
		PoseStack swungPose = new PoseStack();
		swing(swungPose, style, p, side);
		Matrix4f swung = swungPose.last().pose();
		for (Vector3f v : mirrored) {
			swung.transformPosition(v);
		}
		float a = life * glow;
		collector.submitCustomGeometry(ps, Geo.glowType(), (pose, buffer) -> {
			// Aura: brightest along the blade, fading out to both sides.
			ribbonFade(pose, buffer, mirrored[2], mirrored[0], mirrored[1], mirrored[3], accent, 0, a * 0.75f, 0, a * 0.95f);
			ribbonFade(pose, buffer, mirrored[4], mirrored[0], mirrored[1], mirrored[5], accent, 0, a * 0.75f, 0, a * 0.95f);
			// A white-hot core along the blade, running out into a point past the tip.
			ribbonFade(pose, buffer, mirrored[7], mirrored[0], mirrored[1], mirrored[8], hot, 0, a * 0.5f, 0, a);
			ribbonFade(pose, buffer, mirrored[9], mirrored[0], mirrored[1], mirrored[10], hot, 0, a * 0.5f, 0, a);
			ribbonFade(pose, buffer, mirrored[8], mirrored[1], mirrored[6], mirrored[10], hot, a * 0.6f, a, 0, 0);
			// The streak behind the tip.
			for (int i = 0; i < samples - 1; i++) {
				float k0 = 1 - i / (float) (samples - 1);
				float k1 = 1 - (i + 1) / (float) (samples - 1);
				float a0 = k0 * k0 * a;
				float a1 = k1 * k1 * a;
				ribbonFade(pose, buffer, edges[i], tips[i], tips[i + 1], edges[i + 1], crit ? hot : accent, 0, a0 * 0.7f, 0, a1 * 0.7f);
			}
		});
	}
	/** A two-sided strip piece with separate alphas for its inner and outer edges. */
	private static void ribbonFade(PoseStack.Pose pose, VertexConsumer b, Vector3f in0, Vector3f out0, Vector3f out1, Vector3f in1,
								   int color, float inA0, float outA0, float inA1, float outA1) {
		int i0 = Colors.withAlpha(color, Math.min(1, inA0));
		int o0 = Colors.withAlpha(color, Math.min(1, outA0));
		int o1 = Colors.withAlpha(color, Math.min(1, outA1));
		int i1 = Colors.withAlpha(color, Math.min(1, inA1));
		vertex(pose, b, in0, i0);
		vertex(pose, b, out0, o0);
		vertex(pose, b, out1, o1);
		vertex(pose, b, in1, i1);
		vertex(pose, b, in1, i1);
		vertex(pose, b, out1, o1);
		vertex(pose, b, out0, o0);
		vertex(pose, b, in0, i0);
	}

	private static void ribbon(PoseStack.Pose pose, VertexConsumer b, Vector3f m0, Vector3f t0, Vector3f t1, Vector3f m1,
							   int c0, float a0, int c1, float a1) {
		int inner0 = Colors.withAlpha(c0, a0 * 0.15f);
		int inner1 = Colors.withAlpha(c1, a1 * 0.15f);
		int outer0 = Colors.withAlpha(c0, a0);
		int outer1 = Colors.withAlpha(c1, a1);
		vertex(pose, b, m0, inner0);
		vertex(pose, b, t0, outer0);
		vertex(pose, b, t1, outer1);
		vertex(pose, b, m1, inner1);
		// back side
		vertex(pose, b, m1, inner1);
		vertex(pose, b, t1, outer1);
		vertex(pose, b, t0, outer0);
		vertex(pose, b, m0, inner0);
	}

	private static void vertex(PoseStack.Pose pose, VertexConsumer b, Vector3f v, int argb) {
		b.addVertex(pose, v.x, v.y, v.z).setColor(argb).setUv(0.5f, 0.5f).setOverlay(OverlayTexture.NO_OVERLAY)
			.setLight(0xF000F0).setNormal(pose, 0, 1, 0);
	}

	// ---- helpers ----

	/** Like vanilla: rotations happen around axes turned 45 degrees, so the flat item faces you instead of going edge-on. */
	private static void turn(PoseStack ps, float side) {
		ps.rotate(Axis.YP, rad(side * 45));
	}

	private static void unturn(PoseStack ps, float side) {
		ps.rotate(Axis.YP, rad(side * -45));
	}

	private static float rad(float degrees) {
		return (float) Math.toRadians(degrees);
	}

	private static float smooth(float t) {
		return t * t * (3 - 2 * t);
	}

	/** Smoothly interpolated keyframes: pairs of (time, value), times rising from 0 to 1. */
	private static float keys(float t, float... kv) {
		for (int i = 0; i + 3 < kv.length; i += 2) {
			float t0 = kv[i];
			float t1 = kv[i + 2];
			if (t <= t1) {
				float u = t1 > t0 ? Math.clamp((t - t0) / (t1 - t0), 0, 1) : 1;
				return kv[i + 1] + (kv[i + 3] - kv[i + 1]) * smooth(u);
			}
		}
		return kv[kv.length - 1];
	}
}
