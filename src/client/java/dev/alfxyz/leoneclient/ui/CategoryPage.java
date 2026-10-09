package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** A category's modules as cards. */
final class CategoryPage extends Page {
	private static final float EMPTY_H = 64;
	final Category category;

	/** Recent alerts and reports, under the Staff modules. */
	private final @Nullable StaffSection staff;

	CategoryPage(LeoneScreen screen, Category category) {
		super(screen);
		this.category = category;
		this.staff = category == Category.STAFF ? new StaffSection(screen) : null;
	}

	private float gridHeight(List<Module> mods) {
		return mods.isEmpty() && category.isServer() ? EMPTY_H : ModuleCard.gridHeight(mods.size());
	}

	@Override
	float height(Ui ui) {
		return 38 + 18 + gridHeight(Modules.of(category)) + (staff != null ? staff.height(ui) : 0);
	}

	@Override
	void draw(Ui ui, float x, float y) {
		List<Module> mods = Modules.of(category);
		ui.header(category.icon, category.label(), x, y);
		int on = 0, toggleable = 0;
		for (Module m : mods) {
			if (!m.toggleable()) continue;
			toggleable++;
			if (m.enabled()) on++;
		}
		String count = toggleable == 0 ? "" : on + " of " + toggleable + " on";
		String hint = category.isServer() ? "Only while you are on " + category.label() : "Click a module to toggle it, right-click for settings";
		float cw = ui.text.width(count, Ui.HINT);
		ui.text.draw(ui.cv, count, x + W - cw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_SECONDARY);
		float hw = ui.text.width(hint, Ui.HINT);
		ui.text.draw(ui.cv, hint, x + W - cw - (cw > 0 ? 18 : 0) - hw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_HINT);
		float gy = y + 38 + 18;
		if (mods.isEmpty() && category.isServer()) {
			float by = gy;
			ui.entering(ui.stagger(0), () -> {
				ui.cv.dashedBorderRoundRect(x, by, W, EMPTY_H, 12, 1.5f, 4.5f, 3f, Colors.white(0.12f));
				ui.centred("There are no " + category.label() + " modules yet.", Ui.BODY, x, by, W, EMPTY_H, Colors.TEXT_HINT);
			});
			return;
		}
		ModuleCard.grid(ui, screen, mods, x, gy);
		if (staff != null) {
			float sy = gy + ModuleCard.gridHeight(mods.size());
			ui.entering(ui.stagger(mods.size()), () -> staff.draw(ui, x, sy, W));
		}
	}
}
