package dev.alfxyz.leoneclient;

import dev.alfxyz.leoneclient.module.Modules;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.Connection;
import org.jspecify.annotations.Nullable;

/**
 * Knows whether the game is connected to LeoneMC, and which of its servers the
 * player is on. A fresh login is told apart from a proxy server switch by the
 * network connection: switching servers keeps the same connection.
 */
public final class LeoneMC {
	public static final String WEBSITE = "https://leonemc.net";
	/** "Connecting you to ElytraBox." after /server, "Sending you to WildKits" from the hub selector. */
	private static final Pattern SWITCH = Pattern.compile("^(?:Connecting you to|Sending you to) ([A-Za-z0-9_-]+)\\.?$");
	private static final String HUB = "Hub";

	private static volatile boolean connected;
	private static volatile @Nullable String server;
	private static @Nullable String pendingServer;
	private static long pendingAt;
	private static WeakReference<Connection> lastConnection = new WeakReference<>(null);
	private static final List<Runnable> freshJoinListeners = new ArrayList<>();

	private LeoneMC() {
	}

	static void init() {
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			Connection conn = handler.getConnection();
			boolean fresh = lastConnection.get() != conn;
			lastConnection = new WeakReference<>(conn);
			ServerData data = mc.getCurrentServer();
			connected = data != null && !mc.isLocalServer() && isLeoneAddress(data.ip);
			if (!connected) {
				server = null;
			} else if (fresh) {
				server = HUB;
			} else if (pendingServer != null && System.currentTimeMillis() - pendingAt < 15_000) {
				server = pendingServer;
			}
			pendingServer = null;
			if (fresh && (connected || Modules.ALL_SERVERS.enabled())) {
				for (Runnable r : freshJoinListeners) r.run();
			}
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			// also fires while a proxy moves us between servers; JOIN sets the state again
			if (handler.getConnection() == null || !handler.getConnection().isConnected()) {
				connected = false;
				server = null;
			}
		});
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
		if (!connected) return;
		Matcher m = SWITCH.matcher(plain.strip());
		if (m.matches()) {
			pendingServer = m.group(1);
			pendingAt = System.currentTimeMillis();
		}
	}

	/** Whether a server-list address points at LeoneMC. */
	public static boolean isLeoneAddress(@Nullable String address) {
		if (address == null) return false;
		String host = address.strip().toLowerCase(Locale.ROOT);
		int colon = host.lastIndexOf(':');
		if (colon > 0 && host.indexOf(':') == colon) host = host.substring(0, colon);
		while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
		return host.equals("leonemc.net") || host.endsWith(".leonemc.net")
			|| host.equals("leonemc.minehut.gg") || host.endsWith(".minehut.gg") || host.endsWith(".minehut.com") || host.equals("minehut.com")
			|| host.equals("104.234.169.135");
	}
}
