package dev.mordo.kairo.core;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;

/**
 * The one place for the client's name and id. Renaming the client means changing {@link #NAME}
 * (and {@link #MOD_ID} plus the matching id in fabric.mod.json / the assets folder if the id changes too).
 */
public final class ClientInfo {
	/** "Kairo", or "Kairo Void" in the Void jar. */
	public static final String NAME = Edition.isVoid() ? "Kairo Void" : "Kairo";
	public static final String FULL_NAME = Edition.isVoid() ? NAME : NAME + " Client";
	public static final String MOD_ID = "kairo";
	public static final String AUTHOR = "Mordsome";
	public static final String MC_VERSION = "26.3";

	/** Full version from fabric.mod.json, e.g. "0.1.0". */
	public static final String VERSION = FabricLoader.getInstance()
		.getModContainer(MOD_ID)
		.map(c -> c.getMetadata().getVersion().getFriendlyString())
		.orElse("dev");

	/** Short version for UI chrome, e.g. "v0.1". */
	public static final String SHORT_VERSION = "v" + shortVersion(VERSION);

	private ClientInfo() {
	}

	/** config/kairo (config/kairo-void for Void, so the two keep their own settings), or a folder inside it (not created here). */
	public static java.nio.file.Path configDir(String... sub) {
		java.nio.file.Path dir = FabricLoader.getInstance().getConfigDir().resolve(Edition.isVoid() ? MOD_ID + "-void" : MOD_ID);
		for (String s : sub) {
			dir = dir.resolve(s);
		}
		return dir;
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	private static String shortVersion(String version) {
		String[] parts = version.split("[.+-]");
		if (parts.length >= 2) {
			return parts[0] + "." + parts[1];
		}
		return version;
	}
}
