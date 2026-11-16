package dev.mordo.kairo.sound;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.hud.NowPlayingHud;
import dev.mordo.kairo.hud.VisualizerHud;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

/**
 * The bridge to what your PC is playing, for the Now Playing and Music Visualiser mods (Windows only).
 *
 * <p>Minecraft can't see other programs' music, so while one of those two mods is switched on (and only then) Kairo
 * runs a small helper: a PowerShell script that ships inside the mod. It asks Windows' own media controls what is
 * playing (the same thing the volume pop-up shows: title, artist, app, position, cover) and measures how loud each
 * band of the PC's sound is. It prints both for Kairo to read. Nothing is recorded, nothing leaves the PC, and the
 * helper is stopped as soon as both mods are off or the game closes.
 */
public final class MediaHelper {
	/** What is playing right now, as the helper last reported it. */
	public record Track(boolean known, boolean playing, long positionMs, long lengthMs, String app, String title, String artist, int cover,
						long receivedNanos) {
		/** The position now: carried on from the last report while it plays. */
		public long positionNow() {
			long position = positionMs + (playing ? (System.nanoTime() - receivedNanos) / 1_000_000L : 0);
			return lengthMs > 0 ? Math.min(position, lengthMs) : position;
		}
	}

	public static final int BANDS = 32;
	private static final Track NONE = new Track(false, false, 0, 0, "", "", "", 0, 0);
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	/** Game tests never start the helper, unless one asks for the real thing (kairo.media.inTests). */
	private static boolean testing() {
		return System.getProperty("fabric.client.gametest") != null && !Boolean.getBoolean("kairo.media.inTests");
	}
	private static final Identifier COVER_ID = ClientInfo.id("now_playing_cover");

	private static volatile Track track = NONE;
	private static volatile float[] bands = new float[BANDS];
	private static volatile long bandsAt;
	private static @Nullable Process process;
	private static boolean runningInfo;
	private static boolean runningAudio;
	private static long retryAt;
	private static boolean hookAdded;

	private static @Nullable DynamicTexture coverTexture;
	private static String coverLoaded = "";
	private static boolean coverLoading;

	/** The tests can stand in for the helper (null = the real thing). */
	public static volatile @Nullable Track testTrack;
	public static volatile float @Nullable [] testBands;

	private MediaHelper() {
	}

	public static boolean supported() {
		return WINDOWS;
	}

	/** True while the helper process is running. */
	public static boolean running() {
		Process running = process;
		return running != null && running.isAlive();
	}

	public static Track track() {
		Track test = testTrack;
		return test != null ? test : track;
	}

	/** True while the helper is running and reporting sound levels. */
	public static boolean hearing() {
		return testBands != null || System.nanoTime() - bandsAt < 1_000_000_000L;
	}

	/** 32 sound levels from 0 to 1, low notes first (all zero while there's nothing to hear). */
	public static float[] bands() {
		float[] test = testBands;
		if (test != null) {
			return test;
		}
		return System.nanoTime() - bandsAt < 500_000_000L ? bands : new float[BANDS];
	}

	// ---- the helper's life ----

	/** Every client tick: start the helper when a music mod needs it, stop it when none does. */
	public static void tick() {
		if (!WINDOWS || testing()) {
			return;
		}
		NowPlayingHud nowPlaying = ModuleManager.get(NowPlayingHud.class);
		VisualizerHud visualizer = ModuleManager.get(VisualizerHud.class);
		boolean audio = visualizer.isEnabled();
		boolean info = nowPlaying.isEnabled() || (audio && visualizer.source.get() == VisualizerHud.Source.MUSIC_ONLY);
		if (!audio && !info) {
			stop();
			return;
		}
		if (process != null && process.isAlive() && info == runningInfo && audio == runningAudio) {
			return;
		}
		long now = System.currentTimeMillis();
		if (now < retryAt) {
			return;
		}
		// Not running, or running with the wrong parts: (re)start. If it keeps failing, try again every 15 seconds.
		retryAt = now + 15_000;
		stop();
		start(info, audio);
	}

	private static void start(boolean info, boolean audio) {
		try {
			Path folder = ClientInfo.configDir("helper");
			Files.createDirectories(folder);
			Path script = folder.resolve("kairo-media.ps1");
			try (InputStream in = MediaHelper.class.getResourceAsStream("/assets/kairo/helper/kairo-media.ps1")) {
				if (in == null) {
					throw new IllegalStateException("helper script missing from the jar");
				}
				Files.copy(in, script, StandardCopyOption.REPLACE_EXISTING);
			}
			ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
				"-File", script.toString(), "-Info", info ? "1" : "0", "-Audio", audio ? "1" : "0",
				"-ParentPid", Long.toString(ProcessHandle.current().pid()), "-CoverPath", coverFile().toString());
			builder.redirectErrorStream(true);
			Process started = builder.start();
			started.getOutputStream().close();
			process = started;
			runningInfo = info;
			runningAudio = audio;
			Thread reader = new Thread(() -> read(started), "Kairo media helper");
			reader.setDaemon(true);
			reader.start();
			if (!hookAdded) {
				hookAdded = true;
				Runtime.getRuntime().addShutdownHook(new Thread(MediaHelper::stop, "Kairo media helper stop"));
			}
			Kairo.LOGGER.info("Media helper started (now playing: {}, sound levels: {})", info, audio);
		} catch (Exception e) {
			Kairo.LOGGER.warn("Could not start the media helper: {}", e.toString());
		}
	}

	/** Stops the helper (no-op when it isn't running). */
	public static void stop() {
		Process running = process;
		process = null;
		if (running != null) {
			running.destroy();
			Kairo.LOGGER.info("Media helper stopped");
		}
		track = NONE;
		bandsAt = 0;
	}

	private static Path coverFile() {
		return ClientInfo.configDir("helper").resolve("cover.img");
	}

	private static void read(Process from) {
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(from.getInputStream(), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (from != process) {
					break; // a newer helper took over
				}
				if (line.startsWith("A ")) {
					readBands(line);
				} else if (line.startsWith("N\t")) {
					readTrack(line);
				} else if (line.startsWith("E ")) {
					Kairo.LOGGER.warn("Media helper: {}", line.substring(2));
				}
			}
		} catch (Exception e) {
			Kairo.LOGGER.debug("Media helper output closed", e);
		}
	}

	private static void readBands(String line) {
		String[] parts = line.split(" ");
		if (parts.length != BANDS + 1) {
			return;
		}
		float[] next = new float[BANDS];
		try {
			for (int i = 0; i < BANDS; i++) {
				next[i] = Integer.parseInt(parts[i + 1]) / 255f;
			}
		} catch (NumberFormatException e) {
			return;
		}
		bands = next;
		bandsAt = System.nanoTime();
	}

	private static void readTrack(String line) {
		String[] parts = line.split("\t", -1);
		if (parts.length < 8 || parts[1].equals("None")) {
			track = NONE;
			return;
		}
		try {
			track = new Track(true, parts[1].equals("Playing"), Long.parseLong(parts[2]), Long.parseLong(parts[3]), appName(parts[4]), parts[5],
				parts[6], Integer.parseInt(parts[7]), System.nanoTime());
		} catch (NumberFormatException e) {
			track = NONE;
		}
	}

	/** A short, friendly name for the program Windows says is playing ("Spotify.exe" -> "Spotify"). */
	static String appName(String id) {
		String lower = id.toLowerCase(Locale.ROOT);
		String[][] known = {{"spotify", "Spotify"}, {"chrome", "Chrome"}, {"msedge", "Edge"}, {"firefox", "Firefox"}, {"308046b0af4a39cb", "Firefox"},
			{"opera", "Opera"}, {"brave", "Brave"}, {"vivaldi", "Vivaldi"}, {"vlc", "VLC"}, {"zunemusic", "Media Player"}, {"zunevideo", "Movies & TV"},
			{"applemusic", "Apple Music"}, {"itunes", "iTunes"}, {"tidal", "TIDAL"}, {"deezer", "Deezer"}, {"amazon", "Amazon Music"},
			{"youtube", "YouTube Music"}, {"foobar", "foobar2000"}, {"musicbee", "MusicBee"}, {"winamp", "Winamp"}, {"aimp", "AIMP"},
			{"soundcloud", "SoundCloud"}, {"discord", "Discord"}};
		for (String[] entry : known) {
			if (lower.contains(entry[0])) {
				return entry[1];
			}
		}
		String name = id;
		int bang = name.indexOf('!');
		if (bang >= 0) {
			name = name.substring(bang + 1);
		}
		if (name.toLowerCase(Locale.ROOT).endsWith(".exe")) {
			name = name.substring(0, name.length() - 4);
		}
		return name.length() > 24 ? name.substring(0, 24) : name;
	}

	// ---- the cover ----

	/** The current song's cover as a texture, or null while there is none (or it's still loading). */
	public static @Nullable Identifier cover() {
		Track now = track();
		if (!now.known() || now.cover() <= 0 || testTrack != null) {
			return null;
		}
		String key = now.cover() + "|" + now.title() + "|" + now.artist();
		if (!key.equals(coverLoaded) && !coverLoading) {
			coverLoading = true;
			Path file = coverFile();
			CompletableFuture.supplyAsync(() -> {
				try {
					BufferedImage image = ImageIO.read(file.toFile());
					return image == null ? null : square(image, 128);
				} catch (Exception e) {
					return null;
				}
			}).thenAccept(pixels -> Minecraft.getInstance().execute(() -> {
				coverLoading = false;
				coverLoaded = key;
				if (coverTexture != null) {
					Minecraft.getInstance().getTextureManager().release(COVER_ID);
					coverTexture = null;
				}
				if (pixels != null) {
					NativeImage image = new NativeImage(128, 128, false);
					org.lwjgl.system.MemoryUtil.memIntBuffer(image.getPointer(), 128 * 128).put(pixels);
					coverTexture = new DynamicTexture(() -> "Kairo now playing cover", image);
					Minecraft.getInstance().getTextureManager().register(COVER_ID, coverTexture);
				}
			}));
		}
		return coverTexture != null && key.equals(coverLoaded) ? COVER_ID : null;
	}

	/** The middle square of a picture at {@code size} pixels, as the texture's own pixel order (ABGR). */
	private static int[] square(BufferedImage source, int size) {
		int side = Math.min(source.getWidth(), source.getHeight());
		int sx = (source.getWidth() - side) / 2;
		int sy = (source.getHeight() - side) / 2;
		BufferedImage scaled = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = scaled.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
		g.drawImage(source, 0, 0, size, size, sx, sy, sx + side, sy + side, null);
		g.dispose();
		int[] argb = scaled.getRGB(0, 0, size, size, null, 0, size);
		int[] out = new int[argb.length];
		for (int i = 0; i < argb.length; i++) {
			int c = argb[i];
			out[i] = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
		}
		return out;
	}
}
