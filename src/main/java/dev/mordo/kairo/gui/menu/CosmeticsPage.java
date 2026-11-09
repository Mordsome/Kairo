package dev.mordo.kairo.gui.menu;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.settings.Choice;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.Setting;
import dev.mordo.kairo.cosmetics.Capes;
import dev.mordo.kairo.cosmetics.Cosmetics;
import dev.mordo.kairo.cosmetics.Emotes;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.SettingsList;
import dev.mordo.kairo.gui.widgets.Widget;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import dev.mordo.kairo.sound.UiSounds;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * The wardrobe (ring → Cosmetics): categories on the left, your player in 3D (drag to turn it), and a grid of
 * items with their colours and options underneath. Everything applies live; only you see it.
 */
public final class CosmeticsPage extends PanelPage {
	private record Category(String name, Icon icon, SettingsList list) {
	}

	private final List<Category> categories = new ArrayList<>();
	private final List<NavItem> nav = new ArrayList<>();
	private int selected;
	private float yaw = 20;
	private boolean dragging;
	private float lastDragX;
	private long lastFrame;
	private float previewX;
	private float previewY;
	private float previewW;
	private float previewH;
	private static final float BUTTON = 30;
	private float pauseX = -100;
	private float frontX = -100;
	private float buttonY = -100;
	private float lastMouseX;
	private float lastMouseY;
	private boolean facingFront;

	CosmeticsPage(MenuScreen menu) {
		super(menu);
		Cosmetics c = Cosmetics.get();
		category("Capes", Icon.IMAGE, capeTiles(c), List.of(c.cape, c.capeColor, c.capeColor2));
		category("Wings", Icon.SPARKLE, enumTiles(c.wings, "wings"), List.of(c.wingColor, c.wingColor2, c.wingSize, c.wingFlap, c.wingRainbow, c.wingGlow));
		category("Hats", Icon.TOTEM, enumTiles(c.hat, "hats"), List.of(c.hatColor, c.hatColor2, c.earSpacing, c.earAngle, c.earSize, c.earTwitch,
			c.hatHeight, c.hatForward, c.hatSize, c.hatTilt));
		category("Bandanas", Icon.FLAME, enumTiles(c.bandana, "bandanas"), List.of(c.bandanaColor, c.bandanaColor2, c.bandanaPattern));
		category("Trails", Icon.HEART, enumTiles(c.trail, "trails"), List.of(c.trailDensity, c.trailWhenMoving));
		emoteCategory(c);
	}

	/** Emotes: one grid per page (dances, reactions...), hearts on your favourites (right-click a tile). */
	private void emoteCategory(Cosmetics c) {
		int index = categories.size();
		SettingsList list = SettingsList.forSettings(host, "OPTIONS", List.of(c.emoteKey, c.emoteCamera, c.emoteBurst,
			c.music1Name, c.music1, c.music1Bpm, c.music1Offset, c.music1Dance, c.music2Name, c.music2, c.music2Bpm, c.music2Offset, c.music2Dance,
			c.musicVolume), false);
		List<List<Tile>> groups = new ArrayList<>();
		for (Emotes.Kind kind : Emotes.Kind.values()) {
			groups.add(emoteTiles(Emotes.of(kind)));
		}
		list.header((g, x, y, w, mx, my, fade, put) -> {
			float h = 0;
			Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Click to try one. Right-click to add it to your favourites (the first page of the wheel).", x, y + 6, 11,
				Theme.text(0.55f, fade));
			h += 24;
			Emotes.Kind[] kinds = Emotes.Kind.values();
			for (int i = 0; i < kinds.length; i++) {
				Fonts.draw(g, Typeface.MONO_BOLD, kinds[i].label().toUpperCase(java.util.Locale.ROOT), x, y + h + 8, 8.5f, Theme.text(0.5f, fade),
					Fonts.Align.LEFT, 2f, false);
				h += 22;
				h += layoutTiles(groups.get(i), g, x, y + h, w, fade, put) + 16;
			}
			return h;
		});
		categories.add(new Category("Emotes", Icon.USER, list));
		nav.add(new NavItem("Emotes", Icon.USER, null, () -> selected == index, () -> {
			selected = index;
			categories.get(index).list().resetScroll();
		}));
	}

	/** Shows a category (0 = Capes … 5 = Emotes). */
	public void select(int index) {
		selected = Math.clamp(index, 0, categories.size() - 1);
		categories.get(selected).list().resetScroll();
	}

	// ---- categories and tiles ----

	private void category(String name, Icon icon, List<Tile> tiles, List<Setting<?>> settings) {
		int index = categories.size();
		SettingsList list = SettingsList.forSettings(host, "OPTIONS", settings, false);
		list.header((g, x, y, w, mx, my, fade, put) -> layoutTiles(tiles, g, x, y, w, fade, put));
		categories.add(new Category(name, icon, list));
		nav.add(new NavItem(name, icon, null, () -> selected == index, () -> {
			selected = index;
			categories.get(index).list().resetScroll();
		}));
	}

	private static float layoutTiles(List<Tile> tiles, GuiGraphicsExtractor g, float x, float y, float w, float fade, Consumer<Widget> put) {
		int columns = Math.max(2, (int) ((w + 10) / (148 + 10)));
		float tw = (w - 10 * (columns - 1)) / columns;
		float th = 64;
		for (int i = 0; i < tiles.size(); i++) {
			Tile tile = tiles.get(i);
			tile.bounds(x + (i % columns) * (tw + 10), y + (i / columns) * (th + 10), tw, th);
			put.accept(tile);
		}
		int rows = (tiles.size() + columns - 1) / columns;
		return rows * (th + 10) - 10;
	}

	/** The pixel-art thumbnail for a style (textures/gui/cosmetics/<folder>/<style>.png). */
	public static Identifier art(String folder, Enum<?> style) {
		return Identifier.fromNamespaceAndPath("kairo", "textures/gui/cosmetics/" + folder + "/" + style.name().toLowerCase(java.util.Locale.ROOT) + ".png");
	}

	private static <E extends Enum<E> & Cosmetics.Styled> List<Tile> enumTiles(EnumSetting<E> setting, String folder) {
		List<Tile> tiles = new ArrayList<>();
		for (E value : setting.values()) {
			Tile tile = new Tile(value.label(), value.none() ? Icon.CLOSE : null, () -> setting.get() == value, () -> setting.set(value), null);
			tile.art = value.none() ? null : art(folder, value);
			tiles.add(tile);
		}
		return tiles;
	}

	private static List<Tile> capeTiles(Cosmetics c) {
		List<Tile> tiles = new ArrayList<>();
		tiles.add(new Tile("None", Icon.CLOSE, () -> c.cape.get().equals("none"), () -> c.cape.set("none"), null));
		for (Capes.Design design : Capes.designs()) {
			String id = "design:" + design.name().toLowerCase(java.util.Locale.ROOT);
			tiles.add(new Tile(design.label(), null, () -> c.cape.get().equals(id), () -> c.cape.set(id),
				() -> Capes.preview(id, c.capeColor.argb(), c.capeColor2.argb())));
		}
		// Imported images (Add image… in the options below).
		for (Choice choice : c.cape.options()) {
			if (choice.id().startsWith("file:")) {
				tiles.add(new Tile(choice.label(), null, () -> c.cape.get().equals(choice.id()), () -> c.cape.set(choice.id()),
					() -> Capes.preview(choice.id(), 0, 0)));
			}
		}
		return tiles;
	}

	private static List<Tile> emoteTiles(List<Emotes.Emote> emotes) {
		List<Tile> tiles = new ArrayList<>();
		for (Emotes.Emote emote : emotes) {
			Tile tile = new Tile(emote.label(), emote.icon(), () -> Emotes.current() == emote, () -> {
				if (Emotes.current() == emote) {
					Emotes.stop();
				} else {
					Emotes.playHere(emote);
				}
			}, null);
			tile.favourite = () -> Emotes.isFavourite(emote);
			tile.onRight = () -> {
				boolean was = Emotes.isFavourite(emote);
				if (Emotes.toggleFavourite(emote)) {
					UiSounds.toggle(!was);
				} else {
					dev.mordo.kairo.gui.Toasts.show("Up to " + Emotes.MAX_FAVOURITES + " favourites", Theme.accent());
				}
			};
			tiles.add(tile);
		}
		return tiles;
	}

	/** One choice in the grid: a cape swatch or an icon, the name, and a tick when it's the current one. */
	private static final class Tile extends Widget {
		private final String label;
		private final @Nullable Icon icon;
		private final BooleanSupplier selected;
		private final Runnable action;
		private final java.util.function.@Nullable Supplier<@Nullable Identifier> swatch;
		private final Animation pick = new Animation(0);
		private @Nullable Identifier art;
		private @Nullable BooleanSupplier favourite;
		private @Nullable Runnable onRight;

		Tile(String label, @Nullable Icon icon, BooleanSupplier selected, Runnable action,
			 java.util.function.@Nullable Supplier<@Nullable Identifier> swatch) {
			this.label = label;
			this.icon = icon;
			this.selected = selected;
			this.action = action;
			this.swatch = swatch;
		}

		@Override
		protected void draw(GuiGraphicsExtractor g, float mx, float my, float fade) {
			boolean on = selected.getAsBoolean();
			pick.animateTo(on ? 1 : 0, 200);
			float p = pick.value();
			float hv = hover.value();
			int accent = Theme.accent();
			int fill = Colors.lerp(Colors.argb(0.05f + 0.04f * hv, 0xFFFFFF), Colors.withAlpha(accent, 0.16f), p);
			int stroke = Colors.lerp(Colors.argb(0.10f + 0.15f * hv, 0xFFFFFF), Colors.withAlpha(accent, 0.85f), p);
			Shapes.rect(g, x, y, w, h, 12, Colors.multiplyAlpha(fill, fade), Colors.multiplyAlpha(stroke, fade), 1 + p * 0.75f);
			float art = h - 16;
			float ax = x + 8;
			float ay = y + 8;
			Identifier texture = swatch != null ? swatch.get() : null;
			if (texture != null && fade > 0.5f) {
				float[] uv = Capes.faceUv();
				float sw = art * 10f / 16f;
				g.blit(texture, Math.round(ax + (art - sw) / 2), Math.round(ay), Math.round(ax + (art + sw) / 2), Math.round(ay + art), uv[0], uv[1], uv[2], uv[3]);
			} else if (this.art != null) {
				Shapes.rect(g, ax, ay, art, art, 9, Colors.multiplyAlpha(Colors.lerp(0x10FFFFFF, Colors.withAlpha(accent, 0.2f), p), fade));
				int inset = Math.round(art * 0.08f);
				g.blit(this.art, Math.round(ax) + inset, Math.round(ay) + inset, Math.round(ax + art) - inset, Math.round(ay + art) - inset, 0, 1, 0, 1);
			} else {
				Shapes.rect(g, ax, ay, art, art, 9, Colors.multiplyAlpha(Colors.lerp(0x10FFFFFF, Colors.withAlpha(accent, 0.2f), p), fade));
				IconRenderer.draw(g, icon != null ? icon : Icon.SPARKLE, ax + art / 2, ay + art / 2, art * 0.45f,
					Colors.multiplyAlpha(on ? accent : Theme.text(0.75f), fade), 1.9f);
			}
			float tx = ax + art + 12;
			Fonts.draw(g, Typeface.OUTFIT_SEMIBOLD, Fonts.ellipsize(Typeface.OUTFIT_SEMIBOLD, label, 13, w - (tx - x) - 26), tx, y + h / 2, 13,
				Theme.text(1, fade));
			if (p > 0.01f) {
				Shapes.circle(g, x + w - 16, y + 16, 7 * p, Colors.multiplyAlpha(accent, fade));
				IconRenderer.draw(g, Icon.CHECK, x + w - 16, y + 16, 9 * p, Colors.multiplyAlpha(Theme.DARK_TEXT, fade), 3f);
			}
			if (favourite != null && favourite.getAsBoolean()) {
				IconRenderer.draw(g, Icon.HEART, x + w - 16, y + h - 16, 10, Colors.multiplyAlpha(accent, fade), 2f);
			}
		}

		@Override
		public boolean mouseClicked(float mx, float my, int button) {
			if (button == InputConstants.MOUSE_BUTTON_RIGHT && onRight != null && contains(mx, my)) {
				onRight.run();
				return true;
			}
			if (button != InputConstants.MOUSE_BUTTON_LEFT || !contains(mx, my)) {
				return false;
			}
			UiSounds.click();
			action.run();
			return true;
		}
	}

	// ---- page ----

	@Override
	protected String navLabel() {
		return "WARDROBE";
	}

	@Override
	protected List<NavItem> navItems() {
		return nav;
	}

	@Override
	protected void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		lastMouseX = mx;
		lastMouseY = my;
		Category cat = categories.get(selected);
		drawTitle(g, "Cosmetics", cat.name(), contentX, fade);
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, "Only you see these: they're drawn on your own player.", contentX, py + 82, 11,
			Theme.text(0.5f, fade));
		float top = py + 104;
		float bottom = py + ph - 20;
		previewW = Math.min(300, contentW * 0.36f);
		previewX = contentX;
		previewY = top;
		previewH = bottom - top;
		drawPreview(g, previewX, previewY, previewW, previewH, fade);
		float listX = contentX + previewW + 22;
		cat.list().render(g, listX, top, contentX + contentW + 14 - listX, bottom - top, mx, my, fade, live);
	}

	/** Your player, slowly turning (drag to turn it yourself). */
	private void drawPreview(GuiGraphicsExtractor g, float x, float y, float w, float h, float fade) {
		Shapes.rect(g, x, y, w, h, 18, Colors.multiplyAlpha(0x30000000, fade), Colors.multiplyAlpha(Theme.BORDER, fade), 1);
		long now = System.nanoTime();
		float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
		lastFrame = now;
		boolean spin = Cosmetics.get().previewSpin.isOn();
		if (!dragging && spin) {
			yaw += dt * 22;
		}
		if (facingFront) {
			// Ease round to face the camera (Front button).
			float target = Math.round(yaw / 360f) * 360f;
			yaw += (target - yaw) * Math.min(1, dt * 10);
			if (Math.abs(target - yaw) < 0.3f) {
				yaw = target;
				facingFront = false;
			}
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) {
			IconRenderer.draw(g, Icon.USER, x + w / 2, y + h / 2 - 20, 54, Theme.text(0.35f, fade), 1.6f);
			Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, "Join a world to see", x + w / 2, y + h / 2 + 34, 12, Theme.text(0.55f, fade));
			Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, "your look in 3D", x + w / 2, y + h / 2 + 52, 12, Theme.text(0.55f, fade));
			return;
		}
		if (fade < 0.6f) {
			return;
		}
		Fonts.drawCentered(g, Typeface.MONO_BOLD, "DRAG TO TURN", x + w / 2, y + h - 16, 8, Theme.text(0.35f, fade));
		// Pause / play and face-front buttons in the top-right corner.
		pauseX = x + w - 42;
		frontX = pauseX - 36;
		buttonY = y + 12;
		previewButton(g, pauseX, buttonY, fade, spin ? null : Icon.PLAY, spin ? "Pause" : "Spin");
		previewButton(g, frontX, buttonY, fade, Icon.USER, "Face front");
		EntityRenderState state = mc.getEntityRenderDispatcher().getRenderer(mc.player).createRenderState(mc.player, 1.0f);
		state.shadowPieces.clear();
		state.outlineColor = 0;
		if (state instanceof LivingEntityRenderState living) {
			living.bodyRot = 180 + yaw;
			living.yRot = 0;
			living.xRot = 0;
			living.boundingBoxWidth = living.boundingBoxWidth / living.scale;
			living.boundingBoxHeight = living.boundingBoxHeight / living.scale;
			living.scale = 1;
		}
		float unit = menu.unit();
		int x0 = Math.round(x * unit);
		int y0 = Math.round((y + 10) * unit);
		int x1 = Math.round((x + w) * unit);
		int y1 = Math.round((y + h - 24) * unit);
		int size = Math.round((y1 - y0) * 0.36f);
		Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(-8));
		Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
		Vector3f translation = new Vector3f(0, state.boundingBoxHeight / 2 + 0.25f, 0);
		g.pose().pushMatrix();
		g.pose().identity();
		g.entity(state, size, translation, rotation, tilt, x0, y0, x1, y1);
		g.pose().popMatrix();
	}

	/** A small round button; {@code icon} null draws pause bars. Shows its name while hovered. */
	private void previewButton(GuiGraphicsExtractor g, float bx, float by, float fade, Icon icon, String name) {
		float mx = lastMouseX;
		float my = lastMouseY;
		boolean over = mx >= bx && mx <= bx + BUTTON && my >= by && my <= by + BUTTON;
		float cx = bx + BUTTON / 2;
		float cy = by + BUTTON / 2;
		Shapes.circle(g, cx, cy, BUTTON / 2, Colors.multiplyAlpha(over ? Colors.withAlpha(Theme.accent(), 0.3f) : 0x55000000, fade),
			Colors.multiplyAlpha(over ? Theme.accent() : 0x40FFFFFF, fade), 1);
		int color = Colors.multiplyAlpha(over ? 0xFFFFFFFF : 0xDDFFFFFF, fade);
		if (icon == null) {
			Shapes.rect(g, cx - 4.5f, cy - 5.5f, 3, 11, 1, color);
			Shapes.rect(g, cx + 1.5f, cy - 5.5f, 3, 11, 1, color);
		} else {
			IconRenderer.draw(g, icon, cx, cy, 13, color, 2f);
		}
		if (over) {
			Fonts.draw(g, Typeface.OUTFIT_MEDIUM, name, cx, by + BUTTON + 12, 10.5f, Theme.text(0.8f, fade), Fonts.Align.CENTER, 0, true);
		}
	}

	private boolean onButton(float mx, float my, float bx) {
		return mx >= bx && mx <= bx + BUTTON && my >= buttonY && my <= buttonY + BUTTON;
	}

	@Override
	public boolean onClick(float mx, float my, int button) {
		if (button == InputConstants.MOUSE_BUTTON_LEFT && onButton(mx, my, pauseX)) {
			var spin = Cosmetics.get().previewSpin;
			spin.set(!spin.isOn());
			dev.mordo.kairo.sound.UiSounds.click();
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT && onButton(mx, my, frontX)) {
			facingFront = true;
			Cosmetics.get().previewSpin.set(false);
			dev.mordo.kairo.sound.UiSounds.click();
			return true;
		}
		if (button == InputConstants.MOUSE_BUTTON_LEFT && mx >= previewX && mx <= previewX + previewW && my >= previewY && my <= previewY + previewH) {
			dragging = true;
			lastDragX = mx;
			return true;
		}
		return false;
	}

	@Override
	public boolean onDrag(float mx, float my, int button) {
		if (!dragging) {
			return false;
		}
		yaw -= (mx - lastDragX) * 1.2f;
		facingFront = false;
		lastDragX = mx;
		return true;
	}

	@Override
	public boolean onRelease(float mx, float my, int button) {
		if (!dragging) {
			return false;
		}
		dragging = false;
		return true;
	}

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		return categories.get(selected).list().mouseScrolled(mx, my, amount);
	}
}
