package dev.mordo.kairo.sound;

import com.mojang.blaze3d.audio.SoundBuffer;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.settings.Choice;
import dev.mordo.kairo.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * All custom sounds: the built-in synthesized ones and the player's own files in {@code config/kairo/sounds}.
 *
 * <p>A sound picker stores a choice id: {@code vanilla}, {@code builtin:<name>} or {@code file:<file name>}.
 * Each loaded sound gets an engine key ({@code kairo:custom/<key>}); {@code SoundBufferLibraryMixin} serves the
 * audio for those keys straight from memory, so no resource pack or reload is needed. Files decode in the
 * background the first time they're needed; until then the vanilla sound keeps playing.
 */
public final class SoundLibrary {
	public static final String VANILLA = "vanilla";
	private static final String NAMESPACE = ClientInfo.MOD_ID;
	private static final String PATH_PREFIX = "sounds/custom/";

	/** engine key -> decoded audio. */
	private static final Map<String, PcmSound> SOUNDS = new ConcurrentHashMap<>();
	/** engine key -> the buffer handed to the sound engine (reused so OpenAL keeps one buffer per sound). */
	private static final Map<String, SoundBuffer> BUFFERS = new ConcurrentHashMap<>();
	/** choice ids currently decoding, or that failed (so we don't retry every play). */
	private static final Map<String, Boolean> PENDING = new ConcurrentHashMap<>();

	private static List<Choice> fileChoices = List.of();
	private static long lastScan;

	private SoundLibrary() {
	}

	public static Path folder() {
		return ClientInfo.configDir("sounds");
	}

	// ---- choices ----

	/** Vanilla, the built-in sounds, then the player's files (rescanned at most every 2 s). */
	public static List<Choice> choices() {
		List<Choice> list = new ArrayList<>();
		list.add(new Choice(VANILLA, "Vanilla", ""));
		for (Map.Entry<String, String> e : Synth.GAME_SOUNDS.entrySet()) {
			list.add(new Choice("builtin:" + e.getKey(), e.getValue(), "BUILT-IN"));
		}
		list.addAll(files());
		return list;
	}

	private static List<Choice> files() {
		long now = System.currentTimeMillis();
		if (now - lastScan > 2000) {
			lastScan = now;
			List<Choice> found = new ArrayList<>();
			Path dir = folder();
			if (Files.isDirectory(dir)) {
				try (Stream<Path> stream = Files.list(dir)) {
					stream.filter(Files::isRegularFile).filter(AudioDecoder::isSupported).sorted()
						.forEach(p -> found.add(new Choice("file:" + p.getFileName(), p.getFileName().toString(), "YOUR FILES")));
				} catch (IOException e) {
					Kairo.LOGGER.warn("Couldn't list {}", dir, e);
				}
			}
			fileChoices = found;
		}
		return fileChoices;
	}

	public static void rescanSoon() {
		lastScan = 0;
	}

	// ---- songs (music emotes) ----

	/** Longest song a music emote plays (it loops). */
	public static final float SONG_SECONDS = 300;

	/** "No song", then the player's files. */
	public static List<Choice> songChoices() {
		List<Choice> list = new ArrayList<>();
		list.add(new Choice("", "No song", ""));
		list.addAll(files());
		return list;
	}

	/**
	 * The engine key for a song file ("file:name"), or null while it's still being read (reading starts in the
	 * background the first time). Songs are read whole, so they're kept apart from the short sounds.
	 */
	public static @Nullable String resolveSong(String choiceId) {
		if (choiceId == null || !choiceId.startsWith("file:")) {
			return null;
		}
		String key = "s" + keyFor(choiceId).substring(1);
		if (SOUNDS.containsKey(key)) {
			return key;
		}
		String pendingId = "song|" + choiceId;
		if (PENDING.putIfAbsent(pendingId, Boolean.TRUE) == null) {
			Path file = folder().resolve(choiceId.substring(5));
			Util.backgroundExecutor().execute(() -> {
				try {
					SOUNDS.put(key, AudioDecoder.decodeSong(file, SONG_SECONDS));
					BUFFERS.remove(key);
					PENDING.remove(pendingId);
				} catch (IOException | RuntimeException e) {
					Kairo.LOGGER.warn("Couldn't load song {}: {}", file, e.getMessage());
					Minecraft.getInstance().execute(() -> Toasts.show("Couldn't load " + file.getFileName() + ": " + e.getMessage(), 0xFFFF7A7A));
				}
			});
		}
		return null;
	}

	// ---- loading ----

	/**
	 * The engine key for a choice, or null if it's vanilla or not loaded yet (loading starts in the background).
	 */
	public static @Nullable String resolve(String choiceId) {
		if (choiceId == null || choiceId.equals(VANILLA)) {
			return null;
		}
		String key = keyFor(choiceId);
		if (key == null) {
			return null;
		}
		if (SOUNDS.containsKey(key)) {
			return key;
		}
		load(choiceId, key);
		return SOUNDS.containsKey(key) ? key : null;
	}

	/** Engine key for a UI sound (always built in). */
	static String uiKey(String name) {
		String key = "b_" + name;
		if (!SOUNDS.containsKey(key)) {
			PcmSound sound = Synth.generate(name);
			if (sound != null) {
				SOUNDS.put(key, sound);
			}
		}
		return key;
	}

	private static @Nullable String keyFor(String choiceId) {
		if (choiceId.startsWith("builtin:")) {
			return "b_" + choiceId.substring(8);
		}
		if (choiceId.startsWith("file:")) {
			String name = choiceId.substring(5);
			// Identifier paths only allow [a-z0-9/._-]; keep it readable and unique.
			String clean = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
			return "f_" + clean + "_" + Integer.toHexString(name.hashCode());
		}
		return null;
	}

	private static void load(String choiceId, String key) {
		if (choiceId.startsWith("builtin:")) {
			PcmSound sound = Synth.generate(choiceId.substring(8));
			if (sound != null) {
				SOUNDS.put(key, sound);
			}
			return;
		}
		if (PENDING.putIfAbsent(choiceId, Boolean.TRUE) != null) {
			return;
		}
		Path file = folder().resolve(choiceId.substring(5));
		Util.backgroundExecutor().execute(() -> {
			try {
				SOUNDS.put(key, AudioDecoder.decode(file));
				BUFFERS.remove(key);
				PENDING.remove(choiceId);
			} catch (IOException | RuntimeException e) {
				Kairo.LOGGER.warn("Couldn't load sound {}: {}", file, e.getMessage());
				Minecraft.getInstance().execute(() -> Toasts.show("Couldn't load " + file.getFileName() + ": " + e.getMessage(), 0xFFFF7A7A));
				// stays in PENDING: don't retry this file until the list is rescanned
			}
		});
	}

	/** Forget a file's decoded audio (it was replaced on disk) and allow retrying failed files. */
	public static void invalidateFiles() {
		SOUNDS.keySet().removeIf(k -> k.startsWith("f_") || k.startsWith("s_"));
		BUFFERS.keySet().removeIf(k -> k.startsWith("f_") || k.startsWith("s_"));
		PENDING.clear();
		rescanSoon();
	}

	// ---- sound engine side ----

	public static Identifier location(String key) {
		return ClientInfo.id("custom/" + key);
	}

	/** Called by SoundBufferLibraryMixin: our audio for a sound path, or null if it isn't one of ours. */
	public static @Nullable SoundBuffer bufferFor(Identifier path) {
		if (!path.getNamespace().equals(NAMESPACE) || !path.getPath().startsWith(PATH_PREFIX)) {
			return null;
		}
		String key = path.getPath().substring(PATH_PREFIX.length());
		if (key.endsWith(".ogg")) {
			key = key.substring(0, key.length() - 4);
		}
		PcmSound sound = SOUNDS.get(key);
		if (sound == null) {
			return null;
		}
		return BUFFERS.computeIfAbsent(key, k -> sound.toBuffer());
	}

	/** The sound engine dropped its OpenAL buffers (reload / device change): ours are gone too. */
	public static void onBuffersCleared() {
		// Same thread and moment vanilla discards its own buffers.
		BUFFERS.values().forEach(SoundBuffer::discardAlBuffer);
		BUFFERS.clear();
	}

	// ---- playing ----

	/** Plays a choice right now, non-positional (settings preview). Vanilla plays {@code vanillaFallback}. */
	public static void preview(String choiceId, @Nullable Identifier vanillaFallback, float volume) {
		Minecraft mc = Minecraft.getInstance();
		if (choiceId.equals(VANILLA)) {
			if (vanillaFallback != null) {
				mc.getSoundManager().play(CustomSoundInstance.vanillaUi(vanillaFallback, volume));
			}
			return;
		}
		String key = resolve(choiceId);
		if (key == null) {
			// Still decoding: try again shortly.
			Util.backgroundExecutor().execute(() -> {
				for (int i = 0; i < 40 && resolve(choiceId) == null && PENDING.containsKey(choiceId); i++) {
					try {
						Thread.sleep(50);
					} catch (InterruptedException e) {
						return;
					}
				}
				String ready = resolve(choiceId);
				if (ready != null) {
					mc.execute(() -> mc.getSoundManager().play(CustomSoundInstance.ui(ready, volume, 1)));
				}
			});
			return;
		}
		mc.getSoundManager().play(CustomSoundInstance.ui(key, volume, 1));
	}

	public static SoundSource uiSource() {
		return SoundSource.UI;
	}
}
