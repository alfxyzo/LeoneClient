package dev.alfxyz.leoneclient.mixin.staff;

import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$GlyphInstance")
public interface GlyphInstanceAccessor {
	@Accessor("x")
	float leone$x();

	@Accessor("y")
	float leone$y();

	@Accessor("color")
	int leone$color();

	@Accessor("shadowColor")
	int leone$shadowColor();

	@Accessor("glyph")
	BakedSheetGlyph leone$glyph();

	@Accessor("style")
	Style leone$style();

	@Accessor("boldOffset")
	float leone$boldOffset();

	@Accessor("shadowOffset")
	float leone$shadowOffset();
}
