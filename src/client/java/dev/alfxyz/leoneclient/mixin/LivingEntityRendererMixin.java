package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Score Sight: how far the line under a player's name shows. */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin {
	private static final String NAME_TAGS =
		"extractNameTags(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V";

	@ModifyArg(method = NAME_TAGS, index = 4, at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/renderer/entity/EntityRenderer;extractNameTags(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;FDD)V"))
	private double leoneclient$belowNameDistance(Entity entity, EntityRenderState state, float partialTicks, double nameTagDistance, double belowNameDistance) {
		return Modules.SCORE_SIGHT.belowNameDistance(entity, belowNameDistance);
	}

	@Inject(method = NAME_TAGS, at = @At("TAIL"))
	private void leoneclient$afterNameTags(LivingEntity entity, LivingEntityRenderState state, float partialTicks, CallbackInfo ci) {
		Modules.SCORE_SIGHT.afterNameTags(entity, state);
	}
}
