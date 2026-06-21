package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.api.Minehut;
import dev.alfxyz.leoneclient.utils.*;
import net.minecraft.client.gui.DrawContext;
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


        if (BlockLobbyAds.check(chat, ci)) return;
        if (BlockMinehutAds.checkChat(chat, ci)) return;
        // Custom item denial detection
        if (chat.equalsIgnoreCase("You cannot use cage item here!") ||
            chat.equalsIgnoreCase("You cannot use here!") ||
            chat.equalsIgnoreCase("You cannot use cobweb circle here!")) {
            dev.alfxyz.leoneclient.LeoneClient.onCustomItemDenial();
        }
    }
}
