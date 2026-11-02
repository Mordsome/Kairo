package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.gui.FilePicker;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ScrollState;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.background.Backgrounds;
import dev.mordo.kairo.render.background.MenuBackground;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Main menu settings (the gear on the main menu, or "Kairo" on Minecraft's title screen): the menu style,
 * the order and visibility of the buttons (drag to reorder), the two cards, and the background with live preview.
 */
public final class MainMenuSettingsScreen extends MainMenuBase {
	private static final float ROW_H = 46;
	private static final float ROW_GAP = 6;
	private static final float PANEL_PAD = 24;

	private final Button done;
	private final Button openFolder;
	private final SettingsList cardsList;
	private final SettingsList minecraftList;
	private final Button editHome;
	private final SettingsList backgroundList;
	private final ScrollState gridScroll = new ScrollState();

	private final List<String> order = new ArrayList<>();
	private final Map<String, Animation> rowY = new HashMap<>();
	private final Map<String, Button> eyes = new HashMap<>();
	private final Map<String, Button> eyesOff = new HashMap<>();
	private final Map<String, Animation> tileHover = new HashMap<>();
	private final Map<String, Button> tileDelete = new HashMap<>();
	private final Animation styleAnim = new Animation(ClientSettings.INSTANCE.customMainMenu.isOn() ? 0 : 1);

	private @Nullable String dragging;
	private float dragY;
	private float grabOffset;

	// Layout from the last frame (hit testing).
	private float leftX;
	private float leftW;
	private float rowsTop;
	private float styleY;
	private float tileW;
	/** Width of the four style tiles (the background grid has its own {@link #tileW}). */
	private float styleTileW;
	private float gridX;
	private float gridTop;
	private float gridBottom;
	private float gridW;
	private float tileH;
	private List<Backgrounds.Entry> entries = List.of();

	public MainMenuSettingsScreen(@Nullable Screen parent) {
		super(Component.literal("Main menu settings"), parent);
		done = new Button("Done", Icon.CHECK, Button.Style.PRIMARY, this::onClose).textSize(12);
		openFolder = new Button("Open folder", Icon.FOLDER, Button.Style.SUBTLE, () -> FilePicker.openFolder(MenuBackground.folder())).textSize(11);
		MainMenuSettings m = MainMenuSettings.INSTANCE;
		List<Setting<?>> cards = List.of(m.showWorldCard, m.showServerCard);
		cardsList = SettingsList.forSettings(host, null, cards, false);
		minecraftList = SettingsList.forSettings(host, null, List.of(m.hideTitleIcons), false);
		editHome = new Button("Edit home screen", Icon.PENCIL, Button.Style.PRIMARY, () -> {
			MainMenuScreen.startEditing = true;
			close(new MainMenuScreen());
		}).textSize(12);
		List<Setting<?>> bg = List.of(ClientSettings.INSTANCE.wallpaperColors, m.backgroundDim, m.backgroundBlur, m.backgroundMotion);
		backgroundList = SettingsList.forSettings(host, null, bg, false)
			.header(new dev.mordo.kairo.gui.widgets.WallpaperSwatches(false).section());
		for (String id : MainMenuSettings.BUTTONS) {
			eyes.put(id, new Button("", Icon.EYE, Button.Style.GHOST, () -> toggleHidden(id)));
			eyesOff.put(id, new Button("", Icon.EYE_OFF, Button.Style.GHOST, () -> toggleHidden(id)));
		}
	}

	@Override
	protected void onShown() {
		order.clear();
		order.addAll(MainMenuSettings.INSTANCE.buttonOrder());
		Backgrounds.rescanSoon();
	}

	/** Back to the main menu in whichever style is chosen now. */
	@Override
	public void onClose() {
		UiSounds.close();
		boolean kairo = ClientSettings.INSTANCE.customMainMenu.isOn();
		if (kairo) {
			close(parent instanceof MainMenuScreen ? parent : new MainMenuScreen());
		} else {
			close(new TitleScreen());
		}
	}

	@Override
	protected boolean blurredBackdrop() {
		return false;
	}

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		MainMenuScreen.drawBackgroundAdjustments(g);
		g.fill(0, 0, g.guiWidth(), g.guiHeight(), Colors.argb(0.25f, 0x05070B));
	}

	// ---- drawing ----

	@Override
	protected void drawContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float left = Math.max(48, viewWidth * 0.05f);
		drawHeader(g, left, 34, "Main menu", "Style, buttons and background", mx, my, fade, live);
		float doneW = done.preferredWidth() + 12;
		done.bounds(viewWidth - left - doneW, 36, doneW, 38);
		put(done, g, mx, my, fade, live);

		float top = 104;
		float bottom = viewHeight - 32;
		float gap = 20;
		leftW = Math.min(540, (viewWidth - left * 2 - gap) * 0.42f);
		leftX = left;
		float rightX = leftX + leftW + gap;
		float rightW = viewWidth - left - rightX;
		float rise = Animation.motion((1 - fade) * 14, 0);

		panel(g, leftX, top + rise, leftW, bottom - top, fade);
		panel(g, rightX, top + rise, rightW, bottom - top, fade);
		drawLeft(g, leftX + PANEL_PAD, top + rise + PANEL_PAD, leftW - PANEL_PAD * 2, bottom - top - PANEL_PAD * 2, mx, my, fade, live);
		drawRight(g, rightX + PANEL_PAD, top + rise + PANEL_PAD, rightW - PANEL_PAD * 2, bottom - top - PANEL_PAD * 2, mx, my, fade, live);
	}

	private static void panel(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		Shapes.shadow(g, x, y + 10, w, h, 22, 26, Colors.argb(0.3f * a, 0));
		Shapes.glassRect(g, x, y, w, h, 22, Theme.glassTint(), Theme.PANEL_BORDER, 1.25f, a);
	}

	private static void label(GuiGraphicsExtractor g, String text, float x, float cy, float a) {
		Fonts.draw(g, Typeface.MONO_BOLD, text, x, cy, 8.5f, Theme.text(0.45f, a), Fonts.Align.LEFT, 2f, false);
	}

	private void drawLeft(GuiGraphicsExtractor g, float x, float y, float w, float h, float mx, float my, float a, boolean live) {
		// Style.
		label(g, "STYLE", x, y + 6, a);
		styleY = y + 22;
		boolean kairo = ClientSettings.INSTANCE.customMainMenu.isOn();
		styleAnim.animateTo(kairo ? 0 : 1, 260, Easing.UI);
		int current = currentStyle();
		boolean isVoid = dev.mordo.kairo.core.Edition.isVoid();
		int count = isVoid ? 3 : 5;
		styleTileW = (w - 10 * (count - 1)) / count;
		String[] titles = {dev.mordo.kairo.core.Edition.isVoid() ? "Stage" : "Kairo", "Dock", "Orbit", "Custom", "Minecraft"};
		String[] about = {dev.mordo.kairo.core.Edition.isVoid() ? "Your player in a spotlight, your buttons beside them" : "Rows, with cards for your last world and server", "A big clock and a dock that magnifies",
			"Your menu as planets in orbit", "Your own home screen: widgets you place and resize", "Minecraft's own title screen, restyled"};
		if (isVoid) {
			// Void: Stage, Simple, Minecraft (drawn with the matching little pictures).
			titles = new String[]{"Stage", "Simple", "Minecraft", "", "", "Simple"};
			about = new String[]{"Your player in a spotlight, your buttons beside them", "Just the name and a column of words over space",
				"Minecraft's own title screen, restyled", "", "", "Just the name and a column of words over space"};
		}
		int hovered = -1;
		for (int i = 0; i < count; i++) {
			float tx = x + i * (styleTileW + 10);
			int code = isVoid ? VOID_CODES[i] : i;
			if (mx >= tx && mx <= tx + styleTileW && my >= styleY && my <= styleY + 96) {
				hovered = code;
			}
			styleTile(g, tx, styleY, styleTileW, titles[code], "", code, current == code, mx, my, a);
		}
		// One line about the hovered style (or the chosen one) instead of a cramped line in every tile.
		int shown = hovered >= 0 ? hovered : current;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, titles[shown], x + 2, styleY + 96 + 16, 12, Theme.text(0.9f, a));
		float nameW = Fonts.width(Typeface.OUTFIT_SEMIBOLD, titles[shown], 12);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, "·  " + about[shown], 11.5f, w - nameW - 12), x + nameW + 10,
			styleY + 96 + 16, 11.5f, Theme.text(0.55f, a));

		// Below the styles: only what the chosen style actually uses.
		float below = styleY + 96 + 26 + 22;
		if (current != 0) {
			rowsTop = -100_000; // no button rows to drag
			drawStyleExtras(g, current, x, below, w, y + h - below, mx, my, a, live);
			return;
		}

		// Buttons (Kairo style).
		float by = below;
		label(g, "BUTTONS", x, by, a);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Drag to reorder · eye to hide", x + w, by, 10.5f, Theme.text(0.45f, a), Fonts.Align.RIGHT, 0, false);
		rowsTop = by + 16;
		MainMenuSettings menu = MainMenuSettings.INSTANCE;
		for (int i = 0; i < order.size(); i++) {
			String id = order.get(i);
			Animation anim = rowY.computeIfAbsent(id, k -> new Animation(0));
			float target = i * (ROW_H + ROW_GAP);
			if (Math.abs(anim.target() - target) > 0.1f) {
				anim.animateTo(target, 200, Easing.UI);
			}
		}
		// Draw the dragged row last so it floats above the others.
		for (String id : order) {
			if (!id.equals(dragging)) {
				buttonRow(g, id, x, rowsTop + rowY.get(id).value(), w, mx, my, a, live, false);
			}
		}
		if (dragging != null) {
			buttonRow(g, dragging, x, dragY, w, mx, my, a, live, true);
		}

		// Cards.
		float cy = rowsTop + MainMenuSettings.BUTTONS.size() * (ROW_H + ROW_GAP) + 22;
		label(g, "CARDS", x, cy, a);
		cardsList.render(g, x, cy + 10, w + 14, Math.max(60, y + h - cy - 10), mx, my, a, live && dragging == null);
	}

	/** Dock, Orbit, Custom and Minecraft: a short note, and the one or two things that style has. */
	private void drawStyleExtras(GuiGraphicsExtractor g, int style, float x, float y, float w, float h, float mx, float my, float a, boolean live) {
		switch (style) {
			case 5 -> {
				label(g, "ABOUT THIS STYLE", x, y, a);
				note(g, x, y + 22, w, "Nothing but the name and your menu as plain words, centred over the drawn space scene. "
					+ "Point at a word and it lights up in your colour.", a);
			}
			case 1, 2 -> {
				label(g, "ABOUT THIS STYLE", x, y, a);
				note(g, x, y + 22, w, style == 1
					? "Your menu sits in a dock under a big clock. Continue and Join chips take you back to your last world and first server."
					: "Your menu orbits a glowing core: scroll or drag to turn it, click a planet to open it.", a);
			}
			case 3 -> {
				label(g, "YOUR HOME SCREEN", x, y, a);
				note(g, x, y + 22, w, "Put together your own main menu from widgets: buttons (bars or squares), clock, worlds, "
					+ "servers you pick, your skin, text, a PvP switch and more. Drag them around and resize them like tiles.", a);
				float bw = editHome.preferredWidth() + 16;
				editHome.bounds(x, y + 120, bw, 40);
				put(editHome, g, mx, my, a, live);
			}
			default -> {
				label(g, "MINECRAFT STYLE", x, y, a);
				minecraftList.render(g, x, y + 10, w + 14, Math.max(60, h - 10), mx, my, a, live);
			}
		}
	}

	private static void note(GuiGraphicsExtractor g, float x, float y, float w, String text, float a) {
		List<String> lines = Fonts.wrap(Typeface.OUTFIT_REGULAR, text, 12.5f, w);
		for (int i = 0; i < lines.size(); i++) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, lines.get(i), x, y + 10 + i * 18, 12.5f, Theme.text(0.7f, a));
		}
	}

	/** Void's three tiles, as the codes below: Stage (0), Simple (5), Minecraft (4). */
	private static final int[] VOID_CODES = {0, 5, 4};

	/** 0 Kairo (Stage in Void), 1 Dock, 2 Orbit, 3 Custom, 4 Minecraft, 5 Simple (Void). */
	private static int currentStyle() {
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			return VOID_CODES[MainMenuSettings.voidStyle()];
		}
		return ClientSettings.INSTANCE.customMainMenu.isOn() ? MainMenuSettings.INSTANCE.layout.get().ordinal() : 4;
	}

	private void styleTile(GuiGraphicsExtractor g, float x, float y, float w, String title, String subtitle, int kind,
						   boolean selected, float mx, float my, float a) {
		float h = 96;
		boolean over = mx >= x && mx <= x + w && my >= y && my <= y + h;
		int accent = Theme.accent();
		int stroke = selected ? Colors.withAlpha(accent, 0.9f) : over ? 0x40FFFFFF : 0x1FFFFFFF;
		Shapes.rect(g, x, y, w, h, 14, Colors.argb((selected ? 0.12f : over ? 0.08f : 0.05f) * a, 0xFFFFFF),
			Colors.multiplyAlpha(stroke, a), selected ? 1.75f : 1);
		// A tiny drawing of each layout.
		// The little drawings are about 72 wide: centre them in the tile.
		float ax = x + Math.max(8, (w - 72) / 2);
		float ay = y + 16;
		if (kind == 1) {
			// Dock: clock and a row of icons at the bottom.
			float mid = x + w / 2;
			Shapes.rect(g, mid - 18, ay + 2, 36, 12, 3, Colors.argb(0.8f * a, 0xFFFFFF));
			Shapes.rect(g, mid - 14, ay + 18, 28, 3, 1.5f, Colors.argb(0.3f * a, 0xFFFFFF));
			Shapes.rect(g, mid - 30, ay + 30, 60, 12, 5, Colors.argb(0.14f * a, 0xFFFFFF));
			for (int i = 0; i < 5; i++) {
				float s = i == 2 ? 10 : 7;
				Shapes.rect(g, mid - 26 + i * 11 + (10 - s) / 2, ay + 36 - s / 2, s, s, 2, Colors.multiplyAlpha(i == 2 ? Colors.withAlpha(accent, 0.9f) : 0x66FFFFFF, a));
			}
		} else if (kind == 2) {
			// Orbit: a tilted ring of planets around a glowing core.
			float mid = x + w / 2;
			float cyy = ay + 22;
			for (int i = 0; i < 24; i++) {
				double t = i * Math.PI * 2 / 24;
				Shapes.circle(g, mid + (float) Math.cos(t) * 30, cyy + (float) Math.sin(t) * 9, 0.7f, Colors.argb(0.35f * a, 0xFFFFFF));
			}
			Shapes.circle(g, mid, cyy, 6, Colors.multiplyAlpha(accent, a));
			for (int i = 0; i < 5; i++) {
				double t = Math.PI / 2 + i * Math.PI * 2 / 5;
				float depth = (float) Math.sin(t);
				Shapes.circle(g, mid + (float) Math.cos(t) * 30, cyy + depth * 9, 3 + depth * 1.2f,
					Colors.multiplyAlpha(i == 0 ? Colors.withAlpha(accent, 0.9f) : 0x80FFFFFF, a));
			}
		} else if (kind == 0) {
			Shapes.rect(g, ax, ay, 26, 5, 2, Colors.argb(0.8f * a, 0xFFFFFF));
			for (int i = 0; i < 4; i++) {
				Shapes.rect(g, ax, ay + 10 + i * 8, 34, 5, 2, Colors.multiplyAlpha(i == 0 ? Colors.withAlpha(accent, 0.8f) : 0x40FFFFFF, a));
			}
			Shapes.rect(g, ax + 48, ay + 10, 22, 13, 3, Colors.argb(0.22f * a, 0xFFFFFF));
			Shapes.rect(g, ax + 48, ay + 26, 22, 13, 3, Colors.argb(0.22f * a, 0xFFFFFF));
		} else if (kind == 5) {
			// Simple: a centred title and a short column of words.
			float mid = x + w / 2;
			Shapes.rect(g, mid - 22, ay + 2, 44, 7, 3, Colors.argb(0.85f * a, 0xFFFFFF));
			for (int i = 0; i < 4; i++) {
				float lw = 26 - (i % 2) * 6;
				Shapes.rect(g, mid - lw / 2, ay + 16 + i * 7, lw, 3, 1.5f, Colors.multiplyAlpha(i == 0 ? Colors.withAlpha(accent, 0.9f) : 0x50FFFFFF, a));
			}
		} else if (kind == 3) {
			// Dashboard: a grid of tiles, one with a figure in it.
			float gw = Math.min(70, w - 28);
			float c = (gw - 8) / 3;
			Shapes.rect(g, ax, ay, c, 16, 3, Colors.argb(0.75f * a, 0xFFFFFF));
			Shapes.rect(g, ax, ay + 20, c, 22, 3, Colors.argb(0.22f * a, 0xFFFFFF));
			Shapes.rect(g, ax + c + 4, ay, c, 12, 3, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.8f), a));
			Shapes.rect(g, ax + c + 4, ay + 16, c, 12, 3, Colors.argb(0.22f * a, 0xFFFFFF));
			Shapes.rect(g, ax + c + 4, ay + 32, c, 10, 3, Colors.argb(0.22f * a, 0xFFFFFF));
			Shapes.rect(g, ax + (c + 4) * 2, ay, c, 42, 3, Colors.argb(0.16f * a, 0xFFFFFF));
			float fx = ax + (c + 4) * 2 + c / 2;
			Shapes.rect(g, fx - 3, ay + 8, 6, 6, 1, Colors.argb(0.8f * a, 0xFFFFFF));
			Shapes.rect(g, fx - 4, ay + 15, 8, 12, 1, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.8f), a));
			Shapes.rect(g, fx - 4, ay + 28, 8, 9, 1, Colors.argb(0.5f * a, 0xFFFFFF));
		} else {
			Shapes.rect(g, ax + 20, ay, 30, 7, 2, Colors.argb(0.8f * a, 0xFFFFFF));
			for (int i = 0; i < 3; i++) {
				Shapes.rect(g, ax + 10, ay + 12 + i * 8, 50, 5, 2, Colors.argb(0.3f * a, 0xFFFFFF));
			}
			Shapes.rect(g, ax + 10, ay + 36, 24, 5, 2, Colors.argb(0.3f * a, 0xFFFFFF));
			Shapes.rect(g, ax + 36, ay + 36, 24, 5, 2, Colors.argb(0.3f * a, 0xFFFFFF));
		}
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, title, 13.5f, w - 12), x + w / 2, y + h - 18, 13.5f,
			Theme.text(1, a), Fonts.Align.CENTER, 0, false);
		if (selected) {
			Shapes.circle(g, x + w - 12, y + 12, 7, Colors.multiplyAlpha(accent, a));
			IconRenderer.draw(g, Icon.CHECK, x + w - 12, y + 12, 9, Colors.multiplyAlpha(Theme.DARK_TEXT, a), 3f);
		}
	}

	private void buttonRow(GuiGraphicsExtractor g, String id, float x, float y, float w, float mx, float my, float a, boolean live, boolean lifted) {
		boolean hidden = MainMenuSettings.INSTANCE.isHidden(id);
		boolean over = mx >= x && mx <= x + w && my >= y && my <= y + ROW_H;
		if (lifted) {
			Shapes.shadow(g, x, y + 8, w, ROW_H, 12, 16, Colors.argb(0.45f * a, 0));
		}
		int fill = lifted ? Colors.argb(0.16f, 0xFFFFFF) : over && dragging == null ? Colors.argb(0.09f, 0xFFFFFF) : Colors.argb(0.05f, 0xFFFFFF);
		Shapes.rect(g, x, y, w, ROW_H, 12, Colors.multiplyAlpha(fill, a),
			Colors.multiplyAlpha(lifted ? Colors.withAlpha(Theme.accent(), 0.7f) : 0x1AFFFFFF, a), 1);
		float alpha = a * (hidden ? 0.4f : 1);
		// Drag handle.
		for (int i = 0; i < 3; i++) {
			Shapes.rect(g, x + 14, y + ROW_H / 2 - 5 + i * 5, 12, 1.6f, 0.8f, Theme.text(0.45f, a));
		}
		IconRenderer.draw(g, iconOf(id), x + 48, y + ROW_H / 2, 18, Theme.text(0.9f, alpha), 1.9f);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, titleOf(id), x + 68, y + ROW_H / 2, 13.5f, Theme.text(1, alpha));
		if (hidden) {
			Fonts.draw(g, Typeface.MONO_BOLD, "HIDDEN", x + w - 52, y + ROW_H / 2, 7.5f, Theme.text(0.4f, a), Fonts.Align.RIGHT, 1.4f, false);
		}
		Button eye = hidden ? eyesOff.get(id) : eyes.get(id);
		eye.bounds(x + w - 42, y + (ROW_H - 34) / 2, 34, 34);
		put(eye, g, mx, my, a, live && dragging == null);
	}

	private void drawRight(GuiGraphicsExtractor g, float x, float y, float w, float h, float mx, float my, float a, boolean live) {
		label(g, "BACKGROUND", x, y + 6, a);
		float ofw = openFolder.preferredWidth() + 4;
		openFolder.bounds(x + w - ofw, y - 6, ofw, 26);
		put(openFolder, g, mx, my, a, live);

		float settingsH = Math.min(320, h * 0.48f);
		gridX = x;
		gridW = w;
		gridTop = y + 30;
		gridBottom = y + h - settingsH - 16;
		int columns = w > 620 ? 4 : 3;
		float gap = 12;
		tileW = (w - 14 - gap * (columns - 1)) / columns;
		tileH = tileW * 9 / 16 + 26;
		entries = Backgrounds.list();
		int count = entries.size() + 2; // plus the two "add" tiles
		int rows = (count + columns - 1) / columns;
		gridScroll.setSizes(rows * (tileH + gap) - gap + 4, gridBottom - gridTop);

		String selected = MainMenuSettings.INSTANCE.background.get();
		g.enableScissor((int) Math.floor(x - 8), (int) Math.floor(gridTop - 6), (int) Math.ceil(x + w + 8), (int) Math.ceil(gridBottom));
		try {
			for (int i = 0; i < count; i++) {
				float tx = x + (i % columns) * (tileW + gap);
				float ty = gridTop + (i / columns) * (tileH + gap) - gridScroll.value();
				if (ty + tileH < gridTop - 6 || ty > gridBottom) {
					continue;
				}
				boolean visible = my >= gridTop && my <= gridBottom;
				if (i < entries.size()) {
					Backgrounds.Entry e = entries.get(i);
					backgroundTile(g, e, tx, ty, e.id().equals(selected), mx, my, a, live && visible);
				} else {
					boolean image = i == entries.size();
					addTile(g, tx, ty, image ? "Image or GIF" : "Panorama folder", image ? Icon.IMAGE : Icon.GLOBE, mx, my, a);
				}
			}
		} finally {
			g.disableScissor();
		}
		gridScroll.drawBar(g, x + w + 6, gridTop, a);

		Shapes.rect(g, x, gridBottom + 8, w, 1, 0, Colors.argb(0.08f * a, 0xFFFFFF));
		backgroundList.render(g, x, gridBottom + 16, w + 14, settingsH, mx, my, a, live && dragging == null);
	}

	private void backgroundTile(GuiGraphicsExtractor g, Backgrounds.Entry e, float x, float y, boolean selected, float mx, float my, float a, boolean live) {
		boolean over = mx >= x && mx <= x + tileW && my >= y && my <= y + tileH;
		Animation hv = tileHover.computeIfAbsent(e.id(), k -> new Animation(0));
		hv.animateTo(over ? 1 : 0, 140);
		float lift = Animation.motion(hv.value() * 2, 0);
		y -= lift;
		float imgH = tileW * 9 / 16;
		Identifier thumb = Backgrounds.thumbnail(e);
		if (thumb != null && a > 0.5f) {
			g.blit(thumb, Math.round(x), Math.round(y), Math.round(x + tileW), Math.round(y + imgH), 0, 1, 0, 1);
		} else {
			float pulse = 0.06f + 0.03f * (float) Math.sin(System.currentTimeMillis() / 220.0);
			Shapes.rect(g, x, y, tileW, imgH, 10, Colors.argb(pulse * a, 0xFFFFFF));
		}
		int accent = Theme.accent();
		int stroke = selected ? Colors.withAlpha(accent, 0.95f) : Colors.lerp(0x24FFFFFF, 0x66FFFFFF, hv.value());
		Shapes.outline(g, x - 1, y - 1, tileW + 2, imgH + 2, 10, Colors.multiplyAlpha(stroke, a), selected ? 2.25f : 1.25f);
		String tag = switch (e.kind()) {
			case PANORAMA -> "PANORAMA";
			case CUBE -> "PANORAMA";
			case GIF -> "GIF";
			case IMAGE -> "IMAGE";
		};
		float tagW = Fonts.width(Typeface.MONO_BOLD, tag, 7, 1) + 10;
		Shapes.rect(g, x + 6, y + 6, tagW, 14, 5, Colors.argb(0.7f * a, Theme.tintBackground(0xFF0C1018)));
		Fonts.draw(g, Typeface.MONO_BOLD, tag, x + 6 + tagW / 2, y + 13, 7, Theme.text(0.85f, a), Fonts.Align.CENTER, 1, false);
		if (selected) {
			Shapes.circle(g, x + tileW - 14, y + 14, 8, Colors.multiplyAlpha(accent, a));
			IconRenderer.draw(g, Icon.CHECK, x + tileW - 14, y + 14, 11, Colors.multiplyAlpha(Theme.DARK_TEXT, a), 3f);
		}
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, Fonts.ellipsize(Typeface.OUTFIT_MEDIUM, e.label(), 11.5f, tileW - 30), x + 2, y + imgH + 14, 11.5f,
			selected ? Colors.multiplyAlpha(accent, a) : Theme.text(0.85f, a));
		if (e.kind() != Backgrounds.Kind.PANORAMA) {
			Button del = tileDelete.computeIfAbsent(e.id(), k -> new Button("", Icon.TRASH, Button.Style.GHOST, () -> askRemove(e)));
			del.bounds(x + tileW - 24, y + imgH + 3, 24, 22);
			if (hv.value() > 0.05f) {
				put(del, g, mx, my, a * hv.value(), live);
			}
		}
	}

	private void addTile(GuiGraphicsExtractor g, float x, float y, String label, Icon icon, float mx, float my, float a) {
		boolean over = mx >= x && mx <= x + tileW && my >= y && my <= y + tileH;
		float imgH = tileW * 9 / 16;
		Shapes.rect(g, x, y, tileW, imgH, 10, Colors.argb((over ? 0.1f : 0.05f) * a, 0xFFFFFF),
			Colors.multiplyAlpha(over ? Colors.withAlpha(Theme.accent(), 0.7f) : 0x26FFFFFF, a), 1.25f);
		IconRenderer.draw(g, Icon.PLUS, x + tileW / 2, y + imgH / 2 - 8, 18, Colors.multiplyAlpha(over ? Theme.accent() : Theme.text(0.7f), a), 2.2f);
		Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, label, x + tileW / 2, y + imgH / 2 + 14, 10.5f, Theme.text(0.65f, a));
		IconRenderer.draw(g, icon, x + 10, y + imgH + 14, 12, Theme.text(0.5f, a), 2f);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, "Add…", x + 22, y + imgH + 14, 11.5f, Theme.text(0.6f, a));
	}

	// ---- actions ----

	private void toggleHidden(String id) {
		MainMenuSettings menu = MainMenuSettings.INSTANCE;
		boolean hide = !menu.isHidden(id);
		if (hide) {
			long visible = MainMenuSettings.BUTTONS.stream().filter(b -> !menu.isHidden(b)).count();
			if (visible <= 1) {
				Toasts.show("Keep at least one button", 0xFFFF7A7A);
				return;
			}
		}
		menu.setHidden(id, hide);
		UiSounds.toggle(!hide);
	}

	private void askRemove(Backgrounds.Entry e) {
		confirm("Remove \"" + e.label() + "\"?", "Deletes it from Kairo's backgrounds folder. The file you picked originally stays where it was.",
			"Remove", true, () -> {
				try {
					Path p = e.path();
					if (Files.isDirectory(p)) {
						try (Stream<Path> walk = Files.walk(p)) {
							for (Path f : walk.sorted(Comparator.reverseOrder()).toList()) {
								Files.deleteIfExists(f);
							}
						}
					} else {
						Files.deleteIfExists(p);
					}
				} catch (IOException ex) {
					Kairo.LOGGER.warn("Couldn't remove background {}", e.path(), ex);
					Toasts.show("Couldn't remove it", 0xFFFF7A7A);
				}
				if (MainMenuSettings.INSTANCE.background.get().equals(e.id())) {
					MainMenuSettings.INSTANCE.background.set(MainMenuSettings.VANILLA_BACKGROUND);
				}
				Backgrounds.rescanSoon();
				Backgrounds.clearThumbnails();
			});
	}

	// ---- input ----

	@Override
	protected boolean onContentClick(float x, float y, int button) {
		if (button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		// Style tiles.
		if (y >= styleY && y <= styleY + 96 && x >= leftX + PANEL_PAD && x <= leftX + leftW - PANEL_PAD) {
			if (dev.mordo.kairo.core.Edition.isVoid()) {
				int index = Math.clamp((int) ((x - leftX - PANEL_PAD) / (styleTileW + 10)), 0, 2);
				if (VOID_CODES[index] != currentStyle()) {
					MainMenuSettings.setVoidStyle(index);
					UiSounds.click();
				}
				return true;
			}
			int kind = Math.clamp((int) ((x - leftX - PANEL_PAD) / (styleTileW + 10)), 0, 4);
			if (kind != currentStyle()) {
				if (kind == 4) {
					ClientSettings.INSTANCE.customMainMenu.set(false);
				} else {
					ClientSettings.INSTANCE.customMainMenu.set(true);
					MainMenuSettings.INSTANCE.layout.set(MainMenuSettings.Layout.values()[kind]);
				}
				UiSounds.click();
			}
			return true;
		}
		// Button rows: start dragging.
		float rowsX = leftX + PANEL_PAD;
		float rowsW = leftW - PANEL_PAD * 2;
		if (x >= rowsX && x <= rowsX + rowsW && y >= rowsTop && y <= rowsTop + order.size() * (ROW_H + ROW_GAP)) {
			int index = (int) ((y - rowsTop) / (ROW_H + ROW_GAP));
			if (index >= 0 && index < order.size()) {
				dragging = order.get(index);
				float rowTop = rowsTop + index * (ROW_H + ROW_GAP);
				grabOffset = y - rowTop;
				dragY = rowTop;
				UiSounds.click();
				return true;
			}
		}
		// Background tiles.
		if (y >= gridTop && y <= gridBottom && x >= gridX && x <= gridX + gridW) {
			int columns = gridW > 620 ? 4 : 3;
			float gap = 12;
			int col = (int) ((x - gridX) / (tileW + gap));
			int row = (int) ((y - gridTop + gridScroll.value()) / (tileH + gap));
			float inX = (x - gridX) - col * (tileW + gap);
			float inY = (y - gridTop + gridScroll.value()) - row * (tileH + gap);
			if (col < columns && inX <= tileW && inY <= tileH) {
				int index = row * columns + col;
				if (index < entries.size()) {
					MainMenuSettings.INSTANCE.background.set(entries.get(index).id());
					UiSounds.click();
				} else if (index == entries.size()) {
					UiSounds.click();
					Backgrounds.importImage();
				} else if (index == entries.size() + 1) {
					UiSounds.click();
					Backgrounds.importPanorama();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	protected boolean onDrag(float x, float y, int button, float dx, float dy) {
		if (dragging == null) {
			return super.onDrag(x, y, button, dx, dy);
		}
		float max = rowsTop + (order.size() - 1) * (ROW_H + ROW_GAP);
		dragY = Math.clamp(y - grabOffset, rowsTop, max);
		int target = Math.clamp(Math.round((dragY - rowsTop) / (ROW_H + ROW_GAP)), 0, order.size() - 1);
		int current = order.indexOf(dragging);
		if (target != current) {
			order.remove(current);
			order.add(target, dragging);
			UiSounds.hover();
		}
		return true;
	}

	@Override
	protected boolean onRelease(float x, float y, int button) {
		if (dragging != null) {
			// Settle from where it was dropped.
			rowY.get(dragging).set(dragY - rowsTop);
			dragging = null;
			MainMenuSettings.INSTANCE.setButtonOrder(order);
			return true;
		}
		return super.onRelease(x, y, button);
	}

	@Override
	protected boolean onContentScroll(float x, float y, float amount) {
		if (y >= gridTop && y <= gridBottom && x >= gridX) {
			gridScroll.scroll(amount);
			return true;
		}
		return backgroundList.mouseScrolled(x, y, amount) || (currentStyle() == 0 ? cardsList.mouseScrolled(x, y, amount)
			: currentStyle() == 4 && minecraftList.mouseScrolled(x, y, amount));
	}

	// ---- shared with the main menu ----

	static Icon iconOf(String id) {
		return switch (id) {
			case "singleplayer" -> Icon.BOX;
			case "multiplayer" -> Icon.GLOBE;
			case "client" -> Icon.TOTEM;
			case "options" -> Icon.SLIDERS;
			default -> Icon.POWER;
		};
	}

	static String titleOf(String id) {
		return switch (id) {
			case "singleplayer" -> "Singleplayer";
			case "multiplayer" -> "Multiplayer";
			case "client" -> ClientInfo.NAME;
			case "options" -> "Options";
			default -> "Quit game";
		};
	}
}
