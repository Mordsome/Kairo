package dev.mordo.kairo.core;

import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.core.settings.TextSetting;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Main menu setup (the gear on the main menu): button order and visibility, the cards, and the menu background
 * (Minecraft's panorama, any image or GIF, or your own panorama). Saved in the profile under "main_menu".
 */
public final class MainMenuSettings extends SettingHolder {
	/** Button ids, in the default order. */
	public static final List<String> BUTTONS = List.of("singleplayer", "multiplayer", "client", "options", "quit");
	public static final String VANILLA_BACKGROUND = "panorama";
	/** After the constants above: the constructor uses them. */
	public static final MainMenuSettings INSTANCE = new MainMenuSettings();

	/** Layout of the client's own main menu (Minecraft's title screen is ClientSettings.customMainMenu = off). */
	public enum Layout implements dev.mordo.kairo.core.settings.EnumSetting.Labeled {
		CLASSIC("Kairo"),
		DOCK("Dock"),
		ORBIT("Orbit"),
		/** The Custom home screen (kept under its old name so saved settings still load). */
		DASHBOARD("Custom"),
		/** Kairo Void: the name and a centred column of words over the drawn space scene. */
		SIMPLE("Simple");

		private final String label;

		Layout(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final dev.mordo.kairo.core.settings.EnumSetting<Layout> layout = add(
		new dev.mordo.kairo.core.settings.EnumSetting<>("layout", "Menu layout", Layout.CLASSIC)
			.<dev.mordo.kairo.core.settings.EnumSetting<Layout>>hidden());

	/** Kairo Void offers three styles: 0 Stage, 1 Simple, 2 Minecraft (older choices count as Stage). */
	public static int voidStyle() {
		if (!ClientSettings.INSTANCE.customMainMenu.isOn()) {
			return 2;
		}
		return INSTANCE.layout.get() == Layout.SIMPLE ? 1 : 0;
	}

	public static void setVoidStyle(int style) {
		ClientSettings.INSTANCE.customMainMenu.set(style != 2);
		if (style != 2) {
			INSTANCE.layout.set(style == 1 ? Layout.SIMPLE : Layout.CLASSIC);
		}
	}

	/** How the Multiplayer screen lists servers. */
	public enum ServerLayout implements dev.mordo.kairo.core.settings.EnumSetting.Labeled {
		LIST("List"),
		COMPACT("Compact"),
		TILES("Tiles");

		private final String label;

		ServerLayout(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public final dev.mordo.kairo.core.settings.EnumSetting<ServerLayout> serverLayout = add(
		new dev.mordo.kairo.core.settings.EnumSetting<>("server_layout", "Server list", ServerLayout.LIST)
			.<dev.mordo.kairo.core.settings.EnumSetting<ServerLayout>>hidden());
	public final BooleanSetting showAddresses = add(new BooleanSetting("show_addresses", "Show server addresses", true)
		.<BooleanSetting>hidden());

	public final TextSetting order = add(new TextSetting("order", "Button order", String.join(",", BUTTONS), 200).hidden());
	/** Widgets of the Custom style (see {@link HomeLayout}); empty = the default layout. */
	public final TextSetting homeLayout = add(new TextSetting("home_layout", "Custom home layout", "", 20000).hidden());
	public final TextSetting hiddenButtons = add(new TextSetting("hidden_buttons", "Hidden buttons", "", 200).hidden());
	public final BooleanSetting showWorldCard = add(new BooleanSetting("world_card", "\"Jump back in\" card", true));
	public final BooleanSetting showServerCard = add(new BooleanSetting("server_card", "\"Quick join\" card", true));
	public final BooleanSetting hideTitleIcons = add(new BooleanSetting("hide_title_icons", "Hide icon buttons (Minecraft style)", false)
		.description("Friends, language and accessibility icons under Realms on Minecraft's title screen"));

	/** {@code panorama}, {@code image:<file>} (PNG/JPEG/BMP/GIF in config/kairo/backgrounds) or {@code cube:<folder>}. */
	public final TextSetting background = add(new TextSetting("background", "Background", VANILLA_BACKGROUND, 200).hidden());
	public final SliderSetting backgroundDim = add(new SliderSetting("background_dim", "Darken", 0.15, 0.0, 0.8, 0.01).percent());
	public final SliderSetting backgroundBlur = add(new SliderSetting("background_blur", "Blur", 0.0, 0.0, 1.0, 0.05).percent());
	public final BooleanSetting backgroundMotion = add(new BooleanSetting("background_motion", "Slow pan", true)
		.description("Images drift and zoom very slowly"));

	private MainMenuSettings() {
		super("main_menu");
	}

	/** The button order, always containing every button exactly once (unknown ids dropped, missing ones appended). */
	public List<String> buttonOrder() {
		Set<String> result = new LinkedHashSet<>();
		for (String id : order.get().split(",")) {
			if (BUTTONS.contains(id.trim())) {
				result.add(id.trim());
			}
		}
		result.addAll(BUTTONS);
		return new ArrayList<>(result);
	}

	public void setButtonOrder(List<String> ids) {
		order.set(String.join(",", ids));
	}

	public boolean isHidden(String id) {
		return Arrays.asList(hiddenButtons.get().split(",")).contains(id);
	}

	public void setHidden(String id, boolean hidden) {
		Set<String> set = new LinkedHashSet<>();
		for (String s : hiddenButtons.get().split(",")) {
			if (!s.isBlank()) {
				set.add(s);
			}
		}
		if (hidden) {
			set.add(id);
		} else {
			set.remove(id);
		}
		hiddenButtons.set(String.join(",", set));
	}
}
