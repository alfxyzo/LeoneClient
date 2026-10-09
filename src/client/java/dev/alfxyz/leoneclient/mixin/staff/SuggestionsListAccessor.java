package dev.alfxyz.leoneclient.mixin.staff;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(CommandSuggestions.SuggestionsList.class)
public interface SuggestionsListAccessor {
	@Accessor("rect")
	Rect2i leone$rect();
}
