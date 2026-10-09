package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.staffchat.StaffState;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Runs every incoming chat line through the chat modules, in a fixed order. */
public final class ChatHooks {
	private ChatHooks() {
	}

	/** Returns the line to show (possibly the same object), or null to hide it. Client messages are left alone. */
	public static @Nullable Component incoming(Component message, GuiMessageSource source) {
		String plain = Chat.plain(message);
		// only the server can switch your staff state or server, never something a player typed
		if (source == GuiMessageSource.SYSTEM_SERVER) {
			LeoneMC.onChat(plain);
			StaffState.onServerMessage(plain);
			if (Modules.ITEM_COOLDOWNS.onServerMessage(plain)) return null;
		}
		// trackers first, so they see lines that a filter below hides
		Modules.TIMERS.track(plain);
		Modules.SESSION_STATS.track(plain);
		Modules.MOD_MODE.track(plain);
		Modules.REPORTS.track(plain);
		Component out = Modules.ANTICHEAT_ALERTS.handle(message, plain);
		if (out == null) return null;
		if (Modules.ALERT_FILTER.hides(plain)) return null;
		if (Modules.CHAT_CLEANER.hides(plain)) return null;
		if (LeoneMC.active()) {
			FriendActivity.Event friend = FriendActivity.track(plain);
			if (friend != null) {
				out = Modules.FRIEND_ACTIVITY.handle(out, friend);
				if (out == null) return null;
			}
		}
		out = Modules.FRIEND_HIGHLIGHT.handle(out);
		out = Modules.MENTIONS.handle(out, plain);
		// last, so a repeat is compared with the line exactly as it was shown
		return Modules.CHAT_CLEANER.stack(out, plain);
	}
}
