package dev.alfxyz.leoneclient.features;

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

/**
 * Counts down to LeoneMC's events and proxy restarts, read from its [Alert] broadcasts (even ones
 * Alert Filter hides), on a movable HUD panel.
 */
public final class Timers extends Module {
	/** "[Alert] 2500 Gems LAVA RISING EVENT (EU) (EU West) starting in 9 minutes. [Click to join]" */
	private static final Pattern EVENT = Pattern.compile("^\\[Alert\\] (?:([\\d,]+) Gems )?(.+?) EVENT((?: \\([^)]*\\))*) starting in (.+?)\\.(?:\\s.*)?$", Pattern.DOTALL);
	/** "[Alert] Proxy restarting in 1 minute 30 seconds. You will be disconnected." */
	private static final Pattern RESTART = Pattern.compile("^\\[Alert\\] Proxy restarting in (.+?)\\.(?:\\s.*)?$", Pattern.DOTALL);
	private static final Pattern PART = Pattern.compile("(\\d+)\\s*(hour|minute|second)s?");
	private static final long KEEP_AFTER_MS = 15_000;

	public final Setting.Toggle events = add(new Setting.Toggle("events", "Events", "SHOW", true),
		"Counts down to events such as Lava Rising and Sword events.");
	public final Setting.Toggle restarts = add(new Setting.Toggle("restarts", "Proxy restarts", "SHOW", true),
		"Counts down to a proxy restart, so you are not caught mid-fight when it disconnects you.");
	public final Setting.Slider shownAtOnce = add(new Setting.Slider("shown", "Shown at once", "PANEL", 1, 6, 1, 4, "%.0f"),
		"The most countdowns the panel lists, soonest first.");
	public final Setting.Toggle warn = add(new Setting.Toggle("warn", "Warn a minute before", "SHOW", true),
		"Shows a notification and plays a sound when a countdown reaches one minute.");

	public enum Kind { EVENT, RESTART }

	public static final class Countdown {
		public final Kind kind;
		public final String title, detail, key;
		public long endsAt;
		boolean warned;

		public Countdown(Kind kind, String title, String detail, long endsAt) {
			this.kind = kind;
			this.title = title;
			this.detail = detail;
			this.key = kind + "|" + title + "|" + detail;
			this.endsAt = endsAt;
		}
	}

	private final List<Countdown> countdowns = new ArrayList<>();

	public Timers() {
		super("timers", Category.HUD, "Timers", Icons.CLOCK,
			"Counts down to events and proxy restarts announced in [Alert] messages, on a panel you can move with Modify HUD.", false);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	public void track(String plain) {
		if (!active() || !plain.startsWith("[Alert]")) return;
		long now = System.currentTimeMillis();
		Matcher m = RESTART.matcher(plain);
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
	}

	private void put(Countdown c) {
		for (Countdown o : countdowns) {
			if (o.key.equals(c.key)) {
				o.endsAt = c.endsAt;
				return;
			}
		}
		countdowns.add(c);
	}

	@Override
	public void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		for (Iterator<Countdown> it = countdowns.iterator(); it.hasNext(); ) {
			Countdown c = it.next();
			if (now - c.endsAt > KEEP_AFTER_MS) {
				it.remove();
				continue;
			}
			long left = c.endsAt - now;
			if (warn.get() && !c.warned && left <= 60_000 && left > 0) {
				c.warned = true;
				mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.0f, 0.7f));
				Notices.push(c.title + " in 1 minute", c.detail, c.kind == Kind.RESTART ? 0xF87171 : 0xFBBF24, c.kind == Kind.RESTART ? Icons.ALERT : Icons.CLOCK);
			}
		}
	}

	/** Countdowns to show, soonest first. */
	public List<Countdown> list() {
		if (!active()) return List.of();
		List<Countdown> out = new ArrayList<>(countdowns);
		out.sort((a, b) -> Long.compare(a.endsAt, b.endsAt));
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
