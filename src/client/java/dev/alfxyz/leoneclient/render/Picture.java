package dev.alfxyz.leoneclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import java.io.InputStream;
import java.nio.ByteBuffer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * An image (the logo, or a player head) area-averaged down to the exact pixel
 * size it is drawn at, plus a Gaussian-blurred copy of its alpha for soft glows.
 * The source is decoded once; atlas copies are made lazily on the render thread.
 */
public final class Picture {
	private static final Logger LOGGER = LogUtils.getLogger();
	private final Atlas atlas;
	private final @Nullable Identifier id;
	private int srcW, srcH;
	private float @Nullable [] srcR, srcG, srcB, srcA; // premultiplied
	private boolean failed;
	private final Int2ObjectOpenHashMap<Atlas.Region> images = new Int2ObjectOpenHashMap<>();
	private final SizeChooser imageSizes = new SizeChooser(), glowSizes = new SizeChooser();
	private final Map<Long, Atlas.Region> glows = new HashMap<>();
	private final Map<Long, Integer> glowPad = new HashMap<>();
	private int generation = -1;

	/** An image from the mod's resources, loaded when first drawn. */
	public Picture(Atlas atlas, Identifier id) {
		this.atlas = atlas;
		this.id = id;
	}

	private Picture(Atlas atlas, NativeImage img) {
		this.atlas = atlas;
		this.id = null;
		read(img);
	}

	/** Copies a decoded image. Safe to call off the render thread; the image can be closed afterwards. */
	public static Picture of(Atlas atlas, NativeImage img) {
		return new Picture(atlas, img);
	}

	private void read(NativeImage img) {
		srcW = img.getWidth();
		srcH = img.getHeight();
		int n = srcW * srcH;
		float[] r = new float[n], g = new float[n], b = new float[n], a = new float[n];
		for (int y = 0; y < srcH; y++) {
			for (int x = 0; x < srcW; x++) {
				int argb = img.getPixel(x, y);
				float al = ((argb >>> 24) & 0xFF) / 255f;
				int i = y * srcW + x;
				a[i] = al;
				r[i] = ((argb >> 16) & 0xFF) / 255f * al;
				g[i] = ((argb >> 8) & 0xFF) / 255f * al;
				b[i] = (argb & 0xFF) / 255f * al;
			}
		}
		srcR = r;
		srcG = g;
		srcB = b;
		srcA = a;
	}

	private boolean ensureSource() {
		if (srcA != null) return true;
		if (failed || id == null) return false;
		try (InputStream in = Minecraft.getInstance().getResourceManager().open(id); NativeImage img = NativeImage.read(in)) {
			read(img);
			return true;
		} catch (Exception e) {
			LOGGER.error("Leone Client: could not load image {}", id, e);
			failed = true;
			return false;
		}
	}

	private void checkGeneration() {
		if (generation != atlas.generation()) {
			images.clear();
			glows.clear();
			glowPad.clear();
			generation = atlas.generation();
		}
	}

	/** Draws the image into the square (x, y, size). */
	public void draw(Canvas cv, float x, float y, float size, int color) {
		if ((cv.withAlpha(color) >>> 24) == 0 || !ensureSource()) return;
		checkGeneration();
		int px = Math.max(2, Math.round(size / cv.px()));
		// while the size animates, a near size already made is stretched instead of making a new one
		int use = imageSizes.choose(px, images.keySet(), images.containsKey(px), 0.75f, 1.35f);
		Atlas.Region r = images.get(use);
		if (r == null) r = images.computeIfAbsent(use, this::buildImage);
		if (r == null) return;
		cv.image(r, x, y, size, size, color);
	}

	/** Draws a blurred copy of the image's alpha (CSS drop-shadow with sigma in local units). */
	public void drawGlow(Canvas cv, float x, float y, float size, float sigma, int color) {
		if ((cv.withAlpha(color) >>> 24) == 0 || !ensureSource()) return;
		checkGeneration();
		float devPer = 1f / cv.px();
		int wantPx = Math.max(2, Math.round(size * devPer / 2)); // glow is smooth, half resolution is plenty
		// an animating glow reuses a near size made with the same blur (scaled, the blur scales with it)
		int sigmaQ = Math.round(sigma * 4);
		IntArrayList madePx = new IntArrayList();
		for (long k : glows.keySet()) if ((int) k == sigmaQ) madePx.add((int) (k >>> 32));
		int px = glowSizes.choose(wantPx, madePx, madePx.contains(wantPx), 0.75f, 1.35f);
		float sigmaPx = sigma * devPer / 2 * px / wantPx;
		long key = ((long) px << 32) | sigmaQ;
		Atlas.Region r = glows.computeIfAbsent(key, k -> buildGlow(px, sigmaPx, k));
		if (r == null) return;
		int pad = glowPad.getOrDefault(key, 0);
		float unit = size / px;
		cv.image(r, x - pad * unit, y - pad * unit, r.w() * unit, r.h() * unit, color);
	}

	private float[][] downsample(int px) {
		float[] r = new float[px * px], g = new float[px * px], b = new float[px * px], a = new float[px * px];
		float sx = srcW / (float) px, sy = srcH / (float) px;
		for (int ty = 0; ty < px; ty++) {
			float y0 = ty * sy, y1 = y0 + sy;
			for (int tx = 0; tx < px; tx++) {
				float x0 = tx * sx, x1 = x0 + sx;
				float ar = 0, ag = 0, ab = 0, aa = 0, wsum = 0;
				for (int yy = (int) y0; yy < Math.min(srcH, (int) Math.ceil(y1)); yy++) {
					float wy = Math.min(y1, yy + 1) - Math.max(y0, yy);
					for (int xx = (int) x0; xx < Math.min(srcW, (int) Math.ceil(x1)); xx++) {
						float w = (Math.min(x1, xx + 1) - Math.max(x0, xx)) * wy;
						int i = yy * srcW + xx;
						ar += srcR[i] * w; ag += srcG[i] * w; ab += srcB[i] * w; aa += srcA[i] * w;
						wsum += w;
					}
				}
				int o = ty * px + tx;
				if (wsum > 0) { r[o] = ar / wsum; g[o] = ag / wsum; b[o] = ab / wsum; a[o] = aa / wsum; }
			}
		}
		return new float[][] {r, g, b, a};
	}

	private Atlas.Region buildImage(int px) {
		float[][] d = downsample(px);
		ByteBuffer buf = MemoryUtil.memAlloc(px * px * 4);
		try {
			for (int i = 0; i < px * px; i++) {
				float a = d[3][i];
				float inv = a > 0 ? 1 / a : 0;
				int o = i * 4;
				buf.put(o, (byte) (a > 0 ? Math.round(Math.min(1, d[0][i] * inv) * 255) : 255));
				buf.put(o + 1, (byte) (a > 0 ? Math.round(Math.min(1, d[1][i] * inv) * 255) : 255));
				buf.put(o + 2, (byte) (a > 0 ? Math.round(Math.min(1, d[2][i] * inv) * 255) : 255));
				buf.put(o + 3, (byte) Math.round(a * 255));
			}
			return atlas.add(px, px, buf);
		} finally {
			MemoryUtil.memFree(buf);
		}
	}

	private Atlas.Region buildGlow(int px, float sigmaPx, long key) {
		int pad = (int) Math.ceil(sigmaPx * 3) + 1;
		int dim = px + pad * 2;
		float[] src = new float[dim * dim];
		float[] a = downsample(px)[3];
		for (int y = 0; y < px; y++) System.arraycopy(a, y * px, src, (y + pad) * dim + pad, px);
		float[] tmp = new float[dim * dim];
		int kr = (int) Math.ceil(sigmaPx * 3);
		float[] kernel = new float[kr * 2 + 1];
		float ksum = 0;
		for (int i = -kr; i <= kr; i++) {
			kernel[i + kr] = (float) Math.exp(-(i * i) / (2 * sigmaPx * sigmaPx + 1e-6));
			ksum += kernel[i + kr];
		}
		for (int i = 0; i < kernel.length; i++) kernel[i] /= ksum;
		for (int y = 0; y < dim; y++) {
			for (int x = 0; x < dim; x++) {
				float s = 0;
				for (int k = -kr; k <= kr; k++) {
					int xx = x + k;
					if (xx >= 0 && xx < dim) s += src[y * dim + xx] * kernel[k + kr];
				}
				tmp[y * dim + x] = s;
			}
		}
		for (int y = 0; y < dim; y++) {
			for (int x = 0; x < dim; x++) {
				float s = 0;
				for (int k = -kr; k <= kr; k++) {
					int yy = y + k;
					if (yy >= 0 && yy < dim) s += tmp[yy * dim + x] * kernel[k + kr];
				}
				src[y * dim + x] = s;
			}
		}
		ByteBuffer buf = Atlas.whiteWithAlpha(src, dim, dim);
		try {
			Atlas.Region r = atlas.add(dim, dim, buf);
			glowPad.put(key, pad);
			return r;
		} finally {
			MemoryUtil.memFree(buf);
		}
	}
}
