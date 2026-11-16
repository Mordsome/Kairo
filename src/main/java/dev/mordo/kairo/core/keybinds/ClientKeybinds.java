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

	// Straight to a page, without the ring (unbound by default).
	public final KeybindSetting openMods = add(new KeybindSetting("open_mods", "Open Mods", Keybind.NONE)
		.onPress(() -> dev.mordo.kairo.gui.MenuShortcuts.open(dev.mordo.kairo.gui.menu.MenuScreen::mods)));
	public final KeybindSetting openVisuals = add(new KeybindSetting("open_visuals", "Open Visuals", Keybind.NONE)
		.onPress(() -> dev.mordo.kairo.gui.MenuShortcuts.open(dev.mordo.kairo.gui.menu.MenuScreen::visualsPage)));
	public final KeybindSetting openCosmetics = add(new KeybindSetting("open_cosmetics", "Open Cosmetics", Keybind.NONE)
		.onPress(() -> dev.mordo.kairo.gui.MenuShortcuts.open(dev.mordo.kairo.gui.menu.MenuScreen::cosmetics)));
	public final KeybindSetting openSettings = add(new KeybindSetting("open_settings", "Open Settings", Keybind.NONE)
		.onPress(() -> dev.mordo.kairo.gui.MenuShortcuts.open(dev.mordo.kairo.gui.menu.MenuScreen::settings)));
	public final KeybindSetting openScreenshots = add(new KeybindSetting("open_screenshots", "Open Screenshots", Keybind.NONE)
		.onPress(() -> dev.mordo.kairo.gui.MenuShortcuts.open(dev.mordo.kairo.gui.menu.MenuScreen::screenshots)));
	public final KeybindSetting openLayout = add(new KeybindSetting("open_layout", "Open HUD layout", Keybind.NONE)
		.onPress(() -> net.minecraft.client.Minecraft.getInstance().gui.setScreen(new dev.mordo.kairo.gui.layout.LayoutScreen())));

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
