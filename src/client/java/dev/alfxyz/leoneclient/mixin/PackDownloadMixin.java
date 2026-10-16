package dev.alfxyz.leoneclient.mixin;

import com.google.common.hash.HashCode;
import com.google.common.hash.HashFunction;
import dev.alfxyz.leoneclient.module.Modules;
import java.net.Proxy;
import java.net.URL;
import java.nio.file.Path;
import java.util.Map;
import net.minecraft.util.HttpUtil;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Before the game downloads a server pack it looks for a copy with the right hash in its download folder.
 * Pack Prefetch puts its own copy there first, so the game finds it and skips the download.
 */
@Mixin(HttpUtil.class)
public abstract class PackDownloadMixin {
	@Inject(method = "downloadFile", at = @At("HEAD"))
	private static void leoneclient$supplyPrefetched(Path targetDir, URL url, Map<String, String> headers, HashFunction hashFunction,
		@Nullable HashCode requestedHash, int maxSize, Proxy proxy, HttpUtil.DownloadProgressListener listener,
		CallbackInfoReturnable<Path> cir) {
		Modules.PACK_PREFETCH.supply(targetDir, requestedHash);
	}
}
