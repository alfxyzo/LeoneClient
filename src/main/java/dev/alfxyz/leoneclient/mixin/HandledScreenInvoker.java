package dev.alfxyz.leoneclient.mixin;

import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Screen.class)
public interface HandledScreenInvoker {
    @Accessor("children")
    List<Element> getChildren();

    @Accessor("drawables")
    List<Element> getDrawables();
}