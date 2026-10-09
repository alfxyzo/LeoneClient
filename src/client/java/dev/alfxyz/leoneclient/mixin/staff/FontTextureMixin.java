package dev.alfxyz.leoneclient.mixin.staff;

import com.mojang.blaze3d.font.GlyphBitmap;
import com.mojang.blaze3d.font.GlyphInfo;
import dev.alfxyz.leoneclient.staffchat.GlyphAtlas;
import net.minecraft.client.gui.font.FontTexture;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FontTexture.class)
public abstract class FontTextureMixin {
	/** Notes each new glyph, so the protected window knows when its copy of this texture is out of date. */
	@Inject(method = "add", at = @At("RETURN"))
	private void leoneclient$glyphAdded(GlyphInfo info, GlyphBitmap bitmap, CallbackInfoReturnable<BakedSheetGlyph> cir) {
		if (cir.getReturnValue() != null) {
			GlyphAtlas.glyphAdded(cir.getReturnValue());
		}
	}
}
