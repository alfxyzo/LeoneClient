package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.staffchat.OverlayFrame;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	/** Gives Combat Log Guard the chance to ask before the player leaves the world. */
	@Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
	private void leoneclient$guardDisconnect(Component reason, CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		if (Modules.COMBAT_LOG_GUARD.intercept(() -> self.disconnectFromWorld(reason))) ci.cancel();
	}

	/** One frame, which runs after the tick that handles chat and keys. Staff chat decides per frame. */
	@Inject(method = "renderFrame", at = @At("HEAD"))
	private void leoneclient$beginFrame(boolean advanceGameTime, CallbackInfo ci) {
		OverlayFrame.begin();
	}

	@Inject(method = "renderFrame", at = @At("RETURN"))
	private void leoneclient$endFrame(boolean advanceGameTime, CallbackInfo ci) {
		OverlayFrame.end();
	}
}
