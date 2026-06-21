package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public abstract class BlockRenderManagerMixin<T extends Entity> {
    @Inject(method = "hasLabel", at = @At("HEAD"), cancellable = true)
    private void blockBillboardGlow(T entity, CallbackInfoReturnable<Boolean> cir) {
        
        if (entity.isGlowing()) {
            cir.setReturnValue(false);
        }
    }
} 