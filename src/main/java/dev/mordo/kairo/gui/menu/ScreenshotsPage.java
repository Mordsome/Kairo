package dev.mordo.kairo.gui.menu;

import com.mojang.blaze3d.platform.NativeImage;
import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.gui.FilePicker;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.IconRenderer;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.widgets.Button;
import dev.mordo.kairo.gui.widgets.NavItem;
import dev.mordo.kairo.gui.widgets.ScrollState;
import dev.mordo.kairo.mods.Screenshots;
import dev.mordo.kairo.render.Animation;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Easing;
import dev.mordo.kairo.render.Shapes;
import dev.mordo.kairo.render.font.Fonts;
import dev.mordo.kairo.render.font.Typeface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Screenshots: every screenshot in a grid, newest first, with a Favourites tab. Click one to see it big, with Copy,
 * Open, Folder, Delete and a star; the arrows (or the mouse wheel) step through them. Favourites are kept by file name
 * in the settings, so they stay starred across launches.
 */
public final class ScreenshotsPage extends PanelPage {
	private static final int THUMB_W = 320;
	private static final int THUMB_H = 180;
	/** One loader thread: pictures appear one after another without slowing the game. */
	private static final ExecutorService LOADER = Executors.newSingleThreadExecutor(r -> {
		Thread thread = new Thread(r, "Kairo screenshot loader");
		thread.setDaemon(true);
		return thread;
	});
	private static final Map<String, Identifier> THUMBS = new HashMap<>();
	private static final Set<String> LOADING = new LinkedHashSet<>();

	private final List<NavItem> nav = new ArrayList<>();
	private final ScrollState scroll = new ScrollState();
	private boolean favouritesOnly;
	private List<File> files = List.of();
	private final List<File> shown = new ArrayList<>();
	private final Map<File, float[]> tiles = new HashMap<>();
	private float gridTop;
	private float gridBottom;

	/** The picture open big, or null for the grid. */
	private @Nullable File open;
	private @Nullable Identifier full;
	private @Nullable DynamicTexture fullTexture;
	private int fullW;
	private int fullH;
	private final Animation viewer = new Animation(0);
	private final Button openFolder;
	private final Button back;
	private final Button copy;
	private final Button openFile;
	private final Button delete;
	private final Button previous;
	private final Button next;
	private final Button star;

	ScreenshotsPage(MenuScreen menu) {
		super(menu);
		nav.add(new NavItem("All", Icon.IMAGE, () -> Integer.toString(files.size()), () -> !favouritesOnly, () -> select(false)));
		nav.add(new NavItem("Favourites", Icon.HEART, () -> Long.toString(files.stream().filter(f -> favourite(f)).count()),
			() -> favouritesOnly, () -> select(true)));
		openFolder = new Button("Open folder", Icon.FOLDER, Button.Style.SUBTLE, () -> FilePicker.openFolder(folder().toPath())).textSize(11.5f);
		back = new Button("", Icon.ARROW_LEFT, Button.Style.GHOST, this::closeViewer);
		copy = new Button("Copy", Icon.COPY, Button.Style.SUBTLE, () -> act(Screenshots::copy)).textSize(11.5f);
		openFile = new Button("Open", Icon.IMAGE, Button.Style.SUBTLE, () -> act(Screenshots::openFile)).textSize(11.5f);
		delete = new Button("Delete", Icon.TRASH, Button.Style.SUBTLE, () -> {
			File file = open;
			if (file != null) {
				Screenshots.delete(file);
				step(1);
				if (open == file) {
					closeViewer();
				}
				reload();
			}
		}).textSize(11.5f);
		previous = new Button("", Icon.ARROW_LEFT, Button.Style.GHOST, () -> step(-1));
		next = new Button("", Icon.ARROW_RIGHT, Button.Style.GHOST, () -> step(1));
		star = new Button("Favourite", Icon.HEART, Button.Style.SUBTLE, () -> {
			if (open != null) {
				toggleFavourite(open);
			}
		}).textSize(11.5f);
	}

	private static File folder() {
		return Minecraft.getInstance().gameDirectory.toPath().resolve("screenshots").toFile();
	}

	// ---- favourites (file names in the settings) ----

	private static Set<String> favourites() {
		Set<String> names = new LinkedHashSet<>(Arrays.asList(ClientSettings.INSTANCE.favouriteScreenshots.get().split("\\|")));
		names.remove("");
		return names;
	}

	private static boolean favourite(File file) {
		return favourites().contains(file.getName());
	}

	private void toggleFavourite(File file) {
		Set<String> names = favourites();
		if (!names.remove(file.getName())) {
			names.add(file.getName());
		}
		// Only names of pictures that still exist are kept, so the list never grows without end.
		Set<String> existing = new LinkedHashSet<>();
		for (File f : files) {
			existing.add(f.getName());
		}
		names.retainAll(existing);
		ClientSettings.INSTANCE.favouriteScreenshots.set(String.join("|", names));
		dev.mordo.kairo.sound.UiSounds.click();
		filter();
	}

	// ---- the list ----

	@Override
	public void show() {
		super.show();
		reload();
	}

	private void reload() {
		File[] found = folder().listFiles((dir, name) -> name.toLowerCase(java.util.Locale.ROOT).endsWith(".png"));
		List<File> list = found == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(found));
		list.sort(Comparator.comparingLong(File::lastModified).reversed());
		files = list;
		filter();
	}

	private void select(boolean favouritesTab) {
		favouritesOnly = favouritesTab;
		closeViewer();
		filter();
		scroll.reset();
	}

	private void filter() {
		shown.clear();
		for (File file : files) {
			if (!favouritesOnly || favourite(file)) {
				shown.add(file);
			}
		}
	}

	@Override
	protected String navLabel() {
		return "SCREENSHOTS";
	}

	@Override
	protected List<NavItem> navItems() {
		return nav;
	}

	// ---- pictures ----

	/** A small copy of the picture for the grid, loaded in the background (null until it's ready). */
	private static @Nullable Identifier thumbnail(File file) {
		String key = file.getAbsolutePath() + "|" + file.lastModified();
		Identifier ready = THUMBS.get(key);
		if (ready != null || !LOADING.add(key)) {
			return ready;
		}
		LOADER.execute(() -> {
			int[] pixels = read(file, THUMB_W, THUMB_H, true);
			Minecraft.getInstance().execute(() -> {
				LOADING.remove(key);
				if (pixels != null) {
					NativeImage image = new NativeImage(THUMB_W, THUMB_H, false);
					org.lwjgl.system.MemoryUtil.memIntBuffer(image.getPointer(), THUMB_W * THUMB_H).put(pixels);
					Identifier id = ClientInfo.id("screenshot_thumb/" + Integer.toHexString(key.hashCode()));
					Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "Kairo screenshot thumbnail", image));
					THUMBS.put(key, id);
				}
			});
		});
		return null;
	}

	/** Reads a picture scaled to w x h (cropped to fill when {@code cover}), as the texture's pixel order (ABGR). */
	private static int @Nullable [] read(File file, int w, int h, boolean cover) {
		try {
			BufferedImage source = ImageIO.read(file);
			if (source == null) {
				return null;
			}
			BufferedImage scaled = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = scaled.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			float sx = source.getWidth();
			float sy = source.getHeight();
			float want = w / (float) h;
			int cw = source.getWidth();
			int ch = source.getHeight();
			if (cover) {
				if (sx / sy > want) {
					cw = Math.round(sy * want);
				} else {
					ch = Math.round(sx / want);
				}
			}
			int ox = (source.getWidth() - cw) / 2;
			int oy = (source.getHeight() - ch) / 2;
			g.drawImage(source, 0, 0, w, h, ox, oy, ox + cw, oy + ch, null);
			g.dispose();
			int[] argb = scaled.getRGB(0, 0, w, h, null, 0, w);
			for (int i = 0; i < argb.length; i++) {
				int c = argb[i];
				argb[i] = (c & 0xFF00FF00) | ((c & 0xFF) << 16) | ((c >> 16) & 0xFF);
			}
			return argb;
		} catch (Exception e) {
			Kairo.LOGGER.debug("Couldn't read {}", file, e);
			return null;
		}
	}

	// ---- the big view ----

	private void openViewer(File file) {
		open = file;
		viewer.animateTo(1, 260, Easing.UI);
		loadFull(file);
	}

	private void closeViewer() {
		if (viewer.target() > 0) {
			viewer.animateTo(0, 200, Easing.UI);
		}
	}

	private void step(int by) {
		if (open == null || shown.isEmpty()) {
			return;
		}
		int at = shown.indexOf(open);
		int to = Math.floorMod((at < 0 ? 0 : at) + by, shown.size());
		File file = shown.get(to);
		if (file != open) {
			open = file;
			loadFull(file);
		}
	}

	private void loadFull(File file) {
		BufferedImage size;
		int w = 1600;
		int h = 900;
		try {
			javax.imageio.ImageReader reader = null;
			try (var in = ImageIO.createImageInputStream(file)) {
				var readers = ImageIO.getImageReaders(in);
				if (readers.hasNext()) {
					reader = readers.next();
					reader.setInput(in);
					float aspect = reader.getWidth(0) / (float) reader.getHeight(0);
					w = Math.min(1600, reader.getWidth(0));
					h = Math.max(1, Math.round(w / aspect));
					reader.dispose();
				}
			}
		} catch (Exception e) {
			Kairo.LOGGER.debug("Couldn't read the size of {}", file, e);
		}
		int fw = w;
		int fh = h;
		LOADER.execute(() -> {
			int[] pixels = read(file, fw, fh, false);
			Minecraft.getInstance().execute(() -> {
				if (open != file || pixels == null) {
					return;
				}
				Identifier id = ClientInfo.id("screenshot_full");
				if (fullTexture != null) {
					Minecraft.getInstance().getTextureManager().release(id);
				}
				NativeImage image = new NativeImage(fw, fh, false);
				org.lwjgl.system.MemoryUtil.memIntBuffer(image.getPointer(), fw * fh).put(pixels);
				fullTexture = new DynamicTexture(() -> "Kairo screenshot", image);
				Minecraft.getInstance().getTextureManager().register(id, fullTexture);
				full = id;
				fullW = fw;
				fullH = fh;
			});
		});
		full = null;
	}

	private void act(java.util.function.Consumer<File> action) {
		if (open != null) {
			action.accept(open);
		}
	}

	// ---- drawing ----

	@Override
	protected void renderContent(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		float v = viewer.value();
		if (v < 0.999f) {
			renderGrid(g, mx, my, fade * (1 - v), live && v < 0.05f);
		}
		if (v > 0.001f && open != null) {
			renderViewer(g, mx, my, fade * v, live && v > 0.95f);
		}
	}

	private void renderGrid(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		Fonts.draw(g, Typeface.OUTFIT_BOLD, "Screenshots", contentX, py + 44, 24, Theme.text(1, fade));
		Fonts.draw(g, Typeface.OUTFIT_REGULAR, favouritesOnly ? "Your favourites. Click a picture, then the heart, to add more"
			: "Newest first. Click one to see it big", contentX, py + 66, 11.5f, Theme.text(0.55f, fade));
		float fw = openFolder.preferredWidth();
		openFolder.bounds(contentX + contentW - fw, py + 30, fw, 34);
		put(openFolder, g, mx, my, fade, live);
		gridTop = py + 96;
		gridBottom = py + ph - 20;
		tiles.clear();
		if (shown.isEmpty()) {
			Fonts.drawCentered(g, Typeface.OUTFIT_MEDIUM, favouritesOnly ? "No favourites yet" : "No screenshots yet (F2 takes one)",
				contentX + contentW / 2, gridTop + 60, 13, Theme.text(0.5f, fade));
			return;
		}
		int columns = Math.max(2, (int) ((contentW + 14) / (250 + 14)));
		float gap = 14;
		float tw = (contentW - gap * (columns - 1)) / columns;
		float th = tw * 9 / 16;
		int rows = (shown.size() + columns - 1) / columns;
		scroll.setSizes(rows * (th + gap) - gap + 4, gridBottom - gridTop);
		float offset = scroll.value();
		Set<String> stars = favourites();
		g.enableScissor((int) Math.floor(contentX - 4), (int) Math.floor(gridTop - 4), (int) Math.ceil(contentX + contentW + 4), (int) Math.ceil(gridBottom));
		try {
			for (int i = 0; i < shown.size(); i++) {
				File file = shown.get(i);
				float x = contentX + (i % columns) * (tw + gap);
				float y = gridTop + (i / columns) * (th + gap) - offset;
				if (y + th < gridTop || y > gridBottom) {
					continue;
				}
				tiles.put(file, new float[]{x, y, tw, th});
				boolean hover = live && mx >= x && mx <= x + tw && my >= y && my <= y + th && my >= gridTop && my <= gridBottom;
				Identifier thumb = thumbnail(file);
				Shapes.rect(g, x, y, tw, th, 10, Colors.multiplyAlpha(0x40000000, fade));
				if (thumb != null) {
					g.blit(thumb, Math.round(x), Math.round(y), Math.round(x + tw), Math.round(y + th), 0, 1, 0, 1);
				}
				Shapes.outline(g, x, y, tw, th, 10, hover ? Colors.multiplyAlpha(Theme.accent(), fade) : Colors.multiplyAlpha(0x26FFFFFF, fade), hover ? 2 : 1);
				if (stars.contains(file.getName())) {
					Shapes.circle(g, x + tw - 16, y + 16, 10, Colors.multiplyAlpha(0xA0000000, fade));
					IconRenderer.draw(g, Icon.HEART, x + tw - 16, y + 16.5f, 11, Colors.multiplyAlpha(Theme.accent() | 0xFF000000, fade), 2f);
				}
			}
		} finally {
			g.disableScissor();
		}
		scroll.drawBar(g, contentX + contentW + 14, gridTop, fade);
	}

	private void renderViewer(GuiGraphicsExtractor g, float mx, float my, float fade, boolean live) {
		File file = open;
		float top = py + 26;
		back.bounds(contentX - 6, top, 36, 36);
		put(back, g, mx, my, fade, live);
		Fonts.draw(g, Typeface.OUTFIT_BOLD, Fonts.ellipsize(Typeface.OUTFIT_BOLD, file.getName(), 18, contentW * 0.45f), contentX + 40, top + 12, 18,
			Theme.text(1, fade));
		// Actions, right-aligned.
		boolean starred = favourite(file);
		star.setLabel(starred ? "Favourite ✓" : "Favourite");
		Button[] actions = {star, copy, openFile, delete};
		float ax = contentX + contentW;
		for (int i = actions.length - 1; i >= 0; i--) {
			float w = actions[i].preferredWidth();
			ax -= w;
			actions[i].bounds(ax, top + 1, w, 34);
			put(actions[i], g, mx, my, fade, live);
			ax -= 8;
		}
		// The picture, as big as fits.
		float areaTop = top + 50;
		float areaH = py + ph - 24 - areaTop;
		float areaW = contentW - 90;
		float aspect = fullH > 0 && full != null ? fullW / (float) fullH : 16f / 9;
		float w = Math.min(areaW, areaH * aspect);
		float h = w / aspect;
		float x = contentX + 45 + (areaW - w) / 2;
		float y = areaTop + (areaH - h) / 2;
		Shapes.rect(g, x, y, w, h, 12, Colors.multiplyAlpha(0x60000000, fade));
		Identifier picture = full;
		if (picture == null) {
			// Still loading: the small copy, if it's there.
			picture = thumbnail(file);
		}
		if (picture != null) {
			g.blit(picture, Math.round(x), Math.round(y), Math.round(x + w), Math.round(y + h), 0, 1, 0, 1);
		}
		Shapes.outline(g, x, y, w, h, 12, Colors.multiplyAlpha(0x30FFFFFF, fade), 1);
		previous.bounds(contentX, areaTop + areaH / 2 - 18, 36, 36);
		next.bounds(contentX + contentW - 36, areaTop + areaH / 2 - 18, 36, 36);
		put(previous, g, mx, my, fade, live);
		put(next, g, mx, my, fade, live);
		int at = shown.indexOf(file);
		Fonts.drawCentered(g, Typeface.MONO_MEDIUM, (at + 1) + " / " + shown.size(), contentX + contentW / 2, py + ph - 14, 10, Theme.text(0.45f, fade));
	}

	// ---- input ----

	@Override
	public boolean onClick(float mx, float my, int button) {
		if (viewer.target() > 0) {
			if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
				closeViewer();
				return true;
			}
			return false;
		}
		if (my < gridTop || my > gridBottom) {
			return false;
		}
		for (Map.Entry<File, float[]> tile : tiles.entrySet()) {
			float[] r = tile.getValue();
			if (mx >= r[0] && mx <= r[0] + r[2] && my >= r[1] && my <= r[1] + r[3]) {
				if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT) {
					toggleFavourite(tile.getKey());
				} else {
					openViewer(tile.getKey());
					dev.mordo.kairo.sound.UiSounds.click();
				}
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onScroll(float mx, float my, float amount) {
		if (viewer.target() > 0) {
			step(amount > 0 ? -1 : 1);
			return true;
		}
		if (my >= gridTop && my <= gridBottom) {
			scroll.scroll(amount);
			return true;
		}
		return false;
	}

	@Override
	public boolean onEscape() {
		if (viewer.target() > 0) {
			closeViewer();
			return true;
		}
		return false;
	}
}
