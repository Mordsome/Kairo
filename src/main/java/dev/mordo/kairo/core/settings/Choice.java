package dev.mordo.kairo.core.settings;

/** One option of a {@link ChoiceSetting}: a stable id (what gets saved), a display label, and a small group tag. */
public record Choice(String id, String label, String group) {
	public static Choice of(String id, String label) {
		return new Choice(id, label, "");
	}
}
