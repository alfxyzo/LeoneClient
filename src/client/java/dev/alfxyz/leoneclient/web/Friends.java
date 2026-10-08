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
	/** Profile pages count views, so the list is fetched sparingly. */
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
		return profile != null && !customAccount() ? profile.name() : null;
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

	public static @Nullable Profile profile() {
		return profile;
	}

	public static List<Friend> list() {
		return profile == null ? List.of() : profile.friends();
	}

	public static @Nullable Friend byName(String name) {
		for (Friend f : list()) if (f.name().equalsIgnoreCase(name)) return f;
		return null;
	}

	public static boolean isFriend(String name) {
		return byName(name) != null;
	}

	/** Online according to the latest chat message about them, or else the website. */
	public static boolean online(Friend f) {
		Location l = location(f.name());
		if (l != null && l.at() > updated) return l.online();
		return f.online();
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
		if (!force && (now - Math.max(updated, lastAttempt) < MIN_REFRESH_MS)) return;
		lastAttempt = now;
		state = State.LOADING;
		UUID uuid = accountUuid();
		LeoneWeb.profile(uuid).whenComplete((result, err) -> Minecraft.getInstance().execute(() -> {
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
		profile = result.get();
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

	/** Names of friends last seen joining {@code server}. */
	public static List<Friend> on(String server) {
		List<Friend> out = new ArrayList<>();
		for (Friend f : list()) {
			Location l = location(f.name());
			if (l != null && l.online() && l.server().equalsIgnoreCase(server)) out.add(f);
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
					pr.get("rank").getAsString(), pr.get("rankColor").getAsInt(), list);
				updated = pr.get("updated").getAsLong();
				state = State.READY;
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
