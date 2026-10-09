package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneConfig;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.Overlay;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.ArrayList;
import java.util.List;

/** Turns HUD overlays on and off, and links to the HUD editor. */
final class OverlaysPage extends Page {
	private static final float TILE_W = (W - 16) / 3, TILE_H = 56, GAP = 8;

	OverlaysPage(LeoneScreen screen) {
		super(screen);
	}

	private static List<Overlay> overlays() {
		List<Overlay> out = new ArrayList<>();
		for (Overlay o : Hud.ALL) if (o.available()) out.add(o);
		return out;
	}

	@Override
	float height(Ui ui) {
		int rows = (overlays().size() + 2) / 3;
		return 38 + 18 + rows * TILE_H + (rows - 1) * GAP;
	}

	@Override
	void draw(Ui ui, float x, float y) {
		ui.header(Icons.OVERLAYS, "Overlays", x, y);
		float bw = ui.buttonWidth("Modify HUD", Icons.HUD, Ui.BUTTON);
		ui.button("ov-edit", x + W - bw, y + 4, 30, "Modify HUD", Icons.HUD, Ui.Btn.GHOST, true, screen::openHudEditor);
		int on = 0;
		List<Overlay> list = overlays();
		for (Overlay o : list) if (o.enabled()) on++;
		String hint = "Click to show or hide, right-click for settings   " + on + " of " + list.size() + " on";
		float hw = ui.text.width(hint, Ui.HINT);
		ui.text.draw(ui.cv, hint, x + W - bw - 14 - hw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_HINT);

		float gy = y + 38 + 18;
		for (int i = 0; i < list.size(); i++) {
			Overlay o = list.get(i);
			float tx = x + (i % 3) * (TILE_W + GAP), ty = gy + (i / 3) * (TILE_H + GAP);
			ui.entering(ui.stagger(i), () -> tile(ui, o, tx, ty));
		}
	}

	private void tile(Ui ui, Overlay o, float x, float y) {
		String k = "ov#" + o.id;
		boolean hov = ui.hovered(x, y, TILE_W, TILE_H);
		boolean on = o.enabled();
		ColorAnim bg = ui.color(k + "#bg", Colors.white(0.035f));
		ColorAnim border = ui.color(k + "#border", Colors.white(0.07f));
		Anim knob = ui.anim(k + "#knob", on ? 1 : 0);
		Anim gear = ui.anim(k + "#gear", 0);
		gear.set(hov ? 1 : 0, ui.now, 150, Ease.EASE);
		bg.set(on ? Colors.accent(hov ? 0.24f : 0.16f) : Colors.white(hov ? 0.07f : 0.035f), ui.now, 150, Ease.EASE);
		border.set(on ? Colors.accent(0.55f) : Colors.white(hov ? 0.16f : 0.07f), ui.now, 150, Ease.EASE);
		knob.set(on ? 1 : 0, ui.now, 150, Ease.EASE);
		ui.cv.fillRoundRect(x, y, TILE_W, TILE_H, 11, bg.get(ui.now));
		ui.cv.borderRoundRect(x, y, TILE_W, TILE_H, 11, 1, border.get(ui.now));
		float lh = ui.text.lineHeight(Ui.TILE), sh = ui.text.lineHeight(Ui.SMALL);
		float top = y + (TILE_H - lh - sh) / 2;
		float maxW = TILE_W - 15 - 14 - 26 - 10;
		ui.text.draw(ui.cv, o.name, x + 15, top + ui.text.ascent(Ui.TILE), Ui.TILE, on ? Colors.WHITE : hov ? Colors.TEXT : Colors.TEXT_SECONDARY);
		ui.text.draw(ui.cv, ui.text.fit(o.description, Ui.SMALL, maxW), x + 15, top + lh + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_HINT);
		ui.switchToggle(x + TILE_W - 14 - 26, y + TILE_H / 2 - 7, false, knob.get(ui.now));
		Runnable settings = () -> screen.openSettings(OverlaySettings.of(o));
		ui.hit(x, y, TILE_W, TILE_H, () -> {
			o.setEnabled(!o.enabled());
			LeoneConfig.save();
		}, settings);
		float g = gear.get(ui.now);
		if (g > 0.01f) {
			// a gear beside the switch, for those who do not think to right-click
			float gx = x + TILE_W - 14 - 26 - 8 - 22, gy = y + TILE_H / 2 - 11;
			ui.cv.push();
			ui.cv.mulAlpha(g);
			ui.iconButton(k + "#settings", gx, gy, 22, Icons.GEAR, 13, Colors.TEXT_MUTED, Colors.WHITE, settings);
			ui.cv.pop();
		}
	}
}
