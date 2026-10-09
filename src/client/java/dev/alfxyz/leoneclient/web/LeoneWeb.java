package dev.alfxyz.leoneclient.web;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alfxyz.leoneclient.LeoneMC;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jspecify.annotations.Nullable;

/**
 * Reads player profiles from leonemc.net. Fetching a page does not count as a
 * profile view; the site counts views with a separate request from its own script.
 */
public final class LeoneWeb {
	private static final String HEX = "(#[0-9A-Fa-f]{3,8})";
	private static final Pattern NAME = Pattern.compile("class=\"player-name\"(?:\\s+style=\"color:\\s*" + HEX + "[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern RANK = Pattern.compile("class=\"player-rank-badge\"(?:\\s+style=\"background-color:\\s*" + HEX + "[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern PRESENCE = Pattern.compile("<div class=\"player-presence([^\"]*)\">(.*?)</div>", Pattern.DOTALL);
	private static final Pattern ONLINE_ON = Pattern.compile("Online on\\s*<strong>([^<]*)</strong>");
	private static final Pattern TIMESTAMP = Pattern.compile("data-timestamp=\"([^\"]+)\"");
	private static final Pattern JOINED = Pattern.compile("class=\"player-joined\">\\s*Joined\\s*<span data-date=\"([^\"]+)\"");
	private static final Pattern PLAYTIME = Pattern.compile("data-minutes-played\\s+data-millis=\"(\\d+)\"");
	private static final Pattern VIEWS = Pattern.compile("data-profile-views>(\\d+)<");
	private static final Pattern CARD = Pattern.compile("<div class=\"player-stat-card\">(.*?)</ul>", Pattern.DOTALL);
	private static final Pattern CARD_TITLE = Pattern.compile("player-stat-card-header\">\\s*<span>([^<]*)</span>");
	private static final Pattern STAT = Pattern.compile("<li>(.*?)</li>", Pattern.DOTALL);
	private static final Pattern STAT_LABEL = Pattern.compile("player-stat-label\">([^<]*)<");
	private static final Pattern STAT_RANK = Pattern.compile("player-stat-rank\">([^<]*)<");
	private static final Pattern STAT_VALUE = Pattern.compile("player-stat-badge\"(?:\\s+style=\"color:\\s*" + HEX + "[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern FRIEND = Pattern.compile(
		"<a\\s+href=\"/player/([0-9a-fA-F-]{36})\"\\s+class=\"friend-head([^\"]*)\"\\s*>(.*?)</a>", Pattern.DOTALL);
	private static final Pattern FRIEND_NAME = Pattern.compile("friend-head-tooltip-name\"(?:\\s+style=\"color:\\s*" + HEX + "[^\"]*\")?\\s*>([^<]*)<");
	private static final Pattern FRIEND_STATUS = Pattern.compile("friend-head-tooltip-status\"\\s*>([^<]*)<");
	private static final Pattern ALT = Pattern.compile("alt=\"([^\"]*)\"");
	private static final Pattern ENTITY = Pattern.compile("&(#\\d+|#x[0-9a-fA-F]+|amp|lt|gt|quot|apos);");

	private LeoneWeb() {
	}

	public record Friend(UUID uuid, String name, int color, boolean online, String status) {
	}

	public record Stat(String label, String rank, String value, int color) {
	}

	public record StatCard(String title, List<Stat> stats) {
	}

	/**
	 * A player's profile. {@code server} is set while online (when the site knows it),
	 * {@code lastSeen} while offline; both in UTC milliseconds or 0 when unknown.
	 */
	public record Profile(UUID uuid, String name, int color, String rank, int rankColor, List<Friend> friends,
		boolean online, @Nullable String server, long lastSeen, long joined, long playtimeMs, int views, List<StatCard> stats, long fetched) {
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

	private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");

	/**
	 * Finds a player by name. The website can list someone under a different name from their Minecraft
	 * one (an older name, or a name LeoneMC gives them), so after its exact matches this tries the
	 * players on this server and Mojang's name lookup, and only then the website's first suggestion.
	 * When several players share a name, yourself or someone on this server wins.
	 */
	public static CompletableFuture<Optional<Player>> lookup(String name) {
		// read on the calling (render) thread, before the request leaves it
		Set<UUID> preferred = new HashSet<>();
		Player here = null;
		Minecraft mc = Minecraft.getInstance();
		preferred.add(mc.getUser().getProfileId());
		if (mc.getConnection() != null) {
			for (PlayerInfo info : mc.getConnection().getOnlinePlayers()) {
				preferred.add(info.getProfile().id());
				if (info.getProfile().name().equalsIgnoreCase(name)) here = new Player(info.getProfile().id(), info.getProfile().name());
			}
		}
		if (mc.getUser().getName().equalsIgnoreCase(name)) here = new Player(mc.getUser().getProfileId(), mc.getUser().getName());
		Player onServer = here;
		return search(name).thenCompose(list -> {
			List<Player> exact = new ArrayList<>();
			for (Player p : list) if (p.name().equalsIgnoreCase(name)) exact.add(p);
			for (Player p : exact) if (preferred.contains(p.uuid())) return CompletableFuture.completedFuture(Optional.of(p));
			if (!exact.isEmpty()) return CompletableFuture.completedFuture(Optional.of(exact.getFirst()));
			if (onServer != null) return CompletableFuture.completedFuture(Optional.of(onServer));
			return mojang(name).thenApply(found -> found.isPresent() || list.isEmpty() ? found : Optional.of(list.getFirst()));
		});
	}

	/** The account that has this Minecraft name right now, from Mojang's public lookup. */
	private static CompletableFuture<Optional<Player>> mojang(String name) {
		if (!USERNAME.matcher(name).matches()) return CompletableFuture.completedFuture(Optional.empty());
		return Http.get("https://api.mojang.com/users/profiles/minecraft/" + name, "application/json").handle((r, err) -> {
			if (err != null || r.status() != 200) return Optional.<Player>empty();
			try {
				JsonObject o = JsonParser.parseString(r.text()).getAsJsonObject();
				String id = o.get("id").getAsString();
				UUID uuid = new UUID(Long.parseUnsignedLong(id.substring(0, 16), 16), Long.parseUnsignedLong(id.substring(16, 32), 16));
				return Optional.of(new Player(uuid, o.get("name").getAsString()));
			} catch (RuntimeException e) {
				return Optional.<Player>empty();
			}
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
		boolean online = false;
		String server = null;
		long lastSeen = 0;
		m = PRESENCE.matcher(html);
		if (m.find()) {
			online = m.group(1).contains("online");
			String body = m.group(2);
			Matcher on = ONLINE_ON.matcher(body);
			if (on.find()) server = unescape(on.group(1)).strip();
			Matcher ts = TIMESTAMP.matcher(body);
			if (!online && ts.find()) lastSeen = instant(ts.group(1));
		}
		m = JOINED.matcher(html);
		long joined = m.find() ? instant(m.group(1)) : 0;
		m = PLAYTIME.matcher(html);
		long playtime = m.find() ? Long.parseLong(m.group(1)) : 0;
		m = VIEWS.matcher(html);
		int views = m.find() ? Integer.parseInt(m.group(1)) : 0;
		return new Profile(uuid, name, color, rank, rankColor, friends(html), online, server, lastSeen, joined, playtime, views, stats(html),
			System.currentTimeMillis());
	}

	private static List<StatCard> stats(String html) {
		List<StatCard> cards = new ArrayList<>();
		Matcher card = CARD.matcher(html);
		while (card.find()) {
			String body = card.group(1);
			Matcher t = CARD_TITLE.matcher(body);
			if (!t.find()) continue;
			List<Stat> stats = new ArrayList<>();
			Matcher li = STAT.matcher(body);
			while (li.find()) {
				String item = li.group(1);
				Matcher label = STAT_LABEL.matcher(item), rank = STAT_RANK.matcher(item), value = STAT_VALUE.matcher(item);
				if (!label.find() || !value.find()) continue;
				stats.add(new Stat(unescape(label.group(1)).strip(), rank.find() ? unescape(rank.group(1)).strip() : "",
					unescape(value.group(2)).strip(), hex(value.group(1), 0xEDEDED)));
			}
			if (!stats.isEmpty()) cards.add(new StatCard(unescape(t.group(1)).strip(), stats));
		}
		return cards;
	}

	private static List<Friend> friends(String html) {
		List<Friend> friends = new ArrayList<>();
		Matcher m = FRIEND.matcher(html);
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
		return friends;
	}

	private static long instant(String iso) {
		try {
			return Instant.parse(iso).toEpochMilli();
		} catch (RuntimeException e) {
			return 0;
		}
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
