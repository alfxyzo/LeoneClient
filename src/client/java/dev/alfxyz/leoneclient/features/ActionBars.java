package dev.alfxyz.leoneclient.features;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * The last action bar messages the server sent, split into the combat tag
 * line and everything else, and the combat tag countdown parsed from it.
 */
public final class ActionBars {
	/** How long a message stays up without being refreshed, like the vanilla action bar. */
	public static final long SHOW_MS = 3500;
	private static final long FADE_MS = 500;
	/** The combat line refreshes several times a second while tagged; after this long it has stopped. */
	private static final long TAG_STALE_MS = 1500;
	private static final Pattern MM_SS = Pattern.compile("(\\d+):(\\d{2})s?\\s*$");
	private static final Pattern SECS = Pattern.compile("(\\d+(?:\\.\\d+)?)s?\\s*$");

	private static @Nullable Component regular, combat, latest;
	private static long regularAt, combatAt, latestAt;
	private static long tagEnd;

	private ActionBars() {
	}

	/** Whether an action bar message is LeoneMC's combat tag line, like "Combat Tag | 14.5". */
	public static boolean isCombat(String plain) {
		return plain.contains("Combat");
	}

	public static void accept(Component message, boolean isCombat) {
		long now = System.currentTimeMillis();
		latest = message;
		latestAt = now;
		if (!isCombat) {
			regular = message;
			regularAt = now;
			return;
		}
		combat = message;
		combatAt = now;
		float secs = parseSeconds(message.getString());
		if (secs > 0) tagEnd = now + (long) (secs * 1000);
	}

	private static @Nullable Component fresh(@Nullable Component c, long at) {
		return c != null && System.currentTimeMillis() - at < SHOW_MS ? c : null;
	}

	public static @Nullable Component regular() {
		return fresh(regular, regularAt);
	}

	public static @Nullable Component combat() {
		return fresh(combat, combatAt);
	}

	public static @Nullable Component latest() {
		return fresh(latest, latestAt);
	}

	/** Opacity for a message received at {@code at}: fades out over the last half second. */
	public static float fade(long at) {
		long left = SHOW_MS - (System.currentTimeMillis() - at);
		return Math.max(0, Math.min(1, left / (float) FADE_MS));
	}

	public static long regularAt() {
		return regularAt;
	}

	public static long combatAt() {
		return combatAt;
	}

	public static long latestAt() {
		return latestAt;
	}

	/** True while the combat tag is counting down. */
	public static boolean tagged() {
		long now = System.currentTimeMillis();
		return tagEnd > now && now - combatAt < TAG_STALE_MS;
	}

	public static float remainingSeconds() {
		return Math.max(0, (tagEnd - System.currentTimeMillis()) / 1000f);
	}

	public static void clear() {
		regular = combat = latest = null;
		tagEnd = 0;
	}

	static float parseSeconds(String raw) {
		String clean = raw.replaceAll("§.", "").strip();
		Matcher m = MM_SS.matcher(clean);
		if (m.find()) {
			try {
				return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2));
			} catch (NumberFormatException ignored) {
			}
		}
		m = SECS.matcher(clean);
		if (m.find()) {
			try {
				return Float.parseFloat(m.group(1));
			} catch (NumberFormatException ignored) {
			}
		}
		return 0;
	}
}
