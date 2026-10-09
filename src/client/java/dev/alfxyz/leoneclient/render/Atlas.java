package dev.alfxyz.leoneclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.render.TextureSetup;
import org.lwjgl.system.MemoryUtil;

/**
 * One linear-filtered RGBA texture that holds every glyph, icon and image the
 * menu draws, plus an opaque white block used for untextured geometry. Regions
 * are packed in shelves. When it fills up, it is wiped at the start of the next
 * frame (never part-way through one, since the GUI is drawn after the whole frame
 * is built) and everything is rasterized again as it is next drawn.
 */
public final class Atlas {
	public static final int SIZE = 4096;
	/** Rows wiped per upload, so clearing never needs one buffer the size of the whole texture. */
	private static final int CLEAR_ROWS = 256;
	private static final int PAD = 2;

	private GpuTexture texture;
	private GpuTextureView view;
	private GpuSampler sampler;
	private TextureSetup setup;

	/**
	 * Rows of the atlas, each holding images of about its own height: {top, height, used width}. A new
	 * image goes in the snuggest row with room, so small glyphs never sit in a row made tall by a head.
	 */
	private final List<int[]> shelves = new ArrayList<>();
	private int nextShelfY;
	private int generation;
	/** Set when something did not fit; the atlas is wiped at the start of the next frame. */
	private boolean full;

	/** UV of the centre of the white block. */
	public final float whiteU = 2f / SIZE, whiteV = 2f / SIZE;

	public record Region(int x, int y, int w, int h, int generation) {
		public float u0() { return (float) x / SIZE; }
		public float v0() { return (float) y / SIZE; }
		public float u1() { return (float) (x + w) / SIZE; }
		public float v1() { return (float) (y + h) / SIZE; }
	}

	public void ensureCreated() {
		if (texture != null && !texture.isClosed()) return;
		var device = RenderSystem.getDevice();
		texture = device.createTexture("leoneclient atlas", GpuTexture.USAGE_COPY_DST | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.RGBA8_UNORM, SIZE, SIZE, 1, 1);
		view = device.createTextureView(texture);
		sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
		setup = TextureSetup.singleTexture(view, sampler);
		clear();
	}

	public TextureSetup textureSetup() {
		ensureCreated();
		return setup;
	}

	public int generation() {
		return generation;
	}

	/** Called at the start of every frame, before anything is drawn: wipes the atlas if something did not fit last frame. */
	public void startFrame() {
		if (!full || texture == null || texture.isClosed()) return;
		full = false;
		clear();
	}

	/** Wipes the texture to transparent white and resets packing. */
	public void clear() {
		generation++;
		ByteBuffer buf = MemoryUtil.memAlloc(SIZE * CLEAR_ROWS * 4);
		try {
			var encoder = RenderSystem.getDevice().createCommandEncoder();
			for (int y0 = 0; y0 < SIZE; y0 += CLEAR_ROWS) {
				for (int i = 0; i < SIZE * CLEAR_ROWS; i++) buf.putInt(i * 4, 0x00FFFFFF);
				// opaque white block in the top-left corner
				if (y0 == 0) for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) buf.putInt((y * SIZE + x) * 4, 0xFFFFFFFF);
				encoder.writeToTexture(texture, buf, 0, 0, 0, y0, SIZE, CLEAR_ROWS);
			}
		} finally {
			MemoryUtil.memFree(buf);
		}
		shelves.clear();
		// the first row starts after the white block
		shelves.add(new int[] {0, 4 + PAD, 4 + PAD});
		nextShelfY = 4 + PAD;
	}

	/**
	 * Uploads a {@code w x h} RGBA image (bytes R,G,B,A) and returns its region, or {@code null} if the
	 * atlas is full. Then it is wiped before the next frame, so callers must not remember the miss.
	 */
	public Region add(int w, int h, ByteBuffer rgba) {
		ensureCreated();
		if (w <= 0 || h <= 0) return new Region(0, 0, 0, 0, generation);
		if (w + PAD > SIZE || h + PAD > SIZE) return null;
		int need = h + PAD, width = w + PAD;
		int[] row = null;
		for (int[] s : shelves) {
			// a row up to a quarter taller than needed (and a few pixels) is a good fit
			if (s[1] >= need && s[1] <= need * 5 / 4 + 3 && s[2] + width <= SIZE && (row == null || s[1] < row[1])) row = s;
		}
		if (row == null) {
			int rowH = (need + 3) / 4 * 4;
			if (nextShelfY + rowH > SIZE) {
				full = true;
				return null;
			}
			row = new int[] {nextShelfY, rowH, 0};
			shelves.add(row);
			nextShelfY += rowH;
		}
		int x = row[2], y = row[0];
		row[2] += width;
		RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, rgba, 0, 0, x, y, w, h);
		return new Region(x, y, w, h, generation);
	}

	/** Allocates a direct buffer for a white image whose alpha comes from {@code alpha}. */
	public static ByteBuffer whiteWithAlpha(float[] alpha, int w, int h) {
		ByteBuffer buf = MemoryUtil.memAlloc(w * h * 4);
		for (int i = 0; i < w * h; i++) {
			int a = Math.round(Math.max(0, Math.min(1, alpha[i])) * 255);
			buf.put(i * 4, (byte) 255).put(i * 4 + 1, (byte) 255).put(i * 4 + 2, (byte) 255).put(i * 4 + 3, (byte) a);
		}
		return buf;
	}

	public void close() {
		if (view != null) view.close();
		if (texture != null) texture.close();
		texture = null;
		view = null;
	}
}
