package dev.alfxyz.leoneclient.utils;

import dev.alfxyz.leoneclient.LeoneClient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

public class BlockLobbyAds {
    public static String name = "block_lobby_ads";
    public static String description = "Blocks ads made by players in the lobby.";
    public static AtomicBoolean toggled = new AtomicBoolean(true);

    final static Pattern pattern = Pattern.compile("\\[AD]");

    public static Boolean check(String chat, CallbackInfo ci) {
        
        if (!toggled.get()) return false;

        
        if (!LeoneClient.onLeoneMC) return false;

        
        if (!(pattern.matcher(chat).find() || chat.contains(": /join"))) return false;

        
        ci.cancel();
        return true;
    }
}