package dev.mordo.kairo.core.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import dev.mordo.kairo.Kairo;

/**
 * Upgrades old profile JSON step by step using the "configVersion" field.
 * To change the format: bump {@link ConfigManager#CONFIG_VERSION} and add a case that turns version N-1 into N.
 * Every step only adds or copies values; nothing a player set is ever dropped.
 */
final class ConfigMigrator {
	private ConfigMigrator() {
	}

	static JsonObject migrate(JsonObject root, int from, int to) {
		if (from > to) {
			Kairo.LOGGER.warn("Profile was written by a newer version (config v{} > v{}); reading what we understand", from, to);
			return root;
		}
		int version = from;
		while (version < to) {
			switch (version) {
				// v0 = files written before the version field existed; same shape as v1.
				case 0 -> {
				}
				// v1 (0.1.0) -> v2 (0.2.0): keep the look a profile had.
				case 1 -> toV2(root);
				default -> Kairo.LOGGER.warn("No migration step from config v{}", version);
			}
			version++;
		}
		root.addProperty("configVersion", to);
		return root;
	}

	/**
	 * 0.2.0 adds theme tints (on for new players) and splits "Panel edge light" off "Living menus". A 0.1.0 profile keeps
	 * exactly the look it had: tints start off (one switch in Visuals → Theme turns them on), and the panel light stays
	 * off if Living menus was off.
	 */
	private static void toV2(JsonObject root) {
		JsonObject client = settings(root, "client");
		if (client == null) {
			return;
		}
		setIfMissing(client, "tint_backgrounds", new JsonPrimitive(false));
		setIfMissing(client, "tint_text", new JsonPrimitive(false));
		JsonElement menuFx = client.get("menu_fx");
		if (menuFx != null && menuFx.isJsonPrimitive()) {
			setIfMissing(client, "panel_edge_light", menuFx);
		}
		Kairo.LOGGER.info("Profile upgraded to the 0.2.0 format (your look is unchanged)");
	}

	/** holders.&lt;id&gt;.settings, or null if the profile doesn't have it. */
	private static JsonObject settings(JsonObject root, String holder) {
		if (!(root.get("holders") instanceof JsonObject holders) || !(holders.get(holder) instanceof JsonObject obj)) {
			return null;
		}
		return obj.get("settings") instanceof JsonObject s ? s : null;
	}

	private static void setIfMissing(JsonObject obj, String key, JsonElement value) {
		if (!obj.has(key)) {
			obj.add(key, value.deepCopy());
		}
	}
}
