package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.LeoneClientConfig;
import dev.alfxyz.leoneclient.utils.AntiMute;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(ChatScreen.class)
public class ClientChatMixin {
    private static boolean filtering = false;

    @Inject(method = "sendMessage", at = @At("HEAD"), cancellable = true)
    private void onSendMessage(String chatText, boolean addToHistory, CallbackInfo ci) {
        if (filtering || !LeoneClientConfig.isAntiMuteEnabled() || chatText.startsWith("/")) return;

        AntiMute.FilterResult result = AntiMute.filter(
            chatText,
            LeoneClientConfig.getAntiMuteReplacement(),
            LeoneClientConfig.isFilterDiscrimination(),
            LeoneClientConfig.isFilterDeathWishes(),
            LeoneClientConfig.isFilterSwears(),
            LeoneClientConfig.isFilterAdvertisements()
        );

        if (!result.filtered.equals(chatText)) {
            ci.cancel();
            filtering = true;
            try {
                ((ChatScreen)(Object)this).sendMessage(result.filtered, addToHistory);
            } finally {
                filtering = false;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(buildNotification(result.matched, LeoneClientConfig.getAntiMuteReplacement()), false);
            }
        }
    }

    private static Text buildNotification(List<String> matched, String replacement) {
        MutableText msg = Text.literal("[").formatted(Formatting.DARK_GRAY)
            .append(Text.literal("LC").formatted(Formatting.RED, Formatting.BOLD))
            .append(Text.literal("] ").formatted(Formatting.DARK_GRAY));

        if (matched.isEmpty()) {
            return msg.append(Text.literal("Your message was auto-filtered.").formatted(Formatting.GRAY));
        }

        List<String> unique = matched.stream().distinct().limit(4).collect(Collectors.toList());
        boolean more = matched.stream().distinct().count() > 4;

        for (int i = 0; i < unique.size(); i++) {
            if (i > 0) msg.append(Text.literal(", ").formatted(Formatting.DARK_GRAY));
            msg.append(Text.literal("\"" + unique.get(i) + "\"").formatted(Formatting.RED));
        }
        if (more) msg.append(Text.literal("…").formatted(Formatting.DARK_GRAY));

        msg.append(Text.literal(" → ").formatted(Formatting.DARK_GRAY));
        msg.append(Text.literal("\"" + replacement + "\"").formatted(Formatting.GREEN));

        return msg;
    }
}
