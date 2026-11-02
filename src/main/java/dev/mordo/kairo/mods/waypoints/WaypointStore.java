package dev.mordo.kairo.mods.waypoints;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Waypoints for the current world or server, one JSON file each in {@code config/kairo/waypoints/}.
 * Every waypoint remembers its dimension.
 */
public final class WaypointStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final List<Waypoint> waypoints = new ArrayList<>();
	private @Nullable String worldKey;
	private boolean dirty;

	public List<Waypoint> all() {
		return waypoints;
	}

	public @Nullable String worldKey() {
		return worldKey;
	}

	/** Loads the right file when the world or server changes. Call every tick. */
	public void sync() {
		String key = currentWorldKey();
		if (key == null) {
			if (worldKey != null) {
				save();
				worldKey = null;
				waypoints.clear();
			}
			return;
		}
		if (!key.equals(worldKey)) {
			save();
			worldKey = key;
			load();
		}
		if (dirty) {
			save();
		}
	}

	public void markDirty() {
		dirty = true;
	}

	public void add(Waypoint waypoint) {
		waypoints.add(waypoint);
		markDirty();
	}

	public void remove(Waypoint waypoint) {
		waypoints.remove(waypoint);
		markDirty();
	}

	/** A readable name for the current world or server ("My World", "play.example.net"), or null. */
	public static @Nullable String worldLabel() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			return server.getWorldData().getLevelName();
		}
		ServerData data = mc.getCurrentServer();
		return data != null ? data.name : "Server";
	}

	/** "sp_<world folder>" in singleplayer, "mp_<address>" on servers. */
	private static @Nullable String currentWorldKey() {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) {
			return null;
		}
		IntegratedServer server = mc.getSingleplayerServer();
		if (server != null) {
			Path root = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
			return "sp_" + clean(String.valueOf(root.getFileName()));
		}
		ServerData data = mc.getCurrentServer();
		return data != null ? "mp_" + clean(data.ip) : "unknown";
	}

	private static String clean(String text) {
		return text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]+", "_");
	}

	private Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve(ClientInfo.MOD_ID).resolve("waypoints").resolve(worldKey + ".json");
	}

	private void load() {
		waypoints.clear();
		dirty = false;
		Path file = file();
		if (!Files.exists(file)) {
			return;
		}
		try {
			JsonElement root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8));
			for (JsonElement element : root.getAsJsonObject().getAsJsonArray("waypoints")) {
				JsonObject o = element.getAsJsonObject();
				Waypoint w = new Waypoint(o.get("name").getAsString(), o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("z").getAsInt(),
					o.get("dimension").getAsString(), (int) Long.parseLong(o.get("color").getAsString(), 16));
				w.visible = !o.has("visible") || o.get("visible").getAsBoolean();
				w.death = o.has("death") && o.get("death").getAsBoolean();
				w.created = o.has("created") ? o.get("created").getAsLong() : 0;
				waypoints.add(w);
			}
		} catch (Exception e) {
			Kairo.LOGGER.error("Couldn't read waypoints {} ({}), keeping a backup and starting empty", file, e.getMessage());
			try {
				Files.move(file, file.resolveSibling(file.getFileName() + ".corrupt"), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ignored) {
				// nothing more we can do
			}
			waypoints.clear();
		}
	}

	public void save() {
		if (worldKey == null || !dirty) {
			return;
		}
		dirty = false;
		JsonArray array = new JsonArray();
		for (Waypoint w : waypoints) {
			JsonObject o = new JsonObject();
			o.addProperty("name", w.name);
			o.addProperty("x", w.x);
			o.addProperty("y", w.y);
			o.addProperty("z", w.z);
			o.addProperty("dimension", w.dimension);
			o.addProperty("color", String.format(Locale.ROOT, "%08X", w.color));
			o.addProperty("visible", w.visible);
			o.addProperty("death", w.death);
			o.addProperty("created", w.created);
			array.add(o);
		}
		JsonObject root = new JsonObject();
		root.add("waypoints", array);
		try {
			Path file = file();
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			Files.writeString(tmp, GSON.toJson(root), StandardCharsets.UTF_8);
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
		} catch (IOException e) {
			Kairo.LOGGER.error("Couldn't save waypoints", e);
		}
	}
}
