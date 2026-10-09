package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.Time;
import dev.alfxyz.leoneclient.features.AnticheatAlerts;
import dev.alfxyz.leoneclient.features.Reports;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import org.jspecify.annotations.Nullable;

/**
 * Under the Staff modules: who was flagged recently and the latest reports and help requests, each with
 * a button to look at the player's profile and one to teleport to them (or join their server first,
 * since teleports do not cross servers). Names stay hidden while Staff Chat is hiding staff chat from
 * recordings, unless you choose to show them, because this page is part of what gets recorded.
 */
final class StaffSection {
	private static final float ROW = 46, GAP = 6, HEAD = 26, MAX_ROWS = 5;
	private final LeoneScreen screen;
	/** "Show anyway", for as long as this menu is open (a new one starts hidden again). */
	private boolean shownAnyway;

	private record Row(String name, String title, String detail, @Nullable String server, long at, int color) {
	}

	StaffSection(LeoneScreen screen) {
		this.screen = screen;
	}

	private boolean hiddenNames() {
		return StaffChat.isHidden() && !shownAnyway;
	}

	private List<Row> flagged() {
		List<Row> out = new ArrayList<>();
		if (!Modules.ANTICHEAT_ALERTS.active()) return out;
		for (AnticheatAlerts.Suspect s : Modules.ANTICHEAT_ALERTS.recentAll()) {
			if (out.size() >= MAX_ROWS) break;
			StringBuilder checks = new StringBuilder();
			for (var e : s.topChecks()) {
				if (!checks.isEmpty()) checks.append(", ");
				checks.append(e.getKey()).append(" ×").append(e.getValue());
			}
			out.add(new Row(s.name, s.name + "  ×" + s.total, checks.toString(), s.server, s.lastAt, 0xFFF87171));
		}
		return out;
	}

	private List<Row> reports() {
		List<Row> out = new ArrayList<>();
		if (!Modules.REPORTS.active()) return out;
		for (Reports.Entry e : Modules.REPORTS.recent()) {
			if (out.size() >= MAX_ROWS) break;
			out.add(new Row(e.name(), e.kind() + ": " + e.name(), e.detail(), e.server(), e.at(), e.kind().equals("Report") ? 0xFFF87171 : 0xFFFBBF24));
		}
		return out;
	}

	private static float columnHeight(int rows) {
		return rows == 0 ? 56 : rows * ROW + (rows - 1) * GAP;
	}

	float height(Ui ui) {
		if (!Modules.ANTICHEAT_ALERTS.active() && !Modules.REPORTS.active()) return 0;
		if (hiddenNames()) return 18 + 22 + 64;
		return 18 + 22 + Math.max(columnHeight(flagged().size()), columnHeight(reports().size()));
	}

	void draw(Ui ui, float x, float y, float w) {
		if (!Modules.ANTICHEAT_ALERTS.active() && !Modules.REPORTS.active()) return;
		float top = y + 18;
		if (hiddenNames()) {
			ui.caps("RECENT", x + 2, top);
			float by = top + 22;
			ui.cv.dashedBorderRoundRect(x, by, w, 64, 12, 1.5f, 4.5f, 3f, Colors.white(0.12f));
			ui.text.draw(ui.cv, "Names are hidden here while staff chat is hidden from recordings.", x + 16,
				ui.text.baselineFor(Ui.DESC, by + 32), Ui.DESC, Colors.TEXT_HINT);
			float bw = ui.buttonWidth("Show anyway", Icons.EYE, Ui.BUTTON);
			ui.button("staff#show", x + w - 16 - bw, by + 17, 30, "Show anyway", Icons.EYE, Ui.Btn.GHOST, true, () -> shownAnyway = true);
			ui.tip(x + w - 16 - bw, by + 17, bw, 30, "Shows the names until you close the menu. They will be in any recording.");
			return;
		}
		float colW = (w - 12) / 2;
		column(ui, "FLAGGED RECENTLY", flagged(), Modules.ANTICHEAT_ALERTS.active() ? "No anticheat alerts in the last few minutes" : "Anticheat Alerts is off",
			x, top, colW);
		column(ui, "REPORTS AND REQUESTS", reports(), Modules.REPORTS.active() ? "No reports or help requests in the last half hour" : "Reports is off",
			x + colW + 12, top, colW);
	}

	private void column(Ui ui, String title, List<Row> rows, String empty, float x, float y, float w) {
		ui.caps(title, x + 2, y);
		float ry = y + 22;
		if (rows.isEmpty()) {
			ui.cv.dashedBorderRoundRect(x, ry, w, 56, 12, 1.5f, 4.5f, 3f, Colors.white(0.12f));
			ui.centred(empty, Ui.SMALL, x, ry, w, 56, Colors.TEXT_HINT);
			return;
		}
		for (Row r : rows) {
			row(ui, r, x, ry, w);
			ry += ROW + GAP;
		}
	}

	private void row(Ui ui, Row r, float x, float y, float w) {
		ui.card(x, y, w, ROW);
		float hx = x + 10, hy = y + (ROW - HEAD) / 2;
		UUID uuid = uuidOf(r.name());
		if (uuid != null) {
			dev.alfxyz.leoneclient.render.Heads.draw(ui.cv, ui.text, uuid, r.name(), hx, hy, HEAD, 7);
		} else {
			ui.cv.fillRoundRect(hx, hy, HEAD, HEAD, 7, Colors.rgba(r.color() & 0xFFFFFF, 0.22f));
			String initial = r.name().isEmpty() ? "?" : r.name().substring(0, 1).toUpperCase(Locale.ROOT);
			ui.centred(initial, Ui.BODY_STRONG, hx, hy, HEAD, HEAD, r.color());
		}

		// buttons on the right: profile always, then teleport, or join their server first
		String here = LeoneMC.server();
		boolean elsewhere = r.server() != null && here != null && !r.server().equalsIgnoreCase(here);
		float bx = x + w - 10 - 26;
		ui.iconButton("staff#profile#" + r.name() + r.at(), bx, y + (ROW - 26) / 2, 26, Icons.USER, 14, Colors.TEXT_MUTED, Colors.WHITE,
			() -> lookUp(r.name()));
		ui.tip(bx, y + (ROW - 26) / 2, 26, 26, "Open " + r.name() + "'s LeoneMC profile");
		bx -= 26 + 4;
		String go = elsewhere ? "server " + r.server().toLowerCase(Locale.ROOT) : "tp " + r.name();
		ui.iconButton("staff#go#" + r.name() + r.at(), bx, y + (ROW - 26) / 2, 26, Icons.LOG_IN, 14, Colors.TEXT_MUTED, Colors.WHITE, () -> run(go));
		ui.tip(bx, y + (ROW - 26) / 2, 26, 26, elsewhere ? "Join " + r.server() + ", where " + r.name() + " is (then teleport from there)" : "Teleport to " + r.name());

		float tx = hx + HEAD + 10, maxW = bx - 8 - tx;
		String when = Time.ago(r.at()) + (r.server() != null ? " · " + r.server() : "");
		float lh = ui.text.lineHeight(Ui.BODY_STRONG), sh = ui.text.lineHeight(Ui.SMALL);
		float ty = y + (ROW - lh - sh) / 2;
		ui.text.draw(ui.cv, ui.text.fit(r.title(), Ui.BODY_STRONG, maxW), tx, ty + ui.text.ascent(Ui.BODY_STRONG), Ui.BODY_STRONG, Colors.TEXT);
		ui.text.draw(ui.cv, ui.text.fit(r.detail() + "  ·  " + when, Ui.SMALL, maxW), tx, ty + lh + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_HINT);
		ui.tip(tx, y, maxW, ROW, r.detail());
	}

	/** The UUID of a player on this server, for their head, or null. */
	private static @Nullable UUID uuidOf(String name) {
		var conn = Minecraft.getInstance().getConnection();
		if (conn == null) return null;
		PlayerInfo info = conn.getPlayerInfo(name);
		return info == null ? null : info.getProfile().id();
	}

	private void lookUp(String name) {
		UUID uuid = uuidOf(name);
		if (uuid != null) {
			screen.openPlayer(uuid, name);
			return;
		}
		dev.alfxyz.leoneclient.web.LeoneWeb.lookup(name).whenComplete((found, err) -> Minecraft.getInstance().execute(() -> {
			if (err == null && found.isPresent()) screen.openPlayer(found.get().uuid(), found.get().name());
		}));
	}

	private static void run(String command) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		mc.gui.setScreen(null);
		mc.getConnection().sendCommand(command);
	}
}
