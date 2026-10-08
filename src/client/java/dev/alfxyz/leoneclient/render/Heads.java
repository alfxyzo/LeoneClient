package dev.alfxyz.leoneclient.render;

import com.mojang.blaze3d.platform.NativeImage;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.web.Http;
import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Player head avatars from mc-heads.net, fetched on first use and kept for the session. */
public final class Heads {
	private static final long RETRY_MS = 5 * 60_000;
	private static final Map<UUID, Picture> loaded = new ConcurrentHashMap<>();
	private static final Map<UUID, Long> pending = new ConcurrentHashMap<>();

	private Heads() {
	}

	private static void request(UUID uuid, Atlas atlas) {
		Long since = pending.get(uuid);
		if (since != null && (since > 0 || System.currentTimeMillis() + since < RETRY_MS)) return;
		pending.put(uuid, 1L);
		Http.get("https://mc-heads.net/avatar/" + uuid + "/64", "image/png").whenComplete((r, err) -> {
			if (err == null && r.status() == 200) {
				try (NativeImage img = NativeImage.read(new ByteArrayInputStream(r.body()))) {
					loaded.put(uuid, Picture.of(atlas, img));
					pending.remove(uuid);
					return;
				} catch (Exception ignored) {
				}
			}
			// negative: the time of the failure, so it is retried later
			pending.put(uuid, -System.currentTimeMillis());
		});
	}

	/** Draws a player's head in a rounded square, or their initial while it loads. */
	public static void draw(Canvas cv, TextRenderer text, UUID uuid, String name, float x, float y, float size, float radius) {
		Picture p = loaded.get(uuid);
		if (p == null) {
			request(uuid, cv.atlas());
			cv.fillRoundRect(x, y, size, size, radius, Colors.accent(0.28f));
			String initial = name.isEmpty() ? "?" : name.substring(0, 1).toUpperCase(Locale.ROOT);
			TextRenderer.Style st = TextRenderer.Style.of(size * 0.5f, TextRenderer.Weight.SEMIBOLD);
			text.draw(cv, initial, x + (size - text.width(initial, st)) / 2, text.baselineFor(st, y + size / 2), st, Colors.WHITE);
			return;
		}
		cv.push();
		cv.clipRect(x, y, x + size, y + size);
		p.draw(cv, x, y, size, Colors.WHITE);
		cv.pop();
	}
}
