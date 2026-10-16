package dev.alfxyz.leoneclient.mixin.ads;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundMapItemDataPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ad Block: watches how often maps are redrawn, to spot advert videos, and skips redrawing them. */
@Mixin(ClientPacketListener.class)
public abstract class AdMapMixin {
	@Inject(method = "handleMapItemData", cancellable = true, at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/network/PacketProcessor;)V"))
	private void leoneclient$watchMaps(ClientboundMapItemDataPacket packet, CallbackInfo ci) {
		if (Modules.AD_BLOCK.onMapData(packet.mapId(), packet.colorPatch().isPresent())) ci.cancel();
	}
}
