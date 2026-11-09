package dev.mordo.kairo.cosmetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Emotes for your own player: limb poses (applied after the normal animation) and whole-body moves (flips, spins,
 * sitting, lying down). Moving, jumping, attacking or using an item stops an emote. Only you see them.
 *
 * <p>They come in four pages (dances, reactions, poses, moves). Your favourites (up to {@link #MAX_FAVOURITES}) get
 * their own page in the emote wheel.
 */
public final class Emotes {
	public static final int MAX_FAVOURITES = 12;

	/** The pages emotes are sorted into. */
	public enum Kind {
		DANCES("Dances"),
		REACTIONS("Reactions"),
		POSES("Poses"),
		MOVES("Moves"),
		MUSIC("Music");

		private final String label;

		Kind(String label) {
			this.label = label;
		}

		public String label() {
			return label;
		}
	}

	public enum Emote {
		// The first fifteen keep their old names, so saved favourites and tests still match.
		WAVE("Wave", 2.4f, false, Icon.USER, Kind.REACTIONS),
		BACKFLIP("Backflip", 1.0f, false, Icon.REFRESH, Kind.MOVES),
		SIT("Sit", 0, true, Icon.BOX, Kind.POSES),
		LIE_DOWN("Lie down", 0, true, Icon.SUN, Kind.POSES),
		DAB("Dab", 1.6f, false, Icon.SPARKLE, Kind.POSES),
		SPIN("Spin", 1.2f, false, Icon.REFRESH, Kind.MOVES),
		CLAP("Clap", 0, true, Icon.VOLUME, Kind.REACTIONS),
		DANCE("Dance", 0, true, Icon.NOTE, Kind.DANCES),
		CHEER("Cheer", 0, true, Icon.SPARKLE, Kind.REACTIONS),
		SALUTE("Salute", 2.0f, false, Icon.CHECK, Kind.REACTIONS),
		BOW("Bow", 2.0f, false, Icon.HEART, Kind.REACTIONS),
		T_POSE("T-pose", 0, true, Icon.PLUS, Kind.POSES),
		FACEPALM("Facepalm", 2.2f, false, Icon.EYE_OFF, Kind.REACTIONS),
		SHRUG("Shrug", 1.6f, false, Icon.INFO, Kind.REACTIONS),
		ZOMBIE("Zombie", 0, true, Icon.MOUSE, Kind.DANCES),
		// Dances
		FLOSS("Floss", 0, true, Icon.NOTE, Kind.DANCES),
		DISCO("Disco", 0, true, Icon.SUN, Kind.DANCES),
		ROBOT("Robot", 0, true, Icon.GEAR, Kind.DANCES),
		HEADBANG("Headbang", 0, true, Icon.VOLUME, Kind.DANCES),
		SHUFFLE("Shuffle", 0, true, Icon.ARROW_RIGHT, Kind.DANCES),
		WIGGLE("Wiggle", 0, true, Icon.SPARKLE, Kind.DANCES),
		// Reactions
		LAUGH("Laugh", 2.4f, false, Icon.CHAT, Kind.REACTIONS),
		CRY("Cry", 2.6f, false, Icon.EYE, Kind.REACTIONS),
		POINT("Point", 1.8f, false, Icon.ARROW_RIGHT, Kind.REACTIONS),
		THINK("Think", 2.6f, false, Icon.SEARCH, Kind.REACTIONS),
		ANGRY("Angry", 2.0f, false, Icon.FLAME, Kind.REACTIONS),
		EXCITED("Excited", 2.0f, false, Icon.SPARKLE, Kind.REACTIONS),
		// Poses
		MEDITATE("Meditate", 0, true, Icon.GLOBE, Kind.POSES),
		HERO("Hero pose", 0, true, Icon.SHIELD, Kind.POSES),
		KNEEL("Kneel", 0, true, Icon.TOTEM, Kind.POSES),
		FLEX("Flex", 0, true, Icon.POWER, Kind.POSES),
		VICTORY("Victory", 2.2f, false, Icon.TOTEM, Kind.POSES),
		LOUNGE("Lounge", 0, true, Icon.SUN, Kind.POSES),
		// Moves
		FRONTFLIP("Front flip", 1.0f, false, Icon.REFRESH, Kind.MOVES),
		CARTWHEEL("Cartwheel", 1.2f, false, Icon.REFRESH, Kind.MOVES),
		JUMP_FOR_JOY("Jump for joy", 1.4f, false, Icon.HEART, Kind.MOVES),
		LEVITATE("Levitate", 0, true, Icon.SPARKLE, Kind.MOVES),
		// Made for music: they move on the beat
		BEAT_BOUNCE("Beat bounce", 0, true, Icon.NOTE, Kind.DANCES),
		BEAT_STEP("Beat step", 0, true, Icon.NOTE, Kind.DANCES),
		// Music emotes: your own song, danced to (names and songs in the wardrobe's emote options)
		MUSIC_1("Music 1", 0, true, Icon.VOLUME, Kind.MUSIC),
		MUSIC_2("Music 2", 0, true, Icon.VOLUME, Kind.MUSIC);

		private final String label;
		private final float duration;
		private final boolean loop;
		private final Icon icon;
		private final Kind kind;

		Emote(String label, float duration, boolean loop, Icon icon, Kind kind) {
			this.label = label;
			this.duration = duration;
			this.loop = loop;
			this.icon = icon;
			this.kind = kind;
		}

		public String label() {
			return MusicEmotes.isMusic(this) ? MusicEmotes.name(this, label) : label;
		}

		public Icon icon() {
			return icon;
		}

		public boolean loops() {
			return loop;
		}

		public Kind kind() {
			return kind;
		}
	}

	/** Every emote. */
	public static Emote[] available() {
		return Emote.values();
	}

	/** The emotes on one page, in order. */
	public static List<Emote> of(Kind kind) {
		List<Emote> list = new ArrayList<>();
		for (Emote e : Emote.values()) {
			if (e.kind == kind) {
				list.add(e);
			}
		}
		return list;
	}

	// ---- favourites ----

	/** Your favourites, in the order you added them (unknown names are skipped). */
	public static List<Emote> favourites() {
		List<Emote> list = new ArrayList<>();
		for (String name : Cosmetics.get().emoteFavourites.get().split(",")) {
			try {
				Emote e = Emote.valueOf(name.trim().toUpperCase(Locale.ROOT));
				if (!list.contains(e)) {
					list.add(e);
				}
			} catch (IllegalArgumentException ignored) {
				// empty or a removed emote
			}
		}
		return list;
	}

	public static boolean isFavourite(Emote emote) {
		return favourites().contains(emote);
	}

	/** Adds or removes a favourite. Returns false when the list is full. */
	public static boolean toggleFavourite(Emote emote) {
		List<Emote> list = favourites();
		if (list.contains(emote)) {
			list.remove(emote);
		} else {
			if (list.size() >= MAX_FAVOURITES) {
				return false;
			}
			list.add(emote);
		}
		StringBuilder sb = new StringBuilder();
		for (Emote e : list) {
			if (!sb.isEmpty()) {
				sb.append(',');
			}
			sb.append(e.name());
		}
		Cosmetics.get().emoteFavourites.set(sb.toString());
		return true;
	}

	// ---- playing ----

	private static @Nullable Emote current;
	private static long startNanos;
	private static @Nullable CameraType savedCamera;
	private static boolean preview;
	private static int ticks;

	private Emotes() {
	}

	public static @Nullable Emote current() {
		return current;
	}

	public static void play(Emote emote) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			return;
		}
		start(emote);
		Cosmetics.EmoteCamera camera = Cosmetics.get().emoteCamera.get();
		if (camera != Cosmetics.EmoteCamera.OFF && mc.options.getCameraType().isFirstPerson()) {
			savedCamera = mc.options.getCameraType();
			mc.options.setCameraType(camera == Cosmetics.EmoteCamera.FRONT ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
		}
	}

	/** Plays without touching the camera (the wardrobe preview shows it). */
	public static void playHere(Emote emote) {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		start(emote);
	}

	private static void start(Emote emote) {
		current = emote;
		startNanos = System.nanoTime();
		ticks = 0;
		burst();
		MusicEmotes.start(emote);
	}

	/** The beat starts now (a music emote's song just started playing). */
	static void restartClock() {
		startNanos = System.nanoTime();
	}

	/** The dance actually shown: a music emote does the dance chosen for it. */
	private static Emote shown() {
		return MusicEmotes.isMusic(current) ? MusicEmotes.dance(current) : current;
	}

	/** Plays on the stand-in player of a menu (no world needed). Joining a world stops it. */
	public static void playPreview(Emote emote) {
		current = emote;
		startNanos = System.nanoTime();
		preview = true;
	}

	public static void stopPreview() {
		if (preview) {
			current = null;
			preview = false;
		}
	}

	public static void stop() {
		preview = false;
		current = null;
		MusicEmotes.stop();
		if (savedCamera != null) {
			Minecraft.getInstance().options.setCameraType(savedCamera);
			savedCamera = null;
		}
	}

	/**
	 * Seconds since the emote started. For a music emote the clock runs at the song's tempo (the dances are made for
	 * 120 BPM), shifted by its beat offset.
	 */
	private static float elapsed() {
		float seconds = (System.nanoTime() - startNanos) / 1e9f;
		if (MusicEmotes.isMusic(current)) {
			return Math.max(0, (seconds + MusicEmotes.offset(current)) * MusicEmotes.bpm(current) / 120f);
		}
		return seconds;
	}

	/** A small burst of light in your colour round your feet as an emote starts (Cosmetics → Emotes). */
	private static void burst() {
		Minecraft mc = Minecraft.getInstance();
		LocalPlayer player = mc.player;
		ClientLevel level = mc.level;
		if (player == null || level == null || !Cosmetics.get().emoteBurst.isOn() || !dev.mordo.kairo.render.Visuals.live()) {
			return;
		}
		DustParticleOptions dust = new DustParticleOptions(Theme.accent() & 0xFFFFFF, 1.1f);
		for (int i = 0; i < 18; i++) {
			double a = i * Math.PI * 2 / 18;
			double cos = Math.cos(a);
			double sin = Math.sin(a);
			dev.mordo.kairo.render.LightParticles.add(level, i % 3 == 0 ? ParticleTypes.END_ROD : dust, player.getX() + cos * 0.45, player.getY() + 0.1, player.getZ() + sin * 0.45,
				cos * 0.09, 0.02, sin * 0.09);
		}
	}

	/** Every client tick: ends finished emotes, and any emote once you move. */
	static void tick() {
		MusicEmotes.tick();
		if (current == null) {
			return;
		}
		Minecraft mc = Minecraft.getInstance();
		if (preview) {
			// A menu's stand-in: ends on its own, or when a world loads.
			if (mc.player != null || (!current.loop && elapsed() > current.duration)) {
				current = null;
				preview = false;
			}
			return;
		}
		if (mc.player == null) {
			current = null;
			savedCamera = null;
			return;
		}
		Options o = mc.options;
		boolean input = mc.gui.screen() == null && (o.keyUp.isDown() || o.keyDown.isDown() || o.keyLeft.isDown() || o.keyRight.isDown()
			|| o.keyJump.isDown() || o.keyAttack.isDown() || o.keyUse.isDown());
		if (input || (!current.loop && elapsed() > current.duration) || mc.player.hurtTime > 0) {
			stop();
			return;
		}
		ticks++;
		// A little light under you while you float.
		if ((current == Emote.LEVITATE || current == Emote.MEDITATE) && ticks % 3 == 0 && mc.level != null && dev.mordo.kairo.render.Visuals.live()) {
			double a = ticks * 0.4;
			dev.mordo.kairo.render.LightParticles.add(mc.level, new DustParticleOptions(Theme.accent() & 0xFFFFFF, 0.8f), mc.player.getX() + Math.cos(a) * 0.4,
				mc.player.getY() + (current == Emote.LEVITATE ? 0.45 : 0.15), mc.player.getZ() + Math.sin(a) * 0.4, 0, -0.01, 0);
		}
	}

	private static boolean active(AvatarRenderState state) {
		return current != null && Cosmetics.isLocal(state);
	}

	/** 0..1: blends in over the first moment and, for one-shot emotes, out at the end. */
	private static float envelope(float t) {
		float in = Math.min(1, t / 0.22f);
		if (current != null && !current.loop) {
			in = Math.min(in, Math.clamp((current.duration - t) / 0.3f, 0, 1));
		}
		return in * in * (3 - 2 * in);
	}

	private static void pose(ModelPart part, float x, float y, float z, float k) {
		part.xRot += (x - part.xRot) * k;
		part.yRot += (y - part.yRot) * k;
		part.zRot += (z - part.zRot) * k;
	}

	private static float sin(double v) {
		return (float) Math.sin(v);
	}

	/** Limb poses, after the model's own animation. */
	public static void applyModel(PlayerModel m, AvatarRenderState state) {
		if (!active(state)) {
			return;
		}
		float t = elapsed();
		float k = envelope(t);
		float pi = (float) Math.PI;
		switch (shown()) {
			case WAVE -> {
				pose(m.rightArm, 0, 0, 2.6f + 0.35f * sin(t * 9), k);
				pose(m.head, 0, -0.15f, 0.08f, k);
			}
			case BACKFLIP, FRONTFLIP -> {
				float tuck = sin(Math.clamp(t / 1.0f, 0, 1) * pi);
				pose(m.rightArm, -2.6f * tuck, 0, 0.3f, k);
				pose(m.leftArm, -2.6f * tuck, 0, -0.3f, k);
				pose(m.rightLeg, -1.2f * tuck, 0, 0, k);
				pose(m.leftLeg, -1.2f * tuck, 0, 0, k);
			}
			case SIT -> {
				pose(m.rightLeg, -pi / 2, 0.15f, 0, k);
				pose(m.leftLeg, -pi / 2, -0.15f, 0, k);
				pose(m.rightArm, -0.55f, 0, 0.1f, k);
				pose(m.leftArm, -0.55f, 0, -0.1f, k);
			}
			case LIE_DOWN -> {
				pose(m.rightArm, 0, 0, 0.35f, k);
				pose(m.leftArm, 0, 0, -0.35f, k);
				pose(m.rightLeg, 0, 0, 0.08f, k);
				pose(m.leftLeg, 0, 0, -0.08f, k);
				pose(m.head, -0.2f, 0, 0, k);
			}
			case DAB -> {
				pose(m.head, 0.75f, -0.55f, 0, k);
				pose(m.rightArm, -1.9f, -0.95f, 0, k);
				pose(m.leftArm, -0.3f, 0, -2.3f, k);
			}
			case SPIN -> {
				pose(m.rightArm, 0, 0, 1.3f, k);
				pose(m.leftArm, 0, 0, -1.3f, k);
			}
			case CLAP -> {
				float clap = sin(t * 12) * 0.5f + 0.5f;
				pose(m.rightArm, -1.25f, -(0.15f + 0.4f * clap), 0, k);
				pose(m.leftArm, -1.25f, 0.15f + 0.4f * clap, 0, k);
			}
			case BEAT_BOUNCE -> {
				// Fist pumps on every beat, alternating arms, a nod and a little knee bend with each one.
				float beat = t * 2;
				float on = 1 - Math.abs(sin(beat * pi));
				boolean right = ((int) Math.floor(beat + 0.5f)) % 2 == 0;
				pose(m.rightArm, -2.5f + (right ? 0.7f * on : 0.1f), 0, 0.2f, k);
				pose(m.leftArm, -2.5f + (right ? 0.1f : 0.7f * on), 0, -0.2f, k);
				pose(m.head, 0.32f * on, 0, 0, k);
				pose(m.rightLeg, -0.22f * on, 0, 0.04f, k);
				pose(m.leftLeg, -0.22f * on, 0, -0.04f, k);
			}
			case BEAT_STEP -> {
				// A step to the side on each beat, arms swinging out the other way, the head tipping with it.
				float beat = t * 2;
				float sway = sin(beat * pi / 2);
				float on = 1 - Math.abs(sin(beat * pi));
				pose(m.rightArm, -0.35f - 0.45f * Math.max(0, sway), 0, 0.25f + 0.55f * Math.max(0, -sway), k);
				pose(m.leftArm, -0.35f - 0.45f * Math.max(0, -sway), 0, -0.25f - 0.55f * Math.max(0, sway), k);
				pose(m.rightLeg, 0, 0, 0.28f * Math.max(0, sway), k);
				pose(m.leftLeg, 0, 0, -0.28f * Math.max(0, -sway), k);
				pose(m.head, 0.14f * on, 0, -0.14f * sway, k);
			}
			case DANCE -> {
				float s = sin(t * 8);
				pose(m.rightArm, 0, 0, 2.4f + 0.4f * s, k);
				pose(m.leftArm, 0, 0, -(2.4f - 0.4f * s), k);
				pose(m.rightLeg, 0.35f * s, 0, 0, k);
				pose(m.leftLeg, -0.35f * s, 0, 0, k);
				pose(m.head, 0, 0, 0.15f * s, k);
			}
			case CHEER -> {
				float s = sin(t * 10);
				pose(m.rightArm, 0, 0, 2.8f + 0.2f * s, k);
				pose(m.leftArm, 0, 0, -2.8f - 0.2f * s, k);
				pose(m.head, -0.25f, 0, 0, k);
			}
			case SALUTE -> {
				pose(m.rightArm, -2.3f, -0.6f, 0, k);
				pose(m.head, -0.05f, 0, 0, k);
			}
			case BOW -> {
				pose(m.rightArm, -0.9f, -0.6f, 0, k);
				pose(m.leftArm, 0.25f, 0, -0.1f, k);
			}
			case T_POSE -> {
				pose(m.rightArm, 0, 0, pi / 2, k);
				pose(m.leftArm, 0, 0, -pi / 2, k);
			}
			case FACEPALM -> {
				pose(m.rightArm, -2.05f, -0.7f, 0, k);
				pose(m.head, 0.45f, 0, 0, k);
			}
			case SHRUG -> {
				pose(m.rightArm, -0.55f, 0, 0.75f, k);
				pose(m.leftArm, -0.55f, 0, -0.75f, k);
				pose(m.head, 0, 0, 0.22f, k);
			}
			case ZOMBIE -> {
				float s = sin(t * 5);
				pose(m.rightArm, -pi / 2 + 0.1f * s, 0, 0, k);
				pose(m.leftArm, -pi / 2 - 0.1f * s, 0, 0, k);
				pose(m.rightLeg, 0.45f * s, 0, 0, k);
				pose(m.leftLeg, -0.45f * s, 0, 0, k);
				pose(m.head, 0.15f, 0, 0.1f * s, k);
			}
			// ---- dances ----
			case FLOSS -> {
				// Both arms swing to one side while the hips go the other, front and back in turn.
				float s = sin(t * 7);
				float front = Math.signum(sin(t * 7 + pi / 2));
				pose(m.rightArm, 0.45f * front, 0, 0.35f + 0.55f * s, k);
				pose(m.leftArm, -0.45f * front, 0, -0.35f + 0.55f * s, k);
				pose(m.head, 0, 0, -0.1f * s, k);
			}
			case DISCO -> {
				float p = sin(t * 5) * 0.5f + 0.5f;
				pose(m.rightArm, -0.3f - 0.4f * (1 - p), -0.2f, 2.6f * p - 0.5f * (1 - p), k);
				pose(m.leftArm, 0.2f, 0, -0.65f, k);
				pose(m.head, 0, 0.25f * (p - 0.5f), 0.12f * (p - 0.5f), k);
				pose(m.rightLeg, -0.2f * p, 0, 0.1f, k);
			}
			case ROBOT -> {
				// Jerky steps: every quarter second a new pose.
				int step = (int) Math.floor(t * 4) % 4;
				float a = step == 0 ? 0.5f : step == 2 ? -0.5f : 0;
				pose(m.rightArm, -pi / 2 + a, 0, 0, k);
				pose(m.leftArm, -pi / 2 - a, 0, 0, k);
				pose(m.head, 0, step == 1 ? 0.5f : step == 3 ? -0.5f : 0, 0, k);
			}
			case HEADBANG -> {
				float s = Math.abs(sin(t * 9));
				pose(m.head, 0.15f + 0.6f * s, 0, 0, k);
				pose(m.rightArm, -0.2f, 0, 2.75f, k);
				pose(m.leftArm, -0.6f - 0.3f * s, 0.3f, 0, k);
			}
			case SHUFFLE -> {
				float s = sin(t * 10);
				pose(m.rightLeg, 0.55f * s, 0, 0.05f, k);
				pose(m.leftLeg, -0.55f * s, 0, -0.05f, k);
				pose(m.rightArm, -0.4f * s, 0, 0.15f, k);
				pose(m.leftArm, 0.4f * s, 0, -0.15f, k);
			}
			case WIGGLE -> {
				float s = sin(t * 6);
				pose(m.rightArm, -0.2f, 0, 0.6f + 0.25f * s, k);
				pose(m.leftArm, -0.2f, 0, -0.6f + 0.25f * s, k);
				pose(m.head, 0, 0, -0.25f * s, k);
			}
			// ---- reactions ----
			case LAUGH -> {
				pose(m.head, -0.4f + 0.08f * sin(t * 20), 0, 0, k);
				pose(m.rightArm, -0.75f, -0.45f, 0, k);
				pose(m.leftArm, -0.75f, 0.45f, 0, k);
			}
			case CRY -> {
				float s = sin(t * 14) * 0.05f;
				pose(m.rightArm, -2.15f + s, -0.5f, 0, k);
				pose(m.leftArm, -2.15f - s, 0.5f, 0, k);
				pose(m.head, 0.45f, 0, 0, k);
			}
			case POINT -> {
				pose(m.rightArm, -pi / 2, -0.08f, 0, k);
				pose(m.leftArm, 0.15f, 0, -0.55f, k);
				pose(m.head, 0, -0.05f, 0, k);
			}
			case THINK -> {
				pose(m.rightArm, -2.0f, -0.6f, 0, k);
				pose(m.leftArm, -0.8f, 0.6f, 0, k);
				pose(m.head, 0.15f + 0.04f * sin(t * 2), 0, 0.14f, k);
			}
			case ANGRY -> {
				float s = sin(t * 11);
				pose(m.rightArm, 0.1f, 0, 0.28f, k);
				pose(m.leftArm, 0.1f, 0, -0.28f, k);
				pose(m.head, 0.3f, 0, 0.05f * s, k);
				pose(m.rightLeg, Math.max(0, s) * -0.45f, 0, 0, k);
				pose(m.leftLeg, Math.max(0, -s) * -0.45f, 0, 0, k);
			}
			case EXCITED -> {
				float s = sin(t * 12);
				pose(m.rightArm, 0, 0, 2.7f + 0.35f * s, k);
				pose(m.leftArm, 0, 0, -2.7f + 0.35f * s, k);
				pose(m.head, -0.2f, 0, 0.1f * s, k);
			}
			// ---- poses ----
			case MEDITATE -> {
				pose(m.rightLeg, -1.45f, 0.9f, 0, k);
				pose(m.leftLeg, -1.45f, -0.9f, 0, k);
				pose(m.rightArm, -0.55f, 0, 0.35f, k);
				pose(m.leftArm, -0.55f, 0, -0.35f, k);
				pose(m.head, 0.1f, 0, 0, k);
			}
			case HERO -> {
				pose(m.rightArm, 0.15f, 0, 0.6f, k);
				pose(m.leftArm, 0.15f, 0, -0.6f, k);
				pose(m.rightLeg, 0, 0, 0.16f, k);
				pose(m.leftLeg, 0, 0, -0.16f, k);
				pose(m.head, -0.18f, 0, 0, k);
			}
			case KNEEL -> {
				pose(m.rightLeg, -1.35f, 0, 0.05f, k);
				pose(m.leftLeg, 0.8f, 0, -0.05f, k);
				pose(m.rightArm, -0.9f, 0, 0.1f, k);
				pose(m.leftArm, 0, 0, -0.1f, k);
				pose(m.head, 0.35f, 0, 0, k);
			}
			case FLEX -> {
				float s = sin(t * 4) * 0.08f;
				pose(m.rightArm, -0.25f, 0, 1.95f + s, k);
				pose(m.leftArm, -0.25f, 0, -1.95f - s, k);
				pose(m.head, -0.15f, 0, 0, k);
				pose(m.rightLeg, 0, 0, 0.12f, k);
				pose(m.leftLeg, 0, 0, -0.12f, k);
			}
			case VICTORY -> {
				pose(m.rightArm, -0.25f * Math.abs(sin(t * 6)), 0, 3.0f, k);
				pose(m.leftArm, 0.1f, 0, -0.3f, k);
				pose(m.head, -0.3f, 0, 0, k);
			}
			case LOUNGE -> {
				pose(m.rightLeg, -pi / 2, 0.12f, 0, k);
				pose(m.leftLeg, -1.25f, -0.2f, 0, k);
				pose(m.rightArm, 0.75f, 0, 0.3f, k);
				pose(m.leftArm, 0.75f, 0, -0.3f, k);
				pose(m.head, -0.25f, 0.2f, 0, k);
			}
			// ---- moves ----
			case CARTWHEEL -> {
				pose(m.rightArm, 0, 0, 2.8f, k);
				pose(m.leftArm, 0, 0, -2.8f, k);
				pose(m.rightLeg, 0, 0, 0.5f, k);
				pose(m.leftLeg, 0, 0, -0.5f, k);
			}
			case JUMP_FOR_JOY -> {
				float air = Math.abs(sin(Math.clamp(t / 1.4f, 0, 1) * pi * 2));
				pose(m.rightArm, 0, 0, 1.2f + 1.6f * air, k);
				pose(m.leftArm, 0, 0, -1.2f - 1.6f * air, k);
				pose(m.rightLeg, 0.6f * air, 0, 0.1f, k);
				pose(m.leftLeg, 0.6f * air, 0, -0.1f, k);
				pose(m.head, -0.25f * air, 0, 0, k);
			}
			case LEVITATE -> {
				float s = sin(t * 1.8f);
				pose(m.rightArm, -0.2f, 0, 1.1f + 0.12f * s, k);
				pose(m.leftArm, -0.2f, 0, -1.1f - 0.12f * s, k);
				pose(m.rightLeg, 0.2f, 0, 0.06f, k);
				pose(m.leftLeg, 0.25f, 0, -0.06f, k);
				pose(m.head, -0.2f, 0, 0, k);
			}
		}
	}

	/** Whole-body moves, in the renderer's rotation space (y up, blocks, the front is -z). */
	public static void applyBody(AvatarRenderState state, PoseStack ps) {
		if (!active(state)) {
			return;
		}
		float t = elapsed();
		float k = envelope(t);
		switch (shown()) {
			case BACKFLIP, FRONTFLIP -> {
				float p = Math.clamp(t / 1.0f, 0, 1);
				float eased = p * p * (3 - 2 * p);
				float dir = current == Emote.BACKFLIP ? 1 : -1;
				ps.translate(0, Math.sin(p * Math.PI) * 1.0, 0);
				ps.translate(0, 0.9, 0);
				ps.rotate(Axis.XP, (float) (dir * eased * Math.PI * 2));
				ps.translate(0, -0.9, 0);
			}
			case SPIN -> {
				float p = Math.clamp(t / 1.2f, 0, 1);
				ps.rotate(Axis.YP, ((float) (p * p * (3 - 2 * p) * Math.PI * 4)));
			}
			case SIT -> ps.translate(0, -0.62 * k, 0);
			case LIE_DOWN -> {
				ps.translate(0, 0.22 * k, 0);
				ps.rotate(Axis.XP, ((float) (Math.PI / 2 * k)));
			}
			case BOW -> {
				ps.translate(0, 0.75, 0);
				ps.rotate(Axis.XP, (-0.7f * k));
				ps.translate(0, -0.75, 0);
			}
			case BEAT_BOUNCE -> {
				// Down on every beat, and a quarter turn back and forth every four.
				double beat = t * 2;
				ps.translate(0, -(1 - Math.abs(Math.sin(beat * Math.PI))) * 0.09 * k, 0);
				ps.rotate(Axis.YP, (float) Math.sin(beat * Math.PI / 4) * 0.3f * k);
			}
			case BEAT_STEP -> {
				double beat = t * 2;
				ps.translate(Math.sin(beat * Math.PI / 2) * 0.17 * k, Math.abs(Math.sin(beat * Math.PI)) * 0.04 * k, 0);
				ps.rotate(Axis.YP, (float) Math.sin(beat * Math.PI / 2) * 0.22f * k);
			}
			case DANCE -> {
				ps.translate(0, Math.abs(Math.sin(t * 8)) * 0.07 * k, 0);
				ps.rotate(Axis.YP, ((float) Math.sin(t * 4) * 0.25f * k));
			}
			case CHEER -> ps.translate(0, Math.abs(Math.sin(t * 5)) * 0.18 * k, 0);
			case FLOSS -> {
				float s = sin(t * 7);
				ps.translate(-0.07 * s * k, 0, 0);
				ps.translate(0, 0.75, 0);
				ps.rotate(Axis.ZP, -0.08f * s * k);
				ps.translate(0, -0.75, 0);
			}
			case DISCO -> ps.translate(0, Math.abs(Math.sin(t * 5)) * 0.05 * k, 0);
			case ROBOT -> {
				int step = (int) Math.floor(t * 2) % 4;
				ps.rotate(Axis.YP, (step == 1 ? 0.35f : step == 3 ? -0.35f : 0) * k);
			}
			case HEADBANG -> {
				ps.translate(0, 0.75, 0);
				ps.rotate(Axis.XP, -0.12f * Math.abs(sin(t * 9)) * k);
				ps.translate(0, -0.75, 0);
			}
			case SHUFFLE -> {
				ps.translate(Math.sin(t * 5) * 0.12 * k, Math.abs(Math.sin(t * 10)) * 0.04 * k, 0);
				ps.rotate(Axis.YP, sin(t * 5) * 0.2f * k);
			}
			case WIGGLE -> {
				ps.translate(0, 0.75, 0);
				ps.rotate(Axis.ZP, sin(t * 6) * 0.15f * k);
				ps.translate(0, -0.75, 0);
			}
			case LAUGH -> {
				ps.translate(0, Math.abs(Math.sin(t * 20)) * 0.025 * k, 0);
				ps.translate(0, 0.75, 0);
				ps.rotate(Axis.XP, 0.12f * k);
				ps.translate(0, -0.75, 0);
			}
			case CRY -> ps.translate(Math.sin(t * 14) * 0.012 * k, 0, 0);
			case ANGRY -> ps.translate(0, Math.abs(Math.sin(t * 11)) * 0.05 * k, 0);
			case EXCITED -> ps.translate(0, Math.abs(Math.sin(t * 8)) * 0.16 * k, 0);
			case MEDITATE -> ps.translate(0, (-0.5 + 0.07 * Math.sin(t * 1.6)) * k, 0);
			case KNEEL -> ps.translate(0, -0.33 * k, 0);
			case VICTORY -> {
				float p = Math.clamp(t / 0.6f, 0, 1);
				ps.translate(0, Math.sin(p * Math.PI) * 0.3 * k, 0);
			}
			case LOUNGE -> {
				ps.translate(0, -0.62 * k, 0);
				ps.translate(0, 0.75, 0);
				ps.rotate(Axis.XP, 0.4f * k);
				ps.translate(0, -0.75, 0);
			}
			case CARTWHEEL -> {
				float p = Math.clamp(t / 1.2f, 0, 1);
				float eased = p * p * (3 - 2 * p);
				ps.translate(Math.sin(p * Math.PI * 2) * 0.25, Math.sin(p * Math.PI) * 0.25, 0);
				ps.translate(0, 0.9, 0);
				ps.rotate(Axis.ZP, (float) (eased * Math.PI * 2));
				ps.translate(0, -0.9, 0);
			}
			case JUMP_FOR_JOY -> {
				float p = Math.clamp(t / 1.4f, 0, 1);
				ps.translate(0, Math.abs(Math.sin(p * Math.PI * 2)) * 0.6, 0);
			}
			case LEVITATE -> ps.translate(0, (0.45 + 0.08 * Math.sin(t * 1.8)) * k, 0);
			default -> {
			}
		}
	}
}
