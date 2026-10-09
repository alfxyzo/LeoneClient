package dev.alfxyz.leoneclient.mixin.staff;

import dev.alfxyz.leoneclient.staffchat.OverlayFrame;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Draws the chat while it is open. Hidden staff lines are remembered for the protected window. */
@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$DrawingFocusedGraphicsAccess")
public abstract class ChatDrawingFocusedMixin {
	@Shadow
	private ActiveTextCollector.Parameters parameters;

	@Inject(method = "handleMessage", at = @At("HEAD"))
	private void leoneclient$collect(int y, float opacity, FormattedCharSequence text, CallbackInfoReturnable<Boolean> cir) {
		OverlayFrame.collectLine(text, parameters.pose(), y, opacity);
	}
}
