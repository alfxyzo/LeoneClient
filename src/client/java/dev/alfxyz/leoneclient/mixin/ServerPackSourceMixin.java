package dev.alfxyz.leoneclient.mixin;

import dev.alfxyz.leoneclient.module.Modules;
import java.net.URL;
import java.util.UUID;
import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.client.resources.server.PackReloadConfig;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pack Prefetch's hooks into the game's server packs: it learns which packs a server asks for, and skips
 * the resource reload when LeoneMC asks for packs that are already loaded (or when leaving LeoneMC).
 */
@Mixin(DownloadedPackSource.class)
public abstract class ServerPackSourceMixin {
	@Inject(method = "pushPack", at = @At("HEAD"))
	private void leoneclient$learnPack(UUID id, URL url, @Nullable String hash, CallbackInfo ci) {
		Modules.PACK_PREFETCH.onPackPushed(url, hash);
	}

	@Inject(method = "startReload", at = @At("HEAD"), cancellable = true)
	private void leoneclient$skipSameReload(PackReloadConfig.Callbacks callbacks, CallbackInfo ci) {
		if (Modules.PACK_PREFETCH.skipReload(callbacks)) ci.cancel();
	}
}
