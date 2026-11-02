package dev.mordo.kairo.core.keybinds;

import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.SettingHolder;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.Setting;
import net.minecraft.client.Minecraft;

import java.util.ArrayList;
import java.util.List;

/**
 * Dispatches presses of the client's own keybinds. Fed by the keyboard/mouse mixins.
 * Binds only fire in-game (no screen open); our own screens handle their keys themselves.
 * Vanilla key handling is never cancelled.
 */
public final class KeybindManager {
	private static final List<KeybindSetting> BINDS = new ArrayList<>();

	private KeybindManager() {
	}

	/** Collects every KeybindSetting from every registered holder. Call after registration. */
	public static void collect() {
		BINDS.clear();
		for (SettingHolder holder : ModuleManager.allHolders()) {
			for (Setting<?> setting : holder.settings()) {
				if (setting instanceof KeybindSetting bind) {
					BINDS.add(bind);
				}
			}
		}
	}

	public static List<KeybindSetting> all() {
		return BINDS;
	}

	public static void onKeyPressed(int scancode) {
		if (!inGame()) {
			return;
		}
		for (KeybindSetting bind : BINDS) {
			if (bind.pressAction() != null && bind.get().matchesKey(scancode)) {
				runNextFrame(bind.pressAction());
			}
		}
	}

	public static void onMousePressed(int button) {
		if (!inGame()) {
			return;
		}
		for (KeybindSetting bind : BINDS) {
			if (bind.pressAction() != null && bind.get().matchesMouse(button)) {
				runNextFrame(bind.pressAction());
			}
		}
	}

	/**
	 * Actions run after the current input event is fully handled. Otherwise a bind that opens a screen
	 * would have that same key press delivered to the new screen too (Right Shift would open and close the menu).
	 */
	private static void runNextFrame(Runnable action) {
		Minecraft.getInstance().schedule(action);
	}

	private static boolean inGame() {
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && mc.gui.screen() == null && mc.gui.overlay() == null;
	}
}
