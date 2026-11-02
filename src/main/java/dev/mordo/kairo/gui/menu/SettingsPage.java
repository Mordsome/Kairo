package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.ModuleManager;
import dev.mordo.kairo.core.SettingHolder;
import dev.mordo.kairo.core.config.ConfigManager;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.gui.ClientScreens;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.ScrollState;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.gui.widgets.TextField;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Client settings: every keybind in one list, config profiles, and an about page. */
public final class SettingsPage extends PanelPage {
	private enum Tab {
		KEYBINDS("Keybinds", Icon.KEYBOARD),
		PROFILES("Profiles", Icon.FOLDER),
		ABOUT("About", Icon.INFO);

		final String label;
		final Icon icon;

		Tab(String label, Icon icon) {
			this.label = label;
			this.icon = icon;
		}
	}

	private Tab tab = Tab.KEYBINDS;
	private final List<NavItem> nav = new ArrayList<>();
	private final SettingsList keybinds;

	// Profiles
	private List<String> profiles = List.of();
	private final TextField newProfile;
	private final Button create;
	private final Map<String, Button> loadButtons = new HashMap<>();
	private final Map<String, Button> deleteButtons = new HashMap<>();
	private final ScrollState profileScroll = new ScrollState();

	// About
	private final Button renderTest;
	private final Button setupAgain;

	SettingsPage(MenuScreen menu) {
		super(menu);
		for (Tab t : Tab.values()) {
			nav.add(new NavItem(t.label, t.icon, null, () -> tab == t, () -> {
				tab = t;
				refreshProfiles();
			}));
		}
		List<SettingsList.Entry> binds = new ArrayList<>();
		for (SettingHolder holder : ModuleManager.allHolders()) {
			String owner = holder instanceof Module module ? module.name() : "Client";
			for (Setting<?> setting : holder.settings()) {
				if (setting instanceof KeybindSetting bind) {
					binds.add(new SettingsList.Entry(bind, bind.name(), owner));
				}
			}
		}
		keybinds = new SettingsList(host, "KEYBINDS", binds, List.of());

		newProfile = new TextField("New profile name", Icon.PLUS).maxLength(24)
			.allow(c -> Character.isLetterOrDigit(c) || c == '-' || c == '_' || c == ' ')
			.onSubmit(name -> createProfile());
		create = new Button("Create", null, Button.Style.PRIMARY, this::createProfile).textSize(11.5f);
		renderTest = new Button("Open render test", Icon.SPARKLE, Button.Style.SUBTLE, ClientScreens::openRenderTest).textSize(11.5f);
		setupAgain = new Button("Run the welcome setup again", Icon.REFRESH, Button.Style.SUBTLE,
			() -> menu.close(new dev.mordo.kairo.gui.welcome.WelcomeScreen(null))).textSize(11.5f);
	}

	@Override
	public void show() {
		super.show();
		refreshProfiles();
	}

	@Override
	protected String navLabel() {
		return "SETTINGS";
	}

	@Override
	protected List<NavItem> navItems() {
		return nav;
	}

	private void refreshProfiles() {
		profiles = ConfigManager.listProfiles();
	}

	private void createProfile() {
		String name = newProfile.text().trim();
		if (name.isEmpty()) {
			return;
		}
		String clean = ConfigManager.sanitizeName(name);
		ConfigManager.createProfile(clean);
		newProfile.setText("");
		refreshProfiles();
		Toasts.show("Profile \"" + clean + "\" created", Theme.accent());
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		drawTitle(g, "Settings", tab.label, contentX, fade);
		float top = py + 92;
		float bottom = py + ph - 20;
		switch (tab) {
			case KEYBINDS -> keybinds.render(g, contentX, top, contentW + 14, bottom - top, mx, my, fade, live);
			case PROFILES -> renderProfiles(g, top, bottom, mx, my, fade, live);
			case ABOUT -> renderAbout(g, top, mx, my, fade, live);
		}
	}

	private void renderProfiles(GuiGraphicsExtractor g, float top, float bottom, float mx, float my, float fade, boolean live) {
		// Create row.
		float createW = create.preferredWidth();
		newProfile.bounds(contentX, top, contentW - createW - 10, 36);
		create.bounds(contentX + contentW - createW, top, createW, 36);
		put(newProfile, g, mx, my, fade, live);
		put(create, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "A new profile starts as a copy of your current settings.", contentX, top + 52, 10.5f,
			Theme.text(0.5f, fade));

		float listTop = top + 72;
		Fonts.draw(g, Typeface.MONO_BOLD, "PROFILES", contentX, listTop + 6, 8.5f, Theme.text(0.45f, fade), Fonts.Align.LEFT, 2f, false);
		listTop += 22;
		float rowH = 52;
		profileScroll.setSizes(profiles.size() * (rowH + 8), bottom - listTop);
		g.enableScissor((int) contentX - 2, (int) listTop, (int) (contentX + contentW + 2), (int) bottom);
		try {
			float y = listTop - profileScroll.value();
			for (String name : profiles) {
				boolean active = name.equals(ConfigManager.activeProfile());
				Shapes.rect(g, contentX, y, contentW, rowH, 14,
					Colors.multiplyAlpha(active ? Colors.withAlpha(Theme.accent(), 0.10f) : Theme.SURFACE, fade),
					Colors.multiplyAlpha(active ? Colors.withAlpha(Theme.accent(), 0.3f) : Theme.BORDER, fade), 1);
				IconRenderer.draw(g, Icon.USER, contentX + 26, y + rowH / 2, 17, active ? Colors.multiplyAlpha(Theme.accent(), fade) : Theme.text(0.6f, fade), 2f);
				Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, name, contentX + 48, y + rowH / 2, 13.5f, Theme.text(1, fade));
				boolean inView = y + rowH > listTop && y < bottom;
				if (active) {
					float pw = Fonts.width(Typeface.MONO_BOLD, "ACTIVE", 8, 1.2f) + 16;
					Shapes.rect(g, contentX + contentW - 16 - pw, y + rowH / 2 - 10, pw, 20, 10, Colors.multiplyAlpha(Theme.accent(), fade));
					Fonts.draw(g, Typeface.MONO_BOLD, "ACTIVE", contentX + contentW - 16 - pw / 2, y + rowH / 2, 8, Colors.multiplyAlpha(Theme.DARK_TEXT, fade),
						Fonts.Align.CENTER, 1.2f, false);
				} else {
					Button load = loadButtons.computeIfAbsent(name, n -> new Button("Load", null, Button.Style.SUBTLE, () -> {
						ConfigManager.switchProfile(n);
						refreshProfiles();
						Toasts.show("Loaded profile \"" + n + "\"", Theme.accent());
					}).textSize(11));
					Button delete = deleteButtons.computeIfAbsent(name, n -> new Button("", Icon.TRASH, Button.Style.GHOST, () -> {
						ConfigManager.deleteProfile(n);
						refreshProfiles();
					}));
					delete.bounds(contentX + contentW - 14 - 32, y + rowH / 2 - 16, 32, 32);
					float lw = load.preferredWidth();
					load.bounds(delete.x - 8 - lw, y + rowH / 2 - 15, lw, 30);
					if (inView) {
						put(load, g, mx, my, fade, live);
						put(delete, g, mx, my, fade, live);
					}
				}
				y += rowH + 8;
			}
		} finally {
			g.disableScissor();
		}
		profileScroll.drawBar(g, contentX + contentW + 14, listTop, fade);
	}

	// ---- easter egg: click the logo ----
	private float logoX;
	private float logoY;
	private final dev.mordo.kairo.render.Animation logoTurns = new dev.mordo.kairo.render.Animation(0);
	private final dev.mordo.kairo.render.Animation logoPop = new dev.mordo.kairo.render.Animation(0);
	private long sparkleStart;
	private final float[] sparkleAngles = new float[14];
	private final float[] sparkleSpeeds = new float[14];
	private final dev.mordo.kairo.gui.widgets.ToggleSwitch ringSpinToggle =
		new dev.mordo.kairo.gui.widgets.ToggleSwitch(dev.mordo.kairo.gui.widgets.Binding.normal(dev.mordo.kairo.core.ClientSettings.INSTANCE.ringSpin));

	@Override
	public boolean onClick(float mx, float my, int button) {
		if (tab == Tab.ABOUT && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
			float dx = mx - logoX;
			float dy = my - logoY;
			if (dx * dx + dy * dy <= 36 * 36) {
				clickLogo();
				return true;
			}
		}
		return false;
	}

	private void clickLogo() {
		// Each click adds a full turn; the logo whirls and throws a few sparks.
		logoTurns.animateTo(Math.round(logoTurns.target()) + 1, 950, dev.mordo.kairo.render.Easing.OUT_CUBIC);
		logoPop.set(1).animateTo(0, 600, dev.mordo.kairo.render.Easing.OUT_CUBIC);
		sparkleStart = System.nanoTime();
		java.util.Random random = new java.util.Random();
		for (int i = 0; i < sparkleAngles.length; i++) {
			sparkleAngles[i] = (float) (i * Math.PI * 2 / sparkleAngles.length + random.nextFloat() * 0.4f);
			sparkleSpeeds[i] = 34 + random.nextFloat() * 26;
		}
		dev.mordo.kairo.sound.UiSounds.pvp(true);
		var s = dev.mordo.kairo.core.ClientSettings.INSTANCE;
		if (!s.ringSpinUnlocked.isOn()) {
			s.ringSpinUnlocked.set(true);
			s.ringSpin.set(true);
			Toasts.show("Easter egg found: the ring now spins! Drag or flick it", Theme.pvpAccent());
		}
	}

	private void renderAbout(GuiGraphicsExtractor g, float top, float mx, float my, float fade, boolean live) {
		float cx = contentX + 40;
		float cy = top + 46;
		logoX = cx;
		logoY = cy;
		// Logo: accent ring with a PvP-coloured arc and a centre dot. Click it...
		float turn = (float) (logoTurns.value() * Math.PI * 2);
		float pop = 1 + logoPop.value() * 0.18f;
		boolean overLogo = (mx - cx) * (mx - cx) + (my - cy) * (my - cy) <= 36 * 36;
		if (overLogo && live) {
			g.requestCursor(com.mojang.blaze3d.platform.cursor.CursorTypes.POINTING_HAND);
		}
		Shapes.arc(g, cx, cy, 26 * pop, 7, (float) Math.toRadians(-54) + turn, (float) Math.toRadians(288), Colors.multiplyAlpha(Theme.accent(), fade));
		Shapes.arc(g, cx, cy, 26 * pop, 7, (float) Math.toRadians(246) + turn, (float) Math.toRadians(48), Colors.multiplyAlpha(Theme.pvpAccent(), fade));
		Shapes.circle(g, cx, cy, 8 * pop, Colors.multiplyAlpha(Theme.accent(), fade));
		float age = (System.nanoTime() - sparkleStart) / 1e9f;
		if (sparkleStart != 0 && age < 0.8f) {
			float k = age / 0.8f;
			for (int i = 0; i < sparkleAngles.length; i++) {
				float r = 30 + sparkleSpeeds[i] * (1 - (1 - k) * (1 - k));
				int color = i % 2 == 0 ? Theme.accent() : Theme.pvpAccent();
				Shapes.circle(g, cx + (float) Math.cos(sparkleAngles[i]) * r, cy + (float) Math.sin(sparkleAngles[i]) * r,
					2.4f * (1 - k), Colors.multiplyAlpha(color, fade * (1 - k)));
			}
		}
		float tx = cx + 50;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, ClientInfo.FULL_NAME, tx, cy - 10, 26, Theme.text(1, fade));
		Fonts.draw(g, Typeface.MONO_MEDIUM, "v" + ClientInfo.VERSION + " · Minecraft " + ClientInfo.MC_VERSION + " · Fabric", tx, cy + 18, 10.5f,
			Theme.text(0.6f, fade), Fonts.Align.LEFT, 0.6f, false);

		float y = cy + 70;
		String[] lines = {
			"Made by Mordo.",
			"Visual and client-side only: no automation, nothing extra sent to servers.",
			"Requires Fabric API and Sodium.",
			"Fonts: Outfit and JetBrains Mono (SIL Open Font License).",
		};
		for (String line : lines) {
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, line, contentX, y, 12, Theme.text(0.75f, fade));
			y += 22;
		}
		y += 10;
		Fonts.draw(g, Typeface.MONO_BOLD, "CONFIG FOLDER", contentX, y, 8.5f, Theme.text(0.45f, fade), Fonts.Align.LEFT, 2f, false);
		String path = FabricLoader.getInstance().getConfigDir().resolve(ClientInfo.MOD_ID).toString();
		Fonts.draw(g, Typeface.MONO_MEDIUM, Fonts.ellipsize(Typeface.MONO_MEDIUM, path, 10.5f, contentW), contentX, y + 20, 10.5f,
			Theme.text(0.7f, fade));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Profile: " + ConfigManager.activeProfile(), contentX, y + 40, 11, Theme.text(0.55f, fade));

		float bw = renderTest.preferredWidth();
		renderTest.bounds(contentX, y + 64, bw, 34);
		put(renderTest, g, mx, my, fade, live);
		float sw = setupAgain.preferredWidth();
		setupAgain.bounds(contentX + bw + 10, y + 64, sw, 34);
		put(setupAgain, g, mx, my, fade, live);

		// Unlocked by the easter egg.
		if (dev.mordo.kairo.core.ClientSettings.INSTANCE.ringSpinUnlocked.isOn()) {
			float ry = y + 118;
			Shapes.rect(g, contentX, ry, contentW, 58, 14, Colors.multiplyAlpha(Colors.withAlpha(Theme.pvpAccent(), 0.08f), fade),
				Colors.multiplyAlpha(Colors.withAlpha(Theme.pvpAccent(), 0.3f), fade), 1);
			IconRenderer.draw(g, Icon.REFRESH, contentX + 26, ry + 29, 18, Colors.multiplyAlpha(Theme.pvpAccent(), fade), 2f);
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Spinning ring", contentX + 48, ry + 21, 13.5f, Theme.text(1, fade));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Drag, flick or scroll the ring menu to spin it. You found the easter egg!", contentX + 48, ry + 39, 10.5f,
				Theme.text(0.55f, fade));
			ringSpinToggle.bounds(contentX + contentW - 16 - dev.mordo.kairo.gui.widgets.ToggleSwitch.WIDTH, ry + 19,
				dev.mordo.kairo.gui.widgets.ToggleSwitch.WIDTH, dev.mordo.kairo.gui.widgets.ToggleSwitch.HEIGHT);
			put(ringSpinToggle, g, mx, my, fade, live);
		}
	}

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		return switch (tab) {
			case KEYBINDS -> keybinds.mouseScrolled(mx, my, amount);
			case PROFILES -> {
				profileScroll.scroll(amount);
				yield true;
			}
			case ABOUT -> false;
		};
	}
}
