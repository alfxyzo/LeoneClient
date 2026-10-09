package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/** Pops up LeoneMC's player reports, help requests and other staff alerts so they are not lost in chat. */
public final class Reports extends Module {
	public static final String REPORTS = "Reports", REQUESTS = "Help requests", VPN = "VPN warnings", BANNED = "Banned joins";
	/** "[Report] (WildKits) Name was reported by Other for hacking." */
	private static final Pattern REPORT = Pattern.compile("^\\[Report\\] \\(([^)]+)\\) (\\S+) was reported by (\\S+) for (.+?)\\.?$");
	/** "[Request] [WildKits] Name has requested staff assistance:\n  Actions: ...\n  Reason: text" */
	private static final Pattern REQUEST = Pattern.compile("^\\[Request\\] \\[([^\\]]+)\\] (\\S+) has requested staff assistance:.*?Reason: (.*)$", Pattern.DOTALL);
	private static final Pattern VPN_LINE = Pattern.compile("^✖ Warning: (\\S+) is suspected of using a VPN/Proxy\\..*$");
	private static final Pattern BANNED_LINE = Pattern.compile("^(\\S+) has (?:tried to join, but is banned|attempted to join but is currently banned)(.*)$");

	public final Setting.Chips kinds = add(new Setting.Chips("kinds", "Pop up for", "ALERTS", List.of(REPORTS, REQUESTS, VPN, BANNED), List.of(REPORTS, REQUESTS)),
		"Which staff alerts get a pop-up. Pop-ups are skipped while Staff Chat is hiding staff chat from recordings, so names never end up in a clip; the sound still plays.");
	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound", "ALERTS", true),
		"Plays a sound for each alert you chose above.");
	private int reports, requests;

	/** A report, help request or warning, for the Staff page: what it is, about whom, what was said, where and when. */
	public record Entry(String kind, String name, String detail, @Nullable String server, long at) {
	}

	private static final int KEEP = 20;
	private static final long KEEP_MS = 30 * 60_000;
	private final Deque<Entry> recent = new ArrayDeque<>();

	public Reports() {
		super("reports", Category.STAFF, "Reports", Icons.INBOX,
			"Pops up player reports and help requests, and optionally VPN warnings, so none slip past in a busy chat.", false);
	}

	public void track(String plain) {
		if (!active()) return;
		Matcher m = REPORT.matcher(plain);
		if (m.matches()) {
			reports++;
			remember(new Entry("Report", m.group(2), m.group(4) + " (by " + m.group(3) + ")", m.group(1), System.currentTimeMillis()));
			alert(REPORTS, "Report · " + m.group(1), m.group(2) + " for " + m.group(4) + " (by " + m.group(3) + ")", 0xF87171, Icons.FLAG);
			return;
		}
		m = REQUEST.matcher(plain);
		if (m.matches()) {
			requests++;
			remember(new Entry("Help request", m.group(2), m.group(3).strip(), m.group(1), System.currentTimeMillis()));
			alert(REQUESTS, "Help request · " + m.group(1), m.group(2) + ": " + m.group(3).strip(), 0xFBBF24, Icons.INBOX);
			return;
		}
		m = VPN_LINE.matcher(plain);
		if (m.matches()) {
			alert(VPN, "VPN warning", m.group(1) + " may be using a VPN or proxy", 0xA78BFA, Icons.ALERT);
			return;
		}
		m = BANNED_LINE.matcher(plain);
		if (m.matches()) alert(BANNED, "Banned player", m.group(1) + " tried to join", 0x9CA3AF, Icons.ALERT);
	}

	private void remember(Entry e) {
		recent.addFirst(e);
		while (recent.size() > KEEP) recent.removeLast();
	}

	/** Reports and help requests from the last half hour, newest first. */
	public List<Entry> recent() {
		long now = System.currentTimeMillis();
		recent.removeIf(e -> now - e.at() > KEEP_MS);
		return List.copyOf(recent);
	}

	private void alert(String kind, String title, String detail, int color, String icon) {
		if (!kinds.has(kind)) return;
		if (sound.get()) Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_CHIME.value(), 1.2f, 0.8f));
		if (!StaffChat.isHidden()) Notices.push(title, detail, color, icon);
	}

	@Override
	public String status() {
		if (reports + requests == 0) return null;
		return reports + (reports == 1 ? " report, " : " reports, ") + requests + (requests == 1 ? " request" : " requests");
	}
}
