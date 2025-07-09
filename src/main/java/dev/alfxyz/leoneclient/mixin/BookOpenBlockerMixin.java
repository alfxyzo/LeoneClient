package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ingame.BookScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class BookOpenBlockerMixin {
    @Inject(method = "setScreen", at = @At("HEAD"), cancellable = true)
    private void blockBookScreen(net.minecraft.client.gui.screen.Screen screen, CallbackInfo ci) {
        if (screen instanceof BookScreen) {
            
            ci.cancel();
        }
    }
} 