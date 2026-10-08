package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Runs every incoming chat line through the chat modules, in a fixed order. */
public final class ChatHooks {
	private ChatHooks() {
	}

	/** Returns the line to show (possibly the same object), or null to hide it. Client messages are left alone. */
	public static @Nullable Component incoming(Component message) {
		String plain = Chat.plain(message);
		LeoneMC.onChat(plain);
		if (Modules.ALERT_FILTER.hides(plain)) return null;
		Component out = message;
		if (LeoneMC.active()) {
			FriendActivity.Event friend = FriendActivity.track(plain);
			if (friend != null) {
				out = Modules.FRIEND_ACTIVITY.handle(out, friend);
				if (out == null) return null;
			}
		}
		out = Modules.FRIEND_HIGHLIGHT.handle(out);
		return Modules.MENTIONS.handle(out, plain);
	}
}
