package dev.mordo.kairo.gui;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.api.Addons;
import dev.mordo.kairo.api.MenuLook;
import dev.mordo.kairo.core.ClientInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.util.Util;
import net.minecraft.world.flag.FeatureFlagSet;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.FileSystem;
import java.nio.file.FileSystemAlreadyExistsException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The images a menu look repaints (see {@link MenuLook#repaints}): Kairo reads Minecraft's own GUI images from the
 * game, lets the look repaint them, and keeps the changed ones in a resource pack it writes itself
 * (config/kairo/look-pack), on top of every other pack. Nothing of Minecraft's is shipped in an addon. Empty when
 * the look in use doesn't repaint.
 */
public final class LookPack {
	public static final String ID = ClientInfo.MOD_ID + ":look";
	private static final Path ROOT = ClientInfo.configDir("look-pack");
	/** The folders of Minecraft's assets offered to the look: container backgrounds and every GUI sprite. */
	private static final String[][] FOLDERS = {
		{"assets/minecraft/textures/gui/container", "inventory.png"},
		{"assets/minecraft/textures/gui/sprites", "hud/hotbar.png"},
	};

	private static volatile String writtenFor = "";
	private static volatile boolean building;
	private static int ticks;
	/** What the last build repainted, by path: so a change of colours can go straight onto the GPU, without a reload. */
	private static volatile Map<String, Image> lastBuilt = Map.of();

	private record Image(int width, int height, int[] argb) {
	}

	private LookPack() {
	}

	/** True while the look in use repaints Minecraft's images (Inventory glass steps aside then). */
	public static boolean active() {
		return !key().equals("off");
	}

	private static String key() {
		MenuLook look = Addons.activeLook();
		try {
			return look != null && look.repaints() ? look.id() + ":" + look.repaintKey() : "off";
		} catch (Throwable t) {
			Addons.lookFailed(look, t);
			return "off";
		}
	}

	/** Once the settings are loaded, before the first resource load: no extra reload needed at startup. */
	public static void prepare() {
		try {
			write();
			if (active()) {
				// Inventory glass may have written its textures before the settings said which look is in use.
				dev.mordo.kairo.mods.InventoryGlass glass = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.InventoryGlass.class);
				if (glass != null) {
					glass.refreshFiles();
				}
			}
		} catch (IOException | RuntimeException e) {
			Kairo.LOGGER.warn("Couldn't prepare the menu look's images", e);
		}
	}

	/** Every client tick: rebuilds and reloads when the look (or one of its options) changed. */
	public static void tick() {
		if (++ticks % 4 != 0 || building || writtenFor.equals(key())) {
			return;
		}
		if (Minecraft.getInstance().gui.overlay() != null) {
			return; // a reload is reading the pack right now: rebuild once it's done
		}
		building = true;
		Minecraft mc = Minecraft.getInstance();
		boolean on = active();
		String before = writtenFor;
		Map<String, Image> old = lastBuilt;
		Util.backgroundExecutor().execute(() -> {
			try {
				write();
				dev.mordo.kairo.mods.InventoryGlass glass = dev.mordo.kairo.core.ModuleManager.get(dev.mordo.kairo.mods.InventoryGlass.class);
				if (glass != null) {
					glass.refreshFiles();
				}
				Map<String, Image> now = lastBuilt;
				// Same look, same images, new colours (another colour scheme): straight onto the GPU, no reload.
				boolean live = on && !before.equals("off") && lookOf(before).equals(lookOf(writtenFor)) && old.keySet().equals(now.keySet());
				mc.execute(() -> {
					if (live && upload(mc, now)) {
						return;
					}
					Toasts.show(on ? "Applying the look to Minecraft's menus…" : "Restoring Minecraft's menus…", Theme.accent());
					mc.reloadResourcePacks();
				});
			} catch (IOException | RuntimeException e) {
				Kairo.LOGGER.warn("Couldn't build the menu look's images", e);
			} finally {
				building = false;
			}
		});
	}

	private static String lookOf(String key) {
		int colon = key.indexOf(':');
		return colon < 0 ? key : key.substring(0, colon);
	}

	/**
	 * Writes the repainted images into the textures already on the GPU: container backgrounds are textures of their
	 * own, GUI sprites sit in the GUI atlas. Returns false (and the caller reloads) if anything doesn't fit.
	 */
	private static boolean upload(Minecraft mc, Map<String, Image> images) {
		try {
			net.minecraft.client.renderer.texture.TextureAtlas atlas = mc.getAtlasManager().getAtlasOrThrow(net.minecraft.data.AtlasIds.GUI);
			com.mojang.renderpearl.api.commands.CommandEncoder encoder = com.mojang.blaze3d.systems.RenderSystem.getDevice().createCommandEncoder();
			String spritePrefix = "textures/gui/sprites/";
			for (Map.Entry<String, Image> entry : images.entrySet()) {
				String path = entry.getKey();
				Image image = entry.getValue();
				try (com.mojang.blaze3d.platform.NativeImage pixels = new com.mojang.blaze3d.platform.NativeImage(image.width(), image.height(), false)) {
					for (int y = 0; y < image.height(); y++) {
						for (int x = 0; x < image.width(); x++) {
							pixels.setPixel(x, y, image.argb()[y * image.width() + x]);
						}
					}
					if (path.startsWith(spritePrefix)) {
						net.minecraft.resources.Identifier id = net.minecraft.resources.Identifier.withDefaultNamespace(
							path.substring(spritePrefix.length(), path.length() - ".png".length()));
						net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = atlas.getSprite(id);
						if (!sprite.contents().name().equals(id) || sprite.contents().width() != image.width() || sprite.contents().height() != image.height()) {
							return false; // not in the atlas as it is on disk (animated, say): reload instead
						}
						encoder.writeToTexture(atlas.getTexture(), pixels, 0, 0, sprite.getX(), sprite.getY());
					} else {
						net.minecraft.client.renderer.texture.AbstractTexture texture = mc.getTextureManager().getTexture(
							net.minecraft.resources.Identifier.withDefaultNamespace(path));
						if (texture.getTexture().getWidth(0) != image.width() || texture.getTexture().getHeight(0) != image.height()) {
							return false;
						}
						encoder.writeToTexture(texture.getTexture(), pixels, 0, 0, 0, 0);
					}
				}
			}
			return true;
		} catch (RuntimeException e) {
			Kairo.LOGGER.info("Couldn't change the menu look's images in place, reloading instead", e);
			return false;
		}
	}

	private static synchronized void write() throws IOException {
		String key = key();
		deleteTree(ROOT.resolve("assets"));
		writtenFor = key;
		Map<String, Image> built = new java.util.LinkedHashMap<>();
		lastBuilt = built;
		if (key.equals("off")) {
			return;
		}
		MenuLook look = Addons.activeLook();
		int written = 0;
		for (Vanilla original : vanilla()) {
			int[] argb = original.argb().clone();
			boolean changed;
			try {
				changed = look.repaint(original.path(), argb, original.width(), original.height());
			} catch (Throwable t) {
				Addons.lookFailed(look, t);
				throw new IOException("The menu look failed while repainting " + original.path(), t);
			}
			if (changed) {
				int w = original.width();
				int h = original.height();
				built.put(original.path(), new Image(w, h, argb));
				BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
				out.setRGB(0, 0, w, h, argb, 0, w);
				Path target = ROOT.resolve("assets/minecraft").resolve(original.path());
				Files.createDirectories(target.getParent());
				ImageIO.write(out, "png", target.toFile());
				if (original.meta() != null) {
					Files.write(target.resolveSibling(target.getFileName() + ".mcmeta"), original.meta());
				}
				written++;
			}
		}
		Kairo.LOGGER.info("Menu look '{}' repainted {} of Minecraft's images", look.id(), written);
	}

	/** One of Minecraft's images as it comes in the game, kept after the first read so rebuilding is quick. */
	private record Vanilla(String path, int width, int height, int[] argb, byte[] meta) {
	}

	private static List<Vanilla> vanillaCache;

	private static synchronized List<Vanilla> vanilla() throws IOException {
		if (vanillaCache == null) {
			List<Vanilla> all = new java.util.ArrayList<>();
			for (String[] folder : FOLDERS) {
				withVanillaFolder(folder[0], folder[1], (relative, file) -> {
					BufferedImage image = ImageIO.read(Files.newInputStream(file));
					if (image == null) {
						return;
					}
					int w = image.getWidth();
					int h = image.getHeight();
					Path meta = file.resolveSibling(file.getFileName() + ".mcmeta");
					all.add(new Vanilla(folder[0].substring("assets/minecraft/".length()) + "/" + relative, w, h,
						image.getRGB(0, 0, w, h, null, 0, w), Files.exists(meta) ? Files.readAllBytes(meta) : null));
				});
			}
			vanillaCache = List.copyOf(all);
		}
		return vanillaCache;
	}

	private interface FileVisitor {
		void visit(String relative, Path file) throws IOException;
	}

	/** Visits every PNG under a folder of Minecraft's own assets (inside the game jar). */
	private static void withVanillaFolder(String folder, String marker, FileVisitor visitor) throws IOException {
		URL url = Minecraft.class.getResource("/" + folder + "/" + marker);
		if (url == null) {
			throw new IOException("Minecraft assets not found: " + folder);
		}
		URI uri;
		try {
			uri = url.toURI();
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
				dir = Path.of(uri);
				for (int i = 0; i < marker.split("/").length; i++) {
					dir = dir.getParent();
				}
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

	private static void deleteTree(Path dir) throws IOException {
		if (!Files.exists(dir)) {
			return;
		}
		try (Stream<Path> walk = Files.walk(dir)) {
			for (Path p : walk.sorted(Comparator.reverseOrder()).toList()) {
				Files.deleteIfExists(p);
			}
		}
	}

	private static final PackLocationInfo LOCATION = new PackLocationInfo(ID, Component.literal(ClientInfo.NAME + " look"),
		PackSource.BUILT_IN, Optional.empty());
	private static final PackSelectionConfig SELECTION = new PackSelectionConfig(true, Pack.Position.TOP, true);

	/** Added to the client's pack repository by MinecraftMixin. */
	public static final RepositorySource SOURCE = consumer -> {
		try {
			Files.createDirectories(ROOT.resolve("assets"));
		} catch (IOException ignored) {
			// PathPackResources copes with a missing folder
		}
		Pack.Metadata metadata = new Pack.Metadata(Component.literal("The menu look's repainted Minecraft menus"),
			PackCompatibility.COMPATIBLE, FeatureFlagSet.of(), List.of());
		consumer.accept(new Pack(LOCATION, new Pack.ResourcesSupplier() {
			@Override
			public PackMetadataResources openMetadata(PackLocationInfo location) {
				return new PathPackResources(location, ROOT);
			}

			@Override
			public Stream<PackResources> openResources(PackLocationInfo location, Pack.Metadata meta) {
				return Stream.of(new PathPackResources(location, ROOT));
			}
		}, metadata, SELECTION));
	};
}
