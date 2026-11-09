package dev.mordo.kairo.sound;

import dev.mordo.kairo.core.settings.ChoiceSetting;
import dev.mordo.kairo.gui.FilePicker;
import net.minecraft.resources.Identifier;

import java.util.function.DoubleSupplier;

/** Sound pickers: Vanilla, the built-in sounds and the player's files, with a play button and "Add sound…". */
public final class SoundChoices {
	private SoundChoices() {
	}

	/** A song picker for a music emote: "No song" or one of the player's files, with "Add song…". */
	public static ChoiceSetting music(String id, String name, String description) {
		return new ChoiceSetting(id, name, "", SoundLibrary::songChoices).<ChoiceSetting>description(description)
			.browseWith("Add song…", done -> FilePicker.importInto(SoundLibrary.folder(), "Songs (OGG, WAV)", "ogg;wav;aiff;aif;au",
				file -> {
					SoundLibrary.invalidateFiles();
					done.accept("file:" + file.getFileName());
				}));
	}

	/**
	 * @param vanillaEvent the sound being replaced (played when previewing "Vanilla")
	 * @param volume       preview volume
	 */
	public static ChoiceSetting setting(String id, String name, String description, Identifier vanillaEvent, DoubleSupplier volume) {
		ChoiceSetting setting = new ChoiceSetting(id, name, SoundLibrary.VANILLA, SoundLibrary::choices).description(description);
		return setting
			.previewWith(choice -> SoundLibrary.preview(choice, vanillaEvent, (float) volume.getAsDouble()))
			.browseWith("Add sound…", done -> FilePicker.importInto(SoundLibrary.folder(), "Sounds (OGG, WAV)", "ogg;wav;aiff;aif;au",
				file -> {
					SoundLibrary.invalidateFiles();
					done.accept("file:" + file.getFileName());
				}));
	}
}
