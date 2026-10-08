package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.List;

/** A compact module tile for search results: icon, name, category and switch. */
final class ModuleTile {
	static final float W = (Page.W - 16) / 3, H = 52, GAP = 8;

	private ModuleTile() {
	}

	static float gridHeight(int count) {
		int rows = (count + 2) / 3;
		return rows * H + Math.max(0, rows - 1) * GAP;
	}

	/** Draws a staggered three-column grid of tiles. */
	static void grid(Ui ui, LeoneScreen screen, List<Module> mods, float x0, float y0) {
		for (int i = 0; i < mods.size(); i++) {
			Module m = mods.get(i);
			float p = ui.stagger(i);
			if (p <= 0) continue;
			float x = x0 + (i % 3) * (W + GAP);
			float y = y0 + (i / 3) * (H + GAP);
			ui.entering(p, () -> draw(ui, screen, m, x, y));
		}
	}

	static void draw(Ui ui, LeoneScreen screen, Module m, float x, float y) {
		String k = "tile#" + m.key();
		boolean hov = ui.hovered(x, y, W, H);
		boolean toggleable = m.toggleable();
		boolean on = toggleable && m.enabled();
		ColorAnim bg = ui.color(k + "#bg", on ? Colors.accent(0.16f) : Colors.white(0.035f));
		ColorAnim border = ui.color(k + "#border", on ? Colors.accent(0.55f) : Colors.white(0.07f));
		ColorAnim fg = ui.color(k + "#fg", on ? Colors.WHITE : Colors.TEXT_SECONDARY);
		Anim knob = ui.anim(k + "#knob", on ? 1 : 0);
		bg.set(on ? Colors.accent(hov ? 0.24f : 0.16f) : Colors.white(hov ? 0.07f : 0.035f), ui.now, 150, Ease.EASE);
		border.set(on ? Colors.accent(0.55f) : Colors.white(hov ? 0.16f : 0.07f), ui.now, 150, Ease.EASE);
		fg.set(on || !toggleable ? Colors.WHITE : hov ? Colors.TEXT : Colors.TEXT_SECONDARY, ui.now, 150, Ease.EASE);
		knob.set(on ? 1 : 0, ui.now, 150, Ease.EASE);

		ui.cv.fillRoundRect(x, y, W, H, 11, bg.get(ui.now));
		ui.cv.borderRoundRect(x, y, W, H, 11, 1, border.get(ui.now));
		ui.icons.draw(ui.cv, m.icon, x + 14, y + H / 2 - 9, 18, 1.8f, on ? Colors.WHITE : Colors.TEXT_DOCK);
		float tx = x + 14 + 18 + 12, maxW = W - (tx - x) - 26 - 14 - 8;
		float lh = ui.text.lineHeight(Ui.TILE), sh = ui.text.lineHeight(Ui.SMALL);
		float top = y + (H - lh - sh) / 2;
		ui.text.draw(ui.cv, ui.text.fit(m.name, Ui.TILE, maxW), tx, top + ui.text.ascent(Ui.TILE), Ui.TILE, fg.get(ui.now));
		ui.text.draw(ui.cv, ui.text.fit(m.category.displayName, Ui.SMALL, maxW), tx, top + lh + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_HINT);
		if (toggleable) ui.switchToggle(x + W - 14 - 26, y + H / 2 - 7, false, knob.get(ui.now));
		else ui.icons.draw(ui.cv, Icons.GEAR, x + W - 14 - 16, y + H / 2 - 8, 16, 1.8f, Colors.TEXT_MUTED);
		Runnable settings = () -> screen.openSettings(m);
		ui.hit(x, y, W, H, toggleable ? () -> screen.toggle(m) : settings, settings);
		ui.tip(x, y, W, H, m.description);
	}

	static List<Module> enabled() {
		return Modules.all().stream().filter(m -> m.toggleable() && m.enabled()).toList();
	}
}
