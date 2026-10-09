package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.Time;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.render.Heads;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Friends;
import dev.alfxyz.leoneclient.web.LeoneWeb;
import dev.alfxyz.leoneclient.web.Names;
import dev.alfxyz.leoneclient.web.LeoneWeb.Player;
import dev.alfxyz.leoneclient.web.LeoneWeb.Profile;
import dev.alfxyz.leoneclient.web.LeoneWeb.Stat;
import dev.alfxyz.leoneclient.web.LeoneWeb.StatCard;
import dev.alfxyz.leoneclient.web.Profiles;
import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** Any LeoneMC player's profile from leonemc.net: rank, where they are, playtime and stats. */
final class PlayersPage extends Page {
	private static final float CARD_H = 112, STAT_ROW = 24, STAT_GAP = 10, LIST_H = 400;
	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH).withZone(ZoneId.systemDefault());
	final TextInput query = new TextInput(17, c -> Character.isLetterOrDigit(c) || c == '_' || c == '.');
	private List<Player> suggestions = List.of();
	private String suggestedFor = "";
	private double typedAt;
	private boolean searching;
	private @Nullable String searchError;
	private @Nullable UUID shown;
	private String shownName = "";
	private final Anim scroll = new Anim(0);

	PlayersPage(LeoneScreen screen) {
		super(screen);
	}

	@Override
	@Nullable TextInput input() {
		return query;
	}

	@Override
	void opened() {
		scroll.snap(0);
		if (shown == null) screen.focus(query);
	}

	/** Shows a player's profile straight away. */
	void show(UUID uuid, String name) {
		shown = uuid;
		shownName = name;
		scroll.snap(0);
		Profiles.want(uuid, 60_000);
	}

	@Override
	void submit() {
		String q = query.value().strip();
		if (q.isEmpty()) return;
		// an exact name goes through the lookup, which picks the right one when two players share it
		boolean exact = suggestions.stream().anyMatch(p -> p.name().equalsIgnoreCase(q));
		if (!exact && !suggestions.isEmpty() && suggestedFor.equalsIgnoreCase(q)) {
			pick(suggestions.getFirst());
			return;
		}
		searching = true;
		LeoneWeb.lookup(q).whenComplete((found, err) -> Minecraft.getInstance().execute(() -> {
			searching = false;
			if (err != null) searchError = "Could not reach leonemc.net";
			else if (found.isEmpty()) searchError = "No LeoneMC player called " + q;
			else pick(found.get());
		}));
	}

	private void pick(Player p) {
		query.clear();
		suggestions = List.of();
		suggestedFor = "";
		searchError = null;
		show(p.uuid(), p.name());
		screen.focus(null);
	}

	/** Asks the website for name suggestions a quarter of a second after typing stops. */
	private void updateSuggestions(Ui ui) {
		String q = query.value().strip();
		if (q.length() < 2) {
			suggestions = List.of();
			suggestedFor = q;
			return;
		}
		if (q.equals(suggestedFor) || searching) return;
		if (!q.equals(lastTyped)) {
			lastTyped = q;
			typedAt = ui.now;
			return;
		}
		if (ui.now - typedAt < 250) return;
		suggestedFor = q;
		searching = true;
		LeoneWeb.search(q).whenComplete((list, err) -> Minecraft.getInstance().execute(() -> {
			searching = false;
			if (err == null && q.equals(query.value().strip())) {
				suggestions = list.subList(0, Math.min(6, list.size()));
				searchError = null;
			}
		}));
	}

	private String lastTyped = "";

	@Override
	boolean scroll(double amount) {
		float target = Math.max(0, Math.min(maxScroll(screen.ui()), scroll.target() - (float) amount * 40));
		scroll.set(target, System.nanoTime() / 1e6, 180, Ease.SNAP);
		return true;
	}

	// ------------------------------------------------------------------ layout

	private static int columns() {
		return 3;
	}

	private static float statCardHeight(Ui ui, StatCard c) {
		return 14 + ui.text.lineHeight(Ui.CAPS) + 8 + c.stats().size() * STAT_ROW + 10;
	}

	/** Column layout of the stat cards: x column and y offset of each card, and the total height. */
	private static float[][] statLayout(Ui ui, List<StatCard> cards) {
		float colW = (W - 16) / 3;
		float[] col = new float[columns()];
		float[][] out = new float[cards.size() + 1][];
		for (int i = 0; i < cards.size(); i++) {
			int c = 0;
			for (int k = 1; k < col.length; k++) if (col[k] < col[c]) c = k;
			out[i] = new float[] {c * (colW + 8), col[c], colW, statCardHeight(ui, cards.get(i))};
			col[c] += out[i][3] + STAT_GAP;
		}
		float max = 0;
		for (float v : col) max = Math.max(max, v);
		out[cards.size()] = new float[] {0, Math.max(0, max - STAT_GAP)};
		return out;
	}

	private float contentHeight(Ui ui) {
		Profile p = shown == null ? null : Profiles.get(shown);
		if (p == null) return 0;
		List<StatCard> cards = p.stats();
		if (cards.isEmpty()) return 72;
		return statLayout(ui, cards)[cards.size()][1];
	}

	private float maxScroll(Ui ui) {
		return Math.max(0, contentHeight(ui) - LIST_H);
	}

	private float listHeight(Ui ui) {
		return Math.min(LIST_H, contentHeight(ui));
	}

	private float suggestionsHeight() {
		return suggestions.isEmpty() || shown != null && query.value().isBlank() ? 0 : 12 + suggestions.size() * 40;
	}

	@Override
	float height(Ui ui) {
		float h = 38 + 18 + 38 + suggestionsHeight();
		if (shown == null) return h + (suggestions.isEmpty() ? 18 + 96 : 0);
		return h + 18 + CARD_H + (listHeight(ui) > 0 ? 18 + listHeight(ui) : 0);
	}

	// --------------------------------------------------------------------- draw

	@Override
	void draw(Ui ui, float x, float y) {
		updateSuggestions(ui);
		ui.header(Icons.USER, "Players", x, y);
		String hint = "Profiles from leonemc.net";
		ui.text.draw(ui.cv, hint, x + W - ui.text.width(hint, Ui.HINT), ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_HINT);

		float sy = y + 38 + 18;
		String status = searchError != null && !query.value().isBlank() ? searchError : searching ? "Searching…" : null;
		ui.entering(ui.stagger(0), () -> ui.input(query, x, sy, W, 38, "Look up a player by name, then press Enter", Icons.SEARCH, status));
		float cy = sy + 38;
		if (suggestionsHeight() > 0) {
			cy += 12;
			for (int i = 0; i < suggestions.size(); i++) {
				Player p = suggestions.get(i);
				float ry = cy + i * 40;
				suggestion(ui, p, x, ry);
			}
			cy += suggestions.size() * 40;
		}
		if (shown == null) {
			if (suggestions.isEmpty()) {
				float by = cy + 18;
				ui.cv.dashedBorderRoundRect(x, by, W, 96, 12, 1.5f, 4.5f, 3f, Colors.white(0.12f));
				ui.centred("Type a name to see their rank, where they are, playtime and stats.", Ui.DESC, x, by, W, 96, Colors.TEXT_HINT);
			}
			return;
		}
		Profiles.want(shown, 60_000);
		Profile p = Profiles.get(shown);
		float py = cy + 18;
		final float cardY = py;
		ui.entering(ui.stagger(1), () -> profileCard(ui, p, x, cardY));
		if (p == null) return;
		float ly = py + CARD_H + 18;
		ui.entering(ui.stagger(2), () -> stats(ui, p, x, ly));
	}

	private void suggestion(Ui ui, Player p, float x, float y) {
		boolean hov = ui.hovered(x, y, W, 36);
		ColorAnim bg = ui.color("pl-sug#" + p.uuid(), Colors.white(0.035f));
		bg.set(Colors.white(hov ? 0.08f : 0.035f), ui.now, 120, Ease.EASE);
		ui.cv.fillRoundRect(x, y, W, 36, 10, bg.get(ui.now));
		Heads.draw(ui.cv, ui.text, p.uuid(), p.name(), x + 8, y + 6, 24, 6);
		ui.text.draw(ui.cv, p.name(), x + 8 + 24 + 10, ui.text.baselineFor(Ui.BODY, y + 18), Ui.BODY, Colors.TEXT);
		if (Friends.isFriend(p.uuid())) ui.badge("Friend", x + W - 10 - ui.badgeWidth("Friend"), y + 18, Colors.ACCENT_RGB, true);
		ui.hit(x, y, W, 36, () -> pick(p), null);
	}

	private void profileCard(Ui ui, @Nullable Profile p, float x, float y) {
		ui.card(x, y, W, CARD_H);
		float hs = 72, hx = x + 20, hy = y + (CARD_H - hs) / 2;
		Heads.draw(ui.cv, ui.text, shown, p != null ? p.name() : shownName, hx, hy, hs, 12);
		float tx = hx + hs + 20;
		if (p == null) {
			String msg = Profiles.missing(shown) ? shownName + " has never played on LeoneMC."
				: Profiles.failed(shown) ? "Could not reach leonemc.net." : "Loading " + shownName + "…";
			ui.text.draw(ui.cv, msg, tx, ui.text.baselineFor(Ui.BODY, y + CARD_H / 2), Ui.BODY, Colors.TEXT_HINT);
			return;
		}
		float top = y + 20;
		ui.text.draw(ui.cv, p.name(), tx, top + ui.text.ascent(Ui.H2), Ui.H2, 0xFF000000 | p.color());
		float bx = tx + ui.text.width(p.name(), Ui.H2) + 10, by = top + ui.text.lineHeight(Ui.H2) / 2 + 1;
		if (!p.rank().isEmpty()) bx += ui.badge(p.rank(), bx, by, p.rankColor(), true) + 6;
		if (Friends.isFriend(p.uuid())) ui.badge("Friend", bx, by, Colors.ACCENT_RGB, false);
		float ly = top + ui.text.lineHeight(Ui.H2) + 6;
		String presence = p.online() ? p.server() != null ? "Online on " + p.server() : "Online" : p.lastSeen() > 0 ? "Last seen " + Time.ago(p.lastSeen()) : "Offline";
		ui.cv.fillCircle(tx + 4, ly + ui.text.lineHeight(Ui.BODY) / 2, 4, p.online() ? 0xFF4ADE80 : 0xFF6B6B73);
		ui.text.draw(ui.cv, presence, tx + 14, ly + ui.text.ascent(Ui.BODY), Ui.BODY, p.online() ? 0xFF86EFAC : Colors.TEXT_MUTED);
		ly += ui.text.lineHeight(Ui.BODY) + 4;
		StringBuilder facts = new StringBuilder();
		if (p.joined() > 0) facts.append("Joined ").append(DATE.format(Instant.ofEpochMilli(p.joined())));
		if (p.playtimeMs() > 0) facts.append(facts.isEmpty() ? "" : "  ·  ").append(playtime(p.playtimeMs())).append(" played");
		facts.append(facts.isEmpty() ? "" : "  ·  ").append(p.friends().size()).append(p.friends().size() == 1 ? " friend" : " friends");
		ui.text.draw(ui.cv, facts.toString(), tx, ly + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_HINT);

		float rx = x + W - 16;
		float ow = ui.buttonWidth("leonemc.net", Icons.EXTERNAL, Ui.BUTTON);
		rx -= ow;
		UUID id = p.uuid();
		ui.button("pl-open", rx, y + 16, 30, "leonemc.net", Icons.EXTERNAL, Ui.Btn.GHOST, true, () -> Util.getPlatform().openUri(LeoneWeb.profileUrl(id)));
		if (p.online() && LeoneMC.connected()) {
			float mw = ui.buttonWidth("Message", Icons.MESSAGE, Ui.BUTTON);
			rx -= mw + 6;
			String name = p.name();
			ui.button("pl-msg", rx, y + 16, 30, "Message", Icons.MESSAGE, Ui.Btn.ACCENT, true, () -> Names.message(id, name));
		}
	}

	private static String playtime(long ms) {
		long hours = ms / 3_600_000;
		if (hours >= 24) return NumberFormat.getIntegerInstance(Locale.ENGLISH).format(hours / 24) + "d " + hours % 24 + "h";
		return hours > 0 ? hours + "h " + ms / 60_000 % 60 + "m" : Math.max(1, ms / 60_000) + "m";
	}

	private void stats(Ui ui, Profile p, float x, float ly) {
		List<StatCard> cards = p.stats();
		if (cards.isEmpty()) {
			ui.cv.dashedBorderRoundRect(x, ly, W, 72, 12, 1.5f, 4.5f, 3f, Colors.white(0.12f));
			ui.centred("No statistics yet.", Ui.DESC, x, ly, W, 72, Colors.TEXT_HINT);
			return;
		}
		float lh = listHeight(ui);
		float off = Math.min(scroll.get(ui.now), maxScroll(ui));
		float[][] layout = statLayout(ui, cards);
		ui.cv.push();
		ui.cv.clipRect(x - 2, ly - 1, x + W + 2, ly + lh + 1);
		for (int i = 0; i < cards.size(); i++) {
			float[] r = layout[i];
			float cy = ly + r[1] - off;
			if (cy + r[3] < ly - 1 || cy > ly + lh + 1) continue;
			statCard(ui, cards.get(i), x + r[0], cy, r[2], r[3]);
		}
		ui.cv.pop();
		float max = maxScroll(ui);
		if (max > 0) {
			float frac = lh / (lh + max);
			float th = Math.max(24, lh * frac), tt = ly + (lh - th) * (off / max);
			ui.cv.fillRoundRect(x + W + 6, tt, 3, th, 1.5f, Colors.white(0.18f));
		}
	}

	private static void statCard(Ui ui, StatCard c, float x, float y, float w, float h) {
		ui.card(x, y, w, h);
		float ix = x + 14, iw = w - 28;
		float cy = y + 14;
		ui.caps(c.title().toUpperCase(Locale.ROOT), ix, cy);
		cy += ui.text.lineHeight(Ui.CAPS) + 8;
		for (Stat s : c.stats()) {
			float mid = cy + STAT_ROW / 2;
			ui.text.draw(ui.cv, ui.text.fit(s.label(), Ui.SMALL, iw * 0.5f), ix, ui.text.baselineFor(Ui.SMALL, mid), Ui.SMALL, Colors.TEXT_MUTED);
			float vw = ui.text.width(s.value(), Ui.BODY_STRONG);
			ui.text.draw(ui.cv, s.value(), ix + iw - vw, ui.text.baselineFor(Ui.BODY_STRONG, mid), Ui.BODY_STRONG, 0xFF000000 | s.color());
			if (!s.rank().isEmpty()) {
				float rw = ui.text.width(s.rank(), Ui.SMALL);
				ui.text.draw(ui.cv, s.rank(), ix + iw - vw - 8 - rw, ui.text.baselineFor(Ui.SMALL, mid), Ui.SMALL, Colors.TEXT_DIM);
			}
			cy += STAT_ROW;
		}
	}
}
