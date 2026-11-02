package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import dev.mordo.kairo.render.Colors;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * An ARGB color, stored in config as "#AARRGGBB". It can also move: <b>Chroma</b> cycles through the rainbow (at the
 * saturation and brightness of the chosen colour) and <b>Gradient</b> flows through 2 to 5 colours you pick. Moving
 * colours are saved as "#AARRGGBB;chroma;speed" or "#AARRGGBB;gradient;speed;#..,#..". {@link #argb()} gives the
 * colour for right now, so everything that reads it animates.
 */
public class ColorSetting extends Setting<Integer> {
	public enum Mode {
		STATIC("Static"),
		CHROMA("Chroma"),
		GRADIENT("Gradient");

		public final String label;

		Mode(String label) {
			this.label = label;
		}
	}

	public static final int MAX_STOPS = 5;

	/** How a mod colour relates to the client accent. */
	public enum AccentRole {
		/** Its own colour, always. */
		NONE,
		/** Uses the accent until you pick a colour for it (Reset brings the accent back). */
		FOLLOW,
		/** Its own colour, but "Accent on every mod" turns it into the accent too. */
		OPTIONAL
	}

	/** How a colour relates to the theme's tints (Visuals → Theme → Tint backgrounds / Tint text). */
	public enum ThemeRole {
		/** Left alone. */
		NONE,
		/** A background (panel, box, button fill): takes a dark tone of the theme colour while tints are on. */
		BACKGROUND,
		/** A light text colour: takes the theme's text tint while its mod follows the theme and it's left at default. */
		TEXT
	}

	/** Set by the client settings: the accent right now, and whether "Accent on every mod" is on. */
	public static java.util.function.IntSupplier accentSource = () -> 0xFF7FD8FF;
	public static java.util.function.BooleanSupplier accentEverywhere = () -> false;
	/** Set by the theme: turns a background colour into its tinted version (or returns it unchanged when tints are off). */
	public static java.util.function.IntUnaryOperator backgroundTinter = c -> c;
	/** Set by the theme: the main text colour right now. */
	public static java.util.function.IntSupplier themeText = () -> 0xFFFFFFFF;

	private AccentRole accentRole = AccentRole.NONE;
	private ThemeRole themeRole = ThemeRole.NONE;
	/** The owning mod's "Follow theme colours" and "Tinted background" switches (always on for client colours). */
	private java.util.function.BooleanSupplier followTheme = () -> false;
	private java.util.function.BooleanSupplier tintBackground = () -> true;

	private final boolean alpha;
	private Mode mode = Mode.STATIC;
	private float speed = 1f;
	private final List<Integer> stops = new ArrayList<>();

	public ColorSetting(String id, String name, int defaultArgb) {
		this(id, name, defaultArgb, true);
	}

	public ColorSetting(String id, String name, int defaultArgb, boolean alpha) {
		super(id, name, alpha ? defaultArgb : (defaultArgb | 0xFF000000));
		this.alpha = alpha;
	}

	public boolean hasAlpha() {
		return alpha;
	}

	/** This colour follows the accent until the player picks one (see {@link AccentRole#FOLLOW}). */
	public ColorSetting followsAccent() {
		accentRole = AccentRole.FOLLOW;
		return this;
	}

	/** This colour turns into the accent when "Accent on every mod" is on. */
	public ColorSetting accentable() {
		accentRole = AccentRole.OPTIONAL;
		return this;
	}

	public AccentRole accentRole() {
		return accentRole;
	}

	/** A background colour: tinted with the theme while "Tint backgrounds" (and its mod's "Tinted background") is on. */
	public ColorSetting themeBackground() {
		themeRole = ThemeRole.BACKGROUND;
		return this;
	}

	/** A light text colour: takes the theme's text tint while its mod follows the theme colours. */
	public ColorSetting themeText() {
		themeRole = ThemeRole.TEXT;
		return this;
	}

	public ThemeRole themeRole() {
		return themeRole;
	}

	/** Connects this colour to its mod's "Follow theme colours" and "Tinted background" switches. */
	public void bindTheme(java.util.function.BooleanSupplier follow, java.util.function.BooleanSupplier tinted) {
		this.followTheme = follow;
		this.tintBackground = tinted;
	}

	/** True when this colour is showing the accent instead of its own value right now. */
	public boolean usesAccent() {
		if (accentRole == AccentRole.NONE) {
			return false;
		}
		if (accentEverywhere.getAsBoolean() || followTheme.getAsBoolean()) {
			return true;
		}
		return accentRole == AccentRole.FOLLOW && mode == Mode.STATIC && getNormal().equals(getDefault()) && !hasPvpOverride();
	}

	/** The colour right now (moves over time in Chroma and Gradient modes, follows the theme where it should). */
	public int argb() {
		if (usesAccent()) {
			// Keep this colour's own transparency.
			int keepAlpha = alpha ? (get() & 0xFF000000) : 0xFF000000;
			return keepAlpha | (accentSource.getAsInt() & 0xFFFFFF);
		}
		int base = get();
		if (themeRole == ThemeRole.TEXT && mode == Mode.STATIC && base == getDefault() && followTheme.getAsBoolean()) {
			return (base & 0xFF000000) | (themeText.getAsInt() & 0xFFFFFF);
		}
		int c = switch (mode) {
			case STATIC -> base;
			case CHROMA -> chroma(base, speed);
			case GRADIENT -> gradient(base, stops, speed);
		};
		if (themeRole == ThemeRole.BACKGROUND && tintBackground.getAsBoolean()) {
			return backgroundTinter.applyAsInt(c);
		}
		return c;
	}

	/** Rainbow at the base colour's saturation and brightness (grey or very dark bases get a vivid rainbow). */
	public static int chroma(int base, float speed) {
		float[] hsv = Colors.toHsv(base);
		float s = hsv[1] < 0.15f ? 0.75f : hsv[1];
		float v = hsv[2] < 0.25f ? 1f : hsv[2];
		double t = System.currentTimeMillis() / 1000.0 * speed;
		return (base & 0xFF000000) | (Colors.hsv((float) ((t * 0.15 + hsv[0]) % 1.0), s, v) & 0xFFFFFF);
	}

	/** Flows through the stops and back to the first, about one stop per 4 seconds at speed 1. */
	public static int gradient(int base, List<Integer> stops, float speed) {
		int n = stops.size();
		if (n == 0) {
			return base;
		}
		if (n == 1) {
			return (base & 0xFF000000) | (stops.getFirst() & 0xFFFFFF);
		}
		double pos = (System.currentTimeMillis() / 1000.0 * speed * 0.25) % n;
		int i = (int) Math.floor(pos);
		float f = (float) (pos - i);
		f = f * f * (3 - 2 * f);
		int c = Colors.lerp(stops.get(i) | 0xFF000000, stops.get((i + 1) % n) | 0xFF000000, f);
		return (base & 0xFF000000) | (c & 0xFFFFFF);
	}

	// ---- moving colours ----

	public Mode mode() {
		return mode;
	}

	public void setMode(Mode mode) {
		if (this.mode == mode) {
			return;
		}
		this.mode = mode;
		if (mode == Mode.GRADIENT && stops.size() < 2) {
			// Start from the colour you had and a second one a third of the way round the colour wheel.
			stops.clear();
			int base = get() | 0xFF000000;
			float[] hsv = Colors.toHsv(base);
			stops.add(base);
			stops.add(Colors.hsv((hsv[0] + 0.33f) % 1f, Math.max(0.5f, hsv[1]), Math.max(0.6f, hsv[2])));
		}
		changed();
	}

	public float speed() {
		return speed;
	}

	public void setSpeed(float speed) {
		this.speed = Math.clamp(speed, 0.1f, 4f);
		changed();
	}

	/** The gradient's colours (a copy). */
	public List<Integer> stops() {
		return new ArrayList<>(stops);
	}

	public void setStops(List<Integer> newStops) {
		stops.clear();
		for (int i = 0; i < Math.min(MAX_STOPS, newStops.size()); i++) {
			stops.add(newStops.get(i) | 0xFF000000);
		}
		changed();
	}

	@Override
	public void resetSilently() {
		super.resetSilently();
		mode = Mode.STATIC;
		speed = 1f;
		stops.clear();
	}

	@Override
	public void reset() {
		boolean moving = mode != Mode.STATIC;
		super.reset();
		if (moving) {
			changed();
		}
	}

	@Override
	protected Integer sanitize(Integer value) {
		return alpha ? value : (value | 0xFF000000);
	}

	public static String toHex(int argb, boolean withAlpha) {
		return withAlpha
			? String.format(Locale.ROOT, "#%08X", argb)
			: String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
	}

	/** Parses "#RGB", "#RRGGBB" or "#AARRGGBB" (the # is optional). Returns null if invalid. */
	public static Integer parseHex(String text) {
		String s = text.trim();
		if (s.startsWith("#")) {
			s = s.substring(1);
		}
		try {
			return switch (s.length()) {
				case 3 -> {
					int r = Integer.parseInt(s.substring(0, 1), 16);
					int g = Integer.parseInt(s.substring(1, 2), 16);
					int b = Integer.parseInt(s.substring(2, 3), 16);
					yield 0xFF000000 | (r * 17) << 16 | (g * 17) << 8 | (b * 17);
				}
				case 6 -> 0xFF000000 | Integer.parseUnsignedInt(s, 16);
				case 8 -> (int) Long.parseLong(s, 16);
				default -> null;
			};
		} catch (NumberFormatException e) {
			return null;
		}
	}

	@Override
	public JsonElement toJson(Integer value) {
		String hex = toHex(value, true);
		return switch (mode) {
			case STATIC -> new JsonPrimitive(hex);
			case CHROMA -> new JsonPrimitive(hex + ";chroma;" + speed);
			case GRADIENT -> {
				List<String> list = new ArrayList<>();
				for (int stop : stops) {
					list.add(toHex(stop, false));
				}
				yield new JsonPrimitive(hex + ";gradient;" + speed + ";" + String.join(",", list));
			}
		};
	}

	@Override
	public Integer fromJson(JsonElement json) {
		String[] parts = json.getAsString().split(";");
		Integer parsed = parseHex(parts[0]);
		if (parsed == null) {
			throw new IllegalArgumentException("bad color " + json);
		}
		if (parts.length >= 2) {
			try {
				Mode m = Mode.valueOf(parts[1].toUpperCase(Locale.ROOT));
				float s = parts.length >= 3 ? Float.parseFloat(parts[2]) : 1f;
				List<Integer> list = new ArrayList<>();
				if (parts.length >= 4) {
					for (String hex : parts[3].split(",")) {
						Integer c = parseHex(hex);
						if (c != null && list.size() < MAX_STOPS) {
							list.add(c | 0xFF000000);
						}
					}
				}
				mode = m;
				speed = Math.clamp(s, 0.1f, 4f);
				stops.clear();
				stops.addAll(list);
			} catch (IllegalArgumentException ignored) {
				// unknown extras: keep it a plain colour
			}
		}
		return parsed;
	}
}
