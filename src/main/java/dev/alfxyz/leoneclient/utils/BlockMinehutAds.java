package dev.alfxyz.leoneclient.utils;

import dev.alfxyz.leoneclient.LeoneClient;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.regex.Pattern;
import java.util.concurrent.atomic.AtomicBoolean;


public class BlockMinehutAds {
    public static String name = "block_minehut_ads";
    public static String description = "Blocks Minehut ads.";
    public static AtomicBoolean toggled = new AtomicBoolean(true);
    final static Pattern pattern = Pattern.compile("^(\\n\\n|/n/n)\\[Minehut].*(\\n\\n|/n/n)$");

    public static Boolean checkChat(String chat, CallbackInfo ci) {

        if (!toggled.get()) return false;

        
        if (!LeoneClient.onLeoneMC) return false;

        if (pattern.matcher(chat).find()) {
            LeoneClient.log.info("Blocked: {}", chat);
            ci.cancel();
        }

        return pattern.matcher(chat).find();
    }
}