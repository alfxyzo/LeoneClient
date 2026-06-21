package dev.alfxyz.leoneclient.utils;

import dev.alfxyz.leoneclient.LeoneClient;
import dev.alfxyz.leoneclient.api.Minehut;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.map.MapState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.network.packet.s2c.play.PlaySoundS2CPacket;
import net.minecraft.sound.SoundCategory;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;

import java.util.concurrent.atomic.AtomicBoolean;

public class BlockLobbyMapAds {
    public static String name = "block_lobby_map_ads";
    public static String description = "Removes the humungous map art that advertises things in lobby, including the actionbar and bossbar.";

    public static AtomicBoolean toggled = new AtomicBoolean(true);

    public static void block(ItemFrameEntity entity) {
        
        if (!toggled.get()) return;

        
        if (!LeoneClient.onLeoneMC) return;

        Boolean inLobby = Minehut.inLobby();
        if (inLobby == null || !inLobby) return;

        
        ItemStack stack = entity.getHeldItemStack();
        if (stack == null || stack.getItem() != Items.FILLED_MAP) return;
        
        entity.setHeldItemStack(ItemStack.EMPTY);
    }

    public static boolean checkActionbar(String actionbar, CallbackInfo ci) {
        
        if (!toggled.get() || !LeoneClient.onLeoneMC) return false;

        
        if (actionbar.contains("[Billboard]")) {
            ci.cancel();
        }

        return true;
    }

    public static boolean checkBossbar(String text) {
        
        if (!toggled.get()) return false;

        
        if (!LeoneClient.onLeoneMC) return false;

        if (text.contains("[Billboard]")) {
            return true;
        }

        return false;
    }

    public static boolean checkSound(PlaySoundS2CPacket packet) {
        if (!toggled.get()) return false;
        if (!LeoneClient.onLeoneMC) return false;

        Identifier soundId = Registries.SOUND_EVENT.getId(packet.getSound().value());
        if (soundId != null && soundId.getPath().contains("minehut.ad")) {
            return true; 
        }

        return false;
    }
}
