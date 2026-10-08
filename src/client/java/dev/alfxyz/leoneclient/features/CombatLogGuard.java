package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Asks for confirmation before leaving the server from the pause menu while combat tagged. */
public final class CombatLogGuard extends Module {
	private boolean confirmed;

	public CombatLogGuard() {
		super("combat_log_guard", Category.COMBAT, "Combat Log Guard", Icons.SHIELD,
			"Asks before you disconnect while combat tagged, because logging out in combat kills you and drops your items.", true);
	}

	/**
	 * Called before the game leaves the world. Returns true to stop it and ask first.
	 * {@code proceed} leaves the world for real.
	 */
	public boolean intercept(Runnable proceed) {
		Minecraft mc = Minecraft.getInstance();
		if (confirmed) {
			confirmed = false;
			return false;
		}
		if (!enabled() || !ActionBars.tagged() || !(mc.gui.screen() instanceof PauseScreen pause)) return false;
		String secs = String.format(Locale.ROOT, "%.0f", Math.ceil(ActionBars.remainingSeconds()));
		Screen confirm = new ConfirmScreen(yes -> {
			if (yes) {
				confirmed = true;
				proceed.run();
			} else {
				mc.gui.setScreen(pause);
			}
		},
			Component.literal("You are combat tagged").withStyle(ChatFormatting.RED, ChatFormatting.BOLD),
			Component.literal("Your tag ends in " + secs + "s. Leaving now counts as combat logging: you will die and drop your items."),
			Component.literal("Leave anyway"),
			Component.literal("Stay"));
		mc.gui.setScreen(confirm);
		return true;
	}
}
