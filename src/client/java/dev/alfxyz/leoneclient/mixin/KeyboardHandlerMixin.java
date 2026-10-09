package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.LeoneClient;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hands each key press to the module keybinds as it happens, so a quick tap between two ticks still counts. */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardHandlerMixin {
	@Inject(method = "keyPress", at = @At("HEAD"))
	private void leoneclient$keyPress(long window, int action, KeyEvent event, CallbackInfo ci) {
		LeoneClient.keyPressed(window, action, event.key());
	}
}
