package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Takes the combat tag line out of the action bar and shows it on its own bar. */
public final class CombatBar extends Module {
	public final Setting.Toggle merge = add(new Setting.Toggle("merge", "Merge with action bar", "LAYOUT", false),
		"Shows the combat line after the action bar on one line, instead of on its own bar. Needs Action Bar on.");
	public final Setting.Toggle hideWithTimer = add(new Setting.Toggle("hide_with_timer", "Hide while timer shows", "LAYOUT", false),
		"Hides the combat line while Combat Timer is showing the countdown as a status effect.");
	public final Setting.Toggle background = add(new Setting.Toggle("background", "Background", "STYLE", false),
		"Draws a dark rounded backdrop behind the text.");
	public final Setting.Toggle shadow = add(new Setting.Toggle("shadow", "Text shadow", "STYLE", true),
		"Draws the text with a shadow, like the vanilla action bar.");

	public CombatBar() {
		super("combat_bar", Category.COMBAT, "Combat Bar", Icons.BARS,
			"Puts the combat tag line on its own bar, so it stops replacing the action bar. Move it with Modify HUD.", true);
	}

	/** True when the combat line is drawn as part of the action bar overlay. */
	public boolean merged() {
		return enabled() && merge.get() && Modules.ACTION_BAR.enabled();
	}

	/** The combat line to show, or null while hidden. */
	public @Nullable Component line() {
		if (!enabled()) return null;
		if (hideWithTimer.get() && Modules.COMBAT_TIMER.active() && ActionBars.tagged()) return null;
		return ActionBars.combat();
	}
}
