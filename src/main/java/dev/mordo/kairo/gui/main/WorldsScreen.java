package dev.mordo.kairo.gui.main;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ScrollState;
import dev.mordo.kairo.gui.widgets.TextField;
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
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.EditWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.level.storage.LevelStorageSource;
import net.minecraft.world.level.storage.LevelSummary;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Singleplayer: your worlds as glass cards (icon, name, mode, last played), searchable. Click to select,
 * double-click or Play to open; Edit and Delete appear on hover. Create opens Minecraft's world creation.
 */
public final class WorldsScreen extends MainMenuBase {
	private static final float CARD_H = 104;
	private static final float GAP = 14;

	private final TextField search;
	private final Button create;
	private final Button createEmpty;
	private final ScrollState scroll = new ScrollState();
	private List<LevelSummary> all = List.of();
	private final List<LevelSummary> shown = new ArrayList<>();
	private boolean loaded;
	private @Nullable String selectedId;
	private long lastClickTime;
	private @Nullable String lastClickId;
	private final Map<String, CardWidgets> widgets = new HashMap<>();
	private final Map<String, Animation> appear = new HashMap<>();
	private float listTop;
	private float listBottom;
	private float listX;
	private float listW;
	private int columns = 2;

	private final class CardWidgets {
		final Button play;
		final Button edit;
		final Button delete;
		final Animation hover = new Animation(0);
		final Animation selected = new Animation(0);

		CardWidgets(LevelSummary s) {
			play = new Button("Play", Icon.PLAY, Button.Style.PRIMARY, () -> play(s)).textSize(11.5f);
			edit = new Button("", Icon.PENCIL, Button.Style.SUBTLE, () -> edit(s));
			delete = new Button("", Icon.TRASH, Button.Style.DANGER, () -> askDelete(s));
		}
	}

	public WorldsScreen(@Nullable Screen parent) {
		super(Component.literal("Singleplayer"), parent);
		search = new TextField("Search worlds", Icon.SEARCH).maxLength(64).onChange(t -> refilter());
		create = new Button("Create world", Icon.PLUS, Button.Style.PRIMARY, this::createWorld).textSize(12);
		createEmpty = new Button("Create your first world", Icon.PLUS, Button.Style.PRIMARY, this::createWorld).textSize(12.5f);
	}

	@Override
	protected void onShown() {
		reload();
	}

	private void reload() {
		Minecraft mc = Minecraft.getInstance();
		loaded = false;
		widgets.clear();
		appear.clear();
		Favicons.forgetAllWorlds();
		try {
			LevelStorageSource source = mc.getLevelSource();
			source.loadLevelSummaries(source.findLevelCandidates()).thenAcceptAsync(list -> {
				all = list;
				loaded = true;
				refilter();
			}, mc).exceptionally(t -> {
				Kairo.LOGGER.warn("Couldn't load worlds", t);
				loaded = true;
				return null;
			});
		} catch (Exception e) {
			Kairo.LOGGER.warn("Couldn't list worlds", e);
			all = List.of();
			loaded = true;
			refilter();
		}
	}

	private void refilter() {
		shown.clear();
		String q = search.text().trim().toLowerCase(Locale.ROOT);
		for (LevelSummary s : all) {
			if (q.isEmpty() || s.getLevelName().toLowerCase(Locale.ROOT).contains(q) || s.getLevelId().toLowerCase(Locale.ROOT).contains(q)) {
				shown.add(s);
			}
		}
		for (int i = 0; i < shown.size(); i++) {
			appear.computeIfAbsent(shown.get(i).getLevelId(), id -> new Animation(0)).animateTo(1, 380, Easing.UI, Math.min(i, 12) * 35);
		}
		if (selectedId == null && !shown.isEmpty()) {
			selectedId = shown.getFirst().getLevelId();
		}
		scroll.reset();
	}

	// ---- actions ----

	private void createWorld() {
		UiSounds.click();
		Minecraft mc = Minecraft.getInstance();
		CreateWorldScreen.openFresh(mc, () -> mc.gui.setScreen(this));
	}

	private void play(LevelSummary s) {
		if (!s.primaryActionActive()) {
			return;
		}
		UiSounds.click();
		Minecraft mc = Minecraft.getInstance();
		mc.createWorldOpenFlows().openWorld(s.getLevelId(), () -> mc.gui.setScreen(this));
	}

	private void edit(LevelSummary s) {
		Minecraft mc = Minecraft.getInstance();
		UiSounds.click();
		try {
			LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().validateAndCreateAccess(s.getLevelId());
			mc.gui.setScreen(EditWorldScreen.create(mc, access, changed -> {
				access.safeClose();
				mc.gui.setScreen(this);
			}));
		} catch (Exception e) {
			Kairo.LOGGER.warn("Couldn't open world {} for editing", s.getLevelId(), e);
			Toasts.show("Couldn't open that world for editing", 0xFFFF7A7A);
		}
	}

	private void askDelete(LevelSummary s) {
		UiSounds.click();
		confirm("Delete \"" + s.getLevelName() + "\"?", "The world will be gone forever. This can't be undone.", "Delete", true, () -> {
			Minecraft mc = Minecraft.getInstance();
			String id = s.getLevelId();
			Util.backgroundExecutor().execute(() -> {
				try (LevelStorageSource.LevelStorageAccess access = mc.getLevelSource().createAccess(id)) {
					access.deleteLevel();
					mc.execute(() -> {
						Toasts.show("Deleted \"" + s.getLevelName() + "\"", Theme.accent());
						if (id.equals(selectedId)) {
							selectedId = null;
						}
						reload();
					});
				} catch (Exception e) {
					Kairo.LOGGER.warn("Couldn't delete world {}", id, e);
					mc.execute(() -> Toasts.show("Couldn't delete that world", 0xFFFF7A7A));
				}
			});
		});
	}

	// ---- drawing ----

	@Override
	protected void drawContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float left = Math.max(48, viewWidth * 0.06f);
		float headerBottom = drawHeader(g, left, 42, "Singleplayer",
			!loaded ? "Loading…" : all.size() + (all.size() == 1 ? " world" : " worlds"), mx, my, fade, live);

		float createW = create.preferredWidth() + 10;
		create.bounds(viewWidth - left - createW, 44, createW, 38);
		put(create, g, mx, my, fade, live);
		search.bounds(create.x - 12 - 280, 44, 280, 38);
		put(search, g, mx, my, fade, live);

		listTop = headerBottom + 34;
		listBottom = viewHeight - 32;
		listX = left;
		listW = viewWidth - left * 2;
		columns = listW >= 980 ? 2 : 1;
		float cardW = (listW - GAP * (columns - 1)) / columns;

		if (!loaded) {
			for (int i = 0; i < columns * 3; i++) {
				float cx = listX + (i % columns) * (cardW + GAP);
				float cy = listTop + (i / columns) * (CARD_H + GAP);
				float pulse = 0.05f + 0.03f * (float) Math.sin(System.currentTimeMillis() / 220.0 + i * 0.5);
				Shapes.rect(g, cx, cy, cardW, CARD_H, 18, Colors.argb(pulse * fade, 0xFFFFFF));
			}
			return;
		}
		if (all.isEmpty()) {
			drawEmpty(g, mx, my, fade, live);
			return;
		}
		if (shown.isEmpty()) {
			Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, "No worlds match \"" + search.text() + "\"", viewWidth / 2, listTop + 60, 14, Theme.text(0.55f, fade));
			return;
		}

		int rowsCount = (shown.size() + columns - 1) / columns;
		scroll.setSizes(rowsCount * (CARD_H + GAP) - GAP + 12, listBottom - listTop);
		g.enableScissor((int) Math.floor(listX - 12), (int) Math.floor(listTop - 8), (int) Math.ceil(listX + listW + 12), (int) Math.ceil(listBottom));
		try {
			for (int i = 0; i < shown.size(); i++) {
				LevelSummary s = shown.get(i);
				float cx = listX + (i % columns) * (cardW + GAP);
				float cy = listTop + 4 + (i / columns) * (CARD_H + GAP) - scroll.value();
				if (cy + CARD_H < listTop - 8 || cy > listBottom) {
					continue;
				}
				drawCard(g, s, cx, cy, cardW, mx, my, fade, live && my >= listTop && my <= listBottom);
			}
		} finally {
			g.disableScissor();
		}
		scroll.drawBar(g, listX + listW + 10, listTop, fade);
	}

	private void drawCard(GuiGraphicsExtractor g, LevelSummary s, float x, float y, float w, float mx, float my, float fade, boolean live) {
		CardWidgets cw = widgets.computeIfAbsent(s.getLevelId(), id -> new CardWidgets(s));
		float ap = appear.getOrDefault(s.getLevelId(), new Animation(1)).value();
		float a = fade * ap;
		y += Animation.motion((1 - ap) * 14, 0);
		boolean over = live && mx >= x && mx <= x + w && my >= y && my <= y + CARD_H;
		cw.hover.animateTo(over ? 1 : 0, 160);
		boolean isSelected = s.getLevelId().equals(selectedId);
		cw.selected.animateTo(isSelected ? 1 : 0, 200);
		float hv = cw.hover.value();
		float sel = cw.selected.value();
		float lift = Animation.motion(hv * 2, 0);
		y -= lift;

		if (hv > 0.01f) {
			Shapes.shadow(g, x, y + 8, w, CARD_H, 18, 18, Colors.argb(0.3f * hv * a, 0));
		}
		int stroke = Colors.lerp(Colors.lerp(0x24FFFFFF, 0x40FFFFFF, hv), Colors.withAlpha(Theme.accent(), 0.7f), sel);
		Shapes.glassRect(g, x, y, w, CARD_H, 18, Theme.glassTint(), stroke, 1.25f + sel * 0.5f, a);

		float icon = 72;
		Favicons.draw(g, Favicons.world(s), Icon.BOX, x + 16, y + (CARD_H - icon) / 2, icon, a);
		float tx = x + 16 + icon + 18;

		// Hover actions on the right.
		float bh = 34;
		float by = y + (CARD_H - bh) / 2;
		float playW = cw.play.preferredWidth() + 8;
		float actionsW = playW + 8 + bh + 6 + bh;
		float actionsA = a * Math.max(hv, sel);
		cw.delete.bounds(x + w - 16 - bh, by, bh, bh);
		cw.edit.bounds(cw.delete.x - 6 - bh, by, bh, bh);
		cw.play.bounds(cw.edit.x - 8 - playW, by, playW, bh);
		cw.play.setEnabled(s.primaryActionActive());
		cw.edit.setEnabled(s.canEdit());
		cw.delete.setEnabled(s.canDelete());
		if (actionsA > 0.02f) {
			put(cw.play, g, mx, my, actionsA, live && Math.max(hv, sel) > 0.5f);
			put(cw.edit, g, mx, my, actionsA, live && Math.max(hv, sel) > 0.5f);
			put(cw.delete, g, mx, my, actionsA, live && Math.max(hv, sel) > 0.5f);
		}

		float textW = w - (tx - x) - 16 - actionsW * Math.max(hv, sel) - 12;
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, s.getLevelName(), 16.5f, textW), tx, y + 32, 16.5f, Theme.text(1, a));
		String mode = s.isHardcore() ? "Hardcore" : capitalize(s.getGameMode().getName());
		String info = mode + (s.hasCommands() ? "  ·  Cheats" : "") + "  ·  " + timeAgo(s.getLastPlayed());
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, info, 12, textW), tx, y + 55, 12, Theme.text(0.58f, a));

		// Small tags: version, hardcore, experimental, problems.
		float tagX = tx;
		tagX = tag(g, s.getWorldVersionName().getString(), tagX, y + 78, 0x33FFFFFF, Theme.text(0.6f), a);
		if (s.isHardcore()) {
			tagX = tag(g, "HARDCORE", tagX, y + 78, 0x40FF5A5A, 0xFFFF9A9A, a);
		}
		if (s.isExperimental()) {
			tagX = tag(g, "EXPERIMENTAL", tagX, y + 78, 0x40FFC145, 0xFFFFD27A, a);
		}
		if (!s.primaryActionActive() || s.isLocked()) {
			tag(g, Fonts.ellipsize(Typeface.MONO_BOLD, s.getInfo().getString().toUpperCase(Locale.ROOT), 7.5f, 220), tagX, y + 78, 0x40FF7A7A, 0xFFFFB0B0, a);
		}
	}

	private static float tag(GuiGraphicsExtractor g, String text, float x, float cy, int fill, int color, float a) {
		float w = Fonts.width(Typeface.MONO_BOLD, text, 7.5f, 1) + 12;
		Shapes.rect(g, x, cy - 8, w, 16, 5, Colors.multiplyAlpha(fill, a));
		Fonts.draw(g, Typeface.MONO_BOLD, text, x + w / 2, cy, 7.5f, Colors.multiplyAlpha(color, a), Fonts.Align.CENTER, 1, false);
		return x + w + 6;
	}

	private void drawEmpty(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float cx = viewWidth / 2;
		float cy = listTop + (listBottom - listTop) * 0.36f;
		Shapes.glassRect(g, cx - 48, cy - 48, 96, 96, 28, Theme.glassTint(), 0x2EFFFFFF, 1.25f, fade);
		IconRenderer.draw(g, Icon.BOX, cx, cy, 40, Colors.multiplyAlpha(Theme.accent(), fade), 1.8f);
		Fonts.drawCentered(g, Typeface.OUTFIT_BOLD, "No worlds yet", cx, cy + 86, 22, Theme.text(1, fade));
		Fonts.drawCentered(g, Typeface.OUTFIT_REGULAR, "Your worlds will show up here.", cx, cy + 114, 13, Theme.text(0.55f, fade));
		float bw = createEmpty.preferredWidth() + 16;
		createEmpty.bounds(cx - bw / 2, cy + 140, bw, 40);
		put(createEmpty, g, mx, my, fade, live);
	}

	// ---- input ----

	@Override
	protected boolean onContentClick(float x, float y, int button) {
		LevelSummary hit = cardAt(x, y);
		if (hit == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
			return false;
		}
		long now = System.currentTimeMillis();
		boolean doubleClick = hit.getLevelId().equals(lastClickId) && now - lastClickTime < 350;
		lastClickTime = now;
		lastClickId = hit.getLevelId();
		if (doubleClick) {
			play(hit);
		} else if (!hit.getLevelId().equals(selectedId)) {
			selectedId = hit.getLevelId();
			UiSounds.hover();
		}
		return true;
	}

	private @Nullable LevelSummary cardAt(float x, float y) {
		if (y < listTop || y > listBottom || x < listX || x > listX + listW) {
			return null;
		}
		float cardW = (listW - GAP * (columns - 1)) / columns;
		int col = (int) ((x - listX) / (cardW + GAP));
		int row = (int) ((y - listTop - 4 + scroll.value()) / (CARD_H + GAP));
		float inRowY = (y - listTop - 4 + scroll.value()) - row * (CARD_H + GAP);
		float inColX = (x - listX) - col * (cardW + GAP);
		if (inRowY > CARD_H || inColX > cardW || col >= columns) {
			return null;
		}
		int index = row * columns + col;
		return index >= 0 && index < shown.size() ? shown.get(index) : null;
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
		int index = 0;
		for (int i = 0; i < shown.size(); i++) {
			if (shown.get(i).getLevelId().equals(selectedId)) {
				index = i;
			}
		}
		int move = event.isDown() ? columns : event.isUp() ? -columns : event.isRight() ? 1 : event.isLeft() ? -1 : 0;
		if (move != 0) {
			selectedId = shown.get(Math.clamp(index + move, 0, shown.size() - 1)).getLevelId();
			UiSounds.hover();
			return true;
		}
		if (event.isConfirmation()) {
			play(shown.get(index));
			return true;
		}
		if (event.key() == InputConstants.KEY_DELETE) {
			askDelete(shown.get(index));
			return true;
		}
		return false;
	}

	/** Typing anywhere starts a search. */
	@Override
	public boolean charTyped(net.minecraft.client.input.CharacterEvent event) {
		if (!host.isFocused(search) && Character.isLetterOrDigit(event.codepoint()) && !isClosing()) {
			host.focus(search);
		}
		return super.charTyped(event);
	}

	private static String capitalize(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}
}
