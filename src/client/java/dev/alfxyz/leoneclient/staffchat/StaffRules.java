package dev.alfxyz.leoneclient.staffchat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/** Which chat lines count as staff chat on LeoneMC, and the texts that mean vanish or mod mode. */
public final class StaffRules {
	static final List<String> DEFAULT_STARTS_WITH = List.of("[Staff]", "[SC]", "Anticheat");
	static final List<String> DEFAULT_CONTAINS = List.of("Tick #");
	/**
	 * LeoneMC staff-only messages that cannot be told apart by a fixed start or part: alerts, reports,
	 * lookup output (which includes other players' private messages), punishment feedback and staff tools.
	 * Each must match the whole message, after colour codes and leading spaces and line breaks are removed.
	 */
	static final List<String> DEFAULT_MATCHES = List.of(
		// Alerts, including other anticheats' "[GrimAC] <player> failed <check> (vl:<n>)" format
		"\\[[^\\]]{1,20}\\] [A-Za-z0-9_.]{3,17} failed .*\\(vl:.*",
		"\u2716 Warning: [A-Za-z0-9_.]{3,17} is suspected of using a VPN/Proxy\\..*",
		"\u26a0 Sus Players \u26a0",
		"There are currently \\d+ that might be cheating!",
		"Run /sus to check it out!",
		"Loading suspicious players\\.\\.\\.",
		"No suspicious players found\\.",
		"[A-Za-z0-9_.]{3,17} has tried to join, but is banned .*",
		"[A-Za-z0-9_.]{3,17} has attempted to join but is currently banned .*",
		// Reports, requests, admin chat, filtered messages, social spy, command spy
		"\\[(?:Report|Request|Admin|Filtered|Commands|\u2709)\\] .*",
		// Chat log lookups (these include other players' private messages)
		"Fetching logs\\.\\.\\. Please wait\\.",
		"Displaying page \\d+/\\d+ \\(\\d+ logs\\) for .*",
		"\\[\\d+(?:s|m|h|d|w|mo|y) ago\\] \\([^)]*\\) \\([A-Za-z0-9_.]{3,17}\\) \\[[^\\]]*\\] >.*",
		"No logs found\\.",
		"[A-Za-z0-9_.]{3,17} has no logs\\.",
		// Anticheat logs, player information and player notes
		"-+\\n[A-Za-z0-9_.]{3,17}'s logs \\(Page \\d+/\\d+\\).*",
		"-+\\n[A-Za-z0-9_.]{3,17}'s information\\n.*",
		"\u270e [A-Za-z0-9_.]{3,17} has the following notes:.*",
		"That player does not have any notes\\.",
		// Punishment and evidence feedback
		"Punishment applied: .*",
		"Player punished! Click to add chat logs as evidence.*",
		"Successfully added evidence to punishment\\.",
		"Evidence added\\.",
		"Successfully deleted the evidence\\.",
		"Missing Evidence: .*",
		"Invalid punishment reason\\.",
		"You do not have permission to view other players' punishment history\\.",
		"Warning!\\nYou have \\d+ unresolved punishments.*",
		// CoreProtect lookups and its own messages
		"CoreProtect - .*",
		"-+ Container Transactions.*",
		"\\d+(?:\\.\\d+)?/[a-z]+ ago [-+] .*",
		"(?:\u25c0 ?)?Page \\d+/\\d+ ?\u25b6.*",
		"\\[Previous Page\\] \\| \\[Next Page\\].*",
		// Staff mode, staff chat switching, staff command broadcasts, staff stats, teleports and server notices
		"You (?:are now in|are no longer in|can only enable) mod mode.*",
		"(?:\u2714 |\u274c )?You are (?:now|no longer) talking in staff chat\\.",
		"You cannot use this command while in staff mode\\.",
		"\\[[A-Za-z0-9_.]{3,17}: .*\\]",
		"Playtime this week: .*",
		"(?:\\| )?(?:Total|Weekly) (?:Punishments|Bans): .*",
		"Teleported to [A-Za-z0-9_.]{3,17}\\.",
		"Teleporting you to [A-Za-z0-9_.]{3,17}\\.",
		"\u274c You cannot teleport to players on other servers\\.",
		"\u274c You don't have permission to teleport to offline players\\.",
		"\\[Server Information\\] .* has been (?:un-)?whitelisted\\.");
	static final String VANISH_TEXT = "You are currently Vanished";
	static final List<String> STAFF_MODE_ON = List.of("You are now in mod mode");
	static final List<String> STAFF_MODE_OFF = List.of("You are no longer in mod mode");

	/** While vanished or in mod mode, staff chat is drawn normally so it can be recorded. */
	public boolean revealWhileVanished = true;
	/** How long after the vanish text was last shown the player still counts as vanished. */
	public int vanishGraceSeconds = 3;
	/** Shows a short note (only on your screen) when hiding turns on or off. */
	public boolean showStatusMessages = true;
	/** More line starts to treat as staff chat, from the module's settings. */
	public List<String> extraStartsWith = List.of();

	private final List<Pattern> compiled;

	public StaffRules() {
		List<Pattern> result = new ArrayList<>();
		int flags = Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;
		for (String regex : DEFAULT_MATCHES) {
			try {
				result.add(Pattern.compile(regex, flags));
			} catch (PatternSyntaxException e) {
				StaffChat.LOGGER.warn("Ignoring an invalid staff message pattern {}: {}", regex, e.getDescription());
			}
		}
		compiled = List.copyOf(result);
	}

	/** True if this text (colour codes allowed) is a staff message. */
	public boolean isStaff(String raw) {
		String text = clean(raw);
		for (String prefix : DEFAULT_STARTS_WITH) if (startsWith(text, prefix)) return true;
		for (String prefix : extraStartsWith) if (!prefix.isEmpty() && startsWith(text, prefix)) return true;
		String lower = text.toLowerCase(Locale.ROOT);
		for (String part : DEFAULT_CONTAINS) if (lower.contains(part.toLowerCase(Locale.ROOT))) return true;
		for (Pattern pattern : compiled) if (pattern.matcher(text).matches()) return true;
		return false;
	}

	private static boolean startsWith(String text, String prefix) {
		return text.regionMatches(true, 0, prefix, 0, prefix.length());
	}

	/** True if this action bar text says you are vanished. */
	public boolean isVanishText(String raw) {
		return clean(raw).toLowerCase(Locale.ROOT).contains(VANISH_TEXT.toLowerCase(Locale.ROOT));
	}

	/** True if this chat message starts mod mode. Only the start of the line counts, so players cannot fake it. */
	public boolean isStaffModeOn(String raw) {
		String text = clean(raw);
		for (String p : STAFF_MODE_ON) if (startsWith(text, p)) return true;
		return false;
	}

	/** True if this chat message ends mod mode. */
	public boolean isStaffModeOff(String raw) {
		String text = clean(raw);
		for (String p : STAFF_MODE_OFF) if (startsWith(text, p)) return true;
		return false;
	}

	/**
	 * Removes legacy colour codes, then anything at the start that is not text: spaces of any kind,
	 * line breaks, and resource-pack icons (private-use characters, which servers put before messages).
	 */
	static String clean(String text) {
		String plain = text.replaceAll("\u00a7.", "");
		int start = 0;
		while (start < plain.length()) {
			int codePoint = plain.codePointAt(start);
			if (!Character.isWhitespace(codePoint) && !Character.isSpaceChar(codePoint) && Character.getType(codePoint) != Character.PRIVATE_USE) {
				break;
			}
			start += Character.charCount(codePoint);
		}
		return plain.substring(start);
	}
}
