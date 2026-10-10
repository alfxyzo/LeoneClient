package dev.alfxyz.leoneclient.web;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * What a player's page on leonemc.net shows that LeoneMC's API does not (yet): the colour of their name,
 * their playtime on the whole network, their profile views, the colour of each friend's name, and the
 * colours of stat values such as ranked tiers. Read from the page's HTML, as Leone Client did before the
 * API existed; everything else comes from the API.
 */
final class ProfilePage {
	private static final String HEX = "(#[0-9A-Fa-f]{3,8})";
	private static final Pattern NAME = Pattern.compile("class=\"player-name\"(?:\\s+style=\"color:\\s*" + HEX + "[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern PLAYTIME = Pattern.compile("data-minutes-played\\s+data-millis=\"(\\d+)\"");
	private static final Pattern VIEWS = Pattern.compile("data-profile-views>(\\d+)<");
	private static final Pattern CARD = Pattern.compile("<div class=\"player-stat-card\">(.*?)</ul>", Pattern.DOTALL);
	private static final Pattern CARD_TITLE = Pattern.compile("player-stat-card-header\">\\s*<span>([^<]*)</span>");
	private static final Pattern STAT = Pattern.compile("<li>(.*?)</li>", Pattern.DOTALL);
	private static final Pattern STAT_LABEL = Pattern.compile("player-stat-label\">([^<]*)<");
	private static final Pattern STAT_VALUE = Pattern.compile("player-stat-badge\"(?:\\s+style=\"color:\\s*" + HEX + "[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern FRIEND = Pattern.compile(
		"<a\\s+href=\"/player/([0-9a-fA-F-]{36})\"\\s+class=\"friend-head([^\"]*)\"\\s*>(.*?)</a>", Pattern.DOTALL);
	private static final Pattern FRIEND_NAME = Pattern.compile("friend-head-tooltip-name\"(?:\\s+style=\"color:\\s*" + HEX + "[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern ENTITY = Pattern.compile("&(#\\d+|#x[0-9a-fA-F]+|amp|lt|gt|quot|apos);");

	/**
	 * The page's extras. {@code statColours} is keyed by stat card and stat name in lower case
	 * ("wildkits\nrank") and also by card and shown value, since the page and the API can name a stat
	 * differently.
	 */
	record Extras(@Nullable Integer nameColour, long playtimeMs, int views, Map<UUID, Integer> friendColours, Map<String, Integer> statColours) {
		@Nullable Integer statColour(String card, String label, String value) {
			Integer c = statColours.get(key(card, label));
			return c != null ? c : statColours.get(key(card, "=" + value));
		}
	}

	private ProfilePage() {
	}

	static Extras read(String html) {
		Integer nameColour = null;
		Matcher m = NAME.matcher(html);
		if (m.find() && m.group(1) != null) nameColour = hex(m.group(1));
		m = PLAYTIME.matcher(html);
		long playtime = m.find() ? Long.parseLong(m.group(1)) : 0;
		m = VIEWS.matcher(html);
		int views = m.find() ? Integer.parseInt(m.group(1)) : 0;
		Map<UUID, Integer> friends = new HashMap<>();
		m = FRIEND.matcher(html);
		while (m.find()) {
			Matcher n = FRIEND_NAME.matcher(m.group(3));
			if (!n.find() || n.group(1) == null) continue;
			try {
				Integer c = hex(n.group(1));
				if (c != null) friends.put(UUID.fromString(m.group(1)), c);
			} catch (IllegalArgumentException ignored) {
			}
		}
		Map<String, Integer> stats = new HashMap<>();
		Matcher card = CARD.matcher(html);
		while (card.find()) {
			Matcher t = CARD_TITLE.matcher(card.group(1));
			if (!t.find()) continue;
			String title = unescape(t.group(1)).strip();
			Matcher li = STAT.matcher(card.group(1));
			while (li.find()) {
				Matcher label = STAT_LABEL.matcher(li.group(1)), value = STAT_VALUE.matcher(li.group(1));
				if (!label.find() || !value.find() || value.group(1) == null) continue;
				Integer c = hex(value.group(1));
				if (c == null) continue;
				stats.put(key(title, unescape(label.group(1)).strip()), c);
				stats.put(key(title, "=" + unescape(value.group(2)).strip()), c);
			}
		}
		return new Extras(nameColour, playtime, views, friends, stats);
	}

	private static String key(String card, String label) {
		return card.toLowerCase(Locale.ROOT) + "\n" + label.toLowerCase(Locale.ROOT);
	}

	private static @Nullable Integer hex(String s) {
		String h = s.startsWith("#") ? s.substring(1) : s;
		if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
		if (h.length() == 8) h = h.substring(0, 6);
		try {
			return h.length() == 6 ? Integer.parseInt(h, 16) : null;
		} catch (NumberFormatException e) {
			return null;
		}
	}

	private static String unescape(String s) {
		if (s.indexOf('&') < 0) return s;
		Matcher m = ENTITY.matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String e = m.group(1);
			String rep = switch (e) {
				case "amp" -> "&";
				case "lt" -> "<";
				case "gt" -> ">";
				case "quot" -> "\"";
				case "apos" -> "'";
				default -> {
					try {
						int cp = e.startsWith("#x") ? Integer.parseInt(e.substring(2), 16) : Integer.parseInt(e.substring(1));
						yield new String(Character.toChars(cp));
					} catch (RuntimeException ex) {
						yield m.group();
					}
				}
			};
			m.appendReplacement(sb, Matcher.quoteReplacement(rep));
		}
		m.appendTail(sb);
		return sb.toString();
	}
}
