package dev.mordo.kairo.mods;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.mods.totem.TotemPack;
import dev.mordo.kairo.render.Colors;
import dev.mordo.kairo.render.Shapes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Inventories and containers in the glass look. Minecraft's container textures are recoloured into the client's
 * own resource pack (the light grey panel becomes see-through, slots become dark glass, outlines soften) and a
 * frosted panel is drawn behind them, so every arrow, flame and icon in those textures stays where it was.
 */
public final class InventoryGlass extends Module {
	public final ColorSetting tint = add(new ColorSetting("tint", "Panel tint", 0xB00C1018)
		.description("Colour and opacity of the glass behind inventories"));
	public final BooleanSetting frosted = add(new BooleanSetting("frosted", "Frosted", true)
		.description("Blur the world behind the panel"));
	public final SliderSetting corner = add(new SliderSetting("corner", "Corner radius", 6, 0, 12, 0.5).unit("px"));
	public final SliderSetting slotShade = add(new SliderSetting("slot_shade", "Slot darkness", 0.35, 0.1, 0.8, 0.05).percent()
		.description("How dark the item slots are"));
	public final BooleanSetting accentBorder = add(new BooleanSetting("accent_border", "Accent border", false)
		.description("Outline the panel in the client accent colour"));

	private static final Path PACK_ROOT = TotemPack.root();
	private static final String CONTAINER_DIR = "assets/minecraft/textures/gui/container";
	private static final String TABS_DIR = "assets/minecraft/textures/gui/sprites/container/creative_inventory";
	private long rebuildAt;
	/** What the textures on disk were built for, so a reload only happens when something really changed. */
	private volatile String writtenFor = "";

	public InventoryGlass() {
		super("inventory", "Inventory", "Glass inventories and containers", Category.VISUAL, Icon.BOX, true);
		enabled.onChange(v -> scheduleRebuild());
	}

	@Override
	public void init() {
		// Before the first resource load: no extra reload needed at startup.
		try {
			writeTextures();
		} catch (IOException | RuntimeException e) {
			Kairo.LOGGER.warn("Couldn't prepare the glass inventory textures", e);
		}
	}

	/** True while a container screen is open and the glass look is on. */
	public boolean activeFor(Object screen) {
		return isEnabled() && screen instanceof AbstractContainerScreen<?>;
	}

	public boolean active() {
		return activeFor(Minecraft.getInstance().gui.screen());
	}

	/** The frosted panel behind a container's (recoloured) texture. */
	public void drawPanel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
		float r = corner.floatValue();
		int border = accentBorder.isOn() ? Colors.withAlpha(Theme.accent(), 0.75f) : 0x33FFFFFF;
		Shapes.shadow(g, x, y + 4, w, h, r, 16, 0x40000000);
		if (frosted.isOn()) {
			Shapes.glassRect(g, x, y, w, h, r, tint.argb(), border, 1, 1);
		} else {
			Shapes.rect(g, x, y, w, h, r, Colors.opaqueTint(tint.argb()), border, 1);
		}
	}

	/** A creative tab (26x32 at x, y): glass, the selected one brighter and joined to the panel. */
	public void drawTab(GuiGraphicsExtractor g, int x, int y, int w, int h, boolean top, boolean selected) {
		float r = Math.min(corner.floatValue(), 6);
		int accent = Theme.accent();
		float tx = x + 1;
		float tw = w - 2;
		// Top tabs hang 4 px into the panel, bottom tabs start 4 px inside it; the selected one reaches further in.
		float ty = top ? y + (selected ? 0 : 3) : y;
		float th = top ? (selected ? h : h - 7) : (selected ? h : h - 3);
		if (!top && !selected) {
			ty = y + 4;
		}
		int tintColor = selected ? Colors.lerp(tint.argb(), 0xB0182234, 0.5f) : Colors.multiplyAlpha(tint.argb(), 0.85f);
		int border = selected ? Colors.withAlpha(accent, 0.7f) : 0x26FFFFFF;
		if (frosted.isOn()) {
			Shapes.glassRect(g, tx, ty, tw, th, r, tintColor, border, 1, 1);
		} else {
			Shapes.rect(g, tx, ty, tw, th, r, Colors.opaqueTint(tintColor), border, 1);
		}
		if (selected) {
			float lineY = top ? ty + 1.5f : ty + th - 2.5f;
			Shapes.rect(g, tx + 6, lineY, tw - 12, 1, 0.5f, Colors.withAlpha(accent, 0.9f));
		}
	}

	/** Dark label text (vanilla's 0x404040 on light grey) turns light on glass. */
	public static int labelColor(int color) {
		return color == 0xFF404040 ? 0xFFE6ECF2 : color;
	}

	// ---- textures ----

	/** Runs every client tick; rebuilds and reloads a moment after a setting changes. */
	public void tickRebuild() {
		if (rebuildAt != 0 && System.currentTimeMillis() >= rebuildAt) {
			rebuildAt = 0;
			Minecraft mc = Minecraft.getInstance();
			if (writtenFor.equals(key())) {
				return;
			}
			Util.backgroundExecutor().execute(() -> {
				try {
					writeTextures();
					mc.execute(() -> {
						Toasts.show(isEnabled() ? "Applying glass inventories…" : "Restoring Minecraft's inventories…", Theme.accent());
						mc.reloadResourcePacks();
					});
				} catch (IOException | RuntimeException e) {
					Kairo.LOGGER.warn("Couldn't build the glass inventory textures", e);
				}
			});
		}
	}

	private void scheduleRebuild() {
		rebuildAt = System.currentTimeMillis() + 400;
	}

	private String key() {
		return isEnabled() ? "on:v3" : "off";
	}

	private synchronized void writeTextures() throws IOException {
		writtenFor = key();
		deleteTree(PACK_ROOT.resolve(CONTAINER_DIR));
		deleteTree(PACK_ROOT.resolve(TABS_DIR));
		if (!isEnabled()) {
			return;
		}
		// Exact vanilla GUI greys -> glass. Anything else (arrows, icons, flames) stays as it is.
		// The panel's own bevel (white top-left, grey bottom-right, black rim) goes completely: the glass panel
		// behind has its own rounded edge, and square bevel lines would stick out of rounded corners. In slots the
		// white edge becomes a see-through gap, so neighbouring slots stay apart.
		// Slots themselves are cut out of the textures (see clearSlots) and drawn by the client as rounded cells
		// (drawSlots), so every slot looks the same: inventory, armour, crafting, results. What's left in these
		// greys are decorations like arrows: they turn into soft white shapes on the glass.
		Map<Integer, Integer> panel = Map.of(
			0xFFC6C6C6, 0x00000000,
			0xFFFFFFFF, 0x00000000,
			0xFF555555, 0x00000000,
			0xFF000000, 0x00000000,
			0xFF8B8B8B, 0x38FFFFFF,
			0xFF373737, 0x00000000);
		// Creative tabs sit outside the panel, so they keep a dark body instead of turning see-through.
		Map<Integer, Integer> tabs = Map.of(
			0xFFC6C6C6, 0xC8E6ECF2,
			0xFF8B8B8B, 0x50FFFFFF,
			0xFFFFFFFF, 0xF0FFFFFF,
			0xFF555555, 0x40000000,
			0xFF000000, 0x00000000);
		withVanillaFolder(CONTAINER_DIR, "inventory.png", (rel, file) -> {
			if (!rel.endsWith("gamemode_switcher.png")) {
				recolor(file, PACK_ROOT.resolve(CONTAINER_DIR).resolve(rel), panel, true);
			}
		});
		// Tabs themselves are drawn as glass by CreativeTabsMixin; only the scroll handle needs a new colour.
		withVanillaFolder(TABS_DIR, "tab_top_selected_1.png", (rel, file) -> {
			if (rel.startsWith("scroller")) {
				recolor(file, PACK_ROOT.resolve(TABS_DIR).resolve(rel), tabs, false);
				copyMeta(file, PACK_ROOT.resolve(TABS_DIR).resolve(rel));
			}
		});
	}

	private interface FileVisitor {
		void visit(String relative, Path file) throws IOException;
	}

	/** Visits every PNG under a folder of Minecraft's own assets (inside the game jar). */
	private static void withVanillaFolder(String folder, String markerFile, FileVisitor visitor) throws IOException {
		URL marker = Minecraft.class.getResource("/" + folder + "/" + markerFile);
		if (marker == null) {
			throw new IOException("Minecraft assets not found: " + folder);
		}
		URI uri;
		try {
			uri = marker.toURI();
		} catch (URISyntaxException e) {
			throw new IOException(e);
		}
		FileSystem opened = null;
		try {
			Path dir;
			if ("jar".equals(uri.getScheme())) {
				FileSystem fs;
				try {
					fs = FileSystems.newFileSystem(uri, Map.of());
					opened = fs;
				} catch (FileSystemAlreadyExistsException e) {
					fs = FileSystems.getFileSystem(uri);
				}
				dir = fs.getPath("/" + folder);
			} else {
				dir = Path.of(uri).getParent();
			}
			List<Path> files;
			try (Stream<Path> walk = Files.walk(dir)) {
				files = walk.filter(p -> p.toString().endsWith(".png")).sorted(Comparator.comparing(Path::toString)).toList();
			}
			for (Path file : files) {
				visitor.visit(dir.relativize(file).toString().replace('\\', '/'), file);
			}
		} finally {
			if (opened != null) {
				opened.close();
			}
		}
	}

	private static void recolor(Path source, Path target, Map<Integer, Integer> map, boolean clearSlots) throws IOException {
		BufferedImage image;
		try (InputStream in = Files.newInputStream(source)) {
			image = ImageIO.read(in);
		}
		if (image == null) {
			return;
		}
		BufferedImage out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		// Greyscale PNGs (the creative tabs) would come back gamma-shifted from getRGB; read their raw values.
		boolean gray = image.getColorModel().getColorSpace().getType() == java.awt.color.ColorSpace.TYPE_GRAY;
		java.awt.image.Raster raster = image.getRaster();
		int bands = raster.getNumBands();
		int depthShift = Math.max(0, image.getColorModel().getComponentSize(0) - 8);
		int w = image.getWidth();
		int h = image.getHeight();
		int[][] px = new int[h][w];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				if (gray) {
					int v = raster.getSample(x, y, 0) >> depthShift;
					int a = bands > 1 ? raster.getSample(x, y, 1) >> depthShift : 255;
					px[y][x] = a << 24 | v << 16 | v << 8 | v;
				} else {
					px[y][x] = image.getRGB(x, y);
				}
			}
		}
		boolean[][] cleared = clearSlots ? findSlots(px, w, h) : new boolean[h][w];
		for (int y = 0; y < h; y++) {
			for (int x = 0; x < w; x++) {
				int argb = px[y][x];
				Integer mapped = map.get(argb);
				out.setRGB(x, y, cleared[y][x] ? 0 : mapped != null ? mapped : argb);
			}
		}
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		ImageIO.write(out, "png", bytes);
		Files.createDirectories(target.getParent());
		Files.write(target, bytes.toByteArray());
	}

	private static final int SLOT_EDGE = 0xFF373737;
	private static final int SLOT_FILL = 0xFF8B8B8B;
	private static final int SLOT_LIGHT = 0xFFFFFFFF;

	/**
	 * Finds Minecraft's slot boxes in a container texture: a square with a dark (0x373737) top and left edge, a
	 * grey (0x8B8B8B) inside and a white bottom-right corner. Normal slots are 18x18, result slots 26x26.
	 */
	private static boolean[][] findSlots(int[][] px, int w, int h) {
		boolean[][] cleared = new boolean[h][w];
		for (int y = 0; y < h - 8; y++) {
			for (int x = 0; x < w - 8; x++) {
				if (px[y][x] != SLOT_EDGE || cleared[y][x]) {
					continue;
				}
				if ((x > 0 && px[y][x - 1] == SLOT_EDGE) || (y > 0 && px[y - 1][x] == SLOT_EDGE)) {
					continue; // not a top-left corner
				}
				int right = 0;
				while (x + right + 1 < w && px[y][x + right + 1] == SLOT_EDGE) {
					right++;
				}
				int down = 0;
				while (y + down + 1 < h && px[y + down + 1][x] == SLOT_EDGE) {
					down++;
				}
				int n = right + 1; // edge length = box size - 1
				if (right != down || n < 8 || x + n >= w || y + n >= h) {
					continue;
				}
				if (px[y + 1][x + 1] != SLOT_FILL || px[y + n][x + n] != SLOT_LIGHT) {
					continue;
				}
				for (int yy = y; yy <= y + n; yy++) {
					for (int xx = x; xx <= x + n; xx++) {
						cleared[yy][xx] = true;
					}
				}
			}
		}
		return cleared;
	}

	/** The client's slot cells, under the items (called in the container's own coordinates). */
	public void drawSlots(GuiGraphicsExtractor g, java.util.List<net.minecraft.world.inventory.Slot> slots) {
		float r = Math.min(corner.floatValue(), 4);
		int fill = Colors.argb(Math.min(0.75f, slotShade.floatValue() + 0.05f), 0x05080D);
		int border = 0x1FFFFFFF;
		int accent = Theme.accent();
		for (net.minecraft.world.inventory.Slot slot : slots) {
			if (!slot.isActive()) {
				continue;
			}
			if (slot instanceof net.minecraft.world.inventory.ResultSlot) {
				Shapes.rect(g, slot.x - 5, slot.y - 5, 26, 26, r + 1, fill, Colors.withAlpha(accent, 0.55f), 1);
			} else {
				Shapes.rect(g, slot.x - 1, slot.y - 1, 18, 18, r, fill, border, 0.75f);
			}
		}
	}

	/** Sprite metadata (nine-slice scaling, animation) has to come from the same pack as the image. */
	private static void copyMeta(Path png, Path target) throws IOException {
		Path meta = png.resolveSibling(png.getFileName() + ".mcmeta");
		if (Files.isRegularFile(meta)) {
			Files.write(target.resolveSibling(target.getFileName() + ".mcmeta"), Files.readAllBytes(meta));
		}
	}

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		List<Path> all = new ArrayList<>();
		try (Stream<Path> walk = Files.walk(dir)) {
			walk.forEach(all::add);
		}
		all.sort(Comparator.reverseOrder());
		for (Path p : all) {
			Files.deleteIfExists(p);
		}
	}

}
