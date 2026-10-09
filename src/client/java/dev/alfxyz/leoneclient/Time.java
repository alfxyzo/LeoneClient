package dev.alfxyz.leoneclient;

/** Short human-readable times. */
public final class Time {
	private Time() {
	}

	/** "just now", "5 min ago", "3 h ago", "2 days ago", "4 months ago", "2 years ago". */
	public static String ago(long millis) {
		long s = Math.max(0, (System.currentTimeMillis() - millis) / 1000);
		if (s < 60) return "just now";
		if (s < 3600) return s / 60 + " min ago";
		if (s < 86400) return s / 3600 + " h ago";
		if (s < 86400 * 60) return s / 86400 + (s / 86400 == 1 ? " day ago" : " days ago");
		long months = s / (86400 * 30);
		if (months < 24) return months + " months ago";
		return s / (86400 * 365) + " years ago";
	}

	/** A countdown such as "4:05" or "1:02:30". */
	public static String clock(long ms) {
		long s = Math.max(0, (ms + 999) / 1000);
		long h = s / 3600, m = s / 60 % 60, sec = s % 60;
		return h > 0 ? String.format(java.util.Locale.ROOT, "%d:%02d:%02d", h, m, sec) : String.format(java.util.Locale.ROOT, "%d:%02d", m, sec);
	}
}
