package dev.mordo.kairo.core.keybinds;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.core.SettingHolder;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.gui.ClientScreens;
import dev.mordo.kairo.gui.Toasts;

/** Client-wide keybinds (module-specific binds live in their modules). Saved under "keybinds". */
public final class ClientKeybinds extends SettingHolder {
	public static final ClientKeybinds INSTANCE = new ClientKeybinds();

	public final KeybindSetting openMenu = add(new KeybindSetting("open_menu", "Open menu", Keybind.keyboard(InputConstants.KEY_RSHIFT))
		.onPress(ClientScreens::openRing));

	public final KeybindSetting togglePvp = add(new KeybindSetting("toggle_pvp", "Toggle PvP mode", Keybind.NONE)
		.onPress(() -> {
			PvpMode.toggle();
			Toasts.pvpMode(PvpMode.isActive());
		}));

	public final KeybindSetting toggleVisuals = add(dev.mordo.kairo.core.Edition.isVoid()
		? new KeybindSetting("toggle_visuals", "Visuals on / off", Keybind.keyboard(InputConstants.KEY_J)).onPress(dev.mordo.kairo.render.Visuals::toggle)
		: new KeybindSetting("toggle_visuals", "Visuals on / off", Keybind.NONE).<KeybindSetting>hidden());

	private ClientKeybinds() {
		super("keybinds");
	}
}
