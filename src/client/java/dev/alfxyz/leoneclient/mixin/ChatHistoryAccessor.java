package dev.alfxyz.leoneclient.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets modules take back a chat line shown earlier (a repeat moving down with a count) and rebuild the lines (a chat tab change). */
@Mixin(ChatComponent.class)
public interface ChatHistoryAccessor {
	/** Every message in the chat, newest first. */
	@Accessor("allMessages")
	List<GuiMessage> leone$allMessages();

	/** The wrapped lines being shown, newest first. */
	@Accessor("trimmedMessages")
	List<GuiMessage.Line> leone$lines();

	/** How many lines the chat is scrolled up by. */
	@Accessor("chatScrollbarPos")
	int leone$scroll();

	/** Rebuilds the wrapped lines from the messages, after one was removed or the chat tab changed. */
	@Invoker("refreshTrimmedMessages")
	void leone$refreshLines();
}
