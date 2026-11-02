package dev.mordo.kairo.render.background;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.ClientSettings;
import dev.mordo.kairo.core.Edition;
import dev.mordo.kairo.core.MainMenuSettings;
import dev.mordo.kairo.core.settings.ColorSetting;
import net.minecraft.util.Util;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The colours of the main menu background ("wallpaper colours"). A custom image, a GIF (its first frame) or a custom
 * panorama is read once in the background and turned into 4 to 6 swatches; Minecraft's panorama and Kairo Void's
 * drawn space scene have built-in palettes. Results are cached per file, so nothing is recomputed every frame.
 *
 * <p>With Visuals → Theme → "Use wallpaper colours" on, the accent follows the chosen swatch whenever the background
 * changes. Picking a swatch sets the accent right away. The picture itself never leaves the player's computer and is
 * never put in a look code.
 */
public final class WallpaperColors {
	/** Minecraft's panorama: sky, grass, water, sand, wood and haze. The first is Kairo's default accent. */
	private static final List<Integer> MINECRAFT = List.of(0xFF7FD8FF, 0xFF8CCB5E, 0xFF5FB3E8, 0xFFE0B26A, 0xFFC99A6B, 0xFFA9B8FF);
	/** Kairo Void's space scene: its violet, the blue and pink nebulae, indigo, teal and the warm planet. */
	private static final List<Integer> VOID_SCENE = List.of(0xFF8B5CF6, 0xFF2EC5FF, 0xFFE061FF, 0xFF6F7CFF, 0xFF3FE0C5, 0xFFFFB86B);
	private static final int SAMPLE = 160;

	/** Swatches, and how bright the picture is (WCAG luminance of its brighter quarter; -1 = unknown). */
	private record Palette(List<Integer> swatches, double brightness) {
	}

	private static final Map<String, Palette> CACHE = new ConcurrentHashMap<>();
	private static String syncedChoice = "";
	private static volatile List<Integer> swatches = List.of();
	private static volatile double brightness = -1;
	private static volatile String swatchesFor = "";
	private static volatile boolean loading;
	private static volatile int generation;
	/** Text on the menu background needs at most this luminance behind it for 4.5:1 (white text). */
	private static final double READABLE = 0.18;

	private WallpaperColors() {
	}

	/** The swatches of the current background, best first (empty while they're being read, or if it failed). */
	public static List<Integer> swatches() {
		sync();
		return swatchesFor.equals(syncedChoice) ? swatches : List.of();
	}

	public static boolean loading() {
		sync();
		return loading;
	}

	/**
	 * How bright the custom background is: the WCAG luminance (0..1) that a quarter of the picture is brighter than.
	 * -1 for Minecraft's panorama, the Void scene, or while it's being read.
	 */
	public static double brightness() {
		sync();
		return swatchesFor.equals(syncedChoice) ? brightness : -1;
	}

	/**
	 * The least darkening a bright image or GIF background needs so white text on it keeps 4.5:1 (0 for dark pictures).
	 * Used on top of the player's own "Darken" setting, never instead of it.
	 */
	public static float readableDim() {
		double b = brightness();
		// Only images and GIFs are drawn (and darkened) by Kairo; a custom panorama is Minecraft's own spinning cube.
		if (b <= READABLE || !syncedChoice.startsWith("image:")) {
			return 0;
		}
		// Darkening scales the sRGB values; luminance follows roughly with a power of 2.2.
		return (float) (1 - Math.pow(READABLE / b, 1 / 2.2));
	}

	/** True if the background is the player's own picture or panorama (not Minecraft's or the Void scene). */
	public static boolean isCustom() {
		return !MainMenuSettings.INSTANCE.background.get().equals(MainMenuSettings.VANILLA_BACKGROUND);
	}

	/** The swatch the accent was last taken from, or -1. */
	public static int pickedIndex() {
		ClientSettings s = ClientSettings.INSTANCE;
		List<Integer> list = swatches();
		int accent = s.accent.argb() | 0xFF000000;
		for (int i = 0; i < list.size(); i++) {
			if ((list.get(i) | 0xFF000000) == accent) {
				return i;
			}
		}
		return -1;
	}

	/** Uses swatch {@code index} as the theme colour. */
	public static void pick(int index) {
		List<Integer> list = swatches();
		if (index < 0 || index >= list.size()) {
			return;
		}
		ClientSettings s = ClientSettings.INSTANCE;
		s.accent.setMode(ColorSetting.Mode.STATIC);
		s.accent.set(list.get(index));
		s.wallpaperSwatch.set((double) index);
		s.wallpaperSource.set(syncedChoice);
	}

	private static boolean started;
	private static boolean wasOn;

	/** Client tick: follows background changes while "Use wallpaper colours" is on. */
	public static void tick() {
		ClientSettings s = ClientSettings.INSTANCE;
		boolean on = s.wallpaperColors.isOn();
		boolean switchedOn = started && on && !wasOn;
		started = true;
		wasOn = on;
		if (!on) {
			return;
		}
		if (switchedOn) {
			followNow();
			return;
		}
		List<Integer> list = swatches();
		// Only once per background: a colour picked by hand stays until the background changes.
		if (!list.isEmpty() && !syncedChoice.equals(s.wallpaperSource.get())) {
			pick(Math.min(s.wallpaperSwatch.intValue(), list.size() - 1));
		}
	}

	/** "Use wallpaper colours" was just switched on: take the colour now. */
	private static void followNow() {
		List<Integer> list = swatches();
		ClientSettings s = ClientSettings.INSTANCE;
		if (!list.isEmpty()) {
			pick(Math.min(s.wallpaperSwatch.intValue(), list.size() - 1));
		} else {
			s.wallpaperSource.set(""); // the tick applies it once the colours are read
		}
	}

	// ---- reading ----

	private static void sync() {
		String choice = MainMenuSettings.INSTANCE.background.get();
		if (choice.equals(syncedChoice)) {
			return;
		}
		syncedChoice = choice;
		int gen = ++generation;
		if (choice.equals(MainMenuSettings.VANILLA_BACKGROUND)) {
			publish(gen, choice, new Palette(Edition.isVoid() ? VOID_SCENE : MINECRAFT, -1));
			return;
		}
		String key = cacheKey(choice);
		Palette cached = CACHE.get(key);
		if (cached != null) {
			publish(gen, choice, cached);
			return;
		}
		loading = true;
		Util.ioPool().execute(() -> {
			Palette result = new Palette(List.of(), -1);
			try {
				int[] pixels = read(choice);
				if (pixels != null) {
					result = new Palette(List.copyOf(PaletteExtractor.extract(pixels)), brightness(pixels));
				}
			} catch (Exception e) {
				Kairo.LOGGER.warn("Couldn't read colours from background {}: {}", choice, e.toString());
			}
			CACHE.put(key, result);
			publish(gen, choice, result);
		});
	}

	private static void publish(int gen, String choice, Palette palette) {
		if (gen != generation) {
			return; // a newer choice is already on its way
		}
		swatches = palette.swatches();
		brightness = palette.brightness();
		swatchesFor = choice;
		loading = false;
	}

	/** The luminance a quarter of the opaque pixels are brighter than. */
	static double brightness(int[] argb) {
		double[] lum = new double[argb.length];
		int n = 0;
		for (int c : argb) {
			if ((c >>> 24) >= 128) {
				lum[n++] = dev.mordo.kairo.render.ColorScience.luminance(c);
			}
		}
		if (n == 0) {
			return -1;
		}
		java.util.Arrays.sort(lum, 0, n);
		return lum[Math.min(n - 1, (int) (n * 0.75))];
	}

	/** File and its last change, so editing a picture in place reads it again. */
	private static String cacheKey(String choice) {
		try {
			Path p = path(choice);
			if (p != null) {
				Path file = Files.isDirectory(p) ? PanoramaFolders.face(p, 0) : p;
				return choice + "@" + Files.getLastModifiedTime(file).toMillis();
			}
		} catch (Exception ignored) {
			// missing file: the read fails and caches an empty list for it
		}
		return choice;
	}

	private static Path path(String choice) {
		if (choice.startsWith("image:")) {
			return MenuBackground.folder().resolve(choice.substring(6));
		}
		if (choice.startsWith("cube:")) {
			return MenuBackground.folder().resolve(choice.substring(5));
		}
		return null;
	}

	/** ARGB pixels of a small copy of the picture (a GIF's first frame; a panorama's four sides). */
	private static int[] read(String choice) throws Exception {
		Path p = path(choice);
		if (p == null) {
			return null;
		}
		if (choice.startsWith("cube:")) {
			List<int[]> faces = new ArrayList<>();
			int length = 0;
			for (int i = 0; i < 4; i++) {
				int[] face = sample(ImageIO.read(PanoramaFolders.face(p, i).toFile()));
				if (face != null) {
					faces.add(face);
					length += face.length;
				}
			}
			int[] all = new int[length];
			int at = 0;
			for (int[] face : faces) {
				System.arraycopy(face, 0, all, at, face.length);
				at += face.length;
			}
			return all;
		}
		File file = p.toFile();
		return sample(ImageIO.read(file)); // ImageIO gives a GIF's first frame
	}

	/** Scales the picture to at most 160 px across and returns its ARGB pixels. */
	static int[] sample(BufferedImage image) {
		if (image == null) {
			return null;
		}
		float fit = Math.min(1, SAMPLE / (float) Math.max(image.getWidth(), image.getHeight()));
		int w = Math.max(1, Math.round(image.getWidth() * fit));
		int h = Math.max(1, Math.round(image.getHeight() * fit));
		BufferedImage small = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = small.createGraphics();
		g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
		g.drawImage(image, 0, 0, w, h, null);
		g.dispose();
		return small.getRGB(0, 0, w, h, null, 0, w);
	}
}
