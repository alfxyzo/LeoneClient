package dev.alfxyz.leoneclient.staffchat;

import static dev.alfxyz.leoneclient.staffchat.StaffChat.LOGGER;

import com.google.common.collect.MapMaker;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import java.nio.ByteBuffer;
import java.util.Map;

/**
 * CPU copies of Minecraft's glyph textures, so the overlay can draw exactly the pixels Minecraft would.
 * Glyphs are only ever added to these textures, never changed, so a copy stays valid for every glyph
 * that existed when it was taken.
 */
public final class GlyphAtlas {
	/** One texture's pixels. Grayscale textures have one byte per texel, colour textures four (RGBA). */
	static final class Copy {
		byte[] data;
		int width;
		int height;
		int bytesPerTexel;
		long coveredSeq = -1;
		boolean pending;
		int failures;
	}

	private static long seq;
	private static boolean updated;
	// Weak identity maps: entries vanish when Minecraft drops a texture or glyph (for example on resource reload).
	private static final Map<GpuTexture, Copy> COPIES = new MapMaker().weakKeys().makeMap();
	private static final Map<Object, Long> GLYPH_SEQ = new MapMaker().weakKeys().makeMap();

	private GlyphAtlas() {
	}

	/** True once after a copy finished, so the overlay knows to redraw. */
	static boolean consumeUpdated() {
		boolean result = updated;
		updated = false;
		return result;
	}

	/** Called whenever Minecraft adds a glyph to a font texture. */
	public static void glyphAdded(Object glyph) {
		GLYPH_SEQ.put(glyph, ++seq);
	}

	/**
	 * Returns a copy of the texture that contains this glyph, or null if one is still being made.
	 * A missing or out-of-date copy is requested here and arrives a frame or two later.
	 */
	static Copy forGlyph(Object glyph, GpuTexture texture) {
		long needed = GLYPH_SEQ.getOrDefault(glyph, 0L);
		Copy copy = COPIES.computeIfAbsent(texture, t -> new Copy());
		if (copy.data != null && copy.coveredSeq >= needed) {
			return copy;
		}
		request(texture, copy);
		return null;
	}

	private static void request(GpuTexture texture, Copy copy) {
		if (copy.failures >= 3) {
			throw new IllegalStateException("Glyph textures cannot be copied");
		}
		if (copy.pending || texture.isClosed()) {
			return;
		}
		copy.pending = true;
		long requestSeq = seq;
		int width = texture.getWidth(0);
		int height = texture.getHeight(0);
		int bytesPerTexel = texture.getFormat().blockSize();
		int size = width * height * bytesPerTexel;
		GpuDevice device = RenderSystem.getDevice();
		GpuBuffer buffer = device.createBuffer(() -> "Leone Client staff chat glyph copy", GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, size);
		device.createCommandEncoder().copyTextureToBuffer(texture, buffer, 0L, () -> {
			try (GpuBufferSlice.MappedView view = buffer.map(true, false)) {
				ByteBuffer source = view.data();
				byte[] data = new byte[size];
				source.get(0, data);
				copy.data = data;
				copy.width = width;
				copy.height = height;
				copy.bytesPerTexel = bytesPerTexel;
				copy.coveredSeq = requestSeq;
			} catch (Throwable t) {
				copy.failures++;
				LOGGER.error("Could not copy a glyph texture", t);
			} finally {
				copy.pending = false;
				buffer.close();
				updated = true;
			}
		}, 0);
	}
}
