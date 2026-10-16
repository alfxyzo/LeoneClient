package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** HUD Tailor: what the action bar and held item name are showing, to size their boxes in the editor. */
@Mixin(Hud.class)
public interface HudFieldsAccessor {
	@Accessor("overlayMessageString")
	@Nullable Component leone$overlayMessage();

	@Accessor("overlayMessageTime")
	int leone$overlayMessageTime();

	@Accessor("toolHighlightTimer")
	int leone$toolHighlightTimer();

	@Accessor("lastToolHighlight")
	ItemStack leone$lastToolHighlight();
}
