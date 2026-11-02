package dev.mordo.kairo.core.settings;

import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Picks one option from a list that can change at runtime (e.g. the sound files in a folder). The saved value is
 * the option's id; an id that no longer exists simply shows as missing and behaves like the default.
 * Optional hooks add a preview button and a "browse for a file" button to the picker.
 */
public class ChoiceSetting extends Setting<String> {
	private final Supplier<List<Choice>> options;
	private @Nullable Consumer<String> preview;
	private @Nullable Consumer<Consumer<String>> browse;
	private String browseLabel = "Browse…";

	public ChoiceSetting(String id, String name, String defaultValue, Supplier<List<Choice>> options) {
		super(id, name, defaultValue);
		this.options = options;
	}

	public List<Choice> options() {
		return options.get();
	}

	/** Adds a play/preview button (called with the selected id). */
	public ChoiceSetting previewWith(Consumer<String> preview) {
		this.preview = preview;
		return this;
	}

	/** Adds a browse button: the action gets a callback to hand the imported option's id back. */
	public ChoiceSetting browseWith(String label, Consumer<Consumer<String>> browse) {
		this.browseLabel = label;
		this.browse = browse;
		return this;
	}

	public @Nullable Consumer<String> preview() {
		return preview;
	}

	public @Nullable Consumer<Consumer<String>> browse() {
		return browse;
	}

	public String browseLabel() {
		return browseLabel;
	}

	/** Display label for an id (the id itself if it isn't in the list any more). */
	public String label(String id) {
		for (Choice choice : options()) {
			if (choice.id().equals(id)) {
				return choice.label();
			}
		}
		int colon = id.indexOf(':');
		return (colon >= 0 ? id.substring(colon + 1) : id) + " (missing)";
	}

	@Override
	protected String sanitize(String value) {
		return value.length() > 200 ? value.substring(0, 200) : value;
	}

	@Override
	public JsonElement toJson(String value) {
		return new JsonPrimitive(value);
	}

	@Override
	public String fromJson(JsonElement json) {
		return json.getAsString();
	}
}
