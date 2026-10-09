package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.Locale;

/** Hides LeoneMC's resource pack reminders from the title, subtitle and action bar. */
public final class PackWarnings extends Module {
	private int hidden;

	public PackWarnings() {
		super("pack_warnings", Category.HUD, "Pack Warnings", Icons.EYE_OFF,
			"Hides the resource pack titles and reminders LeoneMC shows when you decline its pack.", false);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	public boolean blocks(String plain) {
		if (!active()) return false;
		String l = plain.toLowerCase(Locale.ROOT);
		if (l.contains("resource pack") || l.contains("resources declined") || l.contains("texture pack")) {
			hidden++;
			return true;
		}
		return false;
	}

	@Override
	public String status() {
		return hidden == 0 ? null : hidden + " hidden";
	}
}
