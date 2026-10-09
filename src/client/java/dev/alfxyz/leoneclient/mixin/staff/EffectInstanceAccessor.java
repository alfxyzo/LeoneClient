package dev.alfxyz.leoneclient.mixin.staff;

import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.font.glyphs.BakedSheetGlyph$EffectInstance")
public interface EffectInstanceAccessor {
	@Accessor("glyph")
	BakedSheetGlyph leone$glyph();

	@Accessor("x0")
	float leone$x0();

	@Accessor("y0")
	float leone$y0();

	@Accessor("x1")
	float leone$x1();

	@Accessor("y1")
	float leone$y1();

	@Accessor("color")
	int leone$color();

	@Accessor("shadowColor")
	int leone$shadowColor();

	@Accessor("shadowOffset")
	float leone$shadowOffset();
}
