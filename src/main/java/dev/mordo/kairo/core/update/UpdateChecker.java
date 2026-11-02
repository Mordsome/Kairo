package dev.mordo.kairo.core.update;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.jspecify.annotations.Nullable;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Opt-in update check (Settings → About → "Check for updates on GitHub", off by default). At most once per launch,
 * on its own thread, it asks GitHub's public API for the latest release of Mordsome/Kairo. The request carries only a
 * User-Agent naming the updater (GitHub requires one): nothing about the player, the game or the computer. Any
 * failure is a log line and nothing else. Nothing is downloaded here; see {@link UpdateInstaller} for the Update button.
 */
public final class UpdateChecker {
	public static final String REPO = "Mordsome/Kairo";
	private static final String API = "https://api.github.com/repos/" + REPO + "/releases/latest";
	/** Release pages and downloads must come from this repository. */
	static final String RELEASE_PAGES = "https://github.com/" + REPO + "/releases/";
	static final String USER_AGENT = "Kairo-Updater";
	private static final int MAX_NOTES = 6000;

	public enum State {
		/** Not checked (switched off, or not yet). */
		IDLE,
		CHECKING,
		UP_TO_DATE,
		AVAILABLE,
		/** The check failed (offline, GitHub unreachable...). Logged only. */
		FAILED
	}

	/** A newer release: its version, page, notes as plain text, and this edition's jar and checksum file. */
	public record Release(String version, String pageUrl, String notes, String jarName, String jarUrl, long jarSize, String sumsUrl) {
	}

	private static volatile State state = State.IDLE;
	private static volatile @Nullable Release latest;
	private static boolean checkedThisLaunch;
	private static boolean announced;

	private UpdateChecker() {
	}

	public static State state() {
		return state;
	}

	public static @Nullable Release latest() {
		return latest;
	}

	/** Shows {@code release} as available without asking GitHub (the visual tour uses it to show the About page). */
	public static void showForTest(Release release) {
		latest = release;
		state = State.AVAILABLE;
	}

	/** Client tick: checks once the main menu shows (if allowed), then tells the player once if there's something new. */
	public static void tick(Minecraft mc) {
		if (!ClientSettings.INSTANCE.checkUpdates.isOn()) {
			return;
		}
		Screen screen = mc.gui.screen();
		boolean mainMenu = screen instanceof net.minecraft.client.gui.screens.TitleScreen
			|| screen instanceof dev.mordo.kairo.gui.main.MainMenuScreen;
		if (!mainMenu) {
			return;
		}
		checkOnce();
		Release r = latest;
		if (state == State.AVAILABLE && r != null && !announced) {
			announced = true;
			Toasts.show(ClientInfo.NAME + " " + r.version() + " is out · Right Shift → Settings → About", Theme.accent(), 6000);
		}
	}

	/** Starts the check if it hasn't run this launch (also called when the player switches checking on). */
	public static synchronized void checkOnce() {
		if (checkedThisLaunch || !ClientSettings.INSTANCE.checkUpdates.isOn()) {
			return;
		}
		checkedThisLaunch = true;
		state = State.CHECKING;
		Thread thread = new Thread(UpdateChecker::run, "Kairo update check");
		thread.setDaemon(true);
		thread.start();
	}

	private static void run() {
		try {
			HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(5))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
			HttpRequest request = HttpRequest.newBuilder(URI.create(API))
				.timeout(Duration.ofSeconds(8))
				.header("User-Agent", USER_AGENT)
				.header("Accept", "application/vnd.github+json")
				.GET()
				.build();
			HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() != 200) {
				throw new IllegalStateException("GitHub answered " + response.statusCode());
			}
			Release release = parse(response.body(), ClientInfo.VERSION, Edition.isVoid());
			latest = release;
			state = release != null ? State.AVAILABLE : State.UP_TO_DATE;
			if (release != null) {
				Kairo.LOGGER.info("Update check: {} {} is available (you have {})", ClientInfo.NAME, release.version(), ClientInfo.VERSION);
			} else {
				Kairo.LOGGER.info("Update check: {} {} is up to date", ClientInfo.NAME, ClientInfo.VERSION);
			}
		} catch (Exception e) {
			state = State.FAILED;
			Kairo.LOGGER.info("Update check failed ({}); trying again next launch", e.toString());
		}
	}

	/**
	 * Reads GitHub's release JSON. Returns the release if it is newer than {@code current} and has this edition's jar
	 * and a SHA256SUMS.txt from this repository; null otherwise.
	 */
	public static @Nullable Release parse(String json, String current, boolean voidEdition) {
		JsonObject root = JsonParser.parseString(json).getAsJsonObject();
		if (root.has("draft") && root.get("draft").getAsBoolean() || root.has("prerelease") && root.get("prerelease").getAsBoolean()) {
			return null;
		}
		String tag = root.get("tag_name").getAsString();
		String version = tag.startsWith("v") || tag.startsWith("V") ? tag.substring(1) : tag;
		if (compare(version, current) <= 0) {
			return null;
		}
		String page = string(root, "html_url");
		if (page == null || !page.startsWith(RELEASE_PAGES)) {
			page = RELEASE_PAGES + "latest";
		}
		String jarName = (voidEdition ? "kairo-void-" : "kairo-") + version + ".jar";
		String jarUrl = null;
		long jarSize = -1;
		String sumsUrl = null;
		JsonArray assets = root.has("assets") && root.get("assets").isJsonArray() ? root.getAsJsonArray("assets") : new JsonArray();
		for (JsonElement element : assets) {
			JsonObject asset = element.getAsJsonObject();
			String name = string(asset, "name");
			String url = string(asset, "browser_download_url");
			if (name == null || url == null || !url.startsWith(RELEASE_PAGES + "download/")) {
				continue;
			}
			if (name.equals(jarName)) {
				jarUrl = url;
				jarSize = asset.has("size") ? asset.get("size").getAsLong() : -1;
			} else if (name.equals("SHA256SUMS.txt")) {
				sumsUrl = url;
			}
		}
		if (jarUrl == null || sumsUrl == null) {
			Kairo.LOGGER.info("Release {} has no {} or SHA256SUMS.txt; not offering it", tag, jarName);
			return null;
		}
		String body = string(root, "body");
		return new Release(version, page, plainText(body == null ? "" : body), jarName, jarUrl, jarSize, sumsUrl);
	}

	/** Compares dotted versions number by number ("0.10.0" > "0.9.2"); anything after a - or + is ignored. */
	public static int compare(String a, String b) {
		int[] x = numbers(a);
		int[] y = numbers(b);
		for (int i = 0; i < Math.max(x.length, y.length); i++) {
			int xi = i < x.length ? x[i] : 0;
			int yi = i < y.length ? y[i] : 0;
			if (xi != yi) {
				return Integer.compare(xi, yi);
			}
		}
		return 0;
	}

	private static int[] numbers(String version) {
		String core = version.trim().split("[-+ ]", 2)[0];
		String[] parts = core.split("\\.");
		int[] out = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			try {
				out[i] = Integer.parseInt(parts[i].replaceAll("\\D", "").isEmpty() ? "0" : parts[i].replaceAll("\\D", ""));
			} catch (NumberFormatException e) {
				out[i] = 0;
			}
		}
		return out;
	}

	/** Release notes (Markdown) as plain text: no headings marks, emphasis, links, images or HTML. */
	public static String plainText(String markdown) {
		StringBuilder out = new StringBuilder();
		for (String raw : markdown.replace("\r", "").split("\n")) {
			String line = raw.strip();
			line = line.replaceAll("!\\[[^]]*]\\([^)]*\\)", "");          // images
			line = line.replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1");        // links: keep the text
			line = line.replaceAll("<[^>]+>", "");                          // HTML tags
			line = line.replaceAll("^#{1,6}\\s*", "");                      // headings
			line = line.replaceAll("^>\\s?(\\[!\\w+]\\s*)?", "");           // quotes and callouts
			line = line.replaceAll("^[-*+]\\s+", "• ");                     // bullets
			line = line.replaceAll("(\\*\\*|__|`)", "");                    // bold, code
			line = line.replaceAll("(?<!\\w)[*_](\\S[^*_]*)[*_](?!\\w)", "$1"); // italics
			out.append(line).append('\n');
		}
		String text = out.toString().replaceAll("\n{3,}", "\n\n").strip();
		return text.length() > MAX_NOTES ? text.substring(0, MAX_NOTES) + "…" : text;
	}

	private static @Nullable String string(JsonObject obj, String key) {
		JsonElement e = obj.get(key);
		return e != null && e.isJsonPrimitive() ? e.getAsString() : null;
	}

	/** Lower-case hex, for checksums. */
	static String hex(byte[] bytes) {
		StringBuilder sb = new StringBuilder(bytes.length * 2);
		for (byte b : bytes) {
			sb.append(String.format(Locale.ROOT, "%02x", b));
		}
		return sb.toString();
	}
}
