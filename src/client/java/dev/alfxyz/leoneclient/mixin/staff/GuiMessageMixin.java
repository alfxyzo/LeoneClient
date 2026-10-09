package dev.alfxyz.leoneclient.mixin.staff;

import dev.alfxyz.leoneclient.staffchat.StaffLine;
import dev.alfxyz.leoneclient.staffchat.StaffPlaceholder;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GuiMessage.class)
public abstract class GuiMessageMixin {
	/** Splits a placeholder exactly as the real message would be split, one hideable line per wrapped line. */
	@Inject(method = "splitLines", at = @At("HEAD"), cancellable = true)
	private void leoneclient$split(Font font, int width, CallbackInfoReturnable<List<FormattedCharSequence>> cir) {
		GuiMessage self = (GuiMessage) (Object) this;
		if (self.content() instanceof StaffPlaceholder placeholder) {
			GuiMessage real = new GuiMessage(self.addedTime(), placeholder.real(), self.signature(), self.source(), self.tag());
			cir.setReturnValue(StaffLine.wrapAll(real.splitLines(font, width)));
		}
	}
}
