package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Replaces words that LeoneMC's chat filter mutes for, before your message is sent. */
public final class AntiMute extends Module {
	/** Private message commands: the first take a player name before the message, the second do not. */
	private static final List<String> ONE_ARG = List.of("msg", "tell", "w", "whisper", "m", "pm", "dm", "message");
	private static final List<String> NO_ARG = List.of("r", "reply");

	public final Setting.Chips filters = add(new Setting.Chips("filters", "Filters", "FILTER",
			List.of(MuteFilter.DISCRIMINATION, MuteFilter.DEATH_WISHES, MuteFilter.SWEARS, MuteFilter.ADS),
			List.of(MuteFilter.DISCRIMINATION, MuteFilter.DEATH_WISHES, MuteFilter.SWEARS, MuteFilter.ADS)),
		"Which kinds of words get replaced.");
	public final Setting.Text replacement = add(new Setting.Text("replacement", "Replacement", "FILTER", "[redacted]", "[redacted]", 32, c -> true),
		"The text that takes the place of a blocked word.");
	public final Setting.Toggle privateMessages = add(new Setting.Toggle("private_messages", "Private messages", "WHERE", true),
		"Also filters what you write with /msg, /r and other private message commands.");
	public final Setting.Toggle notify = add(new Setting.Toggle("notify", "Tell me what changed", "WHERE", true),
		"Shows a line in chat with the words that were replaced.");

	public AntiMute() {
		super("anti_mute", Category.CHAT, "Anti-Mute", Icons.ERASER,
			"Swaps out words that would get you muted on LeoneMC before your message is sent.", true);
	}

	private String rep() {
		String r = replacement.get();
		return r.isBlank() ? "[redacted]" : r;
	}

	private Set<String> groups() {
		return filters.selected;
	}

	/** Filters a chat message. Returns it unchanged when nothing matched. */
	public String chat(String message) {
		if (!enabled()) return message;
		MuteFilter.Result r = MuteFilter.filter(message, rep(), groups());
		if (r.changed()) report(r.matched());
		return r.text();
	}

	/** Filters the message part of a private message command (given without the slash). */
	public String command(String command) {
		if (!enabled() || !privateMessages.get()) return command;
		String[] parts = command.split(" ", -1);
		String name = parts[0].toLowerCase(Locale.ROOT);
		int skip = ONE_ARG.contains(name) ? 2 : NO_ARG.contains(name) ? 1 : -1;
		if (skip < 0 || parts.length <= skip) return command;
		int start = 0;
		for (int i = 0; i < skip; i++) start = command.indexOf(' ', start) + 1;
		String head = command.substring(0, start), body = command.substring(start);
		MuteFilter.Result r = MuteFilter.filter(body, rep(), groups());
		if (!r.changed()) return command;
		report(r.matched());
		return head + r.text();
	}

	private void report(List<String> matched) {
		if (!notify.get()) return;
		MutableComponent msg = Component.empty();
		List<String> unique = matched.stream().distinct().limit(4).toList();
		for (int i = 0; i < unique.size(); i++) {
			if (i > 0) msg.append(Component.literal(", ").withStyle(ChatFormatting.DARK_GRAY));
			msg.append(Component.literal("\"" + unique.get(i) + "\"").withStyle(ChatFormatting.RED));
		}
		if (matched.stream().distinct().count() > 4) msg.append(Component.literal("…").withStyle(ChatFormatting.DARK_GRAY));
		msg.append(Component.literal(" → ").withStyle(ChatFormatting.DARK_GRAY));
		msg.append(Component.literal("\"" + rep() + "\"").withStyle(ChatFormatting.GREEN));
		Chat.info(msg);
	}
}
