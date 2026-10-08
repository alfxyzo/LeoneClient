package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.web.Friends;
import dev.alfxyz.leoneclient.web.LeoneWeb.Friend;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Reworks LeoneMC's "Friends | X has joined the server Y." messages. */
public final class FriendActivity extends Module {
	private static final Pattern LINE = Pattern.compile("^Friends \\| (\\S+) has (joined|left) the server (.+?)\\.?$");
	private static final int JOIN = 0x4ADE80, LEAVE = 0xF87171;

	public final Setting.Choice display = add(new Setting.Choice("display", "Show as", "MESSAGES",
			List.of("Compact", "Chat", "Notification", "Hidden"), "Compact"),
		"Compact turns each message into a short line you can click to message the friend. Chat leaves it as the server sent it. "
			+ "Notification shows a pop-up instead of a chat line.");
	public final Setting.Choice scope = add(new Setting.Choice("scope", "Which servers", "MESSAGES", List.of("All", "Mine only"), "All"),
		"Mine only keeps just the friends joining or leaving the server you are on.");
	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound on my server", "MESSAGES", false),
		"Plays a sound when a friend joins the server you are on.");

	public record Event(String name, boolean joined, String server, boolean yours) {
	}

	public FriendActivity() {
		super("friend_activity", Category.FRIENDS, "Friend Activity", Icons.FRIENDS,
			"Tidies LeoneMC's friend join and leave messages, and keeps track of where your friends are for the Friends and Servers pages.", true);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	/** Parses a friend join or leave line and records where the friend went. Null if it is not one. */
	public static @Nullable Event track(String plain) {
		Matcher m = LINE.matcher(plain);
		if (!m.matches()) return null;
		String name = m.group(1), where = m.group(3);
		boolean joined = m.group(2).equals("joined");
		String current = LeoneMC.server();
		boolean yours = where.startsWith("you");
		String server = yours ? (current != null ? current : "your server") : where;
		if (!yours && current != null && server.equalsIgnoreCase(current)) yours = true;
		Friends.seen(name, joined, server);
		return new Event(name, joined, server, yours);
	}

	/** What to show for a friend activity line: the message, a replacement, or null to hide it. */
	public @Nullable Component handle(Component message, Event e) {
		if (!active()) return message;
		if (scope.is("Mine only") && !e.yours()) return null;
		if (e.yours() && e.joined() && sound.get()) {
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 0.8f, 0.6f));
		}
		Friend f = Friends.byName(e.name());
		return switch (display.get()) {
			case "Chat" -> message;
			case "Hidden" -> null;
			case "Notification" -> {
				String detail = (e.joined() ? "Joined " : "Left ") + (e.yours() ? "your server" : e.server());
				if (f != null) Notices.player(e.name(), detail, e.joined() ? JOIN : LEAVE, f.uuid());
				else Notices.push(e.name(), detail, e.joined() ? JOIN : LEAVE, Icons.FRIENDS);
				yield null;
			}
			default -> compact(e, f);
		};
	}

	private static Component compact(Event e, @Nullable Friend f) {
		MutableComponent name = Component.literal(e.name()).withStyle(s -> s
			.withColor(f != null ? f.color() : 0xFFFFFF)
			.withClickEvent(new ClickEvent.SuggestCommand("/msg " + e.name() + " "))
			.withHoverEvent(new HoverEvent.ShowText(Component.literal("Click to message " + e.name()))));
		MutableComponent out = Component.literal(e.joined() ? "+ " : "- ").withColor(e.joined() ? JOIN : LEAVE).append(name);
		out.append(Component.literal(e.joined() ? " joined " : " left ").withStyle(ChatFormatting.GRAY));
		if (e.yours()) out.append(Component.literal("your server").withColor(Colors.ACCENT_RGB));
		else out.append(Component.literal(e.server()).withStyle(ChatFormatting.WHITE));
		return out;
	}
}
