package dev.alfxyz.leoneclient.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientCommonPacketListenerImpl;
import net.minecraft.network.protocol.common.ClientboundTransferPacket;
import org.spongepowered.asm.mixin.Mixin;

/**
 * LeoneMC moves players between its proxies with transfer packets (for example on /hub). Some mods
 * open a confirmation screen from the network thread while handling one, which crashes the game with
 * "Rendersystem called from wrong thread". Handling the whole packet on the main thread, where vanilla
 * would end up anyway, keeps that from happening.
 */
@Mixin(ClientCommonPacketListenerImpl.class)
public abstract class TransferThreadMixin {
	@WrapMethod(method = "handleTransfer")
	private void leoneclient$onMainThread(ClientboundTransferPacket packet, Operation<Void> original) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.isSameThread()) {
			original.call(packet);
		} else {
			mc.execute(() -> original.call(packet));
		}
	}
}
