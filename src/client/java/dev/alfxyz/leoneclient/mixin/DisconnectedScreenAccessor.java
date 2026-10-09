package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.network.DisconnectionDetails;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Why the connection ended, so Auto Reconnect can tell a restart from a kick. */
@Mixin(DisconnectedScreen.class)
public interface DisconnectedScreenAccessor {
	@Accessor("details")
	DisconnectionDetails leone$details();
}
