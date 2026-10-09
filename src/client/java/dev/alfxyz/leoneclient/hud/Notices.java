package dev.alfxyz.leoneclient.hud;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** Short pop-up notifications, drawn by the Notifications overlay. */
public final class Notices {
	/** How long a pop-up shows, and how many show at once; set from the Notifications overlay's settings. */
	public static volatile double showMs = 4500;
	public static volatile int max = 5;

	/** {@code head} shows a player's face instead of {@code icon}. */
	public record Notice(String title, String detail, int color, @Nullable String icon, @Nullable UUID head, double at) {
	}

	private static final List<Notice> list = new ArrayList<>();

	private Notices() {
	}

	private static double now() {
		return System.nanoTime() / 1e6;
	}

	public static synchronized void push(String title, String detail, int color, @Nullable String icon) {
		add(new Notice(title, detail, color, icon, null, now()));
	}

	public static synchronized void player(String title, String detail, int color, UUID head) {
		add(new Notice(title, detail, color, null, head, now()));
	}

	private static void add(Notice n) {
		// the same message again replaces the old one instead of stacking
		list.removeIf(o -> o.title().equals(n.title()) && o.detail().equals(n.detail()));
		list.add(n);
		while (list.size() > Math.max(1, max)) list.removeFirst();
	}

	/** Notices still showing, oldest first. */
	public static synchronized List<Notice> active() {
		double now = now();
		list.removeIf(n -> now - n.at() > showMs);
		while (list.size() > Math.max(1, max)) list.removeFirst();
		return new ArrayList<>(list);
	}
}
