package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.render.Heads;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Friends;
import dev.alfxyz.leoneclient.web.LeoneWeb;
import dev.alfxyz.leoneclient.web.LeoneWeb.Friend;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** Your LeoneMC friends, from your profile on leonemc.net, with where they are right now. */
final class FriendsPage extends Page {
	private static final float TILE_W = (W - 16) / 3, TILE_H = 56, GAP = 8, BAR_H = 52, LIST_H = 4 * TILE_H + 3 * GAP + 30;
	private static final long MANUAL_REFRESH_MS = 10_000;
	final TextInput add = new TextInput(16, c -> Character.isLetterOrDigit(c) || c == '_');
	final TextInput account = new TextInput(16, c -> Character.isLetterOrDigit(c) || c == '_' || c == '.');
	private boolean editingAccount;
	private @Nullable String accountError;
	private @Nullable String sent;
	private long lastManualRefresh;
	private final Anim scroll = new Anim(0);

	FriendsPage(LeoneScreen screen) {
		super(screen);
	}

	@Override
	@Nullable TextInput input() {
		return editingAccount ? account : add;
	}

	@Override
	void opened() {
		scroll.snap(0);
		editingAccount = false;
		accountError = null;
		Friends.refresh(false);
	}

	// ------------------------------------------------------------------ layout

	private record Section(String title, List<Friend> friends) {
	}

	private static List<Section> sections() {
		List<Friend> online = new ArrayList<>(), offline = new ArrayList<>();
		for (Friend f : Friends.sorted()) (Friends.online(f) ? online : offline).add(f);
		List<Section> out = new ArrayList<>();
		if (!online.isEmpty()) out.add(new Section("ONLINE · " + online.size(), online));
		if (!offline.isEmpty()) out.add(new Section("OFFLINE · " + offline.size(), offline));
		return out;
	}

	private static float sectionHeight(Ui ui, Section s) {
		int rows = (s.friends().size() + 2) / 3;
		return ui.text.lineHeight(Ui.CAPS) + 10 + rows * TILE_H + (rows - 1) * GAP;
	}

	private static float contentHeight(Ui ui) {
		float h = 0;
		List<Section> list = sections();
		for (int i = 0; i < list.size(); i++) h += (i > 0 ? 16 : 0) + sectionHeight(ui, list.get(i));
		return h;
	}

	private float maxScroll(Ui ui) {
		return Math.max(0, contentHeight(ui) - LIST_H);
	}

	private static boolean hasList() {
		return !Friends.list().isEmpty();
	}

	private float listHeight(Ui ui) {
		return hasList() ? Math.min(LIST_H, contentHeight(ui)) : 96;
	}

	@Override
	float height(Ui ui) {
		return 38 + 18 + BAR_H + 14 + 38 + 18 + listHeight(ui);
	}

	@Override
	boolean scroll(double amount) {
		Ui ui = screen.ui();
		float target = Math.max(0, Math.min(maxScroll(ui), scroll.target() - (float) amount * 46));
		scroll.set(target, System.nanoTime() / 1e6, 180, Ease.SNAP);
		return true;
	}

	// ------------------------------------------------------------------ actions

	private boolean canAdd() {
		String n = add.value().strip();
		return LeoneMC.connected() && n.length() >= 3 && !Friends.isFriend(n);
	}

	@Override
	void submit() {
		if (editingAccount) {
			String n = account.value().strip();
			accountError = null;
			if (n.isEmpty()) {
				Friends.setAccount(null);
				editingAccount = false;
			} else {
				Friends.chooseAccount(n, err -> accountError = err);
				editingAccount = false;
				account.clear();
			}
			return;
		}
		if (!canAdd()) return;
		String n = add.value().strip();
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() != null) mc.getConnection().sendCommand("friend add " + n);
		sent = n;
		add.clear();
	}

	private void refresh() {
		long now = System.currentTimeMillis();
		if (now - lastManualRefresh < MANUAL_REFRESH_MS) return;
		lastManualRefresh = now;
		Friends.refresh(true);
	}

	private static void openProfile(java.util.UUID uuid) {
		Util.getPlatform().openUri(LeoneWeb.profileUrl(uuid));
	}

	private void message(String name) {
		Minecraft mc = Minecraft.getInstance();
		mc.gui.setScreen(null);
		mc.gui.openChatAndAddText(ChatComponent.ChatMethod.MESSAGE, "/msg " + name + " ");
	}

	// --------------------------------------------------------------------- draw

	@Override
	void draw(Ui ui, float x, float y) {
		ui.header(Icons.FRIENDS, "Friends", x, y);
		float rx = x + W;
		float pw = ui.buttonWidth("Profile", Icons.EXTERNAL, Ui.BUTTON);
		rx -= pw;
		ui.button("fr-profile", rx, y + 4, 30, "Profile", Icons.EXTERNAL, Ui.Btn.GHOST, true, () -> openProfile(Friends.accountUuid()));
		ui.tip(rx, y + 4, pw, 30, "Opens this profile on leonemc.net in your browser.");
		boolean loading = Friends.state() == Friends.State.LOADING;
		float fw = ui.buttonWidth(loading ? "Loading…" : "Refresh", Icons.REFRESH, Ui.BUTTON);
		rx -= fw + 6;
		ui.button("fr-refresh", rx, y + 4, 30, loading ? "Loading…" : "Refresh", Icons.REFRESH, Ui.Btn.GHOST, !loading, this::refresh);
		String count = hasList() ? Friends.onlineCount() + " online · " + Friends.list().size() + " friends" : "";
		float cw = ui.text.width(count, Ui.HINT);
		ui.text.draw(ui.cv, count, rx - 14 - cw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_HINT);

		float by = y + 38 + 18;
		ui.entering(ui.stagger(0), () -> accountBar(ui, x, by));

		float ay = by + BAR_H + 14;
		float addW = ui.buttonWidth("Add", Icons.USER_PLUS, Ui.BUTTON);
		ui.entering(ui.stagger(1), () -> {
			String status = null;
			String n = add.value().strip();
			if (!n.isEmpty() && Friends.isFriend(n)) status = "Already your friend";
			else if (!n.isEmpty() && !LeoneMC.connected()) status = "Join LeoneMC first";
			String placeholder = sent != null ? "Sent /friend add " + sent : "Send a friend request by username";
			ui.input(add, x, ay, W - addW - 8, 38, placeholder, Icons.USER_PLUS, status);
			ui.button("fr-add", x + W - addW, ay, 38, "Add", null, Ui.Btn.ACCENT, canAdd(), this::submit);
			ui.tip(x + W - addW, ay, addW, 38, "Sends /friend add to LeoneMC. Only works while you are connected.");
		});

		float ly = ay + 38 + 18;
		ui.entering(ui.stagger(2), () -> list(ui, x, ly));
	}

	private void accountBar(Ui ui, float x, float y) {
		ui.cv.fillRoundRect(x, y, W, BAR_H, 12, Colors.white(0.035f));
		ui.cv.borderRoundRect(x, y, W, BAR_H, 12, 1, Colors.white(0.07f));
		float mid = y + BAR_H / 2;
		LeoneWeb.Profile p = Friends.profile();
		String name = Friends.accountName();
		Heads.draw(ui.cv, ui.text, Friends.accountUuid(), name, x + 12, mid - 15, 30, 8);
		float tx = x + 12 + 30 + 12;
		if (editingAccount) {
			float bw = ui.buttonWidth("Show", null, Ui.BUTTON), mw = ui.buttonWidth("Mine", null, Ui.BUTTON);
			float iw = W - (tx - x) - bw - mw - 12 - 12;
			ui.input(account, tx, mid - 17, iw, 34, "LeoneMC username, or leave empty for yours", null, accountError);
			ui.button("fr-acc-show", tx + iw + 6, mid - 15, 30, "Show", null, Ui.Btn.ACCENT, true, this::submit);
			ui.button("fr-acc-mine", tx + iw + 6 + bw + 6, mid - 15, 30, "Mine", null, Ui.Btn.GHOST, true, () -> {
				editingAccount = false;
				accountError = null;
				Friends.setAccount(null);
			});
			return;
		}
		float lh = ui.text.lineHeight(Ui.BODY_STRONG), sh = ui.text.lineHeight(Ui.SMALL);
		float top = mid - (lh + sh) / 2;
		String lead = Friends.customAccount() ? "Friends of " : "Your friends, ";
		int nameColor = p != null ? 0xFF000000 | p.color() : Colors.TEXT;
		float base = top + ui.text.ascent(Ui.BODY_STRONG);
		ui.text.draw(ui.cv, lead, tx, base, Ui.BODY_STRONG, Colors.TEXT);
		float nx = tx + ui.text.width(lead, Ui.BODY_STRONG);
		ui.text.draw(ui.cv, name, nx, base, Ui.BODY_STRONG, nameColor);
		float bx = nx + ui.text.width(name, Ui.BODY_STRONG) + 8;
		if (p != null && !p.rank().isEmpty()) ui.badge(p.rank(), bx, top + lh / 2, p.rankColor(), true);
		String sub = switch (Friends.state()) {
			case LOADING -> "Loading from leonemc.net…";
			case ERROR -> Friends.error();
			case NOT_FOUND -> "No LeoneMC profile for this account";
			default -> Friends.updated() > 0 ? "From leonemc.net · updated " + ConfigsPage.ago(Friends.updated()) : "From leonemc.net";
		};
		if (accountError != null) sub = accountError;
		ui.text.draw(ui.cv, sub, tx, top + lh + ui.text.ascent(Ui.SMALL), Ui.SMALL, accountError != null || Friends.state() == Friends.State.ERROR ? 0xFFFF9A9A : Colors.TEXT_HINT);
		float cw = ui.buttonWidth("Change account", Icons.USER, Ui.BUTTON);
		ui.button("fr-acc", x + W - 10 - cw, mid - 15, 30, "Change account", Icons.USER, Ui.Btn.GHOST, true, () -> {
			editingAccount = true;
			accountError = null;
			account.clear();
			screen.focus(account);
		});
		ui.tip(x + W - 10 - cw, mid - 15, cw, 30, "Show another player's LeoneMC friends, for example an alt account.");
	}

	private void list(Ui ui, float x, float ly) {
		if (!hasList()) {
			ui.cv.dashedBorderRoundRect(x, ly, W, 96, 12, 1.5f, 4.5f, 3f, Colors.white(0.12f));
			String msg = switch (Friends.state()) {
				case LOADING, IDLE -> "Loading your friends from leonemc.net…";
				case ERROR -> "Could not reach leonemc.net. Try Refresh in a moment.";
				case NOT_FOUND -> Friends.accountName() + " has not played on LeoneMC. Use Change account to pick another player.";
				default -> "No friends yet. Add some with the box above while on LeoneMC.";
			};
			ui.centred(ui.text.fit(msg, Ui.DESC, W - 40), Ui.DESC, x, ly, W, 96, Colors.TEXT_HINT);
			return;
		}
		float lh = listHeight(ui);
		float off = Math.min(scroll.get(ui.now), maxScroll(ui));
		ui.cv.push();
		ui.cv.clipRect(x - 2, ly - 1, x + W + 2, ly + lh + 1);
		float cy = ly - off;
		for (Section s : sections()) {
			ui.caps(s.title(), x, cy);
			cy += ui.text.lineHeight(Ui.CAPS) + 10;
			List<Friend> fs = s.friends();
			for (int i = 0; i < fs.size(); i++) {
				Friend f = fs.get(i);
				float tx = x + (i % 3) * (TILE_W + GAP), ty = cy + (i / 3) * (TILE_H + GAP);
				boolean visible = ty + TILE_H >= ly && ty <= ly + lh;
				if (visible) tile(ui, f, tx, ty, ty >= ly - 4 && ty + TILE_H <= ly + lh + 4);
			}
			int rows = (fs.size() + 2) / 3;
			cy += rows * TILE_H + (rows - 1) * GAP + 16;
		}
		ui.cv.pop();
		float max = maxScroll(ui);
		if (max > 0) {
			float frac = lh / (lh + max);
			float th = Math.max(24, lh * frac), tt = ly + (lh - th) * (off / max);
			ui.cv.fillRoundRect(x + W + 6, tt, 3, th, 1.5f, Colors.white(0.18f));
		}
	}

	private void tile(Ui ui, Friend f, float x, float y, boolean interactive) {
		String k = "fr#" + f.uuid();
		boolean hov = interactive && ui.hovered(x, y, TILE_W, TILE_H);
		boolean online = Friends.online(f);
		ColorAnim bg = ui.color(k + "#bg", Colors.white(0.035f));
		bg.set(online ? Colors.accent(hov ? 0.16f : 0.08f) : Colors.white(hov ? 0.07f : 0.035f), ui.now, 150, Ease.EASE);
		Anim actions = ui.anim(k + "#act", 0);
		actions.set(hov ? 1 : 0, ui.now, 150, Ease.EASE);
		ui.cv.fillRoundRect(x, y, TILE_W, TILE_H, 12, bg.get(ui.now));
		ui.cv.borderRoundRect(x, y, TILE_W, TILE_H, 12, 1, online ? Colors.accent(hov ? 0.6f : 0.35f) : Colors.white(hov ? 0.16f : 0.07f));
		float hs = 34, hx = x + 11, hy = y + (TILE_H - hs) / 2;
		ui.cv.push();
		if (!online) ui.cv.mulAlpha(0.55f);
		Heads.draw(ui.cv, ui.text, f.uuid(), f.name(), hx, hy, hs, 8);
		ui.cv.pop();
		if (online) {
			ui.cv.fillCircle(hx + hs - 1, hy + hs - 1, 6, Colors.glass(1));
			ui.cv.fillCircle(hx + hs - 1, hy + hs - 1, 4, 0xFF4ADE80);
		}
		float tx = hx + hs + 11, maxW = TILE_W - (tx - x) - 12;
		float ao = actions.get(ui.now);
		if (ao > 0) maxW -= 30 * ao + (online ? 32 * ao : 0);
		float lh = ui.text.lineHeight(Ui.TILE), sh = ui.text.lineHeight(Ui.SMALL);
		float top = y + (TILE_H - lh - sh) / 2;
		int nameColor = 0xFF000000 | f.color();
		ui.text.draw(ui.cv, ui.text.fit(f.name(), Ui.BODY_STRONG, maxW), tx, top + ui.text.ascent(Ui.TILE), Ui.BODY_STRONG, online ? nameColor : Colors.lerp(nameColor, Colors.TEXT_HINT, 0.4f));
		Friends.Location at = Friends.location(f.name());
		String status = online ? at != null && at.online() ? "On " + at.server() : "Online" : at != null && !at.online() ? "Left " + at.server() + " " + ConfigsPage.ago(at.at()) : "Offline";
		ui.text.draw(ui.cv, ui.text.fit(status, Ui.SMALL, maxW), tx, top + lh + ui.text.ascent(Ui.SMALL), Ui.SMALL, online ? 0xFF86EFAC : Colors.TEXT_HINT);
		if (!interactive) return;
		ui.hit(x, y, TILE_W, TILE_H, () -> openProfile(f.uuid()), null);
		ui.tip(x, y, TILE_W - 70, TILE_H, "Click to open " + f.name() + "'s profile on leonemc.net");
		if (ao > 0) {
			ui.cv.push();
			ui.cv.mulAlpha(ao);
			float bx = x + TILE_W - 8 - 28;
			ui.iconButton(k + "#prof", bx, y + (TILE_H - 28) / 2, 28, Icons.EXTERNAL, 14, Colors.TEXT_MUTED, Colors.WHITE, () -> openProfile(f.uuid()));
			if (online && LeoneMC.connected()) {
				bx -= 32;
				ui.iconButton(k + "#msg", bx, y + (TILE_H - 28) / 2, 28, Icons.MESSAGE, 14, Colors.TEXT_MUTED, Colors.WHITE, () -> message(f.name()));
			}
			ui.cv.pop();
		}
	}
}
