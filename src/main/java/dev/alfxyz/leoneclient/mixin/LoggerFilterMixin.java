package dev.alfxyz.leoneclient.mixin;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Logger.class)
public abstract class LoggerFilterMixin {
    @Inject(method = "log", at = @At("HEAD"), cancellable = true, remap = false)
    private void filterLog(Level level, String message, CallbackInfo ci) {
        if (message != null && (message.contains("Requested creation of existing team") || message.contains("Ignoring player info update for unknown player"))) {
            ci.cancel();
        }
    }
} 