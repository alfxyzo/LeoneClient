package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Hides kinds of LeoneMC "[Alert]" broadcasts. Proxy restart warnings are always kept. */
public final class AlertFilter extends Module {
	public static final String STAFF = "Staff recruitment", PURCHASES = "Purchases", EVENTS = "Events", STREAMS = "Streams", TIPS = "Tips", OTHER = "Other";

	public final Setting.Chips hide = add(new Setting.Chips("hide", "Hide", "ALERTS",
			List.of(STAFF, PURCHASES, EVENTS, STREAMS, TIPS, OTHER), List.of(STAFF)),
		"Kinds of [Alert] message to hide. Warnings that the proxy is restarting always show.");
	private int hidden;

	public AlertFilter() {
		super("alert_filter", Category.CHAT, "Alert Filter", Icons.BELL_OFF,
			"Hides the [Alert] broadcasts you do not care about, like the staff recruitment advert.", true);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	/** The kind of an alert line, or null if the line is not an alert (or must never be hidden). */
	public static @Nullable String classify(String plain) {
		if (!plain.startsWith("[Alert]")) return null;
		String l = plain.toLowerCase(Locale.ROOT);
		if (l.contains("restarting")) return null;
		if (l.contains("looking for staff")) return STAFF;
		if (l.contains("has purchased")) return PURCHASES;
		if (l.contains("event") && (l.contains("starting in") || l.contains("click to join"))) return EVENTS;
		if (l.contains("streaming")) return STREAMS;
		if (l.startsWith("[alert] want to") || l.contains("found a bug")) return TIPS;
		return OTHER;
	}

	public boolean hides(String plain) {
		if (!active()) return false;
		String kind = classify(plain);
		if (kind == null || !hide.has(kind)) return false;
		hidden++;
		return true;
	}

	@Override
	public String status() {
		return hidden == 0 ? null : hidden == 1 ? "1 alert hidden" : hidden + " alerts hidden";
	}
}
