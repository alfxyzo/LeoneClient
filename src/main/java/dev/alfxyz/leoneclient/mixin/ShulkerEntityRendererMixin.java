package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.render.entity.ShulkerEntityRenderer;
import net.minecraft.entity.mob.ShulkerEntity;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.render.VertexConsumerProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShulkerEntityRenderer.class)
public class ShulkerEntityRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(ShulkerEntity entity, float entityYaw, float tickDelta, MatrixStack matrices, VertexConsumerProvider vertexConsumers, int light, CallbackInfo ci) {
        System.out.println("[LeoneClient] ShulkerEntityRendererMixin: onRender called");
        ci.cancel();
    }
} 