package dev.mordo.kairo.gui.main;

import dev.mordo.kairo.cosmetics.Capes;
import dev.mordo.kairo.cosmetics.Cosmetics;
import dev.mordo.kairo.cosmetics.Emotes;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * The Stage main menu style (Kairo Void's default): your player standing in a spotlight on a dark stage, with dust
 * drifting through the beam, a glossy floor that reflects them, a slow camera drift, and an emote now and then.
 * The menu itself stays in the left column.
 */
final class StageMenu {
	private static final int FLOOR = 0x08080B;
	private static final int BACK_TOP = 0x0E0E13;
	private static final int BACK_BOTTOM = 0x060608;
	private static final Emotes.Emote[] SHOW = {Emotes.Emote.WAVE, Emotes.Emote.DANCE, Emotes.Emote.BACKFLIP, Emotes.Emote.SALUTE, Emotes.Emote.SPIN};
	/** Seconds between emotes, and how long a looping one (Dance) runs. */
	private static final float EMOTE_EVERY = 8.5f;
	private static final float LOOP_FOR = 4.0f;

	private final long start = System.nanoTime();
	private @Nullable Supplier<PlayerSkin> skin;
	private int nextEmote;
	private float lastEmoteAt = -3;
	private boolean loopRunning;

	void reset() {
		Minecraft mc = Minecraft.getInstance();
		skin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
	}

	void close() {
		Emotes.stopPreview();
	}

	private float time() {
		return (System.nanoTime() - start) / 1e9f;
	}

	/** Where the stage sits: the player stands at (x, floor). In GUI pixels. */
	private static float centreX(float w, float t) {
		return w * 0.665f + (float) Math.sin(t * 0.07) * w * 0.012f;
	}

	/** The whole stage, behind the menu. {@code a} fades it in. */
	void draw(GuiGraphicsExtractor g, float intro) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		float t = time();
		float a = Animation.motion(intro, 1);
		int accent = Theme.accent();
		float cx = centreX(w, t);
		float floorY = h * 0.80f;

		// Room: dark, a touch lighter at the top, and a faint colour glow on the back wall.
		Shapes.gradientRect(g, 0, 0, w, floorY, 0, Colors.argb(1, BACK_TOP), Colors.argb(1, BACK_BOTTOM));
		Shapes.rect(g, 0, floorY, w, h - floorY, 0, Colors.argb(1, FLOOR));
		for (int i = 0; i < 16; i++) {
			float r = h * (0.12f + i * 0.032f);
			Shapes.circle(g, cx, floorY - h * 0.28f, r, Colors.withAlpha(accent, 0.008f * a));
		}

		// Spotlight beam: from above, widening to a pool on the floor, soft at the edges.
		float sway = (float) Math.sin(t * 0.21) * h * 0.012f;
		float topW = h * 0.05f;
		float bottomW = h * 0.62f;
		float top = -h * 0.04f;
		int light = Colors.lerp(0xFFFFF4E6, accent | 0xFF000000, 0.18f);
		// Nested triangles (apex up, off the top of the screen) give the beam soft edges without seams.
		float beamH = floorY - top;
		for (int i = 0; i < 16; i++) {
			float k = i / 15f;
			float bw = bottomW * (1.15f - 0.55f * k);
			g.pose().pushMatrix();
			g.pose().translate(cx + sway * 0.5f, top + beamH / 2);
			g.pose().scale(bw, beamH / 0.85f);
			Shapes.triangle(g, 0, 0, 1, (float) (-Math.PI / 2), Colors.withAlpha(light, 0.011f * a));
			g.pose().popMatrix();
		}
		// The beam fades towards its source.
		Shapes.gradientRect(g, cx - bottomW, 0, bottomW * 2, floorY * 0.55f, 0, Colors.argb(0.85f, BACK_TOP), Colors.argb(0, BACK_TOP));

		// The pool of light on the floor (an ellipse built from soft rings).
		g.pose().pushMatrix();
		g.pose().translate(cx, floorY + h * 0.012f);
		g.pose().scale(1, 0.16f);
		for (int i = 0; i < 18; i++) {
			float r = bottomW * (0.64f - i * 0.032f);
			Shapes.circle(g, 0, 0, r, Colors.withAlpha(light, (0.02f + i * 0.004f) * a));
		}
		// Contact shadow under the feet.
		for (int i = 0; i < 6; i++) {
			Shapes.circle(g, 0, 0, h * (0.075f - i * 0.01f), Colors.argb(0.12f * a, 0x000000));
		}
		g.pose().popMatrix();

		float size = h * 0.235f * (1 + 0.035f * (float) Math.sin(t * 0.11));
		float yaw = 20 + 26 * (float) Math.sin(t * 0.17);
		pickEmote(t);
		drawPlayer(g, cx, floorY, size, yaw, a, true);
		drawPlayer(g, cx, floorY, size, yaw, a, false);

		// The reflection fades into the floor.
		Shapes.gradientRect(g, 0, floorY, w, h * 0.22f, 0, Colors.argb(0.6f, FLOOR), Colors.argb(1, FLOOR));
		Shapes.rect(g, 0, floorY + h * 0.22f, w, h - floorY - h * 0.22f + 1, 0, Colors.argb(1, FLOOR));
		// A thin highlight where the floor meets the light.
		Shapes.gradientRectH(g, cx - bottomW * 0.55f, floorY, bottomW * 0.55f, 1.2f, 0, Colors.withAlpha(light, 0), Colors.withAlpha(light, 0.22f * a));
		Shapes.gradientRectH(g, cx, floorY, bottomW * 0.55f, 1.2f, 0, Colors.withAlpha(light, 0.22f * a), Colors.withAlpha(light, 0));

		// Dust drifting up through the beam.
		for (int i = 0; i < 80; i++) {
			float s1 = hash(i * 1.37f);
			float s2 = hash(i * 7.91f + 3);
			float s3 = hash(i * 3.13f + 11);
			float k = ((t * (0.012f + 0.02f * s1) + s2) % 1f);
			float y = floorY - k * (floorY - top) * 0.98f;
			float along = (y - top) / (floorY - top);
			float bw = topW + (bottomW - topW) * along;
			float x = cx + sway * (1 - along) + (s3 - 0.5f) * bw * 0.95f + (float) Math.sin(t * 0.5f + i) * h * 0.006f;
			float twinkle = 0.55f + 0.45f * (float) Math.sin(t * (1.2f + s1 * 2) + i);
			float edge = 1 - Math.abs(s3 - 0.5f) * 2;
			float alpha = (0.18f + 0.5f * edge) * twinkle * a * Math.min(1, k * 6) * Math.min(1, (1 - k) * 4);
			Shapes.circle(g, x, y, 0.8f + 1.6f * s2, Colors.withAlpha(light, alpha));
		}

		// Darken the far left so the menu reads well, and vignette the corners.
		Shapes.gradientRectH(g, 0, 0, w * 0.5f, h, 0, Colors.argb(0.55f, 0x050507), Colors.argb(0, 0x050507));
		Shapes.gradientRect(g, 0, 0, w, h * 0.2f, 0, Colors.argb(0.5f, 0x050507), Colors.argb(0, 0x050507));
	}

	private static float hash(float n) {
		double s = Math.sin(n * 12.9898) * 43758.5453;
		return (float) (s - Math.floor(s));
	}

	/** Plays an emote on the stand-in player every few seconds. */
	private void pickEmote(float t) {
		if (Minecraft.getInstance().player != null) {
			return;
		}
		if (loopRunning && t - lastEmoteAt > LOOP_FOR) {
			Emotes.stopPreview();
			loopRunning = false;
		}
		if (t - lastEmoteAt > EMOTE_EVERY) {
			lastEmoteAt = t;
			Emotes.Emote emote = SHOW[nextEmote++ % SHOW.length];
			Emotes.playPreview(emote);
			loopRunning = emote.loops();
		}
	}

	/** Your player, standing with their feet at {@code floorY}; {@code mirrored} draws the reflection under the floor. */
	private void drawPlayer(GuiGraphicsExtractor g, float cx, float floorY, float size, float yaw, float a, boolean mirrored) {
		if (skin == null || a < 0.4f) {
			return;
		}
		PlayerSkin current = skin.get();
		AvatarRenderState state = new AvatarRenderState();
		state.entityType = EntityTypes.PLAYER;
		state.id = Cosmetics.PREVIEW_ID;
		state.skin = current;
		state.ageInTicks = (System.nanoTime() % 1_000_000_000_000L) / 1e9f * 20;
		state.boundingBoxWidth = 0.6f;
		state.boundingBoxHeight = 1.8f;
		state.eyeHeight = 1.62f;
		// Upside down mirrors left and right too, so the reflection turns the other way to match.
		state.bodyRot = mirrored ? 180 - yaw : 180 + yaw;
		state.yRot = 0;
		state.xRot = 0;
		state.lightCoords = 0xF000F0;
		Cosmetics cosmetics = Cosmetics.get();
		if (cosmetics.isEnabled()) {
			Capes.apply(cosmetics, state);
		}
		// The entity is drawn centred on its middle (0.9 blocks up), in a box tall enough for flips.
		float mid = mirrored ? floorY + 0.9f * size : floorY - 0.9f * size;
		int x0 = Math.round(cx - size * 1.4f);
		int x1 = Math.round(cx + size * 1.4f);
		int y0 = Math.round(mid - size * 1.7f);
		int y1 = Math.round(mid + size * 1.7f);
		Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(-4));
		Quaternionf rotation = mirrored ? new Quaternionf().rotateX((float) Math.toRadians(4)) : new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
		// The offset is applied on screen (y down) before the turn, so upside down it goes the other way.
		Vector3f translation = new Vector3f(0, mirrored ? -0.9f : 0.9f, 0);
		g.pose().pushMatrix();
		g.pose().identity();
		try {
			g.entity(state, Math.round(size), translation, rotation, mirrored ? null : tilt, x0, y0, x1, y1);
		} catch (RuntimeException ignored) {
			// Skins can still be loading; the stage just stays empty for a moment.
		}
		g.pose().popMatrix();
	}
}
