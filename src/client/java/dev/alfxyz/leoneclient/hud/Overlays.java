package dev.alfxyz.leoneclient.hud;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.Time;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.features.ActionBars;
import dev.alfxyz.leoneclient.features.AnticheatAlerts;
import dev.alfxyz.leoneclient.features.ModModeStatus;
import dev.alfxyz.leoneclient.features.SessionStats;
import dev.alfxyz.leoneclient.features.Timers;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Gfx;
import dev.alfxyz.leoneclient.render.Heads;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import dev.alfxyz.leoneclient.ui.Colors;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** The built-in overlays. */
final class Overlays {
	private Overlays() {
	}

	static List<Overlay> create() {
		return List.of(new Watermark(), new ModuleList(), new ActionBarOverlay(), new CombatBarOverlay(), new Notifications(), new ServerOverlay(),
			new TimersPanel(), new SessionStatsOverlay(), new StaffStatusOverlay(), new AnticheatPanel(),
			new Fps(), new Ping(), new Coordinates(), new Speed(), new Cps(), new Keystrokes());
	}

	// ------------------------------------------------------------ watermark

	static final class Watermark extends Overlay {
		private static final Style NAME = Style.of(14, Weight.SEMIBOLD);
		private static final Style SUB = Style.of(14, Weight.REGULAR);

		Watermark() {
			super("watermark", "Watermark", "Leone logo and name", true, START, START, 8, 8);
		}

		@Override
		public float width(Context c) {
			return 8 + 18 + 8 + c.text.width("Leone", NAME) + 4 + c.text.width("Client", SUB) + 12;
		}

		@Override
		public float height(Context c) {
			return 30;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pill(x, y, w, h);
			Gfx.logo().draw(c.cv, x + 8, y + (h - 18) / 2, 18, Colors.WHITE);
			float tx = x + 8 + 18 + 8, base = c.text.baselineFor(NAME, y + h / 2);
			c.text.draw(c.cv, "Leone", tx, base, NAME, Colors.TEXT);
			c.text.draw(c.cv, "Client", tx + c.text.width("Leone", NAME) + 4, base, SUB, Colors.TEXT_HINT);
		}
	}

	// ---------------------------------------------------------- module list

	static final class ModuleList extends Overlay {
		private static final float ROW = 20;
		private final Map<Module, Anim> shown = new HashMap<>();

		ModuleList() {
			super("module_list", "Module List", "Modules that are on, longest first", false, END, START, 8, 8);
		}

		private List<Module> rows(Context c) {
			List<Module> list = new ArrayList<>();
			for (Module m : Modules.all()) {
				boolean on = m.toggleable() && m.enabled() && m.visible;
				Anim a = shown.computeIfAbsent(m, k -> new Anim(on ? 1 : 0));
				a.set(on ? 1 : 0, c.now, 220, Ease.SNAP);
				if (a.get(c.now) > 0.001f) list.add(m);
			}
			list.sort((a, b) -> {
				int d = Float.compare(c.text.width(b.name, VALUE), c.text.width(a.name, VALUE));
				return d != 0 ? d : a.name.compareTo(b.name);
			});
			return list;
		}

		@Override
		public float width(Context c) {
			float w = 0;
			for (Module m : rows(c)) w = Math.max(w, c.text.width(m.name, VALUE) + 20);
			return w == 0 && c.editor ? 110 : w;
		}

		@Override
		public float height(Context c) {
			float h = 0;
			for (Module m : rows(c)) h += ROW * shown.get(m).get(c.now);
			return h == 0 && c.editor ? ROW : h;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			List<Module> rows = rows(c);
			if (rows.isEmpty()) {
				if (c.editor) {
					c.cv.fillRoundRect(x, y, w, h, 4, Colors.glass(0.5f));
					c.text.draw(c.cv, "Module List", x + 10, c.text.baselineFor(VALUE, y + h / 2), VALUE, Colors.TEXT_HINT);
				}
				return;
			}
			float cy = y;
			for (Module m : rows) {
				float t = shown.get(m).get(c.now);
				float rw = c.text.width(m.name, VALUE) + 20;
				float slide = (1 - t) * (rw + 12);
				float rx = c.alignEnd ? x + w - rw + slide : x - slide;
				c.cv.push();
				c.cv.mulAlpha(t);
				c.cv.fillRect(rx, cy, rx + rw, cy + ROW, Colors.glass(0.6f));
				float bar = c.alignEnd ? rx + rw - 2 : rx;
				c.cv.fillRect(bar, cy, bar + 2, cy + ROW, Colors.ACCENT);
				c.text.draw(c.cv, m.name, rx + (c.alignEnd ? 8 : 10), c.text.baselineFor(VALUE, cy + ROW / 2), VALUE, Colors.TEXT);
				c.cv.pop();
				cy += ROW * t;
			}
		}
	}

	// ------------------------------------------------------------ simple pills

	static final class Fps extends Overlay {
		Fps() {
			super("fps", "FPS", "Frames per second", false, START, START, 8, 46);
		}

		private String value(Context c) {
			return Integer.toString(c.mc.getFps());
		}

		@Override
		public float width(Context c) {
			return c.pairWidth(value(c), "FPS");
		}

		@Override
		public float height(Context c) {
			return PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pair(x, y, w, h, value(c), "FPS");
		}
	}

	static final class Ping extends Overlay {
		Ping() {
			super("ping", "Ping", "Latency to the server", false, START, START, 8, 78);
		}

		private String value(Context c) {
			if (c.mc.player == null || c.mc.getConnection() == null) return "0";
			PlayerInfo info = c.mc.getConnection().getPlayerInfo(c.mc.player.getUUID());
			return Integer.toString(info == null ? 0 : info.getLatency());
		}

		@Override
		public float width(Context c) {
			return c.pairWidth(value(c), "ms");
		}

		@Override
		public float height(Context c) {
			return PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pair(x, y, w, h, value(c), "ms");
		}
	}

	static final class Coordinates extends Overlay {
		Coordinates() {
			super("coordinates", "Coordinates", "Position and facing", false, START, START, 8, 110);
		}

		private String[] parts(Context c) {
			if (c.mc.player == null) return new String[] {"0", "0", "0", "N"};
			Direction d = c.mc.player.getDirection();
			String face = switch (d) {
				case NORTH -> "N";
				case SOUTH -> "S";
				case EAST -> "E";
				case WEST -> "W";
				default -> d.getName().toUpperCase(Locale.ROOT);
			};
			return new String[] {Integer.toString(c.mc.player.getBlockX()), Integer.toString(c.mc.player.getBlockY()), Integer.toString(c.mc.player.getBlockZ()), face};
		}

		@Override
		public float width(Context c) {
			String[] p = parts(c);
			float w = 10;
			String[] labels = {"X", "Y", "Z"};
			for (int i = 0; i < 3; i++) w += c.text.width(labels[i], VALUE) + 5 + c.text.width(p[i], VALUE_STRONG) + 12;
			return w + c.text.width(p[3], VALUE_STRONG) + 10;
		}

		@Override
		public float height(Context c) {
			return PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pill(x, y, w, h);
			String[] p = parts(c);
			String[] labels = {"X", "Y", "Z"};
			float base = c.text.baselineFor(VALUE, y + h / 2), cx = x + 10;
			for (int i = 0; i < 3; i++) {
				c.text.draw(c.cv, labels[i], cx, base, VALUE, Colors.TEXT_HINT);
				cx += c.text.width(labels[i], VALUE) + 5;
				c.text.draw(c.cv, p[i], cx, base, VALUE_STRONG, Colors.TEXT);
				cx += c.text.width(p[i], VALUE_STRONG) + 12;
			}
			c.text.draw(c.cv, p[3], cx, base, VALUE_STRONG, Colors.accent(1));
		}
	}

	static final class Speed extends Overlay {
		private double speed;

		Speed() {
			super("speed", "Speed", "Horizontal speed in blocks per second", false, START, START, 8, 142);
		}

		@Override
		public void tick(Minecraft mc) {
			if (mc.player == null) return;
			double dx = mc.player.getX() - mc.player.xo, dz = mc.player.getZ() - mc.player.zo;
			speed += (Math.sqrt(dx * dx + dz * dz) * 20 - speed) * 0.5;
		}

		private String value() {
			return String.format(Locale.ROOT, "%.2f", speed);
		}

		@Override
		public float width(Context c) {
			return c.pairWidth(value(), "m/s");
		}

		@Override
		public float height(Context c) {
			return PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pair(x, y, w, h, value(), "m/s");
		}
	}

	/** Tracks clicks per second for the attack and use keys. */
	static final class ClickCounter {
		private final Deque<Long> left = new ArrayDeque<>(), right = new ArrayDeque<>();
		private boolean wasLeft, wasRight;

		void poll(Minecraft mc) {
			long now = System.currentTimeMillis();
			boolean l = mc.options.keyAttack.isDown(), r = mc.options.keyUse.isDown();
			if (l && !wasLeft) left.add(now);
			if (r && !wasRight) right.add(now);
			wasLeft = l;
			wasRight = r;
			while (!left.isEmpty() && now - left.peekFirst() > 1000) left.pollFirst();
			while (!right.isEmpty() && now - right.peekFirst() > 1000) right.pollFirst();
		}

		int left() {
			return left.size();
		}

		int right() {
			return right.size();
		}
	}

	static final ClickCounter CLICKS = new ClickCounter();

	static final class Cps extends Overlay {
		Cps() {
			super("cps", "CPS", "Left and right clicks per second", false, START, START, 8, 174);
		}

		private String value() {
			return CLICKS.left() + " | " + CLICKS.right();
		}

		@Override
		public float width(Context c) {
			return c.pairWidth(value(), "CPS");
		}

		@Override
		public float height(Context c) {
			return PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pair(x, y, w, h, value(), "CPS");
		}
	}

	// ---------------------------------------------------------- keystrokes

	static final class Keystrokes extends Overlay {
		private static final float KEY = 30, GAP = 4, SPACE_H = 16;
		private static final Style KEY_STYLE = Style.of(13, Weight.SEMIBOLD);
		private static final Style SMALL = Style.of(10, Weight.REGULAR);
		private final Map<String, Anim> press = new HashMap<>();

		Keystrokes() {
			super("keystrokes", "Keystrokes", "Movement keys and mouse buttons", false, END, END, 8, 8);
		}

		@Override
		public float width(Context c) {
			return KEY * 3 + GAP * 2;
		}

		@Override
		public float height(Context c) {
			return KEY * 3 + SPACE_H + GAP * 3;
		}

		private void key(Context c, String id, String label, String sub, float x, float y, float w, float h, KeyMapping km) {
			Anim a = press.computeIfAbsent(id, k -> new Anim(0));
			a.set(km.isDown() ? 1 : 0, c.now, 80, Ease.EASE);
			float t = a.get(c.now);
			c.cv.fillRoundRect(x, y, w, h, 7, Colors.lerp(Colors.glass(0.62f), Colors.accent(0.85f), t));
			c.cv.borderRoundRect(x, y, w, h, 7, 1, Colors.lerp(Colors.white(0.08f), Colors.accent(1), t));
			int col = Colors.lerp(Colors.TEXT_DOCK, Colors.WHITE, t);
			if (sub.isEmpty()) {
				c.text.draw(c.cv, label, x + (w - c.text.width(label, KEY_STYLE)) / 2, c.text.baselineFor(KEY_STYLE, y + h / 2), KEY_STYLE, col);
			} else {
				c.text.draw(c.cv, label, x + (w - c.text.width(label, KEY_STYLE)) / 2, c.text.baselineFor(KEY_STYLE, y + h / 2 - 5), KEY_STYLE, col);
				c.text.draw(c.cv, sub, x + (w - c.text.width(sub, SMALL)) / 2, c.text.baselineFor(SMALL, y + h / 2 + 8), SMALL, Colors.alpha(col, 0.75f));
			}
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			var o = c.mc.options;
			key(c, "w", "W", "", x + KEY + GAP, y, KEY, KEY, o.keyUp);
			float r2 = y + KEY + GAP;
			key(c, "a", "A", "", x, r2, KEY, KEY, o.keyLeft);
			key(c, "s", "S", "", x + KEY + GAP, r2, KEY, KEY, o.keyDown);
			key(c, "d", "D", "", x + 2 * (KEY + GAP), r2, KEY, KEY, o.keyRight);
			float r3 = r2 + KEY + GAP, half = (w - GAP) / 2;
			key(c, "lmb", "LMB", CLICKS.left() + " CPS", x, r3, half, KEY, o.keyAttack);
			key(c, "rmb", "RMB", CLICKS.right() + " CPS", x + half + GAP, r3, half, KEY, o.keyUse);
			float r4 = r3 + KEY + GAP;
			Anim a = press.computeIfAbsent("space", k -> new Anim(0));
			a.set(o.keyJump.isDown() ? 1 : 0, c.now, 80, Ease.EASE);
			float t = a.get(c.now);
			c.cv.fillRoundRect(x, r4, w, SPACE_H, 6, Colors.lerp(Colors.glass(0.62f), Colors.accent(0.85f), t));
			c.cv.borderRoundRect(x, r4, w, SPACE_H, 6, 1, Colors.lerp(Colors.white(0.08f), Colors.accent(1), t));
			c.cv.fillRoundRect(x + w / 2 - 14, r4 + SPACE_H / 2 - 1, 28, 2, 1, Colors.lerp(Colors.TEXT_DOCK, Colors.WHITE, t));
		}
	}

	// ------------------------------------------------------- action bars

	/** Shared drawing for the two action bar overlays: vanilla text at native size, optionally on a backdrop. */
	abstract static class BarOverlay extends Overlay {
		private static final float PAD_X = 6, PAD_Y = 2, LINE = 9;

		BarOverlay(String id, String name, String description, float oy) {
			super(id, name, description, true, CENTER, END, 0, oy);
		}

		@Override
		public boolean guiPixels() {
			return true;
		}

		abstract @Nullable Component line();

		abstract Component sample();

		abstract long at();

		abstract int tint();

		abstract boolean background();

		abstract boolean shadow();

		abstract boolean fades();

		private @Nullable Component content(Context c) {
			Component line = line();
			return line == null && c.editor ? sample() : line;
		}

		@Override
		public boolean shown() {
			return line() != null;
		}

		@Override
		public float width(Context c) {
			Component t = content(c);
			return t == null ? 0 : c.componentWidth(t) + 2 * PAD_X * c.guiPx();
		}

		@Override
		public float height(Context c) {
			return content(c) == null ? 0 : (LINE + 2 * PAD_Y) * c.guiPx();
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			Component t = content(c);
			if (t == null) return;
			float alpha = c.editor || !fades() ? 1 : ActionBars.fade(at());
			if (alpha <= 0) return;
			c.cv.push();
			c.cv.mulAlpha(alpha);
			if (background()) c.cv.fillRoundRect(x, y, w, h, 5 * c.guiPx(), Colors.glass(0.62f));
			c.component(t, x + PAD_X * c.guiPx(), y + (PAD_Y + 1) * c.guiPx(), tint(), shadow());
			c.cv.pop();
		}
	}

	static final class ActionBarOverlay extends BarOverlay {
		ActionBarOverlay() {
			super("action_bar", "Action Bar", "The server's action bar", 39);
		}

		@Override
		public boolean enabled() {
			return Modules.ACTION_BAR.enabled();
		}

		@Override
		public void setEnabled(boolean on) {
			Modules.ACTION_BAR.setEnabled(on);
		}

		@Override
		@Nullable Component line() {
			return Modules.ACTION_BAR.line();
		}

		@Override
		Component sample() {
			return Modules.COMBAT_BAR.merged() ? Component.literal("2m 30s  §7|  §cCombat Tag | 14.5") : Component.literal("2m 30s");
		}

		@Override
		long at() {
			return Modules.ACTION_BAR.lineAt();
		}

		@Override
		int tint() {
			return Modules.COMBAT_BAR.merged() ? 0xFFFFFFFF : 0xFFFFAA00;
		}

		@Override
		boolean background() {
			return Modules.ACTION_BAR.background.get();
		}

		@Override
		boolean shadow() {
			return Modules.ACTION_BAR.shadow.get();
		}

		@Override
		boolean fades() {
			return Modules.ACTION_BAR.fade.get();
		}
	}

	static final class CombatBarOverlay extends BarOverlay {
		CombatBarOverlay() {
			super("combat_bar", "Combat Bar", "Your combat tag line", 55);
		}

		@Override
		public boolean enabled() {
			return Modules.COMBAT_BAR.enabled() && !Modules.COMBAT_BAR.merged();
		}

		@Override
		public void setEnabled(boolean on) {
			Modules.COMBAT_BAR.setEnabled(on);
		}

		@Override
		@Nullable Component line() {
			return Modules.COMBAT_BAR.line();
		}

		@Override
		Component sample() {
			return Component.literal("Combat Tag | 14.5");
		}

		@Override
		long at() {
			return ActionBars.combatAt();
		}

		@Override
		int tint() {
			return 0xFFFF5555;
		}

		@Override
		boolean background() {
			return Modules.COMBAT_BAR.background.get();
		}

		@Override
		boolean shadow() {
			return Modules.COMBAT_BAR.shadow.get();
		}

		@Override
		boolean fades() {
			return true;
		}
	}

	// ------------------------------------------------------------ server

	static final class ServerOverlay extends Overlay {
		ServerOverlay() {
			super("server", "Server", "The LeoneMC server you are on", false, START, START, 8, 206);
		}

		private @Nullable String value(Context c) {
			String s = LeoneMC.server();
			return s == null && c.editor ? "ElytraBox" : s;
		}

		@Override
		public boolean shown() {
			return LeoneMC.server() != null;
		}

		@Override
		public float width(Context c) {
			String v = value(c);
			return v == null ? 0 : 10 + 14 + 7 + c.text.width(v, VALUE_STRONG) + 10;
		}

		@Override
		public float height(Context c) {
			return value(c) == null ? 0 : PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			String v = value(c);
			if (v == null) return;
			c.pill(x, y, w, h);
			Gfx.icons().draw(c.cv, Icons.SERVER, x + 10, y + (h - 14) / 2, 14, 1.8f, Colors.accent(1));
			c.text.draw(c.cv, v, x + 10 + 14 + 7, c.text.baselineFor(VALUE, y + h / 2), VALUE_STRONG, Colors.TEXT);
		}
	}

	// ------------------------------------------------------- notifications

	static final class Notifications extends Overlay {
		private static final float W = 250, ROW = 46, GAP = 6;
		private static final Style TITLE = Style.of(13, Weight.SEMIBOLD);
		private static final Style DETAIL = Style.of(11.5f, Weight.REGULAR);

		Notifications() {
			super("notifications", "Notifications", "Pop-ups from Leone Client", true, END, CENTER, 8, 0);
		}

		private List<Notices.Notice> items(Context c) {
			List<Notices.Notice> list = Notices.active();
			if (list.isEmpty() && c.editor) {
				list = List.of(new Notices.Notice("Notifications", "Pop-ups appear here", Colors.ACCENT_RGB, Icons.STAR, null, c.now - 1000));
			}
			return list;
		}

		@Override
		public boolean shown() {
			return !Notices.active().isEmpty();
		}

		@Override
		public float width(Context c) {
			return items(c).isEmpty() ? 0 : W;
		}

		@Override
		public float height(Context c) {
			int n = items(c).size();
			return n == 0 ? 0 : n * ROW + (n - 1) * GAP;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			float cy = y;
			for (Notices.Notice n : items(c)) {
				double age = c.editor ? 1000 : c.now - n.at();
				float in = Ease.progress(age, 0, 0, 320, Ease.SNAP);
				float out = 1 - Ease.progress(age, 0, Notices.SHOW_MS - 400, 400, Ease.EASE);
				float slide = (1 - in) * 40 * (c.alignEnd ? 1 : -1);
				c.cv.push();
				c.cv.translate(slide, 0);
				c.cv.mulAlpha(Math.min(in, out));
				c.cv.boxShadow(x, cy, w, ROW, 12, 0, 6, 10, Colors.black(0.25f));
				c.cv.fillRoundRect(x, cy, w, ROW, 12, Colors.glass(0.82f));
				c.cv.borderRoundRect(x, cy, w, ROW, 12, 1, Colors.white(0.09f));
				c.cv.fillRoundRect(x + 1, cy + 10, 3, ROW - 20, 1.5f, Colors.rgba(n.color(), 1));
				float ix = x + 12, iy = cy + (ROW - 28) / 2;
				if (n.head() != null) {
					Heads.draw(c.cv, c.text, n.head(), n.title(), ix, iy, 28, 7);
				} else {
					c.cv.fillRoundRect(ix, iy, 28, 28, 8, Colors.rgba(n.color(), 0.22f));
					if (n.icon() != null) Gfx.icons().draw(c.cv, n.icon(), ix + 6, iy + 6, 16, 1.8f, Colors.rgba(n.color(), 1));
				}
				float tx = ix + 28 + 10, maxW = w - (tx - x) - 12;
				float lh = c.text.lineHeight(TITLE), sh = c.text.lineHeight(DETAIL);
				float top = cy + (ROW - lh - sh) / 2;
				c.text.draw(c.cv, c.text.fit(n.title(), TITLE, maxW), tx, top + c.text.ascent(TITLE), TITLE, Colors.TEXT);
				c.text.draw(c.cv, c.text.fit(n.detail(), DETAIL, maxW), tx, top + lh + c.text.ascent(DETAIL), DETAIL, Colors.TEXT_HINT);
				c.cv.pop();
				cy += ROW + GAP;
			}
		}
	}

	// ------------------------------------------------------------- panels

	/** A glass panel with a small caps title, used by the list overlays. */
	abstract static class Panel extends Overlay {
		protected static final Style CAPS = new Style(10.5f, Weight.SEMIBOLD, 0.06f);
		protected static final Style ROW_TITLE = Style.of(12.5f, Weight.SEMIBOLD);
		protected static final Style ROW_TEXT = Style.of(11, Weight.REGULAR);
		protected static final float W = 250, HEAD = 26, PAD = 10;

		Panel(String id, String name, String description, int ax, int ay, float ox, float oy) {
			super(id, name, description, false, ax, ay, ox, oy);
		}

		abstract int rows(Context c);

		abstract float rowHeight();

		abstract String title();

		abstract void row(Context c, int i, float x, float y, float w);

		@Override
		public float width(Context c) {
			return rows(c) == 0 ? 0 : W;
		}

		@Override
		public float height(Context c) {
			int n = rows(c);
			return n == 0 ? 0 : HEAD + n * rowHeight() + PAD - 4;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			int n = rows(c);
			if (n == 0) return;
			c.cv.fillRoundRect(x, y, w, h, 12, Colors.glass(0.7f));
			c.cv.borderRoundRect(x, y, w, h, 12, 1, Colors.white(0.08f));
			c.text.draw(c.cv, title(), x + PAD + 2, c.text.baselineFor(CAPS, y + HEAD / 2 + 1), CAPS, Colors.TEXT_HINT);
			for (int i = 0; i < n; i++) row(c, i, x + PAD, y + HEAD + i * rowHeight(), w - 2 * PAD);
		}
	}

	static final class AnticheatPanel extends Panel {
		private static final int MAX = 6;

		AnticheatPanel() {
			super("anticheat_panel", "Anticheat Panel", "Players flagged recently", START, CENTER, 8, -60);
		}

		@Override
		public boolean available() {
			return Modules.ANTICHEAT_ALERTS.category.visible();
		}

		@Override
		public boolean enabled() {
			return Modules.ANTICHEAT_ALERTS.enabled() && available();
		}

		@Override
		public void setEnabled(boolean on) {
			Modules.ANTICHEAT_ALERTS.setEnabled(on);
		}

		@Override
		public boolean shown() {
			return !Modules.ANTICHEAT_ALERTS.recent().isEmpty();
		}

		private List<AnticheatAlerts.Suspect> list(Context c) {
			List<AnticheatAlerts.Suspect> l = Modules.ANTICHEAT_ALERTS.recent();
			if (l.isEmpty() && c.editor) {
				AnticheatAlerts.Suspect s = new AnticheatAlerts.Suspect("Player");
				s.checks.put("Reach A", 6);
				s.checks.put("Simulation", 2);
				s.total = 8;
				s.lastAt = System.currentTimeMillis() - 4000;
				s.server = "ElytraBox";
				return List.of(s);
			}
			return l.subList(0, Math.min(MAX, l.size()));
		}

		@Override
		int rows(Context c) {
			return list(c).size();
		}

		@Override
		float rowHeight() {
			return 38;
		}

		@Override
		String title() {
			return "ANTICHEAT";
		}

		@Override
		void row(Context c, int i, float x, float y, float w) {
			AnticheatAlerts.Suspect s = list(c).get(i);
			String count = "×" + s.total;
			float cw = c.text.width(count, ROW_TITLE);
			c.text.draw(c.cv, c.text.fit(s.name, ROW_TITLE, w - cw - 8), x + 2, c.text.baselineFor(ROW_TITLE, y + 10), ROW_TITLE, Colors.TEXT);
			c.text.draw(c.cv, count, x + w - cw, c.text.baselineFor(ROW_TITLE, y + 10), ROW_TITLE, 0xFFF87171);
			StringBuilder checks = new StringBuilder();
			for (var e : s.topChecks()) {
				if (!checks.isEmpty()) checks.append(", ");
				checks.append(e.getKey()).append(" ×").append(e.getValue());
			}
			String age = Time.ago(s.lastAt) + (s.server != null ? " · " + s.server : "");
			float aw = c.text.width(age, ROW_TEXT);
			c.text.draw(c.cv, c.text.fit(checks.toString(), ROW_TEXT, w - aw - 10), x + 2, c.text.baselineFor(ROW_TEXT, y + 26), ROW_TEXT, Colors.TEXT_MUTED);
			c.text.draw(c.cv, age, x + w - aw, c.text.baselineFor(ROW_TEXT, y + 26), ROW_TEXT, Colors.TEXT_HINT);
		}
	}

	static final class TimersPanel extends Panel {
		TimersPanel() {
			super("timers", "Timers", "Event and restart countdowns", END, CENTER, 8, -110);
		}

		@Override
		public boolean enabled() {
			return Modules.TIMERS.enabled();
		}

		@Override
		public void setEnabled(boolean on) {
			Modules.TIMERS.setEnabled(on);
		}

		@Override
		public boolean shown() {
			return !Modules.TIMERS.list().isEmpty();
		}

		private List<Timers.Countdown> list(Context c) {
			List<Timers.Countdown> l = Modules.TIMERS.list();
			if (l.isEmpty() && c.editor) return List.of(SAMPLE);
			return l.subList(0, Math.min(4, l.size()));
		}

		private static final Timers.Countdown SAMPLE = new Timers.Countdown(Timers.Kind.EVENT, "Lava Rising", "EU · EU West · 2,500 gems", Long.MAX_VALUE / 2);

		@Override
		int rows(Context c) {
			return list(c).size();
		}

		@Override
		float rowHeight() {
			return 36;
		}

		@Override
		String title() {
			return "TIMERS";
		}

		@Override
		void row(Context c, int i, float x, float y, float w) {
			Timers.Countdown t = list(c).get(i);
			boolean restart = t.kind == Timers.Kind.RESTART;
			long left = t == SAMPLE ? 7 * 60_000 + 30_000 : t.endsAt - System.currentTimeMillis();
			int color = restart ? 0xFFF87171 : left < 60_000 ? 0xFFFBBF24 : Colors.ACCENT;
			Gfx.icons().draw(c.cv, restart ? Icons.ALERT : Icons.CLOCK, x + 2, y + 9, 16, 1.8f, color);
			String clock = left <= 0 ? "Now" : Time.clock(left);
			float cw = c.text.width(clock, VALUE_STRONG);
			c.text.draw(c.cv, clock, x + w - cw, c.text.baselineFor(VALUE_STRONG, y + 17), VALUE_STRONG, left <= 0 ? 0xFF4ADE80 : Colors.TEXT);
			float tx = x + 2 + 16 + 9, maxW = w - (tx - x) - cw - 10;
			c.text.draw(c.cv, c.text.fit(t.title, ROW_TITLE, maxW), tx, c.text.baselineFor(ROW_TITLE, y + 10), ROW_TITLE, Colors.TEXT);
			c.text.draw(c.cv, c.text.fit(t.detail, ROW_TEXT, maxW), tx, c.text.baselineFor(ROW_TEXT, y + 25), ROW_TEXT, Colors.TEXT_HINT);
		}
	}

	static final class SessionStatsOverlay extends Overlay {
		SessionStatsOverlay() {
			super("session_stats", "Session Stats", "Kills, deaths, KDR and streak", false, START, START, 8, 238);
		}

		@Override
		public boolean enabled() {
			return Modules.SESSION_STATS.enabled();
		}

		@Override
		public void setEnabled(boolean on) {
			Modules.SESSION_STATS.setEnabled(on);
		}

		private String[][] parts() {
			SessionStats s = Modules.SESSION_STATS;
			return new String[][] {{String.valueOf(s.kills()), "K"}, {String.valueOf(s.deaths()), "D"}, {s.kdr(), "KDR"}, {String.valueOf(s.streak()), "streak"}};
		}

		@Override
		public float width(Context c) {
			float w = 10;
			for (String[] p : parts()) w += c.text.width(p[0], VALUE_STRONG) + 4 + c.text.width(p[1], VALUE) + 12;
			return w - 2;
		}

		@Override
		public float height(Context c) {
			return PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pill(x, y, w, h);
			float cx = x + 10, base = c.text.baselineFor(VALUE, y + h / 2);
			for (String[] p : parts()) {
				c.text.draw(c.cv, p[0], cx, base, VALUE_STRONG, Colors.TEXT);
				cx += c.text.width(p[0], VALUE_STRONG) + 4;
				c.text.draw(c.cv, p[1], cx, base, VALUE, Colors.TEXT_HINT);
				cx += c.text.width(p[1], VALUE) + 12;
			}
		}
	}

	static final class StaffStatusOverlay extends Overlay {
		StaffStatusOverlay() {
			super("staff_status", "Staff Status", "Mod mode, vanish and staff chat", false, CENTER, START, 0, 8);
		}

		@Override
		public boolean available() {
			return Modules.MOD_MODE.category.visible();
		}

		@Override
		public boolean enabled() {
			return Modules.MOD_MODE.enabled() && available();
		}

		@Override
		public void setEnabled(boolean on) {
			Modules.MOD_MODE.setEnabled(on);
		}

		private record Part(String icon, String text, int color) {
		}

		private List<Part> parts() {
			List<Part> out = new java.util.ArrayList<>();
			ModModeStatus m = Modules.MOD_MODE;
			out.add(m.modMode() ? new Part(Icons.SHIELD_CHECK, "Mod mode", 0xFF4ADE80) : new Part(Icons.SHIELD, "Mod mode off", Colors.TEXT_HINT));
			if (m.vanished()) out.add(new Part(Icons.EYE_OFF, "Vanished", 0xFFC084FC));
			if (Modules.STAFF_CHAT.active()) {
				boolean hidden = StaffChat.isHidden();
				out.add(new Part(hidden ? Icons.VIDEO_OFF : Icons.EYE, hidden ? "Staff chat hidden" : "Staff chat visible", hidden ? 0xFF4ADE80 : 0xFFFBBF24));
			}
			if (m.punishments() > 0) out.add(new Part(Icons.FLAG, m.punishments() + (m.punishments() == 1 ? " punishment" : " punishments"), Colors.TEXT_DOCK));
			return out;
		}

		@Override
		public float width(Context c) {
			float w = 8;
			for (Part p : parts()) w += 14 + 6 + c.text.width(p.text(), VALUE) + 14;
			return w - 6;
		}

		@Override
		public float height(Context c) {
			return PILL_H;
		}

		@Override
		public void draw(Context c, float x, float y, float w, float h) {
			c.pill(x, y, w, h);
			float cx = x + 10, base = c.text.baselineFor(VALUE, y + h / 2);
			for (Part p : parts()) {
				Gfx.icons().draw(c.cv, p.icon(), cx, y + (h - 14) / 2, 14, 1.8f, p.color());
				cx += 14 + 6;
				c.text.draw(c.cv, p.text(), cx, base, VALUE, p.color());
				cx += c.text.width(p.text(), VALUE) + 14;
			}
		}
	}
}
