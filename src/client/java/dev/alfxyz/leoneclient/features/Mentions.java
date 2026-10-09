package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.web.Friends;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Pings you when someone says your name (or one of your keywords) in chat. */
public final class Mentions extends Module {
	/**
	 * How LeoneMC shows a player talking: "Rank Name [tag] » message" in chat, "[Staff] (Server) Name: message"
	 * in staff chat, "Rank | Name: message" on some servers, and "<Name> message" elsewhere.
	 */
	private static final List<Pattern> SENT = List.of(
		Pattern.compile("^(.{1,80}?) » (.*)$", Pattern.DOTALL),
		Pattern.compile("^\\[Staff\\] \\([^)]{1,32}\\) ([A-Za-z0-9_.]{3,17}): (.*)$", Pattern.DOTALL),
		Pattern.compile("^(?:[^|]{1,40} \\| )?([A-Za-z0-9_.]{3,17}): (.*)$", Pattern.DOTALL),
		Pattern.compile("^<([^>]{1,40})> (.*)$", Pattern.DOTALL));
	/** Plugins that write like players ("Grim » Name failed Check"). */
	private static final Set<String> NOT_PLAYERS = Set.of("grim", "removal", "tg", "anticheat", "server");

	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound", "ALERT", true),
		"Plays a ping when you are mentioned.");
	public final Setting.Toggle highlight = add(new Setting.Toggle("highlight", "Highlight", "ALERT", true),
		"Shows your name in bold in the theme colour wherever it appears in chat.");
	public final Setting.Text keywords = add(new Setting.Text("keywords", "Extra words", "WORDS", "", "nickname, team name", 64, c -> true),
		"More words that count as a mention, separated by commas. Your Minecraft and LeoneMC names are always included.");
	private @Nullable Pattern pattern;
	private String patternKey = "";
	private long lastPing;
	private int mentions;

	public Mentions() {
		super("mentions", Category.CHAT, "Mentions", Icons.AT, "Pings you and highlights your name when someone mentions you in chat.", false);
	}

	private @Nullable Pattern pattern() {
		List<String> words = new ArrayList<>();
		words.add(Minecraft.getInstance().getUser().getName());
		String display = Friends.displayName();
		if (display != null && !display.isBlank()) words.add(display);
		for (String w : keywords.get().split(",")) if (!w.isBlank()) words.add(w.strip());
		String key = String.join("\n", words);
		if (!key.equals(patternKey)) {
			patternKey = key;
			pattern = ChatStyle.words(words);
		}
		return pattern;
	}

	/**
	 * Splits a chat line into who sent it and what they said, or returns null for anything that is not
	 * a player talking (joins, kills, staff notices and the like name you without mentioning you).
	 */
	static String @Nullable [] senderAndBody(String plain) {
		for (Pattern p : SENT) {
			Matcher m = p.matcher(plain);
			if (!m.matches()) continue;
			String sender = m.group(1).strip();
			if (NOT_PLAYERS.contains(sender.toLowerCase(Locale.ROOT))) return null;
			return new String[] {sender, m.group(2)};
		}
		return null;
	}

	/** Whether some text names you: your Minecraft or LeoneMC name, or one of the extra words. Works while the module is off. */
	public boolean namesYou(String text) {
		Pattern p = pattern();
		return p != null && p.matcher(text).find();
	}

	/** Pings and highlights. Returns the message to show. */
	public Component handle(Component message, String plain) {
		if (!enabled()) return message;
		Pattern p = pattern();
		if (p == null) return message;
		String[] parts = senderAndBody(plain);
		// your own messages are not mentions, and neither is a line nobody sent
		if (parts == null || p.matcher(parts[0]).find() || !p.matcher(parts[1]).find()) return message;
		mentions++;
		long now = System.currentTimeMillis();
		if (sound.get() && now - lastPing > 600) {
			lastPing = now;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f));
		}
		if (!highlight.get()) return message;
		return ChatStyle.restyle(message, p, (s, word) -> s.withBold(true).withColor(Colors.ACCENT_RGB));
	}

	@Override
	public String status() {
		return mentions == 0 ? null : mentions == 1 ? "1 mention" : mentions + " mentions";
	}
}
