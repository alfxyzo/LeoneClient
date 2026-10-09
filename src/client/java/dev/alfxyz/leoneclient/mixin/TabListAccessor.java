package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The tab list's header and footer, where networks put their name, for recognising LeoneMC by any address. */
@Mixin(PlayerTabOverlay.class)
public interface TabListAccessor {
	@Accessor("header")
	@Nullable Component leone$header();

	@Accessor("footer")
	@Nullable Component leone$footer();
}
