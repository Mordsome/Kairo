package dev.mordo.kairo.core;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * Which client this jar is. One codebase builds two: <b>Kairo</b> (glass menus, lots of mods and cosmetics) and
 * <b>Kairo Void</b> (flat dark-grey interface, a few cosmetics and emotes, PvP mode, and big in-game effects).
 * The Void jar carries a {@code kairo-edition.txt} saying "void"; {@code -Dkairo.edition=void} does the same in
 * development ({@code ./gradlew runVoidClient}).
 */
public enum Edition {
	KAIRO,
	VOID;

	private static Edition current = detect();

	public static Edition current() {
		return current;
	}

	public static boolean isVoid() {
		return current == VOID;
	}

	/** Only for automated visual tests, to check both looks in one run. */
	public static void setForTests(Edition edition) {
		current = edition;
	}

	private static Edition detect() {
		String property = System.getProperty("kairo.edition");
		if (property != null) {
			return property.trim().toLowerCase(Locale.ROOT).equals("void") ? VOID : KAIRO;
		}
		try (InputStream in = Edition.class.getResourceAsStream("/kairo-edition.txt")) {
			if (in != null && new String(in.readAllBytes(), StandardCharsets.UTF_8).trim().equalsIgnoreCase("void")) {
				return VOID;
			}
		} catch (IOException ignored) {
			// no marker: the main client
		}
		return KAIRO;
	}
}
