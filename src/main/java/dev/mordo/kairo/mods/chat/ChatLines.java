package dev.mordo.kairo.mods.chat;

import dev.mordo.kairo.mixin.ChatComponentAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/** Helpers for the chat screen: which message is under the mouse, and the search filter. */
public final class ChatLines {
	private static @Nullable Predicate<GuiMessage> savedFilter;

	private ChatLines() {
	}

	/** The chat message under a GUI position, or null. Mirrors vanilla's chat layout maths. */
	public static @Nullable GuiMessage messageAt(ChatComponent chat, double mouseX, double mouseY) {
		ChatComponentAccessor access = (ChatComponentAccessor) chat;
		Minecraft mc = Minecraft.getInstance();
		double scale = access.kairo$scale();
		int screenHeight = mc.getWindow().getGuiScaledHeight();
		int chatBottom = (int) Math.floor((screenHeight - 40) / scale);
		int entryHeight = (int) (9 * (mc.options.chatLineSpacing().get() + 1.0));
		double localY = mouseY / scale;
		double localX = mouseX / scale - 4;
		int index = (int) Math.floor((chatBottom - localY) / entryHeight);
		List<GuiMessage.Line> lines = access.kairo$lines();
		int visible = Math.min(access.kairo$linesPerPage(), lines.size() - access.kairo$scroll());
		if (localX < -4 || localX > ChatComponent.getWidth(mc.options.chatWidth().get()) / scale || index < 0 || index >= visible) {
			return null;
		}
		return lines.get(index + access.kairo$scroll()).parent();
	}

	/** Shows only messages containing {@code query} (case-insensitive); empty shows everything again. */
	public static void filter(ChatComponent chat, String query) {
		if (savedFilter == null) {
			savedFilter = ((ChatComponentAccessor) chat).kairo$filter();
		}
		Predicate<GuiMessage> base = savedFilter;
		String needle = query.trim().toLowerCase(Locale.ROOT);
		chat.setVisibleMessageFilter(needle.isEmpty() ? base
			: message -> base.test(message) && message.content().getString().toLowerCase(Locale.ROOT).contains(needle));
	}

	/** Puts the chat's own filter back (when the chat screen closes). */
	public static void clearFilter(ChatComponent chat) {
		if (savedFilter != null) {
			chat.setVisibleMessageFilter(savedFilter);
			savedFilter = null;
		}
	}
}
