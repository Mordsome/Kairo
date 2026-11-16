package dev.mordo.kairo.core;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.config.ConfigManager;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Restarting the game from Kairo (Settings → About). A game can't start itself again cleanly, so the restart is done by
 * whoever started it: the Kairo Launcher passes {@code -Dkairo.launcher.restart=<file>} when it can do that. To
 * restart, Kairo writes that file and closes; the launcher sees the file when the game has exited and starts the same
 * install again. Without a launcher that offers this, the button only closes the game. See the Kairo Addons spec,
 * "Restarting the game".
 */
public final class Restart {
	private static final String PROPERTY = "kairo.launcher.restart";

	private Restart() {
	}

	/** True when the launcher that started the game will start it again after a restart request. */
	public static boolean byLauncher() {
		String path = System.getProperty(PROPERTY, "");
		return !path.isBlank();
	}

	/** Saves the settings, asks the launcher for a restart (when it offers one) and closes the game. */
	public static void restart() {
		ConfigManager.save();
		if (byLauncher()) {
			try {
				Path marker = Path.of(System.getProperty(PROPERTY));
				if (marker.getParent() != null) {
					Files.createDirectories(marker.getParent());
				}
				Files.writeString(marker, "restart\n", StandardCharsets.UTF_8);
			} catch (Exception e) {
				Kairo.LOGGER.warn("Could not ask the launcher for a restart; closing instead: {}", e.toString());
			}
		}
		Minecraft.getInstance().stop();
	}
}
