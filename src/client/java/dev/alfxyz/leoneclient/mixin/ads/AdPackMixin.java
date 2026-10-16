package dev.alfxyz.leoneclient.mixin.ads;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.common.ClientboundResourcePackPushPacket;
import net.minecraft.network.protocol.common.ServerboundResourcePackPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ad Block: turns down Minehut's ads resource pack, answering the server as the game does when a player
 * declines a pack, so it is never downloaded or loaded.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class AdPackMixin {
	@Shadow
	@Final
	protected Connection connection;

	@Inject(method = "handleResourcePackPush", at = @At("HEAD"), cancellable = true)
	private void leoneclient$declineAdPack(ClientboundResourcePackPushPacket packet, CallbackInfo ci) {
		if (!Modules.AD_BLOCK.blocksPack(packet.url())) return;
		connection.send(new ServerboundResourcePackPacket(packet.id(), ServerboundResourcePackPacket.Action.DECLINED));
		Modules.AD_BLOCK.onPackDeclined(packet.url());
		ci.cancel();
	}
}
