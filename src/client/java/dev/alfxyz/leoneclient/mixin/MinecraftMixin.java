package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Gives Combat Log Guard the chance to ask before the player leaves the world. */
@Mixin(Minecraft.class)
public abstract class MinecraftMixin {
	@Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
	private void leoneclient$guardDisconnect(Component reason, CallbackInfo ci) {
		Minecraft self = (Minecraft) (Object) this;
		if (Modules.COMBAT_LOG_GUARD.intercept(() -> self.disconnectFromWorld(reason))) ci.cancel();
	}
}
