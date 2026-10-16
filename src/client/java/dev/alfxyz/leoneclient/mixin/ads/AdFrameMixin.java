package dev.alfxyz.leoneclient.mixin.ads;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.world.entity.decoration.BlockAttachedEntity;
import net.minecraft.world.entity.decoration.ItemFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ad Block: clicks pass through the item frames of a hidden advert billboard. */
@Mixin(BlockAttachedEntity.class)
public abstract class AdFrameMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void leoneclient$clickThrough(CallbackInfoReturnable<Boolean> cir) {
		if ((Object) this instanceof ItemFrame frame && frame.level().isClientSide() && Modules.AD_BLOCK.hides(frame)) cir.setReturnValue(false);
	}
}
