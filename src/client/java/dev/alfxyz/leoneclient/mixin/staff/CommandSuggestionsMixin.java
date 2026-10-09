package dev.alfxyz.leoneclient.mixin.staff;

import dev.alfxyz.leoneclient.staffchat.OverlayFrame;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Command suggestions and usage hints are drawn over the chat, so the protected window must not draw over them. */
@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
	@Shadow @Final private Screen screen;
	@Shadow @Final private List<FormattedCharSequence> commandUsage;
	@Shadow private int commandUsagePosition;
	@Shadow private int commandUsageWidth;
	@Shadow @Final private boolean anchorToBottom;
	@Shadow private CommandSuggestions.SuggestionsList suggestions;

	@Inject(method = "extractRenderState", at = @At("HEAD"), require = 0)
	private void leoneclient$occlude(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
		if (!(screen instanceof ChatScreen)) {
			return;
		}
		Matrix3x2fc pose = graphics.pose();
		if (suggestions != null) {
			Rect2i rect = ((SuggestionsListAccessor) suggestions).leone$rect();
			// One extra row above and below for the scroll markers.
			OverlayFrame.occlude(pose, rect.getX(), rect.getY() - 1, rect.getX() + rect.getWidth(), rect.getY() + rect.getHeight() + 1);
			return;
		}
		// Mirrors CommandSuggestions.extractUsage.
		for (int i = 0; i < commandUsage.size(); i++) {
			int y = anchorToBottom ? screen.height - 27 - 12 * i : 72 + 12 * i;
			OverlayFrame.occlude(pose, commandUsagePosition - 1, y, commandUsagePosition + commandUsageWidth + 1, y + 12);
		}
	}
}
