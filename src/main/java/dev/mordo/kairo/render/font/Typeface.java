package dev.mordo.kairo.render.font;

/** The bundled client fonts (assets/kairo/font, SIL OFL; licenses in /licenses). */
public enum Typeface {
	OUTFIT_REGULAR("outfit-regular.ttf"),
	OUTFIT_MEDIUM("outfit-medium.ttf"),
	OUTFIT_SEMIBOLD("outfit-semibold.ttf"),
	OUTFIT_BOLD("outfit-bold.ttf"),
	MONO_MEDIUM("jetbrainsmono-medium.ttf"),
	MONO_BOLD("jetbrainsmono-bold.ttf");

	private final String file;

	Typeface(String file) {
		this.file = file;
	}

	public String file() {
		return file;
	}
}
