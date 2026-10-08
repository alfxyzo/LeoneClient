package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Draws the action bar as a movable overlay instead of in its vanilla spot. */
public final class ActionBar extends Module {
	public final Setting.Toggle background = add(new Setting.Toggle("background", "Background", "STYLE", false),
		"Draws a dark rounded backdrop behind the text.");
	public final Setting.Toggle shadow = add(new Setting.Toggle("shadow", "Text shadow", "STYLE", true),
		"Draws the text with a shadow, like the vanilla action bar.");
	public final Setting.Toggle fade = add(new Setting.Toggle("fade", "Fade out", "STYLE", true),
		"Fades the bar out when the server stops updating it, instead of cutting it off.");

	public ActionBar() {
		super("action_bar", Category.HUD, "Action Bar", Icons.PANEL_BOTTOM,
			"Shows the action bar wherever you put it with Modify HUD. With Combat Bar on, the combat line no longer replaces it.", true);
	}

	/** The line to draw: the regular bar (plus the combat line when merged), or whatever came last. */
	public @Nullable Component line() {
		if (!enabled()) return null;
		CombatBar cb = Modules.COMBAT_BAR;
		if (!cb.enabled()) return ActionBars.latest();
		Component regular = ActionBars.regular();
		Component combat = cb.merged() ? cb.line() : null;
		if (regular != null && combat != null) return regular.copy().append(Component.literal("  §7|  ")).append(combat);
		return regular != null ? regular : combat;
	}

	/** When the line being drawn arrived, for fading. */
	public long lineAt() {
		CombatBar cb = Modules.COMBAT_BAR;
		if (!cb.enabled()) return ActionBars.latestAt();
		if (cb.merged() && cb.line() != null) return Math.max(ActionBars.regularAt(), ActionBars.combatAt());
		return ActionBars.regularAt();
	}
}
