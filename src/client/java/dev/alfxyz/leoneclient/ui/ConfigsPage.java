package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneConfig;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.List;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** Config profiles: create one from the current state, load, delete. */
final class ConfigsPage extends Page {
	private static final float ROW = 52, ROW_GAP = 8;
	private static final int VISIBLE_ROWS = 6;
	final TextInput name = new TextInput(24, c -> Character.isLetterOrDigit(c) || c == ' ' || c == '_' || c == '-');
	private List<LeoneConfig.Profile> profiles = List.of();
	private @Nullable String confirmDelete;
	private double confirmAt;
	private final Anim scroll = new Anim(0);

	ConfigsPage(LeoneScreen screen) {
		super(screen);
	}

	@Override
	@Nullable TextInput input() {
		return name;
	}

	@Override
	void opened() {
		refresh();
		confirmDelete = null;
		scroll.snap(0);
	}

	private void refresh() {
		profiles = LeoneConfig.listProfiles();
	}

	private float listHeight() {
		int rows = Math.max(1, Math.min(VISIBLE_ROWS, profiles.size()));
		return rows * ROW + (rows - 1) * ROW_GAP;
	}

	private float maxScroll() {
		float full = profiles.size() * ROW + Math.max(0, profiles.size() - 1) * ROW_GAP;
		return Math.max(0, full - listHeight());
	}

	@Override
	float height(Ui ui) {
		return 38 + 18 + 38 + 18 + ui.text.lineHeight(Ui.CAPS) + 12 + listHeight();
	}

	private boolean canCreate() {
		String n = name.value().strip();
		return LeoneConfig.isValidName(n) && profiles.stream().noneMatch(p -> p.name().equalsIgnoreCase(n));
	}

	private @Nullable String status() {
		String n = name.value().strip();
		if (n.isEmpty()) return null;
		if (profiles.stream().anyMatch(p -> p.name().equalsIgnoreCase(n))) return "Name taken";
		if (!LeoneConfig.isValidName(n)) return "Use letters, digits, spaces, _ or -";
		return null;
	}

	@Override
	void submit() {
		if (!canCreate()) return;
		LeoneConfig.create(name.value().strip());
		name.clear();
		refresh();
		scroll.snap(0);
	}

	@Override
	boolean scroll(double amount) {
		float target = Math.max(0, Math.min(maxScroll(), scroll.target() - (float) amount * 40));
		scroll.set(target, System.nanoTime() / 1e6, 180, Ease.SNAP);
		return true;
	}

	@Override
	void draw(Ui ui, float x, float y) {
		if (confirmDelete != null && ui.now - confirmAt > 3000) confirmDelete = null;
		ui.header(Icons.CONFIGS, "Configs", x, y);
		float folderW = ui.buttonWidth("Open folder", Icons.FOLDER, Ui.BUTTON);
		ui.button("cfg-folder", x + W - folderW, y + 4, 30, "Open folder", Icons.FOLDER, Ui.Btn.GHOST, true,
			() -> Util.getPlatform().openPath(LeoneConfig.configsFolder()));

		float ry = y + 38 + 18;
		boolean ok = canCreate();
		float createW = ui.buttonWidth("Save as new", null, Ui.BUTTON);
		ui.entering(ui.stagger(0), () -> {
			ui.input(name, x, ry, W - createW - 8, 38, "Name a new config from your current setup", null, status());
			ui.button("cfg-create", x + W - createW, ry, 38, "Save as new", null, Ui.Btn.ACCENT, ok, this::submit);
		});

		float cy = ry + 38 + 18;
		ui.caps(profiles.size() == 1 ? "1 CONFIG" : profiles.size() + " CONFIGS", x, cy);
		float ly = cy + ui.text.lineHeight(Ui.CAPS) + 12;
		float lh = listHeight();
		float off = scroll.get(ui.now);
		ui.cv.push();
		ui.cv.clipRect(x - 2, ly - 1, x + W + 2, ly + lh + 1);
		for (int i = 0; i < profiles.size(); i++) {
			LeoneConfig.Profile p = profiles.get(i);
			float rowY = ly + i * (ROW + ROW_GAP) - off;
			if (rowY + ROW < ly - 1 || rowY > ly + lh + 1) continue;
			ui.entering(ui.stagger(i + 1), () -> row(ui, p, x, rowY, ly, lh));
		}
		ui.cv.pop();
		if (maxScroll() > 0) {
			float frac = lh / (lh + maxScroll());
			float th = Math.max(24, lh * frac), tt = ly + (lh - th) * (off / maxScroll());
			ui.cv.fillRoundRect(x + W + 6, tt, 3, th, 1.5f, Colors.white(0.18f));
		}
	}

	private void row(Ui ui, LeoneConfig.Profile p, float x, float y, float clipTop, float clipH) {
		boolean active = p.name().equals(LeoneConfig.activeConfig);
		boolean visible = y >= clipTop - 1 && y + ROW <= clipTop + clipH + 1;
		boolean hov = visible && ui.hovered(x, y, W, ROW);
		ColorAnim bg = ui.color("cfg#" + p.name(), Colors.white(0.035f));
		bg.set(active ? Colors.accent(0.12f) : Colors.white(hov ? 0.06f : 0.035f), ui.now, 150, Ease.EASE);
		ui.cv.fillRoundRect(x, y, W, ROW, 12, bg.get(ui.now));
		ui.cv.borderRoundRect(x, y, W, ROW, 12, 1, active ? Colors.accent(0.45f) : Colors.white(hov ? 0.14f : 0.07f));
		ui.cv.fillRoundRect(x + 10, y + 10, 32, 32, 9, active ? Colors.accent(0.25f) : Colors.white(0.06f));
		ui.icons.draw(ui.cv, Icons.CONFIGS, x + 18, y + 18, 16, 1.8f, active ? Colors.WHITE : Colors.TEXT_DOCK);
		float lh = ui.text.lineHeight(Ui.BODY_STRONG), sh = ui.text.lineHeight(Ui.SMALL);
		float top = y + (ROW - lh - sh) / 2;
		ui.text.draw(ui.cv, p.name(), x + 54, top + ui.text.ascent(Ui.BODY_STRONG), Ui.BODY_STRONG, Colors.TEXT);
		String meta = (p.enabled() == 1 ? "1 module on" : p.enabled() + " modules on") + " · " + (active ? "in use, saves automatically" : "saved " + ago(p.modified()));
		ui.text.draw(ui.cv, meta, x + 54, top + lh + ui.text.ascent(Ui.SMALL), Ui.SMALL, Colors.TEXT_HINT);

		float right = x + W - 10;
		if (active) {
			String label = "Active";
			float bw = ui.text.width(label, Ui.BUTTON) + 22;
			ui.cv.fillRoundRect(right - bw, y + ROW / 2 - 13, bw, 26, 13, Colors.accent(0.2f));
			ui.cv.borderRoundRect(right - bw, y + ROW / 2 - 13, bw, 26, 13, 1, Colors.accent(0.6f));
			ui.text.draw(ui.cv, label, right - bw + 11, ui.text.baselineFor(Ui.BUTTON, y + ROW / 2), Ui.BUTTON, Colors.WHITE);
			return;
		}
		if (!visible) return;
		boolean confirming = p.name().equals(confirmDelete);
		if (confirming) {
			float dw = ui.buttonWidth("Delete", Icons.TRASH, Ui.BUTTON);
			ui.button("cfg-del#" + p.name(), right - dw, y + ROW / 2 - 15, 30, "Delete", Icons.TRASH, Ui.Btn.DANGER, true, () -> {
				LeoneConfig.delete(p.name());
				confirmDelete = null;
				refresh();
				scroll.snap(Math.min(scroll.target(), maxScroll()));
			});
			right -= dw + 6;
		} else {
			ui.iconButton("cfg-trash#" + p.name(), right - 30, y + ROW / 2 - 15, 30, Icons.TRASH, 15, Colors.TEXT_HINT, 0xFFFF8A8A, () -> {
				confirmDelete = p.name();
				confirmAt = ui.now;
			});
			right -= 30 + 6;
		}
		float lw = ui.buttonWidth("Load", null, Ui.BUTTON);
		ui.button("cfg-load#" + p.name(), right - lw, y + ROW / 2 - 15, 30, "Load", null, Ui.Btn.GHOST, true, () -> {
			LeoneConfig.switchTo(p.name());
			refresh();
			scroll.snap(0);
		});
	}

	static String ago(long millis) {
		return dev.alfxyz.leoneclient.Time.ago(millis);
	}
}
