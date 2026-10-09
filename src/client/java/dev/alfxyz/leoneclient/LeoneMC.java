package dev.alfxyz.leoneclient;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.mixin.TabListAccessor;
import dev.alfxyz.leoneclient.module.Modules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * Knows whether the game is connected to LeoneMC, and which of its servers the
 * player is on. A fresh login is told apart from a proxy server switch by the
 * network connection: switching servers keeps the same connection.
 *
 * LeoneMC is recognised whatever address it was joined by. An address with "leonemc" in it counts at
 * once. Any other server is watched for LeoneMC's own signs: "leonemc" in the sidebar (which ends with
 * play.leonemc.net) or in the tab list's header or footer, or one of its announcement boxes, which
 * players cannot type. An address recognised that way is remembered, so it counts at once next time.
 */
public final class LeoneMC {
	public static final String WEBSITE = "https://leonemc.net";
	/** "Connecting you to ElytraBox." after /server, "Sending you to WildKits" from the hub selector. */
	private static final Pattern SWITCH = Pattern.compile("^(?:Connecting you to|Sending you to) ([A-Za-z0-9_-]+)\\.?$");
	public static final String HUB = "Hub";

	private static volatile boolean connected;
	private static volatile @Nullable String server;
	private static @Nullable String pendingServer;
	private static long pendingAt;
	private static WeakReference<Connection> lastConnection = new WeakReference<>(null);
	private static final List<Runnable> freshJoinListeners = new ArrayList<>();
	/** Sidebar titles, without spaces and in lower case, and the server each one means. */
	private static final Map<String, String> SIDEBAR_TITLES = Map.of(
		"lobby", HUB, "hub", HUB, "elytrabox", "ElytraBox", "wildkits", "WildKits", "randomkits", "WildKits",
		"coreraiding", "CoreRaiding", "insanekits", "InsaneKits", "lifesteal", "Lifesteal", "gens", "Gens", "survival", "Survival");
	private static boolean checkSidebar;
	private static int ticks;
	/** On a server not known to be LeoneMC: watching for its signs. */
	private static boolean probing;
	private static int probeTicks;
	private static @Nullable String host;
	/** Whether the fresh-join listeners have run for this connection. */
	private static boolean joinRan;
	/** Announcement boxes ("Discord", blank line, "| ..." lines), which only the server sends. */
	private static final Pattern BOX = Pattern.compile("^[A-Z][A-Za-z ]{2,30}\\n\\s*\\n\\|.*", Pattern.DOTALL);
	/** Addresses LeoneMC was recognised at by its signs, from earlier games. */
	private static final Set<String> learned = new LinkedHashSet<>();
	private static boolean learnedLoaded;
	private static final int MAX_LEARNED = 24;

	private LeoneMC() {
	}

	static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			Connection conn = handler.getConnection();
			boolean fresh = lastConnection.get() != conn;
			lastConnection = new WeakReference<>(conn);
			ServerData data = mc.getCurrentServer();
			if (fresh) {
				// a new connection is decided afresh; a server switch keeps what this one turned out to be
				host = data == null || mc.isLocalServer() ? null : hostOf(data.ip);
				connected = host != null && (isLeoneAddress(host) || learned().contains(host));
				probing = host != null && !connected;
				joinRan = false;
			}
			boolean announced = pendingServer != null && System.currentTimeMillis() - pendingAt < 15_000;
			if (!connected) {
				server = null;
			} else if (announced) {
				server = pendingServer;
			} else if (fresh) {
				server = HUB;
			} else {
				// moved without a message (a kick back to the hub, say): the sidebar will tell
				server = null;
			}
			// the sidebar can confirm or correct anything that was not announced
			checkSidebar = connected && !announced;
			pendingServer = null;
			if (fresh && (connected || Modules.ALL_SERVERS.enabled())) runJoinListeners();
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			// also fires while a proxy moves us between servers; JOIN sets the state again
			if (handler.getConnection() == null || !handler.getConnection().isConnected()) {
				connected = false;
				probing = false;
				server = null;
			}
		});
	}

	/**
	 * Reads the server from LeoneMC's sidebar title ("Lobby", "Elytra Box") when no message said where
	 * the player went. Called every client tick; looks once a second until it knows.
	 */
	public static void tick(Minecraft mc) {
		if (probing && mc.level != null && ++probeTicks % 20 == 0) {
			String sign = signOnScreen(mc);
			if (sign != null) recognise(sign);
		}
		if (!checkSidebar || !connected || mc.level == null || ++ticks % 20 != 0) return;
		Objective sidebar = mc.level.getScoreboard().getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return;
		String title = Chat.plain(sidebar.getDisplayName()).replace(" ", "").toLowerCase(Locale.ROOT);
		String known = SIDEBAR_TITLES.get(title);
		if (known == null) return;
		server = known;
		checkSidebar = false;
	}

	/** Runs whenever the player logs in to LeoneMC (not on server switches). */
	public static void onFreshJoin(Runnable r) {
		freshJoinListeners.add(r);
	}

	/** True while connected to the LeoneMC network. */
	public static boolean connected() {
		return connected;
	}

	/** True when LeoneMC-only features should run: on LeoneMC, or anywhere with All Servers on. */
	public static boolean active() {
		return connected || Modules.ALL_SERVERS.enabled() && Minecraft.getInstance().level != null;
	}

	/** The LeoneMC server the player is on, best effort ("Hub" after logging in), or null when not on LeoneMC. */
	public static @Nullable String server() {
		return connected ? server : null;
	}

	/** Watches server chat for LeoneMC's server switch messages. */
	public static void onChat(String plain) {
		if (probing && BOX.matcher(plain).matches() && plain.toLowerCase(Locale.ROOT).contains("leonemc")) recognise("an announcement");
		if (!connected) return;
		Matcher m = SWITCH.matcher(plain.strip());
		if (m.matches()) {
			pendingServer = m.group(1);
			pendingAt = System.currentTimeMillis();
		}
	}

	/** Whether an address is plainly LeoneMC's: any with "leonemc" in it, such as play.leonemc.gg or eu.leonemc.net. */
	public static boolean isLeoneAddress(@Nullable String address) {
		String h = address == null ? null : hostOf(address);
		return h != null && h.contains("leonemc");
	}

	/** Whether an address is LeoneMC's, plainly or because LeoneMC was recognised there before. */
	public static boolean isKnownAddress(@Nullable String address) {
		String h = address == null ? null : hostOf(address);
		return h != null && (h.contains("leonemc") || learned().contains(h));
	}

	/** The host of a server-list address, in lower case, without a port or a trailing dot. */
	static @Nullable String hostOf(String address) {
		String h = address.strip().toLowerCase(Locale.ROOT);
		int colon = h.lastIndexOf(':');
		if (colon > 0 && h.indexOf(':') == colon) h = h.substring(0, colon);
		while (h.endsWith(".")) h = h.substring(0, h.length() - 1);
		return h.isEmpty() ? null : h;
	}

	/** LeoneMC's name in the sidebar or the tab list, or null. Says where it was seen. */
	private static @Nullable String signOnScreen(Minecraft mc) {
		Scoreboard board = mc.level.getScoreboard();
		Objective sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar != null) {
			if (mentionsLeone(sidebar.getDisplayName())) return "the sidebar";
			for (PlayerScoreEntry entry : board.listPlayerScores(sidebar)) {
				if (entry.isHidden()) continue;
				Component line = PlayerTeam.formatNameForTeam(board.getPlayersTeam(entry.owner()), entry.ownerName());
				if (mentionsLeone(line)) return "the sidebar";
			}
		}
		TabListAccessor tab = (TabListAccessor) mc.gui.hud.getTabList();
		if (mentionsLeone(tab.leone$header()) || mentionsLeone(tab.leone$footer())) return "the tab list";
		return null;
	}

	private static boolean mentionsLeone(@Nullable Component c) {
		return c != null && Chat.plain(c).toLowerCase(Locale.ROOT).replace(" ", "").contains("leonemc");
	}

	/** LeoneMC's signs were seen: from now on this connection is LeoneMC, and its address is remembered. */
	private static void recognise(String where) {
		if (connected || !probing) return;
		probing = false;
		connected = true;
		// a new login lands in a hub; the sidebar corrects that if not
		if (server == null) server = HUB;
		checkSidebar = true;
		LogUtils.getLogger().info("Leone Client: recognised LeoneMC at {} from {}", host, where);
		if (host != null && learned().add(host)) {
			while (learned.size() > MAX_LEARNED) learned.remove(learned.iterator().next());
			saveLearned();
		}
		if (!joinRan) runJoinListeners();
	}

	private static void runJoinListeners() {
		joinRan = true;
		for (Runnable r : freshJoinListeners) r.run();
	}

	private static Path learnedFile() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient").resolve("network.json");
	}

	private static Set<String> learned() {
		if (learnedLoaded) return learned;
		learnedLoaded = true;
		try {
			if (Files.isRegularFile(learnedFile())) {
				JsonObject o = JsonParser.parseString(Files.readString(learnedFile())).getAsJsonObject();
				if (o.has("addresses")) for (JsonElement e : o.getAsJsonArray("addresses")) learned.add(e.getAsString());
			}
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not read {}", learnedFile(), e);
		}
		return learned;
	}

	private static void saveLearned() {
		JsonObject o = new JsonObject();
		JsonArray list = new JsonArray();
		for (String a : learned) list.add(a);
		o.add("addresses", list);
		try {
			Files.createDirectories(learnedFile().getParent());
			Files.writeString(learnedFile(), o.toString());
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not write {}", learnedFile(), e);
		}
	}

	/** For the autotest: as if a fresh connection to this address had just been made. */
	public static void debugJoin(String address) {
		host = hostOf(address);
		connected = host != null && (isLeoneAddress(host) || learned().contains(host));
		probing = host != null && !connected;
		joinRan = true;
	}

	/** For the autotest: forgets a learned address and the pretend connection. */
	public static void debugForget(String address) {
		String h = hostOf(address);
		if (h != null && learned().remove(h)) saveLearned();
		connected = false;
		probing = false;
		server = null;
	}

	/** For the autotest. */
	public static boolean debugProbing() {
		return probing;
	}
}
