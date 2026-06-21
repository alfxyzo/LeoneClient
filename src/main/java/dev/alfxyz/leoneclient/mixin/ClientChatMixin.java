package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(ChatScreen.class)
public class ClientChatMixin {
    // All anti-mute and chat logic removed as requested.
}
