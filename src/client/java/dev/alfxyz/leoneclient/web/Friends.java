package dev.alfxyz.leoneclient.web;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.web.LeoneWeb.Friend;
import dev.alfxyz.leoneclient.web.LeoneWeb.Player;
import dev.alfxyz.leoneclient.web.LeoneWeb.Profile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The player's LeoneMC friends list, read from their profile on leonemc.net and
 * cached on disk, plus where each friend was last seen according to LeoneMC's
 * "Friends | X has joined the server Y." chat messages.
 */
public final class Friends {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	/** The list changes rarely, so it is fetched at most this often unless asked for. */
	private static final long MIN_REFRESH_MS = 90_000;
	private static final long LOCATION_TTL_MS = 6 * 3600_000L;

	public enum State { IDLE, LOADING, READY, NOT_FOUND, ERROR }

	public record Location(String server, boolean online, long at) {
	}

	private static @Nullable Player account;
	private static @Nullable Profile profile;
	private static long updated;
	private static volatile State state = State.IDLE;
	private static String error = "";
	private static long lastAttempt;
	private static final Map<String, Location> locations = new ConcurrentHashMap<>();

	private Friends() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient").resolve("friends.json");
	}

	// ----------------------------------------------------------------- account

	/** The account whose friends are shown: the one chosen in the Friends page, or the logged-in player. */
	public static UUID accountUuid() {
		return account != null ? account.uuid() : Minecraft.getInstance().getUser().getProfileId();
	}

	public static String accountName() {
		if (account != null) return account.name();
		if (profile != null && profile.uuid().equals(accountUuid()) && !profile.name().isEmpty()) return profile.name();
		return Minecraft.getInstance().getUser().getName();
	}

	public static boolean customAccount() {
		return account != null;
	}

	/** The player's name as LeoneMC shows it, which can differ from the Minecraft name. */
	public static @Nullable String displayName() {
		return Account.name();
	}

	/** Shows another player's friends (or the logged-in player's when null) and fetches them. */
	public static void setAccount(@Nullable Player player) {
		account = player;
		profile = null;
		updated = 0;
		state = State.IDLE;
		save();
		refresh(true);
	}

	/** Looks a username up on leonemc.net and switches to it. Reports a problem through {@code onError}. */
	public static void chooseAccount(String name, Consumer<String> onError) {
		LeoneWeb.lookup(name).whenComplete((found, err) -> Minecraft.getInstance().execute(() -> {
			if (err != null) onError.accept("Could not reach leonemc.net");
			else if (found.isEmpty()) onError.accept("No LeoneMC player called " + name);
			else setAccount(found.get());
		}));
	}

	// ------------------------------------------------------------------- data

	public static State state() {
		return state;
	}

	public static String error() {
		return error;
	}

	public static long updated() {
		return updated;
	}

	/** The profile whose friends are shown; never a cached one from another account on this computer. */
	public static @Nullable Profile profile() {
		return profile != null && profile.uuid().equals(accountUuid()) ? profile : null;
	}

	public static List<Friend> list() {
		Profile p = profile();
		return p == null ? List.of() : p.friends();
	}

	/**
	 * A friend by the name chat shows. Never one of your own names: a friend can share your LeoneMC name
	 * (an old account of yours, say), and in chat that name is always you.
	 */
	public static @Nullable Friend byName(String name) {
		if (isYourName(name)) return null;
		for (Friend f : list()) if (f.name().equalsIgnoreCase(name)) return f;
		return null;
	}

	/** Your Minecraft name or the name LeoneMC shows for you. */
	public static boolean isYourName(String name) {
		if (name.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) return true;
		String shown = Account.name();
		return shown != null && name.equalsIgnoreCase(shown);
	}

	public static boolean isFriend(String name) {
		return byName(name) != null;
	}

	/** By UUID, for players the website shows, since two players can share a name there. */
	public static boolean isFriend(UUID uuid) {
		for (Friend f : list()) if (f.uuid().equals(uuid)) return true;
		return false;
	}

	/** A friend's name colour: their rank's colour from their own profile once loaded, else the one known before. */
	public static int color(Friend f) {
		Profile p = Profiles.get(f.uuid());
		return p != null ? p.color() : f.color();
	}

	/** Without the page's colours (it did not load), each friend keeps the one known from before (their own profile, or saved). */
	private static Profile withColours(Profile fresh) {
		if (fresh.pageLoaded()) return fresh;
		java.util.Map<UUID, Integer> known = new java.util.HashMap<>();
		if (profile != null) for (Friend f : profile.friends()) known.put(f.uuid(), f.color());
		List<Friend> friends = new ArrayList<>();
		for (Friend f : fresh.friends()) {
			Profile own = Profiles.get(f.uuid());
			int c = own != null ? own.color() : known.getOrDefault(f.uuid(), f.color());
			friends.add(new Friend(f.uuid(), f.name(), c, f.online(), f.status()));
		}
		return new Profile(fresh.uuid(), fresh.name(), fresh.color(), fresh.rank(), fresh.rankColor(), friends, fresh.online(), fresh.server(),
			fresh.lastSeen(), fresh.joined(), fresh.playtimeMs(), fresh.views(), fresh.stats(), fresh.fetched(), fresh.statsLoaded(), false);
	}

	/** Online according to whichever is newest: their own profile page, a chat message about them, or the friends list. */
	public static boolean online(Friend f) {
		Profile p = Profiles.get(f.uuid());
		Location l = location(f.name());
		long listAt = updated;
		if (p != null && p.fetched() >= listAt && (l == null || p.fetched() >= l.at())) return p.online();
		if (l != null && l.at() > listAt) return l.online();
		return f.online();
	}

	/** The server an online friend is on, if known: from their profile page or a recent chat message. */
	public static @Nullable String server(Friend f) {
		Profile p = Profiles.get(f.uuid());
		Location l = location(f.name());
		if (l != null && l.online() && (p == null || l.at() > p.fetched())) return l.server();
		if (p != null && p.online()) return p.server();
		return l != null && l.online() ? l.server() : null;
	}

	/** When an offline friend was last online (UTC ms), or 0 if unknown. */
	public static long lastSeen(Friend f) {
		Profile p = Profiles.get(f.uuid());
		Location l = location(f.name());
		long seen = p != null && !p.online() ? p.lastSeen() : 0;
		if (l != null && !l.online()) seen = Math.max(seen, l.at());
		return seen;
	}

	/** Keeps every friend's own profile fresh enough to show where they are or when they were last on. */
	public static void wantPresence() {
		for (Friend f : list()) Profiles.want(f.uuid(), online(f) ? 60_000 : 5 * 60_000, false);
	}

	/** Friends sorted online first, then by name. */
	public static List<Friend> sorted() {
		List<Friend> out = new ArrayList<>(list());
		out.sort(Comparator.comparing((Friend f) -> !online(f)).thenComparing(f -> f.name().toLowerCase(Locale.ROOT)));
		return out;
	}

	public static int onlineCount() {
		int n = 0;
		for (Friend f : list()) if (online(f)) n++;
		return n;
	}

	/** Fetches the list unless it was fetched recently. */
	public static void refresh(boolean force) {
		if (state == State.LOADING) return;
		long now = System.currentTimeMillis();
		// with nothing to show for this account yet, try again sooner (but not on every frame after a failure)
		boolean have = profile() != null;
		long since = now - (have ? Math.max(updated, lastAttempt) : lastAttempt);
		if (!force && since < (have ? MIN_REFRESH_MS : 15_000)) return;
		lastAttempt = now;
		state = State.LOADING;
		UUID uuid = accountUuid();
		// the friends list from the API, and their name colours from the page
		LeoneWeb.profile(uuid, false, true).whenComplete((result, err) -> Minecraft.getInstance().execute(() -> {
			if (!uuid.equals(accountUuid())) {
				state = State.IDLE;
				return;
			}
			if (err != null) {
				LOGGER.warn("Leone Client: could not load friends for {}", uuid, err);
				error = "Could not reach leonemc.net";
				state = State.ERROR;
				return;
			}
			apply(result);
		}));
	}

	private static void apply(Optional<Profile> result) {
		if (result.isEmpty()) {
			profile = null;
			state = State.NOT_FOUND;
			return;
		}
		profile = withColours(result.get());
		Profiles.put(profile);
		// show the account under the name the website uses, which can differ from the one typed
		if (account != null && account.uuid().equals(profile.uuid()) && !profile.name().isEmpty()) account = new Player(account.uuid(), profile.name());
		updated = System.currentTimeMillis();
		state = State.READY;
		save();
	}

	// -------------------------------------------------------------- locations

	/** Records a "has joined / has left the server" message about a friend. */
	public static void seen(String name, boolean joined, String server) {
		locations.put(name.toLowerCase(Locale.ROOT), new Location(server, joined, System.currentTimeMillis()));
	}

	public static @Nullable Location location(String name) {
		Location l = locations.get(name.toLowerCase(Locale.ROOT));
		if (l == null || System.currentTimeMillis() - l.at() > LOCATION_TTL_MS) return null;
		return l;
	}

	/** Friends currently on {@code server}. */
	public static List<Friend> on(String server) {
		List<Friend> out = new ArrayList<>();
		for (Friend f : list()) {
			String at = online(f) ? server(f) : null;
			if (at != null && at.equalsIgnoreCase(server)) out.add(f);
		}
		return out;
	}

	// -------------------------------------------------------------------- disk

	public static void load() {
		Path p = file();
		if (!Files.isRegularFile(p)) return;
		try {
			JsonElement root = JsonParser.parseString(Files.readString(p));
			if (!root.isJsonObject()) return; // an older format: a plain list of names
			JsonObject o = root.getAsJsonObject();
			if (o.has("account") && o.get("account").isJsonObject()) {
				JsonObject a = o.getAsJsonObject("account");
				account = new Player(UUID.fromString(a.get("uuid").getAsString()), a.get("name").getAsString());
			}
			if (o.has("profile") && o.get("profile").isJsonObject()) {
				JsonObject pr = o.getAsJsonObject("profile");
				List<Friend> list = new ArrayList<>();
				for (JsonElement e : pr.getAsJsonArray("friends")) {
					JsonObject f = e.getAsJsonObject();
					list.add(new Friend(UUID.fromString(f.get("uuid").getAsString()), f.get("name").getAsString(), f.get("color").getAsInt(), false, "Offline"));
				}
				profile = new Profile(UUID.fromString(pr.get("uuid").getAsString()), pr.get("name").getAsString(), pr.get("color").getAsInt(),
					pr.get("rank").getAsString(), pr.get("rankColor").getAsInt(), list, false, null, 0, 0, 0, 0, List.of(), 0, false, true);
				updated = pr.get("updated").getAsLong();
				state = profile() != null ? State.READY : State.IDLE;
			}
		} catch (Exception e) {
			LOGGER.warn("Leone Client: could not read {}", p, e);
		}
	}

	private static void save() {
		JsonObject root = new JsonObject();
		if (account != null) {
			JsonObject a = new JsonObject();
			a.addProperty("uuid", account.uuid().toString());
			a.addProperty("name", account.name());
			root.add("account", a);
		}
		if (profile != null) {
			JsonObject pr = new JsonObject();
			pr.addProperty("uuid", profile.uuid().toString());
			pr.addProperty("name", profile.name());
			pr.addProperty("color", profile.color());
			pr.addProperty("rank", profile.rank());
			pr.addProperty("rankColor", profile.rankColor());
			pr.addProperty("updated", updated);
			JsonArray arr = new JsonArray();
			for (Friend f : profile.friends()) {
				JsonObject o = new JsonObject();
				o.addProperty("uuid", f.uuid().toString());
				o.addProperty("name", f.name());
				o.addProperty("color", f.color());
				arr.add(o);
			}
			pr.add("friends", arr);
			root.add("profile", pr);
		}
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), GSON.toJson(root));
		} catch (Exception e) {
			LOGGER.warn("Leone Client: could not write {}", file(), e);
		}
	}
}
