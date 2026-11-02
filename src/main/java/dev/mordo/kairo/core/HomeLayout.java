package dev.mordo.kairo.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.gui.Icon;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The Custom main menu: widgets placed on a grid of {@link #COLS} x {@link #ROWS} cells, each covering a rectangle of
 * cells. Saved as JSON in {@link MainMenuSettings#homeLayout}.
 */
public final class HomeLayout {
	public static final int COLS = 12;
	public static final int ROWS = 8;

	/** What a widget is. Default and smallest sizes are in cells. */
	public enum Type {
		CLOCK("Clock", Icon.CLOCK, 4, 2, 2, 1),
		CLIENT("Client name", Icon.TOTEM, 5, 2, 3, 1),
		BUTTON("Button", Icon.PLAY, 4, 1, 1, 1),
		WORLDS("Recent worlds", Icon.BOX, 5, 3, 3, 2),
		SERVERS("Servers", Icon.SERVER, 5, 3, 3, 2),
		SERVER("One server", Icon.GLOBE, 4, 2, 2, 1),
		SKIN("Your skin", Icon.USER, 3, 5, 2, 3),
		PVP("PvP switch", Icon.SWORD, 4, 2, 3, 2),
		STATS("Stats", Icon.GAUGE, 3, 2, 2, 2),
		TEXT("Text", Icon.PENCIL, 4, 1, 1, 1);

		public final String label;
		public final Icon icon;
		public final int defaultW;
		public final int defaultH;
		public final int minW;
		public final int minH;

		Type(String label, Icon icon, int defaultW, int defaultH, int minW, int minH) {
			this.label = label;
			this.icon = icon;
			this.defaultW = defaultW;
			this.defaultH = defaultH;
			this.minW = minW;
			this.minH = minH;
		}
	}

	/** One placed widget. {@code props} hold its options (button action, text, chosen servers...). */
	public static final class Tile {
		public Type type;
		public int x;
		public int y;
		public int w;
		public int h;
		public final Map<String, String> props = new LinkedHashMap<>();

		public Tile(Type type, int x, int y, int w, int h) {
			this.type = type;
			this.x = x;
			this.y = y;
			this.w = w;
			this.h = h;
		}

		public Tile with(String key, String value) {
			props.put(key, value);
			return this;
		}

		public String prop(String key, String fallback) {
			return props.getOrDefault(key, fallback);
		}

		public boolean overlaps(int ox, int oy, int ow, int oh) {
			return x < ox + ow && ox < x + w && y < oy + oh && oy < y + h;
		}
	}

	private HomeLayout() {
	}

	/** The layout the Custom style starts with (much like the old Dashboard). */
	public static List<Tile> defaults() {
		List<Tile> tiles = new ArrayList<>();
		tiles.add(new Tile(Type.CLOCK, 0, 0, 4, 2));
		tiles.add(new Tile(Type.BUTTON, 0, 2, 4, 1).with("action", "singleplayer"));
		tiles.add(new Tile(Type.BUTTON, 0, 3, 4, 1).with("action", "multiplayer"));
		tiles.add(new Tile(Type.BUTTON, 0, 4, 4, 1).with("action", "client"));
		tiles.add(new Tile(Type.BUTTON, 0, 5, 2, 1).with("action", "options"));
		tiles.add(new Tile(Type.BUTTON, 2, 5, 2, 1).with("action", "quit"));
		tiles.add(new Tile(Type.PVP, 0, 6, 4, 2));
		tiles.add(new Tile(Type.CLIENT, 4, 0, 5, 2));
		tiles.add(new Tile(Type.WORLDS, 4, 2, 5, 3));
		tiles.add(new Tile(Type.SERVERS, 4, 5, 5, 3));
		tiles.add(new Tile(Type.SKIN, 9, 0, 3, 6));
		tiles.add(new Tile(Type.STATS, 9, 6, 3, 2));
		return tiles;
	}

	public static List<Tile> load() {
		String json = MainMenuSettings.INSTANCE.homeLayout.get();
		if (json == null || json.isBlank()) {
			return defaults();
		}
		try {
			List<Tile> tiles = new ArrayList<>();
			for (JsonElement element : JsonParser.parseString(json).getAsJsonArray()) {
				JsonObject o = element.getAsJsonObject();
				Type type = Type.valueOf(o.get("type").getAsString().toUpperCase(Locale.ROOT));
				Tile tile = new Tile(type, o.get("x").getAsInt(), o.get("y").getAsInt(), o.get("w").getAsInt(), o.get("h").getAsInt());
				if (o.has("props")) {
					for (var entry : o.getAsJsonObject("props").entrySet()) {
						tile.props.put(entry.getKey(), entry.getValue().getAsString());
					}
				}
				clamp(tile);
				tiles.add(tile);
			}
			return tiles;
		} catch (RuntimeException e) {
			Kairo.LOGGER.warn("Couldn't read the custom main menu layout, using the default", e);
			return defaults();
		}
	}

	public static void save(List<Tile> tiles) {
		JsonArray array = new JsonArray();
		for (Tile tile : tiles) {
			JsonObject o = new JsonObject();
			o.addProperty("type", tile.type.name());
			o.addProperty("x", tile.x);
			o.addProperty("y", tile.y);
			o.addProperty("w", tile.w);
			o.addProperty("h", tile.h);
			if (!tile.props.isEmpty()) {
				JsonObject props = new JsonObject();
				tile.props.forEach(props::addProperty);
				o.add("props", props);
			}
			array.add(o);
		}
		MainMenuSettings.INSTANCE.homeLayout.set(array.toString());
	}

	/** Keeps a tile inside the grid and at least its minimum size. */
	public static void clamp(Tile tile) {
		tile.w = Math.clamp(tile.w, tile.type.minW, COLS);
		tile.h = Math.clamp(tile.h, tile.type.minH, ROWS);
		tile.x = Math.clamp(tile.x, 0, COLS - tile.w);
		tile.y = Math.clamp(tile.y, 0, ROWS - tile.h);
	}

	/** True when a tile of this size fits at (x, y) without covering any other tile (except {@code ignore}). */
	public static boolean fits(List<Tile> tiles, Tile ignore, int x, int y, int w, int h) {
		if (x < 0 || y < 0 || x + w > COLS || y + h > ROWS) {
			return false;
		}
		for (Tile other : tiles) {
			if (other != ignore && other.overlaps(x, y, w, h)) {
				return false;
			}
		}
		return true;
	}

	/** The first free spot for a w x h tile (top-left first), or null. Shrinks to the minimum size if needed. */
	public static int[] freeSpot(List<Tile> tiles, Type type) {
		for (int[] size : new int[][]{{type.defaultW, type.defaultH}, {type.minW, type.minH}}) {
			for (int y = 0; y <= ROWS - size[1]; y++) {
				for (int x = 0; x <= COLS - size[0]; x++) {
					if (fits(tiles, null, x, y, size[0], size[1])) {
						return new int[]{x, y, size[0], size[1]};
					}
				}
			}
		}
		return null;
	}
}
