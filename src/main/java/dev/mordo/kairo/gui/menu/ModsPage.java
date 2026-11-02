package dev.mordo.kairo.gui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.PvpMode;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Previewable;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.Binding;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ModCard;
import dev.mordo.kairo.gui.widgets.ModeSwitch;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.ScrollState;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.gui.widgets.TextField;
import dev.mordo.kairo.gui.widgets.ToggleSwitch;
import dev.mordo.kairo.hud.HudElement;
import dev.mordo.kairo.hud.HudManager;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Mods panel: categories on the left, a live search, and a 3-column grid of mod cards.
 * A card's gear (or the card itself) slides that mod's settings in from the right.
 */
public final class ModsPage extends PanelPage {
	private static final float GAP = 14;
	private final Button[] layoutButtons = new Button[ClientSettings.ModsLayout.values().length];
	private static final float GRID_TOP = 92;

	private @Nullable Category category;
	private final TextField search;
	private final List<NavItem> nav = new ArrayList<>();
	private final Map<Module, ModCard> cards = new IdentityHashMap<>();
	private final List<ModCard> shown = new ArrayList<>();
	private final ScrollState gridScroll = new ScrollState();
	private float gridTop;
	private float gridBottom;

	// Per-mod settings view.
	private final Animation detail = new Animation(0);
	private @Nullable Module detailModule;
	private final Map<Module, SettingsList> settingsLists = new HashMap<>();
	private final Map<Module, ToggleSwitch> enableToggles = new IdentityHashMap<>();
	private final Button detailBack;
	private final Button detailReset;
	/** Whether the settings preview shows the PvP values (local to the preview; the real mode isn't touched). */
	private boolean previewPvp;
	private final ModeSwitch previewMode = new ModeSwitch(new Binding<>() {
		@Override
		public Boolean get() {
			return previewPvp;
		}

		@Override
		public void set(Boolean pvp) {
			previewPvp = pvp;
		}
	}).textSize(10);

	ModsPage(MenuScreen menu) {
		super(menu);
		search = new TextField("Search mods", Icon.SEARCH).maxLength(32).onChange(text -> refresh());
		nav.add(new NavItem("All", Icon.GRID, () -> Integer.toString(ModuleManager.modules().size()),
			() -> category == null, () -> selectCategory(null)));
		Icon[] icons = {Icon.LAYOUT, Icon.SWORD, Icon.SPARKLE, Icon.SLIDERS};
		for (Category c : Category.values()) {
			nav.add(new NavItem(c.displayName(), icons[c.ordinal()], () -> Integer.toString(ModuleManager.byCategory(c).size()),
				() -> category == c, () -> selectCategory(c)));
		}
		for (Module module : ModuleManager.modules()) {
			cards.put(module, new ModCard(module, () -> openSettings(module)));
		}
		Icon[] layoutIcons = {Icon.GRID, Icon.VIEW_LARGE, Icon.VIEW_LIST, Icon.VIEW_TINY};
		for (ClientSettings.ModsLayout layout : ClientSettings.ModsLayout.values()) {
			layoutButtons[layout.ordinal()] = new Button("", layoutIcons[layout.ordinal()], Button.Style.GHOST, () -> {
				if (ClientSettings.INSTANCE.modsLayout.get() != layout) {
					ClientSettings.INSTANCE.modsLayout.set(layout);
					dev.mordo.kairo.sound.UiSounds.click();
					refresh();
				}
			});
		}
		detailBack = new Button("", Icon.ARROW_LEFT, Button.Style.GHOST, this::closeSettings);
		detailBack.bounds(0, 0, 36, 36);
		detailReset = new Button("Reset", null, Button.Style.SUBTLE, () -> {
			if (detailModule != null) {
				detailModule.resetAll();
			}
		}).textSize(11.5f);
	}

	@Override
	protected String navLabel() {
		return "CATEGORIES";
	}

	@Override
	protected List<NavItem> navItems() {
		return nav;
	}

	@Override
	public void show() {
		super.show();
		refresh();
	}

	private void selectCategory(@Nullable Category c) {
		category = c;
		closeSettings();
		refresh();
	}

	/** Re-filters the grid and replays the staggered card intro. */
	private void refresh() {
		shown.clear();
		String query = search.text().trim().toLowerCase(Locale.ROOT);
		for (Module module : ModuleManager.modules()) {
			if (category != null && module.category() != category) {
				continue;
			}
			if (!query.isEmpty() && !module.name().toLowerCase(Locale.ROOT).contains(query)
				&& !module.description().toLowerCase(Locale.ROOT).contains(query)) {
				continue;
			}
			shown.add(cards.get(module));
		}
		for (int i = 0; i < shown.size(); i++) {
			shown.get(i).appear.set(0).animateTo(1, 320, Easing.UI, i * 30);
		}
		gridScroll.reset();
	}

	private void openSettings(Module module) {
		detailModule = module;
		previewPvp = PvpMode.isActive();
		settingsList(module).resetScroll();
		host.reset();
		detail.animateTo(1, 320, Easing.UI);
	}

	/** Opens a mod's settings without the slide (when the menu is opened straight onto them). */
	void showSettingsNow(Module module) {
		openSettings(module);
		detail.set(1);
	}

	private void closeSettings() {
		if (detail.target() > 0) {
			host.reset();
			detail.animateTo(0, 280, Easing.UI);
		}
	}

	private SettingsList settingsList(Module module) {
		return settingsLists.computeIfAbsent(module, m -> {
			SettingsList list = SettingsList.forSettings(host, "SETTINGS", m.settings(), true);
			if (m instanceof SettingsList.HasSection withSection) {
				list.header(withSection.settingsSection(host));
			}
			return list;
		});
	}

	// ---- drawing ----

	@Override
	protected void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float t = detail.value();
		float slide = Animation.motion(1, 0);
		if (t < 0.999f) {
			g.pose().pushMatrix();
			g.pose().translate(-40 * t * slide, 0);
			renderGrid(g, mx, my, fade * (1 - t), live && t < 0.05f);
			g.pose().popMatrix();
		}
		if (t > 0.001f && detailModule != null) {
			g.pose().pushMatrix();
			g.pose().translate(60 * (1 - t) * slide, 0);
			renderDetail(g, detailModule, mx, my, fade * t, live && t > 0.95f);
			g.pose().popMatrix();
		}
	}

	private void renderGrid(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		String subtitle = category == null ? "All categories" : category.displayName();
		if (PvpMode.isActive()) {
			subtitle += "  ·  switches only change PvP mode";
		}
		drawTitle(g, "Mods", subtitle, contentX, fade);
		search.bounds(contentX + contentW - 260, py + 26, 260, 36);
		put(search, g, mx, my, fade, live);

		// Layout switcher: cards, large tiles, list, tiny tiles.
		ClientSettings.ModsLayout layout = ClientSettings.INSTANCE.modsLayout.get();
		float sw = 34;
		float sx = search.x - 14 - sw * layoutButtons.length - 4;
		Shapes.rect(g, sx - 3, py + 26, sw * layoutButtons.length + 6, 36, 12, Colors.argb(0.05f * fade, 0xFFFFFF), Colors.argb(0.08f * fade, 0xFFFFFF), 1);
		for (int i = 0; i < layoutButtons.length; i++) {
			if (i == layout.ordinal()) {
				Shapes.rect(g, sx + i * sw + 1, py + 29, sw - 2, 30, 9, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.22f), fade),
					Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.6f), fade), 1);
			}
			layoutButtons[i].bounds(sx + i * sw, py + 27, sw, 34);
			put(layoutButtons[i], g, mx, my, fade, live);
		}

		gridTop = py + GRID_TOP;
		gridBottom = py + ph - 20;
		float viewH = gridBottom - gridTop;
		int columns = switch (layout) {
			case CARDS -> 3;
			case LARGE -> 2;
			case LIST -> 1;
			case TINY -> Math.max(4, (int) ((contentW + GAP) / (128 + GAP)));
		};
		float cardH = switch (layout) {
			case CARDS -> 92;
			case LARGE -> 150;
			case LIST -> 54;
			case TINY -> 112;
		};
		float gap = layout == ClientSettings.ModsLayout.LIST ? 8 : GAP;
		float cardW = (contentW - gap * (columns - 1)) / columns;
		int rows = (shown.size() + columns - 1) / columns;
		gridScroll.setSizes(rows * (cardH + gap) - gap + 8, viewH);

		if (shown.isEmpty()) {
			Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, "No mods match \"" + search.text() + "\"", contentX + contentW / 2,
				gridTop + 60, 13, Theme.text(0.5f, fade));
			return;
		}
		// Scissor a little above the grid so lifted cards aren't clipped.
		g.enableScissor((int) Math.floor(contentX - 10), (int) Math.floor(gridTop - 6), (int) Math.ceil(contentX + contentW + 10), (int) Math.ceil(gridBottom));
		try {
			float offset = gridScroll.value();
			for (int i = 0; i < shown.size(); i++) {
				ModCard card = shown.get(i);
				float cx = contentX + (i % columns) * (cardW + gap);
				float cy = gridTop + 4 + (i / columns) * (cardH + gap) - offset;
				card.bounds(cx, cy, cardW, cardH);
				boolean visible = cy + cardH > gridTop && cy < gridBottom;
				if (visible) {
					put(card, g, mx, my, fade, live && my >= gridTop && my <= gridBottom);
				}
			}
		} finally {
			g.disableScissor();
		}
		gridScroll.drawBar(g, contentX + contentW + 14, gridTop, fade);
	}

	private void renderDetail(GuiGraphicsExtractor g, Module module, float mx, float my, float fade, boolean live) {
		float top = py + 26;
		detailBack.bounds(contentX - 6, top, 36, 36);
		put(detailBack, g, mx, my, fade, live);
		float titleX = contentX + 40;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, module.name(), titleX, top + 12, 24, Theme.text(1, fade));
		float nameW = Fonts.width(Typeface.OUTFIT_BOLD, module.name(), 24);
		Fonts.draw(g, Typeface.MONO_BOLD, module.category().displayName().toUpperCase(Locale.ROOT), titleX + nameW + 12, top + 13, 8.5f,
			Theme.text(0.4f, fade), Fonts.Align.LEFT, 1.6f, false);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, module.description(), titleX, top + 34, 11.5f, Theme.text(0.55f, fade));

		ToggleSwitch enable = enableToggles.computeIfAbsent(module, m -> new ToggleSwitch(Binding.normal(m.enabled)));
		enable.bounds(contentX + contentW - ToggleSwitch.WIDTH, top + 8, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		put(enable, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.OUTFIT_MEDIUM, module.enabled.getNormal() ? "On" : "Off", enable.x - 10, top + 18, 11.5f,
			Theme.text(0.6f, fade), Fonts.Align.RIGHT, 0, false);
		// The switch above is the normal state; say so when PvP mode differs.
		Boolean pvpOn = module.enabled.getPvpOverride();
		if (pvpOn != null && !pvpOn.equals(module.enabled.getNormal())) {
			Fonts.draw(g, Typeface.MONO_BOLD, pvpOn ? "ON IN PVP" : "OFF IN PVP", enable.x + ToggleSwitch.WIDTH, top + 42, 7.5f,
				Colors.multiplyAlpha(Theme.pvpAccent(), fade), Fonts.Align.RIGHT, 1.2f, false);
		}
		float resetW = detailReset.preferredWidth();
		detailReset.bounds(enable.x - 46 - resetW, top + 3, resetW, 30);
		put(detailReset, g, mx, my, fade, live);

		float listTop = top + 66;
		if (module instanceof Previewable || module instanceof HudElement) {
			// Live preview (HUD elements at their real on-screen size), switchable between normal and PvP values.
			boolean hud = module instanceof HudElement;
			float previewH = hud ? 176 : 150;
			Shapes.rect(g, contentX, listTop - 6, contentW, previewH, 16, Colors.multiplyAlpha(0x26000000, fade),
				Colors.multiplyAlpha(Theme.BORDER, fade), 1);
			String label = (hud ? "PREVIEW" : "PREVIEW · 4X") + (previewPvp ? " · PVP MODE" : "");
			Fonts.draw(g, Typeface.MONO_BOLD, label, contentX + 14, listTop + 8, 8,
				previewPvp ? Colors.multiplyAlpha(Theme.pvpAccent(), fade * 0.8f) : Theme.text(0.4f, fade), Fonts.Align.LEFT, 1.6f, false);
			float switchW = previewMode.preferredWidth() - 10;
			previewMode.bounds(contentX + contentW - 8 - switchW, listTop - 6 + 8, switchW, 26);
			put(previewMode, g, mx, my, fade, live);
			if (fade > 0.6f) {
				float cx = contentX + contentW / 2;
				float cy = listTop - 6 + previewH / 2 + 4;
				float maxW = contentW - 40;
				float maxH = previewH - 30;
				float unit = menu.unit();
				PvpMode.preview(previewPvp, () -> {
					if (module instanceof HudElement element) {
						HudManager.renderPreview(g, element, cx, cy, maxW, maxH, unit);
					} else {
						((Previewable) module).drawPreview(g, cx, cy, maxW, maxH, unit);
					}
				});
			}
			listTop += previewH + 8;
		}
		Shapes.rect(g, contentX, listTop - 10, contentW, 1, 0, Colors.argb(0.08f * fade, 0xFFFFFF));
		settingsList(module).render(g, contentX, listTop, contentW + 14, py + ph - 20 - listTop, mx, my, fade, live);
	}

	// ---- input ----

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		if (detail.target() > 0 && detailModule != null) {
			return settingsList(detailModule).mouseScrolled(mx, my, amount);
		}
		if (my >= gridTop && my <= gridBottom && mx >= contentX - 10 && mx <= contentX + contentW + 20) {
			gridScroll.scroll(amount);
			return true;
		}
		return false;
	}

	@Override
	public boolean onClick(float mx, float my, int button) {
		if (button == InputConstants.MOUSE_BUTTON_RIGHT && detail.target() > 0) {
			closeSettings();
			return true;
		}
		return false;
	}

	/** Typing anywhere in the grid starts a search. */
	@Override
	public boolean charTyped(CharacterEvent event) {
		if (detail.target() > 0 || !Character.isLetterOrDigit(event.codepoint())) {
			return false;
		}
		host.focus(search);
		return search.charTyped(event);
	}

	@Override
	public boolean onEscape() {
		if (detail.target() > 0) {
			closeSettings();
			return true;
		}
		return false;
	}
}
