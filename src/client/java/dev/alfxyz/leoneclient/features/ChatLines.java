package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.mixin.ChatHistoryAccessor;
import dev.alfxyz.leoneclient.staffchat.StaffPlaceholder;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;

/** Lets a module take back a line it put in chat earlier, so a repeat can replace it with a count. */
final class ChatLines {
	/** Only this many of the newest lines are searched. */
	private static final int SEARCH = 100;

	private ChatLines() {
	}

	/** Removes the newest chat line with this text (as {@link Chat#plain} gives it), if it is still there. */
	static void takeBack(String text) {
		var chat = Minecraft.getInstance().gui.hud.getChat();
		List<GuiMessage> messages = ((ChatHistoryAccessor) chat).leone$allMessages();
		for (int i = 0; i < Math.min(messages.size(), SEARCH); i++) {
			Component content = messages.get(i).content();
			if (content instanceof StaffPlaceholder placeholder) content = placeholder.real();
			if (Chat.plain(content).equals(text)) {
				messages.remove(i);
				((ChatHistoryAccessor) chat).leone$refreshLines();
				return;
			}
		}
	}

	/** The message with a repeat count after it, like "[x3]". */
	static Component counted(Component message, int count) {
		return message.copy().append(Component.literal(" [x" + count + "]").withStyle(ChatFormatting.GOLD));
	}
}
