package dev.alfxyz.leoneclient.mixin.staff;

import com.mojang.blaze3d.textures.GpuTextureView;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(BakedSheetGlyph.class)
public interface BakedSheetGlyphAccessor {
	@Accessor("textureView")
	GpuTextureView leone$textureView();

	@Accessor("u0")
	float leone$u0();

	@Accessor("u1")
	float leone$u1();

	@Accessor("v0")
	float leone$v0();

	@Accessor("v1")
	float leone$v1();

	@Accessor("left")
	float leone$left();

	@Accessor("right")
	float leone$right();

	@Accessor("up")
	float leone$up();

	@Accessor("down")
	float leone$down();
}
