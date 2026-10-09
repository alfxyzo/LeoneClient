package dev.alfxyz.leoneclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * A module's settings: the core card (Enabled / Bind / Visible) and one card
 * per setting group, laid out in two balanced columns.
 */
final class ModuleSettingsView {
	private static final float COL_W = (Page.W - 12) / 2, CARD_GAP = 12, CORE_H = 148, EMPTY_H = 96;
	private static final float IW = COL_W - 2 - 28;
	private final LeoneScreen screen;
	private final Map<Setting.Text, TextInput> inputs = new HashMap<>();

	private record Card(@Nullable String group, List<Setting> settings, boolean core, float h) {
	}

	private record Placed(Card card, float x, float y) {
	}

	ModuleSettingsView(LeoneScreen screen) {
		this.screen = screen;
	}

	// ----------------------------------------------------------------- layout

	private static float descWidth() {
		return Page.W - 50;
	}

	private static float headerHeight(Ui ui, Module m) {
		return ui.text.lineHeight(Ui.HINT) + 4 + ui.text.lineHeight(Ui.H2) + 6 + ui.paragraphHeight(m.description, Ui.DESC, descWidth(), 3);
	}

	private static float labelHeight() {
		return 22;
	}

	private static List<List<String>> chipRows(Ui ui, List<String> options, float width) {
		List<List<String>> rows = new ArrayList<>();
		List<String> row = new ArrayList<>();
		float x = 0;
		for (String o : options) {
			float w = ui.chipWidth(o);
			if (!row.isEmpty() && x + w > width) {
				rows.add(row);
				row = new ArrayList<>();
				x = 0;
			}
			row.add(o);
			x += w + 6;
		}
		if (!row.isEmpty()) rows.add(row);
		return rows;
	}

	private static List<String> options(Setting s) {
		if (s instanceof Setting.Chips c) return c.options;
		if (s instanceof Setting.Choice c) return c.options;
		return List.of();
	}

	private static boolean chipsInline(Ui ui, Setting s) {
		float total = 0;
		for (String o : options(s)) total += ui.chipWidth(o) + 6;
		return ui.text.width(s.name, Ui.BODY) + 16 + total - 6 <= IW;
	}

	private static float rowHeight(Ui ui, Setting s) {
		return switch (s) {
			case Setting.Toggle t -> 28;
			case Setting.Slider sl -> ui.text.lineHeight(Ui.VALUE) + 6 + 12 + 16;
			case Setting.Text t -> labelHeight() + 6 + 34;
			default -> {
				if (chipsInline(ui, s)) yield 28;
				int rows = chipRows(ui, options(s), IW).size();
				yield labelHeight() + 6 + rows * 28 + (rows - 1) * 6;
			}
		};
	}

	private static float groupHeight(Ui ui, List<Setting> list) {
		float h = 2 + 14 + ui.text.lineHeight(Ui.CAPS);
		for (Setting s : list) h += 12 + rowHeight(ui, s);
		return h + 14;
	}

	private static List<Card> cards(Ui ui, Module m) {
		List<Card> out = new ArrayList<>();
		if (m.toggleable()) out.add(new Card(null, List.of(), true, CORE_H));
		Map<String, List<Setting>> groups = new LinkedHashMap<>();
		for (Setting s : m.settings) if (s.shown()) groups.computeIfAbsent(s.group, k -> new ArrayList<>()).add(s);
		for (Map.Entry<String, List<Setting>> e : groups.entrySet()) out.add(new Card(e.getKey(), e.getValue(), false, groupHeight(ui, e.getValue())));
		if (groups.isEmpty()) out.add(new Card(null, List.of(), false, EMPTY_H));
		return out;
	}

	private static List<Placed> place(List<Card> cards, float x0, float y0) {
		float[] col = {0, 0};
		List<Placed> out = new ArrayList<>();
		for (Card c : cards) {
			int i = col[1] < col[0] ? 1 : 0;
			out.add(new Placed(c, x0 + i * (COL_W + 12), y0 + col[i]));
			col[i] += c.h() + CARD_GAP;
		}
		return out;
	}

	float height(Ui ui, Module m) {
		float bottom = 0;
		for (Placed p : place(cards(ui, m), 0, 0)) bottom = Math.max(bottom, p.y() + p.card().h());
		return headerHeight(ui, m) + 18 + bottom;
	}

	// ------------------------------------------------------------------- draw

	void draw(Ui ui, float x0, float y0, Module m, double at) {
		float hp = Ease.progress(ui.now, at, 0, 320, Ease.SNAP);
		ui.entering(hp, () -> header(ui, x0, y0, m));
		List<Placed> placed = place(cards(ui, m), x0, y0 + headerHeight(ui, m) + 18);
		for (int i = 0; i < placed.size(); i++) {
			Placed p = placed.get(i);
			float prog = Ease.progress(ui.now, at, 60 + 40 * i, 360, Ease.SNAP);
			if (p.card().core()) ui.entering(prog, () -> coreCard(ui, p.x(), p.y(), m));
			else if (p.card().group() == null) ui.entering(prog, () -> emptyCard(ui, p.x(), p.y(), m));
			else ui.entering(prog, () -> groupCard(ui, p.x(), p.y(), p.card(), m));
		}
	}

	private void header(Ui ui, float x0, float y0, Module m) {
		boolean backHov = ui.hovered(x0, y0, 38, 38);
		ui.cv.fillRoundRect(x0, y0, 38, 38, 11, Colors.white(backHov ? 0.09f : 0.05f));
		ui.cv.borderRoundRect(x0, y0, 38, 38, 11, 1, Colors.white(0.1f));
		ui.icons.draw(ui.cv, Icons.BACK, x0 + 11, y0 + 11, 16, 2, Colors.TEXT);
		ui.hit(x0, y0, 38, 38, screen::closeSettings, null);
		float tx = x0 + 50, ty = y0;
		ui.text.draw(ui.cv, m.category.displayName, tx, ty + ui.text.ascent(Ui.HINT), Ui.HINT, Colors.TEXT_HINT);
		ty += ui.text.lineHeight(Ui.HINT) + 4;
		ui.text.draw(ui.cv, m.name, tx, ty + ui.text.ascent(Ui.H2), Ui.H2, Colors.TEXT);
		float bx = tx + ui.text.width(m.name, Ui.H2) + 12, by = ty + ui.text.lineHeight(Ui.H2) / 2 + 1;
		if (m.leoneOnly()) {
			boolean waiting = !LeoneMC.active();
			bx += ui.badge(waiting ? "LeoneMC only, waiting" : "LeoneMC", bx, by, waiting ? 0xF59E0B : Colors.ACCENT_RGB, !waiting) + 6;
			ui.tip(bx - ui.badgeWidth(waiting ? "LeoneMC only, waiting" : "LeoneMC") - 6, by - 9, ui.badgeWidth("LeoneMC only, waiting"), 18,
				"Only does something on LeoneMC, or anywhere with Client > All Servers on.");
		}
		String blocked = m.unavailable();
		String status = m.status();
		if (blocked != null) {
			float bw = ui.badgeWidth("Unavailable here");
			ui.badge("Unavailable here", bx, by, 0xF59E0B, true);
			ui.tip(bx, by - 9, bw, 18, blocked);
		} else if (status != null && (m.enabled() || !m.toggleable())) {
			ui.badge(status, bx, by, 0xFFFFFF, false);
		}
		ty += ui.text.lineHeight(Ui.H2) + 6;
		ui.paragraph(m.description, Ui.DESC, tx, ty, descWidth(), 3, Colors.TEXT_MUTED);
	}

	private void emptyCard(Ui ui, float x, float y, Module m) {
		ui.cv.dashedBorderRoundRect(x, y, COL_W, EMPTY_H, 12, 1.5f, 4.5f, 3f, Colors.white(0.14f));
		String msg = m.toggleable() ? "Nothing else to set up" : "No settings";
		ui.centred(msg, Ui.DESC, x, y, COL_W, EMPTY_H, Colors.TEXT_HINT);
	}

	private void coreCard(Ui ui, float x, float y, Module m) {
		float w = COL_W;
		ui.card(x, y, w, CORE_H);
		float right = x + w - 1 - 12;
		float r0 = y + 1;
		ui.text.draw(ui.cv, "Enabled", x + 15, ui.text.baselineFor(Ui.BODY, r0 + 24), Ui.BODY, Colors.TEXT);
		Anim en = ui.anim(m.key() + "#enabled", m.enabled() ? 1 : 0);
		en.set(m.enabled() ? 1 : 0, ui.now, 150, Ease.EASE);
		ui.switchToggle(right - 44 + 5, r0 + 24 - 10, true, en.get(ui.now));
		ui.hit(x, r0 + 4, w, 40, () -> screen.toggle(m), null);
		String blockedWhy = m.unavailable();
		ui.tip(x, r0 + 4, w - 60, 40, blockedWhy != null ? blockedWhy : "Turns " + m.name + " on or off.");
		ui.cv.fillRect(x + 1, r0 + 48, x + w - 1, r0 + 49, Colors.white(0.07f));

		float r1 = r0 + 49;
		ui.text.draw(ui.cv, "Keybind", x + 15, ui.text.baselineFor(Ui.BODY, r1 + 24), Ui.BODY, Colors.TEXT);
		ui.tip(x, r1 + 4, 80, 40, "A key that switches " + m.name + " on or off while you play. Click the button, then press the key. Escape cancels.");
		float segY = r1 + 10;
		float bxRight = right;
		if (m.bind >= 0) {
			ui.iconButton(m.key() + "#unbind", right - 28, segY, 28, Icons.CLOSE, 13, Colors.TEXT_MUTED, 0xFFFF8A8A, () -> {
				m.bind = -1;
				screen.setBinding(null);
			});
			ui.tip(right - 28, segY, 28, 28, "Remove the keybind");
			bxRight = right - 28 - 6;
		}
		boolean capturing = screen.binding() == m;
		String bindLabel = capturing ? "Press a key…" : m.bind >= 0 ? keyName(m.bind) : "Set a key";
		float bW = Math.max(84, 1 + 12 + ui.text.width(bindLabel, Ui.BUTTON) + 12 + 1);
		float bX = bxRight - bW;
		boolean bHov = ui.hovered(bX, segY, bW, 28);
		ui.cv.fillRoundRect(bX, segY, bW, 28, 7, capturing ? Colors.accent(0.2f) : Colors.white(bHov ? 0.09f : 0.06f));
		ui.cv.borderRoundRect(bX, segY, bW, 28, 7, 1, capturing ? Colors.accent(0.6f) : Colors.white(0.1f));
		ui.centred(bindLabel, Ui.BUTTON, bX, segY, bW, 28, capturing || m.bind >= 0 ? Colors.WHITE : Colors.TEXT_MUTED);
		ui.hit(bX, segY, bW, 28, () -> screen.setBinding(capturing ? null : m), () -> {
			m.bind = -1;
			screen.setBinding(null);
		});
		ui.cv.fillRect(x + 1, r1 + 48, x + w - 1, r1 + 49, Colors.white(0.07f));

		float r2 = r1 + 49;
		ui.text.draw(ui.cv, "Show in Module List", x + 15, ui.text.baselineFor(Ui.BODY, r2 + 24), Ui.BODY, Colors.TEXT);
		Anim vis = ui.anim(m.key() + "#visible", m.visible ? 1 : 0);
		vis.set(m.visible ? 1 : 0, ui.now, 150, Ease.EASE);
		ui.switchToggle(right - 44 + 5, r2 + 24 - 10, true, vis.get(ui.now));
		ui.hit(x, r2 + 4, w, 40, () -> m.visible = !m.visible, null);
		ui.tip(x, r2 + 4, w - 60, 40, "Lists " + m.name + " in the Module List overlay while it is on.");
	}

	static String keyName(int key) {
		return InputConstants.Type.KEYSYM.getOrCreate(key).getDisplayName().getString();
	}

	private TextInput input(Setting.Text t) {
		return inputs.computeIfAbsent(t, k -> {
			TextInput in = new TextInput(k.maxLength, k.allowed);
			in.set(k.value);
			return in;
		});
	}

	private void groupCard(Ui ui, float x, float y, Card card, Module m) {
		float w = COL_W;
		ui.card(x, y, w, card.h());
		float ix = x + 1 + 14;
		float cy = y + 1 + 14;
		ui.caps(card.group(), ix, cy);
		cy += ui.text.lineHeight(Ui.CAPS);
		for (Setting s : card.settings()) {
			cy += 12;
			float rh = rowHeight(ui, s);
			ui.tip(ix - 6, cy - 4, Math.min(IW + 12, ui.text.width(s.name, Ui.BODY) + 24), Math.min(rh, 30) + 4, s.description);
			switch (s) {
				case Setting.Slider sl -> slider(ui, sl, ix, cy);
				case Setting.Toggle tg -> {
					ui.text.draw(ui.cv, s.name, ix, ui.text.baselineFor(Ui.BODY, cy + 14), Ui.BODY, Colors.TEXT);
					Anim an = ui.anim(m.key() + "#" + s.id, tg.value ? 1 : 0);
					an.set(tg.value ? 1 : 0, ui.now, 150, Ease.EASE);
					ui.switchToggle(ix + IW - 44 + 5, cy + 4, true, an.get(ui.now));
					ui.hit(ix - 6, cy - 4, IW + 12, 36, () -> tg.value = !tg.value, null);
				}
				case Setting.Text t -> {
					ui.text.draw(ui.cv, s.name, ix, ui.text.baselineFor(Ui.BODY, cy + labelHeight() / 2), Ui.BODY, Colors.TEXT);
					TextInput in = input(t);
					if (ui.focused == in) t.value = in.value();
					else if (!in.value().equals(t.value)) in.set(t.value);
					ui.input(in, ix, cy + labelHeight() + 6, IW, 34, t.placeholder, null);
				}
				default -> chips(ui, s, m, ix, cy);
			}
			cy += rh;
		}
	}

	private void slider(Ui ui, Setting.Slider sl, float ix, float cy) {
		float bh = ui.text.lineHeight(Ui.VALUE) + 6;
		ui.text.draw(ui.cv, sl.name, ix, ui.text.baselineFor(Ui.BODY, cy + bh / 2), Ui.BODY, Colors.TEXT);
		String v = sl.display();
		float vw = ui.text.width(v, Ui.VALUE) + 16;
		ui.cv.fillRoundRect(ix + IW - vw, cy, vw, bh, 6, Colors.white(0.07f));
		ui.text.draw(ui.cv, v, ix + IW - vw + 8, ui.text.baselineFor(Ui.VALUE, cy + bh / 2), Ui.VALUE, Colors.TEXT);
		float sy = cy + bh + 12;
		float frac = sl.fraction();
		ui.cv.fillRoundRect(ix, sy + 5, IW, 6, 3, Colors.white(0.1f));
		ui.cv.fillRoundRect(ix, sy + 5, IW * frac, 6, 3, Colors.ACCENT);
		float kx = ix + IW * frac;
		ui.cv.discGlow(kx, sy + 8, 8, 5, Colors.accent(0.7f));
		ui.cv.fillCircle(kx, sy + 8, 11, Colors.glass(0.9f));
		ui.cv.fillCircle(kx, sy + 8, 8, Colors.WHITE);
		ui.hit(ix - 8, sy - 4, IW + 16, 24, () -> screen.beginDrag(ui.cv, sl, ix, IW), null);
	}

	private void chips(Ui ui, Setting s, Module m, float ix, float cy) {
		List<String> opts = options(s);
		boolean inline = chipsInline(ui, s);
		float labelCentre = inline ? cy + 14 : cy + labelHeight() / 2;
		ui.text.draw(ui.cv, s.name, ix, ui.text.baselineFor(Ui.BODY, labelCentre), Ui.BODY, Colors.TEXT);
		if (inline) {
			float cxr = ix + IW;
			for (int i = opts.size() - 1; i >= 0; i--) {
				String opt = opts.get(i);
				float cw = ui.chipWidth(opt);
				chip(ui, s, m, opt, cxr - cw, cy);
				cxr -= cw + 6;
			}
			return;
		}
		float ry = cy + labelHeight() + 6;
		for (List<String> row : chipRows(ui, opts, IW)) {
			float cx = ix;
			for (String opt : row) {
				chip(ui, s, m, opt, cx, ry);
				cx += ui.chipWidth(opt) + 6;
			}
			ry += 28 + 6;
		}
	}

	private static void chip(Ui ui, Setting s, Module m, String opt, float x, float y) {
		String key = m.key() + "#" + s.id + "#" + opt;
		if (s instanceof Setting.Chips c) ui.chip(key, opt, x, y, c.has(opt), () -> c.flip(opt));
		else if (s instanceof Setting.Choice c) ui.chip(key, opt, x, y, c.is(opt), () -> c.set(opt));
	}
}
