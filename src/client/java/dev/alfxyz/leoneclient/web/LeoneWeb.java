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
 * Reads players from LeoneMC's public API (leonemc.net/api/v1): who they are, their rank, whether and
 * where they are online, their friends and their statistics. What the API does not have yet (name
 * colours, network playtime, profile views, stat value colours) still comes from the player's page,
 * and name suggestions from the website's search box. The API allows 120 requests a minute from
 * each player's computer; a 429 pauses further calls for as long as it says.
 */
public final class LeoneWeb {
	private static final String API = LeoneMC.WEBSITE + "/api/v1";
	/** After a 429, no API calls until this time (epoch ms). */
	private static volatile long pausedUntil;

	private LeoneWeb() {
	}

	public record Friend(UUID uuid, String name, int color, boolean online, String status) {
	}

	public record Stat(String label, String rank, String value, int color) {
	}

	public record StatCard(String title, List<Stat> stats) {
	}

	/**
	 * A player's profile. {@code server} is set while online (when known), {@code lastSeen} while offline;
	 * both in UTC milliseconds or 0 when unknown. {@code statsLoaded} says whether {@code stats} was asked
	 * for, and {@code pageLoaded} whether the page's extras were (colours, playtime, views): a profile
	 * fetched only for someone's presence leaves both out.
	 */
	public record Profile(UUID uuid, String name, int color, String rank, int rankColor, List<Friend> friends,
		boolean online, @Nullable String server, long lastSeen, long joined, long playtimeMs, int views, List<StatCard> stats, long fetched,
		boolean statsLoaded, boolean pageLoaded) {
	}

	public record Player(UUID uuid, String name) {
	}

	public static String profileUrl(UUID uuid) {
		return LeoneMC.WEBSITE + "/player/" + uuid;
	}

	/** Fetches everything about a player: the API's profile and statistics, and the page's extras. Empty when they never joined. */
	public static CompletableFuture<Optional<Profile>> profile(UUID uuid) {
		return profile(uuid, true, true);
	}

	/**
	 * Fetches a profile from the API, with or without its statistics, and with or without the extras only
	 * the player's page has (name colours, playtime, views, stat colours). The page is a bonus: if it fails,
	 * the API's answer is used on its own. Empty when the player has never joined.
	 */
	public static CompletableFuture<Optional<Profile>> profile(UUID uuid, boolean withStats, boolean withPage) {
		CompletableFuture<Http.Response> player = api("/players/" + uuid);
		CompletableFuture<Http.@Nullable Response> stats = withStats ? api("/players/" + uuid + "/statistics") : CompletableFuture.completedFuture(null);
		CompletableFuture<ProfilePage.@Nullable Extras> page = withPage
			? Http.get(profileUrl(uuid), "text/html").handle((r, err) -> err != null || r.status() != 200 ? null : ProfilePage.read(r.text()))
			: CompletableFuture.completedFuture(null);
		return player.thenCombine(stats, (p, st) -> {
			if (p.status() == 404) return Optional.<Profile>empty();
			check(p);
			JsonObject o = JsonParser.parseString(p.text()).getAsJsonObject();
			List<StatCard> cards = new ArrayList<>();
			if (st != null && st.status() == 200) cards = statCards(JsonParser.parseString(st.text()).getAsJsonObject());
			return Optional.of(parsePlayer(uuid, o, cards, st != null && st.status() == 200));
		}).thenCombine(page, (found, extras) -> found.map(p -> extras == null ? p : withExtras(p, extras)));
	}

	/** The API's profile with the page's colours, playtime and views added. */
	private static Profile withExtras(Profile p, ProfilePage.Extras x) {
		List<Friend> friends = new ArrayList<>();
		for (Friend f : p.friends()) friends.add(new Friend(f.uuid(), f.name(), x.friendColours().getOrDefault(f.uuid(), f.color()), f.online(), f.status()));
		List<StatCard> cards = new ArrayList<>();
		for (StatCard c : p.stats()) {
			List<Stat> stats = new ArrayList<>();
			for (Stat s : c.stats()) {
				Integer colour = x.statColour(c.title(), s.label(), s.value());
				stats.add(colour == null ? s : new Stat(s.label(), s.rank(), s.value(), colour));
			}
			cards.add(new StatCard(c.title(), stats));
		}
		int color = x.nameColour() != null ? x.nameColour() : p.color();
		return new Profile(p.uuid(), p.name(), color, p.rank(), p.rankColor(), friends, p.online(), p.server(), p.lastSeen(), p.joined(),
			x.playtimeMs(), x.views(), cards, p.fetched(), p.statsLoaded(), true);
	}

	private static CompletableFuture<Http.Response> api(String path) {
		if (System.currentTimeMillis() < pausedUntil) return CompletableFuture.failedFuture(new IllegalStateException("LeoneMC's API asked us to wait"));
		return Http.get(API + path, "application/json").thenApply(r -> {
			if (r.status() == 429) pausedUntil = System.currentTimeMillis() + Math.max(5, r.retryAfter()) * 1000;
			return r;
		});
	}

	/** Throws for anything but a 200, with the API's own message when it gives one. */
	private static void check(Http.Response r) {
		if (r.status() == 200) return;
		String message = "leonemc.net answered " + r.status();
		try {
			JsonObject e = JsonParser.parseString(r.text()).getAsJsonObject();
			if (e.has("message")) message = e.get("message").getAsString();
		} catch (RuntimeException ignored) {
		}
		throw new IllegalStateException(message);
	}

	private static Profile parsePlayer(UUID uuid, JsonObject o, List<StatCard> stats, boolean statsLoaded) {
		String name = str(o, "name", "");
		String rank = "";
		int rankColor = 0x888888;
		if (o.has("rank") && o.get("rank").isJsonObject()) {
			JsonObject r = o.getAsJsonObject("rank");
			rank = str(r, "name", "");
			rankColor = hex(str(r, "color", null), 0x888888);
		}
		// the API has no name colour of its own; on LeoneMC a name takes its rank's colour
		int color = rank.isEmpty() ? 0xFFFFFF : rankColor;
		boolean online = o.has("online") && !o.get("online").isJsonNull() && o.get("online").getAsBoolean();
		String server = str(o, "server", null);
		List<Friend> friends = new ArrayList<>();
		if (o.has("friends") && o.get("friends").isJsonArray()) {
			for (JsonElement e : o.getAsJsonArray("friends")) {
				if (!e.isJsonObject()) continue;
				JsonObject f = e.getAsJsonObject();
				try {
					boolean on = f.has("online") && f.get("online").getAsBoolean();
					friends.add(new Friend(UUID.fromString(f.get("uuid").getAsString()), f.get("name").getAsString(), 0xFFFFFF, on, on ? "Online" : "Offline"));
				} catch (RuntimeException ignored) {
				}
			}
		}
		return new Profile(uuid, name, color, rank, rankColor, friends, online, online ? server : null, time(str(o, "lastSeen", null)),
			time(str(o, "firstJoin", null)), 0, 0, stats, System.currentTimeMillis(), statsLoaded, false);
	}

	/** One card per server, in the API's order, each stat with its place on that server's leaderboard. */
	private static List<StatCard> statCards(JsonObject o) {
		List<StatCard> cards = new ArrayList<>();
		if (!o.has("statistics") || !o.get("statistics").isJsonArray()) return cards;
		String current = null;
		List<Stat> stats = null;
		java.text.NumberFormat places = java.text.NumberFormat.getIntegerInstance(Locale.UK);
		for (JsonElement e : o.getAsJsonArray("statistics")) {
			if (!e.isJsonObject()) continue;
			JsonObject s = e.getAsJsonObject();
			String server = str(s, "server", "");
			if (!server.equals(current)) {
				current = server;
				stats = new ArrayList<>();
				cards.add(new StatCard(server, stats));
			}
			String rank = s.has("rank") && !s.get("rank").isJsonNull() ? "#" + places.format(s.get("rank").getAsLong()) : "";
			stats.add(new Stat(str(s, "name", ""), rank, str(s, "display", ""), 0xFFFFFF));
		}
		return cards;
	}

	private static @Nullable String str(JsonObject o, String key, @Nullable String fallback) {
		return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : fallback;
	}

	private static long time(@Nullable String iso) {
		if (iso == null || iso.isBlank()) return 0;
		try {
			return Instant.parse(iso).toEpochMilli();
		} catch (RuntimeException e) {
			return 0;
		}
	}

	private static int hex(@Nullable String s, int fallback) {
		if (s == null) return fallback;
		String h = s.strip();
		if (h.startsWith("#")) h = h.substring(1);
		if (h.length() == 3) h = "" + h.charAt(0) + h.charAt(0) + h.charAt(1) + h.charAt(1) + h.charAt(2) + h.charAt(2);
		if (h.length() == 8) h = h.substring(0, 6);
		try {
			return h.length() == 6 ? Integer.parseInt(h, 16) : fallback;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

	/** The player with exactly this name (any case) on LeoneMC, from the API; empty when nobody has it. */
	private static CompletableFuture<Optional<Player>> byExactName(String name) {
		if (!USERNAME.matcher(name).matches()) return CompletableFuture.completedFuture(Optional.empty());
		return api("/players/" + name).handle((r, err) -> {
			if (err != null || r.status() != 200) return Optional.<Player>empty();
			try {
				JsonObject o = JsonParser.parseString(r.text()).getAsJsonObject();
				return Optional.of(new Player(UUID.fromString(o.get("uuid").getAsString()), o.get("name").getAsString()));
			} catch (RuntimeException e) {
				return Optional.<Player>empty();
			}
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
			return byExactName(name).thenCompose(api -> api.isPresent() ? CompletableFuture.completedFuture(api)
				: mojang(name).thenApply(found -> found.isPresent() || list.isEmpty() ? found : Optional.of(list.getFirst())));
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
}
