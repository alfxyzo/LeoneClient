package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.List;

/**
 * A module in its category: icon, name, description, live status and switch.
 * Categories hold a handful of modules, so each gets room to explain itself.
 */
final class ModuleCard {
	static final float W = (Page.W - 12) / 2, H = 102, GAP = 12;
	private static final float PAD = 16, ICON = 40, TEXT_X = PAD + ICON + 14;

	private ModuleCard() {
	}

	static float gridHeight(int count) {
		int rows = (count + 1) / 2;
		return rows * H + Math.max(0, rows - 1) * GAP;
	}

	static void grid(Ui ui, LeoneScreen screen, List<Module> mods, float x0, float y0) {
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			float p = ui.stagger(i);
			if (p <= 0) continue;
			float x = x0 + (i % 2) * (W + GAP);
			float y = y0 + (i / 2) * (H + GAP);
			ui.entering(p, () -> draw(ui, screen, m, x, y));
		}
	}

	static void draw(Ui ui, LeoneScreen screen, Module m, float x0, float y) {
		float x = x0 + screen.shake(m);
		String k = "card#" + m.key();
		boolean hov = ui.hovered(x, y, W, H);
		boolean toggleable = m.toggleable();
		boolean on = toggleable && m.enabled();
		boolean waiting = on && m.leoneOnly() && !LeoneMC.active();
		ColorAnim bg = ui.color(k + "#bg", on ? Colors.accent(0.14f) : Colors.white(0.035f));
		ColorAnim border = ui.color(k + "#border", on ? Colors.accent(0.5f) : Colors.white(0.07f));
		Anim knob = ui.anim(k + "#knob", on ? 1 : 0);
		Anim gear = ui.anim(k + "#gear", 0);
		bg.set(on ? Colors.accent(hov ? 0.2f : 0.13f) : Colors.white(hov ? 0.065f : 0.035f), ui.now, 150, Ease.EASE);
		border.set(on ? Colors.accent(hov ? 0.7f : 0.5f) : Colors.white(hov ? 0.16f : 0.07f), ui.now, 150, Ease.EASE);
		knob.set(on ? 1 : 0, ui.now, 150, Ease.EASE);
		gear.set(hov ? 1 : 0, ui.now, 150, Ease.EASE);

		ui.cv.fillRoundRect(x, y, W, H, 14, bg.get(ui.now));
		ui.cv.borderRoundRect(x, y, W, H, 14, 1, border.get(ui.now));

		float ix = x + PAD, iy = y + PAD;
		ui.cv.fillRoundRect(ix, iy, ICON, ICON, 11, on ? Colors.accent(0.24f) : Colors.white(0.06f));
		ui.cv.borderRoundRect(ix, iy, ICON, ICON, 11, 1, on ? Colors.accent(0.55f) : Colors.white(0.08f));
		ui.icons.draw(ui.cv, m.icon, ix + 10, iy + 10, 20, 1.8f, on ? Colors.WHITE : Colors.TEXT_DOCK);

		float tx = x + TEXT_X;
		float right = x + W - PAD - (toggleable ? 34 + 12 : 22);
		float nameY = y + PAD - 1;
		float nameH = ui.text.lineHeight(Ui.CARD_TITLE);
		String name = ui.text.fit(m.name, Ui.CARD_TITLE, right - tx);
		ui.text.draw(ui.cv, name, tx, nameY + ui.text.ascent(Ui.CARD_TITLE), Ui.CARD_TITLE, on || !toggleable ? Colors.WHITE : Colors.TEXT);
		float bx = tx + ui.text.width(name, Ui.CARD_TITLE) + 8, by = nameY + nameH / 2;
		if (m.leoneOnly() && bx + ui.badgeWidth("LeoneMC") <= right) bx += ui.badge("LeoneMC", bx, by, Colors.ACCENT_RGB, on && !waiting) + 5;
		if (m.bind >= 0) {
			String key = ModuleSettingsView.keyName(m.bind);
			if (bx + ui.badgeWidth(key) <= right) ui.badge(key, bx, by, 0xFFFFFF, false);
		}
		String status = waiting ? "Waiting for LeoneMC" : on || !toggleable ? m.status() : m.unavailable();
		// the description runs under the switch; it gives up its last line to the status
		float descRight = x + W - PAD - 22;
		ui.paragraph(m.description, Ui.CARD_TEXT, tx, nameY + nameH + 3, descRight - tx, status != null ? 2 : 3, Colors.TEXT_HINT);

		if (status != null) {
			float sy = y + H - PAD + 2 - ui.text.lineHeight(Ui.SMALL) / 2;
			int dot = waiting || m.unavailable() != null ? 0xFFF59E0B : Colors.ACCENT;
			ui.cv.fillCircle(tx + 3, sy, 3, dot);
			ui.text.draw(ui.cv, ui.text.fit(status, Ui.SMALL, right - tx - 12), tx + 12, ui.text.baselineFor(Ui.SMALL, sy), Ui.SMALL, Colors.TEXT_MUTED);
		}

		if (toggleable) {
			ui.switchToggle(x + W - PAD - 34, y + PAD + 1, true, knob.get(ui.now));
		}
		float go = toggleable ? gear.get(ui.now) : 1;
		if (go > 0) {
			ui.cv.push();
			ui.cv.mulAlpha(go);
			float gx = x + W - PAD - 17, gy = y + H - PAD - 17;
			ui.icons.draw(ui.cv, Icons.GEAR, gx, gy, 17, 1.8f, hov ? Colors.TEXT : Colors.TEXT_MUTED);
			ui.cv.pop();
		}
		Runnable settings = () -> screen.openSettings(m);
		ui.hit(x, y, W, H, toggleable ? () -> screen.toggle(m) : settings, settings);
		ui.hit(x + W - PAD - 26, y + H - PAD - 26, 30, 30, settings, settings);
	}
}
