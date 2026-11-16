package dev.mordo.kairo.api;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.SettingHolder;
import dev.mordo.kairo.core.settings.Choice;
import dev.mordo.kairo.core.settings.Setting;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.EntrypointContainer;
import net.fabricmc.loader.api.metadata.CustomValue;
import net.fabricmc.loader.api.metadata.ModMetadata;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Loads Kairo addons (see {@link KairoAddon}) and keeps what they registered. An addon made for newer sockets than
 * this Kairo has, or not made for this edition, is skipped with a line in the log; one that throws while registering
 * is skipped too. Nothing an addon does here can stop Kairo from starting.
 */
public final class Addons {
	/** The version of the sockets in {@link AddonRegistrar}. Raised only when an addon made for the old one could break. */
	public static final int API_VERSION = 1;
	public static final String ENTRYPOINT = "kairo:addon";
	/** The chooser's value for "Kairo's own look". */
	public static final String DEFAULT_LOOK = "default";

	/** An addon that was loaded: for the About page and the log. */
	public record Loaded(String id, String name, String version) {
	}

	/** The saved options of one menu or HUD look. */
	private static final class LookSettings extends SettingHolder {
		LookSettings(String holderId, List<Setting<?>> settings) {
			super(holderId);
			for (Setting<?> setting : settings) {
				add(setting);
			}
		}
	}

	private static final List<Loaded> LOADED = new ArrayList<>();
	private static final Map<String, MenuLook> LOOKS = new LinkedHashMap<>();
	private static final Set<String> BROKEN = new LinkedHashSet<>();
	private static final Map<String, HudLook> HUD_LOOKS = new LinkedHashMap<>();
	private static final Set<String> BROKEN_HUD = new LinkedHashSet<>();
	private static final Map<String, HomeMenu> HOMES = new LinkedHashMap<>();
	private static final List<Tab> TABS = new ArrayList<>();

	/** A tab an addon added to Visuals. */
	public record Tab(String addon, String name, dev.mordo.kairo.gui.Icon icon, List<Setting<?>> settings) {
	}
	private static final Set<String> BROKEN_HOME = new LinkedHashSet<>();
	/** The chooser's value for "Kairo's ring". */
	public static final String RING = "ring";

	private Addons() {
	}

	/** Called once while Kairo starts: after its own mods are registered, before the settings are loaded. */
	public static void load() {
		List<EntrypointContainer<KairoAddon>> all = FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, KairoAddon.class);
		// Addons that don't go together: one that lists another in "conflicts" wins, and the other stays off.
		Set<String> present = new LinkedHashSet<>();
		for (EntrypointContainer<KairoAddon> container : all) {
			present.add(addonId(container.getProvider().getMetadata().getId()));
		}
		java.util.Map<String, String> pushedOut = new java.util.LinkedHashMap<>();
		for (EntrypointContainer<KairoAddon> container : all) {
			ModMetadata meta = container.getProvider().getMetadata();
			CustomValue custom = meta.getCustomValue(ENTRYPOINT);
			if (custom != null && custom.getType() == CustomValue.CvType.OBJECT && custom.getAsObject().containsKey("conflicts")) {
				for (CustomValue other : custom.getAsObject().get("conflicts").getAsArray()) {
					String otherId = other.getAsString();
					if (present.contains(otherId) && !pushedOut.containsKey(addonId(meta.getId()))) {
						pushedOut.put(otherId, meta.getName());
					}
				}
			}
		}
		for (EntrypointContainer<KairoAddon> container : all) {
			ModMetadata meta = container.getProvider().getMetadata();
			String id = meta.getId();
			String winner = pushedOut.get(addonId(id));
			if (winner != null) {
				Kairo.LOGGER.warn("Addon {} doesn't go together with {}: not loaded (turn one of them off in the launcher)", meta.getName(), winner);
				continue;
			}
			try {
				int api = 1;
				List<String> editions = List.of("kairo", "void");
				CustomValue custom = meta.getCustomValue(ENTRYPOINT);
				if (custom != null && custom.getType() == CustomValue.CvType.OBJECT) {
					CustomValue.CvObject object = custom.getAsObject();
					if (object.containsKey("api")) {
						api = object.get("api").getAsNumber().intValue();
					}
					if (object.containsKey("editions")) {
						List<String> named = new ArrayList<>();
						for (CustomValue edition : object.get("editions").getAsArray()) {
							named.add(edition.getAsString());
						}
						editions = named;
					}
				}
				if (api > API_VERSION) {
					Kairo.LOGGER.warn("Addon {} needs a newer Kairo (addon sockets {}, this Kairo has {}): not loaded", id, api, API_VERSION);
					continue;
				}
				if (!editions.contains(Edition.isVoid() ? "void" : "kairo")) {
					Kairo.LOGGER.info("Addon {} isn't made for {}: not loaded", id, Edition.isVoid() ? "Kairo Void" : "Kairo");
					continue;
				}
				container.getEntrypoint().register(new Registrar(id));
				LOADED.add(new Loaded(id, meta.getName(), meta.getVersion().getFriendlyString()));
				Kairo.LOGGER.info("Addon loaded: {} {}", meta.getName(), meta.getVersion().getFriendlyString());
			} catch (Throwable t) {
				Kairo.LOGGER.error("Addon {} failed to load and was skipped", id, t);
			}
		}
	}

	/** An addon's own id from its mod id ("kairo-addon-themes" is "themes"). */
	private static String addonId(String modId) {
		return modId.startsWith("kairo-addon-") ? modId.substring("kairo-addon-".length()) : modId;
	}

	private record Registrar(String addon) implements AddonRegistrar {
		@Override
		public int apiVersion() {
			return API_VERSION;
		}

		@Override
		public boolean isVoid() {
			return Edition.isVoid();
		}

		@Override
		public void menuLook(MenuLook look) {
			String id = look.id();
			if (!id.matches("[a-z0-9][a-z0-9-]{0,39}") || id.equals(DEFAULT_LOOK)) {
				throw new IllegalArgumentException("Bad menu look id '" + id + "' from " + addon);
			}
			if (LOOKS.putIfAbsent(id, look) != null) {
				throw new IllegalStateException("Menu look '" + id + "' is already registered (" + addon + ")");
			}
			// One theme can be both a menu look and a HUD look (the same object): its options are saved once.
			if (!look.settings().isEmpty() && HUD_LOOKS.get(id) != look) {
				ModuleManager.registerHolder(new LookSettings("addon_look_" + id, look.settings()));
			}
		}

		@Override
		public void hudLook(HudLook look) {
			String id = look.id();
			if (!id.matches("[a-z0-9][a-z0-9-]{0,39}") || id.equals(DEFAULT_LOOK)) {
				throw new IllegalArgumentException("Bad HUD look id '" + id + "' from " + addon);
			}
			if (HUD_LOOKS.putIfAbsent(id, look) != null) {
				throw new IllegalStateException("HUD look '" + id + "' is already registered (" + addon + ")");
			}
			if (!look.settings().isEmpty() && LOOKS.get(id) != look) {
				ModuleManager.registerHolder(new LookSettings("addon_hud_look_" + id, look.settings()));
			}
		}

		@Override
		public void homeMenu(HomeMenu home) {
			String id = home.id();
			if (!id.matches("[a-z0-9][a-z0-9-]{0,39}") || id.equals(RING)) {
				throw new IllegalArgumentException("Bad home menu id '" + id + "' from " + addon);
			}
			if (HOMES.putIfAbsent(id, home) != null) {
				throw new IllegalStateException("Home menu '" + id + "' is already registered (" + addon + ")");
			}
			if (!home.settings().isEmpty()) {
				ModuleManager.registerHolder(new LookSettings("addon_home_" + id, home.settings()));
			}
		}

		@Override
		public void visualsTab(String name, dev.mordo.kairo.gui.Icon icon, List<Setting<?>> settings) {
			if (name == null || name.isBlank() || name.length() > 30) {
				throw new IllegalArgumentException("Bad Visuals tab name from " + addon);
			}
			List<Setting<?>> copy = List.copyOf(settings);
			TABS.add(new Tab(addon, name, icon == null ? dev.mordo.kairo.gui.Icon.SPARKLE : icon, copy));
			ModuleManager.registerHolder(new LookSettings("addon_tab_" + addonId(addon) + "_" + TABS.size(), copy));
		}

		@Override
		public void mod(Module mod) {
			ModuleManager.register(mod);
		}

		@Override
		public void unlock(String feature) {
			Features.unlock(feature);
		}
	}

	/**
	 * Called once the settings are loaded: a look seen for the first time (its addon was just installed) becomes the
	 * menu look (or HUD look), unless the player already picked another one. After that the choice is theirs.
	 */
	public static void afterSettingsLoaded() {
		ClientSettings settings = ClientSettings.INSTANCE;
		pickNew(LOOKS.keySet(), settings.knownMenuLooks, settings.menuLook, "menu look");
		pickNew(HUD_LOOKS.keySet(), settings.knownHudLooks, settings.hudLook, "HUD look");
		pickNew(HOMES.keySet(), settings.knownHomeMenus, settings.homeMenu, "home menu");
	}

	private static void pickNew(Set<String> ids, dev.mordo.kairo.core.settings.TextSetting knownSetting,
								dev.mordo.kairo.core.settings.ChoiceSetting chosen, String what) {
		Set<String> known = new LinkedHashSet<>(Arrays.asList(knownSetting.get().split(",")));
		known.remove("");
		boolean changed = false;
		for (String id : ids) {
			if (known.add(id)) {
				changed = true;
				// Picked once, unless the player chose something else that is still there.
				if (chosen.get().equals(chosen.getDefault()) || !ids.contains(chosen.get())) {
					chosen.set(id);
					Kairo.LOGGER.info("New {} '{}' is now in use (change it in Visuals)", what, id);
				}
			}
		}
		if (changed) {
			knownSetting.set(String.join(",", known));
		}
	}

	public static List<Loaded> loaded() {
		return Collections.unmodifiableList(LOADED);
	}

	public static boolean hasMenuLooks() {
		return !LOOKS.isEmpty();
	}

	/** The chooser's options: Kairo's own look first, then every look addons added. */
	public static List<Choice> menuLookChoices() {
		List<Choice> choices = new ArrayList<>();
		choices.add(new Choice(DEFAULT_LOOK, Edition.isVoid() ? "Kairo Void (flat)" : "Kairo (glass)", ""));
		for (MenuLook look : LOOKS.values()) {
			choices.add(new Choice(look.id(), look.name(), "Addons"));
		}
		return choices;
	}

	/** The options of the look in use (empty for Kairo's own). */
	public static List<Setting<?>> menuLookSettings() {
		List<Setting<?>> all = new ArrayList<>();
		for (MenuLook look : LOOKS.values()) {
			for (Setting<?> setting : look.settings()) {
				all.add(setting);
			}
		}
		return all;
	}

	/** The look to draw menus with, or null for Kairo's own (also when the chosen one's addon is gone or broke). */
	public static @Nullable MenuLook activeLook() {
		if (LOOKS.isEmpty()) {
			return null;
		}
		String id = ClientSettings.INSTANCE.menuLook.get();
		if (id.equals(DEFAULT_LOOK) || BROKEN.contains(id)) {
			return null;
		}
		return LOOKS.get(id);
	}

	/** A look threw while drawing: Kairo's own look takes over until the game is restarted. */
	public static void lookFailed(MenuLook look, Throwable error) {
		if (BROKEN.add(look.id())) {
			Kairo.LOGGER.error("Menu look '{}' failed while drawing; using Kairo's own look for now", look.id(), error);
		}
	}

	/** Every menu look addons added, in the order they were added. */
	public static List<MenuLook> menuLooks() {
		return List.copyOf(LOOKS.values());
	}

	/** Every HUD look addons added, in the order they were added. */
	public static List<HudLook> hudLooks() {
		return List.copyOf(HUD_LOOKS.values());
	}

	public static boolean hasHudLooks() {
		return !HUD_LOOKS.isEmpty();
	}

	/** The HUD look chooser's options: Kairo's own look first, then every HUD look addons added. */
	public static List<Choice> hudLookChoices() {
		List<Choice> choices = new ArrayList<>();
		choices.add(new Choice(DEFAULT_LOOK, Edition.isVoid() ? "Kairo Void (HUD style)" : "Kairo (plain)", ""));
		for (HudLook look : HUD_LOOKS.values()) {
			choices.add(new Choice(look.id(), look.name(), "Addons"));
		}
		return choices;
	}

	/** The options of every HUD look (each look shows its own only while it is in use). */
	public static List<Setting<?>> hudLookSettings() {
		List<Setting<?>> all = new ArrayList<>();
		for (HudLook look : HUD_LOOKS.values()) {
			if (LOOKS.get(look.id()) != look) {
				// A theme that is also a menu look already has its options in menuLookSettings().
				all.addAll(look.settings());
			}
		}
		return all;
	}

	/** The look to draw HUD boxes with, or null for Kairo's own (also when the chosen one's addon is gone or broke). */
	public static @Nullable HudLook activeHudLook() {
		if (HUD_LOOKS.isEmpty()) {
			return null;
		}
		String id = ClientSettings.INSTANCE.hudLook.get();
		if (id.equals(DEFAULT_LOOK) || BROKEN_HUD.contains(id)) {
			return null;
		}
		return HUD_LOOKS.get(id);
	}

	/** A HUD look threw while drawing: Kairo's own look takes over until the game is restarted. */
	public static void hudLookFailed(HudLook look, Throwable error) {
		if (BROKEN_HUD.add(look.id())) {
			Kairo.LOGGER.error("HUD look '{}' failed while drawing; using Kairo's own look for now", look.id(), error);
		}
	}

	/** The Visuals tabs addons added, in the order they were added. */
	public static List<Tab> visualsTabs() {
		return java.util.Collections.unmodifiableList(TABS);
	}

	public static boolean hasHomeMenus() {
		return !HOMES.isEmpty();
	}

	/** The "Right Shift opens" chooser: Kairo's ring first, then every home menu addons added. */
	public static List<Choice> homeMenuChoices() {
		List<Choice> choices = new ArrayList<>();
		choices.add(new Choice(RING, "The ring", ""));
		for (HomeMenu home : HOMES.values()) {
			choices.add(new Choice(home.id(), home.name(), "Addons"));
		}
		return choices;
	}

	/** The options of every home menu (each shows its own while it is in use, if it says so). */
	public static List<Setting<?>> homeMenuSettings() {
		List<Setting<?>> all = new ArrayList<>();
		for (HomeMenu home : HOMES.values()) {
			all.addAll(home.settings());
		}
		return all;
	}

	/** The home menu Right Shift opens, or null for the ring (also when the chosen one's addon is gone or broke). */
	public static @Nullable HomeMenu activeHomeMenu() {
		if (HOMES.isEmpty()) {
			return null;
		}
		String id = ClientSettings.INSTANCE.homeMenu.get();
		if (id.equals(RING) || BROKEN_HOME.contains(id)) {
			return null;
		}
		return HOMES.get(id);
	}

	/** A home menu threw: the ring takes over until the game is restarted. */
	public static void homeMenuFailed(HomeMenu home, Throwable error) {
		if (BROKEN_HOME.add(home.id())) {
			Kairo.LOGGER.error("Home menu '{}' failed; using the ring for now", home.id(), error);
		}
	}
}
