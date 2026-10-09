package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.web.Friends;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Pings you when someone says your name (or one of your keywords) in chat. */
public final class Mentions extends Module {
	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound", "ALERT", true),
		"Plays a ping when you are mentioned.");
	public final Setting.Toggle highlight = add(new Setting.Toggle("highlight", "Highlight", "ALERT", true),
		"Shows your name in bold in the theme colour wherever it appears in chat.");
	public final Setting.Text keywords = add(new Setting.Text("keywords", "Extra words", "WORDS", "", "nickname, team name", 64, c -> true),
		"More words that count as a mention, separated by commas. Your Minecraft and LeoneMC names are always included.");
	private @Nullable Pattern pattern;
	private String patternKey = "";
	private long lastPing;

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

	/** Pings and highlights. Returns the message to show. */
	public Component handle(Component message, String plain) {
		if (!enabled()) return message;
		Pattern p = pattern();
		if (p == null) return message;
		// the sender comes first ("[Rank] Name: hi" on LeoneMC, "<Name> hi" in vanilla): your own messages are not mentions
		int end = plain.startsWith("<") ? plain.indexOf('>') : plain.indexOf(':');
		boolean hasSender = end > 0 && end < 48;
		if (hasSender && p.matcher(plain.substring(0, end)).find()) return message;
		if (!p.matcher(hasSender ? plain.substring(end + 1) : plain).find()) return message;
		long now = System.currentTimeMillis();
		if (sound.get() && now - lastPing > 600) {
			lastPing = now;
			Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f));
		}
		if (!highlight.get()) return message;
		return ChatStyle.restyle(message, p, (s, word) -> s.withBold(true).withColor(Colors.ACCENT_RGB));
	}
}
