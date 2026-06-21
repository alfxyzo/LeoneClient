package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.utils.AntiMute;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerDebugMixin {
    @ModifyVariable(
        method = "sendChatMessage",
        at = @At("HEAD"),
        argsOnly = true,
        ordinal = 0
    )
    private String modifyChatMessage(String message) {
        if (AntiMute.shouldBlockMessage(message)) {
            System.out.println("[LeoneClient] Blocked message: " + message);
            return ""; // Return empty string to effectively block it
        }
        return message; // Return original message if not blocked
    }
} 