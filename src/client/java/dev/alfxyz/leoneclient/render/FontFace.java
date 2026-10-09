package dev.alfxyz.leoneclient.render;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.client.gui.font.providers.FreeTypeUtil;
import org.jspecify.annotations.Nullable;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.util.freetype.FT_Bitmap;
import org.lwjgl.util.freetype.FT_Face;
import org.lwjgl.util.freetype.FT_GlyphSlot;
import org.lwjgl.util.freetype.FT_Vector;
import org.lwjgl.util.freetype.FreeType;
import org.slf4j.Logger;

/**
 * A TrueType face loaded through FreeType, rasterized on demand at exact
 * device-pixel sizes into the {@link Atlas}.
 */
public final class FontFace {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final int LOAD_NO_SCALE = 1;
	private static final int KERNING_UNSCALED = 2;
	private static final int LOAD_TARGET_LIGHT = 1 << 16;
	/** Coverage exponent; below 1 thickens light-on-dark text slightly, like DirectWrite. */
	public static float gamma = 0.82f;

	public record Glyph(float left, float top, int w, int h, Atlas.@Nullable Region region) {
	}

	private final ByteBuffer data;
	private final FT_Face face;
	private final float unitsPerEm;
	public final float ascent, descent;
	private final boolean kerning;
	private final Int2FloatOpenHashMap advances = new Int2FloatOpenHashMap();
	private final Long2FloatOpenHashMap kerns = new Long2FloatOpenHashMap();
	private final Int2ObjectOpenHashMap<Int2ObjectOpenHashMap<Glyph>> sizes = new Int2ObjectOpenHashMap<>();
	/** Sizes (quarter pixels) that have glyphs made, at any phase. */
	private final IntOpenHashSet madeSizes = new IntOpenHashSet();
	private final SizeChooser sizeChooser = new SizeChooser();
	private int atlasGeneration = -1;

	private FontFace(ByteBuffer data, FT_Face face) {
		this.data = data;
		this.face = face;
		this.unitsPerEm = face.units_per_EM();
		this.ascent = face.ascender() / unitsPerEm;
		this.descent = -face.descender() / unitsPerEm;
		this.kerning = FreeType.FT_HAS_KERNING(face);
	}

	public static @Nullable FontFace load(Path path) {
		if (!Files.isRegularFile(path)) return null;
		ByteBuffer buf = null;
		try {
			byte[] bytes = Files.readAllBytes(path);
			buf = MemoryUtil.memAlloc(bytes.length);
			buf.put(bytes).flip();
			synchronized (FreeTypeUtil.LIBRARY_LOCK) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					PointerBuffer ptr = stack.mallocPointer(1);
					int err = FreeType.FT_New_Memory_Face(FreeTypeUtil.getLibrary(), buf, 0L, ptr);
					if (err != 0) throw new IllegalStateException("FT_New_Memory_Face error " + err);
					FT_Face face = FT_Face.create(ptr.get());
					FreeType.FT_Select_Charmap(face, FreeType.FT_ENCODING_UNICODE);
					return new FontFace(buf, face);
				}
			}
		} catch (Exception e) {
			LOGGER.warn("Leone Client: could not load font {}", path, e);
			if (buf != null) MemoryUtil.memFree(buf);
			return null;
		}
	}

	/** Advance of a code point, in em. */
	public float advance(int cp) {
		if (advances.containsKey(cp)) return advances.get(cp);
		float adv = 0.5f;
		synchronized (FreeTypeUtil.LIBRARY_LOCK) {
			if (FreeType.FT_Load_Char(face, cp, LOAD_NO_SCALE) == 0) {
				FT_GlyphSlot slot = face.glyph();
				if (slot != null) adv = slot.advance().x() / unitsPerEm;
			}
		}
		advances.put(cp, adv);
		return adv;
	}

	/** Kerning between two code points, in em. */
	public float kern(int left, int right) {
		if (!kerning) return 0;
		long key = ((long) left << 32) | (right & 0xFFFFFFFFL);
		if (kerns.containsKey(key)) return kerns.get(key);
		float k = 0;
		synchronized (FreeTypeUtil.LIBRARY_LOCK) {
			int gl = FreeType.FT_Get_Char_Index(face, left), gr = FreeType.FT_Get_Char_Index(face, right);
			if (gl != 0 && gr != 0) {
				try (MemoryStack stack = MemoryStack.stackPush()) {
					FT_Vector v = FT_Vector.malloc(stack);
					if (FreeType.FT_Get_Kerning(face, gl, gr, KERNING_UNSCALED, v) == 0) k = v.x() / unitsPerEm;
				}
			}
		}
		kerns.put(key, k);
		return k;
	}

	/** Horizontal sub-pixel positions each glyph is rasterized at, so spacing stays even. */
	public static final int PHASES = 3;

	/** Glyph bitmap at {@code sizeQ / 4} pixels per em, shifted right by {@code phase / PHASES} of a pixel. */
	/** The size, in quarter pixels, to rasterize text wanted at {@code sizeQ} (see {@link SizeChooser}). */
	public int rasterSize(Atlas atlas, int sizeQ) {
		checkGeneration(atlas);
		return sizeChooser.choose(sizeQ, madeSizes, madeSizes.contains(sizeQ), 0.8f, 1.25f);
	}

	private void checkGeneration(Atlas atlas) {
		if (atlasGeneration != atlas.generation()) {
			sizes.clear();
			madeSizes.clear();
			atlasGeneration = atlas.generation();
		}
	}

	public Glyph glyph(Atlas atlas, int cp, int sizeQ, int phase) {
		checkGeneration(atlas);
		madeSizes.add(sizeQ);
		int key = sizeQ * PHASES + phase;
		Int2ObjectOpenHashMap<Glyph> map = sizes.computeIfAbsent(key, k -> new Int2ObjectOpenHashMap<>());
		Glyph g = map.get(cp);
		if (g != null) return g;
		g = rasterize(atlas, cp, sizeQ / 4f, phase);
		// atlas full: it is wiped before the next frame, so leave this glyph out for one frame without remembering it
		if (g == null) return new Glyph(0, 0, 0, 0, null);
		map.put(cp, g);
		return g;
	}

	private @Nullable Glyph rasterize(Atlas atlas, int cp, float px, int phase) {
		synchronized (FreeTypeUtil.LIBRARY_LOCK) {
			FreeType.FT_Set_Char_Size(face, 0, Math.round(px * 64), 72, 72);
			// light hinting only snaps vertically, so the outline can be shifted by a fraction of a pixel
			if (FreeType.FT_Load_Char(face, cp, FreeType.FT_LOAD_DEFAULT | LOAD_TARGET_LIGHT) != 0) {
				return new Glyph(0, 0, 0, 0, null);
			}
			FT_GlyphSlot slot = face.glyph();
			if (slot == null) return new Glyph(0, 0, 0, 0, null);
			if (slot.format() == FreeType.FT_GLYPH_FORMAT_OUTLINE && phase != 0) {
				FreeType.FT_Outline_Translate(slot.outline(), Math.round(64f * phase / PHASES), 0);
			}
			if (FreeType.FT_Render_Glyph(slot, FreeType.FT_RENDER_MODE_LIGHT) != 0) return new Glyph(0, 0, 0, 0, null);
			FT_Bitmap bmp = slot.bitmap();
			int w = bmp.width(), h = bmp.rows(), pitch = bmp.pitch();
			if (w == 0 || h == 0) return new Glyph(0, 0, 0, 0, null);
			ByteBuffer src = bmp.buffer(Math.abs(pitch) * h);
			ByteBuffer rgba = MemoryUtil.memAlloc(w * h * 4);
			try {
				for (int y = 0; y < h; y++) {
					int row = pitch >= 0 ? y * pitch : (h - 1 - y) * -pitch;
					for (int x = 0; x < w; x++) {
						float cov = (src.get(row + x) & 0xFF) / 255f;
						int a = Math.round((float) Math.pow(cov, gamma) * 255);
						int o = (y * w + x) * 4;
						rgba.put(o, (byte) 255).put(o + 1, (byte) 255).put(o + 2, (byte) 255).put(o + 3, (byte) a);
					}
				}
				Atlas.Region region = atlas.add(w, h, rgba);
				if (region == null) return null;
				return new Glyph(slot.bitmap_left(), slot.bitmap_top(), w, h, region);
			} finally {
				MemoryUtil.memFree(rgba);
			}
		}
	}

	public void close() {
		synchronized (FreeTypeUtil.LIBRARY_LOCK) {
			FreeType.FT_Done_Face(face);
		}
		MemoryUtil.memFree(data);
	}
}
