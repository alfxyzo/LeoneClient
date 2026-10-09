package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.mixin.DisconnectedScreenAccessor;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.Colors;
import java.util.Locale;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.multiplayer.JoinMultiplayerScreen;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Reconnects to LeoneMC after a restart or a dropped connection, with a countdown you can cancel on
 * the disconnect screen. It never reconnects when you were sent away on purpose (a kick, a ban, a login
 * from elsewhere), and gives up after a few tries in a row.
 */
public final class AutoReconnect extends Module {
	private static final int MAX_TRIES = 5;
	/** Disconnect reasons that mean the server or staff sent you away on purpose. */
	private static final Pattern ON_PURPOSE = Pattern.compile(
		"\\b(ban(ned)?|kick(ed)?|blacklist(ed)?|punish(ed|ment)?|whitelist(ed)?|outdated|incompatible|vpn|alts?)\\b"
			+ "|logged in from another location|already connected|already online|invalid session|failed to log in|not authenticated",
		Pattern.CASE_INSENSITIVE);
	/** How long after reconnecting a join still counts as that reconnect, for going back to your server. */
	private static final long RETURN_WINDOW_MS = 120_000;

	public final Setting.Slider delay = add(new Setting.Slider("delay", "Wait before reconnecting", "RECONNECT", 3, 60, 1, 8, "%.0f s"),
		"How long the disconnect screen counts down before reconnecting. During a restart, a little longer gives the network time to come back.");
	public final Setting.Toggle sameServer = add(new Setting.Toggle("same_server", "Back to the same server", "RECONNECT", true),
		"After reconnecting, takes you back to the server you were on (instead of the hub, or your Auto Join server).");

	/** The LeoneMC server-list entry being played on (null on any other server), and the LeoneMC server you were on. */
	private @Nullable ServerData last;
	private @Nullable String lastServer;
	private int tries;
	/** When a LeoneMC connection last dropped, and whether a reconnect of ours is under way. */
	private long droppedAt;
	private boolean attempting;
	/** The disconnect screen being counted down on, and its state. */
	private @Nullable Screen counting;
	private @Nullable Button button;
	private long dueAt;
	private boolean cancelled;
	private @Nullable String waitReason;
	/** Where to go after the reconnect lands, and since when. */
	private @Nullable String returnTo;
	private long reconnectedAt;

	public AutoReconnect() {
		super("auto_reconnect", Category.SERVER, "Auto Reconnect", Icons.REFRESH,
			"Puts you back on LeoneMC after a restart or a dropped connection, and back on the server you were on. Never after a kick or a ban.", false);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			ServerData data = mc.getCurrentServer();
			boolean leone = data != null && !mc.isLocalServer() && LeoneMC.isLeoneAddress(data.ip);
			last = leone ? data : null;
			if (!leone) lastServer = null;
			tries = 0;
			attempting = false;
		});
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			if (last != null) droppedAt = System.currentTimeMillis();
		});
		// after Auto Join's own listener (it was made first), so a return trip wins over the favourite server
		LeoneMC.onFreshJoin(() -> {
			String target = returnTo;
			returnTo = null;
			if (target == null || !active() || !LeoneMC.connected() || System.currentTimeMillis() - reconnectedAt > RETURN_WINDOW_MS) return;
			Modules.AUTO_JOIN.cancelPending();
			Minecraft mc = Minecraft.getInstance();
			if (mc.getConnection() == null) return;
			mc.getConnection().sendCommand("server " + target.toLowerCase(Locale.ROOT));
			Notices.push(name, "Taking you back to " + target, Colors.ACCENT_RGB, Icons.REFRESH);
		});
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (screen instanceof DisconnectedScreen ds) onDisconnectedScreen(ds);
			// back at the title screen or server list: whatever happens next is your own doing
			else if (screen instanceof TitleScreen || screen instanceof JoinMultiplayerScreen) attempting = false;
		});
	}

	@Override
	public void tick(Minecraft mc) {
		// the server you are on, kept up to date so it is known when the connection drops
		if (LeoneMC.connected() && LeoneMC.server() != null) lastServer = LeoneMC.server();
	}

	private void onDisconnectedScreen(DisconnectedScreen screen) {
		if (!enabled() || last == null) return;
		// init runs again when the window is resized: keep the same countdown
		boolean same = screen == counting;
		// only for a LeoneMC connection that just dropped, or a reconnect of ours that failed
		if (!same && !attempting && System.currentTimeMillis() - droppedAt > 10_000) return;
		if (!same) {
			droppedAt = 0;
			counting = screen;
			cancelled = false;
			Component reason = ((DisconnectedScreenAccessor) screen).leone$details().reason();
			String why = Chat.plain(reason);
			if (ON_PURPOSE.matcher(why).find()) waitReason = "Not reconnecting: you were sent away on purpose";
			else if (tries >= MAX_TRIES) waitReason = "Gave up after " + MAX_TRIES + " tries";
			else waitReason = null;
			dueAt = System.currentTimeMillis() + (long) (delay.get() * 1000);
		}
		int bottom = 0;
		for (AbstractWidget wd : Screens.getWidgets(screen)) bottom = Math.max(bottom, wd.getY() + wd.getHeight());
		button = Button.builder(label(), b -> {
			if (waitReason == null && !cancelled) cancelled = true;
			else reconnect();
		}).bounds(screen.width / 2 - 100, bottom + 6, 200, 20).build();
		Screens.getWidgets(screen).add(button);
		if (waitReason != null) button.setTooltip(Tooltip.create(Component.literal(waitReason + ". Click to try anyway.")));
		ScreenEvents.afterTick(screen).register(s -> {
			if (s != counting || button == null) return;
			if (waitReason == null && !cancelled && System.currentTimeMillis() >= dueAt) {
				reconnect();
				return;
			}
			button.setMessage(label());
		});
	}

	private Component label() {
		if (waitReason != null || cancelled) return Component.literal("Reconnect");
		long left = Math.max(0, (dueAt - System.currentTimeMillis() + 999) / 1000);
		return Component.literal("Reconnecting in " + left + " s (click to cancel)");
	}

	private void reconnect() {
		Minecraft mc = Minecraft.getInstance();
		ServerData data = last;
		counting = null;
		button = null;
		if (data == null) return;
		tries++;
		attempting = true;
		returnTo = sameServer.get() && lastServer != null && !lastServer.equalsIgnoreCase(LeoneMC.HUB) ? lastServer : null;
		reconnectedAt = System.currentTimeMillis();
		ConnectScreen.startConnecting(new JoinMultiplayerScreen(new TitleScreen()), mc, ServerAddress.parseString(data.ip), data, false, null);
	}

	@Override
	public String status() {
		if (!enabled()) return null;
		return lastServer != null && sameServer.get() ? "Will bring you back to " + lastServer : null;
	}

	/** For the autotest: as if a LeoneMC connection to this entry had just dropped. */
	public void debugDropped(ServerData data, String server) {
		last = data;
		lastServer = server;
		droppedAt = System.currentTimeMillis();
	}

	/** For the autotest: forgets the pretend connection. */
	public void debugForget() {
		last = null;
		lastServer = null;
		counting = null;
		button = null;
		attempting = false;
	}

	/** For the autotest: whether a disconnect reason counts as being sent away on purpose. */
	public static boolean onPurpose(String reason) {
		return ON_PURPOSE.matcher(reason).find();
	}
}
