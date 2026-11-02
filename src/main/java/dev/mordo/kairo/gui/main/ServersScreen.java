package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ScrollState;
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
import net.minecraft.client.gui.screens.DirectJoinServerScreen;
import net.minecraft.client.gui.screens.ManageServerScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multiplayer: your saved servers as glass rows, pinged live (icon, MOTD in its own colours, signal bars, players).
 * Click to select, double-click or Join to connect; Edit / Delete on hover; Add server, Direct connect and Refresh
 * at the top. Adding and editing use Minecraft's own server form.
 */
public final class ServersScreen extends MainMenuBase {
	private static final float ROW_H = 92;
	private static final float GAP = 12;
	private static final float COMPACT_H = 50;
	private static final float TILE_H = 196;
	private static final float TILE_MIN_W = 270;

	private final ServerList servers;
	private final List<ServerData> list = new ArrayList<>();
	private final ServerPings pings = new ServerPings();
	private final Map<ServerData, RowWidgets> widgets = new IdentityHashMap<>();
	private final ScrollState scroll = new ScrollState();
	private final Button add;
	private final Button direct;
	private final Button refresh;
	private final Button addEmpty;
	private final Button[] layoutButtons = new Button[MainMenuSettings.ServerLayout.values().length];
	private final Button addressToggle;
	private @Nullable ServerData selected;
	private long lastClickTime;
	private @Nullable ServerData lastClick;
	private float listTop;
	private float listBottom;
	private float listX;
	private float listW;

	private final class RowWidgets {
		final Button join;
		final Button edit;
		final Button delete;
		final Animation hover = new Animation(0);
		final Animation selected = new Animation(0);
		final Animation appear = new Animation(0);

		RowWidgets(ServerData data) {
			join = new Button("Join", Icon.ARROW_RIGHT, Button.Style.PRIMARY, () -> join(data)).textSize(11.5f);
			edit = new Button("", Icon.PENCIL, Button.Style.SUBTLE, () -> edit(data));
			delete = new Button("", Icon.TRASH, Button.Style.DANGER, () -> askDelete(data));
		}
	}

	public ServersScreen(@Nullable Screen parent) {
		super(Component.literal("Multiplayer"), parent);
		servers = new ServerList(Minecraft.getInstance());
		add = new Button("Add server", Icon.PLUS, Button.Style.PRIMARY, this::addServer).textSize(12);
		direct = new Button("Direct connect", Icon.LINK, Button.Style.SUBTLE, this::directConnect).textSize(12);
		refresh = new Button("", Icon.REFRESH, Button.Style.SUBTLE, this::refreshAll);
		addEmpty = new Button("Add your first server", Icon.PLUS, Button.Style.PRIMARY, this::addServer).textSize(12.5f);
		Icon[] icons = {Icon.VIEW_LIST, Icon.VIEW_COMPACT, Icon.VIEW_TINY};
		for (MainMenuSettings.ServerLayout layout : MainMenuSettings.ServerLayout.values()) {
			layoutButtons[layout.ordinal()] = new Button("", icons[layout.ordinal()], Button.Style.GHOST, () -> {
				if (MainMenuSettings.INSTANCE.serverLayout.get() != layout) {
					MainMenuSettings.INSTANCE.serverLayout.set(layout);
					scroll.reset();
					UiSounds.click();
				}
			});
		}
		addressToggle = new Button("", Icon.EYE, Button.Style.GHOST, () -> {
			MainMenuSettings.INSTANCE.showAddresses.set(!MainMenuSettings.INSTANCE.showAddresses.isOn());
			UiSounds.toggle(MainMenuSettings.INSTANCE.showAddresses.isOn());
		});
	}

	private static MainMenuSettings.ServerLayout layout() {
		return MainMenuSettings.INSTANCE.serverLayout.get();
	}

	private static boolean addresses() {
		return MainMenuSettings.INSTANCE.showAddresses.isOn();
	}

	private int columns() {
		return layout() == MainMenuSettings.ServerLayout.TILES ? Math.max(2, (int) ((listW + GAP) / (TILE_MIN_W + GAP))) : 1;
	}

	private float cellH() {
		return switch (layout()) {
			case LIST -> ROW_H;
			case COMPACT -> COMPACT_H;
			case TILES -> TILE_H;
		};
	}

	private float gap() {
		return layout() == MainMenuSettings.ServerLayout.COMPACT ? 8 : GAP;
	}

	/** Cell rectangle for server {@code i} (x, y, w, h), before scrolling. */
	private float[] cell(int i) {
		int cols = columns();
		float gap = gap();
		float w = (listW - gap * (cols - 1)) / cols;
		float h = cellH();
		return new float[]{listX + (i % cols) * (w + gap), listTop + 4 + (i / cols) * (h + gap), w, h};
	}

	@Override
	protected void onShown() {
		servers.load();
		list.clear();
		for (int i = 0; i < servers.size(); i++) {
			list.add(servers.get(i));
		}
		widgets.clear();
		for (int i = 0; i < list.size(); i++) {
			ServerData data = list.get(i);
			RowWidgets rw = new RowWidgets(data);
			rw.appear.animateTo(1, 380, Easing.UI, Math.min(i, 12) * 35);
			widgets.put(data, rw);
			pings.refresh(data, servers::save);
		}
		if (selected == null || !list.contains(selected)) {
			selected = list.isEmpty() ? null : list.getFirst();
		}
	}

	@Override
	public void tick() {
		pings.tick();
	}

	@Override
	public void removed() {
		super.removed();
		pings.close();
	}

	// ---- actions ----

	private void join(ServerData data) {
		UiSounds.click();
		ConnectScreen.startConnecting(this, Minecraft.getInstance(), ServerAddress.parseString(data.ip), data, false, null);
	}

	private void addServer() {
		UiSounds.click();
		Minecraft mc = Minecraft.getInstance();
		ServerData fresh = new ServerData("Minecraft Server", "", ServerData.Type.OTHER);
		mc.gui.setScreen(new ManageServerScreen(this, Component.literal("Add server"), ok -> {
			if (ok) {
				ServerData hidden = servers.unhide(fresh.ip);
				if (hidden != null) {
					hidden.copyNameIconFrom(fresh);
				} else {
					servers.add(fresh, false);
				}
				servers.save();
				selected = null;
				Toasts.show("Added \"" + fresh.name + "\"", Theme.accent());
			}
			mc.gui.setScreen(this);
		}, fresh));
	}

	private void edit(ServerData data) {
		UiSounds.click();
		Minecraft mc = Minecraft.getInstance();
		ServerData copy = new ServerData(data.name, data.ip, ServerData.Type.OTHER);
		copy.copyFrom(data);
		mc.gui.setScreen(new ManageServerScreen(this, Component.literal("Edit server"), ok -> {
			if (ok) {
				data.copyFrom(copy);
				servers.save();
			}
			mc.gui.setScreen(this);
		}, copy));
	}

	private void directConnect() {
		UiSounds.click();
		Minecraft mc = Minecraft.getInstance();
		ServerData target = new ServerData("Direct connect", "", ServerData.Type.OTHER);
		mc.gui.setScreen(new DirectJoinServerScreen(this, ok -> {
			if (ok) {
				join(target);
			} else {
				mc.gui.setScreen(this);
			}
		}, target));
	}

	private void askDelete(ServerData data) {
		UiSounds.click();
		confirm("Remove \"" + data.name + "\"?", "It will be removed from your server list. You can always add it again.", "Remove", true, () -> {
			servers.remove(data);
			servers.save();
			list.remove(data);
			widgets.remove(data);
			if (selected == data) {
				selected = list.isEmpty() ? null : list.getFirst();
			}
		});
	}

	private void refreshAll() {
		UiSounds.click();
		for (ServerData data : list) {
			pings.refresh(data, servers::save);
		}
	}

	// ---- drawing ----

	@Override
	protected void drawContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float left = Math.max(48, viewWidth * 0.06f);
		int online = 0;
		for (ServerData d : list) {
			if (d.state() == ServerData.State.SUCCESSFUL) {
				online++;
			}
		}
		String subtitle = list.isEmpty() ? "No servers yet" : list.size() + (list.size() == 1 ? " server" : " servers") + "  ·  " + online + " online";
		float headerBottom = drawHeader(g, left, 42, "Multiplayer", subtitle, mx, my, fade, live);

		float addW = add.preferredWidth() + 10;
		add.bounds(viewWidth - left - addW, 44, addW, 38);
		float directW = direct.preferredWidth() + 8;
		direct.bounds(add.x - 10 - directW, 44, directW, 38);
		refresh.bounds(direct.x - 10 - 38, 44, 38, 38);
		put(refresh, g, mx, my, fade, live);
		// View: list / compact / tiles, and the address eye (handy when streaming).
		addressToggle.setIcon(addresses() ? Icon.EYE : Icon.EYE_OFF);
		addressToggle.bounds(refresh.x - 10 - 38, 44, 38, 38);
		put(addressToggle, g, mx, my, fade, live);
		float sw = 36;
		float sx = addressToggle.x - 14 - sw * layoutButtons.length;
		Shapes.rect(g, sx - 3, 44, sw * layoutButtons.length + 6, 38, 12, Colors.argb(0.05f * fade, 0xFFFFFF), Colors.argb(0.08f * fade, 0xFFFFFF), 1);
		for (int i = 0; i < layoutButtons.length; i++) {
			if (i == layout().ordinal()) {
				Shapes.rect(g, sx + i * sw + 1, 47, sw - 2, 32, 9, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.22f), fade),
					Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.6f), fade), 1);
			}
			layoutButtons[i].bounds(sx + i * sw, 45, sw, 36);
			put(layoutButtons[i], g, mx, my, fade, live);
		}
		put(direct, g, mx, my, fade, live);
		put(add, g, mx, my, fade, live);

		listTop = headerBottom + 34;
		listBottom = viewHeight - 32;
		listX = left;
		listW = viewWidth - left * 2;

		if (list.isEmpty()) {
			drawEmpty(g, mx, my, fade, live);
			return;
		}
		int rowsCount = (list.size() + columns() - 1) / columns();
		scroll.setSizes(rowsCount * (cellH() + gap()) - gap() + 12, listBottom - listTop);
		g.enableScissor((int) Math.floor(listX - 12), (int) Math.floor(listTop - 8), (int) Math.ceil(listX + listW + 12), (int) Math.ceil(listBottom));
		try {
			for (int i = 0; i < list.size(); i++) {
				float[] c = cell(i);
				float y = c[1] - scroll.value();
				if (y + c[3] < listTop - 8 || y > listBottom) {
					continue;
				}
				boolean rowLive = live && my >= listTop && my <= listBottom;
				switch (layout()) {
					case LIST -> drawRow(g, list.get(i), c[0], y, c[2], mx, my, fade, rowLive);
					case COMPACT -> drawCompact(g, list.get(i), c[0], y, c[2], mx, my, fade, rowLive);
					case TILES -> drawTile(g, list.get(i), c[0], y, c[2], mx, my, fade, rowLive);
				}
			}
		} finally {
			g.disableScissor();
		}
		scroll.drawBar(g, listX + listW + 10, listTop, fade);
	}

	private void drawRow(GuiGraphicsExtractor g, ServerData data, float x, float y, float w, float mx, float my, float fade, boolean live) {
		RowWidgets rw = widgets.computeIfAbsent(data, RowWidgets::new);
		float ap = rw.appear.value();
		float a = fade * ap;
		y += Animation.motion((1 - ap) * 14, 0);
		boolean over = live && mx >= x && mx <= x + w && my >= y && my <= y + ROW_H;
		rw.hover.animateTo(over ? 1 : 0, 160);
		rw.selected.animateTo(data == selected ? 1 : 0, 200);
		float hv = rw.hover.value();
		float sel = rw.selected.value();
		y -= Animation.motion(hv * 2, 0);

		if (hv > 0.01f) {
			Shapes.shadow(g, x, y + 8, w, ROW_H, 18, 18, Colors.argb(0.3f * hv * a, 0));
		}
		int stroke = Colors.lerp(Colors.lerp(0x24FFFFFF, 0x40FFFFFF, hv), Colors.withAlpha(Theme.accent(), 0.7f), sel);
		Shapes.glassRect(g, x, y, w, ROW_H, 18, Theme.glassTint(), stroke, 1.25f + sel * 0.5f, a);

		float icon = 64;
		Favicons.draw(g, Favicons.server(data), Icon.SERVER, x + 14, y + (ROW_H - icon) / 2, icon, a);
		float tx = x + 14 + icon + 18;

		// Right: signal and players, then (on hover / selected) the actions.
		float right = x + w - 18;
		float bh = 34;
		float by = y + (ROW_H - bh) / 2;
		float joinW = rw.join.preferredWidth() + 8;
		float actionsW = joinW + 8 + bh + 6 + bh;
		float show = Math.max(hv, sel);
		rw.delete.bounds(right - bh, by, bh, bh);
		rw.edit.bounds(rw.delete.x - 6 - bh, by, bh, bh);
		rw.join.bounds(rw.edit.x - 8 - joinW, by, joinW, bh);
		if (show > 0.02f) {
			boolean clickable = live && show > 0.5f;
			put(rw.join, g, mx, my, a * show, clickable);
			put(rw.edit, g, mx, my, a * show, clickable);
			put(rw.delete, g, mx, my, a * show, clickable);
		}
		float statsRight = right - actionsW * show - (show > 0.02f ? 18 : 0);
		drawSignal(g, data, statsRight, y + 30, a, true);

		float textW = statsRight - 150 - tx;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, data.name, 16.5f, textW), tx, y + 26, 16.5f, Theme.text(1, a));
		Minecraft mc = Minecraft.getInstance();
		if (data.state() == ServerData.State.SUCCESSFUL || data.state() == ServerData.State.INCOMPATIBLE) {
			List<FormattedCharSequence> lines = mc.font.split(data.motd, Math.max(40, Math.round(textW)));
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				g.text(mc.font, lines.get(i), Math.round(tx), Math.round(y + 44 + i * 12), Colors.withAlpha(0xFFFFFF, 0.85f * a), false);
			}
		} else {
			MainMenuScreen.drawStatusLine(g, data, tx, y + 54, textW, a);
		}
		if (addresses()) {
			Fonts.draw(g, Typeface.MONO_MEDIUM, Fonts.ellipsize(Typeface.MONO_MEDIUM, data.ip, 9, textW), tx, y + ROW_H - 14, 9, Theme.text(0.35f, a));
		}
	}

	/** Shared hover / selection animation and the glass background for a server cell. Returns {alpha, hover, selected, y}. */
	private float[] cellFrame(GuiGraphicsExtractor g, RowWidgets rw, ServerData data, float x, float y, float w, float h, float radius,
							  float mx, float my, float fade, boolean live) {
		float ap = rw.appear.value();
		float a = fade * ap;
		y += Animation.motion((1 - ap) * 14, 0);
		boolean over = live && mx >= x && mx <= x + w && my >= y && my <= y + h;
		rw.hover.animateTo(over ? 1 : 0, 160);
		rw.selected.animateTo(data == selected ? 1 : 0, 200);
		float hv = rw.hover.value();
		float sel = rw.selected.value();
		y -= Animation.motion(hv * 2, 0);
		if (hv > 0.01f) {
			Shapes.shadow(g, x, y + 8, w, h, radius, 18, Colors.argb(0.3f * hv * a, 0));
		}
		int stroke = Colors.lerp(Colors.lerp(0x24FFFFFF, 0x40FFFFFF, hv), Colors.withAlpha(Theme.accent(), 0.7f), sel);
		Shapes.glassRect(g, x, y, w, h, radius, Theme.glassTint(), stroke, 1.25f + sel * 0.5f, a);
		return new float[]{a, hv, sel, y};
	}

	/** One slim line per server: icon, name, status, ping; actions on hover. */
	private void drawCompact(GuiGraphicsExtractor g, ServerData data, float x, float y0, float w, float mx, float my, float fade, boolean live) {
		RowWidgets rw = widgets.computeIfAbsent(data, RowWidgets::new);
		float h = COMPACT_H;
		float[] f = cellFrame(g, rw, data, x, y0, w, h, 13, mx, my, fade, live);
		float a = f[0];
		float y = f[3];
		float show = Math.max(f[1], f[2]);
		float icon = 32;
		Favicons.draw(g, Favicons.server(data), Icon.SERVER, x + 10, y + (h - icon) / 2, icon, a);
		float tx = x + 10 + icon + 14;
		float bh = 30;
		float by = y + (h - bh) / 2;
		float right = x + w - 12;
		float joinW = rw.join.preferredWidth() + 6;
		rw.delete.bounds(right - bh, by, bh, bh);
		rw.edit.bounds(rw.delete.x - 6 - bh, by, bh, bh);
		rw.join.bounds(rw.edit.x - 8 - joinW, by, joinW, bh);
		if (show > 0.02f) {
			boolean clickable = live && show > 0.5f;
			put(rw.join, g, mx, my, a * show, clickable);
			put(rw.edit, g, mx, my, a * show, clickable);
			put(rw.delete, g, mx, my, a * show, clickable);
		}
		float statsRight = right - (joinW + 8 + bh + 6 + bh) * show - (show > 0.02f ? 16 : 0);
		drawSignal(g, data, statsRight, y + h / 2 - 4, a, false);
		float nameW = Math.min(260, (statsRight - 120 - tx) * 0.45f);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, data.name, 14, nameW), tx, y + h / 2, 14, Theme.text(1, a));
		float infoX = tx + nameW + 16;
		float infoW = statsRight - 110 - infoX;
		if (infoW > 40) {
			if (addresses()) {
				Fonts.draw(g, Typeface.MONO_MEDIUM, Fonts.ellipsize(Typeface.MONO_MEDIUM, data.ip, 9.5f, infoW), infoX, y + h / 2, 9.5f, Theme.text(0.4f, a));
			} else {
				MainMenuScreen.drawStatusLine(g, data, infoX, y + h / 2, infoW, a);
			}
		}
	}

	/** A card in a grid: big icon, name, one MOTD line, signal and players; Join across the bottom on hover. */
	private void drawTile(GuiGraphicsExtractor g, ServerData data, float x, float y0, float w, float mx, float my, float fade, boolean live) {
		RowWidgets rw = widgets.computeIfAbsent(data, RowWidgets::new);
		float h = TILE_H;
		float[] f = cellFrame(g, rw, data, x, y0, w, h, 20, mx, my, fade, live);
		float a = f[0];
		float y = f[3];
		float show = Math.max(f[1], f[2]);
		float icon = 56;
		Favicons.draw(g, Favicons.server(data), Icon.SERVER, x + 16, y + 16, icon, a);
		drawSignal(g, data, x + w - 16, y + 24, a, true);
		float tx = x + 16;
		float textW = w - 32;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, data.name, 16, textW), tx, y + 92, 16, Theme.text(1, a));
		Minecraft mc = Minecraft.getInstance();
		if (data.state() == ServerData.State.SUCCESSFUL || data.state() == ServerData.State.INCOMPATIBLE) {
			List<FormattedCharSequence> lines = mc.font.split(data.motd, Math.max(40, Math.round(textW)));
			for (int i = 0; i < Math.min(2, lines.size()); i++) {
				g.text(mc.font, lines.get(i), Math.round(tx), Math.round(y + 108 + i * 12), Colors.withAlpha(0xFFFFFF, 0.8f * a), false);
			}
		} else {
			MainMenuScreen.drawStatusLine(g, data, tx, y + 116, textW, a);
		}
		if (addresses()) {
			Fonts.draw(g, Typeface.MONO_MEDIUM, Fonts.ellipsize(Typeface.MONO_MEDIUM, data.ip, 9, textW), tx, y + h - 54, 9, Theme.text(0.35f, a));
		}
		float bh = 32;
		float by = y + h - bh - 12;
		rw.delete.bounds(x + w - 12 - bh, by, bh, bh);
		rw.edit.bounds(rw.delete.x - 6 - bh, by, bh, bh);
		rw.join.bounds(x + 12, by, rw.edit.x - 8 - (x + 12), bh);
		if (show > 0.02f) {
			boolean clickable = live && show > 0.5f;
			put(rw.join, g, mx, my, a * show, clickable);
			put(rw.edit, g, mx, my, a * show, clickable);
			put(rw.delete, g, mx, my, a * show, clickable);
		}
	}

	/** Signal bars + "34 ms" and the player count, right-aligned at {@code right}. */
	private static void drawSignal(GuiGraphicsExtractor g, ServerData data, float right, float cy, float a, boolean withPlayers) {
		boolean ok = data.state() == ServerData.State.SUCCESSFUL;
		int bars = ok ? ServerPings.bars(data.ping) : 0;
		int color = switch (data.state()) {
			case SUCCESSFUL -> ServerPings.pingColor(data.ping);
			case UNREACHABLE -> 0xFFFF7A7A;
			case INCOMPATIBLE -> 0xFFFFC145;
			default -> 0x80FFFFFF;
		};
		float bx = right - 5 * 5;
		for (int i = 0; i < 5; i++) {
			float bh = 4 + i * 3;
			boolean lit = i < bars;
			boolean pinging = data.state() == ServerData.State.PINGING || data.state() == ServerData.State.INITIAL;
			float pulse = pinging ? 0.25f + 0.5f * (0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 160.0 - i)) : 0;
			int c = lit ? color : Colors.argb(pinging ? pulse : 0.18f, 0xFFFFFF);
			Shapes.rect(g, bx + i * 5, cy + 8 - bh, 3, bh, 1, Colors.multiplyAlpha(c, a));
		}
		// The status line under the name already says "Offline" / "Pinging"; only add what it does not.
		String label = switch (data.state()) {
			case SUCCESSFUL -> data.ping + " ms";
			case INCOMPATIBLE -> data.version.getString();
			default -> "";
		};
		if (!label.isEmpty()) {
			Fonts.draw(g, Typeface.MONO_MEDIUM, label, bx - 8, cy + 3, 10, Colors.multiplyAlpha(ok ? Theme.text(0.75f) : color, a), Fonts.Align.RIGHT, 0, false);
		}
		if (ok && data.players != null && withPlayers) {
			String players = data.players.online() + " / " + data.players.max();
			IconRenderer.draw(g, Icon.USER, right - Fonts.width(Typeface.MONO_MEDIUM, players, 10) - 12, cy + 26, 11, Theme.text(0.55f, a), 2.2f);
			Fonts.draw(g, Typeface.MONO_MEDIUM, players, right, cy + 26, 10, Theme.text(0.6f, a), Fonts.Align.RIGHT, 0, false);
		}
	}

	private void drawEmpty(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float cx = viewWidth / 2;
		float cy = listTop + (listBottom - listTop) * 0.36f;
		Shapes.glassRect(g, cx - 48, cy - 48, 96, 96, 28, Theme.glassTint(), 0x2EFFFFFF, 1.25f, fade);
		IconRenderer.draw(g, Icon.GLOBE, cx, cy, 40, Colors.multiplyAlpha(Theme.accent(), fade), 1.8f);
		Fonts.drawCentered(g, Typeface.OUTFIT_BOLD, "No servers yet", cx, cy + 86, 22, Theme.text(1, fade));
		Fonts.drawCentered(g, Typeface.OUTFIT_REGULAR, "Add one, or use Direct connect for a quick visit.", cx, cy + 114, 13, Theme.text(0.55f, fade));
		float bw = addEmpty.preferredWidth() + 16;
		addEmpty.bounds(cx - bw / 2, cy + 140, bw, 40);
		put(addEmpty, g, mx, my, fade, live);
	}

	// ---- input ----

	@Override
	protected boolean onContentClick(float x, float y, int button) {
		ServerData hit = rowAt(x, y);
		if (hit == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		long now = System.currentTimeMillis();
		boolean doubleClick = hit == lastClick && now - lastClickTime < 350;
		lastClickTime = now;
		lastClick = hit;
		if (doubleClick) {
			join(hit);
		} else if (hit != selected) {
			selected = hit;
			UiSounds.hover();
		}
		return true;
	}

	private @Nullable ServerData rowAt(float x, float y) {
		if (y < listTop || y > listBottom || x < listX || x > listX + listW) {
			return null;
		}
		float sy = y + scroll.value();
		for (int i = 0; i < list.size(); i++) {
			float[] c = cell(i);
			if (x >= c[0] && x <= c[0] + c[2] && sy >= c[1] && sy <= c[1] + c[3]) {
				return list.get(i);
			}
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
		if (list.isEmpty()) {
			return false;
		}
		int index = Math.max(0, list.indexOf(selected));
		int cols = columns();
		if (cols > 1 && (event.isLeft() || event.isRight())) {
			selected = list.get(Math.clamp(index + (event.isRight() ? 1 : -1), 0, list.size() - 1));
			UiSounds.hover();
			return true;
		}
		if (event.isDown() || event.isUp()) {
			selected = list.get(Math.clamp(index + (event.isDown() ? cols : -cols), 0, list.size() - 1));
			UiSounds.hover();
			return true;
		}
		if (event.isConfirmation() && selected != null) {
			join(selected);
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE && selected != null) {
			askDelete(selected);
			return true;
		}
		if (event.key() == InputConstants.KEY_F5) {
			refreshAll();
			return true;
		}
		return false;
	}
}
