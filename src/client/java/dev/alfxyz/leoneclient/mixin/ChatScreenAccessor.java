package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** What is being typed in chat, so the note about where it goes can step aside for commands. */
@Mixin(ChatScreen.class)
public interface ChatScreenAccessor {
	@Accessor("input")
	EditBox leone$input();
}
