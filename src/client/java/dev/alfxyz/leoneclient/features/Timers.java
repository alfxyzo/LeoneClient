package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

/**
 * Counts down to what is coming on LeoneMC, on a movable HUD panel: events and proxy restarts from its
 * [Alert] broadcasts (even ones Alert Filter hides), the server's own countdowns from the sidebar (KOTH,
 * Key All, Map Reset), envoy events, and the current Target bounty. Can warn a minute before.
 */
public final class Timers extends Module {
	/** "[Alert] 2500 Gems LAVA RISING EVENT (EU) (EU West) starting in 9 minutes. [Click to join]" */
	private static final Pattern EVENT = Pattern.compile("^\\[Alert\\] (?:([\\d,]+) Gems )?(.+?) EVENT((?: \\([^)]*\\))*) starting in (.+?)\\.(?:\\s.*)?$", Pattern.DOTALL);
	/** "[Alert] Proxy restarting in 1 minute 30 seconds. You will be disconnected." */
	private static final Pattern RESTART = Pattern.compile("^\\[Alert\\] Proxy restarting in (.+?)\\.(?:\\s.*)?$", Pattern.DOTALL);
	/** "Envoys | An envoy event will start in 4:59!" */
	private static final Pattern ENVOY_SOON = Pattern.compile("^Envoys \\| An envoy event will start in (\\d+):(\\d{2})!?$");
	/** "Supplydrops | Supplydrops will spawn in 4:59!" on ElytraBox, "SupplyDrops| An Event will start in 4:59!" on InsaneKits. */
	private static final Pattern SUPPLY_SOON = Pattern.compile("^Supply ?drops ?\\| (?:Supply ?drops will spawn in|An Event will start in) (\\d+):(\\d{2})!?$",
		Pattern.CASE_INSENSITIVE);
	/** Supply drops have begun, or someone collected one. */
	private static final Pattern SUPPLY_BEGUN = Pattern.compile("^Supply ?drops ?\\| (?:Supply ?drops have started!|.+ has collected a supply ?drop!).*",
		Pattern.CASE_INSENSITIVE);
	/** "Reboot | The server is rebooting in 30 seconds", sent by each server before it restarts. */
	private static final Pattern REBOOT = Pattern.compile("^Reboot \\| The server is rebooting in (\\d+) seconds?\\.?$");
	private static final Pattern ENVOY_BEGUN = Pattern.compile("^Envoys \\| An envoy event has begun!.*");
	/** "TARGET! Name is now the target! Eliminate them to win +250.0 Tokens!" */
	private static final Pattern TARGET = Pattern.compile("^TARGET! (\\S+) is now the target! Eliminate them to win \\+([\\d.,]+) (\\w+)!?$");
	/** "TARGET! Name was eliminated by Other, earning them +250.0 Tokens!" */
	private static final Pattern TARGET_DOWN = Pattern.compile("^TARGET! (\\S+) was eliminated by .*");
	/** A sidebar line such as "Koth in: 00:34:04" or "Key All: 09:55". */
	private static final Pattern SIDEBAR = Pattern.compile("^\\s*(?:\\W\\s*)?([A-Za-z][A-Za-z ]{1,24}?)\\s*:\\s*(\\d{1,2}):(\\d{2})(?::(\\d{2}))?\\s*$");
	private static final Pattern PART = Pattern.compile("(\\d+)\\s*(hour|minute|second)s?");
	private static final long KEEP_AFTER_MS = 15_000, SIDEBAR_GONE_MS = 3000, TARGET_KEEP_MS = 15 * 60_000;

	public final Setting.Toggle events = add(new Setting.Toggle("events", "Events", "SHOW", true),
		"Counts down to events such as Lava Rising and Sword events from [Alert] messages.");
	public final Setting.Toggle restarts = add(new Setting.Toggle("restarts", "Restarts", "SHOW", true),
		"Counts down to a proxy restart or a server reboot, so you are not caught mid-fight when it disconnects you.");
	public final Setting.Toggle serverTimers = add(new Setting.Toggle("server_timers", "Server timers", "SHOW", true),
		"Follows the countdowns in the server's sidebar, such as KOTH, Key All and Map Reset, so they can warn you too.");
	public final Setting.Toggle envoys = add(new Setting.Toggle("envoys", "Envoys and supply drops", "SHOW", true),
		"Counts down to an envoy event or supply drops (ElytraBox, InsaneKits) once they are announced.");
	public final Setting.Toggle target = add(new Setting.Toggle("target", "Target bounty", "SHOW", true),
		"Shows who the current Target is and the reward for eliminating them, until someone does.");
	public final Setting.Slider shownAtOnce = add(new Setting.Slider("shown", "Shown at once", "PANEL", 1, 6, 1, 4, "%.0f"),
		"The most rows the panel lists, soonest first.");
	public final Setting.Toggle warn = add(new Setting.Toggle("warn", "Warn a minute before", "WARN", true),
		"Shows a notification and plays a sound when a countdown reaches one minute.");

	public enum Kind { EVENT, RESTART, SERVER, ENVOY, SUPPLY, TARGET }

	public static final class Countdown {
		public final Kind kind;
		public final String title, detail, key;
		public long endsAt;
		/** Something happening now rather than a countdown (the Target). */
		public final boolean live;
		boolean warned;
		/** When it was first heard of, and last confirmed (sidebar countdowns are confirmed every second). */
		final long firstSeenAt;
		long seenAt;

		public Countdown(Kind kind, String title, String detail, long endsAt) {
			this(kind, title, detail, endsAt, false);
		}

		Countdown(Kind kind, String title, String detail, long endsAt, boolean live) {
			this.kind = kind;
			this.title = title;
			this.detail = detail;
			this.key = kind + "|" + title + "|" + detail;
			this.endsAt = endsAt;
			this.live = live;
			this.firstSeenAt = this.seenAt = System.currentTimeMillis();
		}
	}

	private final List<Countdown> countdowns = new ArrayList<>();
	private int ticks;

	public Timers() {
		super("timers", Category.HUD, "Timers", Icons.CLOCK,
			"Counts down to events, proxy restarts, KOTH, Key All, Map Reset and envoys, and shows the Target bounty, on a panel you can move with Modify HUD.", false);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	public void track(String plain) {
		if (!active()) return;
		long now = System.currentTimeMillis();
		Matcher m;
		if (plain.startsWith("[Alert]")) {
			m = RESTART.matcher(plain);
			if (m.matches()) {
				long ms = duration(m.group(1));
				if (ms > 0 && restarts.get()) put(new Countdown(Kind.RESTART, "Proxy restart", "You will be disconnected", now + ms));
				return;
			}
			m = EVENT.matcher(plain);
			if (m.matches() && events.get()) {
				long ms = duration(m.group(4));
				if (ms <= 0) return;
				String regions = m.group(3).strip().replace(") (", " · ").replace("(", "").replace(")", "");
				String detail = regions + (m.group(1) != null ? (regions.isEmpty() ? "" : " · ") + m.group(1) + " gems" : "");
				put(new Countdown(Kind.EVENT, titleCase(m.group(2)), detail, now + ms));
			}
			return;
		}
		if (plain.startsWith("Envoys |")) {
			m = ENVOY_SOON.matcher(plain);
			if (m.matches() && envoys.get()) {
				long ms = (Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 1000;
				put(new Countdown(Kind.ENVOY, "Envoy event", "Envoys spawn around KOTH", now + ms));
			} else if (ENVOY_BEGUN.matcher(plain).matches()) {
				countdowns.removeIf(c -> c.kind == Kind.ENVOY);
			}
			return;
		}
		m = SUPPLY_SOON.matcher(plain);
		if (m.matches()) {
			if (envoys.get()) {
				long ms = (Long.parseLong(m.group(1)) * 60 + Long.parseLong(m.group(2))) * 1000;
				put(new Countdown(Kind.SUPPLY, "Supply drops", "Crates drop in the PvP area", now + ms));
			}
			return;
		}
		if (SUPPLY_BEGUN.matcher(plain).matches()) {
			countdowns.removeIf(c -> c.kind == Kind.SUPPLY);
			return;
		}
		m = REBOOT.matcher(plain);
		if (m.matches()) {
			if (restarts.get()) put(new Countdown(Kind.RESTART, "Server reboot", "Back to the hub when it restarts", now + Long.parseLong(m.group(1)) * 1000));
			return;
		}
		if (plain.startsWith("TARGET!")) {
			m = TARGET.matcher(plain);
			if (m.matches() && target.get()) {
				countdowns.removeIf(c -> c.kind == Kind.TARGET);
				countdowns.add(new Countdown(Kind.TARGET, "Target: " + m.group(1), "+" + m.group(2) + " " + m.group(3).toLowerCase(Locale.ROOT) + " to eliminate them",
					now + TARGET_KEEP_MS, true));
			} else if (TARGET_DOWN.matcher(plain).matches()) {
				countdowns.removeIf(c -> c.kind == Kind.TARGET);
			}
		}
	}

	private void put(Countdown c) {
		for (Countdown o : countdowns) {
			if (o.key.equals(c.key)) {
				o.endsAt = c.endsAt;
				o.seenAt = c.seenAt;
				return;
			}
		}
		countdowns.add(c);
	}

	@Override
	public void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (active() && serverTimers.get() && ++ticks % 20 == 0) readSidebar(mc, now);
		for (Iterator<Countdown> it = countdowns.iterator(); it.hasNext(); ) {
			Countdown c = it.next();
			// a sidebar countdown that is no longer on the sidebar (another server, or it finished) goes
			if (now - c.endsAt > KEEP_AFTER_MS || c.kind == Kind.SERVER && now - c.seenAt > SIDEBAR_GONE_MS) {
				it.remove();
				continue;
			}
			long left = c.endsAt - now;
			if (warn.get() && !c.live && !c.warned && left <= 60_000 && left > 0) {
				c.warned = true;
				// one already under a minute when first seen is not news
				if (now - c.firstSeenAt < 1500 && left < 55_000) continue;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.7f));
				Notices.push(c.title + " in 1 minute", c.detail, c.kind == Kind.RESTART ? 0xF87171 : 0xFBBF24, c.kind == Kind.RESTART ? Icons.ALERT : Icons.CLOCK);
			}
		}
	}

	/** Takes every "Label: 00:34:04" line of the sidebar as a countdown the server keeps up to date. */
	private void readSidebar(Minecraft mc, long now) {
		if (mc.level == null) return;
		Scoreboard board = mc.level.getScoreboard();
		Objective sidebar = board.getDisplayObjective(DisplaySlot.SIDEBAR);
		if (sidebar == null) return;
		for (PlayerScoreEntry entry : board.listPlayerScores(sidebar)) {
			if (entry.isHidden()) continue;
			PlayerTeam team = board.getPlayersTeam(entry.owner());
			String line = Chat.plain(PlayerTeam.formatNameForTeam(team, entry.ownerName()));
			Matcher m = SIDEBAR.matcher(line);
			if (!m.matches()) continue;
			long a = Long.parseLong(m.group(2)), b = Long.parseLong(m.group(3));
			long secs = m.group(4) != null ? a * 3600 + b * 60 + Long.parseLong(m.group(4)) : a * 60 + b;
			String label = sidebarLabel(m.group(1).strip());
			Countdown c = new Countdown(Kind.SERVER, label, "From the sidebar", now + secs * 1000);
			put(c);
		}
	}

	/** "Koth in" reads better as "KOTH", the rest as the server wrote them. */
	private static String sidebarLabel(String label) {
		String l = label.toLowerCase(Locale.ROOT);
		if (l.startsWith("koth")) return "KOTH";
		if (l.equals("key all") || l.equals("keyall")) return "Key All";
		if (l.equals("map reset")) return "Map Reset";
		return label;
	}

	/** Countdowns to show: anything live first, then the soonest. */
	public List<Countdown> list() {
		if (!active()) return List.of();
		List<Countdown> out = new ArrayList<>(countdowns);
		out.sort((a, b) -> a.live != b.live ? (a.live ? -1 : 1) : Long.compare(a.endsAt, b.endsAt));
		return out;
	}

	static long duration(String text) {
		long ms = 0;
		Matcher m = PART.matcher(text.toLowerCase(Locale.ROOT));
		while (m.find()) {
			long n = Long.parseLong(m.group(1));
			ms += switch (m.group(2)) {
				case "hour" -> n * 3_600_000;
				case "minute" -> n * 60_000;
				default -> n * 1000;
			};
		}
		return ms;
	}

	private static String titleCase(String s) {
		StringBuilder sb = new StringBuilder();
		for (String w : s.toLowerCase(Locale.ROOT).split(" ")) {
			if (w.isEmpty()) continue;
			if (!sb.isEmpty()) sb.append(' ');
			sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1));
		}
		return sb.toString();
	}

	@Override
	public String status() {
		int n = list().size();
		return n == 0 ? null : n == 1 ? "1 countdown" : n + " countdowns";
	}
}
