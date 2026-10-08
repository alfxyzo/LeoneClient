package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import java.util.List;

/** A category's modules as cards. */
final class CategoryPage extends Page {
	final Category category;

	CategoryPage(LeoneScreen screen, Category category) {
		super(screen);
		this.category = category;
	}

	@Override
	float height(Ui ui) {
		return 38 + 18 + ModuleCard.gridHeight(Modules.of(category).size());
	}

	@Override
	void draw(Ui ui, float x, float y) {
		List<Module> mods = Modules.of(category);
		ui.header(category.icon, category.displayName, x, y);
		int on = 0, toggleable = 0;
		for (Module m : mods) {
			if (!m.toggleable()) continue;
			toggleable++;
			if (m.enabled()) on++;
		}
		String count = toggleable == 0 ? "" : on + " of " + toggleable + " on";
		String hint = "Click a card to switch it, right-click for settings";
		float cw = ui.text.width(count, Ui.HINT);
		ui.text.draw(ui.cv, count, x + W - cw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_SECONDARY);
		float hw = ui.text.width(hint, Ui.HINT);
		ui.text.draw(ui.cv, hint, x + W - cw - (cw > 0 ? 18 : 0) - hw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_HINT);
		ModuleCard.grid(ui, screen, mods, x, y + 38 + 18);
	}
}
