package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.features.ActionBars;
import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures action bar messages for the movable bars, and drops resource pack prompts. */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void leoneclient$actionBar(Component message, boolean animateColor, CallbackInfo ci) {
		String plain = Chat.plain(message);
		if (Modules.PACK_WARNINGS.blocks(plain)) {
			ci.cancel();
			return;
		}
		boolean bars = Modules.ACTION_BAR.enabled() || Modules.COMBAT_BAR.enabled();
		if (plain.isEmpty()) {
			// servers clear the bar with an empty message; ours time out on their own
			if (bars) ci.cancel();
			return;
		}
		boolean combat = ActionBars.isCombat(plain);
		ActionBars.accept(message, combat);
		if (combat ? bars : Modules.ACTION_BAR.enabled()) ci.cancel();
	}

	@Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
	private void leoneclient$title(Component title, CallbackInfo ci) {
		if (Modules.PACK_WARNINGS.blocks(Chat.plain(title))) ci.cancel();
	}

	@Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
	private void leoneclient$subtitle(Component subtitle, CallbackInfo ci) {
		if (Modules.PACK_WARNINGS.blocks(Chat.plain(subtitle))) ci.cancel();
	}
}
