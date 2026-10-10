package dev.alfxyz.leoneclient.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * The logged-in player's own LeoneMC profile: their name on LeoneMC and their
 * rank, which decides whether the Staff category is shown. The last answer is
 * kept on disk so the category is there before the website replies.
 */
public final class Account {
	/** LeoneMC's staff ranks, as leonemc.net/staff names them (and their short forms). */
	private static final Set<String> STAFF_RANKS = Set.of("trainee", "helper", "junior mod", "jr mod", "jr. mod", "mod", "moderator",
		"senior mod", "sr mod", "sr. mod", "admin", "administrator", "developer", "dev", "manager", "executive", "owner", "builder");
	private static final long REFRESH_MS = 10 * 60_000;

	private static @Nullable UUID uuid;
	private static String name = "";
	private static String rank = "";
	private static int rankColor = 0x888888;
	private static long checked;
	private static boolean loading;

	private Account() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient").resolve("account.json");
	}

	private static UUID self() {
		return Minecraft.getInstance().getUser().getProfileId();
	}

	/** The rank on LeoneMC, for example "Junior Mod", or empty when not known. */
	public static String rank() {
		return isSelf() ? rank : "";
	}

	public static int rankColor() {
		return rankColor;
	}

	/** The player's name as LeoneMC shows it (it can differ from the Minecraft name), or null. */
	public static @Nullable String name() {
		return isSelf() && !name.isEmpty() ? name : null;
	}

	private static boolean isSelf() {
		return uuid != null && uuid.equals(self());
	}

	/** True for LeoneMC staff: their rank is one of the staff ranks. */
	public static boolean staff() {
		if (Boolean.getBoolean("leoneclient.staff")) return true;
		return STAFF_RANKS.contains(rank().toLowerCase(Locale.ROOT).strip());
	}

	/** Fetches the player's own profile unless it was checked recently. */
	public static void refresh(boolean force) {
		if (loading || !force && isSelf() && System.currentTimeMillis() - checked < REFRESH_MS) return;
		loading = true;
		UUID me = self();
		LeoneWeb.profile(me, false).whenComplete((result, err) -> Minecraft.getInstance().execute(() -> {
			loading = false;
			if (err != null) return;
			uuid = me;
			checked = System.currentTimeMillis();
			if (result.isPresent()) {
				Profiles.put(result.get());
				name = result.get().name();
				rank = result.get().rank();
				rankColor = result.get().rankColor();
			} else {
				name = "";
				rank = "";
			}
			save();
		}));
	}

	public static void load() {
		try {
			if (!Files.isRegularFile(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
			uuid = UUID.fromString(o.get("uuid").getAsString());
			name = o.get("name").getAsString();
			rank = o.get("rank").getAsString();
			rankColor = o.get("rankColor").getAsInt();
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not read {}", file(), e);
		}
	}

	private static void save() {
		if (uuid == null) return;
		JsonObject o = new JsonObject();
		o.addProperty("uuid", uuid.toString());
		o.addProperty("name", name);
		o.addProperty("rank", rank);
		o.addProperty("rankColor", rankColor);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), o.toString());
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not write {}", file(), e);
		}
	}
}
