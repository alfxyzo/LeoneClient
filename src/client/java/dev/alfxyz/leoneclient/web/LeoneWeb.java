package dev.alfxyz.leoneclient.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alfxyz.leoneclient.LeoneMC;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/** Reads player profiles from leonemc.net. */
public final class LeoneWeb {
	private static final Pattern NAME = Pattern.compile("class=\"player-name\"(?:\\s+style=\"color:\\s*(#[0-9A-Fa-f]{3,8})[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern RANK = Pattern.compile("class=\"player-rank-badge\"(?:\\s+style=\"background-color:\\s*(#[0-9A-Fa-f]{3,8})[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern FRIEND = Pattern.compile(
		"<a\\s+href=\"/player/([0-9a-fA-F-]{36})\"\\s+class=\"friend-head([^\"]*)\"\\s*>(.*?)</a>", Pattern.DOTALL);
	private static final Pattern FRIEND_NAME = Pattern.compile("friend-head-tooltip-name\"(?:\\s+style=\"color:\\s*(#[0-9A-Fa-f]{3,8})[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern FRIEND_STATUS = Pattern.compile("friend-head-tooltip-status\"\\s*>([^<]*)<");
	private static final Pattern ALT = Pattern.compile("alt=\"([^\"]*)\"");
	private static final Pattern ENTITY = Pattern.compile("&(#\\d+|#x[0-9a-fA-F]+|amp|lt|gt|quot|apos);");

	private LeoneWeb() {
	}

	public record Friend(UUID uuid, String name, int color, boolean online, String status) {
	}

	public record Profile(UUID uuid, String name, int color, String rank, int rankColor, List<Friend> friends) {
	}

	public record Player(UUID uuid, String name) {
	}

	public static String profileUrl(UUID uuid) {
		return LeoneMC.WEBSITE + "/player/" + uuid;
	}

	/** Fetches a profile. Completes with empty when the player has never joined LeoneMC. */
	public static CompletableFuture<Optional<Profile>> profile(UUID uuid) {
		return Http.get(profileUrl(uuid), "text/html").thenApply(r -> {
			if (r.status() == 404) return Optional.empty();
			if (r.status() != 200) throw new IllegalStateException("leonemc.net answered " + r.status());
			return Optional.of(parseProfile(uuid, r.text()));
		});
	}

	/** Players whose name starts with {@code query}, as the website's search box suggests them. */
	public static CompletableFuture<List<Player>> search(String query) {
		String url = LeoneMC.WEBSITE + "/player/search/suggestions?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
		return Http.get(url, "application/json").thenApply(r -> {
			if (r.status() != 200) throw new IllegalStateException("leonemc.net answered " + r.status());
			List<Player> out = new ArrayList<>();
			JsonElement root = JsonParser.parseString(r.text());
			if (!root.isJsonArray()) return out;
			for (JsonElement e : root.getAsJsonArray()) {
				if (!e.isJsonObject()) continue;
				JsonObject o = e.getAsJsonObject();
				try {
					out.add(new Player(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString()));
				} catch (RuntimeException ignored) {
				}
			}
			return out;
		});
	}

	/** Finds the best match for a username: an exact match if there is one, otherwise the first suggestion. */
	public static CompletableFuture<Optional<Player>> lookup(String name) {
		return search(name).thenApply(list -> {
			for (Player p : list) if (p.name().equals(name)) return Optional.of(p);
			for (Player p : list) if (p.name().equalsIgnoreCase(name)) return Optional.of(p);
			return list.isEmpty() ? Optional.<Player>empty() : Optional.of(list.getFirst());
		});
	}

	static Profile parseProfile(UUID uuid, String html) {
		String name = "";
		int color = 0xFFFFFF;
		Matcher m = NAME.matcher(html);
		if (m.find()) {
			name = unescape(m.group(2)).strip();
			color = hex(m.group(1), 0xFFFFFF);
		}
		String rank = "";
		int rankColor = 0x888888;
		m = RANK.matcher(html);
		if (m.find()) {
			rank = unescape(m.group(2)).strip();
			rankColor = hex(m.group(1), 0x888888);
		}
		List<Friend> friends = new ArrayList<>();
		m = FRIEND.matcher(html);
		while (m.find()) {
			UUID id;
			try {
				id = UUID.fromString(m.group(1));
			} catch (IllegalArgumentException e) {
				continue;
			}
			String classes = m.group(2), body = m.group(3);
			String fname = null;
			int fcolor = 0xFFFFFF;
			Matcher n = FRIEND_NAME.matcher(body);
			if (n.find()) {
				fname = unescape(n.group(2)).strip();
				fcolor = hex(n.group(1), 0xFFFFFF);
			}
			if (fname == null || fname.isEmpty()) {
				Matcher a = ALT.matcher(body);
				if (!a.find()) continue;
				fname = unescape(a.group(1)).strip();
			}
			Matcher s = FRIEND_STATUS.matcher(body);
			String status = s.find() ? unescape(s.group(1)).strip() : "";
			String lower = status.toLowerCase(Locale.ROOT);
			boolean online = classes.contains("online") && !classes.contains("offline") || lower.startsWith("online");
			friends.add(new Friend(id, fname, fcolor, online, status));
		}
		return new Profile(uuid, name, color, rank, rankColor, friends);
	}

	static int hex(@Nullable String s, int fallback) {
		if (s == null) return fallback;
		String h = s.startsWith("#") ? s.substring(1) : s;
		try {
			if (h.length() == 3) {
				h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
			}
			if (h.length() == 8) h = h.substring(0, 6);
			if (h.length() != 6) return fallback;
			return Integer.parseInt(h, 16);
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	static String unescape(String s) {
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
