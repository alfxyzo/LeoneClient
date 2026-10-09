package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.extract.LevelExtractor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * World drawings that follow players smoothly: added each frame where the game gathers its own debug
 * drawings, so they are interpolated like the player models and hidden behind blocks as usual.
 */
@Mixin(LevelExtractor.class)
public abstract class LevelExtractorMixin {
	@Inject(method = "extract", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/debug/DebugRenderer;emitGizmos(Lnet/minecraft/client/renderer/culling/Frustum;DDDF)V", shift = At.Shift.AFTER))
	private void leoneclient$gizmos(DeltaTracker deltaTracker, Camera camera, float partialTick, CallbackInfo ci) {
		Modules.WEB_ESCAPE.emit(deltaTracker.getGameTimeDeltaPartialTick(false));
	}
}
