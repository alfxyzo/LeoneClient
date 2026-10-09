package dev.alfxyz.leoneclient.mixin;

import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.features.ChatHooks;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import dev.alfxyz.leoneclient.staffchat.StaffPlaceholder;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Sends every incoming chat line through the chat modules, which can hide or restyle it, and then
 * through Staff Chat, which stores staff lines as placeholders.
 */
@Mixin(ChatComponent.class)
public abstract class ChatComponentMixin {
	@Unique
	private boolean leoneclient$replaying;

	@Shadow
	private void addMessage(Component message, @Nullable MessageSignature signature, GuiMessageSource source, @Nullable GuiMessageTag tag) {
	}

	@Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/multiplayer/chat/GuiMessageSource;Lnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V",
		at = @At("HEAD"), cancellable = true)
	private void leoneclient$incoming(Component message, @Nullable MessageSignature signature, GuiMessageSource source, @Nullable GuiMessageTag tag, CallbackInfo ci) {
		if (leoneclient$replaying || source == GuiMessageSource.SYSTEM_CLIENT) return;
		Component out = ChatHooks.incoming(message, source);
		if (out != null) out = StaffChat.wrapIfStaff(out);
		if (out == message) return;
		ci.cancel();
		if (out == null) return;
		leoneclient$replaying = true;
		try {
			addMessage(out, signature, source, tag);
		} finally {
			leoneclient$replaying = false;
		}
	}

	/** Keeps the real text of staff lines in latest.log, as it would be without the placeholder. */
	@Inject(method = "logChatMessage", at = @At("HEAD"), cancellable = true)
	private void leoneclient$logStaff(GuiMessage message, CallbackInfo ci) {
		if (message.content() instanceof StaffPlaceholder placeholder) {
			String text = placeholder.real().getString().replace("\r", "\\r").replace("\n", "\\n");
			String logTag = message.tag() == null ? null : message.tag().logTag();
			if (logTag != null) LogUtils.getLogger().info("[{}] [CHAT] {}", logTag, text);
			else LogUtils.getLogger().info("[CHAT] {}", text);
			ci.cancel();
		}
	}
}
