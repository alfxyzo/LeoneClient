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

	ELYTRABOX("ElytraBox", Icons.FEATHER, "elytrabox"),
	WILDKITS("WildKits", Icons.SWORDS, "wildkits", "randomkits"),
	CORERAIDING("CoreRaiding", Icons.SHIELD, "coreraiding"),
	INSANEKITS("InsaneKits", Icons.FLAME, "insanekits"),
	LIFESTEAL("Lifesteal", Icons.HEART, "lifesteal"),
	GENS("Gens", Icons.COINS, "gens"),
	SURVIVAL("Survival", Icons.TREE, "survival"),
	MONEYDUPE("MoneyDupe", Icons.COINS, "moneydupe"),
	KNOCKBACKFFA("KnockbackFFA", Icons.FEATHER, "knockbackffa", "kbffa"),
	PRACTICE("Practice", Icons.TROPHY, "practice"),
	EVENTS("Events", Icons.STAR, "event", "fest"),
	HUB("Hub", Icons.HOME, "hub", "lobby"),
	/** A LeoneMC server Leone Client does not know yet; it is named after the server. */
	OTHER("Server", Icons.SERVER, true);

	public final String displayName;
	public final String icon;
	/** Parts of the server names (as LeoneMC announces them) that mean this server. */
	private final List<String> names;
	private final boolean server;

	/** For the autotest and All Servers: as if on this LeoneMC server, or null. */
	public static @Nullable String pretendServer;

	Category(String displayName, String icon) {
		this.displayName = displayName;
		this.icon = icon;
		this.names = List.of();
		this.server = false;
	}

	/** A LeoneMC server, known by these parts of its name. */
	Category(String displayName, String icon, String... names) {
		this.displayName = displayName;
		this.icon = icon;
		this.names = List.of(names);
		this.server = true;
	}

	/** A LeoneMC server matched by no name. */
	Category(String displayName, String icon, boolean server) {
		this.displayName = displayName;
		this.icon = icon;
		this.names = List.of();
		this.server = server;
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
