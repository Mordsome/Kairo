package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.HomeLayout;
import dev.mordo.kairo.core.HomeLayout.Tile;
import dev.mordo.kairo.core.HomeLayout.Type;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.core.config.ConfigManager;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.TextField;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Supplier;

/**
 * The "Custom" main menu style: your own home screen made of widgets on a {@link HomeLayout#COLS} x
 * {@link HomeLayout#ROWS} grid (clock, buttons as bars or squares, recent worlds, servers you pick, your skin in 3D,
 * text, a PvP switch, stats...). The pencil opens edit mode: drag widgets to move them, drag the corner to resize,
 * x to remove, the gear for a widget's options, "Add widget" for more. Everything is in reference units (810 tall).
 */
final class DashboardMenu {
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH);
	private static final DateTimeFormatter SECONDS = DateTimeFormatter.ofPattern("ss", Locale.ENGLISH);
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH);
	private static final float M = 44;
	private static final float GAP = 14;
	private static final float TOP = 74;
	private static final float BOTTOM_SPACE = 70;
	private static final String[] ACTIONS = {"singleplayer", "multiplayer", "client", "options", "quit"};

	private final MainMenuScreen parent;
	private final Map<String, MenuRow> actions;
	private List<Tile> tiles = new ArrayList<>();
	private final Map<Tile, TileUi> ui = new IdentityHashMap<>();
	private final Animation intro = new Animation(0);

	// Data.
	private final List<LevelSummary> worlds = new ArrayList<>();
	private boolean worldsLoaded;
	private final List<ServerData> savedServers = new ArrayList<>();
	private @Nullable ServerPings pings;
	private final Animation pvpAnim = new Animation(PvpMode.isActive() ? 1 : 0);

	// Skin.
	private Model.@Nullable Simple wide;
	private Model.@Nullable Simple slim;
	private @Nullable Supplier<PlayerSkin> skin;
	private float yaw = 25;
	private float pitch = -5;
	private boolean turningSkin;
	private long lastFrame;

	// Grid (from the last frame).
	private float cellW;
	private float cellH;
	private float viewW = 1440;
	private float viewH = 810;

	/** Click areas from the last frame (switches, skins, edit handles). Later ones win. */
	private record Hit(float x, float y, float w, float h, Runnable onPress) {
		boolean contains(float px, float py) {
			return px >= x && px <= x + w && py >= y && py <= y + h;
		}
	}

	private final List<Hit> hits = new ArrayList<>();

	// Editing.
	private boolean editing;
	private final Button editButton;
	private final Button addButton;
	private final Button resetButton;
	private final Button doneButton;
	private @Nullable Tile moving;
	private @Nullable Tile resizing;
	private float grabX;
	private float grabY;
	private float dragX;
	private float dragY;
	private int candX;
	private int candY;
	private int candW;
	private int candH;
	private boolean candOk;
	private boolean picking;
	private final List<Button> pickerButtons = new ArrayList<>();
	private @Nullable Tile optionsFor;
	private final List<Button> optionButtons = new ArrayList<>();
	private final List<String> optionLabels = new ArrayList<>();
	private final List<Integer> optionRows = new ArrayList<>();
	private @Nullable TextField textField;
	private final Button optionsDone;

	/** Per-tile buttons (each widget has its own, so two worlds widgets don't share them). */
	private final class TileUi {
		final Button[] play = new Button[6];
		final Button[] join = new Button[6];
		final List<ServerData> shown = new ArrayList<>();
		@Nullable Button more;
		@Nullable Button pvpOptions;
		@Nullable HomeButton button;
		@Nullable ServerData single;
	}

	DashboardMenu(MainMenuScreen parent, Map<String, MenuRow> actions) {
		this.parent = parent;
		this.actions = actions;
		editButton = new Button("Edit", Icon.PENCIL, Button.Style.GHOST, this::startEditing).textSize(11);
		addButton = new Button("Add widget", Icon.PLUS, Button.Style.PRIMARY, () -> {
			picking = true;
			optionsFor = null;
		}).textSize(12);
		resetButton = new Button("Reset", Icon.REFRESH, Button.Style.SUBTLE, () -> {
			tiles = HomeLayout.defaults();
			ui.clear();
			save();
			UiSounds.click();
		}).textSize(12);
		doneButton = new Button("Done", Icon.CHECK, Button.Style.PRIMARY, this::stopEditing).textSize(12);
		optionsDone = new Button("Done", Icon.CHECK, Button.Style.PRIMARY, () -> {
			optionsFor = null;
			textField = null;
			save();
		}).textSize(12);
		for (Type type : Type.values()) {
			pickerButtons.add(new Button(type.label, type.icon, Button.Style.SUBTLE, () -> addWidget(type)).textSize(12));
		}
	}

	// ---- data ----

	void refresh() {
		tiles = HomeLayout.load();
		ui.clear();
		intro.set(0).animateTo(1, 560, Easing.UI);
		Minecraft mc = Minecraft.getInstance();
		worldsLoaded = false;
		worlds.clear();
		try {
			mc.getLevelSource().loadLevelSummaries(mc.getLevelSource().findLevelCandidates())
				.thenAcceptAsync(list -> {
					worlds.clear();
					list.stream().filter(s -> !s.isDisabled() && s.primaryActionActive())
						.sorted(Comparator.comparingLong(LevelSummary::getLastPlayed).reversed()).limit(6).forEach(worlds::add);
					worldsLoaded = true;
				}, mc)
				.exceptionally(t -> {
					worldsLoaded = true;
					return null;
				});
		} catch (Exception e) {
			Kairo.LOGGER.warn("Couldn't list worlds", e);
			worldsLoaded = true;
		}
		savedServers.clear();
		ServerList list = new ServerList(mc);
		list.load();
		for (int i = 0; i < list.size(); i++) {
			savedServers.add(list.get(i));
		}
		if (!savedServers.isEmpty() && mc.allowsMultiplayer()) {
			if (pings == null) {
				pings = new ServerPings();
			}
			for (int i = 0; i < Math.min(12, savedServers.size()); i++) {
				pings.refresh(savedServers.get(i), () -> {
				});
			}
		}
		skin = mc.getSkinManager().createLookup(mc.getGameProfile(), false);
	}

	void tick() {
		if (pings != null) {
			pings.tick();
		}
	}

	void close() {
		if (pings != null) {
			pings.close();
			pings = null;
		}
	}

	private void save() {
		HomeLayout.save(tiles);
	}

	private TileUi ui(Tile tile) {
		return ui.computeIfAbsent(tile, t -> new TileUi());
	}

	// ---- editing ----

	boolean editing() {
		return editing;
	}

	void startEditing() {
		editing = true;
		picking = false;
		optionsFor = null;
		UiSounds.click();
	}

	void stopEditing() {
		editing = false;
		picking = false;
		optionsFor = null;
		textField = null;
		moving = null;
		resizing = null;
		save();
		UiSounds.click();
	}

	/** Esc while editing: close a popup, else leave edit mode. Returns true when it handled it. */
	boolean escape() {
		if (!editing) {
			return false;
		}
		if (picking || optionsFor != null) {
			picking = false;
			optionsFor = null;
			textField = null;
			save();
		} else {
			stopEditing();
		}
		return true;
	}

	private void addWidget(Type type) {
		int[] spot = HomeLayout.freeSpot(tiles, type);
		picking = false;
		if (spot == null) {
			Toasts.show("No room for it: make space or shrink a widget first", Theme.accent());
			return;
		}
		Tile tile = new Tile(type, spot[0], spot[1], spot[2], spot[3]);
		if (type == Type.BUTTON) {
			tile.with("action", "singleplayer");
		} else if (type == Type.TEXT) {
			tile.with("text", "Your text here");
		}
		tiles.add(tile);
		save();
		UiSounds.click();
	}

	private void openOptions(Tile tile) {
		optionsFor = tile;
		picking = false;
		textField = null;
		if (tile.type == Type.TEXT) {
			textField = new TextField("Your text", Icon.PENCIL).maxLength(120).onChange(text -> tile.props.put("text", text));
			textField.setText(tile.prop("text", ""));
		}
		rebuildOptions();
		UiSounds.click();
	}

	/** Chips for the open widget's options; the chosen one in each row is highlighted. */
	private void rebuildOptions() {
		optionButtons.clear();
		optionLabels.clear();
		optionRows.clear();
		Tile tile = optionsFor;
		if (tile == null) {
			return;
		}
		switch (tile.type) {
			case BUTTON -> {
				optionRow("Opens");
				for (String id : ACTIONS) {
					chip(buttonTitle(id), MainMenuSettingsScreen.iconOf(id), tile.prop("action", "singleplayer").equals(id), () -> tile.props.put("action", id));
				}
				optionRow("Shape");
				for (String[] s : new String[][]{{"auto", "Fit the size"}, {"bar", "Bar"}, {"square", "Square"}}) {
					chip(s[1], null, tile.prop("shape", "auto").equals(s[0]), () -> tile.props.put("shape", s[0]));
				}
			}
			case TEXT -> {
				optionRow("Size");
				for (String[] s : new String[][]{{"s", "Small"}, {"m", "Medium"}, {"l", "Large"}, {"xl", "Huge"}}) {
					chip(s[1], null, tile.prop("size", "m").equals(s[0]), () -> tile.props.put("size", s[0]));
				}
				optionRow("Align");
				for (String[] s : new String[][]{{"left", "Left"}, {"center", "Centre"}}) {
					chip(s[1], null, tile.prop("align", "left").equals(s[0]), () -> tile.props.put("align", s[0]));
				}
				optionRow("Background");
				for (String[] s : new String[][]{{"glass", "Glass"}, {"none", "None"}}) {
					chip(s[1], null, tile.prop("panel", "glass").equals(s[0]), () -> tile.props.put("panel", s[0]));
				}
			}
			case CLOCK -> {
				optionRow("Show");
				chip("Seconds", null, !tile.prop("seconds", "on").equals("off"),
					() -> tile.props.put("seconds", tile.prop("seconds", "on").equals("off") ? "on" : "off"));
				chip("Date", null, !tile.prop("date", "on").equals("off"),
					() -> tile.props.put("date", tile.prop("date", "on").equals("off") ? "on" : "off"));
				chip("Greeting", null, !tile.prop("greeting", "on").equals("off"),
					() -> tile.props.put("greeting", tile.prop("greeting", "on").equals("off") ? "on" : "off"));
			}
			case SERVERS -> {
				optionRow("Show these (none picked = your first servers)");
				Set<String> picked = pickedServers(tile);
				for (ServerData data : savedServers) {
					chip(data.name, Icon.SERVER, picked.contains(data.ip), () -> {
						Set<String> now = pickedServers(tile);
						if (!now.remove(data.ip)) {
							now.add(data.ip);
						}
						tile.props.put("servers", String.join("|", now));
					});
				}
			}
			case SERVER -> {
				optionRow("Server");
				String chosen = tile.prop("server", savedServers.isEmpty() ? "" : savedServers.getFirst().ip);
				for (ServerData data : savedServers) {
					chip(data.name, Icon.SERVER, chosen.equals(data.ip), () -> tile.props.put("server", data.ip));
				}
			}
			default -> {
			}
		}
	}

	private void optionRow(String label) {
		optionLabels.add(label);
		optionRows.add(optionButtons.size());
	}

	private void chip(String label, @Nullable Icon icon, boolean on, Runnable change) {
		optionButtons.add(new Button(label, icon, on ? Button.Style.PRIMARY : Button.Style.SUBTLE, () -> {
			change.run();
			UiSounds.click();
			rebuildOptions();
		}).textSize(11.5f));
	}

	private static boolean hasOptions(Type type) {
		return type == Type.BUTTON || type == Type.TEXT || type == Type.CLOCK || type == Type.SERVERS || type == Type.SERVER;
	}

	private static Set<String> pickedServers(Tile tile) {
		Set<String> set = new LinkedHashSet<>();
		for (String ip : tile.prop("servers", "").split("\\|")) {
			if (!ip.isBlank()) {
				set.add(ip);
			}
		}
		return set;
	}

	// ---- drawing ----

	void drawBackground(GuiGraphicsExtractor g) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, Colors.argb(0.42f, 0x05070B));
		Shapes.gradientRect(g, 0, 0, w, h * 0.3f, 0, Colors.argb(0.4f, 0x05070B), Colors.argb(0, 0x05070B));
		Shapes.gradientRect(g, 0, h * 0.7f, w, h * 0.3f, 0, Colors.argb(0, 0x05070B), Colors.argb(0.55f, 0x05070B));
	}

	private float cellX(int col) {
		return M + col * (cellW + GAP);
	}

	private float cellY(int row) {
		return TOP + row * (cellH + GAP);
	}

	private float spanW(int cols) {
		return cols * cellW + (cols - 1) * GAP;
	}

	private float spanH(int rows) {
		return rows * cellH + (rows - 1) * GAP;
	}

	/**
	 * Draws the home screen. {@code put.accept(widget, live)} draws a widget (and gives it input when live).
	 */
	void draw(GuiGraphicsExtractor g, float viewWidth, float viewHeight, float mx, float my, float fade, BiConsumer<Widget, Boolean> put) {
		viewW = viewWidth;
		viewH = viewHeight;
		cellW = (viewWidth - M * 2 - GAP * (HomeLayout.COLS - 1)) / HomeLayout.COLS;
		cellH = (viewHeight - TOP - BOTTOM_SPACE - GAP * (HomeLayout.ROWS - 1)) / HomeLayout.ROWS;
		hits.clear();
		float a = fade * intro.value();
		boolean popup = picking || optionsFor != null;
		boolean tilesLive = !editing;

		if (editing) {
			// The grid's cells, faintly.
			for (int r = 0; r < HomeLayout.ROWS; r++) {
				for (int c = 0; c < HomeLayout.COLS; c++) {
					Shapes.rect(g, cellX(c), cellY(r), cellW, cellH, 10, Colors.argb(0.05f * fade, 0xFFFFFF), Colors.argb(0.08f * fade, 0xFFFFFF), 1);
				}
			}
		}
		for (Tile tile : tiles) {
			if (tile == moving) {
				continue;
			}
			float w = spanW(tile.w);
			float h = spanH(tile.h);
			if (tile == resizing) {
				w = Math.max(spanW(tile.type.minW), dragX - cellX(tile.x));
				h = Math.max(spanH(tile.type.minH), dragY - cellY(tile.y));
			}
			drawTile(g, tile, cellX(tile.x), cellY(tile.y), w, h, mx, my, a, tilesLive, put);
			if (editing) {
				drawEditChrome(g, tile, cellX(tile.x), cellY(tile.y), w, h, mx, my, fade, popup);
			}
		}
		if (editing) {
			if (moving != null || resizing != null) {
				// Where it would land.
				int color = candOk ? Theme.accent() : 0xFFFF6B6B;
				Shapes.rect(g, cellX(candX), cellY(candY), spanW(candW), spanH(candH), 16, Colors.multiplyAlpha(Colors.withAlpha(color, 0.12f), fade),
					Colors.multiplyAlpha(color, fade), 2);
			}
			if (moving != null) {
				float w = spanW(moving.w);
				float h = spanH(moving.h);
				drawTile(g, moving, dragX - grabX, dragY - grabY, w, h, mx, my, a * 0.85f, false, put);
			}
			drawEditBar(g, mx, my, fade, popup, put);
			if (picking) {
				drawPicker(g, mx, my, fade, put);
			} else if (optionsFor != null) {
				drawOptions(g, mx, my, fade, put);
			}
		} else {
			float bw = editButton.preferredWidth() + 8;
			editButton.bounds(M, 26, bw, 30);
			put.accept(editButton, true);
		}
	}

	private void drawEditBar(GuiGraphicsExtractor g, float mx, float my, float fade, boolean popup, BiConsumer<Widget, Boolean> put) {
		float cy = 40;
		Fonts.draw(g, Typeface.MONO_BOLD, "EDITING  ·  DRAG TO MOVE  ·  CORNER TO RESIZE", M, cy, 8.5f, Colors.multiplyAlpha(Theme.accent(), fade),
			Fonts.Align.LEFT, 2f, false);
		float x = viewW / 2 + 40;
		for (Button b : new Button[]{addButton, resetButton, doneButton}) {
			float bw = b.preferredWidth() + 12;
			b.bounds(x, cy - 17, bw, 34);
			put.accept(b, !popup);
			x += bw + 10;
		}
	}

	/** Outline, x, gear and the resize grip on a tile in edit mode. */
	private void drawEditChrome(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float mx, float my, float fade, boolean popup) {
		boolean over = mx >= x && mx <= x + w && my >= y && my <= y + h && !popup;
		Shapes.rect(g, x, y, w, h, 18, Colors.argb(0.25f * fade, 0x000000), Colors.multiplyAlpha(over ? Theme.accent() : 0x66FFFFFF, fade), over ? 2 : 1.25f);
		// What it is and its size, as a small badge in the bottom-left corner.
		String badge = (w >= 150 ? tile.type.label.toUpperCase(Locale.ROOT) + "  " : "") + tile.w + "×" + tile.h;
		float bw = Fonts.width(Typeface.MONO_BOLD, badge, 8, 1.5f) + 14;
		Shapes.rect(g, x + 8, y + h - 26, bw, 18, 9, Colors.multiplyAlpha(Theme.tintBackground(0xCC0C1018), fade), Colors.multiplyAlpha(0x33FFFFFF, fade), 1);
		Fonts.draw(g, Typeface.MONO_BOLD, badge, x + 15, y + h - 17, 8, Theme.text(0.8f, fade), Fonts.Align.LEFT, 1.5f, false);
		if (popup) {
			return;
		}
		// Body: move.
		hits.add(new Hit(x, y, w, h, () -> {
			moving = tile;
			grabX = dragX - x;
			grabY = dragY - y;
			candX = tile.x;
			candY = tile.y;
			candW = tile.w;
			candH = tile.h;
			candOk = true;
			UiSounds.click();
		}));
		// Resize grip (bottom-right).
		float gx = x + w - 22;
		float gy = y + h - 22;
		for (int i = 0; i < 3; i++) {
			Shapes.line(g, gx + 6 + i * 4, gy + 18, gx + 18, gy + 6 + i * 4, 1.6f, Colors.multiplyAlpha(0xCCFFFFFF, fade));
		}
		hits.add(new Hit(gx - 4, gy - 4, 30, 30, () -> {
			resizing = tile;
			candX = tile.x;
			candY = tile.y;
			candW = tile.w;
			candH = tile.h;
			candOk = true;
			UiSounds.click();
		}));
		// Remove.
		handle(g, x + w - 16, y + 16, Icon.CLOSE, 0xFFFF6B6B, mx, my, fade);
		hits.add(new Hit(x + w - 30, y + 2, 28, 28, () -> {
			tiles.remove(tile);
			ui.remove(tile);
			save();
			UiSounds.click();
		}));
		// Options.
		if (hasOptions(tile.type)) {
			handle(g, x + 16, y + 16, Icon.GEAR, Theme.accent(), mx, my, fade);
			hits.add(new Hit(x + 2, y + 2, 28, 28, () -> openOptions(tile)));
		}
	}

	private static void handle(GuiGraphicsExtractor g, float cx, float cy, Icon icon, int color, float mx, float my, float fade) {
		boolean over = Math.hypot(mx - cx, my - cy) < 12;
		Shapes.circle(g, cx, cy, 11, Colors.multiplyAlpha(over ? color : 0xCC10141C, fade), Colors.multiplyAlpha(color, fade), 1.25f);
		IconRenderer.draw(g, icon, cx, cy, 11, Colors.multiplyAlpha(over ? 0xFF0C1018 : color, fade), 2.2f);
	}

	private void drawPicker(GuiGraphicsExtractor g, float mx, float my, float fade, BiConsumer<Widget, Boolean> put) {
		float w = 640;
		int cols = 3;
		float bw = (w - 48 - (cols - 1) * 10) / cols;
		int rows = (pickerButtons.size() + cols - 1) / cols;
		float h = 84 + rows * 50;
		float x = viewW / 2 - w / 2;
		float y = viewH / 2 - h / 2;
		popupPanel(g, x, y, w, h, fade);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, "Add a widget", x + 24, y + 34, 20, Theme.text(1, fade));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "It goes in the first free space; move and resize it after.", x + 24, y + 58, 11.5f, Theme.text(0.55f, fade));
		for (int i = 0; i < pickerButtons.size(); i++) {
			Button b = pickerButtons.get(i);
			b.bounds(x + 24 + (i % cols) * (bw + 10), y + 76 + (i / cols) * 50, bw, 40);
			put.accept(b, true);
		}
	}

	private void drawOptions(GuiGraphicsExtractor g, float mx, float my, float fade, BiConsumer<Widget, Boolean> put) {
		Tile tile = optionsFor;
		if (tile == null) {
			return;
		}
		float w = 640;
		float x = viewW / 2 - w / 2;
		// Measure: rows of chips that wrap.
		float inner = w - 48;
		List<float[]> placed = new ArrayList<>();
		float cy = 76;
		if (textField != null) {
			cy += 56;
		}
		for (int r = 0; r < optionRows.size(); r++) {
			int from = optionRows.get(r);
			int to = r + 1 < optionRows.size() ? optionRows.get(r + 1) : optionButtons.size();
			cy += 24;
			float cx = 0;
			for (int i = from; i < to; i++) {
				float bw = optionButtons.get(i).preferredWidth() + 10;
				if (cx > 0 && cx + bw > inner) {
					cx = 0;
					cy += 40;
				}
				placed.add(new float[]{cx, cy, bw});
				cx += bw + 8;
			}
			cy += 50;
		}
		float h = cy + 50;
		float y = Math.max(20, viewH / 2 - h / 2);
		popupPanel(g, x, y, w, h, fade);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, tile.type.label, x + 24, y + 34, 20, Theme.text(1, fade));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Changes show right away behind this card.", x + 24, y + 58, 11.5f, Theme.text(0.55f, fade));
		float labelY = y + 76 + (textField != null ? 56 : 0);
		if (textField != null) {
			textField.bounds(x + 24, y + 76, inner, 40);
			put.accept(textField, true);
		}
		for (int r = 0; r < optionRows.size(); r++) {
			Fonts.draw(g, Typeface.MONO_BOLD, optionLabels.get(r).toUpperCase(Locale.ROOT), x + 24, labelY + 8, 8.5f, Theme.text(0.45f, fade),
				Fonts.Align.LEFT, 2f, false);
			int from = optionRows.get(r);
			int to = r + 1 < optionRows.size() ? optionRows.get(r + 1) : optionButtons.size();
			float lastY = labelY;
			for (int i = from; i < to; i++) {
				float[] p = placed.get(i);
				optionButtons.get(i).bounds(x + 24 + p[0], y + p[1], p[2], 32);
				put.accept(optionButtons.get(i), true);
				lastY = y + p[1];
			}
			labelY = lastY + 50;
		}
		if (optionRows.isEmpty() && textField == null) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, "This widget has no options.", x + 24, y + 96, 12.5f, Theme.text(0.6f, fade));
		}
		float dw = optionsDone.preferredWidth() + 12;
		optionsDone.bounds(x + w - 24 - dw, y + h - 48, dw, 34);
		put.accept(optionsDone, true);
	}

	private static void popupPanel(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		Shapes.shadow(g, x, y + 14, w, h, 22, 40, Colors.argb(0.5f * a, 0));
		Shapes.glassRect(g, x, y, w, h, 22, Theme.glassTint(), Theme.PANEL_BORDER, 1.25f, a);
		Shapes.rect(g, x, y, w, h, 22, Colors.argb(0.35f * a, 0x05070B));
	}

	private static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		if (a <= 0.01f) {
			return;
		}
		Shapes.shadow(g, x, y + 10, w, h, 20, 22, Colors.argb(0.28f * a, 0));
		Shapes.glassRect(g, x, y, w, h, 20, Theme.glassTint(), 0x2EFFFFFF, 1.25f, a);
	}

	private static void kicker(GuiGraphicsExtractor g, String text, Icon icon, float x, float y, float a) {
		IconRenderer.draw(g, icon, x + 6, y, 13, Colors.multiplyAlpha(Theme.accent(), a), 2.2f);
		Fonts.draw(g, Typeface.MONO_BOLD, text, x + 20, y, 8.5f, Colors.multiplyAlpha(Theme.accent(), a), Fonts.Align.LEFT, 2f, false);
	}

	private void drawTile(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float mx, float my, float a, boolean live,
						  BiConsumer<Widget, Boolean> put) {
		switch (tile.type) {
			case CLOCK -> {
				panel(g, x, y, w, h, a);
				drawClock(g, tile, x, y, w, h, a);
			}
			case CLIENT -> {
				panel(g, x, y, w, h, a);
				drawName(g, x, y, w, h, a);
			}
			case BUTTON -> {
				TileUi u = ui(tile);
				String id = tile.prop("action", "singleplayer");
				if (u.button == null || !u.button.id.equals(id)) {
					u.button = new HomeButton(id);
				}
				u.button.shape = tile.prop("shape", "auto");
				u.button.bounds(x, y, w, h);
				put.accept(u.button, live);
			}
			case WORLDS -> {
				panel(g, x, y, w, h, a);
				drawWorlds(g, tile, x, y, w, h, a, live, put);
			}
			case SERVERS -> {
				panel(g, x, y, w, h, a);
				drawServers(g, tile, x, y, w, h, a, live, put);
			}
			case SERVER -> {
				panel(g, x, y, w, h, a);
				drawServer(g, tile, x, y, w, h, a, live, put);
			}
			case SKIN -> {
				panel(g, x, y, w, h, a);
				drawSkin(g, x, y, w, h, a, live);
			}
			case PVP -> drawPvp(g, tile, x, y, w, h, a, mx, my, live, put);
			case STATS -> {
				panel(g, x, y, w, h, a);
				drawStats(g, x, y, w, h, a);
			}
			case TEXT -> drawText(g, tile, x, y, w, h, a);
		}
	}

	// ---- widgets ----

	private void drawClock(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float a) {
		LocalDateTime now = LocalDateTime.now();
		Minecraft mc = Minecraft.getInstance();
		boolean greeting = !tile.prop("greeting", "on").equals("off") && h >= 120;
		boolean date = !tile.prop("date", "on").equals("off") && h >= 100;
		boolean seconds = !tile.prop("seconds", "on").equals("off");
		if (greeting) {
			int hour = now.getHour();
			String part = hour < 5 ? "Good night" : hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening";
			String text = Fonts.ellipsize(Typeface.MONO_BOLD, part.toUpperCase(Locale.ROOT) + ", " + mc.getUser().getName().toUpperCase(Locale.ROOT), 8.5f, w - 90);
			kicker(g, text, Icon.CLOCK, x + 22, y + 28, a);
		}
		String time = now.format(TIME);
		float top = greeting ? y + 44 : y + 10;
		float bottomSpace = date ? 40 : 10;
		float size = Math.max(18, Math.min((y + h - bottomSpace - top) * 0.95f, (w - 70) / 2.6f));
		float cy = top + (y + h - bottomSpace - top) / 2;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, time, x + 20, cy, size, Theme.text(1, a));
		float tw = Fonts.width(Typeface.OUTFIT_BOLD, time, size);
		if (seconds) {
			Fonts.draw(g, Typeface.MONO_BOLD, now.format(SECONDS), x + 28 + tw, cy + size * 0.22f, Math.max(10, size * 0.17f), Colors.multiplyAlpha(Theme.accent(), a));
		}
		if (date) {
			Fonts.draw(g, Typeface.OUTFIT_MEDIUM, Fonts.ellipsize(Typeface.OUTFIT_MEDIUM, now.format(DATE), 15, w - 44), x + 24, y + h - 26, 15, Theme.text(0.7f, a));
		}
		if (seconds && w >= 220 && h >= 120) {
			float sec = (now.getSecond() + now.getNano() / 1e9f) / 60f;
			float rx = x + w - 44;
			float ry = y + 44;
			Shapes.ring(g, rx, ry, 16, 2.4f, Colors.argb(0.12f * a, 0xFFFFFF));
			Shapes.arc(g, rx, ry, 16, 2.4f, (float) (-Math.PI / 2), (float) (sec * Math.PI * 2), Colors.multiplyAlpha(Theme.accent(), a));
		}
	}

	private static void drawName(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		boolean tall = h >= 120;
		if (tall) {
			kicker(g, "CLIENT", Icon.TOTEM, x + 22, y + 28, a);
		}
		float size = Math.max(20, Math.min(tall ? h * 0.36f : h * 0.55f, (w - 60) / 4.2f));
		float cy = tall ? y + h * 0.52f : y + h / 2 - (h >= 90 ? 8 : 0);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, ClientInfo.NAME, x + 20, cy, size, Theme.text(1, a));
		float nw = Fonts.width(Typeface.OUTFIT_BOLD, ClientInfo.NAME, size);
		Shapes.circle(g, x + 20 + nw + size * 0.18f, cy + size * 0.22f, Math.max(3, size * 0.09f), Colors.multiplyAlpha(Theme.accent(), a));
		if (h >= 90) {
			Fonts.draw(g, Typeface.OUTFIT_MEDIUM, Fonts.ellipsize(Typeface.OUTFIT_MEDIUM, "Client for Minecraft " + ClientInfo.MC_VERSION + "  ·  "
				+ ClientInfo.SHORT_VERSION, 13, w - 48), x + 24, y + h - 26, 13, Theme.text(0.6f, a));
		}
		float t = (System.currentTimeMillis() % 4000) / 4000f;
		float bw = w * 0.3f;
		float bx = x + 20 + (w - 40 - bw) * (0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2));
		Shapes.rect(g, bx, y + h - 6, bw, 2, 1, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.7f), a));
	}

	private int listRows(float h) {
		return Math.clamp((int) ((h - 50) / 50), 1, 6);
	}

	private void drawWorlds(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float a, boolean live, BiConsumer<Widget, Boolean> put) {
		TileUi u = ui(tile);
		kicker(g, "RECENT WORLDS", Icon.BOX, x + 22, y + 26, a);
		if (u.more == null) {
			u.more = new Button("All worlds", null, Button.Style.GHOST, () -> Minecraft.getInstance().gui.setScreen(new WorldsScreen(parent))).textSize(10.5f);
		}
		if (w >= 260) {
			float mw = u.more.preferredWidth();
			u.more.bounds(x + w - 16 - mw, y + 12, mw, 28);
			put.accept(u.more, live);
		}
		int rows = listRows(h);
		float rowH = (h - 54) / rows;
		if (!worldsLoaded) {
			for (int i = 0; i < rows; i++) {
				shimmer(g, x + 22, y + 48 + i * rowH + 6, w - 44, rowH - 12, a);
			}
			return;
		}
		if (worlds.isEmpty()) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "No worlds yet", x + 24, y + h / 2 + 10, 15, Theme.text(0.8f, a));
			return;
		}
		for (int i = 0; i < Math.min(rows, worlds.size()); i++) {
			LevelSummary world = worlds.get(i);
			float ry = y + 46 + i * rowH;
			float icon = Math.min(40, rowH - 10);
			Favicons.draw(g, Favicons.world(world), Icon.BOX, x + 22, ry + (rowH - icon) / 2, icon, a);
			float tx = x + 32 + icon;
			if (u.play[i] == null) {
				int index = i;
				u.play[i] = new Button("Play", Icon.PLAY, Button.Style.PRIMARY, () -> playWorld(index)).textSize(11);
			}
			float bw = u.play[i].preferredWidth() + 8;
			float textW = w - (tx - x) - 22 - bw - 10;
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, world.getLevelName(), 14, textW), tx, ry + rowH / 2 - 8, 14,
				Theme.text(1, a));
			String mode = world.isHardcore() ? "Hardcore" : MainMenuScreen.capitalize(world.getGameMode().getName());
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, mode + "  ·  " + MainMenuScreen.timeAgo(world.getLastPlayed()), 11, textW),
				tx, ry + rowH / 2 + 10, 11, Theme.text(0.55f, a));
			u.play[i].bounds(x + w - 22 - bw, ry + rowH / 2 - 15, bw, 30);
			put.accept(u.play[i], live);
		}
	}

	/** The servers a Servers widget shows: the picked ones (in your list's order), or your first ones. */
	private List<ServerData> serversFor(Tile tile, int max) {
		Set<String> picked = pickedServers(tile);
		List<ServerData> out = new ArrayList<>();
		for (ServerData data : savedServers) {
			if (picked.isEmpty() || picked.contains(data.ip)) {
				out.add(data);
			}
			if (out.size() >= max) {
				break;
			}
		}
		return out;
	}

	private void drawServers(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float a, boolean live, BiConsumer<Widget, Boolean> put) {
		TileUi u = ui(tile);
		kicker(g, "SERVERS", Icon.SERVER, x + 22, y + 26, a);
		if (u.more == null) {
			u.more = new Button("All servers", null, Button.Style.GHOST, () -> Minecraft.getInstance().gui.setScreen(new ServersScreen(parent))).textSize(10.5f);
		}
		if (w >= 260) {
			float mw = u.more.preferredWidth();
			u.more.bounds(x + w - 16 - mw, y + 12, mw, 28);
			put.accept(u.more, live);
		}
		int rows = listRows(h);
		float rowH = (h - 54) / rows;
		u.shown.clear();
		u.shown.addAll(serversFor(tile, rows));
		if (u.shown.isEmpty() || !Minecraft.getInstance().allowsMultiplayer()) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "No servers yet", x + 24, y + h / 2 + 10, 15, Theme.text(0.8f, a));
			return;
		}
		for (int i = 0; i < u.shown.size(); i++) {
			ServerData data = u.shown.get(i);
			float ry = y + 46 + i * rowH;
			float icon = Math.min(40, rowH - 10);
			Favicons.draw(g, Favicons.server(data), Icon.SERVER, x + 22, ry + (rowH - icon) / 2, icon, a);
			float tx = x + 32 + icon;
			if (u.join[i] == null) {
				int index = i;
				u.join[i] = new Button("Join", Icon.ARROW_RIGHT, Button.Style.PRIMARY, () -> {
					if (index < u.shown.size()) {
						join(u.shown.get(index));
					}
				}).textSize(11);
			}
			float bw = u.join[i].preferredWidth() + 8;
			float textW = w - (tx - x) - 22 - bw - 10;
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, data.name, 14, textW), tx, ry + rowH / 2 - 8, 14,
				Theme.text(1, a));
			MainMenuScreen.drawStatusLine(g, data, tx, ry + rowH / 2 + 10, textW, a);
			u.join[i].bounds(x + w - 22 - bw, ry + rowH / 2 - 15, bw, 30);
			put.accept(u.join[i], live);
		}
	}

	/** One server, big: its icon, name, status and a Join button. */
	private void drawServer(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float a, boolean live, BiConsumer<Widget, Boolean> put) {
		TileUi u = ui(tile);
		String ip = tile.prop("server", "");
		ServerData data = null;
		for (ServerData s : savedServers) {
			if (s.ip.equals(ip)) {
				data = s;
			}
		}
		if (data == null && !savedServers.isEmpty() && ip.isEmpty()) {
			data = savedServers.getFirst();
		}
		if (data == null) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Pick a server (edit mode, gear)", x + 20, y + h / 2, 13, Theme.text(0.7f, a));
			return;
		}
		u.single = data;
		float icon = Math.min(h - 28, 64);
		Favicons.draw(g, Favicons.server(data), Icon.SERVER, x + 16, y + (h - icon) / 2, icon, a);
		float tx = x + 28 + icon;
		if (u.join[0] == null) {
			u.join[0] = new Button("Join", Icon.ARROW_RIGHT, Button.Style.PRIMARY, () -> {
				if (u.single != null) {
					join(u.single);
				}
			}).textSize(11.5f);
		}
		float bw = u.join[0].preferredWidth() + 10;
		boolean side = w - (tx - x) > 180 + bw;
		float textW = side ? w - (tx - x) - bw - 34 : w - (tx - x) - 16;
		float nameY = h >= 100 ? y + h / 2 - 14 : y + h / 2 - 8;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, data.name, 16, textW), tx, nameY, 16, Theme.text(1, a));
		MainMenuScreen.drawStatusLine(g, data, tx, nameY + 22, textW, a);
		if (side) {
			u.join[0].bounds(x + w - 18 - bw, y + h / 2 - 17, bw, 34);
		} else {
			u.join[0].bounds(tx, Math.min(y + h - 40, nameY + 38), bw, 30);
		}
		put.accept(u.join[0], live);
	}

	private void drawSkin(GuiGraphicsExtractor g, float x, float y, float w, float h, float a, boolean live) {
		Minecraft mc = Minecraft.getInstance();
		boolean label = h >= 200;
		if (h >= 160) {
			kicker(g, "YOU", Icon.USER, x + 22, y + 28, a);
		}
		if (label) {
			Fonts.draw(g, Typeface.OUTFIT_BOLD, mc.getUser().getName(), x + w / 2, y + h - 40, 20, Theme.text(1, a), Fonts.Align.CENTER, 0, false);
			Fonts.draw(g, Typeface.MONO_BOLD, "DRAG TO TURN", x + w / 2, y + h - 18, 8, Theme.text(0.35f, a), Fonts.Align.CENTER, 1.5f, false);
		}
		if (live) {
			hits.add(new Hit(x, y, w, h, () -> turningSkin = true));
		}
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		if (now != lastFrame) {
			lastFrame = now;
			if (!turningSkin) {
				yaw += dt * 18;
			}
		}
		if (skin == null || a < 0.6f) {
			return;
		}
		if (wide == null) {
			var models = mc.getEntityModels();
			wide = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER), RenderTypes::entityTranslucent);
			slim = new Model.Simple(models.bakeLayer(ModelLayers.PLAYER_SLIM), RenderTypes::entityTranslucent);
		}
		PlayerSkin current = skin.get();
		Model.Simple model = current.model() == PlayerModelType.SLIM ? slim : wide;
		float unit = parent.unit();
		float top = h >= 160 ? 44 : 10;
		float bottom = label ? 60 : 10;
		int x0 = Math.round((x + 10) * unit);
		int y0 = Math.round((y + top) * unit);
		int x1 = Math.round((x + w - 10) * unit);
		int y1 = Math.round((y + h - bottom) * unit);
		g.pose().pushMatrix();
		g.pose().identity();
		try {
			// A stand-in player (no entity exists at the title screen) so the cosmetics layer draws on it too.
			var state = new net.minecraft.client.renderer.entity.state.AvatarRenderState();
			state.entityType = net.minecraft.world.entity.EntityTypes.PLAYER;
			state.id = dev.mordo.kairo.cosmetics.Cosmetics.PREVIEW_ID;
			state.skin = current;
			state.ageInTicks = (System.nanoTime() % 1_000_000_000_000L) / 1e9f * 20;
			state.boundingBoxWidth = 0.6f;
			state.boundingBoxHeight = 1.8f;
			state.eyeHeight = 1.62f;
			state.bodyRot = 180 + yaw;
			state.yRot = 0;
			state.xRot = 0;
			state.lightCoords = 0xF000F0;
			var cosmetics = dev.mordo.kairo.cosmetics.Cosmetics.get();
			if (cosmetics.isEnabled()) {
				dev.mordo.kairo.cosmetics.Capes.apply(cosmetics, state);
			}
			int size = Math.round(Math.min((y1 - y0) * 0.47f, (x1 - x0) * 0.75f));
			org.joml.Quaternionf tilt = new org.joml.Quaternionf().rotateX((float) Math.toRadians(-8 + pitch * 0.5f));
			org.joml.Quaternionf rotation = new org.joml.Quaternionf().rotateZ((float) Math.PI).mul(tilt);
			org.joml.Vector3f translation = new org.joml.Vector3f(0, state.boundingBoxHeight / 2 + 0.25f, 0);
			g.entity(state, size, translation, rotation, tilt, x0, y0, x1, y1);
		} catch (RuntimeException e) {
			float scale = 0.97f * (y1 - y0) / 2.125f;
			g.skin(model, current.body().texturePath(), scale, pitch, yaw, -1.0625f, x0, y0, x1, y1);
		}
		g.pose().popMatrix();
	}

	private void drawStats(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		kicker(g, "TODAY", Icon.GAUGE, x + 22, y + 26, a);
		int on = 0;
		for (var module : ModuleManager.modules()) {
			if (module.isEnabled()) {
				on++;
			}
		}
		String[][] stats = {
			{String.valueOf(on), "mods on"},
			{String.valueOf(savedServers.size()), savedServers.size() == 1 ? "server" : "servers"},
			{ConfigManager.activeProfile(), "profile"}
		};
		float cw = (w - 44) / stats.length;
		float vy = y + Math.max(58, h * 0.52f);
		for (int i = 0; i < stats.length; i++) {
			float cx = x + 22 + i * cw;
			Fonts.draw(g, Typeface.OUTFIT_BOLD, Fonts.ellipsize(Typeface.OUTFIT_BOLD, stats[i][0], 20, cw - 8), cx, vy, 20, Theme.text(1, a));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, stats[i][1], cx, vy + 24, 11, Theme.text(0.5f, a));
		}
	}

	/** Your own text: size, alignment and an optional glass background. */
	private static void drawText(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float a) {
		if (tile.prop("panel", "glass").equals("glass")) {
			panel(g, x, y, w, h, a);
		}
		String text = tile.prop("text", "");
		if (text.isBlank()) {
			text = "Your text here";
		}
		float size = switch (tile.prop("size", "m")) {
			case "s" -> 13;
			case "l" -> 30;
			case "xl" -> 52;
			default -> 19;
		};
		boolean centre = tile.prop("align", "left").equals("center");
		Typeface face = size >= 28 ? Typeface.OUTFIT_BOLD : Typeface.OUTFIT_SEMIBOLD;
		List<String> lines = Fonts.wrap(face, text, size, w - 40);
		float lineH = size * 1.25f;
		int max = Math.max(1, (int) ((h - 16) / lineH));
		if (lines.size() > max) {
			lines = new ArrayList<>(lines.subList(0, max));
		}
		float top = y + h / 2 - (lines.size() - 1) * lineH / 2;
		for (int i = 0; i < lines.size(); i++) {
			float ly = top + i * lineH;
			if (centre) {
				Fonts.draw(g, face, lines.get(i), x + w / 2, ly, size, Theme.text(1, a), Fonts.Align.CENTER, 0, true);
			} else {
				Fonts.draw(g, face, lines.get(i), x + 20, ly, size, Theme.text(1, a), Fonts.Align.LEFT, 0, true);
			}
		}
	}

	/** PvP mode: a big switch (it and the On/Off label toggle), what it changes, and an Options button under the switch. */
	private void drawPvp(GuiGraphicsExtractor g, Tile tile, float x, float y, float w, float h, float a, float mx, float my, boolean live,
						 BiConsumer<Widget, Boolean> put) {
		TileUi u = ui(tile);
		boolean active = PvpMode.isActive();
		pvpAnim.animateTo(active ? 1 : 0, 300);
		float on = pvpAnim.value();
		int pvp = Theme.pvpAccent();
		if (a > 0.01f) {
			Shapes.shadow(g, x, y + 10, w, h, 20, 22, Colors.argb(0.28f * a, 0));
			Shapes.glassRect(g, x, y, w, h, 20, Theme.glassTint(), Colors.lerp(0x2EFFFFFF, Colors.withAlpha(pvp, 0.7f), on), 1.25f, a);
			if (on > 0.01f) {
				Shapes.rect(g, x, y, w, h, 20, Colors.multiplyAlpha(Colors.withAlpha(pvp, 0.14f * on), a));
			}
		}
		int kickerColor = Colors.lerp(Theme.accent(), pvp, on);
		IconRenderer.draw(g, Icon.SWORD, x + 28, y + 28, 13, Colors.multiplyAlpha(kickerColor, a), 2.2f);
		Fonts.draw(g, Typeface.MONO_BOLD, "PVP MODE", x + 42, y + 28, 8.5f, Colors.multiplyAlpha(kickerColor, a), Fonts.Align.LEFT, 2f, false);
		float titleY = y + Math.min(h * 0.5f, 72);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, active ? "On" : "Off", x + 24, titleY, 34, Colors.multiplyAlpha(Colors.lerp(0xFFF4F8FB, pvp, on), a));
		if (w >= 300) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, active ? "Calmer effects, steady camera, your PvP HUD"
				: "Flip the switch for your PvP setup", 12, w - 140), x + 24, titleY + 30, 12, Theme.text(0.6f, a));
		}
		float sw = 64;
		float sh = 34;
		float sx = x + w - 24 - sw;
		float sy = titleY - sh / 2 - 8;
		boolean overSwitch = live && ((mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh) || (mx >= x + 16 && mx <= x + 106 && my >= titleY - 22 && my <= titleY + 22));
		if (overSwitch) {
			Shapes.shadow(g, sx, sy, sw, sh, sh / 2, 12, Colors.multiplyAlpha(Colors.withAlpha(active ? pvp : Theme.accent(), 0.45f), a));
		}
		Shapes.rect(g, sx, sy, sw, sh, sh / 2, Colors.multiplyAlpha(Colors.lerp(overSwitch ? 0x4DFFFFFF : 0x33FFFFFF, pvp, on), a));
		float knob = sh - 8;
		float kx = sx + 4 + (sw - 8 - knob) * on;
		Shapes.shadow(g, kx, sy + 4, knob, knob, knob / 2, 8, Colors.argb(0.3f * a, 0));
		Shapes.circle(g, kx + knob / 2, sy + sh / 2, knob / 2, Colors.multiplyAlpha(0xFFFFFFFF, a));
		if (live) {
			Runnable toggle = () -> {
				PvpMode.toggle();
				UiSounds.click();
			};
			hits.add(new Hit(sx, sy, sw, sh, toggle));
			hits.add(new Hit(x + 16, titleY - 22, 90, 44, toggle));
		}
		if (u.pvpOptions == null) {
			u.pvpOptions = new Button("Options", Icon.SLIDERS, Button.Style.SUBTLE,
				() -> Minecraft.getInstance().gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::pvp, parent)))
				.textSize(11);
		}
		if (h >= 120) {
			float bw = u.pvpOptions.preferredWidth() + 6;
			u.pvpOptions.bounds(sx + sw - bw, sy + sh + 10, bw, 28);
			put.accept(u.pvpOptions, live);
		}
	}

	private static void shimmer(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		float pulse = 0.06f + 0.04f * (float) Math.sin(System.currentTimeMillis() / 220.0);
		Shapes.rect(g, x, y, w, h, Math.min(8, h / 2), Colors.argb(pulse * a, 0xFFFFFF));
	}

	private void playWorld(int index) {
		if (index < worlds.size()) {
			UiSounds.click();
			Minecraft mc = Minecraft.getInstance();
			mc.createWorldOpenFlows().openWorld(worlds.get(index).getLevelId(), () -> mc.gui.setScreen(parent));
		}
	}

	private void join(ServerData data) {
		UiSounds.click();
		ConnectScreen.startConnecting(parent, Minecraft.getInstance(), ServerAddress.parseString(data.ip), data, false, null);
	}

	static String buttonTitle(String id) {
		return switch (id) {
			case "singleplayer" -> "Singleplayer";
			case "multiplayer" -> "Multiplayer";
			case "client" -> ClientInfo.NAME;
			case "options" -> "Options";
			case "quit" -> "Quit game";
			default -> id;
		};
	}

	private static String buttonSubtitle(String id) {
		return switch (id) {
			case "singleplayer" -> "Play your worlds or make a new one";
			case "multiplayer" -> "Join a server or connect directly";
			case "client" -> "Mods, PvP mode, look and keybinds";
			case "options" -> "Video, controls, sound and more";
			case "quit" -> "See you soon";
			default -> "";
		};
	}

	/** A menu button as a widget: a bar (icon, title, line) or a square tile (big icon, title). */
	private final class HomeButton extends Widget {
		final String id;
		String shape = "auto";

		HomeButton(String id) {
			this.id = id;
		}

		private boolean square() {
			return switch (shape) {
				case "square" -> true;
				case "bar" -> false;
				default -> h >= w * 0.7f;
			};
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			MenuRow row = actions.get(id);
			boolean enabled = row == null || row.isEnabled();
			float hv = hover.value() * (enabled ? 1 : 0);
			int accent = id.equals("quit") ? 0xFFFF7A7A : Theme.accent();
			Shapes.shadow(g, x, y + 8, w, h, 18, 18, Colors.argb((0.22f + 0.1f * hv) * fade, 0));
			Shapes.glassRect(g, x, y, w, h, 18, Theme.glassTint(), Colors.lerp(0x2EFFFFFF, Colors.withAlpha(accent, 0.8f), hv), 1.25f, fade);
			if (hv > 0.01f) {
				Shapes.rect(g, x, y, w, h, 18, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.12f * hv), fade));
			}
			int text = Theme.text(enabled ? 1 : 0.4f, fade);
			Icon icon = MainMenuSettingsScreen.iconOf(id);
			if (square()) {
				float s = Math.min(w, h);
				float iconSize = Math.clamp(s * 0.3f, 18, 46);
				float cy = y + h / 2 - (h >= 90 ? 12 : 0);
				Shapes.circle(g, x + w / 2, cy, iconSize * 0.95f, Colors.multiplyAlpha(Colors.lerp(0x14FFFFFF, Colors.withAlpha(accent, 0.25f), hv), fade));
				IconRenderer.draw(g, icon, x + w / 2, cy, iconSize, Colors.multiplyAlpha(Colors.lerp(0xFFE6EEF6, accent, hv), fade), 2.2f);
				if (h >= 90) {
					Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, buttonTitle(id), 14, w - 16), x + w / 2, y + h - 22, 14,
						text, Fonts.Align.CENTER, 0, false);
				}
			} else {
				float box = Math.min(h - 16, 38);
				Shapes.rect(g, x + 12, y + (h - box) / 2, box, box, 10, Colors.multiplyAlpha(Colors.lerp(0x14FFFFFF, Colors.withAlpha(accent, 0.25f), hv), fade));
				IconRenderer.draw(g, icon, x + 12 + box / 2, y + h / 2, Math.min(18, box * 0.5f), Colors.multiplyAlpha(Colors.lerp(0xFFE6EEF6, accent, hv), fade), 2f);
				float tx = x + 24 + box;
				float tw = w - (tx - x) - 12;
				boolean line = h >= 52 && w >= 280;
				Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, buttonTitle(id), 15, tw), tx, y + h / 2 - (line ? 8 : 0), 15, text);
				if (line) {
					String sub = row != null && !row.isEnabled() ? "Multiplayer is disabled for this account" : buttonSubtitle(id);
					Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, sub, 11, tw), tx, y + h / 2 + 11, 11, Theme.text(0.55f, fade));
				}
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) {
				return false;
			}
			MenuRow row = actions.get(id);
			if (row != null && row.isEnabled()) {
				row.activate();
			}
			return true;
		}
	}

	// ---- input ----

	boolean press(float x, float y, int button) {
		dragX = x;
		dragY = y;
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		if (editing && (picking || optionsFor != null)) {
			return true; // clicks outside the card do nothing
		}
		for (int i = hits.size() - 1; i >= 0; i--) {
			if (hits.get(i).contains(x, y)) {
				hits.get(i).onPress().run();
				return true;
			}
		}
		return editing;
	}

	boolean drag(float x, float y, float dx, float dy) {
		dragX = x;
		dragY = y;
		if (turningSkin) {
			yaw += dx * 2.5f;
			pitch = Math.clamp(pitch - dy * 2.5f, -50, 50);
			return true;
		}
		if (moving != null) {
			candX = Math.round((x - grabX - M) / (cellW + GAP));
			candY = Math.round((y - grabY - TOP) / (cellH + GAP));
			candW = moving.w;
			candH = moving.h;
			candX = Math.clamp(candX, 0, HomeLayout.COLS - candW);
			candY = Math.clamp(candY, 0, HomeLayout.ROWS - candH);
			candOk = HomeLayout.fits(tiles, moving, candX, candY, candW, candH);
			return true;
		}
		if (resizing != null) {
			candX = resizing.x;
			candY = resizing.y;
			candW = Math.clamp(Math.round((x - cellX(resizing.x) + GAP) / (cellW + GAP)), resizing.type.minW, HomeLayout.COLS - resizing.x);
			candH = Math.clamp(Math.round((y - cellY(resizing.y) + GAP) / (cellH + GAP)), resizing.type.minH, HomeLayout.ROWS - resizing.y);
			candOk = HomeLayout.fits(tiles, resizing, candX, candY, candW, candH);
			return true;
		}
		return false;
	}

	boolean release(float x, float y) {
		if (turningSkin) {
			turningSkin = false;
			return true;
		}
		if (moving != null) {
			if (candOk) {
				moving.x = candX;
				moving.y = candY;
				save();
			} else {
				Toasts.show("Something's already there", 0xFFFF6B6B);
			}
			moving = null;
			return true;
		}
		if (resizing != null) {
			if (candOk) {
				resizing.w = candW;
				resizing.h = candH;
				save();
			}
			resizing = null;
			return true;
		}
		return false;
	}

}
