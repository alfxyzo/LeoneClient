package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * Stacks LeoneMC's anticheat alerts: repeats of the same player and check are hidden for a while, and
 * a panel lists who has been flagged recently, for what and how often.
 */
public final class AnticheatAlerts extends Module {
	/** "Anticheat > Name flagged Reach A (69.0x)", optionally followed by " (Server)". */
	private static final Pattern FLAGGED = Pattern.compile("^Anticheat > (\\S+) flagged (.+?) \\(([\\d.,]+)x\\)(?: \\(([^)]+)\\))?$");
	/** "[GrimAC] Name failed Simulation (vl:51.0): details", also "[Vulcan] Name failed Bad Packets (9) (vl:1/10): details". */
	private static final Pattern FAILED = Pattern.compile("^\\[[^\\]]{1,20}\\] (\\S+) failed (.+?)(?: \\(\\d+\\))? \\(vl:([\\d.,]+)(?:/\\d+)?\\).*$", Pattern.DOTALL);
	private static final long NEW_PLAYER_MS = 5 * 60_000;

	public final Setting.Toggle hideRepeats = add(new Setting.Toggle("hide_repeats", "Hide repeats", "CHAT", true),
		"Treats an alert as a repeat when the same player was flagged for the same check a moment ago, so it does not get a line of its own. The panel still counts it.");
	public final Setting.Toggle countRepeats = add(new Setting.Toggle("count_repeats", "Count repeats", "CHAT", true),
		"Instead of hiding a repeat, moves the alert down to the newest line with a count, such as [x3], so you can see it is still coming in.");
	public final Setting.Slider window = add(new Setting.Slider("window", "Repeat window", "CHAT", 5, 120, 5, 30, "%.0f s"),
		"How long after an alert the same player and check count as a repeat.");
	public final Setting.Slider panelPlayers = add(new Setting.Slider("panel_players", "Players listed", "PANEL", 1, 10, 1, 6, "%.0f"),
		"The most players the panel lists, most recently flagged first.");
	public final Setting.Slider panelMinutes = add(new Setting.Slider("panel_minutes", "Keep players for", "PANEL", 1, 15, 1, 3, "%.0f min"),
		"How long a player stays on the panel after their last alert.");
	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound for new players", "PANEL", false),
		"Plays a sound the first time a player is flagged in five minutes.");

	/** A flagged player: checks with counts, the strongest violation level, and when and where they were last flagged. */
	public static final class Suspect {
		public final String name;
		public final Map<String, Integer> checks = new LinkedHashMap<>();
		public int total;
		public long lastAt;
		public @Nullable String server;

		public Suspect(String name) {
			this.name = name;
		}

		/** The checks, most frequent first. */
		public List<Map.Entry<String, Integer>> topChecks() {
			List<Map.Entry<String, Integer>> list = new ArrayList<>(checks.entrySet());
			list.sort(Map.Entry.<String, Integer>comparingByValue().reversed());
			return list;
		}
	}

	private final Map<String, Suspect> suspects = new HashMap<>();
	private final Map<String, Stack> stacks = new HashMap<>();
	private int alerts, hidden;

	public AnticheatAlerts() {
		super("anticheat_alerts", Category.STAFF, "Anticheat Alerts", Icons.FLAG,
			"Folds repeated anticheat alerts into one line with a count, and lists recently flagged players on a HUD panel.", false);
		countRepeats.shownWhen(hideRepeats::get);
		window.shownWhen(hideRepeats::get);
	}

	/** One alert line in chat and its repeats: the text shown, how many alerts it stands for, and when it was shown. */
	private static final class Stack {
		String shownText;
		int count = 1;
		long shownAt;

		Stack(String shownText, long shownAt) {
			this.shownText = shownText;
			this.shownAt = shownAt;
		}
	}

	/**
	 * Records an alert and returns the line to show: the alert itself, the alert with a repeat count
	 * (after taking back the earlier line it replaces), or null to hide it.
	 */
	public @Nullable Component handle(Component message, String plain) {
		if (!active()) return message;
		String name, check, server = null;
		Matcher m = FLAGGED.matcher(plain);
		if (m.matches()) {
			name = m.group(1);
			check = m.group(2);
			server = m.group(4);
		} else {
			m = FAILED.matcher(plain);
			if (!m.matches()) return message;
			name = m.group(1);
			check = m.group(2);
		}
		long now = System.currentTimeMillis();
		alerts++;
		Suspect s = suspects.computeIfAbsent(name.toLowerCase(java.util.Locale.ROOT), k -> new Suspect(name));
		boolean isNew = now - s.lastAt > NEW_PLAYER_MS;
		s.checks.merge(check, 1, Integer::sum);
		s.total++;
		s.lastAt = now;
		if (server != null) s.server = server;
		if (isNew && sound.get()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BELL.value(), 1.4f, 0.6f));
		String key = s.name.toLowerCase(java.util.Locale.ROOT) + "\n" + check;
		Stack stack = stacks.get(key);
		if (!hideRepeats.get() || stack == null || now - stack.shownAt >= window.get() * 1000) {
			stacks.put(key, new Stack(plain, now));
			return message;
		}
		hidden++;
		stack.count++;
		if (!countRepeats.get()) return null;
		// the newest alert with its count takes the place of the line it repeats
		ChatLines.takeBack(stack.shownText);
		Component counted = ChatLines.counted(message, stack.count);
		stack.shownText = Chat.plain(counted);
		stack.shownAt = now;
		return counted;
	}

	/** Players flagged in the last few minutes, most recent first. Empty while staff chat is hidden from recordings. */
	public List<Suspect> recent() {
		if (!active() || StaffChat.isHidden()) return List.of();
		long now = System.currentTimeMillis();
		List<Suspect> out = new ArrayList<>();
		long keep = (long) (panelMinutes.get() * 60_000);
		for (Suspect s : suspects.values()) if (now - s.lastAt < keep) out.add(s);
		out.sort(Comparator.comparingLong((Suspect s) -> -s.lastAt));
		return out;
	}

	@Override
	public String status() {
		if (alerts == 0) return null;
		return alerts + (alerts == 1 ? " alert" : " alerts") + (hidden == 0 ? "" : ", " + hidden + (hidden == 1 ? " repeat" : " repeats")
			+ (countRepeats.get() ? " counted" : " hidden"));
	}
}
