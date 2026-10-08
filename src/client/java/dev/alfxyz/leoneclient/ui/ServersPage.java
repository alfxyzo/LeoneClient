package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.render.Heads;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Friends;
import dev.alfxyz.leoneclient.web.LeoneWeb.Friend;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;

/** LeoneMC's servers: switch with one click, and see which friends are on each. */
final class ServersPage extends Page {
	private static final float CARD_W = (W - 16) / 3, CARD_H = 104, GAP = 8;

	private record Server(String name, String command, String blurb, String icon) {
		boolean current() {
			String on = LeoneMC.server();
			if (on == null) return false;
			if (command.equals("hub")) return on.toLowerCase(Locale.ROOT).contains("hub");
			return on.equalsIgnoreCase(name);
		}
	}

	private static final List<Server> SERVERS = List.of(
		new Server("ElytraBox", "server elytrabox", "Elytra PvP in the box", Icons.FEATHER),
		new Server("WildKits", "server wildkits", "Random kit PvP", Icons.SWORDS),
		new Server("CoreRaiding", "server coreraiding", "Raid the enemy cores", Icons.GEM),
		new Server("InsaneKits", "server insanekits", "Kit PvP, turned up", Icons.FLAME),
		new Server("Lifesteal", "server lifesteal", "Lifesteal SMP", Icons.HEART),
		new Server("Gens", "server gens", "Generators and upgrades", Icons.COINS),
		new Server("Survival", "server survival", "Classic survival", Icons.TREE),
		new Server("Hub", "hub", "Back to the lobby", Icons.HOME));

	ServersPage(LeoneScreen screen) {
		super(screen);
	}

	@Override
	float height(Ui ui) {
		int rows = (SERVERS.size() + 2) / 3;
		return 38 + 18 + rows * CARD_H + (rows - 1) * GAP;
	}

	@Override
	void draw(Ui ui, float x, float y) {
		ui.header(Icons.SERVER, "Servers", x, y);
		String on = LeoneMC.server();
		String hint = !LeoneMC.connected() ? "Join LeoneMC to switch servers" : on != null ? "You are on " + on : "Connected to LeoneMC";
		float hw = ui.text.width(hint, Ui.HINT);
		ui.text.draw(ui.cv, hint, x + W - hw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, LeoneMC.connected() ? Colors.TEXT_SECONDARY : Colors.TEXT_HINT);
		float gy = y + 38 + 18;
		for (int i = 0; i < SERVERS.size(); i++) {
			Server s = SERVERS.get(i);
			float cx = x + (i % 3) * (CARD_W + GAP), cy = gy + (i / 3) * (CARD_H + GAP);
			ui.entering(ui.stagger(i), () -> card(ui, s, cx, cy));
		}
	}

	private void card(Ui ui, Server s, float x, float y) {
		boolean connected = LeoneMC.active();
		boolean here = s.current();
		boolean hov = ui.hovered(x, y, CARD_W, CARD_H);
		ColorAnim bg = ui.color("srv#" + s.name(), Colors.white(0.035f));
		bg.set(here ? Colors.accent(0.16f) : Colors.white(hov && connected ? 0.07f : 0.035f), ui.now, 150, Ease.EASE);
		ui.cv.fillRoundRect(x, y, CARD_W, CARD_H, 14, bg.get(ui.now));
		ui.cv.borderRoundRect(x, y, CARD_W, CARD_H, 14, 1, here ? Colors.accent(0.6f) : Colors.white(hov && connected ? 0.16f : 0.07f));
		ui.cv.push();
		if (!connected) ui.cv.mulAlpha(0.6f);
		float ix = x + 14, iy = y + 14;
		ui.cv.fillRoundRect(ix, iy, 36, 36, 10, here ? Colors.accent(0.3f) : Colors.white(0.06f));
		ui.icons.draw(ui.cv, s.icon(), ix + 9, iy + 9, 18, 1.8f, here ? Colors.WHITE : Colors.TEXT_DOCK);
		float tx = ix + 36 + 12, maxW = CARD_W - (tx - x) - 12;
		float lh = ui.text.lineHeight(Ui.BODY_STRONG), sh = ui.text.lineHeight(Ui.SMALL);
		float top = iy + (36 - lh - sh) / 2;
		ui.text.draw(ui.cv, s.name(), tx, top + ui.text.ascent(Ui.BODY_STRONG), Ui.BODY_STRONG, Colors.TEXT);
		ui.text.draw(ui.cv, ui.text.fit(s.blurb(), Ui.SMALL, maxW), tx, top + lh + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_HINT);

		float fy = y + CARD_H - 14 - 24;
		List<Friend> friends = s.command().equals("hub") ? List.of() : Friends.on(s.name());
		float hx = x + 14;
		int shown = Math.min(5, friends.size());
		for (int i = 0; i < shown; i++) {
			Friend f = friends.get(i);
			ui.cv.fillRoundRect(hx - 1.5f, fy - 1.5f, 27, 27, 7, Colors.glass(1));
			Heads.draw(ui.cv, ui.text, f.uuid(), f.name(), hx, fy, 24, 6);
			hx += 20;
		}
		if (friends.size() > shown) {
			String more = "+" + (friends.size() - shown);
			ui.text.draw(ui.cv, more, hx + 8, ui.text.baselineFor(Ui.SMALL, fy + 12), Ui.SMALL, Colors.TEXT_MUTED);
		} else if (friends.isEmpty() && !here) {
			ui.text.draw(ui.cv, "No friends seen here", x + 14, ui.text.baselineFor(Ui.SMALL, fy + 12), Ui.SMALL, Colors.TEXT_DIM);
		}
		if (!friends.isEmpty()) {
			StringBuilder names = new StringBuilder();
			for (Friend f : friends) names.append(names.isEmpty() ? "" : ", ").append(f.name());
			ui.tip(x + 10, fy - 2, Math.max(60, hx - x), 28, "Friends here: " + names);
		}
		ui.cv.pop();

		float right = x + CARD_W - 12;
		if (here) {
			float bw = ui.badgeWidth("You are here");
			ui.badge("You are here", right - bw, fy + 12, Colors.ACCENT_RGB, true);
		} else if (connected) {
			float jw = ui.buttonWidth("Join", null, Ui.BUTTON);
			ui.button("srv-join#" + s.name(), right - jw, fy - 3, 30, "Join", null, hov ? Ui.Btn.ACCENT : Ui.Btn.GHOST, true, () -> join(s));
			ui.hit(x, y, CARD_W, CARD_H - 44, () -> join(s), null);
		}
	}

	private void join(Server s) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null || !LeoneMC.active()) return;
		mc.getConnection().sendCommand(s.command());
		screen.onClose();
	}
}
