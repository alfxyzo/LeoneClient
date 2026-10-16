package dev.alfxyz.leoneclient.mixin.ads;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ad Block: silences Minehut's advert sounds before they reach the sound engine. */
@Mixin(SoundManager.class)
public abstract class AdSoundMixin {
	@Inject(method = "play", at = @At("HEAD"), cancellable = true)
	private void leoneclient$muteAds(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
		if (Modules.AD_BLOCK.onSound(sound)) cir.setReturnValue(SoundEngine.PlayResult.NOT_STARTED);
	}
}
