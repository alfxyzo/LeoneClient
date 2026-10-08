package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.ui.LeoneScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets the menu animate the vanilla menu blur radius. */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
	@Shadow
	public abstract GameRenderState gameRenderState();

	@Inject(method = "extractOptions", at = @At("TAIL"))
	private void leoneclient$animateBlur(CallbackInfo ci) {
		int radius = LeoneScreen.blurRadiusOverride();
		if (radius >= 0) this.gameRenderState().optionsRenderState.menuBackgroundBlurriness = radius;
	}
}
