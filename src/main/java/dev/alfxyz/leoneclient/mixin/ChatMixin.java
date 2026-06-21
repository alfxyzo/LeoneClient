package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.LeoneClient;
import dev.alfxyz.leoneclient.LeoneClientConfig;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChatHud.class)
public class ChatMixin {
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V", at = @At("HEAD"), cancellable = true)
    private void onMessage(Text message, MessageSignatureData signature, MessageIndicator indicator, CallbackInfo ci) {
        String chat = message.getString();
        if (LeoneClient.onLeoneMC
                && LeoneClientConfig.isHideChatAlerts()
                && chat.contains("[Alert]")
                && chat.toLowerCase().contains("staff")) {
            ci.cancel();
        }
    }
}
