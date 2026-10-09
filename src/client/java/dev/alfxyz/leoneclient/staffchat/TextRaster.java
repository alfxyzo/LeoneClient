package dev.alfxyz.leoneclient.staffchat;

import dev.alfxyz.leoneclient.mixin.staff.BakedSheetGlyphAccessor;
import dev.alfxyz.leoneclient.mixin.staff.EffectInstanceAccessor;
import dev.alfxyz.leoneclient.mixin.staff.GlyphInstanceAccessor;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.font.TextRenderable;
import net.minecraft.client.gui.font.glyphs.BakedSheetGlyph;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2fc;

/**
 * Draws text into an image exactly as Minecraft's GUI text pipeline would: Minecraft lays the text out
 * (glyphs, colours, shadows, bold, italic, underline) and this class fills the same quads pixel by pixel,
 * sampling the same glyph textures with the same rules (nearest texel, alpha below 0.1 discarded,
 * normal alpha blending). The result is premultiplied, which is what a layered window expects.
 */
final class TextRaster {
	record Image(int[] pixels, int x, int y, int width, int height) {}

	/**
	 * A parallelogram in window pixels with an affine texture mapping: corner (x, y) has texture (u0, v0),
	 * and moving along edge a or edge b adds (ua, va) or (ub, vb).
	 */
	private record Quad(float x, float y, float ax, float ay, float bx, float by,
		float u0, float v0, float ua, float va, float ub, float vb, int color, GlyphAtlas.Copy texture) {}

	private final List<Quad> quads = new ArrayList<>();
	private float minX = Float.POSITIVE_INFINITY;
	private float minY = Float.POSITIVE_INFINITY;
	private float maxX = Float.NEGATIVE_INFINITY;
	private float maxY = Float.NEGATIVE_INFINITY;
	private boolean missingTexture;

	/** True if a glyph texture copy was not ready yet, so the image would be incomplete. */
	boolean missingTexture() {
		return missingTexture;
	}

	/**
	 * Adds text the way GuiGraphicsExtractor would draw it: laid out at (x, y), transformed by the GUI pose,
	 * then multiplied by {@code scale} to get window pixels.
	 */
	void addText(Font font, FormattedCharSequence text, Matrix3x2fc pose, float scale, int x, int y, int color, boolean shadow) {
		Font.PreparedText prepared = font.prepareText(text, x, y, color, shadow, false, 0);
		prepared.visit(new Font.GlyphVisitor() {
			@Override
			public void acceptGlyph(TextRenderable.Styled glyph) {
				addGlyph(glyph, pose, scale);
			}

			@Override
			public void acceptEffect(TextRenderable effect) {
				addEffect(effect, pose, scale);
			}
		});
	}

	// Mirrors BakedSheetGlyph.renderChar: shadow (and its bold copy) first, then the glyph (and its bold copy).
	private void addGlyph(TextRenderable.Styled renderable, Matrix3x2fc pose, float scale) {
		if (!(renderable instanceof GlyphInstanceAccessor glyph)) {
			return; // sprite and player-head glyphs are not drawn by the overlay
		}
		BakedSheetGlyph sheet = glyph.leone$glyph();
		BakedSheetGlyphAccessor sheetData = (BakedSheetGlyphAccessor) sheet;
		GlyphAtlas.Copy texture = GlyphAtlas.forGlyph(sheet, sheetData.leone$textureView().texture());
		if (texture == null) {
			missingTexture = true;
			return;
		}
		Style style = glyph.leone$style();
		boolean italic = style.isItalic();
		boolean bold = style.isBold();
		float x = glyph.leone$x();
		float y = glyph.leone$y();
		int shadowColor = glyph.leone$shadowColor();
		if (shadowColor != 0) {
			float offset = glyph.leone$shadowOffset();
			addGlyphQuad(sheetData, texture, pose, scale, italic, x + offset, y + offset, shadowColor, bold);
			if (bold) {
				addGlyphQuad(sheetData, texture, pose, scale, italic, x + glyph.leone$boldOffset() + offset, y + offset, shadowColor, true);
			}
		}
		addGlyphQuad(sheetData, texture, pose, scale, italic, x, y, glyph.leone$color(), bold);
		if (bold) {
			addGlyphQuad(sheetData, texture, pose, scale, italic, x + glyph.leone$boldOffset(), y, glyph.leone$color(), true);
		}
	}

	// Mirrors BakedSheetGlyph.render, including the italic shear and the 0.1 extra thickness for bold.
	private void addGlyphQuad(BakedSheetGlyphAccessor sheet, GlyphAtlas.Copy texture, Matrix3x2fc pose, float scale,
		boolean italic, float x, float y, int color, boolean bold) {
		float left = x + sheet.leone$left();
		float right = x + sheet.leone$right();
		float top = y + sheet.leone$up();
		float bottom = y + sheet.leone$down();
		float shearTop = italic ? 1.0F - 0.25F * sheet.leone$up() : 0.0F;
		float shearBottom = italic ? 1.0F - 0.25F * sheet.leone$down() : 0.0F;
		float extra = bold ? 0.1F : 0.0F;
		addQuad(pose, scale,
			left + shearTop - extra, top - extra,
			right + shearTop + extra, top - extra,
			left + shearBottom - extra, bottom + extra,
			sheet.leone$u0(), sheet.leone$v0(), sheet.leone$u1(), sheet.leone$v1(), color, texture);
	}

	// Mirrors BakedSheetGlyph.renderEffect and buildEffect (underline, strikethrough, background).
	private void addEffect(TextRenderable renderable, Matrix3x2fc pose, float scale) {
		if (!(renderable instanceof EffectInstanceAccessor effect)) {
			return;
		}
		BakedSheetGlyph sheet = effect.leone$glyph();
		BakedSheetGlyphAccessor sheetData = (BakedSheetGlyphAccessor) sheet;
		GlyphAtlas.Copy texture = GlyphAtlas.forGlyph(sheet, sheetData.leone$textureView().texture());
		if (texture == null) {
			missingTexture = true;
			return;
		}
		if (effect.leone$shadowColor() != 0) {
			addEffectQuad(effect, sheetData, texture, pose, scale, effect.leone$shadowOffset(), effect.leone$shadowColor());
		}
		addEffectQuad(effect, sheetData, texture, pose, scale, 0.0F, effect.leone$color());
	}

	private void addEffectQuad(EffectInstanceAccessor effect, BakedSheetGlyphAccessor sheet, GlyphAtlas.Copy texture,
		Matrix3x2fc pose, float scale, float offset, int color) {
		float x0 = effect.leone$x0() + offset;
		float x1 = effect.leone$x1() + offset;
		float y0 = effect.leone$y0() + offset;
		float y1 = effect.leone$y1() + offset;
		// buildEffect puts (u0, v0) at (x0, y1), (u1, v0) at (x0, y0) and (u0, v1) at (x1, y1).
		addQuad(pose, scale, x0, y1, x0, y0, x1, y1, sheet.leone$u0(), sheet.leone$v0(), sheet.leone$u1(), sheet.leone$v1(), color, texture);
	}

	private void addQuad(Matrix3x2fc pose, float scale, float ox, float oy, float ax, float ay, float bx, float by,
		float u0, float v0, float u1, float v1, int color, GlyphAtlas.Copy texture) {
		float px = (pose.m00() * ox + pose.m10() * oy + pose.m20()) * scale;
		float py = (pose.m01() * ox + pose.m11() * oy + pose.m21()) * scale;
		float pax = (pose.m00() * ax + pose.m10() * ay + pose.m20()) * scale;
		float pay = (pose.m01() * ax + pose.m11() * ay + pose.m21()) * scale;
		float pbx = (pose.m00() * bx + pose.m10() * by + pose.m20()) * scale;
		float pby = (pose.m01() * bx + pose.m11() * by + pose.m21()) * scale;
		addQuad(new Quad(px, py, pax - px, pay - py, pbx - px, pby - py, u0, v0, u1 - u0, 0.0F, 0.0F, v1 - v0, color, texture));
	}

	private void addQuad(Quad quad) {
		quads.add(quad);
		include(quad.x(), quad.y());
		include(quad.x() + quad.ax(), quad.y() + quad.ay());
		include(quad.x() + quad.bx(), quad.y() + quad.by());
		include(quad.x() + quad.ax() + quad.bx(), quad.y() + quad.ay() + quad.by());
	}

	private void include(float x, float y) {
		minX = Math.min(minX, x);
		minY = Math.min(minY, y);
		maxX = Math.max(maxX, x);
		maxY = Math.max(maxY, y);
	}

	/**
	 * Fills every quad into an image clipped to the window's client area. Pixels whose centres fall in an
	 * occluder (things Minecraft draws over the chat, given as x0, y0, x1, y1 in window pixels) are left clear.
	 */
	Image render(int clipWidth, int clipHeight, List<float[]> occluders) {
		int left = Math.max(0, (int) Math.floor(minX));
		int top = Math.max(0, (int) Math.floor(minY));
		int right = Math.min(clipWidth, (int) Math.ceil(maxX));
		int bottom = Math.min(clipHeight, (int) Math.ceil(maxY));
		if (quads.isEmpty() || right <= left || bottom <= top) {
			return null;
		}
		int width = right - left;
		int height = bottom - top;
		float[] buffer = new float[width * height * 4];
		for (Quad quad : quads) {
			fill(quad, buffer, left, top, width, height);
		}
		for (float[] rect : occluders) {
			clear(rect, buffer, left, top, width, height);
		}

		int[] pixels = new int[width * height];
		boolean empty = true;
		for (int i = 0; i < pixels.length; i++) {
			int a = channel(buffer[i * 4 + 3]);
			if (a == 0) {
				continue;
			}
			empty = false;
			pixels[i] = a << 24 | channel(buffer[i * 4]) << 16 | channel(buffer[i * 4 + 1]) << 8 | channel(buffer[i * 4 + 2]);
		}
		return empty ? null : new Image(pixels, left, top, width, height);
	}

	private static void fill(Quad q, float[] buffer, int left, int top, int width, int height) {
		float det = q.ax() * q.by() - q.ay() * q.bx();
		if (Math.abs(det) < 1e-6F) {
			return;
		}
		float x1 = q.x() + q.ax();
		float x2 = q.x() + q.bx();
		float x3 = x1 + q.bx();
		float y1 = q.y() + q.ay();
		float y2 = q.y() + q.by();
		float y3 = y1 + q.by();
		int startX = Math.max(left, (int) Math.floor(Math.min(Math.min(q.x(), x1), Math.min(x2, x3))));
		int endX = Math.min(left + width, (int) Math.ceil(Math.max(Math.max(q.x(), x1), Math.max(x2, x3))));
		int startY = Math.max(top, (int) Math.floor(Math.min(Math.min(q.y(), y1), Math.min(y2, y3))));
		int endY = Math.min(top + height, (int) Math.ceil(Math.max(Math.max(q.y(), y1), Math.max(y2, y3))));

		float vertexA = (q.color() >>> 24) / 255.0F;
		float vertexR = (q.color() >> 16 & 0xFF) / 255.0F;
		float vertexG = (q.color() >> 8 & 0xFF) / 255.0F;
		float vertexB = (q.color() & 0xFF) / 255.0F;
		GlyphAtlas.Copy texture = q.texture();
		byte[] data = texture.data;
		boolean gray = texture.bytesPerTexel == 1;

		for (int py = startY; py < endY; py++) {
			float dy = py + 0.5F - q.y();
			for (int px = startX; px < endX; px++) {
				float dx = px + 0.5F - q.x();
				float s = (dx * q.by() - dy * q.bx()) / det;
				float t = (q.ax() * dy - q.ay() * dx) / det;
				if (s < 0.0F || s >= 1.0F || t < 0.0F || t >= 1.0F) {
					continue;
				}
				float u = q.u0() + s * q.ua() + t * q.ub();
				float v = q.v0() + s * q.va() + t * q.vb();
				int tx = Math.floorMod((int) Math.floor(u * texture.width), texture.width);
				int ty = Math.floorMod((int) Math.floor(v * texture.height), texture.height);
				int index = (ty * texture.width + tx) * texture.bytesPerTexel;
				float tr;
				float tg;
				float tb;
				float ta;
				if (gray) {
					tr = tg = tb = ta = (data[index] & 0xFF) / 255.0F;
				} else {
					tr = (data[index] & 0xFF) / 255.0F;
					tg = (data[index + 1] & 0xFF) / 255.0F;
					tb = (data[index + 2] & 0xFF) / 255.0F;
					ta = (data[index + 3] & 0xFF) / 255.0F;
				}
				float a = ta * vertexA;
				if (a < 0.1F) {
					continue; // the text shader discards these
				}
				int pixel = (py - top) * width + (px - left);
				float keep = 1.0F - a;
				int o = pixel * 4;
				buffer[o] = tr * vertexR * a + buffer[o] * keep;
				buffer[o + 1] = tg * vertexG * a + buffer[o + 1] * keep;
				buffer[o + 2] = tb * vertexB * a + buffer[o + 2] * keep;
				buffer[o + 3] = a + buffer[o + 3] * keep;
			}
		}
	}

	private static void clear(float[] rect, float[] buffer, int left, int top, int width, int height) {
		int startX = Math.max(left, (int) Math.ceil(rect[0] - 0.5F));
		int endX = Math.min(left + width, (int) Math.ceil(rect[2] - 0.5F));
		int startY = Math.max(top, (int) Math.ceil(rect[1] - 0.5F));
		int endY = Math.min(top + height, (int) Math.ceil(rect[3] - 0.5F));
		for (int py = startY; py < endY; py++) {
			for (int px = startX; px < endX; px++) {
				int o = ((py - top) * width + (px - left)) * 4;
				buffer[o] = buffer[o + 1] = buffer[o + 2] = buffer[o + 3] = 0.0F;
			}
		}
	}

	private static int channel(float value) {
		return Math.max(0, Math.min(255, Math.round(value * 255.0F)));
	}
}
