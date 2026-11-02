package dev.mordo.kairo.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.core.SettingHolder;
import dev.mordo.kairo.core.settings.Setting;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Saves and loads profiles from {@code config/<modid>/}.
 *
 * <pre>
 * config/kairo/
 *   kairo.json          which profile is active
 *   profiles/default.json one file per profile
 * </pre>
 *
 * Changes are saved ~0.75s after the last edit (debounced on the client tick) and on exit.
 * A file that can't be read is backed up next to itself and replaced by defaults; it never crashes the game.
 */
public final class ConfigManager {
	/** 1 = 0.1.0, 2 = 0.2.0 (theme tints, panel edge light; see {@link ConfigMigrator}). */
	public static final int CONFIG_VERSION = 2;
	public static final String DEFAULT_PROFILE = "default";
	private static final long SAVE_DELAY_MS = 750;
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private static Path dir;
	private static Path profilesDir;
	private static Path metaFile;
	private static String activeProfile = DEFAULT_PROFILE;

	private static volatile boolean dirty;
	private static volatile long lastChange;
	/** While loading, setting changes must not schedule saves. */
	private static boolean loading;

	private ConfigManager() {
	}

	public static void init() {
		dir = FabricLoader.getInstance().getConfigDir().resolve(ClientInfo.MOD_ID);
		profilesDir = dir.resolve("profiles");
		metaFile = dir.resolve(ClientInfo.MOD_ID + ".json");
		try {
			Files.createDirectories(profilesDir);
		} catch (IOException e) {
			Kairo.LOGGER.error("Couldn't create config folder {}", profilesDir, e);
		}
		readMeta();
		loadProfile(activeProfile);
	}

	// ---- saving ----

	public static void markDirty() {
		if (loading) {
			return;
		}
		dirty = true;
		lastChange = System.currentTimeMillis();
	}

	/** Called every client tick. */
	public static void tick() {
		if (dirty && System.currentTimeMillis() - lastChange >= SAVE_DELAY_MS) {
			save();
		}
	}

	public static void save() {
		if (profilesDir == null) {
			return;
		}
		dirty = false;
		JsonObject root = new JsonObject();
		root.addProperty("configVersion", CONFIG_VERSION);
		root.addProperty("pvpMode", PvpMode.isActiveForReal());
		JsonObject holders = new JsonObject();
		for (SettingHolder holder : ModuleManager.allHolders()) {
			holders.add(holder.id(), writeHolder(holder));
		}
		root.add("holders", holders);
		writeAtomically(profileFile(activeProfile), GSON.toJson(root));
		writeMeta();
	}

	private static JsonObject writeHolder(SettingHolder holder) {
		JsonObject normal = new JsonObject();
		JsonObject pvp = new JsonObject();
		for (Setting<?> setting : holder.settings()) {
			normal.add(setting.id(), setting.normalJson());
			if (setting.shouldWritePvp()) {
				pvp.add(setting.id(), setting.pvpJson());
			}
		}
		JsonObject obj = new JsonObject();
		obj.add("settings", normal);
		if (!pvp.isEmpty()) {
			obj.add("pvp", pvp);
		}
		return obj;
	}

	// ---- loading ----

	public static void loadProfile(String name) {
		activeProfile = sanitizeName(name);
		Path file = profileFile(activeProfile);
		loading = true;
		try {
			resetAllSilently();
			if (Files.exists(file)) {
				JsonObject root = readJson(file);
				if (root == null) {
					// Corrupt: keep the defaults we just applied and overwrite the broken file.
					dirty = true;
				} else {
					applyProfile(root);
				}
			} else {
				Kairo.LOGGER.info("No profile '{}' yet, using defaults", activeProfile);
				dirty = true;
			}
		} finally {
			loading = false;
		}
		afterLoad();
		if (dirty) {
			save();
		}
	}

	private static void applyProfile(JsonObject root) {
		int version = root.has("configVersion") ? root.get("configVersion").getAsInt() : 0;
		if (version < CONFIG_VERSION) {
			// Keep the old file next to the new one, then save the upgraded profile right away.
			Path file = profileFile(activeProfile);
			try {
				Files.copy(file, file.resolveSibling(file.getFileName() + ".v" + version + "-backup"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException e) {
				Kairo.LOGGER.warn("Couldn't back up {} before upgrading it", file.getFileName(), e);
			}
			dirty = true;
		}
		root = ConfigMigrator.migrate(root, version, CONFIG_VERSION);

		PvpMode.restore(root.has("pvpMode") && root.get("pvpMode").getAsBoolean());

		JsonObject holders = root.has("holders") && root.get("holders").isJsonObject()
			? root.getAsJsonObject("holders") : new JsonObject();
		for (SettingHolder holder : ModuleManager.allHolders()) {
			JsonElement element = holders.get(holder.id());
			if (element != null && element.isJsonObject()) {
				readHolder(holder, element.getAsJsonObject());
			}
		}
	}

	private static void readHolder(SettingHolder holder, JsonObject obj) {
		JsonObject normal = obj.has("settings") && obj.get("settings").isJsonObject() ? obj.getAsJsonObject("settings") : null;
		JsonObject pvp = obj.has("pvp") && obj.get("pvp").isJsonObject() ? obj.getAsJsonObject("pvp") : null;
		for (Setting<?> setting : holder.settings()) {
			// Each value is read on its own, so one bad entry only resets that one setting.
			if (normal != null && normal.has(setting.id())) {
				try {
					setting.loadNormal(normal.get(setting.id()));
				} catch (RuntimeException e) {
					Kairo.LOGGER.warn("Bad value for {}.{} ({}), using default", holder.id(), setting.id(), e.getMessage());
				}
			}
			if (pvp != null && pvp.has(setting.id())) {
				try {
					setting.loadPvp(pvp.get(setting.id()));
				} catch (RuntimeException e) {
					Kairo.LOGGER.warn("Bad PvP override for {}.{} ({}), using default", holder.id(), setting.id(), e.getMessage());
				}
			}
		}
	}

	private static void resetAllSilently() {
		for (SettingHolder holder : ModuleManager.allHolders()) {
			for (Setting<?> setting : holder.settings()) {
				setting.resetSilently();
			}
		}
	}

	/** Loading bypassed listeners, so tell everything about its (possibly new) values once. */
	private static void afterLoad() {
		for (SettingHolder holder : ModuleManager.allHolders()) {
			for (Setting<?> setting : holder.settings()) {
				setting.notifyEffectiveChanged();
			}
		}
		ModuleManager.syncAll();
	}

	/** Returns null (after backing the file up) if the file isn't valid JSON. */
	private static JsonObject readJson(Path file) {
		try {
			String text = Files.readString(file, StandardCharsets.UTF_8);
			JsonElement element = JsonParser.parseString(text);
			if (!element.isJsonObject()) {
				throw new IllegalStateException("top level is not an object");
			}
			return element.getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			Path backup = file.resolveSibling(file.getFileName() + ".corrupt-"
				+ LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
			Kairo.LOGGER.error("Config {} is unreadable ({}). Backing it up to {} and using defaults.",
				file, e.getMessage(), backup.getFileName());
			try {
				Files.move(file, backup, StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException moveError) {
				Kairo.LOGGER.error("Couldn't back up corrupt config", moveError);
			}
			return null;
		}
	}

	// ---- profiles ----

	public static String activeProfile() {
		return activeProfile;
	}

	public static List<String> listProfiles() {
		List<String> names = new ArrayList<>();
		if (profilesDir != null && Files.isDirectory(profilesDir)) {
			try (Stream<Path> files = Files.list(profilesDir)) {
				files.map(p -> p.getFileName().toString())
					.filter(n -> n.endsWith(".json"))
					.map(n -> n.substring(0, n.length() - 5))
					.sorted()
					.forEach(names::add);
			} catch (IOException e) {
				Kairo.LOGGER.warn("Couldn't list profiles", e);
			}
		}
		if (!names.contains(activeProfile)) {
			names.add(activeProfile);
		}
		return names;
	}

	public static void switchProfile(String name) {
		save();
		loadProfile(name);
	}

	/** Saves the current values under a new name and switches to it. */
	public static void createProfile(String name) {
		save();
		activeProfile = sanitizeName(name);
		save();
	}

	public static boolean deleteProfile(String name) {
		String clean = sanitizeName(name);
		if (clean.equals(activeProfile)) {
			return false;
		}
		try {
			return Files.deleteIfExists(profileFile(clean));
		} catch (IOException e) {
			Kairo.LOGGER.warn("Couldn't delete profile {}", clean, e);
			return false;
		}
	}

	public static String sanitizeName(String name) {
		String clean = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-");
		clean = clean.replaceAll("^-+|-+$", "");
		return clean.isEmpty() ? DEFAULT_PROFILE : clean;
	}

	private static Path profileFile(String name) {
		return profilesDir.resolve(name + ".json");
	}

	// ---- meta file ----

	private static void readMeta() {
		if (!Files.exists(metaFile)) {
			return;
		}
		JsonObject meta = readJson(metaFile);
		if (meta != null && meta.has("activeProfile")) {
			activeProfile = sanitizeName(meta.get("activeProfile").getAsString());
		}
	}

	private static void writeMeta() {
		JsonObject meta = new JsonObject();
		meta.addProperty("configVersion", CONFIG_VERSION);
		meta.addProperty("activeProfile", activeProfile);
		writeAtomically(metaFile, GSON.toJson(meta));
	}

	private static void writeAtomically(Path target, String content) {
		Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
		try {
			Files.createDirectories(target.getParent());
			Files.writeString(tmp, content, StandardCharsets.UTF_8);
			try {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch (AtomicMoveNotSupportedException e) {
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
			}
		} catch (IOException e) {
			Kairo.LOGGER.error("Couldn't save {}", target, e);
		}
	}
}
