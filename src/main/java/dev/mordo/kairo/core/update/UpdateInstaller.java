package dev.mordo.kairo.core.update;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.metadata.ModOrigin;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The Update button. Only ever on a click: downloads this edition's jar from the GitHub release over HTTPS, checks it
 * against the release's SHA256SUMS.txt (a mismatch throws it away), and keeps it in {@code config/kairo/update}.
 * When the game closes, a small helper waits for the game to exit, removes the old Kairo jar from the mods folder and
 * moves the new one in (Windows keeps the running jar locked, and Fabric won't start with two jars of the same mod).
 * Nothing else is downloaded, run or touched.
 */
public final class UpdateInstaller {
	private static final long MAX_JAR = 64L * 1024 * 1024;
	private static final long MAX_SUMS = 64L * 1024;
	/** What a Kairo jar is called: kairo-0.2.0.jar or kairo-void-0.2.0.jar. */
	private static final Pattern JAR_NAME = Pattern.compile("kairo(-void)?-[0-9][0-9A-Za-z.+-]*\\.jar");

	public enum Stage {
		NONE,
		DOWNLOADING,
		/** Downloaded and verified; swapped in when the game closes. */
		READY,
		FAILED
	}

	private static volatile Stage stage = Stage.NONE;
	private static volatile String message = "";
	private static volatile float progress;
	private static volatile @Nullable Path staged;
	private static boolean helperStarted;

	private UpdateInstaller() {
	}

	public static Stage stage() {
		return stage;
	}

	/** A short line about the last failure, for the About page. */
	public static String message() {
		return message;
	}

	/** Download progress 0..1 (0 when the size is unknown). */
	public static float progress() {
		return progress;
	}

	/** This copy of Kairo's own jar in the mods folder, or null (a development run, or loaded from elsewhere). */
	public static @Nullable Path currentJar() {
		try {
			var container = FabricLoader.getInstance().getModContainer(ClientInfo.MOD_ID).orElse(null);
			if (container == null || container.getOrigin().getKind() != ModOrigin.Kind.PATH) {
				return null;
			}
			List<Path> paths = container.getOrigin().getPaths();
			if (paths.size() != 1) {
				return null;
			}
			Path jar = paths.getFirst().toAbsolutePath().normalize();
			Path mods = modsFolder();
			if (!Files.isRegularFile(jar) || !jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")
				|| !jar.getParent().equals(mods)) {
				return null;
			}
			return jar;
		} catch (RuntimeException e) {
			return null;
		}
	}

	private static Path modsFolder() {
		return FabricLoader.getInstance().getGameDir().resolve("mods").toAbsolutePath().normalize();
	}

	/** Why Update can't work here, or null if it can. */
	public static @Nullable String unavailableReason() {
		return currentJar() == null ? "Updating works when Kairo runs from your mods folder (not in a development run)" : null;
	}

	private static Path updateDir() {
		return ClientInfo.configDir("update");
	}

	/** Start-up: an update downloaded last time but not yet applied (the game crashed?) is applied on this exit; an old one is cleared. */
	public static void init() {
		Path dir = updateDir();
		if (!Files.isDirectory(dir)) {
			return;
		}
		try (var files = Files.list(dir)) {
			for (Path file : files.toList()) {
				String name = file.getFileName().toString();
				if (!JAR_NAME.matcher(name).matches()) {
					continue;
				}
				String version = name.replaceFirst("^kairo(-void)?-", "").replaceFirst("\\.jar$", "");
				if (UpdateChecker.compare(version, ClientInfo.VERSION) > 0 && currentJar() != null) {
					staged = file;
					stage = Stage.READY;
				} else {
					Files.deleteIfExists(file); // already installed
				}
			}
		} catch (IOException e) {
			Kairo.LOGGER.warn("Couldn't look at {}", dir, e);
		}
	}

	/** Downloads and verifies {@code release} (on its own thread). The swap happens when the game closes. */
	public static synchronized void install(UpdateChecker.Release release) {
		if (stage == Stage.DOWNLOADING || stage == Stage.READY) {
			return;
		}
		if (unavailableReason() != null || !JAR_NAME.matcher(release.jarName()).matches()) {
			stage = Stage.FAILED;
			message = unavailableReason() != null ? unavailableReason() : "That release has an unexpected file name";
			return;
		}
		stage = Stage.DOWNLOADING;
		progress = 0;
		message = "";
		Thread thread = new Thread(() -> download(release), "Kairo update download");
		thread.setDaemon(true);
		thread.start();
	}

	private static void download(UpdateChecker.Release release) {
		Path dir = updateDir();
		Path part = dir.resolve(release.jarName() + ".part");
		try {
			Files.createDirectories(dir);
			HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
			// 1. The checksum for our jar.
			String sums = new String(get(client, release.sumsUrl(), MAX_SUMS, null, -1), StandardCharsets.UTF_8);
			String expected = expectedHash(sums, release.jarName());
			if (expected == null) {
				throw new IOException("SHA256SUMS.txt has no line for " + release.jarName());
			}
			// 2. The jar, hashed while it downloads.
			MessageDigest sha = MessageDigest.getInstance("SHA-256");
			try (OutputStream out = Files.newOutputStream(part)) {
				get(client, release.jarUrl(), MAX_JAR, new OutputStream() {
					@Override
					public void write(int b) throws IOException {
						out.write(b);
						sha.update((byte) b);
					}

					@Override
					public void write(byte[] b, int off, int len) throws IOException {
						out.write(b, off, len);
						sha.update(b, off, len);
					}
				}, release.jarSize());
			}
			String actual = UpdateChecker.hex(sha.digest());
			if (!actual.equalsIgnoreCase(expected)) {
				Files.deleteIfExists(part);
				throw new IOException("checksum mismatch (expected " + expected + ", got " + actual + ")");
			}
			Path jar = dir.resolve(release.jarName());
			Files.move(part, jar, StandardCopyOption.REPLACE_EXISTING);
			staged = jar;
			stage = Stage.READY;
			Kairo.LOGGER.info("Update {} downloaded and verified; it goes in when the game closes", release.version());
		} catch (Exception e) {
			try {
				Files.deleteIfExists(part);
			} catch (IOException ignored) {
				// nothing to clean up
			}
			stage = Stage.FAILED;
			message = e.getMessage() != null && e.getMessage().startsWith("checksum")
				? "The download didn't match its checksum, so it wasn't used"
				: "The download failed. Check your connection and try again";
			Kairo.LOGGER.warn("Update download failed: {}", e.toString());
		}
	}

	/**
	 * GETs {@code url} (HTTPS only, at most {@code limit} bytes). With {@code sink} the body is streamed into it and
	 * null returned; otherwise the body is returned.
	 */
	private static byte @Nullable [] get(HttpClient client, String url, long limit, @Nullable OutputStream sink, long size) throws Exception {
		if (!url.startsWith("https://")) {
			throw new IOException("not HTTPS: " + url);
		}
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
			.timeout(Duration.ofSeconds(60))
			.header("User-Agent", UpdateChecker.USER_AGENT)
			.GET()
			.build();
		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
		if (response.statusCode() != 200) {
			throw new IOException("HTTP " + response.statusCode() + " for " + url);
		}
		if (!response.uri().getScheme().equals("https")) {
			throw new IOException("redirected off HTTPS");
		}
		try (InputStream in = response.body()) {
			java.io.ByteArrayOutputStream memory = sink == null ? new java.io.ByteArrayOutputStream() : null;
			OutputStream out = sink != null ? sink : memory;
			byte[] buffer = new byte[16384];
			long total = 0;
			int n;
			while ((n = in.read(buffer)) > 0) {
				total += n;
				if (total > limit) {
					throw new IOException("download too large");
				}
				out.write(buffer, 0, n);
				if (size > 0) {
					progress = Math.min(1f, total / (float) size);
				}
			}
			return memory == null ? null : memory.toByteArray();
		}
	}

	/** The hash for {@code fileName} in a sha256sum-style file ("hash  name" per line). */
	static @Nullable String expectedHash(String sums, String fileName) {
		for (String line : sums.split("\\R")) {
			String[] parts = line.trim().split("\\s+\\*?", 2);
			if (parts.length == 2 && parts[1].trim().equals(fileName) && parts[0].matches("(?i)[0-9a-f]{64}")) {
				return parts[0].toLowerCase(Locale.ROOT);
			}
		}
		return null;
	}

	// ---- swapping the jar when the game closes ----

	/** Called as the game closes: if an update is ready, hands it to the helper that swaps the jar once the game is gone. */
	public static synchronized void onExit() {
		Path next = staged;
		Path current = currentJar();
		if (stage != Stage.READY || next == null || helperStarted || current == null || !Files.isRegularFile(next)) {
			return;
		}
		if (!JAR_NAME.matcher(next.getFileName().toString()).matches()) {
			return;
		}
		helperStarted = true;
		Path mods = modsFolder();
		try {
			if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
				startWindowsHelper(current, next, mods);
			} else {
				// Other systems don't lock open files: swap right away.
				swap(current, next, mods);
			}
		} catch (Exception e) {
			Kairo.LOGGER.error("Couldn't apply the update; the new jar stays in {}", next.getParent(), e);
		}
	}

	private static void swap(Path current, Path next, Path mods) throws IOException {
		Path target = mods.resolve(next.getFileName().toString());
		Path pending = mods.resolve(next.getFileName() + ".pending");
		Files.copy(next, pending, StandardCopyOption.REPLACE_EXISTING);
		Files.deleteIfExists(current);
		Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING);
		Files.deleteIfExists(next);
		Kairo.LOGGER.info("Updated {} -> {}", current.getFileName(), target.getFileName());
	}

	/**
	 * Windows: a hidden PowerShell script (written by Kairo into config/kairo/update) that waits for this game process
	 * to end, then: copies the new jar into mods under a name Fabric ignores, removes the old jar (giving up, and
	 * changing nothing, if it stays locked), and renames the new one into place. It logs to update.log next to itself.
	 */
	private static void startWindowsHelper(Path current, Path next, Path mods) throws IOException {
		Path dir = next.getParent();
		Path script = dir.resolve("apply-update.ps1");
		Files.writeString(script, HELPER, StandardCharsets.UTF_8);
		new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden",
			"-File", script.toString(),
			"-GamePid", Long.toString(ProcessHandle.current().pid()),
			"-OldJar", current.toString(),
			"-NewJar", next.toString(),
			"-ModsDir", mods.toString(),
			"-Log", dir.resolve("update.log").toString())
			.redirectOutput(ProcessBuilder.Redirect.DISCARD)
			.redirectError(ProcessBuilder.Redirect.DISCARD)
			.start();
		Kairo.LOGGER.info("Update helper started: {} replaces {} after the game closes", next.getFileName(), current.getFileName());
	}

	private static final String HELPER = """
		# Kairo update helper. Written and started by Kairo when the game closed after you clicked Update.
		# Waits for the game to exit, then swaps the old Kairo jar in the mods folder for the new one.
		param([int]$GamePid, [string]$OldJar, [string]$NewJar, [string]$ModsDir, [string]$Log)
		function Write-Log([string]$Text) { Add-Content -LiteralPath $Log -Value ("[{0}] {1}" -f (Get-Date -Format s), $Text) }
		try {
		    $game = Get-Process -Id $GamePid -ErrorAction SilentlyContinue
		    if ($game) { [void]$game.WaitForExit(180000) }
		    $name = [IO.Path]::GetFileName($NewJar)
		    if ($name -notmatch '^kairo(-void)?-[0-9][0-9A-Za-z.+-]*\\.jar$') { Write-Log "Unexpected file name $name; nothing changed"; exit 1 }
		    $target = Join-Path $ModsDir $name
		    $pending = "$target.pending"
		    Copy-Item -LiteralPath $NewJar -Destination $pending -Force
		    $removed = $false
		    for ($i = 0; $i -lt 30 -and -not $removed; $i++) {
		        try { if (Test-Path -LiteralPath $OldJar) { Remove-Item -LiteralPath $OldJar -Force -ErrorAction Stop }; $removed = $true }
		        catch { Start-Sleep -Seconds 1 }
		    }
		    if (-not $removed) { Remove-Item -LiteralPath $pending -Force; Write-Log "The old jar stayed locked; nothing changed. Close Minecraft fully and update again."; exit 1 }
		    Move-Item -LiteralPath $pending -Destination $target -Force
		    Remove-Item -LiteralPath $NewJar -Force -ErrorAction SilentlyContinue
		    Write-Log "Updated: $target"
		} catch {
		    Write-Log ("Update failed: " + $_)
		    exit 1
		}
		""";
}
