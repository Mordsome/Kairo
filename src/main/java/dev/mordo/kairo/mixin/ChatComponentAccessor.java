package dev.mordo.kairo.mixin;

import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;
import java.util.function.Predicate;

/** Read access to the chat's lines and layout (search filter, right-click copy). */
@Mixin(ChatComponent.class)
public interface ChatComponentAccessor {
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> kairo$lines();

	@Accessor("chatScrollbarPos")
	int kairo$scroll();

	@Accessor("visibleMessageFilter")
	Predicate<GuiMessage> kairo$filter();

	@Invoker("getScale")
	double kairo$scale();

	@Invoker("getLinesPerPage")
	int kairo$linesPerPage();
}
