package dev.alfxyz.leoneclient.mixin;

import java.util.List;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Lets a module take back a chat line it showed earlier, so a repeated alert can move down with a count. */
@Mixin(ChatComponent.class)
public interface ChatHistoryAccessor {
	/** Every message in the chat, newest first. */
	@Accessor("allMessages")
	List<GuiMessage> leone$allMessages();

	/** Rebuilds the wrapped lines from the messages, after one was removed. */
	@Invoker("refreshTrimmedMessages")
	void leone$refreshLines();
}
