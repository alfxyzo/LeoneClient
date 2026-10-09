package dev.alfxyz.leoneclient.mixin.staff;

import dev.alfxyz.leoneclient.staffchat.OverlayFrame;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.tooltip.TooltipRenderUtil;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Tooltips are drawn over the chat, so the protected window must not draw over them. The background
 * extends 3 pixels of padding plus a 1 pixel border around the text.
 */
@Mixin(TooltipRenderUtil.class)
public abstract class TooltipRenderUtilMixin {
	@Inject(method = "extractTooltipBackground", at = @At("HEAD"), require = 0)
	private static void leoneclient$occlude(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Identifier style, CallbackInfo ci) {
		OverlayFrame.occlude(graphics.pose(), x - 4, y - 4, x + width + 4, y + height + 4);
	}
}
