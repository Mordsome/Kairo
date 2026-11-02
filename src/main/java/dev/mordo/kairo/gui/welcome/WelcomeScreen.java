package dev.mordo.kairo.gui.welcome;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.cosmetics.Capes;
import dev.mordo.kairo.cosmetics.Cosmetics;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.main.MainMenuBase;
import dev.mordo.kairo.gui.main.MainScreens;
import dev.mordo.kairo.gui.widgets.Binding;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.ToggleSwitch;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.hud.HudElement;
import dev.mordo.kairo.hud.HudManager;
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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * First-run setup on a black screen. Kairo: welcome, a photosensitivity warning, your colour, main menu style, a few
 * comfort switches, a HUD preset, a cape and wings, and the keys to remember. Kairo Void shows what makes it the
 * visual edition first, then the same warning, a whole look (preset), how much effect you want, the menu style, a HUD
 * and cosmetics. Everything applies live, and Esc skips it. It shows once (and again from Settings → About).
 */
public final class WelcomeScreen extends MainMenuBase {
	/** The kinds of step; each edition shows its own list of them. */
	private enum Kind {
		WELCOME, SAFETY, COLOUR, PRESET, QUALITY, STYLE, FEEL, HUD, LOOK, DONE
	}

	private static final Kind[] KAIRO_STEPS = {Kind.WELCOME, Kind.SAFETY, Kind.COLOUR, Kind.STYLE, Kind.FEEL, Kind.HUD, Kind.LOOK, Kind.DONE};
	private static final Kind[] VOID_STEPS = {Kind.WELCOME, Kind.SAFETY, Kind.PRESET, Kind.QUALITY, Kind.STYLE, Kind.HUD, Kind.LOOK, Kind.DONE};
	private final Kind[] kinds = dev.mordo.kairo.core.Edition.isVoid() ? VOID_STEPS : KAIRO_STEPS;
	private final int STEPS = kinds.length;
	private static final int[] PALETTE = {
		0xFF7FD8FF, 0xFF6C8CFF, 0xFFB07BFF, 0xFFFF6FB5, 0xFFFF6B6B, 0xFFFF9A52,
		0xFFFFC94D, 0xFFA6E35A, 0xFF5CF2B0, 0xFF3FD0D4, 0xFFF4F8FB, 0xFFFF4D5E
	};
	private static final String[] PALETTE_NAMES = {
		"Ice", "Indigo", "Violet", "Rose", "Coral", "Orange", "Gold", "Lime", "Mint", "Teal", "Snow", "Ruby"
	};

	private final @Nullable Screen after;
	private int step;
	private int previousStep = -1;
	private final Animation stepAnim = new Animation(1);
	private final Animation logo = new Animation(0);
	private final Button next;
	private final Button back;
	private final Button skip;
	private final List<List<Widget>> stepWidgets = new ArrayList<>();
	private int hudPreset = -1;

	/** {@code after}: where to go when it's done (null = the game, or the main menu if there's no world). */
	public WelcomeScreen(@Nullable Screen after) {
		super(Component.literal("Welcome to " + ClientInfo.NAME), null);
		this.after = after;
		next = new Button("Let's go", Icon.ARROW_RIGHT, Button.Style.PRIMARY, this::next).textSize(13);
		back = new Button("Back", Icon.ARROW_LEFT, Button.Style.GHOST, this::back).textSize(12.5f);
		skip = new Button("Skip setup", null, Button.Style.GHOST, this::finish).textSize(11.5f);
		for (int i = 0; i < Kind.values().length; i++) {
			stepWidgets.add(new ArrayList<>());
		}
		buildSafetyStep();
		buildColorStep();
		buildPresetStep();
		buildQualityStep();
		buildStyleStep();
		buildFeelStep();
		buildHudStep();
		buildLookStep();
	}

	private List<Widget> widgets(Kind kind) {
		return stepWidgets.get(kind.ordinal());
	}

	// ---- safety: flashing lights ----

	private ToggleSwitch calmMotion;

	private void buildSafetyStep() {
		ClientSettings s = ClientSettings.INSTANCE;
		widgets(Kind.SAFETY).add(new Card("Full effects", "Every flash, as designed", () -> !s.reduceFlashing.isOn(),
			() -> s.reduceFlashing.set(false), (g, x, y, w, h, a) -> flashArt(g, false, x, y, w, h, a)));
		widgets(Kind.SAFETY).add(new Card("Fewer flashes", "Explosions, hits and lightning toned down", () -> s.reduceFlashing.isOn(),
			() -> s.reduceFlashing.set(true), (g, x, y, w, h, a) -> flashArt(g, true, x, y, w, h, a)));
		calmMotion = new ToggleSwitch(Binding.normal(s.reduceMotion));
		widgets(Kind.SAFETY).add(calmMotion);
	}

	/** Bright bursts, or the same scene with soft glows. */
	private static void flashArt(GuiGraphicsExtractor g, boolean calm, float x, float y, float w, float h, float a) {
		Shapes.rect(g, x, y, w, h, 12, Colors.argb(0.35f * a, 0x000000));
		int accent = Theme.accent();
		double t = System.currentTimeMillis() / 1000.0;
		float cx = x + w / 2;
		float cy = y + h / 2;
		if (calm) {
			float p = 0.8f + 0.2f * (float) Math.sin(t * 1.5);
			Shapes.shadow(g, cx - 34, cy - 34, 68, 68, 34, 30, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.35f * p), a));
			Shapes.circle(g, cx, cy, 12, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.8f), a));
			return;
		}
		float k = (float) ((t * 1.4) % 1.0);
		Shapes.shadow(g, cx - 40, cy - 40, 80, 80, 40, 34, Colors.multiplyAlpha(Colors.withAlpha(0xFFFFFFFF, 0.5f * (1 - k)), a));
		for (int i = 0; i < 10; i++) {
			double ang = i * Math.PI / 5 + 0.3;
			float r0 = 14 + k * 10;
			float r1 = 26 + k * 34;
			Shapes.line(g, cx + (float) Math.cos(ang) * r0, cy + (float) Math.sin(ang) * r0, cx + (float) Math.cos(ang) * r1, cy + (float) Math.sin(ang) * r1,
				2.2f, Colors.multiplyAlpha(Colors.withAlpha(i % 2 == 0 ? 0xFFFFFFFF : accent, 1 - k), a));
		}
		Shapes.circle(g, cx, cy, 12, Colors.multiplyAlpha(0xFFFFFFFF, a));
	}

	// ---- Kairo Void: a whole look, and how much of it ----

	private void buildPresetStep() {
		if (!dev.mordo.kairo.core.Edition.isVoid()) {
			return;
		}
		String[] picks = {"Void", "Frostbite", "Hellfire", "Nebula", "Sakura", "Retrowave", "Abyssal", "Clean"};
		for (String name : picks) {
			for (dev.mordo.kairo.core.VoidPresets.Preset p : dev.mordo.kairo.core.VoidPresets.ALL) {
				if (p.name().equals(name)) {
					widgets(Kind.PRESET).add(new Card(p.name(), p.description(), () -> dev.mordo.kairo.core.VoidPresets.active(p),
						() -> dev.mordo.kairo.core.VoidPresets.apply(p), (g, x, y, w, h, a) -> dev.mordo.kairo.gui.menu.VisualPreviews.preset(g, p, x, y, w, h, a)));
				}
			}
		}
	}

	private void buildQualityStep() {
		if (!dev.mordo.kairo.core.Edition.isVoid()) {
			return;
		}
		var vs = dev.mordo.kairo.core.VoidSettings.INSTANCE;
		var levels = dev.mordo.kairo.core.VoidSettings.Quality.values();
		String[] lines = {"Blades and flashes, a light sky. For older PCs", "Shockwaves, hit-stop and the cosmic sky",
			"Adds the planet, aurora and shooting stars", "Everything, turned up: deeper fog, brighter blades"};
		for (int i = 0; i < 4; i++) {
			var level = levels[i];
			int index = i;
			widgets(Kind.QUALITY).add(new Card(level.label(), lines[i], () -> vs.quality.get() == level, () -> vs.quality.set(level),
				(g, x, y, w, h, a) -> qualityArt(g, index, x, y, w, h, a)));
		}
	}

	/** More and more layers: stars, then a nebula, a planet, an aurora. */
	private static void qualityArt(GuiGraphicsExtractor g, int level, float x, float y, float w, float h, float a) {
		int accent = Theme.accent();
		Shapes.gradientRect(g, x, y, w, h, 12, Colors.multiplyAlpha(Colors.lerp(0xFF05050A, accent, 0.06f + 0.06f * level), a),
			Colors.multiplyAlpha(0xFF020205, a));
		double t = System.currentTimeMillis() / 1000.0;
		int stars = 6 + level * 10;
		for (int i = 0; i < stars; i++) {
			float sx = x + (float) ((Math.sin(i * 12.9898) * 43758.5453) % 1 + 1) % 1 * w;
			float sy = y + (float) ((Math.sin(i * 78.233) * 12543.123) % 1 + 1) % 1 * h * 0.8f;
			float tw = 0.5f + 0.5f * (float) Math.sin(t * 2 + i);
			Shapes.circle(g, sx, sy, 0.8f + (i % 3) * 0.3f, Colors.multiplyAlpha(Colors.withAlpha(0xFFFFFFFF, 0.4f + 0.5f * tw), a));
		}
		if (level >= 1) {
			Shapes.shadow(g, x + w * 0.15f, y + h * 0.15f, w * 0.5f, h * 0.45f, h * 0.2f, h * 0.25f, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.25f), a));
		}
		if (level >= 2) {
			float px = x + w * 0.72f;
			float py = y + h * 0.36f;
			Shapes.circle(g, px, py, h * 0.13f, Colors.multiplyAlpha(Colors.lerp(accent, 0xFF202030, 0.4f), a));
			Shapes.arc(g, px, py, h * 0.21f, 1.4f, -0.4f, 3.6f, Colors.multiplyAlpha(Colors.withAlpha(0xFFFFFFFF, 0.6f), a));
			for (int i = 0; i < 3; i++) {
				float k = i / 3f;
				Shapes.gradientRect(g, x + w * (0.1f + 0.25f * i), y + h * 0.05f, w * 0.18f, h * 0.45f, 6,
					Colors.multiplyAlpha(Colors.withAlpha(0xFF5CF2B0, 0.0f), a), Colors.multiplyAlpha(Colors.withAlpha(0xFF5CF2B0, 0.18f + 0.05f * k), a));
			}
		}
		if (level >= 3) {
			Shapes.shadow(g, x, y + h * 0.55f, w, h * 0.45f, 12, 20, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.3f), a));
			float k = (float) ((t * 0.6) % 1.0);
			Shapes.line(g, x + w * (0.2f + 0.5f * k), y + h * (0.1f + 0.2f * k), x + w * (0.12f + 0.5f * k), y + h * (0.06f + 0.2f * k), 1.4f,
				Colors.multiplyAlpha(Colors.withAlpha(0xFFFFFFFF, 1 - k), a));
		}
		// Ground (square on top, rounded at the bottom like the card).
		Shapes.rect(g, x, y + h * 0.82f, w, h * 0.18f, 12, Colors.multiplyAlpha(0xFF0B0B10, a));
		Shapes.rect(g, x, y + h * 0.82f, w, Math.min(12, h * 0.09f), 0, Colors.multiplyAlpha(0xFF0B0B10, a));
	}

	@Override
	protected void onShown() {
		logo.set(0).animateTo(1, 1400, Easing.OUT_CUBIC, 200);
	}

	@Override
	protected boolean drawsPanorama() {
		return false;
	}

	@Override
	protected boolean blurredBackdrop() {
		return false;
	}

	@Override
	public void onClose() {
		finish();
	}

	// ---- steps ----

	private void go(int target) {
		if (target == step || target < 0 || target >= STEPS) {
			return;
		}
		previousStep = step;
		step = target;
		stepAnim.set(0).animateTo(1, 420, Easing.UI);
		host.reset();
		UiSounds.click();
	}

	private void next() {
		if (step == STEPS - 1) {
			finish();
		} else {
			go(step + 1);
		}
	}

	private void back() {
		go(step - 1);
	}

	private void finish() {
		ClientSettings.INSTANCE.onboarded.set(true);
		UiSounds.pvp(true);
		Minecraft mc = Minecraft.getInstance();
		// Back to the game when there's a world (never to a menu that was already closing), else the main menu.
		Screen target = after != null && !(after instanceof dev.mordo.kairo.gui.KairoScreen) ? after
			: mc.level != null ? null : MainScreens.replace(new TitleScreen());
		close(target);
	}

	/** A selectable card: art on top, title and a line under it, an accent ring when chosen. */
	private static final class Card extends Widget {
		interface Art {
			void draw(GuiGraphicsExtractor g, float x, float y, float w, float h, float a);
		}

		private final String title;
		private final String line;
		private final BooleanSupplier chosen;
		private final Runnable action;
		private final Art art;
		private final Animation pick = new Animation(0);

		Card(String title, String line, BooleanSupplier chosen, Runnable action, Art art) {
			this.title = title;
			this.line = line;
			this.chosen = chosen;
			this.action = action;
			this.art = art;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			boolean on = chosen.getAsBoolean();
			pick.animateTo(on ? 1 : 0, 220);
			float p = pick.value();
			float hv = hover.value();
			int accent = Theme.accent();
			float lift = Animation.motion(hv * 3, 0);
			float cy = y - lift;
			Shapes.shadow(g, x, cy + 10, w, h, 18, 22, Colors.argb((0.25f + 0.2f * hv) * fade, 0));
			Shapes.rect(g, x, cy, w, h, 18, Colors.multiplyAlpha(Colors.lerp(Colors.argb(0.05f + 0.04f * hv, 0xFFFFFF), Colors.withAlpha(accent, 0.14f), p), fade),
				Colors.multiplyAlpha(Colors.lerp(Colors.argb(0.12f + 0.14f * hv, 0xFFFFFF), Colors.withAlpha(accent, 0.9f), p), fade), 1 + p);
			float artH = h - 64;
			art.draw(g, x + 14, cy + 14, w - 28, artH - 14, fade);
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, title, x + 18, cy + h - 38, 16, Theme.text(1, fade));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, Fonts.ellipsize(Typeface.OUTFIT_REGULAR, line, 11.5f, w - 36), x + 18, cy + h - 18, 11.5f,
				Theme.text(0.55f, fade));
			if (p > 0.01f) {
				Shapes.circle(g, x + w - 20, cy + 20, 10 * p, Colors.multiplyAlpha(accent, fade));
				IconRenderer.draw(g, Icon.CHECK, x + w - 20, cy + 20, 13 * p, Colors.multiplyAlpha(Theme.DARK_TEXT, fade), 3f);
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) {
				return false;
			}
			UiSounds.click();
			action.run();
			return true;
		}
	}

	/** A colour dot. */
	private static final class Swatch extends Widget {
		private final int color;
		private final String name;
		private final Animation pick = new Animation(0);

		Swatch(int color, String name) {
			this.color = color;
			this.name = name;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			boolean on = (ClientSettings.INSTANCE.accent.argb() | 0xFF000000) == color;
			pick.animateTo(on ? 1 : 0, 220);
			float p = pick.value();
			float hv = hover.value();
			float cx = x + w / 2;
			float cy = y + w / 2;
			float r = w / 2 - 6 + hv * 2;
			Shapes.shadow(g, cx - r, cy - r, r * 2, r * 2, r, 14 + 10 * p, Colors.multiplyAlpha(Colors.withAlpha(color, 0.35f + 0.35f * p), fade));
			Shapes.circle(g, cx, cy, r, Colors.multiplyAlpha(color, fade));
			if (p > 0.01f) {
				Shapes.ring(g, cx, cy, r + 5, 2f, Colors.multiplyAlpha(Colors.withAlpha(0xFFFFFFFF, p), fade));
			}
			Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, name, cx, y + w + 12, 11, Theme.text(0.45f + 0.4f * Math.max(p, hv), fade));
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) {
				return false;
			}
			UiSounds.click();
			ClientSettings.INSTANCE.accent.set(color);
			return true;
		}
	}

	private void buildColorStep() {
		for (int i = 0; i < PALETTE.length; i++) {
			widgets(Kind.COLOUR).add(new Swatch(PALETTE[i], PALETTE_NAMES[i]));
		}
	}

	private void buildStyleStep() {
		if (dev.mordo.kairo.core.Edition.isVoid()) {
			// Void: Stage, Simple, Minecraft.
			String[] names = {"Stage", "Simple", "Minecraft"};
			String[] lines = {"Your player in a spotlight", "Just the name and a column of words", "The classic title, in the Void look"};
			int[] art = {0, 5, 4};
			for (int i = 0; i < 3; i++) {
				int index = i;
				widgets(Kind.STYLE).add(new Card(names[i], lines[i], () -> MainMenuSettings.voidStyle() == index,
					() -> MainMenuSettings.setVoidStyle(index), (g, x, y, w, h, a) -> styleArt(g, art[index], x, y, w, h, a)));
			}
			return;
		}
		String[] names = {dev.mordo.kairo.core.Edition.isVoid() ? "Stage" : "Kairo", "Dock", "Orbit", "Custom", "Minecraft"};
		String[] lines = {"Rows, and cards for your last world", "A big clock and a magnifying dock", "Your menu as planets in orbit",
			"Build your own from widgets", "The classic title, in the Kairo look"};
		for (int i = 0; i < 5; i++) {
			int kind = i;
			widgets(Kind.STYLE).add(new Card(names[i], lines[i], () -> currentStyle() == kind, () -> {
				if (kind == 4) {
					ClientSettings.INSTANCE.customMainMenu.set(false);
				} else {
					ClientSettings.INSTANCE.customMainMenu.set(true);
					MainMenuSettings.INSTANCE.layout.set(MainMenuSettings.Layout.values()[kind]);
				}
			}, (g, x, y, w, h, a) -> styleArt(g, kind, x, y, w, h, a)));
		}
	}

	private static int currentStyle() {
		return ClientSettings.INSTANCE.customMainMenu.isOn() ? MainMenuSettings.INSTANCE.layout.get().ordinal() : 4;
	}

	private record Toggle(String title, String line, ToggleSwitch toggle) {
	}

	private final List<Toggle> toggles = new ArrayList<>();

	private void buildFeelStep() {
		ClientSettings s = ClientSettings.INSTANCE;
		toggles.add(new Toggle("Animated buttons", "Buttons fade in and react when you click them", new ToggleSwitch(Binding.normal(s.buttonAnimations))));
		toggles.add(new Toggle("Menu sounds", "Soft clicks and swooshes in menus", new ToggleSwitch(new Binding<>() {
			@Override
			public Boolean get() {
				return s.menuSounds.floatValue() > 0.01f;
			}

			@Override
			public void set(Boolean on) {
				s.menuSounds.set(on ? 0.6 : 0.0);
			}
		})));
		toggles.add(new Toggle("Client fonts", "Outfit and JetBrains Mono instead of the pixel font", new ToggleSwitch(Binding.normal(s.customFonts))));
		toggles.add(new Toggle("Restyle Minecraft's menus", "Options, pause menu and the rest in the glass look", new ToggleSwitch(Binding.normal(s.restyleMenus))));
		toggles.add(new Toggle("Reduce motion", "Fades only: no sliding, scaling or spinning", new ToggleSwitch(Binding.normal(s.reduceMotion))));
		for (Toggle t : toggles) {
			widgets(Kind.FEEL).add(t.toggle());
		}
	}

	private void buildHudStep() {
		String[] names = {"Clean", "PvP", "Everything", "Nothing"};
		String[] lines = {"FPS and ping in a corner", "Keystrokes, CPS, armour, ping and FPS", "Every HUD element, ready to arrange", "A clear screen; add things later"};
		for (int i = 0; i < 4; i++) {
			int preset = i;
			widgets(Kind.HUD).add(new Card(names[i], lines[i], () -> hudPreset == preset, () -> applyHud(preset),
				(g, x, y, w, h, a) -> hudArt(g, preset, x, y, w, h, a)));
		}
	}

	private void applyHud(int preset) {
		hudPreset = preset;
		Set<String> on = switch (preset) {
			case 0 -> Set.of("fps", "ping");
			case 1 -> Set.of("keystrokes", "cps", "armor_status", "ping", "fps");
			case 2 -> null;
			default -> Set.of();
		};
		for (HudElement element : HudManager.elements()) {
			boolean enable = on == null ? !element.id().equals("direction") || preset == 2 : on.contains(element.id());
			element.enabled.set(enable);
		}
	}

	private void buildLookStep() {
		Cosmetics c = Cosmetics.get();
		String[][] capes = {{"none", "No cape"}, {"design:kairo", "Kairo"}, {"design:galaxy", "Galaxy"},
			{"design:flames", "Flames"}, {"design:aurora", "Aurora"}, {"design:sunset", "Sunset"}};
		for (String[] cape : capes) {
			widgets(Kind.LOOK).add(new Card(cape[1], cape[0].equals("none") ? "Keep it simple" : "Cape", () -> c.cape.get().equals(cape[0]),
				() -> c.cape.set(cape[0]), (g, x, y, w, h, a) -> capeArt(g, cape[0], x, y, w, h, a)));
		}
		Cosmetics.WingStyle[] wings = {Cosmetics.WingStyle.NONE, Cosmetics.WingStyle.ANGEL, Cosmetics.WingStyle.DRAGON, Cosmetics.WingStyle.SERAPH};
		for (Cosmetics.WingStyle wing : wings) {
			widgets(Kind.LOOK).add(new Card(wing == Cosmetics.WingStyle.NONE ? "No wings" : wing.label(), "Wings", () -> c.wings.get() == wing,
				() -> c.wings.set(wing), (g, x, y, w, h, a) -> {
				if (wing == Cosmetics.WingStyle.NONE) {
					IconRenderer.draw(g, Icon.CLOSE, x + w / 2, y + h / 2, Math.min(w, h) * 0.4f, Theme.text(0.4f, a), 1.8f);
				} else if (a > 0.5f) {
					int s = Math.round(Math.min(w, h));
					int left = Math.round(x + w / 2 - s / 2f);
					int top = Math.round(y + h / 2 - s / 2f);
					g.blit(dev.mordo.kairo.gui.menu.CosmeticsPage.art("wings", wing), left, top, left + s, top + s, 0, 1, 0, 1);
				}
			}));
		}
	}

	// ---- drawing ----

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		int w = g.guiWidth();
		int h = g.guiHeight();
		g.fill(0, 0, w, h, 0xFF000000);
		// Slow drifting glows in your colour.
		long now = System.currentTimeMillis();
		int accent = Theme.accent();
		for (int i = 0; i < 3; i++) {
			double t = now / (9000.0 + i * 2500) + i * 2.1;
			float cx = w * (0.5f + 0.32f * (float) Math.cos(t));
			float cy = h * (0.5f + 0.28f * (float) Math.sin(t * 1.3));
			float r = h * (0.22f + i * 0.05f);
			int color = i == 2 ? Theme.pvpAccent() : accent;
			Shapes.shadow(g, cx - r, cy - r, r * 2, r * 2, r, r * 0.9f, Colors.withAlpha(color, i == 2 ? 0.06f : 0.10f));
		}
	}

	@Override
	protected void drawContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float a = fade;
		float cx = viewWidth / 2;
		// Top bar: wordmark, step count, skip.
		drawMark(g, 56, 52, 13, a, 1);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, ClientInfo.NAME, 78, 52, 18, Theme.text(1, a));
		Fonts.draw(g, Typeface.MONO_BOLD, "SETUP  " + (step + 1) + " / " + STEPS, cx, 52, 9, Theme.text(0.4f, a), Fonts.Align.CENTER, 2f, false);
		float sw = skip.preferredWidth();
		skip.bounds(viewWidth - 48 - sw, 34, sw, 36);
		skip.setLabel(step == STEPS - 1 ? "Close" : "Skip setup");
		put(skip, g, mx, my, a, live);

		// Content of this step, sliding in.
		float t = stepAnim.value();
		float dir = previousStep < step ? 1 : -1;
		float slide = Animation.motion((1 - t) * 40 * dir, 0);
		g.pose().pushMatrix();
		g.pose().translate(slide, 0);
		drawStep(g, step, mx, my, a * t, live && t > 0.9f);
		g.pose().popMatrix();

		// Bottom: dots and the buttons.
		float by = viewHeight - 92;
		for (int i = 0; i < STEPS; i++) {
			boolean current = i == step;
			float dw = current ? 22 : 8;
			float dx = cx - (STEPS * 16 + 14) / 2f + i * 16 + (i > step ? 14 : 0);
			Shapes.rect(g, dx, by + 13, dw, 8, 4, Colors.multiplyAlpha(current ? Theme.accent() : i < step ? Colors.withAlpha(Theme.accent(), 0.5f) : 0x33FFFFFF, a));
		}
		next.setLabel(step == 0 ? "Let's go" : step == STEPS - 1 ? "Start playing" : "Next");
		float nw = next.preferredWidth() + 24;
		next.bounds(viewWidth - 64 - nw, by, nw, 42);
		put(next, g, mx, my, a, live);
		if (step > 0) {
			float bw = back.preferredWidth() + 10;
			back.bounds(64, by, bw, 42);
			put(back, g, mx, my, a, live);
		}
	}

	private void title(GuiGraphicsExtractor g, String title, String line, float y, float a) {
		float cx = viewWidth / 2;
		Fonts.draw(g, Typeface.OUTFIT_BOLD, title, cx, y, 44, Theme.text(1, a), Fonts.Align.CENTER, 0, false);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, line, cx, y + 44, 15, Theme.text(0.55f, a), Fonts.Align.CENTER, 0, false);
	}

	private void drawStep(GuiGraphicsExtractor g, int s, float mx, float my, float a, boolean live) {
		float cx = viewWidth / 2;
		switch (kinds[s]) {
			case WELCOME -> {
				if (dev.mordo.kairo.core.Edition.isVoid()) {
					drawVoidWelcome(g, a);
					return;
				}
				float l = logo.value();
				drawMark(g, cx, viewHeight * 0.36f, 70, a, l);
				float rise = Animation.motion((1 - l) * 20, 0);
				Fonts.draw(g, Typeface.OUTFIT_BOLD, "Welcome to " + ClientInfo.NAME, cx, viewHeight * 0.36f + 150 + rise, 54, Theme.text(1, a * l),
					Fonts.Align.CENTER, 0, false);
				Fonts.draw(g, Typeface.OUTFIT_REGULAR, "A visual client for Minecraft " + ClientInfo.MC_VERSION + ". A few quick choices and you're in.",
					cx, viewHeight * 0.36f + 204 + rise, 16, Theme.text(0.6f, a * l), Fonts.Align.CENTER, 0, false);
			}
			case SAFETY -> drawSafety(g, mx, my, a, live);
			case COLOUR -> {
				title(g, "Pick your colour", "It's used for highlights everywhere. Change it any time in Visuals.", 170, a);
				List<Widget> sw = widgets(Kind.COLOUR);
				float size = 70;
				float gap = 22;
				int perRow = 6;
				float rowW = perRow * size + (perRow - 1) * gap;
				for (int i = 0; i < sw.size(); i++) {
					float x = cx - rowW / 2 + (i % perRow) * (size + gap);
					float y = 290 + (i / perRow) * (size + 40);
					sw.get(i).bounds(x, y, size, size);
					put(sw.get(i), g, mx, my, a, live);
				}
				// A little sample of how it looks.
				float py = 540;
				Shapes.glassRect(g, cx - 210, py, 420, 70, 20, Theme.glassTint(), 0x26FFFFFF, 1.25f, a);
				Shapes.rect(g, cx - 190, py + 18, 130, 34, 12, Colors.multiplyAlpha(Theme.accent(), a));
				Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Button", cx - 125, py + 35, 13, Colors.multiplyAlpha(Theme.DARK_TEXT, a), Fonts.Align.CENTER, 0, false);
				Shapes.rect(g, cx - 40, py + 25, 40, 20, 10, Colors.multiplyAlpha(Theme.accent(), a));
				Shapes.circle(g, cx - 10, py + 35, 7, Colors.multiplyAlpha(0xFFFFFFFF, a));
				Fonts.draw(g, Typeface.MONO_BOLD, "ACCENT", cx + 30, py + 28, 8.5f, Colors.multiplyAlpha(Theme.accent(), a), Fonts.Align.LEFT, 2f, false);
				Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Looks like this", cx + 30, py + 46, 14, Theme.text(1, a));
			}
			case PRESET -> {
				title(g, "Pick a look", "Colour, atmosphere, HUD and crystals in one click. Change any of it later in Visuals.", 140, a);
				List<Widget> all = widgets(Kind.PRESET);
				layoutCards(g, all.subList(0, 4), 240, 230, 205, mx, my, a, live);
				layoutCards(g, all.subList(4, all.size()), 463, 230, 205, mx, my, a, live);
			}
			case QUALITY -> {
				title(g, "How much effect?", "Sets every effect at once. In game, J turns all visuals off and back on.", 170, a);
				layoutCards(g, widgets(Kind.QUALITY), 280, 250, 250, mx, my, a, live);
				Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Every visual can also be switched on its own: Right Shift → Visuals.", cx, 575, 13,
					Theme.text(0.5f, a), Fonts.Align.CENTER, 0, false);
			}
			case STYLE -> {
				title(g, "Choose your main menu", "You can switch any time with the gear on the main menu.", 170, a);
				layoutCards(g, widgets(Kind.STYLE), 280, 236, 230, mx, my, a, live);
			}
			case FEEL -> {
				title(g, "Make it feel right", "A few comfort switches. Visuals has all the rest.", 170, a);
				float w = 600;
				float x = cx - w / 2;
				float y = 270;
				Shapes.glassRect(g, x, y, w, toggles.size() * 66 + 16, 22, Theme.glassTint(), 0x26FFFFFF, 1.25f, a);
				for (int i = 0; i < toggles.size(); i++) {
					Toggle tg = toggles.get(i);
					float ry = y + 8 + i * 66;
					Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, tg.title(), x + 26, ry + 25, 15, Theme.text(1, a));
					Fonts.draw(g, Typeface.OUTFIT_REGULAR, tg.line(), x + 26, ry + 45, 11.5f, Theme.text(0.55f, a));
					tg.toggle().bounds(x + w - 26 - ToggleSwitch.WIDTH * 1.2f, ry + 33 - ToggleSwitch.HEIGHT * 0.6f, ToggleSwitch.WIDTH * 1.2f, ToggleSwitch.HEIGHT * 1.2f);
					put(tg.toggle(), g, mx, my, a, live);
					if (i < toggles.size() - 1) {
						Shapes.rect(g, x + 22, ry + 65, w - 44, 1, 0, Colors.argb(0.06f * a, 0xFFFFFF));
					}
				}
			}
			case HUD -> {
				title(g, "Pick a HUD", "Arrange it later with Right Shift → Layout.", 170, a);
				layoutCards(g, widgets(Kind.HUD), 280, 250, 210, mx, my, a, live);
			}
			case LOOK -> {
				title(g, "Look the part", "Only you see cosmetics. Lots more in Right Shift → Cosmetics.", 150, a);
				List<Widget> all = widgets(Kind.LOOK);
				layoutCards(g, all.subList(0, 6), 250, 160, 190, mx, my, a, live);
				layoutCards(g, all.subList(6, all.size()), 465, 180, 150, mx, my, a, live);
			}
			case DONE -> {
				title(g, "You're all set", "The keys worth remembering:", 170, a);
				List<String[]> keys = new ArrayList<>(List.of(
					new String[]{keyName(), "Open the client menu"},
					new String[]{"B", "Emote wheel (scroll for more pages)"},
					new String[]{"M", "Waypoints"},
					new String[]{"P", "PvP mode (in the menu)"},
					new String[]{"F5", "See your cosmetics"}));
				if (dev.mordo.kairo.core.Edition.isVoid()) {
					keys.add(1, new String[]{"J", "All visuals off and on"});
				}
				float w = 520;
				float x = cx - w / 2;
				float labelX = 0;
				for (String[] k : keys) {
					labelX = Math.max(labelX, Fonts.width(Typeface.MONO_BOLD, k[0], 11, 1) + 24);
				}
				float rowH = keys.size() > 5 ? 56 : 62;
				for (int i = 0; i < keys.size(); i++) {
					String[] k = keys.get(i);
					float ry = 270 + i * rowH;
					Shapes.glassRect(g, x, ry, w, 46, 16, Theme.glassTint(), 0x22FFFFFF, 1.25f, a);
					float kw = Fonts.width(Typeface.MONO_BOLD, k[0], 11, 1) + 24;
					Shapes.rect(g, x + 14, ry + 9, kw, 28, 8, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.18f), a),
						Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.7f), a), 1);
					Fonts.draw(g, Typeface.MONO_BOLD, k[0], x + 14 + kw / 2, ry + 23, 11, Colors.multiplyAlpha(Theme.accent(), a), Fonts.Align.CENTER, 1, false);
					Fonts.draw(g, Typeface.OUTFIT_MEDIUM, k[1], x + 14 + labelX + 18, ry + 23, 14, Theme.text(0.9f, a));
				}
			}
		}
	}

	/** Safety first: the flashing-lights warning, a choice of how much flashing, and reduce motion. */
	private void drawSafety(GuiGraphicsExtractor g, float mx, float my, float a, boolean live) {
		float cx = viewWidth / 2;
		int amber = 0xFFFFC94D;
		float iy = 128;
		Shapes.shadow(g, cx - 36, iy - 36, 72, 72, 36, 26, Colors.multiplyAlpha(Colors.withAlpha(amber, 0.3f), a));
		Shapes.triangle(g, cx, iy + 4, 62, (float) (-Math.PI / 2), Colors.multiplyAlpha(amber, a));
		Fonts.draw(g, Typeface.OUTFIT_BOLD, "!", cx, iy + 12, 30, Colors.multiplyAlpha(0xFF14110A, a), Fonts.Align.CENTER, 0, false);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, "Photosensitivity warning", cx, 205, 38, Theme.text(1, a), Fonts.Align.CENTER, 0, false);
		String[] paragraphs = {
			ClientInfo.NAME + " has flashing lights, bright bursts of colour, screen shakes and fast-moving effects. A very small number of people "
				+ "can have a seizure when they see flashing lights or patterns, even if they have never had one before.",
			"If you or anyone in your family has had seizures or epilepsy, talk to a doctor before playing. Stop playing right away and rest if you "
				+ "feel dizzy or sick, or notice blurred vision, eye or muscle twitches, or confusion."
		};
		float y = 250;
		for (String p : paragraphs) {
			for (String line : Fonts.wrap(Typeface.OUTFIT_REGULAR, p, 14, 780)) {
				Fonts.draw(g, Typeface.OUTFIT_REGULAR, line, cx, y, 14, Theme.text(0.75f, a), Fonts.Align.CENTER, 0, false);
				y += 21;
			}
			y += 10;
		}
		List<Widget> cards = widgets(Kind.SAFETY);
		layoutCards(g, cards.subList(0, 2), y + 6, 260, 160, mx, my, a, live);
		// Reduce motion, under the cards.
		float rw = 540;
		float rx = cx - rw / 2;
		float ry = y + 184;
		Shapes.glassRect(g, rx, ry, rw, 52, 16, Theme.glassTint(), 0x22FFFFFF, 1.25f, a);
		Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, "Reduce motion", rx + 20, ry + 19, 14, Theme.text(1, a));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Menus fade instead of sliding, scaling or spinning", rx + 20, ry + 37, 11, Theme.text(0.55f, a));
		calmMotion.bounds(rx + rw - 20 - ToggleSwitch.WIDTH, ry + 26 - ToggleSwitch.HEIGHT / 2f, ToggleSwitch.WIDTH, ToggleSwitch.HEIGHT);
		put(calmMotion, g, mx, my, a, live);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Change these any time: Right Shift → Visuals → Fewer flashes.", cx, ry + 74, 11.5f, Theme.text(0.45f, a),
			Fonts.Align.CENTER, 0, false);
	}

	/** Kairo Void's welcome: what the visual edition adds, next to plain Kairo, over a living sky. */
	private void drawVoidWelcome(GuiGraphicsExtractor g, float a) {
		float cx = viewWidth / 2;
		float l = logo.value();
		float rise = Animation.motion((1 - l) * 20, 0);
		drawMark(g, cx, 104, 34, a, l);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, "Welcome to " + ClientInfo.NAME, cx, 180 + rise, 48, Theme.text(1, a * l), Fonts.Align.CENTER, 0, false);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "The visual edition of Kairo: everything Kairo has, turned all the way up.", cx, 222 + rise, 15,
			Theme.text(0.6f, a * l), Fonts.Align.CENTER, 0, false);

		String[][] rows = {
			{"Menus", "Frosted glass", "Flat greys with drifting light"},
			{"Combat", "Hit particles, kill effects", "+ energy blades, shockwaves, hit-stop"},
			{"Sky", "The normal sky", "Nebulae, a galaxy, aurora, a ringed planet"},
			{"World", "Your world as it is", "10 atmospheres: blizzard, inferno, galaxy..."},
			{"Crystals", "Vanilla crystals", "Prism, Void core, Plasma, Hologram"},
			{"Looks", "Pick each setting", "11 one-click presets, share codes"},
		};
		float tw = 820;
		float tx = cx - tw / 2;
		float top = 262;
		float headH = 40;
		float rowH = 52;
		float labelW = 150;
		float colW = (tw - labelW) / 2;
		float voidX = tx + labelW + colW;
		float tableH = headH + rows.length * rowH;
		float k = Math.clamp((l - 0.3f) / 0.7f, 0, 1);
		float ta = a * k;
		// The Void column glows over a changing atmosphere.
		voidBackdrop(g, voidX, top, colW, tableH, ta);
		Shapes.shadow(g, voidX, top, colW, tableH, 10, 22, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.25f), ta));
		Shapes.rect(g, voidX, top, colW, tableH, 10, 0, Colors.multiplyAlpha(Colors.withAlpha(Theme.accent(), 0.8f), ta), 1.5f);
		Fonts.draw(g, Typeface.MONO_BOLD, "KAIRO", tx + labelW + colW / 2, top + headH / 2, 10, Theme.text(0.45f, ta), Fonts.Align.CENTER, 2f, false);
		Fonts.draw(g, Typeface.MONO_BOLD, "KAIRO VOID", voidX + colW / 2, top + headH / 2, 10, Colors.multiplyAlpha(Theme.accent(), ta), Fonts.Align.CENTER, 2f, false);
		for (int i = 0; i < rows.length; i++) {
			float ry = top + headH + i * rowH;
			float rowA = ta * Math.clamp((l - 0.35f - i * 0.08f) / 0.3f, 0, 1);
			Shapes.rect(g, tx, ry, tw, 1, 0, Colors.argb(0.07f * rowA, 0xFFFFFF));
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, rows[i][0], tx + 18, ry + rowH / 2, 14, Theme.text(0.85f, rowA));
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, rows[i][1], tx + labelW + colW / 2, ry + rowH / 2, 13, Theme.text(0.45f, rowA), Fonts.Align.CENTER, 0, false);
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, rows[i][2], voidX + colW / 2, ry + rowH / 2, 13, Theme.text(1, rowA), Fonts.Align.CENTER, 0, true);
		}
	}

	/** A dimmed atmosphere scene that changes every few seconds (behind the Void column). */
	private static void voidBackdrop(GuiGraphicsExtractor g, float x, float y, float w, float h, float a) {
		var all = dev.mordo.kairo.core.VoidSettings.Atmosphere.values();
		double t = System.currentTimeMillis() / 3500.0;
		int i = (int) Math.floor(t) % (all.length - 1) + 1;
		int next = i % (all.length - 1) + 1;
		float k = (float) Math.clamp((t - Math.floor(t) - 0.8) / 0.2, 0, 1);
		dev.mordo.kairo.gui.menu.VisualPreviews.atmosphere(g, all[i], x, y, w, h, a * 0.6f * (1 - k));
		if (k > 0) {
			dev.mordo.kairo.gui.menu.VisualPreviews.atmosphere(g, all[next], x, y, w, h, a * 0.6f * k);
		}
		Shapes.rect(g, x, y, w, h, dev.mordo.kairo.gui.widgets.VisualCard.ART_RADIUS, Colors.argb(0.45f * a, 0x000000));
	}
	private static String keyName() {
		String name = dev.mordo.kairo.core.keybinds.ClientKeybinds.INSTANCE.openMenu.get().displayName().getString();
		return name.toUpperCase(java.util.Locale.ROOT).replace("RIGHT ", "R").replace("LEFT ", "L");
	}

	private void layoutCards(GuiGraphicsExtractor g, List<Widget> cards, float y, float w, float h, float mx, float my, float a, boolean live) {
		float gap = 18;
		float maxW = viewWidth - 160;
		float cw = Math.min(w, (maxW - gap * (cards.size() - 1)) / cards.size());
		float total = cards.size() * cw + (cards.size() - 1) * gap;
		float x = viewWidth / 2 - total / 2;
		for (Widget card : cards) {
			card.bounds(x, y, cw, h);
			put(card, g, mx, my, a, live);
			x += cw + gap;
		}
	}

	// ---- art ----

	/** The Kairo mark: a ring with an orange arc in its gap and a dot, drawn in as {@code progress} goes 0 → 1. */
	private static void drawMark(GuiGraphicsExtractor g, float cx, float cy, float r, float a, float progress) {
		int accent = Theme.accent();
		float pi = (float) Math.PI;
		float gap = 0.82f;
		float sweep = (pi * 2 - gap * 2) * progress;
		if (progress > 0.02f) {
			Shapes.shadow(g, cx - r, cy - r, r * 2, r * 2, r, r * 0.5f, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.3f * progress), a));
		}
		Shapes.arc(g, cx, cy, r, r * 0.17f, -pi / 2 + gap, sweep, Colors.multiplyAlpha(accent, a));
		float pop = Math.clamp((progress - 0.6f) / 0.4f, 0, 1);
		if (pop > 0) {
			Shapes.arc(g, cx, cy, r, r * 0.17f, -pi / 2 - 0.3f, 0.6f, Colors.multiplyAlpha(Colors.withAlpha(Theme.pvpAccent(), pop), a));
			Shapes.circle(g, cx, cy, r * 0.32f * pop, Colors.multiplyAlpha(accent, a));
		}
	}

	private static void styleArt(GuiGraphicsExtractor g, int kind, float x, float y, float w, float h, float a) {
		int accent = Theme.accent();
		Shapes.rect(g, x, y, w, h, 12, Colors.argb(0.35f * a, 0x000000));
		float mx = x + w / 2;
		switch (kind) {
			case 0 -> {
				Shapes.rect(g, x + 16, y + 18, 60, 9, 3, Colors.argb(0.85f * a, 0xFFFFFF));
				for (int i = 0; i < 4; i++) {
					Shapes.rect(g, x + 16, y + 38 + i * 18, 80, 12, 4, Colors.multiplyAlpha(i == 0 ? Colors.withAlpha(accent, 0.85f) : 0x40FFFFFF, a));
				}
				Shapes.rect(g, x + w - 76, y + 38, 60, 32, 6, Colors.argb(0.2f * a, 0xFFFFFF));
				Shapes.rect(g, x + w - 76, y + 78, 60, 32, 6, Colors.argb(0.2f * a, 0xFFFFFF));
			}
			case 1 -> {
				Shapes.rect(g, mx - 40, y + 20, 80, 26, 6, Colors.argb(0.85f * a, 0xFFFFFF));
				Shapes.rect(g, mx - 30, y + 54, 60, 6, 3, Colors.argb(0.3f * a, 0xFFFFFF));
				Shapes.rect(g, mx - 70, y + h - 40, 140, 28, 10, Colors.argb(0.16f * a, 0xFFFFFF));
				for (int i = 0; i < 5; i++) {
					float s = i == 2 ? 22 : 16;
					Shapes.rect(g, mx - 60 + i * 26 + (22 - s) / 2, y + h - 26 - s / 2, s, s, 5, Colors.multiplyAlpha(i == 2 ? accent : 0x66FFFFFF, a));
				}
			}
			case 2 -> {
				float cy = y + h / 2;
				for (int i = 0; i < 40; i++) {
					double t = i * Math.PI * 2 / 40;
					Shapes.circle(g, mx + (float) Math.cos(t) * (w * 0.38f), cy + (float) Math.sin(t) * 18, 1, Colors.argb(0.35f * a, 0xFFFFFF));
				}
				Shapes.circle(g, mx, cy, 13, Colors.multiplyAlpha(accent, a));
				for (int i = 0; i < 5; i++) {
					double t = Math.PI / 2 + i * Math.PI * 2 / 5 + System.currentTimeMillis() / 4000.0;
					float d = (float) Math.sin(t);
					Shapes.circle(g, mx + (float) Math.cos(t) * (w * 0.38f), cy + d * 18, 6 + d * 2.5f, Colors.multiplyAlpha(d > 0.8f ? accent : 0x99FFFFFF, a));
				}
			}
			case 3 -> {
				// Dashboard: tiles, one with a little figure.
				float c = (w - 16 - 12) / 3;
				float top = y + 10;
				float hh = h - 20;
				Shapes.rect(g, x + 8, top, c, hh * 0.38f, 4, Colors.argb(0.75f * a, 0xFFFFFF));
				Shapes.rect(g, x + 8, top + hh * 0.38f + 5, c, hh * 0.62f - 5, 4, Colors.argb(0.2f * a, 0xFFFFFF));
				Shapes.rect(g, x + 14 + c, top, c, hh * 0.3f, 4, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.8f), a));
				Shapes.rect(g, x + 14 + c, top + hh * 0.3f + 5, c, hh * 0.33f, 4, Colors.argb(0.2f * a, 0xFFFFFF));
				Shapes.rect(g, x + 14 + c, top + hh * 0.63f + 10, c, hh * 0.37f - 10, 4, Colors.argb(0.2f * a, 0xFFFFFF));
				Shapes.rect(g, x + 20 + c * 2, top, c, hh, 4, Colors.argb(0.14f * a, 0xFFFFFF));
				float fx = x + 20 + c * 2.5f;
				float fy = top + hh * 0.22f;
				Shapes.rect(g, fx - 7, fy, 14, 14, 2, Colors.argb(0.85f * a, 0xFFFFFF));
				Shapes.rect(g, fx - 9, fy + 16, 18, 22, 2, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.85f), a));
				Shapes.rect(g, fx - 8, fy + 40, 16, 18, 2, Colors.argb(0.5f * a, 0xFFFFFF));
			}
			case 5 -> {
				// Simple: the name centred, a column of words under it.
				Shapes.rect(g, mx - 42, y + 22, 84, 14, 4, Colors.argb(0.85f * a, 0xFFFFFF));
				Shapes.rect(g, mx - 20, y + 42, 40, 2, 1, Colors.multiplyAlpha(accent, a));
				for (int i = 0; i < 4; i++) {
					float lw = 54 - (i % 2) * 14;
					Shapes.rect(g, mx - lw / 2, y + 56 + i * 15, lw, 7, 3, Colors.multiplyAlpha(i == 0 ? Colors.withAlpha(accent, 0.9f) : 0x50FFFFFF, a));
				}
			}
			default -> {
				Shapes.rect(g, mx - 46, y + 18, 92, 16, 3, Colors.argb(0.85f * a, 0xFFFFFF));
				for (int i = 0; i < 3; i++) {
					Shapes.rect(g, mx - 60, y + 46 + i * 16, 120, 11, 3, Colors.argb(0.3f * a, 0xFFFFFF));
				}
				Shapes.rect(g, mx - 60, y + 94, 58, 11, 3, Colors.argb(0.3f * a, 0xFFFFFF));
				Shapes.rect(g, mx + 2, y + 94, 58, 11, 3, Colors.argb(0.3f * a, 0xFFFFFF));
			}
		}
	}

	private static void hudArt(GuiGraphicsExtractor g, int preset, float x, float y, float w, float h, float a) {
		Shapes.rect(g, x, y, w, h, 12, Colors.argb(0.35f * a, 0x000000));
		int accent = Theme.accent();
		int box = Colors.argb(0.35f * a, 0xFFFFFF);
		switch (preset) {
			case 0 -> {
				Shapes.rect(g, x + w - 52, y + 10, 42, 12, 3, box);
				Shapes.rect(g, x + w - 52, y + 26, 42, 12, 3, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.6f), a));
			}
			case 1 -> {
				for (int i = 0; i < 3; i++) {
					Shapes.rect(g, x + 10 + i * 16, y + 26, 13, 13, 3, box);
				}
				Shapes.rect(g, x + 26, y + 10, 13, 13, 3, box);
				Shapes.rect(g, x + 10, y + 42, 45, 9, 3, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.6f), a));
				Shapes.rect(g, x + w - 52, y + 10, 42, 12, 3, box);
				for (int i = 0; i < 4; i++) {
					Shapes.rect(g, x + w - 26, y + h - 60 + i * 13, 16, 10, 2, box);
				}
			}
			case 2 -> {
				Shapes.rect(g, x + 10, y + 10, 50, 36, 3, box);
				Shapes.rect(g, x + w - 52, y + 10, 42, 12, 3, box);
				Shapes.rect(g, x + w - 52, y + 26, 42, 12, 3, box);
				Shapes.rect(g, x + 10, y + h / 2, 56, 40, 3, box);
				Shapes.rect(g, x + w / 2 - 40, y + 8, 80, 10, 3, Colors.multiplyAlpha(Colors.withAlpha(accent, 0.6f), a));
				for (int i = 0; i < 4; i++) {
					Shapes.rect(g, x + w - 26, y + h - 60 + i * 13, 16, 10, 2, box);
				}
			}
			default -> Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, "Just the game", x + w / 2, y + h / 2, 12, Theme.text(0.45f, a));
		}
	}

	private static void capeArt(GuiGraphicsExtractor g, String choice, float x, float y, float w, float h, float a) {
		if (choice.equals("none")) {
			IconRenderer.draw(g, Icon.CLOSE, x + w / 2, y + h / 2, Math.min(w, h) * 0.4f, Theme.text(0.4f, a), 1.8f);
			return;
		}
		Cosmetics c = Cosmetics.get();
		Identifier texture = Capes.preview(choice, c.capeColor.argb(), c.capeColor2.argb());
		if (texture == null || a < 0.5f) {
			return;
		}
		float[] uv = Capes.faceUv();
		float ch = h;
		float cw = ch * 10f / 16f;
		g.blit(texture, Math.round(x + w / 2 - cw / 2), Math.round(y), Math.round(x + w / 2 + cw / 2), Math.round(y + ch), uv[0], uv[1], uv[2], uv[3]);
	}

	// ---- input ----

	@Override
	protected boolean onKey(KeyEvent event) {
		if (event.isConfirmation()) {
			next();
			return true;
		}
		if (event.isRight()) {
			next();
			return true;
		}
		if (event.isLeft()) {
			back();
			return true;
		}
		return false;
	}

}
