package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.multiplayer.ClientPacketListener;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Filters outgoing chat for Anti-Mute. */
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
}
