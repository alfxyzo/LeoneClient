package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import java.net.URL;
import java.util.UUID;
import net.minecraft.client.resources.server.DownloadedPackSource;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Tells Pack Prefetch which packs a server asks for, so it can fetch their next versions ahead of time. */
@Mixin(DownloadedPackSource.class)
public abstract class PackPushMixin {
	@Inject(method = "pushPack", at = @At("HEAD"))
	private void leoneclient$learnPack(UUID id, URL url, @Nullable String hash, CallbackInfo ci) {
		Modules.PACK_PREFETCH.onPackPushed(url, hash);
	}
}
