package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.gui.Icon;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Everything the ring can show. Which ones it shows (in this order, clockwise from the top) is your choice, in
 * Visuals → Ring; the first six are the defaults.
 */
public enum RingSection {
	MODS("Mods", Icon.GRID, "OPEN", "Toggle features and tune each one"),
	SETTINGS("Settings", Icon.SLIDERS, "OPEN", "Keybinds, profiles and client options"),
	PVP("PvP Mode", Icon.SWORD, "CLICK TO TOGGLE", null),
	VISUALS("Visuals", Icon.SPARKLE, "OPEN", "Effects, animations, theme and glass"),
	LAYOUT("Layout", Icon.LAYOUT, "EDIT", "Drag your HUD around, snaps to edges"),
	COSMETICS("Cosmetics", Icon.HANGER, "WARDROBE", "Capes, wings, hats, trails and emotes"),
	WAYPOINTS("Waypoints", Icon.PIN, "OPEN", "Your saved places, with beams and distance"),
	EMOTES("Emotes", Icon.USER, "PICK ONE", "Backflips, sitting, dancing and more"),
	PVP_OPTIONS("PvP options", Icon.SHIELD, "OPEN", "What changes when PvP mode is on"),
	SCREENSHOTS("Screenshots", Icon.CAMERA, "OPEN FOLDER", "Your screenshots folder"),
	/** Kairo Void only: every visual on or off at once. */
	EFFECTS("Effects", Icon.EYE, "CLICK TO TOGGLE", null);

	public static final String DEFAULT = dev.mordo.kairo.core.Edition.isVoid()
		? "MODS,SETTINGS,PVP,EFFECTS,VISUALS,LAYOUT,COSMETICS" : "MODS,SETTINGS,PVP,VISUALS,LAYOUT,COSMETICS";

	/** Whether this edition has the section at all. */
	public boolean available() {
		return this != EFFECTS || dev.mordo.kairo.core.Edition.isVoid();
	}
	public static final int MIN = 3;
	public static final int MAX = 8;

	public final String title;
	public final Icon icon;
	final String kicker;
	private final String description;

	private static String cachedFrom = "";
	private static RingSection[] cached = values();

	RingSection(String title, Icon icon, String kicker, String description) {
		this.title = title;
		this.icon = icon;
		this.kicker = kicker;
		this.description = description;
	}

	String description() {
		if (this == EFFECTS) {
			return dev.mordo.kairo.core.VoidSettings.INSTANCE.master.isOn()
				? "Every visual is on. Click for plain Minecraft."
				: "Visuals are off. Click to bring them all back.";
		}
		if (this == PVP) {
			return PvpMode.isActive()
				? "Calmer visuals for fighting. Right-click for PvP options."
				: "Normal visuals. Click to go PvP, right-click for options.";
		}
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			if (this == VISUALS) {
				return "Combat effects, sky, animations and quality";
			}
		}
		return description;
	}

	/** The segments the ring shows now, in order (from the saved list; unknown names are skipped). */
	public static RingSection[] active() {
		String saved = ClientSettings.INSTANCE.ringSegments.get();
		if (!saved.equals(cachedFrom)) {
			List<RingSection> list = new ArrayList<>();
			for (String name : saved.split(",")) {
				try {
					RingSection s = valueOf(name.trim().toUpperCase(Locale.ROOT));
					if (!list.contains(s) && s.available()) {
						list.add(s);
					}
				} catch (IllegalArgumentException ignored) {
					// a removed section
				}
			}
			if (list.size() < MIN) {
				list.clear();
				for (String name : DEFAULT.split(",")) {
					list.add(valueOf(name));
				}
			}
			cached = list.toArray(new RingSection[0]);
			cachedFrom = saved;
		}
		return cached;
	}

	public static boolean isShown(RingSection section) {
		for (RingSection s : active()) {
			if (s == section) {
				return true;
			}
		}
		return false;
	}

	/** Adds or removes a segment (keeping 3 to 8), in the order of this list. Returns false when it can't. */
	public static boolean toggle(RingSection section) {
		List<RingSection> now = new ArrayList<>(List.of(active()));
		if (now.contains(section)) {
			if (now.size() <= MIN) {
				return false;
			}
			now.remove(section);
		} else {
			if (now.size() >= MAX) {
				return false;
			}
			now.add(section);
			now.sort(java.util.Comparator.comparingInt(Enum::ordinal));
		}
		StringBuilder sb = new StringBuilder();
		for (RingSection s : now) {
			if (!sb.isEmpty()) {
				sb.append(',');
			}
			sb.append(s.name());
		}
		ClientSettings.INSTANCE.ringSegments.set(sb.toString());
		return true;
	}
}