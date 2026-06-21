package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.LeoneClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class TitleMixin {
    @Inject(method = "setTitle", at = @At("HEAD"), cancellable = true)
    private void blockResourcePackTitle(Text title, CallbackInfo ci) {
        if (!LeoneClient.onLeoneMC) return;
        String text = title.getString();
        if (text.contains("Resources Declined") || text.contains("Resource Pack")) {
            ci.cancel();
        }
    }

    @Inject(method = "setSubtitle", at = @At("HEAD"), cancellable = true)
    private void blockResourcePackSubtitle(Text subtitle, CallbackInfo ci) {
        if (!LeoneClient.onLeoneMC) return;
        String text = subtitle.getString();
        if (text.contains("resource pack") || text.contains("Resource Pack")) {
            ci.cancel();
        }
    }
}
