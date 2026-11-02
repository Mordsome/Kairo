package dev.mordo.kairo.cosmetics;

import com.mojang.blaze3d.platform.InputConstants;
import dev.mordo.kairo.core.Category;
import dev.mordo.kairo.core.ClientInfo;
import dev.mordo.kairo.core.Module;
import dev.mordo.kairo.core.keybinds.Keybind;
import dev.mordo.kairo.core.settings.BooleanSetting;
import dev.mordo.kairo.core.settings.Choice;
import dev.mordo.kairo.core.settings.ChoiceSetting;
import dev.mordo.kairo.core.settings.ColorSetting;
import dev.mordo.kairo.core.settings.EnumSetting;
import dev.mordo.kairo.core.settings.KeybindSetting;
import dev.mordo.kairo.core.settings.SliderSetting;
import dev.mordo.kairo.gui.FilePicker;
import dev.mordo.kairo.gui.Icon;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Cosmetics: capes, wings, hats, bandanas, trails and emotes. Client-side only: they're drawn on your own
 * character (third person, F5, the inventory and the wardrobe preview); other players don't see them.
 * Edited in the wardrobe (ring → Cosmetics).
 */
public final class Cosmetics extends Module {
	// ---- styles ----

	public interface Styled extends EnumSetting.Labeled {
		boolean none();
	}

	public enum WingStyle implements Styled {
		NONE("None"), ANGEL("Angel"), DRAGON("Dragon"), BAT("Bat"), SERAPH("Seraph"), ENERGY("Energy"),
		DEMON("Demon"), PHOENIX("Phoenix"), CRYSTAL("Crystal"), SKELETAL("Skeletal");

		private final String label;

		WingStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		@Override
		public boolean none() {
			return this == NONE;
		}
	}

	public enum HatStyle implements Styled {
		NONE("None"), CROWN("Crown"), HALO("Halo"), TOP_HAT("Top hat"), PARTY_HAT("Party hat"), WITCH_HAT("Witch hat"),
		CAT_EARS("Cat ears"), BUNNY_EARS("Bunny ears"), HORNS("Horns"), HEADPHONES("Headphones"), SUNGLASSES("Sunglasses"),
		COWBOY("Cowboy hat"), BEANIE("Beanie");

		private final String label;

		HatStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		@Override
		public boolean none() {
			return this == NONE;
		}
	}

	public enum BandanaStyle implements Styled {
		NONE("None"), HEADBAND("Headband"), NINJA("Ninja band"), PIRATE("Pirate bandana"), FACE_MASK("Face bandana"),
		SCARF("Scarf"), NECKERCHIEF("Neckerchief");

		private final String label;

		BandanaStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		@Override
		public boolean none() {
			return this == NONE;
		}
	}

	public enum TrailStyle implements Styled {
		NONE("None"), HEARTS("Hearts"), SPARKLES("Sparkles"), FLAMES("Flames"), SOUL_FLAMES("Soul flames"), NOTES("Notes"),
		SNOW("Snow"), TOTEM("Totem"), ENCHANT("Enchant"), CLOUDS("Clouds"), CHERRY("Cherry petals"), RAINBOW("Rainbow dust"),
		ELECTRIC("Electric");

		private final String label;

		TrailStyle(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}

		@Override
		public boolean none() {
			return this == NONE;
		}
	}

	public enum Pattern implements EnumSetting.Labeled {
		SOLID("Solid"), STRIPES("Stripes"), DOTS("Dots");

		private final String label;

		Pattern(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	public enum EmoteCamera implements EnumSetting.Labeled {
		FRONT("Front view"), BACK("Behind you"), OFF("Don't change");

		private final String label;

		EmoteCamera(String label) {
			this.label = label;
		}

		@Override
		public String label() {
			return label;
		}
	}

	// ---- settings ----

	public final ChoiceSetting cape = add(new ChoiceSetting("cape", "Cape", "none", Cosmetics::capeChoices)
		.<ChoiceSetting>description("A built-in design, or any image")
		.browseWith("Add image…", done -> FilePicker.importInto(capeFolder(), "Images", "png;jpg;jpeg;bmp",
			file -> done.accept("file:" + file.getFileName()))));
	public final ColorSetting capeColor = add(new ColorSetting("cape_color", "Cape color", 0xFF7FD8FF, false).followsAccent()
		.description("Used by the Gradient, Stripes, Checker and Neon designs"));
	public final ColorSetting capeColor2 = add(new ColorSetting("cape_color_2", "Cape second color", 0xFF0B1019, false));

	public final EnumSetting<WingStyle> wings = add(new EnumSetting<>("wings", "Wings", WingStyle.NONE));
	public final ColorSetting wingColor = add(new ColorSetting("wing_color", "Wing color", 0xFFF4F8FB, false));
	public final ColorSetting wingColor2 = add(new ColorSetting("wing_color_2", "Wing tip color", 0xFF7FD8FF, false).followsAccent());
	public final SliderSetting wingSize = add(new SliderSetting("wing_size", "Wing size", 1.0, 0.5, 1.6, 0.05).multiplier());
	public final SliderSetting wingFlap = add(new SliderSetting("wing_flap", "Flap speed", 1.0, 0, 3, 0.1).multiplier()
		.description("0 = still"));
	public final BooleanSetting wingRainbow = add(new BooleanSetting("wing_rainbow", "Rainbow wings", false));
	public final BooleanSetting wingGlow = add(new BooleanSetting("wing_glow", "Glowing wings", false)
		.description("Always lit, even in the dark"));

	public final EnumSetting<HatStyle> hat = add(new EnumSetting<>("hat", "Hat", HatStyle.NONE));
	public final ColorSetting hatColor = add(new ColorSetting("hat_color", "Hat color", 0xFF1A1D24, false));
	public final ColorSetting hatColor2 = add(new ColorSetting("hat_color_2", "Hat detail color", 0xFFFFC94D, false));
	// Fit: every skin's head is different.
	public final SliderSetting hatHeight = add(new SliderSetting("hat_height", "Hat height", 0, -4, 4, 0.25).unit("px")
		.description("Move the hat up (+) or down (-)"));
	public final SliderSetting hatForward = add(new SliderSetting("hat_forward", "Hat forward", 0, -3, 3, 0.25).unit("px")
		.description("Move it towards the face (+) or the back (-)"));
	public final SliderSetting hatSize = add(new SliderSetting("hat_size", "Hat size", 1.0, 0.7, 1.4, 0.02).multiplier());
	public final SliderSetting hatTilt = add(new SliderSetting("hat_tilt", "Hat tilt", 0, -25, 25, 1).unit("°")
		.description("Tip it forwards (+) or back (-)"));
	public final SliderSetting earSpacing = add(new SliderSetting("ear_spacing", "Ear spacing", 0, -1.5, 3, 0.25).unit("px")
		.<SliderSetting>description("Move the ears further apart (+) or closer (-)").visibleWhen(this::catEars));
	public final SliderSetting earAngle = add(new SliderSetting("ear_angle", "Ear angle", 0, -30, 40, 1).unit("°")
		.<SliderSetting>description("Tilt the ears outwards (+) or inwards (-)").visibleWhen(this::catEars));
	public final SliderSetting earSize = add(new SliderSetting("ear_size", "Ear size", 1.0, 0.6, 1.6, 0.05).multiplier()
		.<SliderSetting>visibleWhen(this::catEars));
	public final BooleanSetting earTwitch = add(new BooleanSetting("ear_twitch", "Ear twitches", true)
		.<BooleanSetting>description("Ears flick now and then, sway a little and lie back when you run").visibleWhen(this::catEars));

	public final EnumSetting<BandanaStyle> bandana = add(new EnumSetting<>("bandana", "Bandana", BandanaStyle.NONE));
	public final ColorSetting bandanaColor = add(new ColorSetting("bandana_color", "Bandana color", 0xFFD7263D, false));
	public final ColorSetting bandanaColor2 = add(new ColorSetting("bandana_color_2", "Bandana pattern color", 0xFFF4F8FB, false));
	public final EnumSetting<Pattern> bandanaPattern = add(new EnumSetting<>("bandana_pattern", "Bandana pattern", Pattern.DOTS));

	public final EnumSetting<TrailStyle> trail = add(new EnumSetting<>("trail", "Trail", TrailStyle.NONE));
	public final SliderSetting trailDensity = add(new SliderSetting("trail_density", "Trail amount", 1.0, 0.25, 3, 0.25).multiplier());
	public final BooleanSetting trailWhenMoving = add(new BooleanSetting("trail_moving", "Only while moving", true));

	public final KeybindSetting emoteKey = add(new KeybindSetting("emote_key", "Emote wheel", Keybind.keyboard(InputConstants.KEY_B))
		.onPress(EmoteWheelScreen::open)
		.<KeybindSetting>description("Pick an emote; moving stops it"));
	public final EnumSetting<EmoteCamera> emoteCamera = add(new EnumSetting<>("emote_camera", "Camera during emotes", EmoteCamera.FRONT));
	public final BooleanSetting emoteBurst = add(new BooleanSetting("emote_burst", "Emote sparkle", true)
		.description("A small burst of light in your colour when an emote starts"));
	/** Your favourite emotes (the first page of the wheel), in order. Right-click an emote to add or remove it. */
	public final dev.mordo.kairo.core.settings.TextSetting emoteFavourites = add(new dev.mordo.kairo.core.settings.TextSetting("emote_favourites",
		"Favourite emotes", "WAVE,DANCE,BACKFLIP,FLOSS,DAB,CLAP,SIT,LAUGH", 400).<dev.mordo.kairo.core.settings.TextSetting>hidden());
	/** Whether the wardrobe preview turns by itself (the pause button on it). */
	public final BooleanSetting previewSpin = add(new BooleanSetting("preview_spin", "Turn the wardrobe preview", true).<BooleanSetting>hidden());

	public Cosmetics() {
		super("cosmetics", "Cosmetics", "Capes, wings, hats, trails and emotes", Category.VISUAL, Icon.HANGER, true);
	}

	private boolean catEars() {
		return hat.get() == HatStyle.CAT_EARS;
	}

	public static Cosmetics get() {
		return dev.mordo.kairo.core.ModuleManager.get(Cosmetics.class);
	}

	@Override
	@SuppressWarnings({"unchecked", "rawtypes"})
	public void init() {
		try {
			Files.createDirectories(capeFolder());
		} catch (IOException ignored) {
			// the import creates it again if needed
		}
		LivingEntityRenderLayerRegistrationCallback.EVENT.register((type, renderer, helper, context) -> {
			if (renderer instanceof AvatarRenderer<?> avatar) {
				helper.register(new CosmeticsLayer((RenderLayerParent) avatar, this));
			}
		});
	}

	@Override
	public Runnable openAction() {
		return () -> {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(dev.mordo.kairo.gui.menu.MenuScreen.openOn(dev.mordo.kairo.gui.menu.MenuScreen::cosmetics, mc.gui.screen()));
		};
	}

	@Override
	public void tick() {
		Trails.tick(this);
		Emotes.tick();
	}

	/** True for the render state of your own player (in the world, the inventory or the wardrobe preview). */
	/** Id of the stand-in player drawn on the Dashboard main menu (there's no player entity there). */
	public static final int PREVIEW_ID = Integer.MIN_VALUE + 7;

	public static boolean isLocal(AvatarRenderState state) {
		if (state.id == PREVIEW_ID) {
			return true;
		}
		Minecraft mc = Minecraft.getInstance();
		return mc.player != null && state.id == mc.player.getId();
	}

	public static Path capeFolder() {
		return ClientInfo.configDir("capes");
	}

	private static List<Choice> capeChoices() {
		List<Choice> list = new ArrayList<>();
		list.add(new Choice("none", "None", ""));
		for (Capes.Design design : Capes.designs()) {
			list.add(new Choice("design:" + design.name().toLowerCase(Locale.ROOT), design.label(), "DESIGNS"));
		}
		Path dir = capeFolder();
		if (Files.isDirectory(dir)) {
			try (Stream<Path> files = Files.list(dir)) {
				files.filter(Files::isRegularFile)
					.filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g|bmp)"))
					.sorted()
					.forEach(p -> list.add(new Choice("file:" + p.getFileName(), p.getFileName().toString(), "YOUR IMAGES")));
			} catch (IOException ignored) {
				// unreadable folder: just the designs
			}
		}
		return list;
	}
}
