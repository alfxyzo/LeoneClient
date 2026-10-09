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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** Any LeoneMC player's profile from leonemc.net: rank, where they are, playtime and stats. */
final class PlayersPage extends Page {
	private static final float CARD_H = 112, LIST_H = 400, TAB_H = 30, TILE_H = 64, TILE_GAP = 8;
	private static final int COLS = 4;
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
	/** Which game mode's stats are shown. */
	private int mode;

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
		mode = 0;
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
				// you first, so two players listed under one name (as can happen on leonemc.net) are told apart
				List<Player> sorted = new ArrayList<>(list.subList(0, Math.min(6, list.size())));
				sorted.sort(Comparator.comparing((Player sp) -> !isYou(sp.uuid())));
				suggestions = sorted;
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

	/** The stat grid of the chosen game mode: four tiles to a row. */
	private static float gridHeight(StatCard c) {
		int rows = (c.stats().size() + COLS - 1) / COLS;
		return rows == 0 ? 0 : rows * TILE_H + (rows - 1) * TILE_GAP;
	}

	private @Nullable StatCard chosen(Profile p) {
		List<StatCard> cards = p.stats();
		if (cards.isEmpty()) return null;
		return cards.get(Math.min(mode, cards.size() - 1));
	}

	/** Height of the grid's window; it scrolls when a game mode has more stats than fit. */
	private float gridWindow(Profile p) {
		StatCard c = chosen(p);
		return c == null ? 0 : Math.min(LIST_H - TAB_H - 12, gridHeight(c));
	}

	private float maxScroll(Ui ui) {
		Profile p = shown == null ? null : Profiles.get(shown);
		StatCard c = p == null ? null : chosen(p);
		return c == null ? 0 : Math.max(0, gridHeight(c) - gridWindow(p));
	}

	/** Everything below the profile card: the game mode tabs and the grid, or the empty note. */
	private float listHeight(Ui ui) {
		Profile p = shown == null ? null : Profiles.get(shown);
		if (p == null) return 0;
		if (p.stats().isEmpty()) return 72;
		return TAB_H + 12 + gridWindow(p);
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

	private static boolean isYou(UUID uuid) {
		return uuid.equals(Minecraft.getInstance().getUser().getProfileId());
	}

	private void suggestion(Ui ui, Player p, float x, float y) {
		boolean hov = ui.hovered(x, y, W, 36);
		ColorAnim bg = ui.color("pl-sug#" + p.uuid(), Colors.white(0.035f));
		bg.set(Colors.white(hov ? 0.08f : 0.035f), ui.now, 120, Ease.EASE);
		ui.cv.fillRoundRect(x, y, W, 36, 10, bg.get(ui.now));
		Heads.draw(ui.cv, ui.text, p.uuid(), p.name(), x + 8, y + 6, 24, 6);
		ui.text.draw(ui.cv, p.name(), x + 8 + 24 + 10, ui.text.baselineFor(Ui.BODY, y + 18), Ui.BODY, Colors.TEXT);
		String badge = isYou(p.uuid()) ? "You" : Friends.isFriend(p.uuid()) ? "Friend" : null;
		if (badge != null) ui.badge(badge, x + W - 10 - ui.badgeWidth(badge), y + 18, Colors.ACCENT_RGB, true);
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
		ui.text.draw(ui.cv, p.name(), tx, top + ui.text.ascent(Ui.H2), Ui.H2, readable(p.color()));
		float bx = tx + ui.text.width(p.name(), Ui.H2) + 10, by = top + ui.text.lineHeight(Ui.H2) / 2 + 1;
		if (!p.rank().isEmpty()) bx += ui.badge(p.rank(), bx, by, p.rankColor(), true) + 6;
		if (isYou(p.uuid())) ui.badge("You", bx, by, Colors.ACCENT_RGB, false);
		else if (Friends.isFriend(p.uuid())) ui.badge("Friend", bx, by, Colors.ACCENT_RGB, false);
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
		// one tab per game mode
		float tx = x;
		for (int i = 0; i < cards.size(); i++) {
			String title = cards.get(i).title();
			float tw = ui.text.width(title, Ui.BUTTON) + 26;
			if (tx + tw > x + W) break;
			boolean on = i == Math.min(mode, cards.size() - 1);
			boolean hov = ui.hovered(tx, ly, tw, TAB_H);
			ColorAnim bg = ui.color("pl-tab#" + i, on ? Colors.accent(0.22f) : Colors.white(0.04f));
			bg.set(on ? Colors.accent(0.22f) : Colors.white(hov ? 0.09f : 0.04f), ui.now, 140, Ease.EASE);
			ui.cv.fillRoundRect(tx, ly, tw, TAB_H, TAB_H / 2, bg.get(ui.now));
			ui.cv.borderRoundRect(tx, ly, tw, TAB_H, TAB_H / 2, 1, on ? Colors.accent(0.6f) : Colors.white(0.08f));
			ui.text.draw(ui.cv, title, tx + 13, ui.text.baselineFor(Ui.BUTTON, ly + TAB_H / 2), Ui.BUTTON, on ? Colors.WHITE : Colors.TEXT_SECONDARY);
			int index = i;
			ui.hit(tx, ly, tw, TAB_H, () -> {
				mode = index;
				scroll.snap(0);
			}, null);
			tx += tw + 6;
		}

		StatCard card = chosen(p);
		float gy = ly + TAB_H + 12, gh = gridWindow(p);
		float off = Math.min(scroll.get(ui.now), maxScroll(ui));
		float tileW = (W - (COLS - 1) * TILE_GAP) / COLS;
		ui.cv.push();
		ui.cv.clipRect(x - 2, gy - 1, x + W + 2, gy + gh + 1);
		List<Stat> stats = card.stats();
		for (int i = 0; i < stats.size(); i++) {
			float sx = x + (i % COLS) * (tileW + TILE_GAP), sy = gy + (i / COLS) * (TILE_H + TILE_GAP) - off;
			if (sy + TILE_H < gy - 1 || sy > gy + gh + 1) continue;
			statTile(ui, stats.get(i), sx, sy, tileW);
		}
		ui.cv.pop();
		float max = maxScroll(ui);
		if (max > 0) {
			float th = Math.max(24, gh * gh / (gh + max)), tt = gy + (gh - th) * (off / max);
			ui.cv.fillRoundRect(x + W + 6, tt, 3, th, 1.5f, Colors.white(0.18f));
		}
	}

	/** A colour from the website, lifted towards white when it is too dark to read on the menu's dark cards. */
	private static int readable(int rgb) {
		int r = (rgb >> 16) & 255, g = (rgb >> 8) & 255, b = rgb & 255;
		float luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f;
		int argb = 0xFF000000 | rgb;
		return luma >= 0.45f ? argb : Colors.lerp(argb, Colors.WHITE, Math.min(0.6f, 0.45f - luma + 0.2f));
	}

	/** One stat: what it is, its value, and the player's place on that leaderboard. */
	private static void statTile(Ui ui, Stat s, float x, float y, float w) {
		ui.card(x, y, w, TILE_H);
		float ix = x + 12, iw = w - 24;
		float rw = s.rank().isEmpty() ? 0 : ui.text.width(s.rank(), Ui.SMALL) + 8;
		float labelY = y + 11;
		ui.text.draw(ui.cv, ui.text.fit(s.label(), Ui.SMALL, iw - rw), ix, labelY + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_MUTED);
		if (rw > 0) {
			ui.text.draw(ui.cv, s.rank(), ix + iw - rw + 8, labelY + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_DIM);
			ui.tip(ix + iw - rw, labelY - 2, rw, 18, "Place on the leaderboard for " + s.label());
		}
		float valueY = y + TILE_H - 12 - ui.text.lineHeight(Ui.STAT);
		ui.text.draw(ui.cv, ui.text.fit(s.value(), Ui.STAT, iw), ix, valueY + ui.text.ascent(Ui.STAT), Ui.STAT, readable(s.color()));
		if (ui.text.width(s.label(), Ui.SMALL) > iw - rw) ui.tip(x, y, w, TILE_H, s.label() + ": " + s.value());
	}
}
