package dev.mordo.kairo.gui.menu;

import dev.mordo.kairo.core.LookCodes;
import dev.mordo.kairo.gui.Theme;
import dev.mordo.kairo.gui.Toasts;
import net.minecraft.client.Minecraft;

/** The "Copy my look" and "Paste a look" buttons: look codes through the clipboard. */
public final class LookShare {
	private LookShare() {
	}

	public static void copy() {
		String code = LookCodes.export();
		Minecraft.getInstance().keyboardHandler.setClipboard(code);
		Toasts.show("Look code copied (" + code.length() + " characters). Paste it to a friend", Theme.accent());
	}

	public static void paste() {
		String text = Minecraft.getInstance().keyboardHandler.getClipboard();
		int changed = LookCodes.apply(text);
		if (changed < 0) {
			Toasts.show("No look code on the clipboard. Copy one first", 0xFFFF7A7A);
		} else if (changed == 0) {
			Toasts.show("That look is the one you have already", Theme.accent());
		} else {
			Toasts.show("Look applied: " + changed + " settings changed", Theme.accent());
		}
	}
}
