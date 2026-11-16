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

	/** The saved options of one menu look. */
	private static final class LookSettings extends SettingHolder {
		LookSettings(MenuLook look) {
			super("addon_look_" + look.id());
			for (Setting<?> setting : look.settings()) {
				add(setting);
			}
		}
	}

	private static final List<Loaded> LOADED = new ArrayList<>();
	private static final Map<String, MenuLook> LOOKS = new LinkedHashMap<>();
	private static final Set<String> BROKEN = new LinkedHashSet<>();

	private Addons() {
	}

	/** Called once while Kairo starts: after its own mods are registered, before the settings are loaded. */
	public static void load() {
		for (EntrypointContainer<KairoAddon> container : FabricLoader.getInstance().getEntrypointContainers(ENTRYPOINT, KairoAddon.class)) {
			ModMetadata meta = container.getProvider().getMetadata();
			String id = meta.getId();
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
			if (!look.settings().isEmpty()) {
				ModuleManager.registerHolder(new LookSettings(look));
			}
		}

		@Override
		public void mod(Module mod) {
			ModuleManager.register(mod);
		}
	}

	/**
	 * Called once the settings are loaded: a look seen for the first time (its addon was just installed) becomes the
	 * menu look, unless the player already picked another one. After that the choice is theirs.
	 */
	public static void afterSettingsLoaded() {
		ClientSettings settings = ClientSettings.INSTANCE;
		Set<String> known = new LinkedHashSet<>(Arrays.asList(settings.knownMenuLooks.get().split(",")));
		known.remove("");
		boolean changed = false;
		for (String id : LOOKS.keySet()) {
			if (known.add(id)) {
				changed = true;
				if (settings.menuLook.get().equals(DEFAULT_LOOK)) {
					settings.menuLook.set(id);
					Kairo.LOGGER.info("New menu look '{}' is now in use (change it in Visuals)", id);
				}
			}
		}
		if (changed) {
			settings.knownMenuLooks.set(String.join(",", known));
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
}
