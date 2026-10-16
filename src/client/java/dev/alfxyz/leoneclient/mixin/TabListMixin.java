package dev.alfxyz.leoneclient.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** HUD Tailor: the tab list without the server's header or footer. */
@Mixin(PlayerTabOverlay.class)
public abstract class TabListMixin {
	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;header:Lnet/minecraft/network/chat/Component;"))
	private @Nullable Component leoneclient$header(@Nullable Component header) {
		return Modules.HUD_TAILOR.active() && Modules.HUD_TAILOR.tabHeader.get() ? null : header;
	}

	@ModifyExpressionValue(method = "extractRenderState", at = @At(value = "FIELD",
		target = "Lnet/minecraft/client/gui/components/PlayerTabOverlay;footer:Lnet/minecraft/network/chat/Component;"))
	private @Nullable Component leoneclient$footer(@Nullable Component footer) {
		return Modules.HUD_TAILOR.active() && Modules.HUD_TAILOR.tabFooter.get() ? null : footer;
	}
}
