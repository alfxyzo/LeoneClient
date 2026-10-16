package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.features.ActionBars;
import dev.alfxyz.leoneclient.hud.Sidebar;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.staffchat.StaffState;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Captures action bar messages for the movable bars, drops resource pack prompts, and lets HUD Tailor draw
 * the scoreboard and leave out screen effects.
 */
@Mixin(Hud.class)
public abstract class HudMixin {
	@Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
	private void leoneclient$actionBar(Component message, boolean animateColor, CallbackInfo ci) {
		String plain = Chat.plain(message);
		StaffState.onActionBar(plain);
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

	@Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
	private void leoneclient$sidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
		if (!Modules.HUD_TAILOR.active()) return;
		Sidebar.draw(graphics, objective);
		ci.cancel();
	}

	@Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
	private void leoneclient$vignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
		if (Modules.HUD_TAILOR.hides("Vignette")) ci.cancel();
	}

	@Inject(method = "extractSpyglassOverlay", at = @At("HEAD"), cancellable = true)
	private void leoneclient$spyglass(GuiGraphicsExtractor graphics, float scale, CallbackInfo ci) {
		if (Modules.HUD_TAILOR.hides("Spyglass")) ci.cancel();
	}

	@Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
	private void leoneclient$portal(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
		if (Modules.HUD_TAILOR.hides("Portal")) ci.cancel();
	}

	@Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
	private void leoneclient$nausea(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
		if (Modules.HUD_TAILOR.hides("Nausea")) ci.cancel();
	}

	/** Full-screen textures: the powder snow frost, and the carved pumpkin (or any worn item's) overlay. */
	@Inject(method = "extractTextureOverlay", at = @At("HEAD"), cancellable = true)
	private void leoneclient$textureOverlay(GuiGraphicsExtractor graphics, Identifier texture, float alpha, CallbackInfo ci) {
		boolean snow = texture.getPath().contains("powder_snow");
		if (Modules.HUD_TAILOR.hides(snow ? "Powder snow" : "Pumpkin")) ci.cancel();
	}
}
