package dev.mordo.kairo.gui.main;

import dev.mordo.kairo.mixin.JoinMultiplayerScreenAccessor;
import dev.mordo.kairo.mixin.SelectWorldScreenAccessor;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import org.jspecify.annotations.Nullable;

/** Reads the "go back to" screen of the vanilla screens the client replaces. */
public final class ScreenParents {
	private ScreenParents() {
	}

	public static @Nullable Screen parentOf(Screen screen) {
		if (screen instanceof SelectWorldScreen) {
			return ((SelectWorldScreenAccessor) screen).kairo$lastScreen();
		}
		if (screen instanceof JoinMultiplayerScreen) {
			return ((JoinMultiplayerScreenAccessor) screen).kairo$lastScreen();
		}
		return null;
	}
}
