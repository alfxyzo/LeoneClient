package dev.alfxyz.leoneclient.mixin.ads;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.world.entity.Interaction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Ad Block: clicks pass through the clickable boxes laid over a hidden advert billboard. */
@Mixin(Interaction.class)
public abstract class AdInteractionMixin {
	@Inject(method = "isPickable", at = @At("HEAD"), cancellable = true)
	private void leoneclient$clickThrough(CallbackInfoReturnable<Boolean> cir) {
		Interaction self = (Interaction) (Object) this;
		if (self.level().isClientSide() && Modules.AD_BLOCK.hides(self)) cir.setReturnValue(false);
	}
}
