package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Account;
import dev.alfxyz.leoneclient.web.Friends;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Hides the broadcasts that make up a good part of LeoneMC's chat (other players' deaths, crate
 * openings, coinflips, votes and the like), and folds a line repeated straight after itself into one
 * line with a count.
 */
public final class ChatCleaner extends Module {
	public static final String DEATHS = "Other players' deaths", CRATES = "Crate openings", COINFLIPS = "Coinflips", STREAKS = "Killstreaks",
		VOTES = "Votes", DAILY = "Daily rewards", ENVOYS = "Envoy pickups", JOINS = "Lobby joins", BOXES = "Announcement boxes";

	/** "☠ Victim was slain by Killer using Sword." */
	private static final Pattern DEATH = Pattern.compile("^☠ .*");
	/** "🔥 Name is on a 5 killstreak!" */
	private static final Pattern STREAK = Pattern.compile("^\\S+ \\S+ is on a \\d+ killstreak!$|^\\S+ is on a \\d+ killstreak!$");
	/** "Crates | Name has opened a KOTH Crate and won a Eagle Key." (not "Crates | You received ...", your own reward) */
	private static final Pattern CRATE = Pattern.compile("^Crates \\| \\S+ has opened .*");
	/** "▶ Name has won a coinflip worth 60,000 against Other." */
	private static final Pattern COINFLIP = Pattern.compile("(?i).*\\bhas won a coinflip\\b.*");
	private static final Pattern VOTE = Pattern.compile("^Voting \\| .*");
	private static final Pattern DAILY_LINE = Pattern.compile("^DAILY! .*");
	/** "Envoys | Name has collected a Common Envoy! (12 remaining)" */
	private static final Pattern ENVOY = Pattern.compile("^Envoys \\| \\S+ has collected .*");
	/** "Gold Name has joined the lobby!" */
	private static final Pattern JOIN = Pattern.compile("^(?:.{0,48} )?[A-Za-z0-9_.]{3,17} has joined the lobby!$");
	/** The rotating tips: "Discord\n \n| Join our discord server...", "Server Store\n \n| Want to purchase gems..." */
	private static final Pattern BOX = Pattern.compile("^[A-Z][A-Za-z ]{2,30}\\n \\n\\| .*", Pattern.DOTALL);
	/** A repeat further apart than this starts a new line. */
	private static final long STACK_MS = 60_000;

	public final Setting.Chips hide = add(new Setting.Chips("hide", "Hide", "HIDE",
			List.of(DEATHS, CRATES, COINFLIPS, STREAKS, VOTES, DAILY, ENVOYS, JOINS, BOXES),
			List.of(CRATES, COINFLIPS, VOTES, DAILY, ENVOYS, JOINS, BOXES)),
		"Kinds of broadcast to keep out of chat. Lines that name you or one of your friends always show, and so do your own crate rewards.");
	public final Setting.Toggle stack = add(new Setting.Toggle("stack", "Stack repeats", "REPEATS", true),
		"When a line is the same as the one just before it, the two become one line with a count, such as [x3].");

	private int hidden, stacked;
	private String lastPlain = "", lastShown = "";
	private int lastCount;
	private long lastAt;

	public ChatCleaner() {
		super("chat_cleaner", Category.CHAT, "Chat Cleaner", Icons.FILTER,
			"Hides the broadcasts you do not want, like crate openings and other players' deaths, and stacks repeated lines.", false);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	/** True when this broadcast should not reach chat. */
	public boolean hides(String plain) {
		if (!active()) return false;
		String kind = kind(plain);
		if (kind == null || !hide.has(kind)) return false;
		// anything naming you or a friend stays: your coinflips, a friend's kill, a friend joining the lobby
		if (involvesYou(plain)) return false;
		hidden++;
		return true;
	}

	/** The kind of broadcast a line is, or null for anything else. */
	static @Nullable String kind(String plain) {
		if (DEATH.matcher(plain).matches()) return DEATHS;
		if (STREAK.matcher(plain).matches()) return STREAKS;
		if (CRATE.matcher(plain).matches()) return CRATES;
		if (COINFLIP.matcher(plain).matches()) return COINFLIPS;
		if (VOTE.matcher(plain).matches()) return VOTES;
		if (DAILY_LINE.matcher(plain).matches()) return DAILY;
		if (ENVOY.matcher(plain).matches()) return ENVOYS;
		if (JOIN.matcher(plain).matches()) return JOINS;
		if (BOX.matcher(plain).matches()) return BOXES;
		return null;
	}

	/** Your Minecraft or LeoneMC name, or a friend's, is in the line. */
	private static boolean involvesYou(String plain) {
		String lower = plain.toLowerCase(Locale.ROOT);
		if (containsName(lower, Minecraft.getInstance().getUser().getName())) return true;
		String shown = Account.name();
		if (shown != null && containsName(lower, shown)) return true;
		for (var f : Friends.list()) if (containsName(lower, f.name())) return true;
		return false;
	}

	private static boolean containsName(String lower, String name) {
		return Pattern.compile("(?<![a-z0-9_])" + Pattern.quote(name.toLowerCase(Locale.ROOT)) + "(?![a-z0-9_])").matcher(lower).find();
	}

	/**
	 * The line to show: the message itself, or, when it repeats the line just before it, the message with
	 * a count, after taking back the earlier line.
	 */
	public Component stack(Component message, String plain) {
		if (!active() || !stack.get() || plain.isBlank()) return message;
		long now = System.currentTimeMillis();
		if (plain.equals(lastPlain) && now - lastAt < STACK_MS) {
			ChatLines.takeBack(lastShown);
			lastCount++;
			stacked++;
			lastAt = now;
			Component counted = ChatLines.counted(message, lastCount);
			lastShown = Chat.plain(counted);
			return counted;
		}
		lastPlain = plain;
		lastShown = plain;
		lastCount = 1;
		lastAt = now;
		return message;
	}

	/** Something other than a repeat came in, for example a line another module hid: the next repeat starts again. */
	public void interrupted() {
		lastPlain = "";
	}

	@Override
	public String status() {
		if (hidden + stacked == 0) return null;
		StringBuilder sb = new StringBuilder();
		if (hidden > 0) sb.append(hidden).append(hidden == 1 ? " line hidden" : " lines hidden");
		if (stacked > 0) sb.append(sb.isEmpty() ? "" : ", ").append(stacked).append(stacked == 1 ? " repeat stacked" : " repeats stacked");
		return sb.toString();
	}
}
