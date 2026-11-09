package dev.mordo.kairo.cosmetics;

import dev.mordo.kairo.sound.CustomSoundInstance;
import dev.mordo.kairo.sound.SoundLibrary;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Music emotes: your own song plays (only for you, on the Jukebox/Note Blocks volume) while you dance to it. The song
 * is read the first time it's needed; the dance starts straight away and lines up with the beat again the moment the
 * song actually starts. The dance's speed follows the BPM you set, nudged by the beat offset.
 */
final class MusicEmotes {
	private static @Nullable CustomSoundInstance playing;
	private static Emotes.@Nullable Emote playingFor;
	private static boolean waiting;

	private MusicEmotes() {
	}

	static boolean isMusic(Emotes.@Nullable Emote emote) {
		return emote == Emotes.Emote.MUSIC_1 || emote == Emotes.Emote.MUSIC_2;
	}

	private static boolean first(Emotes.Emote emote) {
		return emote == Emotes.Emote.MUSIC_1;
	}

	/** The dance a music emote does. */
	static Emotes.Emote dance(Emotes.Emote emote) {
		Cosmetics c = Cosmetics.get();
		return (first(emote) ? c.music1Dance : c.music2Dance).get().emote;
	}

	static float bpm(Emotes.Emote emote) {
		Cosmetics c = Cosmetics.get();
		return (first(emote) ? c.music1Bpm : c.music2Bpm).floatValue();
	}

	/** Beat offset in seconds. */
	static float offset(Emotes.Emote emote) {
		Cosmetics c = Cosmetics.get();
		return (first(emote) ? c.music1Offset : c.music2Offset).floatValue() / 1000f;
	}

	static String name(Emotes.Emote emote, String fallback) {
		Cosmetics c = Cosmetics.get();
		String name = (first(emote) ? c.music1Name : c.music2Name).get().strip();
		return name.isEmpty() ? fallback : name;
	}

	private static String song(Emotes.Emote emote) {
		Cosmetics c = Cosmetics.get();
		return (first(emote) ? c.music1 : c.music2).get();
	}

	/** An emote started. */
	static void start(Emotes.Emote emote) {
		stop();
		if (!isMusic(emote)) {
			return;
		}
		playingFor = emote;
		waiting = !song(emote).isEmpty();
		tryPlay();
	}

	/** Every client tick: stops the song once its emote ends, starts it once it's read. */
	static void tick() {
		if (playingFor != null && Emotes.current() != playingFor) {
			stop();
			return;
		}
		if (waiting) {
			tryPlay();
		}
	}

	private static void tryPlay() {
		Emotes.Emote emote = playingFor;
		if (emote == null) {
			return;
		}
		String key = SoundLibrary.resolveSong(song(emote));
		if (key == null) {
			return; // still reading it
		}
		waiting = false;
		playing = CustomSoundInstance.song(key, Cosmetics.get().musicVolume.floatValue());
		Minecraft.getInstance().getSoundManager().play(playing);
		Emotes.restartClock(); // the first beat is now
	}

	static void stop() {
		if (playing != null) {
			Minecraft.getInstance().getSoundManager().stop(playing);
		}
		playing = null;
		playingFor = null;
		waiting = false;
	}
}
