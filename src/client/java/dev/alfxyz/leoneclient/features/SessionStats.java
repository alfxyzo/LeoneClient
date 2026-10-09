package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Account;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;

/** Your kills, deaths, KDR and kill streak this session, read from LeoneMC's death messages. */
public final class SessionStats extends Module {
	/** "☠ Victim was slain by Killer using Sword." / "was shot by" / "was burnt to a crisp whilst fighting Killer." */
	private static final Pattern DEATH = Pattern.compile("^☠ (\\S+) (.+?)\\.?$");
	private static final Pattern KILLER = Pattern.compile("(?:\\bby|\\bfighting) (\\S+?)(?: using .*)?$");

	public final Setting.Choice reset = add(new Setting.Choice("reset", "Start again", "RESET", List.of("On login", "On server switch", "Never"), "On server switch"),
		"When the counts go back to zero. Server switch also covers logging in.");
	public static final String KILLS = "Kills", DEATHS = "Deaths", KDR = "KDR", STREAK = "Streak", BEST = "Best streak";
	public final Setting.Chips show = add(new Setting.Chips("show", "Show", "PANEL", List.of(KILLS, DEATHS, KDR, STREAK, BEST), List.of(KILLS, DEATHS, KDR, STREAK)),
		"Which numbers the panel shows.");
	private int kills, deaths, streak, best;
	private String lastServer;

	public SessionStats() {
		super("session_stats", Category.COMBAT, "Session Stats", Icons.TROPHY,
			"Counts your kills, deaths, KDR and kill streak from the death messages, on a panel you can move with Modify HUD.", false);
		LeoneMC.onFreshJoin(() -> {
			if (!reset.is("Never")) clear();
		});
	}

	public void track(String plain) {
		if (!enabled() || !plain.startsWith("☠")) return;
		Matcher m = DEATH.matcher(plain);
		if (!m.matches()) return;
		String victim = m.group(1);
		Matcher k = KILLER.matcher(m.group(2));
		String killer = k.find() ? k.group(1) : null;
		if (isMe(victim)) {
			deaths++;
			streak = 0;
		} else if (killer != null && isMe(killer)) {
			kills++;
			streak++;
			best = Math.max(best, streak);
		}
	}

	private static boolean isMe(String name) {
		String n = name.toLowerCase(Locale.ROOT);
		if (n.equals(Minecraft.getInstance().getUser().getName().toLowerCase(Locale.ROOT))) return true;
		String display = Account.name();
		return display != null && n.equals(display.toLowerCase(Locale.ROOT));
	}

	@Override
	public void tick(Minecraft mc) {
		String server = LeoneMC.server();
		if (reset.is("On server switch") && server != null && !Objects.equals(server, lastServer) && lastServer != null) clear();
		if (server != null) lastServer = server;
	}

	public void clear() {
		kills = deaths = streak = best = 0;
	}

	public int kills() {
		return kills;
	}

	public int deaths() {
		return deaths;
	}

	public int streak() {
		return streak;
	}

	public int best() {
		return best;
	}

	public String kdr() {
		return String.format(Locale.ROOT, "%.2f", deaths == 0 ? (float) kills : kills / (float) deaths);
	}

	@Override
	public String status() {
		return kills + deaths == 0 ? null : kills + " kills, " + deaths + " deaths";
	}
}
