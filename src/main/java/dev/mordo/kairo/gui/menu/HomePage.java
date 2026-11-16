package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.api.Addons;
import dev.mordo.kairo.api.HomeMenu;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Easing;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * The menu's home page when an addon's {@link HomeMenu} is chosen instead of the ring. Kairo does the page's part
 * (opening and leaving, the backdrop, running items); the addon draws and handles input. If the addon breaks, the
 * menu goes back to the ring and the ring is used until the game restarts.
 */
final class HomePage extends Page implements HomeMenu.Context {
	private final HomeMenu home;
	private final Animation appear = new Animation(0);
	private boolean hidden = true;

	HomePage(MenuScreen menu, HomeMenu home) {
		super(menu);
		this.home = home;
	}

	HomeMenu home() {
		return home;
	}

	@Override
	public void show() {
		hidden = false;
		appear.animateTo(1, 260, Easing.OUT_CUBIC);
		safely(() -> home.opened(this));
		if (safelyTrue(home::takesText)) {
			// SDL only sends typed characters while text input is switched on.
			net.minecraft.client.Minecraft.getInstance().textInputManager().startTextInput(this);
		}
	}

	@Override
	public void hide() {
		appear.animateTo(0, 180, Easing.OUT_CUBIC);
		net.minecraft.client.Minecraft.getInstance().textInputManager().stopTextInput(this);
	}

	@Override
	public boolean isHidden() {
		if (appear.target() == 0 && appear.isDone()) {
			hidden = true;
		}
		return hidden;
	}

	@Override
	public void render(GuiGraphicsExtractor g, float mx, float my, float viewWidth, float viewHeight, boolean interactive) {
		float a = Animation.motion(appear.value(), appear.target() > 0 ? 1 : appear.value());
		safely(() -> home.render(g, this, mx, my, viewWidth, viewHeight, a));
	}

	@Override
	public boolean onClick(float mx, float my, int button) {
		// Home menus get 0 for left, 1 for right, 2 for middle, whatever numbers the window system uses.
		int simple = button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT ? 0
			: button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT ? 1
			: button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_MIDDLE ? 2 : -1;
		return simple >= 0 && safelyTrue(() -> home.mouseClicked(this, mx, my, simple));
	}

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		return safelyTrue(() -> home.mouseScrolled(this, mx, my, amount));
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			return false; // MenuScreen asks onEscape
		}
		return safelyTrue(() -> home.keyPressed(this, event.key(), event.hasShiftDown(), event.hasControlDown()));
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return safelyTrue(() -> home.charTyped(this, event.codepoint()));
	}

	@Override
	public boolean onEscape() {
		return safelyTrue(() -> home.escape(this));
	}

	// ---- Context ----

	@Override
	public List<HomeMenu.Item> sections() {
		List<HomeMenu.Item> items = new ArrayList<>();
		for (RingSection section : RingSection.active()) {
			items.add(item(section));
		}
		return items;
	}

	@Override
	public void close() {
		menu.onClose();
	}

	@Override
	public List<HomeMenu.Item> search(String query, int limit) {
		String q = query.trim().toLowerCase(Locale.ROOT);
		if (q.isEmpty() || limit <= 0) {
			return List.of();
		}
		record Scored(HomeMenu.Item item, int score) {
		}
		List<Scored> found = new ArrayList<>();
		for (RingSection section : RingSection.values()) {
			if (section.available()) {
				int score = score(q, section.title, section.description());
				if (score > 0) {
					found.add(new Scored(item(section), score + 30));
				}
			}
		}
		for (Module module : ModuleManager.modules()) {
			int score = score(q, module.name(), module.description());
			if (score > 0) {
				found.add(new Scored(item(module), score + 20));
			}
			for (Setting<?> setting : module.settings()) {
				if (!setting.isVisible() || setting.isHidden()) {
					continue;
				}
				int s = score(q, setting.name(), setting.descriptionText());
				if (s > 0) {
					found.add(new Scored(new HomeMenu.Item(setting.name(), module.name(), module.icon(), "Setting", null,
						() -> openMod(module), null), s));
				}
			}
		}
		found.sort(Comparator.comparingInt(Scored::score).reversed());
		List<HomeMenu.Item> items = new ArrayList<>();
		for (int i = 0; i < Math.min(limit, found.size()); i++) {
			items.add(found.get(i).item());
		}
		return items;
	}

	/** How well {@code text} (and then {@code detail}) matches: 0 for no match, more for a better one. */
	private static int score(String q, String text, String detail) {
		String t = text.toLowerCase(Locale.ROOT);
		if (t.equals(q)) {
			return 100;
		}
		if (t.startsWith(q)) {
			return 80;
		}
		for (String word : t.split("[ \\-_/]+")) {
			if (word.startsWith(q)) {
				return 65;
			}
		}
		if (t.contains(q)) {
			return 50;
		}
		if (q.length() >= 3 && detail != null && detail.toLowerCase(Locale.ROOT).contains(q)) {
			return 20;
		}
		// Letters in order ("kst" finds Keystrokes).
		int at = 0;
		for (int i = 0; i < t.length() && at < q.length(); i++) {
			if (t.charAt(i) == q.charAt(at)) {
				at++;
			}
		}
		return at == q.length() && q.length() >= 2 ? 10 : 0;
	}

	private HomeMenu.Item item(RingSection section) {
		return new HomeMenu.Item(section.title, section.description(), section.icon, section.isAction() ? "Action" : "Page",
			section.isAction() ? section::isOn : null, () -> {
			if (!section.isAction()) {
				dev.mordo.kairo.sound.UiSounds.click();
			}
			section.open(menu);
		}, null);
	}

	private HomeMenu.Item item(Module module) {
		return new HomeMenu.Item(module.name(), module.description(), module.icon(), "Mod", module::isEnabled, () -> openMod(module),
			() -> {
				module.toggle();
				ModuleManager.syncAll();
			});
	}

	private void openMod(Module module) {
		dev.mordo.kairo.sound.UiSounds.click();
		ModsPage mods = menu.mods();
		menu.navigate(mods);
		mods.showSettingsNow(module);
	}

	// ---- the addon breaking ----

	private void safely(Runnable action) {
		try {
			action.run();
		} catch (Throwable t) {
			broke(t);
		}
	}

	private boolean safelyTrue(java.util.function.BooleanSupplier action) {
		try {
			return action.getAsBoolean();
		} catch (Throwable t) {
			broke(t);
			return true;
		}
	}

	private void broke(Throwable t) {
		Addons.homeMenuFailed(home, t);
		menu.navigate(menu.ring());
	}
}
