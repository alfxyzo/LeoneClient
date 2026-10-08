package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundOpenBookPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Filters outgoing chat for Anti-Mute and lets Book Blocker refuse server-opened books. */
@Mixin(ClientPacketListener.class)
public abstract class ClientPacketListenerMixin {
	@ModifyVariable(method = "sendChat", at = @At("HEAD"), argsOnly = true)
	private String leoneclient$filterChat(String message) {
		return Modules.ANTI_MUTE.chat(message);
	}

	@ModifyVariable(method = "sendCommand", at = @At("HEAD"), argsOnly = true)
	private String leoneclient$filterCommand(String command) {
		return Modules.ANTI_MUTE.command(command);
	}

	@Inject(method = "handleOpenBook", at = @At("HEAD"), cancellable = true)
	private void leoneclient$blockBook(ClientboundOpenBookPacket packet, CallbackInfo ci) {
		if (Modules.BOOK_BLOCKER.blocks()) ci.cancel();
	}
}
