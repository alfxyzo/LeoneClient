package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.LeoneClient;
import dev.alfxyz.leoneclient.LeoneClientConfig;
import dev.alfxyz.leoneclient.utils.ActionBarStore;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class ActionbarMixin {
    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onActionBarSet(Text message, boolean tinted, CallbackInfo ci) {
        String text = message.getString();

        if (LeoneClient.onLeoneMC
                && (text.toLowerCase().contains("resource pack") || text.contains("Resources Declined"))) {
            ci.cancel();
            return;
        }

        if (!LeoneClientConfig.isActionBarEnabled()) return;

        if (text.isEmpty()) {
            ci.cancel();
            return;
        }

        if (text.contains("Combat")) {
            ActionBarStore.setCombatActionBar(message);
        } else {
            ActionBarStore.setRegularActionBar(message);
        }
        ci.cancel();
    }
}
