package dev.mordo.kairo.mods;

import dev.mordo.kairo.Kairo;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.Choice;
import dev.mordo.kairo.core.settings.ChoiceSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.FilePicker;
import dev.mordo.kairo.gui.Icon;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import dev.mordo.kairo.mods.totem.TotemImages;
import dev.mordo.kairo.mods.totem.TotemPack;
import dev.mordo.kairo.sound.SoundChoices;
import dev.mordo.kairo.sound.SoundLibrary;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Change how the totem looks (any image, or a built-in recolour) and how its pop sounds.
 * The look is a texture in the client's own resource pack, so it shows everywhere: hotbar, hand, the pop animation.
 * Changing it reloads resources once (like switching a resource pack).
 */
public final class TotemChanger extends Module {
	public static final Identifier TOTEM_SOUND = Identifier.withDefaultNamespace("item.totem.use");
	private static final Set<String> IMAGE_TYPES = Set.of("png", "jpg", "jpeg", "bmp", "gif");

	public final ChoiceSetting look = add(new ChoiceSetting("look", "Totem look", "vanilla", TotemChanger::lookChoices)
		.<ChoiceSetting>description("Any PNG, JPEG, BMP or GIF works")
		.browseWith("Add image…", done -> FilePicker.importInto(folder(), "Images", "png;jpg;jpeg;bmp;gif",
			file -> done.accept("file:" + file.getFileName()))));
	public final BooleanSetting removeBackground = add(new BooleanSetting("remove_background", "Remove image background", false)
		.description("Clears a plain background around your image (handy for JPEGs)")
		.visibleWhen(() -> look.get().startsWith("file:")));
	public final ChoiceSetting popSound = add(SoundChoices.setting("pop_sound", "Pop sound", "Plays instead of the totem sound",
		TOTEM_SOUND, () -> this.popVolume.value()));
	public final SliderSetting popVolume = add(new SliderSetting("pop_volume", "Pop volume", 1.0, 0.05, 1.0, 0.05).percent()
		.visibleWhen(() -> !popSound.get().equals(SoundLibrary.VANILLA)));
	public final SliderSetting popPitch = add(new SliderSetting("pop_pitch", "Pop pitch", 1.0, 0.5, 2.0, 0.05).multiplier()
		.visibleWhen(() -> !popSound.get().equals(SoundLibrary.VANILLA)));

	/** Set when the look changes; the texture is rebuilt a moment later (so dragging through options doesn't reload 5 times). */
	private long rebuildAt;

	public TotemChanger() {
		super("totem_changer", "Totem Changer", "Your own totem image and pop sound", Category.VISUAL, Icon.IMAGE, true);
		look.onChange(v -> scheduleRebuild());
		removeBackground.onChange(v -> scheduleRebuild());
		enabled.onChange(v -> scheduleRebuild());
	}

	public static Path folder() {
		return ClientInfo.configDir("totems");
	}

	@Override
	public void init() {
		// At startup, write the texture before the first resource load so no extra reload is needed.
		try {
			Files.createDirectories(folder());
			writeTexture(build());
		} catch (IOException | RuntimeException e) {
			Kairo.LOGGER.warn("Couldn't prepare the totem texture", e);
		}
	}

	/** Runs every client tick (even while disabled, so turning it off brings the vanilla totem back). */
	public void tickRebuild() {
		if (rebuildAt != 0 && System.currentTimeMillis() >= rebuildAt) {
			rebuildAt = 0;
			Minecraft mc = Minecraft.getInstance();
			Util.backgroundExecutor().execute(() -> {
				try {
					if (writeTexture(build())) {
						mc.execute(() -> {
							Toasts.show("Applying totem look…", Theme.accent());
							mc.reloadResourcePacks();
						});
					}
				} catch (IOException | RuntimeException e) {
					Kairo.LOGGER.warn("Couldn't build the totem texture", e);
					mc.execute(() -> Toasts.show("Couldn't use that image: " + e.getMessage(), 0xFFFF7A7A));
				}
			});
		}
	}

	private void scheduleRebuild() {
		rebuildAt = System.currentTimeMillis() + 350;
	}

	/** The PNG bytes for the chosen look, or null for vanilla. */
	private byte @Nullable [] build() throws IOException {
		String choice = look.get();
		if (!isEnabled() || choice.equals("vanilla")) {
			return null;
		}
		BufferedImage image;
		if (choice.startsWith("tint:")) {
			TotemImages.Tint tint = TotemImages.tint(choice.substring(5));
			if (tint == null) {
				return null;
			}
			image = TotemImages.recolor(TotemImages.vanilla(), tint);
		} else if (choice.startsWith("file:")) {
			Path file = folder().resolve(choice.substring(5));
			if (!Files.isRegularFile(file)) {
				return null;
			}
			image = TotemImages.fromFile(file, removeBackground.isOn());
		} else {
			return null;
		}
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		ImageIO.write(image, "png", out);
		return out.toByteArray();
	}

	/** Writes (or removes) the texture. Returns true if anything changed on disk. */
	private static synchronized boolean writeTexture(byte @Nullable [] png) throws IOException {
		Path target = TotemPack.TOTEM_TEXTURE;
		if (png == null) {
			return Files.deleteIfExists(target);
		}
		if (Files.isRegularFile(target) && java.util.Arrays.equals(Files.readAllBytes(target), png)) {
			return false;
		}
		Files.createDirectories(target.getParent());
		Files.write(target, png);
		return true;
	}

	private static List<Choice> lookChoices() {
		List<Choice> list = new ArrayList<>();
		list.add(new Choice("vanilla", "Vanilla", ""));
		for (TotemImages.Tint tint : TotemImages.TINTS) {
			list.add(new Choice("tint:" + tint.id(), tint.label(), "RECOLORS"));
		}
		Path dir = folder();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(Files::isRegularFile)
					.filter(p -> IMAGE_TYPES.contains(extension(p)))
					.sorted()
					.forEach(p -> list.add(new Choice("file:" + p.getFileName(), p.getFileName().toString(), "YOUR IMAGES")));
			} catch (IOException ignored) {
				// folder unreadable: just the built-ins
			}
		}
		return list;
	}

	private static String extension(Path p) {
		String n = p.getFileName().toString();
		int dot = n.lastIndexOf('.');
		return dot < 0 ? "" : n.substring(dot + 1).toLowerCase(Locale.ROOT);
	}

	// ---- sound ----

	/** Engine key of the pop sound to play instead of vanilla's, or null for vanilla. */
	public @Nullable String popSoundKey() {
		return isEnabled() ? SoundLibrary.resolve(popSound.get()) : null;
	}
}
