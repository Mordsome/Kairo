package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Module;
import net.minecraft.client.gui.screens.Screen;
import dev.mordo.kairo.core.keybinds.ClientKeybinds;
import dev.mordo.kairo.gui.KairoScreen;
import dev.mordo.kairo.gui.widgets.WidgetHost;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.function.Function;

/**
 * The client menu: one screen with the shared blurred backdrop, showing one {@link Page} at a time.
 * Switching pages overlaps the old page's exit with the new page's intro.
 */
public final class MenuScreen extends KairoScreen {
	private final WidgetHost host = new WidgetHost();
	private final Animation backdrop = new Animation(0);
	private final RingPage ring;
	private @Nullable ModsPage mods;
	private @Nullable VisualsPage visuals;
	private @Nullable SettingsPage settings;
	private @Nullable PvpPage pvp;
	private @Nullable CosmeticsPage cosmetics;
	private Page current;
	private @Nullable Page leaving;
	private boolean started;
	private @Nullable Screen returnTo;

	/** Opens the menu on the ring. */
	public MenuScreen() {
		this(MenuScreen::ring);
	}

	/** Opens the menu on any page (e.g. {@code MenuScreen::mods}). */
	public MenuScreen(Function<MenuScreen, Page> start) {
		super(Component.literal(ClientInfo.NAME));
		this.ring = new RingPage(this);
		this.current = start.apply(this);
	}

	/** Opens the menu straight on one mod's settings; closing it goes to {@code returnTo} (null = the game). */
	public static MenuScreen forModSettings(Module module, @Nullable Screen returnTo) {
		MenuScreen screen = new MenuScreen(menu -> {
			ModsPage page = menu.mods();
			page.showSettingsNow(module);
			return page;
		});
		screen.returnTo = returnTo;
		return screen;
	}

	/** Opens the menu on a page (e.g. {@code MenuScreen::mods}); closing it goes to {@code returnTo}. */
	public static MenuScreen openOn(Function<MenuScreen, Page> start, @Nullable Screen returnTo) {
		MenuScreen screen = new MenuScreen(start);
		screen.returnTo = returnTo;
		return screen;
	}

	/** Esc and friends close the menu, back to where it was opened from. */
	@Override
	public void onClose() {
		close(returnTo);
	}

	public WidgetHost host() {
		return host;
	}

	// ---- pages (created lazily, kept so their state survives going back and forth) ----

	public RingPage ring() {
		return ring;
	}

	public ModsPage mods() {
		if (mods == null) {
			mods = new ModsPage(this);
		}
		return mods;
	}

	public VisualsPage visuals() {
		if (visuals == null) {
			visuals = new VisualsPage(this);
		}
		return visuals;
	}

	private @Nullable VoidVisualsPage voidVisuals;

	/** Kairo Void's card-grid Visuals page. */
	public VoidVisualsPage voidVisuals() {
		if (voidVisuals == null) {
			voidVisuals = new VoidVisualsPage(this);
		}
		return voidVisuals;
	}

	/** The Visuals page for this edition. */
	public Page visualsPage() {
		return dev.mordo.kairo.core.Edition.isVoid() ? voidVisuals() : visuals();
	}

	public SettingsPage settings() {
		if (settings == null) {
			settings = new SettingsPage(this);
		}
		return settings;
	}

	public CosmeticsPage cosmetics() {
		if (cosmetics == null) {
			cosmetics = new CosmeticsPage(this);
		}
		return cosmetics;
	}

	public PvpPage pvp() {
		if (pvp == null) {
			pvp = new PvpPage(this);
		}
		return pvp;
	}

	public void navigate(Page next) {
		if (next == current || isClosing()) {
			return;
		}
		host.reset();
		leaving = current;
		leaving.hide();
		current = next;
		current.show();
	}

	// ---- lifecycle ----

	@Override
	protected void init() {
		super.init();
		if (!started) {
			started = true;
			backdrop.animateTo(1, 250, Easing.OUT_CUBIC);
			dev.mordo.kairo.sound.UiSounds.open();
			current.show();
		}
	}

	@Override
	public void removed() {
		host.reset(); // also switches SDL text input off
	}

	@Override
	protected void onCloseStarted() {
		host.reset();
		backdrop.animateTo(0, 220, Easing.IN_CUBIC);
		current.hide();
	}

	@Override
	protected boolean isCloseAnimationDone() {
		return backdrop.isDone() && current.isHidden();
	}

	// ---- drawing ----

	@Override
	protected void drawBackground(GuiGraphicsExtractor g, float partialTick) {
		Shapes.backdrop(g, backdrop.value(), ClientSettings.INSTANCE.backgroundDim.floatValue(), 0.55f);
	}

	@Override
	protected void draw(GuiGraphicsExtractor g, float mx, float my, float partialTick) {
		host.beginFrame(viewWidth, viewHeight);
		if (leaving != null) {
			if (leaving.isHidden()) {
				leaving = null;
			} else {
				leaving.render(g, mx, my, viewWidth, viewHeight, false);
			}
		}
		current.render(g, mx, my, viewWidth, viewHeight, !isClosing());
		host.renderPopup(g, mx, my);
	}

	// ---- input ----

	@Override
	protected boolean onClick(float x, float y, int button) {
		return host.mouseClicked(x, y, button) || current.onClick(x, y, button);
	}

	@Override
	protected boolean onRelease(float x, float y, int button) {
		return host.mouseReleased(x, y, button) || current.onRelease(x, y, button);
	}

	@Override
	protected boolean onDrag(float x, float y, int button, float dx, float dy) {
		return host.mouseDragged(x, y, button) || current.onDrag(x, y, button);
	}

	@Override
	protected boolean onScroll(float x, float y, float amount) {
		return host.mouseScrolled(x, y, amount) || current.onScroll(x, y, amount);
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (isClosing()) {
			return true;
		}
		if (host.keyPressed(event) || current.keyPressed(event)) {
			return true;
		}
		if (ClientKeybinds.INSTANCE.openMenu.get().matchesKey(event.key())) {
			onClose();
			return true;
		}
		if (event.isEscape()) {
			if (!current.onEscape()) {
				onClose();
			}
			return true;
		}
		return super.keyPressed(event);
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		return !isClosing() && (host.charTyped(event) || current.charTyped(event));
	}
}
