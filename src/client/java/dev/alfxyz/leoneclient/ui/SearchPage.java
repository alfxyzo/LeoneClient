package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.jspecify.annotations.Nullable;

/** Searches every module by name, category or description. With no query it lists the modules that are on. */
final class SearchPage extends Page {
	private static final int MAX = 21;
	final TextInput query = new TextInput(32, c -> true);
	private String lastQuery = "";
	private List<Module> results = List.of();
	private int total;

	SearchPage(LeoneScreen screen) {
		super(screen);
	}

	@Override
	@Nullable TextInput input() {
		return query;
	}

	@Override
	void opened() {
		screen.focus(query);
		refresh(true);
	}

	private void refresh(boolean force) {
		String q = query.value().strip().toLowerCase(Locale.ROOT);
		if (!force && q.equals(lastQuery)) return;
		lastQuery = q;
		List<Module> all;
		if (q.isEmpty()) {
			all = ModuleTile.enabled();
		} else {
			all = new ArrayList<>();
			for (Module m : Modules.all()) {
				if (!m.category.visible()) continue;
				String name = m.name.toLowerCase(Locale.ROOT);
				if (name.contains(q) || m.category.label().toLowerCase(Locale.ROOT).startsWith(q)
					|| q.length() >= 3 && m.description.toLowerCase(Locale.ROOT).contains(q)) all.add(m);
			}
			all.sort(Comparator.comparingInt((Module m) -> rank(m, q)).thenComparing(m -> m.name));
		}
		total = all.size();
		results = all.subList(0, Math.min(MAX, all.size()));
	}

	private static int rank(Module m, String q) {
		String name = m.name.toLowerCase(Locale.ROOT);
		if (name.equals(q)) return 0;
		if (name.startsWith(q)) return 1;
		for (String word : name.split(" ")) if (word.startsWith(q)) return 2;
		if (name.contains(q)) return 3;
		return m.category.label().toLowerCase(Locale.ROOT).startsWith(q) ? 4 : 5;
	}

	private float listTop(Ui ui) {
		return 38 + 18 + ui.text.lineHeight(Ui.CAPS) + 12;
	}

	@Override
	float height(Ui ui) {
		refresh(false);
		return listTop(ui) + (results.isEmpty() ? 72 : ModuleTile.gridHeight(results.size()));
	}

	@Override
	void draw(Ui ui, float x, float y) {
		refresh(false);
		ui.cv.fillRoundRect(x, y, 38, 38, 11, Colors.accent(0.2f));
		ui.cv.borderRoundRect(x, y, 38, 38, 11, 1, Colors.accent(0.6f));
		ui.icons.draw(ui.cv, Icons.SEARCH, x + 10, y + 10, 18, 2, Colors.WHITE);
		String count = total == 1 ? "1 result" : total + " results";
		if (lastQuery.isEmpty()) count = total + " on";
		float cw = ui.text.width(count, Ui.HINT);
		ui.input(query, x + 50, y, W - 50 - cw - 16, 38, "Search modules…", null);
		ui.text.draw(ui.cv, count, x + W - cw, ui.text.baselineFor(Ui.HINT, y + 19), Ui.HINT, Colors.TEXT_HINT);

		float cy = y + 38 + 18;
		String caption = lastQuery.isEmpty() ? "MODULES THAT ARE ON" : total > MAX ? "TOP " + MAX + " OF " + total + " MATCHES" : "MATCHES";
		ui.caps(caption, x, cy);
		float ly = y + listTop(ui);
		if (results.isEmpty()) {
			ui.cv.dashedBorderRoundRect(x, ly, W, 72, 12, 1.5f, 4.5f, 3f, Colors.white(0.12f));
			String msg = lastQuery.isEmpty() ? "No modules are on. Start typing to find one." : "No modules match “" + query.value().strip() + "”";
			ui.centred(msg, Ui.DESC, x, ly, W, 72, Colors.TEXT_HINT);
			return;
		}
		ModuleTile.grid(ui, screen, results, x, ly);
	}

	@Override
	void submit() {
		if (!results.isEmpty() && !lastQuery.isEmpty()) screen.toggle(results.get(0));
	}
}
