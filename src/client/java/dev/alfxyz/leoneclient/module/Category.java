package dev.alfxyz.leoneclient.module;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Account;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/**
 * The wheel segments, clockwise from the top. After the general ones come LeoneMC's servers: only the
 * one you are on is shown, and its modules only run there (each keeps its on or off for when you are
 * back).
 */
public enum Category {
	COMBAT("Combat", Icons.COMBAT),
	CHAT("Chat", Icons.CHAT),
	FRIENDS("Friends", Icons.FRIENDS),
	HUD("HUD", Icons.MONITOR),
	SERVER("Server", Icons.SERVER),
	CLIENT("Client", Icons.INTERNALS),
	/** Only shown to LeoneMC staff. */
	STAFF("Staff", Icons.SHIELD_CHECK),

	ELYTRABOX("ElytraBox", Icons.FEATHER,
		"Team PvP flown on elytras. Deposit feathers, earn shards for playing, build on your plot, mine in private mines, "
			+ "and fight over supply drops, KOTH, envoys and the Target with custom items like the Cage and the Cobweb Circle.",
		"elytrabox"),
	WILDKITS("WildKits", Icons.SWORDS,
		"Kit PvP with a token shop for totems, ender chests and kits, quests, coinflips, envoys, events and regular map resets.",
		"wildkits", "randomkits"),
	CORERAIDING("CoreRaiding", Icons.SHIELD,
		"Place your core, wall it in and wax your blocks, then raid other players' cores with bombs. "
			+ "Newbie protection, raid points, breakables, KOTL, envoys and a tutorial.",
		"coreraiding"),
	INSANEKITS("InsaneKits", Icons.FLAME,
		"Kit PvP with a shard shop, supply drops in the PvP arena, quests, auctions and map resets.",
		"insanekits"),
	LIFESTEAL("Lifesteal", Icons.HEART,
		"Every kill takes a heart from the player you beat. Duels are played in rounds.",
		"lifesteal"),
	GENS("Gens", Icons.COINS,
		"LeoneMC's generators server.",
		"gens"),
	SURVIVAL("Survival", Icons.TREE,
		"Survival with chat games and map resets.",
		"survival"),
	MONEYDUPE("MoneyDupe", Icons.COINS,
		"Team PvP with kits, a battle pass, events, envoys and crates.",
		"moneydupe"),
	KNOCKBACKFFA("KnockbackFFA", Icons.FEATHER,
		"Knock players off the map. Kills give Elo and XP, and items like the Switcher, WebGun and Grappling Hook spawn on the map.",
		"knockbackffa", "kbffa"),
	PRACTICE("Practice", Icons.TROPHY,
		"Duels against matched opponents.",
		"practice"),
	EVENTS("Events", Icons.STAR,
		"LeoneMC's event servers: seasonal minigames such as Beachfest and Frostfest (coconuts, brackets, TNT Tag), and the Event servers.",
		"event", "fest"),
	HUB("Hub", Icons.HOME,
		"The lobby you arrive in: the server selector, crates and keys.",
		"hub", "lobby"),
	/** A LeoneMC server Leone Client does not know yet; it is named after the server. */
	OTHER("Server", Icons.SERVER, "A LeoneMC server Leone Client does not know yet.");

	public final String displayName;
	public final String icon;
	/** For a LeoneMC server: what it is about. Empty for the general categories. */
	public final String about;
	/** Parts of the server names (as LeoneMC announces them) that mean this server. */
	private final List<String> names;
	private final boolean server;

	/** For the autotest and All Servers: as if on this LeoneMC server, or null. */
	public static @Nullable String pretendServer;

	Category(String displayName, String icon) {
		this.displayName = displayName;
		this.icon = icon;
		this.about = "";
		this.names = List.of();
		this.server = false;
	}

	Category(String displayName, String icon, String about, String... names) {
		this.displayName = displayName;
		this.icon = icon;
		this.about = about;
		this.names = List.of(names);
		this.server = true;
	}

	/** Whether this is one of LeoneMC's servers rather than a general category. */
	public boolean isServer() {
		return server;
	}

	/** The name to show: the server's own name for one Leone Client does not know. */
	public String label() {
		if (this == OTHER) {
			String s = currentServerName();
			return s == null ? displayName : s;
		}
		return displayName;
	}

	public boolean visible() {
		if (server) return this == current();
		return this != STAFF || Account.staff();
	}

	/** The LeoneMC server you are on, as a category, or null when not on LeoneMC. */
	public static @Nullable Category current() {
		String s = currentServerName();
		return s == null ? null : forServer(s);
	}

	private static @Nullable String currentServerName() {
		String s = LeoneMC.server();
		if (s == null && Modules.ALL_SERVERS.enabled()) s = pretendServer != null ? pretendServer : Modules.ALL_SERVERS.pretend();
		return s;
	}

	/** The category for a server name as LeoneMC announces it ("ElytraBox", "Event-02", "NA-Hub-01"). */
	public static Category forServer(String name) {
		String lower = name.toLowerCase(Locale.ROOT).replace(" ", "");
		for (Category c : values()) {
			if (!c.server) continue;
			for (String part : c.names) if (lower.contains(part)) return c;
		}
		return OTHER;
	}

	/** The categories on the wheel for this player: the general ones, then the server you are on. */
	public static List<Category> shown() {
		List<Category> out = new ArrayList<>();
		for (Category c : values()) if (c.visible()) out.add(c);
		return out;
	}
}
