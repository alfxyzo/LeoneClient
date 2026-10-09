package dev.alfxyz.leoneclient.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.alfxyz.leoneclient.module.Modules;
import java.util.function.Predicate;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Chat Tabs: every message is still kept (and logged) as usual, but only the ones in the chosen tab are
 * drawn. Vanilla's own filter cannot be used, as it throws away what it does not show.
 */
@Mixin(ChatComponent.class)
public abstract class ChatTabsMixin {
	/** A new message: sorted into tabs, counted as unread where it is not shown, drawn if it is. */
	@WrapOperation(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;addMessageToDisplayQueue(Lnet/minecraft/client/multiplayer/chat/GuiMessage;)V"))
	private void leoneclient$newMessage(ChatComponent chat, GuiMessage message, Operation<Void> original) {
		if (Modules.CHAT_TABS.arrived(message)) original.call(chat, message);
	}

	/** Rebuilding the lines (after a tab change, a resize or a removed line): only the chosen tab's messages. */
	@WrapOperation(method = "refreshTrimmedMessages",
		at = @At(value = "INVOKE", target = "Ljava/util/function/Predicate;test(Ljava/lang/Object;)Z"))
	private boolean leoneclient$inTab(Predicate<Object> filter, Object message, Operation<Boolean> original) {
		return original.call(filter, message) && Modules.CHAT_TABS.shows((GuiMessage) message);
	}
}
