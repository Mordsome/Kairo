package dev.mordo.kairo.core.config;

import com.google.gson.JsonObject;
import dev.mordo.kairo.Kairo;

/**
 * Upgrades old profile JSON step by step using the "configVersion" field.
 * To change the format: bump {@link ConfigManager#CONFIG_VERSION} and add a case that turns version N-1 into N.
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
				default -> Kairo.LOGGER.warn("No migration step from config v{}", version);
			}
			version++;
		}
		root.addProperty("configVersion", to);
		return root;
	}
}
