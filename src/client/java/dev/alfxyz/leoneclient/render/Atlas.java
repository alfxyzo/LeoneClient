package dev.alfxyz.leoneclient.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.textures.GpuSampler;
import com.mojang.blaze3d.textures.GpuTexture;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import net.minecraft.client.gui.render.TextureSetup;
import org.lwjgl.system.MemoryUtil;

/**
 * One linear-filtered RGBA texture that holds every glyph, icon and image the
 * menu draws, plus an opaque white block used for untextured geometry. Regions
 * are packed in shelves. When it fills up, everything is thrown away and
 * re-rasterized lazily.
 */
public final class Atlas {
	public static final int SIZE = 2048;
	private static final int PAD = 2;

	private GpuTexture texture;
	private GpuTextureView view;
	private GpuSampler sampler;
	private TextureSetup setup;

	private int shelfX, shelfY, shelfH;
	private int generation;

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

	/** Wipes the texture to transparent white and resets packing. */
	public void clear() {
		generation++;
		ByteBuffer buf = MemoryUtil.memAlloc(SIZE * SIZE * 4);
		try {
			for (int i = 0; i < SIZE * SIZE; i++) buf.putInt(i * 4, 0x00FFFFFF);
			// opaque white block in the top-left corner
			for (int y = 0; y < 4; y++) for (int x = 0; x < 4; x++) buf.putInt((y * SIZE + x) * 4, 0xFFFFFFFF);
			RenderSystem.getDevice().createCommandEncoder().writeToTexture(texture, buf, 0, 0, 0, 0, SIZE, SIZE);
		} finally {
			MemoryUtil.memFree(buf);
		}
		shelfX = 4 + PAD;
		shelfY = 0;
		shelfH = 4;
	}

	/**
	 * Uploads a {@code w x h} RGBA image (bytes R,G,B,A) and returns its region,
	 * or {@code null} if the atlas is full.
	 */
	public Region add(int w, int h, ByteBuffer rgba) {
		ensureCreated();
		if (w <= 0 || h <= 0) return new Region(0, 0, 0, 0, generation);
		if (w + PAD > SIZE || h + PAD > SIZE) return null;
		if (shelfX + w + PAD > SIZE) {
			shelfY += shelfH + PAD;
			shelfX = 0;
			shelfH = 0;
		}
		if (shelfY + h + PAD > SIZE) return null;
		int x = shelfX, y = shelfY;
		shelfX += w + PAD;
		shelfH = Math.max(shelfH, h);
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
