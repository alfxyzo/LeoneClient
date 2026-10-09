package dev.alfxyz.leoneclient.render;

import java.nio.file.Path;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * Draws text in design units through a {@link Canvas}. Uses Segoe UI from the
 * system font folder when available, otherwise (or when switched) the vanilla
 * Minecraft font.
 */
public final class TextRenderer {
	public enum Weight { REGULAR, SEMIBOLD }

	/** Font size in design px, weight and letter spacing in em. */
	public record Style(float size, Weight weight, float tracking) {
		public static Style of(float size, Weight weight) {
			return new Style(size, weight, 0);
		}
	}

	private final Atlas atlas;
	private @Nullable FontFace regular, semibold;
	private boolean vanilla;
	private @Nullable GuiGraphicsExtractor graphics;

	public TextRenderer(Atlas atlas) {
		this.atlas = atlas;
	}

	public void load() {
		Path fonts = Path.of(System.getenv().getOrDefault("WINDIR", "C:\\Windows"), "Fonts");
		regular = FontFace.load(fonts.resolve("segoeui.ttf"));
		semibold = FontFace.load(fonts.resolve("seguisb.ttf"));
		if (semibold == null) semibold = regular;
	}

	public boolean hasSystemFont() {
		return regular != null;
	}

	public boolean vanilla() {
		return vanilla || regular == null;
	}

	public void setVanilla(boolean vanilla) {
		this.vanilla = vanilla;
	}

	public void setGraphics(@Nullable GuiGraphicsExtractor graphics) {
		this.graphics = graphics;
	}

	private FontFace face(Weight w) {
		return w == Weight.SEMIBOLD && semibold != null ? semibold : regular;
	}

	private static Font mcFont() {
		return Minecraft.getInstance().font;
	}

	/** Vanilla glyphs are 7 units tall; scale them so cap height matches ~0.7em. */
	private static float vanillaScale(Style st) {
		return st.size() * 0.7f / 7f;
	}

	/** Distance from the top of a CSS line box (line-height: normal) to the baseline, in design px. */
	public float ascent(Style st) {
		if (vanilla()) return st.size() * 1.0f;
		return face(st.weight()).ascent * st.size();
	}

	/** Height of a CSS line box with line-height: normal, in design px. */
	public float lineHeight(Style st) {
		if (vanilla()) return st.size() * 1.3f;
		FontFace f = face(st.weight());
		return (f.ascent + f.descent) * st.size();
	}

	/** Baseline y that vertically centres a line box on {@code centreY}. */
	public float baselineFor(Style st, float centreY) {
		return centreY - lineHeight(st) / 2 + ascent(st);
	}

	public float width(String s, Style st) {
		if (s.isEmpty()) return 0;
		if (vanilla()) return mcFont().width(s) * vanillaScale(st) + st.tracking() * st.size() * s.length();
		FontFace f = face(st.weight());
		float w = 0;
		int prev = -1;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (prev >= 0) w += f.kern(prev, cp);
			w += f.advance(cp) + st.tracking();
			prev = cp;
		}
		return w * st.size();
	}

	/** Shortens {@code s} with an ellipsis so it fits within {@code maxWidth}. */
	public String fit(String s, Style st, float maxWidth) {
		if (width(s, st) <= maxWidth) return s;
		String ell = "\u2026";
		int end = s.length();
		while (end > 0 && width(s.substring(0, end) + ell, st) > maxWidth) end--;
		return s.substring(0, end).stripTrailing() + ell;
	}

	public void draw(Canvas cv, String s, float x, float baseline, Style st, int color) {
		if (s.isEmpty() || (cv.withAlpha(color) >>> 24) == 0) return;
		if (vanilla()) {
			drawVanilla(cv, s, x, baseline, st, color);
			return;
		}
		FontFace f = face(st.weight());
		float devPerUnit = 1f / cv.px();
		float truePx = st.size() * devPerUnit;
		int sizeQ = Math.max(4, Math.round(truePx * 4));
		float rasterPx = sizeQ / 4f;
		float k = st.size() / rasterPx; // design units per raster pixel
		boolean snap = cv.axisAligned();
		float gs = cv.guiScale();
		float pen = 0;
		int prev = -1;
		for (int i = 0; i < s.length(); ) {
			int cp = s.codePointAt(i);
			i += Character.charCount(cp);
			if (prev >= 0) pen += f.kern(prev, cp) * st.size();
			if (snap) {
				// the pen position keeps its fraction of a device pixel: the glyph is rasterized
				// at the nearest of FontFace.PHASES offsets instead of being rounded to the pixel
				float ox = x + pen;
				float dx = cv.tx(ox, baseline) * gs, dy = cv.ty(ox, baseline) * gs;
				int ix = (int) Math.floor(dx);
				int phase = Math.round((dx - ix) * FontFace.PHASES);
				if (phase == FontFace.PHASES) {
					ix++;
					phase = 0;
				}
				FontFace.Glyph g = f.glyph(atlas, cp, sizeQ, phase);
				if (g.region() != null && g.w() > 0) {
					Atlas.Region r = g.region();
					float scale = truePx / rasterPx;
					float gx = (ix + g.left() * scale) / gs, gy = (Math.round(dy) - g.top() * scale) / gs;
					cv.texQuadGui(gx, gy, gx + g.w() * scale / gs, gy + g.h() * scale / gs, r.u0(), r.v0(), r.u1(), r.v1(), color);
				}
			} else {
				FontFace.Glyph g = f.glyph(atlas, cp, sizeQ, 0);
				if (g.region() != null && g.w() > 0) {
					Atlas.Region r = g.region();
					float lx = x + pen + g.left() * k, ly = baseline - g.top() * k;
					cv.texQuad(lx, ly, lx + g.w() * k, ly + g.h() * k, r.u0(), r.v0(), r.u1(), r.v1(), color);
				}
			}
			pen += (f.advance(cp) + st.tracking()) * st.size();
			prev = cp;
		}
	}

	private void drawVanilla(Canvas cv, String s, float x, float baseline, Style st, int color) {
		GuiGraphicsExtractor g = graphics;
		if (g == null) return;
		cv.flush(g);
		float sc = vanillaScale(st);
		float[] m = cv.matrix();
		var pose = g.pose();
		pose.pushMatrix();
		pose.set(new Matrix3x2f(m[0], m[1], m[2], m[3], m[4], m[5]));
		pose.translate(x, baseline - 7 * sc);
		pose.scale(sc, sc);
		float extra = st.tracking() * st.size() / sc;
		int col = cv.withAlpha(color);
		if (extra == 0) {
			g.text(mcFont(), s, 0, 0, col, false);
		} else {
			float pen = 0;
			for (int i = 0; i < s.length(); i++) {
				String ch = String.valueOf(s.charAt(i));
				pose.pushMatrix();
				pose.translate(pen, 0);
				g.text(mcFont(), ch, 0, 0, col, false);
				pose.popMatrix();
				pen += mcFont().width(ch) + extra;
			}
		}
		pose.popMatrix();
	}

	public void close() {
		if (regular != null) regular.close();
		if (semibold != null && semibold != regular) semibold.close();
		regular = semibold = null;
	}
}
