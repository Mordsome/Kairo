package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.menu.MenuScreen;
import dev.mordo.kairo.gui.widgets.Button;
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
import net.minecraft.client.gui.screens.options.AccessibilityOptionsScreen;
import net.minecraft.client.gui.screens.options.LanguageSelectScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.ServerList;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.storage.LevelStorageException;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.render.background.MenuBackground;
import java.util.Locale;

/**
 * The client's title screen. The vanilla panorama keeps spinning behind a soft gradient; on the left the wordmark
 * and the main entries (Singleplayer, Multiplayer, Client, Options, Quit); on the right "Jump back in" (your last
 * world) and "Quick join" (your first server, pinged live). Keys: ↑/↓ + Enter, or 1–5.
 */
public final class MainMenuScreen extends MainMenuBase {
	private static final float ROW_H = 62;
	private static final float ROW_GAP = 10;
	private static final float ROW_W = 360;
	private static final float CARD_W = 392;

	/** Every entry by id; {@link #rows} is the visible ones in the player's order. */
	private final Map<String, MenuRow> byId = new LinkedHashMap<>();
	private List<MenuRow> rows = List.of();
	private final MenuRow multiplayer;
	private final Button settings;
	private int keyboardIndex = -1;
	private final Animation cards = new Animation(0);

	// "Jump back in"
	private @Nullable LevelSummary lastWorld;
	private boolean worldsLoaded;
	private final Button playWorld;
	private final Button createWorld;

	// "Quick join"
	private final ServerList servers;
	private @Nullable ServerData quickServer;
	private @Nullable ServerPings pings;
	private final Button joinServer;
	private final Button allServers;

	private final Button language;
	private final Button accessibility;
	private float pillX;
	private float pillY;
	private float pillW;
	private final Animation pvp = new Animation(PvpMode.isActive() ? 1 : 0);

	// Other layouts (Main menu settings → Style).
	private final DockMenu dock = new DockMenu();
	private final OrbitMenu orbit = new OrbitMenu();
	private final DashboardMenu dashboard = new DashboardMenu(this, byId);
	/** Kairo Void shows its stage in place of the Kairo style's cards. */
	private final StageMenu stage = new StageMenu();

	private static boolean staged() {
		return dev.mordo.kairo.core.Edition.isVoid() && layout() == MainMenuSettings.Layout.CLASSIC;
	}
	/** Set before opening to start the Custom style in edit mode (Main menu settings → Edit home screen). */
	public static boolean startEditing;
	private final Button continueChip;
	private final Button joinChip;
	private boolean orbitPressed;

	private static MainMenuSettings.Layout layout() {
		MainMenuSettings.Layout layout = MainMenuSettings.INSTANCE.layout.get();
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			// Void has Stage (the Kairo style with its stage) and Simple.
			return layout == MainMenuSettings.Layout.SIMPLE ? layout : MainMenuSettings.Layout.CLASSIC;
		}
		return layout == MainMenuSettings.Layout.SIMPLE ? MainMenuSettings.Layout.CLASSIC : layout;
	}

	private final SimpleMenu simple = new SimpleMenu();

	public MainMenuScreen() {
		super(Component.literal(ClientInfo.NAME), null);
		Minecraft mc = Minecraft.getInstance();
		MenuRow singleplayer = new MenuRow(Icon.BOX, "Singleplayer", "Play your worlds or make a new one",
			() -> go(new WorldsScreen(this)));
		multiplayer = new MenuRow(Icon.GLOBE, "Multiplayer", "Join a server or connect directly",
			() -> go(new ServersScreen(this)));
		MenuRow client = new MenuRow(Icon.TOTEM, ClientInfo.NAME, "Mods, PvP mode, look and keybinds",
			() -> go(MenuScreen.openOn(MenuScreen::mods, this)));
		MenuRow options = new MenuRow(Icon.SLIDERS, "Options", "Video, controls, sound and more",
			() -> go(new OptionsScreen(this, mc.options)));
		MenuRow quit = new MenuRow(Icon.POWER, "Quit game", "See you soon", mc::stop)
			.accent(() -> 0xFFFF7A7A);
		byId.put("singleplayer", singleplayer);
		byId.put("multiplayer", multiplayer);
		byId.put("client", client);
		byId.put("options", options);
		byId.put("quit", quit);
		settings = new Button("", Icon.GEAR, Button.Style.GHOST, () -> go(new MainMenuSettingsScreen(this)));

		servers = new ServerList(mc);
		playWorld = new Button("Play", Icon.PLAY, Button.Style.PRIMARY, this::playLastWorld).textSize(12);
		createWorld = new Button("Create a world", Icon.PLUS, Button.Style.PRIMARY,
			() -> CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(this))).textSize(12);
		joinServer = new Button("Join", Icon.ARROW_RIGHT, Button.Style.PRIMARY, this::joinQuickServer).textSize(12);
		allServers = new Button("Add a server", Icon.PLUS, Button.Style.SUBTLE, () -> go(new ServersScreen(this))).textSize(12);
		language = new Button("", Icon.GLOBE, Button.Style.GHOST,
			() -> go(new LanguageSelectScreen(this, mc.options, mc.getLanguageManager())));
		accessibility = new Button("", Icon.EYE, Button.Style.GHOST, () -> go(new AccessibilityOptionsScreen(this, mc.options)));
		continueChip = new Button("Continue", Icon.PLAY, Button.Style.SUBTLE, this::playLastWorld).textSize(12);
		joinChip = new Button("Join", Icon.ARROW_RIGHT, Button.Style.SUBTLE, this::joinQuickServer).textSize(12);
	}

	@Override
	public boolean shouldCloseOnEsc() {
		return false;
	}

	@Override
	public void onClose() {
		// It's the title screen: nothing to go back to.
	}

	@Override
	protected boolean blurredBackdrop() {
		return false;
	}

	@Override
	protected void onShown() {
		MainMenuSettings menu = MainMenuSettings.INSTANCE;
		List<MenuRow> visible = new ArrayList<>();
		if (layout() == MainMenuSettings.Layout.CLASSIC) {
			for (String id : menu.buttonOrder()) {
				if (!menu.isHidden(id)) {
					visible.add(byId.get(id));
				}
			}
		} else {
			// Dock and Orbit always show the whole menu in the usual order.
			for (String id : MainMenuSettings.BUTTONS) {
				visible.add(byId.get(id));
			}
		}
		rows = visible;
		keyboardIndex = -1;
		for (int i = 0; i < rows.size(); i++) {
			rows.get(i).appear.set(0).animateTo(1, 460, Easing.UI, 80 + i * 45);
		}
		cards.set(0).animateTo(1, 520, Easing.UI, 220);
		dock.reset();
		orbit.reset();
		stage.reset();
		simple.reset();
		if (layout() == MainMenuSettings.Layout.DASHBOARD) {
			dashboard.refresh();
			if (startEditing) {
				startEditing = false;
				dashboard.startEditing();
			}
		}
		Minecraft mc = Minecraft.getInstance();
		boolean allowed = mc.allowsMultiplayer();
		multiplayer.setEnabled(allowed);
		multiplayer.setSubtitle(allowed ? "Join a server or connect directly" : "Multiplayer is disabled for this account");
		loadLastWorld();
		loadQuickServer();
	}

	@Override
	public void removed() {
		super.removed();
		if (pings != null) {
			pings.close();
			pings = null;
		}
		dashboard.close();
		stage.close();
	}

	@Override
	public void tick() {
		if (pings != null) {
			pings.tick();
		}
		dashboard.tick();
	}

	// ---- data ----

	private void loadLastWorld() {
		Minecraft mc = Minecraft.getInstance();
		worldsLoaded = false;
		try {
			mc.getLevelSource().loadLevelSummaries(mc.getLevelSource().findLevelCandidates())
				.thenAcceptAsync(list -> {
					lastWorld = list.stream().filter(s -> !s.isDisabled() && s.primaryActionActive())
						.max(Comparator.comparingLong(LevelSummary::getLastPlayed)).orElse(null);
					worldsLoaded = true;
				}, mc)
				.exceptionally(t -> {
					worldsLoaded = true;
					return null;
				});
		} catch (LevelStorageException e) {
			Kairo.LOGGER.warn("Couldn't list worlds", e);
			worldsLoaded = true;
		}
	}

	private void loadQuickServer() {
		servers.load();
		quickServer = servers.size() > 0 ? servers.get(0) : null;
		if (quickServer != null && Minecraft.getInstance().allowsMultiplayer()) {
			if (pings == null) {
				pings = new ServerPings();
			}
			pings.refresh(quickServer, () -> ServerList.saveSingleServer(quickServer));
		}
	}

	private void playLastWorld() {
		if (lastWorld != null) {
			UiSounds.click();
			Minecraft.getInstance().createWorldOpenFlows().openWorld(lastWorld.getLevelId(), () -> Minecraft.getInstance().gui.setScreen(this));
		}
	}

	private void joinQuickServer() {
		if (quickServer != null) {
			UiSounds.click();
			ConnectScreen.startConnecting(this, Minecraft.getInstance(), ServerAddress.parseString(quickServer.ip), quickServer, false, null);
		}
	}

	// ---- drawing ----

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		float a = intro.value();
		int w = g.guiWidth();
		int h = g.guiHeight();
		drawBackgroundAdjustments(g);
		switch (layout()) {
			case DOCK -> {
				dock.drawBackground(g);
				return;
			}
			case ORBIT -> {
				orbit.drawBackground(g, a);
				return;
			}
			case DASHBOARD -> {
				dashboard.drawBackground(g);
				return;
			}
			case SIMPLE -> {
				simple.drawBackground(g);
				return;
			}
			default -> {
			}
		}
		if (staged()) {
			stage.draw(g, a);
			return;
		}
		// Darker on the left (text side) and along the bottom, the panorama stays bright in the middle.
		g.fill(0, 0, w, h, Colors.argb(0.14f, 0x05070B));
		Shapes.gradientRectH(g, 0, 0, w * 0.66f, h, 0, Colors.argb(0.86f, 0x05070B), Colors.argb(0, 0x05070B));
		Shapes.gradientRect(g, 0, h * 0.62f, w, h * 0.38f, 0, Colors.argb(0, 0x05070B), Colors.argb(0.6f, 0x05070B));
		Shapes.gradientRect(g, 0, 0, w, h * 0.18f, 0, Colors.argb(0.35f * a, 0x05070B), Colors.argb(0, 0x05070B));
	}

	/** Darken (images are darkened by the background pass itself) and blur, from the main menu settings. */
	static void drawBackgroundAdjustments(GuiGraphicsExtractor g) {
		MainMenuSettings menu = MainMenuSettings.INSTANCE;
		float blur = menu.backgroundBlur.floatValue();
		if (blur > 0.01f) {
			Shapes.backdrop(g, blur, 0, 0.2f);
		}
		if (!MenuBackground.replacesPanorama()) {
			g.fill(0, 0, g.guiWidth(), g.guiHeight(), Colors.argb(menu.backgroundDim.floatValue(), 0x000000));
		}
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		switch (layout()) {
			case DOCK -> {
				float under = dock.drawClock(g, viewWidth, viewHeight, fade);
				drawChips(g, viewWidth / 2, under + 30, mx, my, fade * cards.value(), live);
				dock.drawDock(g, rows, viewWidth, viewHeight, mx, my, fade, live);
				drawTopRight(g, fade);
				drawFooter(g, Math.max(56, viewWidth * 0.066f), mx, my, fade, live);
				return;
			}
			case SIMPLE -> {
				simple.draw(g, rows, keyboardIndex, viewWidth, viewHeight, fade, (w, on) -> put(w, g, mx, my, fade, live && on));
				drawTopRight(g, fade);
				drawFooter(g, Math.max(56, viewWidth * 0.066f), mx, my, fade, live);
				return;
			}
			case DASHBOARD -> {
				dashboard.draw(g, viewWidth, viewHeight, mx, my, fade, (w, on) -> put(w, g, mx, my, fade, live && on));
				drawTopRight(g, fade);
				drawFooter(g, 44, mx, my, fade, live);
				return;
			}
			case ORBIT -> {
				orbit.draw(g, rows, viewWidth, viewHeight, mx, my, fade, live);
				drawChips(g, viewWidth / 2, viewHeight - 112, mx, my, fade * cards.value(), live);
				drawTopRight(g, fade);
				drawFooter(g, Math.max(56, viewWidth * 0.066f), mx, my, fade, live);
				return;
			}
			default -> {
			}
		}
		Minecraft mc = Minecraft.getInstance();
		float left = Math.max(56, viewWidth * 0.066f);
		float rise = Animation.motion((1 - fade) * 16, 0);

		// Wordmark.
		int accent = Theme.accent();
		String welcome = "WELCOME BACK, " + mc.getUser().getName().toUpperCase(Locale.ROOT);
		Fonts.draw(g, Typeface.MONO_BOLD, welcome, left, 186 + rise, 9, Colors.multiplyAlpha(accent, fade), Fonts.Align.LEFT, 2.4f, false);
		float wordSize = 78;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, ClientInfo.NAME, left - 4, 236 + rise, wordSize, Theme.text(1, fade));
		float wordW = Fonts.width(Typeface.OUTFIT_BOLD, ClientInfo.NAME, wordSize);
		glowDot(g, left + wordW + 14, 236 + rise + wordSize * 0.25f, 7, accent, fade);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "Client for Minecraft " + ClientInfo.MC_VERSION, left, 290 + rise, 14.5f, Theme.text(0.6f, fade));

		// Main entries.
		float y = 334;
		for (int i = 0; i < rows.size(); i++) {
			MenuRow row = rows.get(i);
			row.selected = i == keyboardIndex;
			row.bounds(left, y, ROW_W, ROW_H);
			put(row, g, mx, my, fade, live);
			y += ROW_H + ROW_GAP;
		}

		// Right column cards (hidden when the window is too narrow for them). Void's stage stands there instead,
		// with small chips under it.
		float cardX = viewWidth - left - CARD_W;
		if (staged()) {
			drawChips(g, viewWidth * 0.665f, viewHeight - 64, mx, my, fade * cards.value(), live);
		} else if (cardX > left + ROW_W + 40) {
			float ca = fade * cards.value();
			float slide = Animation.motion((1 - cards.value()) * 30, 0);
			MainMenuSettings menu = MainMenuSettings.INSTANCE;
			float cardY = 334;
			if (menu.showWorldCard.isOn()) {
				cardY += drawWorldCard(g, cardX + slide, cardY, mx, my, ca, live) + 14;
			}
			if (menu.showServerCard.isOn()) {
				drawServerCard(g, cardX + slide, cardY, mx, my, ca, live);
			}
		}

		drawTopRight(g, fade);
		drawFooter(g, left, mx, my, fade, live);
	}

	/** Small "Continue" (last world) and "Join" (first server) pills, centred on {@code cx}. */
	private void drawChips(GuiGraphicsExtractor g, float cx, float y, float mx, float my, float a, boolean live) {
		MainMenuSettings menu = MainMenuSettings.INSTANCE;
		boolean world = menu.showWorldCard.isOn() && lastWorld != null;
		boolean server = menu.showServerCard.isOn() && quickServer != null && Minecraft.getInstance().allowsMultiplayer();
		if (!world && !server) {
			return;
		}
		if (world) {
			continueChip.setLabel("Continue  ·  " + Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, lastWorld.getLevelName(), 12, 180));
		}
		if (server) {
			String status = quickServer.state() == ServerData.State.SUCCESSFUL ? "  ·  " + quickServer.ping + " ms" : "";
			joinChip.setLabel(Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, quickServer.name, 12, 180) + status);
		}
		float cw = world ? continueChip.preferredWidth() + 12 : 0;
		float jw = server ? joinChip.preferredWidth() + 12 : 0;
		float total = cw + jw + (world && server ? 12 : 0);
		float x = cx - total / 2;
		if (world) {
			continueChip.bounds(x, y, cw, 34);
			put(continueChip, g, mx, my, a, live);
			x += cw + 12;
		}
		if (server) {
			joinChip.bounds(x, y, jw, 34);
			put(joinChip, g, mx, my, a, live);
		}
	}

	private float drawWorldCard(GuiGraphicsExtractor g, float x, float y, float mx, float my, float a, boolean live) {
		float h = 150;
		card(g, x, y, h, "JUMP BACK IN", Icon.CLOCK, a);
		float iconY = y + 52;
		if (!worldsLoaded) {
			shimmer(g, x + 22, iconY, 64, 64, a);
			shimmer(g, x + 102, iconY + 8, 160, 14, a);
			shimmer(g, x + 102, iconY + 34, 110, 10, a);
			return h;
		}
		if (lastWorld == null) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "No worlds yet", x + 22, iconY + 14, 16, Theme.text(1, a));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Make your first one in a few clicks", x + 22, iconY + 36, 11.5f, Theme.text(0.55f, a));
			float bw = createWorld.preferredWidth() + 8;
			createWorld.bounds(x + CARD_W - 22 - bw, y + h - 22 - 34, bw, 34);
			put(createWorld, g, mx, my, a, live);
			return h;
		}
		Favicons.draw(g, Favicons.world(lastWorld), Icon.BOX, x + 22, iconY, 64, a);
		float tx = x + 102;
		float bw = playWorld.preferredWidth() + 10;
		float textW = CARD_W - (tx - x) - 22 - bw - 12;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, lastWorld.getLevelName(), 16, textW),
			tx, iconY + 16, 16, Theme.text(1, a));
		String mode = lastWorld.isHardcore() ? "Hardcore" : capitalize(lastWorld.getGameMode().getName());
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, mode + "  ·  " + timeAgo(lastWorld.getLastPlayed()), tx, iconY + 38, 11.5f, Theme.text(0.55f, a));
		Fonts.draw(g, Typeface.MONO_MEDIUM, lastWorld.getWorldVersionName().getString(), tx, iconY + 56, 9, Theme.text(0.38f, a));
		playWorld.bounds(x + CARD_W - 22 - bw, iconY + 15, bw, 34);
		put(playWorld, g, mx, my, a, live);
		return h;
	}

	private void drawServerCard(GuiGraphicsExtractor g, float x, float y, float mx, float my, float a, boolean live) {
		float h = 132;
		card(g, x, y, h, "QUICK JOIN", Icon.SERVER, a);
		float iconY = y + 50;
		if (quickServer == null || !Minecraft.getInstance().allowsMultiplayer()) {
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "No servers yet", x + 22, iconY + 12, 16, Theme.text(1, a));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Add one to join it from here", x + 22, iconY + 34, 11.5f, Theme.text(0.55f, a));
			float bw = allServers.preferredWidth() + 8;
			allServers.bounds(x + CARD_W - 22 - bw, y + h - 22 - 34, bw, 34);
			put(allServers, g, mx, my, a, live);
			return;
		}
		ServerData data = quickServer;
		Favicons.draw(g, Favicons.server(data), Icon.SERVER, x + 22, iconY, 52, a);
		float tx = x + 90;
		float bw = joinServer.preferredWidth() + 10;
		float textW = CARD_W - (tx - x) - 22 - bw - 12;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, data.name, 15, textW), tx, iconY + 10, 15, Theme.text(1, a));
		drawStatusLine(g, data, tx, iconY + 32, textW, a);
		// First line of the MOTD in the game's font (keeps its colours).
		List<FormattedCharSequence> motd = Minecraft.getInstance().font.split(data.motd, Math.round(textW));
		if (!motd.isEmpty() && data.state() == ServerData.State.SUCCESSFUL) {
			g.text(Minecraft.getInstance().font, motd.getFirst(), Math.round(tx), Math.round(iconY + 44), Colors.withAlpha(0xFFFFFF, 0.8f * a), false);
		}
		joinServer.bounds(x + CARD_W - 22 - bw, iconY + 9, bw, 34);
		put(joinServer, g, mx, my, a, live);
	}

	/** "● 34 ms · 120 / 500 online", "Pinging…", "Offline". */
	static void drawStatusLine(GuiGraphicsExtractor g, ServerData data, float x, float cy, float maxW, float a) {
		switch (data.state()) {
			case SUCCESSFUL -> {
				int color = ServerPings.pingColor(data.ping);
				glowDot(g, x + 3.5f, cy, 3.2f, color, a);
				String players = data.players == null ? "" : "  ·  " + data.players.online() + " / " + data.players.max() + " online";
				Fonts.draw(g, Typeface.OUTFIT_MEDIUM, data.ping + " ms" + players, x + 13, cy, 11.5f, Theme.text(0.7f, a));
			}
			case INCOMPATIBLE -> {
				glowDot(g, x + 3.5f, cy, 3.2f, 0xFFFFC145, a);
				Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "Different version: " + data.version.getString(), x + 13, cy, 11.5f, Theme.text(0.7f, a));
			}
			case UNREACHABLE -> {
				glowDot(g, x + 3.5f, cy, 3.2f, 0xFFFF7A7A, a);
				Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "Offline", x + 13, cy, 11.5f, Theme.text(0.7f, a));
			}
			default -> {
				int dots = (int) (System.currentTimeMillis() / 350 % 4);
				glowDot(g, x + 3.5f, cy, 3.2f, Colors.withAlpha(0xFFFFFF, 0.5f), a);
				Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "Pinging" + ".".repeat(dots), x + 13, cy, 11.5f, Theme.text(0.5f, a));
			}
		}
	}

	private static void card(GuiGraphicsExtractor g, float x, float y, float h, String kicker, Icon icon, float a) {
		Shapes.shadow(g, x, y + 10, CARD_W, h, 20, 24, Colors.argb(0.3f * a, 0));
		Shapes.glassRect(g, x, y, CARD_W, h, 20, Theme.glassTint(), 0x2EFFFFFF, 1.25f, a);
		IconRenderer.draw(g, icon, x + 28, y + 26, 13, Colors.multiplyAlpha(Theme.accent(), a), 2.2f);
		Fonts.draw(g, Typeface.MONO_BOLD, kicker, x + 42, y + 26, 8.5f, Colors.multiplyAlpha(Theme.accent(), a), Fonts.Align.LEFT, 2f, false);
	}

	/** Pulsing placeholder while something loads. */
	private static void shimmer(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		float pulse = 0.06f + 0.04f * (float) Math.sin(System.currentTimeMillis() / 220.0);
		Shapes.rect(g, x, y, w, h, Math.min(8, h / 2), Colors.argb(pulse * a, 0xFFFFFF));
	}

	/** Mode pill (click to switch PvP mode) and the version. */
	private void drawTopRight(GuiGraphicsExtractor g, float a) {
		pvp.animateTo(PvpMode.isActive() ? 1 : 0, 300);
		float on = pvp.value();
		float size = 11.5f;
		pillW = 40 + Animation.lerp(Fonts.width(Typeface.OUTFIT_SEMIBOLD, "Normal mode", size), Fonts.width(Typeface.OUTFIT_SEMIBOLD, "PvP mode", size), on);
		float pillH = 28;
		pillX = viewWidth - 28 - pillW;
		pillY = 26;
		int dot = Colors.lerp(Theme.accent(), Theme.pvpAccent(), on);
		Shapes.glassRect(g, pillX, pillY, pillW, pillH, pillH / 2, Theme.glassTint(), 0x2EFFFFFF, 1.25f, a);
		glowDot(g, pillX + 16, pillY + pillH / 2, 3.5f, dot, a);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Normal mode", pillX + 27, pillY + pillH / 2, size, Theme.text(1, a * (1 - on)));
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "PvP mode", pillX + 27, pillY + pillH / 2, size, Theme.text(1, a * on));
		Fonts.draw(g, Typeface.MONO_MEDIUM, ClientInfo.MC_VERSION + " · " + ClientInfo.SHORT_VERSION, pillX - 16, pillY + pillH / 2, 10,
			Theme.text(0.75f, a), Fonts.Align.RIGHT, 0.8f, true);
	}

	private void drawFooter(GuiGraphicsExtractor g, float left, float mx, float my, float a, boolean live) {
		float cy = viewHeight - 34;
		Fonts.draw(g, Typeface.MONO_MEDIUM, "Minecraft " + ClientInfo.MC_VERSION + "  ·  Fabric  ·  " + ClientInfo.FULL_NAME + " " + ClientInfo.VERSION,
			left, cy, 9.5f, Theme.text(0.6f, a), Fonts.Align.LEFT, 0.6f, true);
		float right = viewWidth - left;
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Minecraft © Mojang AB. Not an official Minecraft product.", right, cy, 10,
			Theme.text(0.55f, a), Fonts.Align.RIGHT, 0, true);
		float textW = Fonts.width(Typeface.OUTFIT_REGULAR, "Minecraft © Mojang AB. Not an official Minecraft product.", 10);
		accessibility.bounds(right - textW - 16 - 34, cy - 17, 34, 34);
		language.bounds(accessibility.x - 4 - 34, cy - 17, 34, 34);
		settings.bounds(language.x - 4 - 34, cy - 17, 34, 34);
		put(settings, g, mx, my, a, live);
		put(language, g, mx, my, a, live);
		put(accessibility, g, mx, my, a, live);
	}

	// ---- input ----

	@Override
	protected boolean onContentClick(float x, float y, int button) {
		if (button == InputConstants.MOUSE_BUTTON_LEFT && x >= pillX && x <= pillX + pillW && y >= pillY && y <= pillY + 28) {
			PvpMode.toggle();
			return true;
		}
		switch (layout()) {
			case DOCK -> {
				int i = dock.click(x, y);
				if (i >= 0 && button == InputConstants.MOUSE_BUTTON_LEFT) {
					open(i);
					return true;
				}
			}
			case DASHBOARD -> {
				if (dashboard.press(x, y, button)) {
					return true;
				}
			}
			case ORBIT -> {
				if (orbit.click(x, y, button, rows) == -2) {
					orbitPressed = true;
					return true;
				}
			}
			default -> {
			}
		}
		return false;
	}

	private void open(int index) {
		if (index >= 0 && index < rows.size() && rows.get(index).isEnabled()) {
			rows.get(index).activate();
		}
	}

	@Override
	protected boolean onDrag(float x, float y, int button, float dx, float dy) {
		if (super.onDrag(x, y, button, dx, dy)) {
			return true;
		}
		if (layout() == MainMenuSettings.Layout.DASHBOARD && dashboard.drag(x, y, dx, dy)) {
			return true;
		}
		return orbitPressed && orbit.drag(x, y);
	}

	@Override
	protected boolean onRelease(float x, float y, int button) {
		if (layout() == MainMenuSettings.Layout.DASHBOARD && dashboard.release(x, y)) {
			return true;
		}
		if (orbitPressed) {
			orbitPressed = false;
			open(orbit.release(x, y, rows));
			return true;
		}
		return super.onRelease(x, y, button);
	}

	@Override
	protected boolean onContentScroll(float x, float y, float amount) {
		if (layout() == MainMenuSettings.Layout.ORBIT && !rows.isEmpty()) {
			orbit.scroll(amount, rows.size());
			return true;
		}
		return false;
	}

	@Override
	protected boolean onKey(KeyEvent event) {
		if (event.isEscape() && layout() == MainMenuSettings.Layout.DASHBOARD && dashboard.escape()) {
			return true;
		}
		int key = event.key();
		if (key >= InputConstants.KEY_1 && key < InputConstants.KEY_1 + rows.size()) {
			MenuRow row = rows.get(key - InputConstants.KEY_1);
			if (row.isEnabled()) {
				row.activate();
			}
			return true;
		}
		if ((layout() == MainMenuSettings.Layout.DOCK || layout() == MainMenuSettings.Layout.ORBIT) && !rows.isEmpty()) {
			int result = layout() == MainMenuSettings.Layout.DOCK ? dock.key(event, rows.size()) : orbit.key(event, rows);
			if (result >= 0) {
				open(result);
				return true;
			}
			return result == -1;
		}
		if (event.isDown() || event.isUp()) {
			int dir = event.isDown() ? 1 : -1;
			int i = keyboardIndex < 0 ? (dir > 0 ? -1 : rows.size()) : keyboardIndex;
			do {
				i = Math.floorMod(i + dir, rows.size());
			} while (!rows.get(i).isEnabled());
			keyboardIndex = i;
			UiSounds.hover();
			return true;
		}
		if (event.isConfirmation() && keyboardIndex >= 0) {
			rows.get(keyboardIndex).activate();
			return true;
		}
		return false;
	}

	static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
