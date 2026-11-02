package dev.mordo.kairo.mods.waypoints;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.gui.main.MainMenuBase;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ScrollState;
import dev.mordo.kairo.gui.widgets.TextField;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.mods.Waypoints;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every waypoint of the current world or server, in all dimensions: filter by dimension, sort, search, copy
 * coordinates, rename, recolour, move, hide and delete. Opens with M (rebindable) or from the Waypoints mod card.
 */
public final class WaypointsScreen extends MainMenuBase {
	private static final float ROW_H = 78;
	private static final float EDIT_H = 96;
	private static final float GAP = 10;

	public enum Sort implements EnumSetting.Labeled {
		NEAREST("Nearest"), NAME("Name"), NEWEST("Newest"), OLDEST("Oldest");

		private final String label;

		Sort(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	private final Waypoints module;
	private final TextField search;
	private final Button add;
	private final Button settings;
	private final Map<String, Chip> dimensionChips = new LinkedHashMap<>();
	private final Map<Sort, Chip> sortChips = new LinkedHashMap<>();
	private final Map<Waypoint, RowWidgets> rows = new IdentityHashMap<>();
	private final ScrollState scroll = new ScrollState();
	private final List<Waypoint> shown = new ArrayList<>();
	private @Nullable String dimensionFilter;
	private @Nullable Waypoint selected;
	private @Nullable Waypoint editing;
	private long lastClickTime;
	private @Nullable Waypoint lastClick;
	private float listTop;
	private float listBottom;
	private float listX;
	private float listW;

	public WaypointsScreen(@Nullable Screen parent) {
		super(Component.literal("Waypoints"), parent);
		module = ModuleManager.get(Waypoints.class);
		search = new TextField("Search waypoints", Icon.SEARCH).maxLength(40);
		add = new Button("Add here", Icon.PLUS, Button.Style.PRIMARY, this::addHere).textSize(12);
		settings = new Button("", Icon.GEAR, Button.Style.SUBTLE,
			() -> go(MenuScreen.forModSettings(module, new WaypointsScreen(parent))));
		for (Sort sort : Sort.values()) {
			sortChips.put(sort, new Chip(sort.label(), () -> module.sort.get() == sort, () -> module.sort.set(sort)));
		}
	}

	// ---- data ----

	private List<Waypoint> all() {
		module.store().sync();
		return module.store().all();
	}

	private void refreshShown() {
		LocalPlayer player = Minecraft.getInstance().player;
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		shown.clear();
		for (Waypoint w : all()) {
			if (dimensionFilter != null && !w.dimension.equals(dimensionFilter)) {
				continue;
			}
			if (!q.isEmpty() && !w.name.toLowerCase(Locale.ROOT).contains(q) && !coords(w).contains(q)) {
				continue;
			}
			shown.add(w);
		}
		Comparator<Waypoint> order = switch (module.sort.get()) {
			case NAME -> Comparator.comparing(w -> w.name.toLowerCase(Locale.ROOT));
			case NEWEST -> Comparator.comparingLong((Waypoint w) -> w.created).reversed();
			case OLDEST -> Comparator.comparingLong(w -> w.created);
			case NEAREST -> Comparator.comparingDouble(w -> sortDistance(w, player));
		};
		shown.sort(order);
	}

	/** Same-dimension waypoints by distance first, then the rest by name. */
	private static double sortDistance(Waypoint w, @Nullable LocalPlayer player) {
		if (player == null) {
			return 0;
		}
		if (!w.dimension.equals(Waypoints.dimensionOf(player))) {
			return Double.MAX_VALUE / 2;
		}
		return w.distanceTo(player.getX(), player.getY(), player.getZ());
	}

	static String coords(Waypoint w) {
		return w.x + " " + w.y + " " + w.z;
	}

	static String dimensionName(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> "Overworld";
			case "minecraft:the_nether" -> "Nether";
			case "minecraft:the_end" -> "The End";
			default -> {
				int colon = dimension.indexOf(':');
				String path = colon >= 0 ? dimension.substring(colon + 1) : dimension;
				yield Character.toUpperCase(path.charAt(0)) + path.substring(1).replace('_', ' ');
			}
		};
	}

	static int dimensionColor(String dimension) {
		return switch (dimension) {
			case "minecraft:overworld" -> 0xFF6EE7A0;
			case "minecraft:the_nether" -> 0xFFFF7A5A;
			case "minecraft:the_end" -> 0xFFC792FF;
			default -> 0xFFB8C4D0;
		};
	}

	// ---- actions ----

	private void addHere() {
		if (Minecraft.getInstance().player == null) {
			return;
		}
		UiSounds.click();
		module.createAtPlayer();
		List<Waypoint> list = all();
		if (!list.isEmpty()) {
			Waypoint added = list.getLast();
			selected = added;
			startEditing(added);
		}
	}

	private void copy(Waypoint w) {
		UiSounds.click();
		Minecraft.getInstance().keyboardHandler.setClipboard(coords(w));
		Toasts.show("Copied " + coords(w), Theme.accent());
	}

	private void toggleVisible(Waypoint w) {
		w.visible = !w.visible;
		module.store().markDirty();
		UiSounds.toggle(w.visible);
	}

	private void askDelete(Waypoint w) {
		UiSounds.click();
		confirm("Delete \"" + w.name + "\"?", "The waypoint at " + coords(w) + " (" + dimensionName(w.dimension) + ") will be removed.",
			"Delete", true, () -> {
				module.store().remove(w);
				rows.remove(w);
				if (selected == w) {
					selected = null;
				}
				if (editing == w) {
					editing = null;
				}
			});
	}

	private void startEditing(Waypoint w) {
		editing = editing == w ? null : w;
		RowWidgets rw = rows.computeIfAbsent(w, RowWidgets::new);
		rw.loadFields();
		rw.expand.animateTo(editing == w ? 1 : 0, 260, Easing.UI);
		if (editing == w) {
			host.focus(rw.name);
		}
	}

	// ---- drawing ----

	@Override
	protected void drawContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float left = Math.max(48, viewWidth * 0.06f);
		Minecraft mc = Minecraft.getInstance();
		String world = WaypointStore.worldLabel();
		List<Waypoint> all = all();
		String subtitle = world == null ? "Join a world to see its waypoints"
			: world + "  ·  " + all.size() + (all.size() == 1 ? " waypoint" : " waypoints");
		drawHeader(g, left, 34, "Waypoints", subtitle, mx, my, fade, live);

		float y = 36;
		settings.bounds(viewWidth - left - 38, y, 38, 38);
		float addW = add.preferredWidth() + 10;
		add.bounds(settings.x - 10 - addW, y, addW, 38);
		add.setEnabled(mc.player != null);
		search.bounds(add.x - 12 - 260, y, 260, 38);
		put(settings, g, mx, my, fade, live);
		put(add, g, mx, my, fade, live);
		put(search, g, mx, my, fade, live);

		// Dimension chips (with counts) and sort chips.
		float chipY = 100;
		Map<String, Integer> counts = new LinkedHashMap<>();
		counts.put("", all.size());
		for (String dim : List.of("minecraft:overworld", "minecraft:the_nether", "minecraft:the_end")) {
			counts.put(dim, 0);
		}
		for (Waypoint w : all) {
			counts.merge(w.dimension, 1, Integer::sum);
		}
		float cx = left;
		for (Map.Entry<String, Integer> e : counts.entrySet()) {
			String dim = e.getKey();
			Chip chip = dimensionChips.computeIfAbsent(dim, d -> new Chip(d.isEmpty() ? "All" : dimensionName(d),
				() -> d.isEmpty() ? dimensionFilter == null : d.equals(dimensionFilter),
				() -> dimensionFilter = d.isEmpty() ? null : d).dot(d.isEmpty() ? 0 : dimensionColor(d)));
			chip.count = e.getValue();
			float cw = chip.preferredWidth();
			chip.bounds(cx, chipY, cw, 30);
			put(chip, g, mx, my, fade, live);
			cx += cw + 8;
		}
		float sx = viewWidth - left;
		for (Sort sort : List.of(Sort.values()).reversed()) {
			Chip chip = sortChips.get(sort);
			float cw = chip.preferredWidth();
			sx -= cw;
			chip.bounds(sx, chipY, cw, 30);
			put(chip, g, mx, my, fade, live);
			sx -= 6;
		}
		Fonts.draw(g, Typeface.MONO_BOLD, "SORT", sx - 8, chipY + 15, 8, Theme.text(0.45f, fade), Fonts.Align.RIGHT, 2, false);

		listTop = 146;
		listBottom = viewHeight - 52;
		listX = left;
		listW = viewWidth - left * 2;
		refreshShown();
		if (world == null || shown.isEmpty()) {
			drawEmpty(g, world == null, all.isEmpty(), fade);
		} else {
			drawList(g, mx, my, fade, live);
		}
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "↑↓ select  ·  C copy  ·  Enter edit  ·  Del delete  ·  double-click to edit",
			viewWidth / 2, viewHeight - 26, 10.5f, Theme.text(0.45f, fade), Fonts.Align.CENTER, 0, false);
	}

	private void drawList(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float total = 0;
		for (Waypoint w : shown) {
			total += rowHeight(w) + GAP;
		}
		scroll.setSizes(total + 8, listBottom - listTop);
		LocalPlayer player = Minecraft.getInstance().player;
		g.enableScissor((int) Math.floor(listX - 12), (int) Math.floor(listTop - 6), (int) Math.ceil(listX + listW + 12), (int) Math.ceil(listBottom));
		try {
			float y = listTop + 4 - scroll.value();
			for (Waypoint w : shown) {
				float h = rowHeight(w);
				if (y + h > listTop - 6 && y < listBottom) {
					drawRow(g, w, player, listX, y, listW, h, mx, my, fade, live && my >= listTop && my <= listBottom);
				}
				y += h + GAP;
			}
		} finally {
			g.disableScissor();
		}
		scroll.drawBar(g, listX + listW + 10, listTop, fade);
	}

	private float rowHeight(Waypoint w) {
		RowWidgets rw = rows.get(w);
		return ROW_H + (rw == null ? 0 : EDIT_H * rw.expand.value());
	}

	private void drawRow(GuiGraphicsExtractor g, Waypoint w, @Nullable LocalPlayer player, float x, float y, float width, float h,
						 float mx, float my, float fade, boolean live) {
		RowWidgets rw = rows.computeIfAbsent(w, RowWidgets::new);
		boolean over = live && mx >= x && mx <= x + width && my >= y && my <= y + h;
		rw.hover.animateTo(over ? 1 : 0, 150);
		rw.selected.animateTo(w == selected ? 1 : 0, 200);
		float hv = rw.hover.value();
		float sel = rw.selected.value();
		if (hv > 0.01f) {
			Shapes.shadow(g, x, y + 8, width, h, 18, 16, Colors.argb(0.28f * hv * fade, 0));
		}
		int stroke = Colors.lerp(Colors.lerp(0x24FFFFFF, 0x40FFFFFF, hv), Colors.withAlpha(Theme.accent(), 0.7f), sel);
		Shapes.glassRect(g, x, y, width, h, 18, Theme.glassTint(), stroke, 1.25f + sel * 0.5f, fade);
		float a = fade * (w.visible ? 1 : 0.55f);
		float cy = y + ROW_H / 2;

		// Colour dot (click cycles colours) and name.
		rw.dot.bounds(x + 16, cy - 14, 28, 28);
		put(rw.dot, g, mx, my, fade, live);
		float nameX = x + 58;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, w.name, 16.5f, 270), nameX, cy - 9, 16.5f, Theme.text(1, a));
		float tx = nameX;
		tx = tag(g, dimensionName(w.dimension).toUpperCase(Locale.ROOT), tx, cy + 14, Colors.withAlpha(dimensionColor(w.dimension), 0.18f),
			dimensionColor(w.dimension), fade);
		if (w.death) {
			tx = tag(g, "DEATH", tx, cy + 14, 0x40FF5A5A, 0xFFFF9A9A, fade);
		}
		if (!w.visible) {
			tag(g, "HIDDEN", tx, cy + 14, 0x26FFFFFF, Theme.text(0.7f), fade);
		}

		// Coordinates, plus the matching position in the other dimension.
		float coordX = x + Math.max(360, width * 0.34f);
		drawCoord(g, "X", w.x, coordX, cy - 8, a);
		drawCoord(g, "Y", w.y, coordX + 92, cy - 8, a);
		drawCoord(g, "Z", w.z, coordX + 170, cy - 8, a);
		String other = switch (w.dimension) {
			case "minecraft:overworld" -> "Nether  " + Math.floorDiv(w.x, 8) + "  " + Math.floorDiv(w.z, 8);
			case "minecraft:the_nether" -> "Overworld  " + w.x * 8 + "  " + w.z * 8;
			default -> "";
		};
		if (!other.isEmpty()) {
			Fonts.draw(g, Typeface.MONO_MEDIUM, other, coordX, cy + 14, 9.5f, Theme.text(0.45f, a), Fonts.Align.LEFT, 0.5f, false);
		}

		// Actions.
		float bs = 36;
		float bx = x + width - 16 - bs;
		rw.delete.bounds(bx, cy - bs / 2, bs, bs);
		bx -= bs + 6;
		rw.edit.bounds(bx, cy - bs / 2, bs, bs);
		bx -= bs + 6;
		Button eye = w.visible ? rw.hide : rw.show;
		eye.bounds(bx, cy - bs / 2, bs, bs);
		bx -= 6;
		float copyW = rw.copy.preferredWidth() + 4;
		bx -= copyW;
		rw.copy.bounds(bx, cy - bs / 2, copyW, bs);
		put(rw.copy, g, mx, my, fade, live);
		put(eye, g, mx, my, fade, live);
		put(rw.edit, g, mx, my, fade, live);
		put(rw.delete, g, mx, my, fade, live);

		// Distance and a compass arrow pointing at it (only in the same dimension).
		float infoRight = bx - 22;
		if (player != null && w.dimension.equals(Waypoints.dimensionOf(player))) {
			double dist = w.distanceTo(player.getX(), player.getY(), player.getZ());
			Fonts.draw(g, Typeface.MONO_BOLD, Math.round(dist) + " m", infoRight - 34, cy, 13, Colors.multiplyAlpha(Theme.accent(), a), Fonts.Align.RIGHT, 0.3f, false);
			double dx = w.x + 0.5 - player.getX();
			double dz = w.z + 0.5 - player.getZ();
			float bearing = (float) Math.toDegrees(Math.atan2(-dx, dz));
			float rel = (float) Math.toRadians(net.minecraft.util.Mth.wrapDegrees(bearing - player.getYRot()));
			float ax = infoRight - 12;
			Shapes.circle(g, ax, cy, 13, Colors.argb(0.08f * a, 0xFFFFFF), Colors.argb(0.18f * a, 0xFFFFFF), 1);
			// A line arrow (a plain triangle looks the same every 120 degrees, so it can't show direction).
			float dirX = (float) Math.sin(rel);
			float dirY = (float) -Math.cos(rel);
			float tipX = ax + dirX * 7;
			float tipY = cy + dirY * 7;
			int arrow = Colors.multiplyAlpha(Theme.accent(), a);
			Shapes.line(g, ax - dirX * 6, cy - dirY * 6, tipX, tipY, 2, arrow);
			for (int side = -1; side <= 1; side += 2) {
				double back = rel + Math.PI + side * 0.6;
				Shapes.line(g, tipX, tipY, tipX + (float) Math.sin(back) * 5.5f, tipY - (float) Math.cos(back) * 5.5f, 2, arrow);
			}
		} else {
			Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "In " + dimensionName(w.dimension), infoRight, cy, 11.5f, Theme.text(0.45f, a), Fonts.Align.RIGHT, 0, false);
		}

		// Editor.
		float ex = rw.expand.value();
		if (ex > 0.01f) {
			float ey = y + ROW_H;
			float ea = fade * ex;
			Shapes.rect(g, x + 16, ey - 2, width - 32, 1, 0, Colors.argb(0.08f * ea, 0xFFFFFF));
			float fy = ey + 18;
			rw.name.bounds(x + 16, fy, 260, 36);
			rw.fx.bounds(x + 290, fy, 96, 36);
			rw.fy.bounds(x + 392, fy, 80, 36);
			rw.fz.bounds(x + 478, fy, 96, 36);
			rw.here.bounds(x + 586, fy, rw.here.preferredWidth() + 6, 36);
			boolean editable = editing == w && ex > 0.9f;
			for (Widget field : List.of(rw.name, rw.fx, rw.fy, rw.fz, rw.here)) {
				put(field, g, mx, my, ea, live && editable);
			}
			Fonts.draw(g, Typeface.MONO_BOLD, "NAME", x + 18, fy - 8, 7.5f, Theme.text(0.4f, ea), Fonts.Align.LEFT, 1.5f, false);
			Fonts.draw(g, Typeface.MONO_BOLD, "X", x + 292, fy - 8, 7.5f, Theme.text(0.4f, ea), Fonts.Align.LEFT, 1.5f, false);
			Fonts.draw(g, Typeface.MONO_BOLD, "Y", x + 394, fy - 8, 7.5f, Theme.text(0.4f, ea), Fonts.Align.LEFT, 1.5f, false);
			Fonts.draw(g, Typeface.MONO_BOLD, "Z", x + 480, fy - 8, 7.5f, Theme.text(0.4f, ea), Fonts.Align.LEFT, 1.5f, false);
			float px = x + width - 16 - Waypoints.PALETTE.length * 30;
			Fonts.draw(g, Typeface.MONO_BOLD, "COLOR", px + 2, fy - 8, 7.5f, Theme.text(0.4f, ea), Fonts.Align.LEFT, 1.5f, false);
			for (int i = 0; i < rw.swatches.size(); i++) {
				Swatch s = rw.swatches.get(i);
				s.bounds(px + i * 30, fy + 4, 26, 26);
				put(s, g, mx, my, ea, live && editable);
			}
		}
	}

	private static void drawCoord(GuiGraphicsExtractor g, String axis, int value, float x, float cy, float a) {
		Fonts.draw(g, Typeface.MONO_BOLD, axis, x, cy, 9, Theme.text(0.4f, a), Fonts.Align.LEFT, 0, false);
		Fonts.draw(g, Typeface.MONO_MEDIUM, Integer.toString(value), x + 13, cy, 14, Theme.text(0.95f, a), Fonts.Align.LEFT, 0, false);
	}

	private static float tag(GuiGraphicsExtractor g, String text, float x, float cy, int fill, int color, float a) {
		float w = Fonts.width(Typeface.MONO_BOLD, text, 7.5f, 1) + 12;
		Shapes.rect(g, x, cy - 8, w, 16, 5, Colors.multiplyAlpha(fill, a));
		Fonts.draw(g, Typeface.MONO_BOLD, text, x + w / 2, cy, 7.5f, Colors.multiplyAlpha(color, a), Fonts.Align.CENTER, 1, false);
		return x + w + 6;
	}

	private void drawEmpty(GuiGraphicsExtractor g, boolean noWorld, boolean noneAtAll, float fade) {
		float cx = viewWidth / 2;
		float cy = listTop + (listBottom - listTop) * 0.36f;
		Shapes.glassRect(g, cx - 48, cy - 48, 96, 96, 28, Theme.glassTint(), 0x2EFFFFFF, 1.25f, fade);
		IconRenderer.draw(g, Icon.PIN, cx, cy, 40, Colors.multiplyAlpha(Theme.accent(), fade), 1.8f);
		String title = noWorld ? "Not in a world" : noneAtAll ? "No waypoints yet" : "Nothing matches";
		String text = noWorld ? "Waypoints belong to a world or server; join one to see them."
			: noneAtAll ? "Press \"Add here\" to mark where you stand." : "Try another dimension or search.";
		Fonts.drawCentered(g, Typeface.OUTFIT_BOLD, title, cx, cy + 86, 22, Theme.text(1, fade));
		Fonts.drawCentered(g, Typeface.OUTFIT_REGULAR, text, cx, cy + 114, 13, Theme.text(0.55f, fade));
	}

	// ---- input ----

	@Override
	protected boolean onContentClick(float x, float y, int button) {
		Waypoint hit = rowAt(x, y);
		if (hit == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		long now = System.currentTimeMillis();
		boolean doubleClick = hit == lastClick && now - lastClickTime < 350;
		lastClickTime = now;
		lastClick = hit;
		if (doubleClick) {
			startEditing(hit);
		} else if (hit != selected) {
			selected = hit;
			UiSounds.hover();
		}
		return true;
	}

	private @Nullable Waypoint rowAt(float x, float y) {
		if (y < listTop || y > listBottom || x < listX || x > listX + listW) {
			return null;
		}
		float ry = listTop + 4 - scroll.value();
		for (Waypoint w : shown) {
			float h = rowHeight(w);
			if (y >= ry && y <= ry + h) {
				return w;
			}
			ry += h + GAP;
		}
		return null;
	}

	@Override
	protected boolean onContentScroll(float x, float y, float amount) {
		scroll.scroll(amount);
		return true;
	}

	@Override
	protected boolean onKey(KeyEvent event) {
		if (shown.isEmpty()) {
			return false;
		}
		int index = Math.max(0, shown.indexOf(selected));
		if (event.isDown() || event.isUp()) {
			selected = shown.get(Math.clamp(index + (event.isDown() ? 1 : -1), 0, shown.size() - 1));
			UiSounds.hover();
			return true;
		}
		if (selected == null) {
			return false;
		}
		if (event.isConfirmation()) {
			startEditing(selected);
			return true;
		}
		if (event.key() == InputConstants.KEY_C) {
			copy(selected);
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE) {
			askDelete(selected);
			return true;
		}
		return false;
	}

	/** Typing (when no field is focused) starts a search; the open key closes the screen again. */
	@Override
	public boolean keyPressed(KeyEvent event) {
		if (host.focused() == null && module.openKey.get().matchesKey(event.key()) && !isClosing()) {
			onClose();
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (host.focused() == null && Character.isLetterOrDigit(event.codepoint()) && event.codepoint() != 'c' && event.codepoint() != 'C') {
			host.focus(search);
		}
		return super.charTyped(event);
	}

	// ---- widgets ----

	private final class RowWidgets {
		final Waypoint waypoint;
		final Button copy;
		final Button hide;
		final Button show;
		final Button edit;
		final Button delete;
		final Button here;
		final ColorDot dot;
		final TextField name;
		final TextField fx;
		final TextField fy;
		final TextField fz;
		final List<Swatch> swatches = new ArrayList<>();
		final Animation hover = new Animation(0);
		final Animation selected = new Animation(0);
		final Animation expand = new Animation(0);

		RowWidgets(Waypoint w) {
			waypoint = w;
			copy = new Button("Copy", Icon.COPY, Button.Style.SUBTLE, () -> copy(w)).textSize(11.5f);
			hide = new Button("", Icon.EYE, Button.Style.GHOST, () -> toggleVisible(w));
			show = new Button("", Icon.EYE_OFF, Button.Style.GHOST, () -> toggleVisible(w));
			edit = new Button("", Icon.PENCIL, Button.Style.GHOST, () -> startEditing(w));
			delete = new Button("", Icon.TRASH, Button.Style.GHOST, () -> askDelete(w));
			here = new Button("Use my position", Icon.PIN, Button.Style.SUBTLE, () -> moveHere(w)).textSize(11);
			dot = new ColorDot(w);
			name = new TextField("Name", null).maxLength(32).onChange(t -> {
				w.name = t.isBlank() ? "Waypoint" : t;
				module.store().markDirty();
			});
			fx = coordField(v -> w.x = v);
			fy = coordField(v -> w.y = v);
			fz = coordField(v -> w.z = v);
			for (int color : Waypoints.PALETTE) {
				swatches.add(new Swatch(w, color));
			}
		}

		private TextField coordField(java.util.function.IntConsumer apply) {
			return new TextField("0", null).maxLength(9).mono(12)
				.allow(c -> Character.isDigit(c) || c == '-')
				.onChange(t -> {
					try {
						apply.accept(Integer.parseInt(t.trim()));
						module.store().markDirty();
					} catch (NumberFormatException ignored) {
						// half-typed ("-"): keep the old value until it's a number
					}
				});
		}

		void loadFields() {
			name.setText(waypoint.name);
			fx.setText(Integer.toString(waypoint.x));
			fy.setText(Integer.toString(waypoint.y));
			fz.setText(Integer.toString(waypoint.z));
		}

		private void moveHere(Waypoint w) {
			LocalPlayer player = Minecraft.getInstance().player;
			if (player == null) {
				return;
			}
			BlockPos pos = player.blockPosition();
			w.x = pos.getX();
			w.y = pos.getY();
			w.z = pos.getZ();
			w.dimension = Waypoints.dimensionOf(player);
			module.store().markDirty();
			loadFields();
			UiSounds.click();
		}
	}

	/** Colour dot on the row: click to cycle through the palette. */
	private final class ColorDot extends Widget {
		private final Waypoint waypoint;

		ColorDot(Waypoint waypoint) {
			this.waypoint = waypoint;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			float r = 8 + hover.value() * 1.5f;
			Shapes.shadow(g, x + w / 2 - r, y + h / 2 - r, r * 2, r * 2, r, 6, Colors.multiplyAlpha(Colors.withAlpha(waypoint.color, 0.5f), fade));
			Shapes.circle(g, x + w / 2, y + h / 2, r, Colors.multiplyAlpha(waypoint.color, fade), Theme.text(0.35f + 0.4f * hover.value(), fade), 1.2f);
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			int index = 0;
			for (int i = 0; i < Waypoints.PALETTE.length; i++) {
				if (Waypoints.PALETTE[i] == waypoint.color) {
					index = i + 1;
				}
			}
			waypoint.color = Waypoints.PALETTE[index % Waypoints.PALETTE.length];
			module.store().markDirty();
			UiSounds.click();
			return true;
		}
	}

	/** A palette colour in the editor. */
	private final class Swatch extends Widget {
		private final Waypoint waypoint;
		private final int color;

		Swatch(Waypoint waypoint, int color) {
			this.waypoint = waypoint;
			this.color = color;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			boolean on = waypoint.color == color;
			float r = w / 2 - 3 + hover.value();
			Shapes.circle(g, x + w / 2, y + h / 2, r, Colors.multiplyAlpha(color, fade));
			if (on) {
				Shapes.ring(g, x + w / 2, y + h / 2, w / 2, 1.6f, Colors.multiplyAlpha(0xFFFFFFFF, fade));
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			waypoint.color = color;
			module.store().markDirty();
			UiSounds.click();
			return true;
		}
	}

	/** Small pill: dimension filter (with a coloured dot and count) or sort option. */
	private static final class Chip extends Widget {
		private final String label;
		private final java.util.function.BooleanSupplier active;
		private final Runnable action;
		private int dotColor;
		int count = -1;
		private final Animation on = new Animation(0);

		Chip(String label, java.util.function.BooleanSupplier active, Runnable action) {
			this.label = label;
			this.active = active;
			this.action = action;
			this.h = 30;
		}

		Chip dot(int color) {
			this.dotColor = color;
			return this;
		}

		@Override
		public float preferredWidth() {
			float w = Fonts.width(Typeface.OUTFIT_SEMIBOLD, label, 11.5f) + 26;
			if (dotColor != 0) {
				w += 14;
			}
			if (count >= 0) {
				w += Fonts.width(Typeface.MONO_BOLD, Integer.toString(count), 9) + 10;
			}
			return w;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			on.animateTo(active.getAsBoolean() ? 1 : 0, 200);
			float t = on.value();
			int fill = Colors.lerp(Colors.lerp(Theme.SURFACE, Theme.SURFACE_HOVER, hover.value()), Colors.withAlpha(Theme.accent(), 0.22f), t);
			int stroke = Colors.lerp(Theme.BORDER, Colors.withAlpha(Theme.accent(), 0.7f), t);
			Shapes.rect(g, x, y, w, h, h / 2, Colors.multiplyAlpha(fill, fade), Colors.multiplyAlpha(stroke, fade), 1);
			float tx = x + 13;
			if (dotColor != 0) {
				Shapes.circle(g, tx + 3, y + h / 2, 3.5f, Colors.multiplyAlpha(dotColor, fade));
				tx += 14;
			}
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, label, tx, y + h / 2, 11.5f, Colors.lerp(Theme.text(0.75f, fade), Theme.text(1, fade), t));
			if (count >= 0) {
				Fonts.draw(g, Typeface.MONO_BOLD, Integer.toString(count), x + w - 13, y + h / 2, 9, Theme.text(0.5f, fade), Fonts.Align.RIGHT, 0, false);
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT) {
				return false;
			}
			UiSounds.click();
			action.run();
			return true;
		}
	}
}
