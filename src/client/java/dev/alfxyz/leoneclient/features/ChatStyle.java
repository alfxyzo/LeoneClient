package dev.alfxyz.leoneclient.features;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

/** Restyles the parts of a chat message that match a pattern, keeping every other style intact. */
public final class ChatStyle {
	private ChatStyle() {
	}

	/** Pattern matching any of {@code words} as a whole word (names may start with "." for Bedrock players). */
	public static Pattern words(List<String> words) {
		StringBuilder sb = new StringBuilder("(?<![A-Za-z0-9_])(?:");
		for (int i = 0; i < words.size(); i++) {
			if (i > 0) sb.append('|');
			sb.append(Pattern.quote(words.get(i)));
		}
		return Pattern.compile(sb.append(")(?![A-Za-z0-9_])").toString(), Pattern.CASE_INSENSITIVE);
	}

	/**
	 * Returns {@code message} with each match restyled by {@code style} (given the
	 * old style and the matched text), or {@code message} itself if nothing matched.
	 */
	public static Component restyle(Component message, Pattern pattern, BiFunction<Style, String, Style> style) {
		if (!pattern.matcher(message.getString()).find()) return message;
		List<Style> styles = new ArrayList<>();
		List<String> texts = new ArrayList<>();
		message.visit((s, text) -> {
			styles.add(s);
			texts.add(text);
			return Optional.empty();
		}, Style.EMPTY);
		MutableComponent out = Component.empty();
		boolean any = false;
		for (int i = 0; i < texts.size(); i++) {
			String text = texts.get(i);
			Style s = styles.get(i);
			Matcher m = pattern.matcher(text);
			int last = 0;
			while (m.find()) {
				if (m.start() > last) out.append(Component.literal(text.substring(last, m.start())).setStyle(s));
				out.append(Component.literal(m.group()).setStyle(style.apply(s, m.group())));
				last = m.end();
				any = true;
			}
			if (last < text.length()) out.append(Component.literal(text.substring(last)).setStyle(s));
		}
		return any ? out : message;
	}
}
