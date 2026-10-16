package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.resources.server.DownloadedPackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets Pack Prefetch unload server packs it kept loaded after leaving LeoneMC. */
@Mixin(DownloadedPackSource.class)
public interface DownloadedPackSourceAccessor {
	@Accessor("packSource")
	void leoneclient$setPackSource(RepositorySource source);
}
