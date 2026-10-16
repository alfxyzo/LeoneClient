package dev.alfxyz.leoneclient.mixin.ads;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ad Block: stops drawing a hidden advert billboard. */
@Mixin(EntityRenderDispatcher.class)
public abstract class AdRenderMixin {
	@Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
	private void leoneclient$hideAds(Entity entity, Frustum frustum, double camX, double camY, double camZ, CallbackInfoReturnable<Boolean> cir) {
		if (Modules.AD_BLOCK.hides(entity)) cir.setReturnValue(false);
	}
}
