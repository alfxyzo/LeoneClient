package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.utils.ActionBarStore;
import dev.alfxyz.leoneclient.utils.BlockLobbyMapAds;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class ActionbarMixin {
    private static Text lastNormalActionBar = null;
    private static long lastNormalActionBarTimestamp = 0;

    @Inject(method = "setOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void onActionBarSet(Text message, boolean tinted, CallbackInfo ci) {
        if (!dev.alfxyz.leoneclient.LeoneClientConfig.isActionBarEnabled()) return;
        String text = message.getString();
        if (text.contains("Combat")) {
            ActionBarStore.setCombatActionBar(message);
            ci.cancel(); 
        } else {
            ActionBarStore.setRegularActionBar(message);
            ci.cancel(); 
        }
        BlockLobbyMapAds.checkActionbar(text, ci);
    }
}
