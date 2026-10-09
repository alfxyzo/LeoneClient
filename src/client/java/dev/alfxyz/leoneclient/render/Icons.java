package dev.alfxyz.leoneclient.render;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lwjgl.system.MemoryUtil;

/**
 * Stroked 24x24 SVG icons (Lucide, ISC licence), rasterized with an exact distance
 * field at the size they are drawn, so they stay crisp at any GUI scale.
 */
public final class Icons {
	public static final String COMBAT = "M14.5 17.5 3 6V3h3l11.5 11.5 M13 19l6-6 M16 16l4 4 M19 21l2-2";
	public static final String MOVEMENT = "M12.8 19.6A2 2 0 1 0 14 16H2 M17.5 8a2.5 2.5 0 1 1 2 4H2 M9.8 4.4A2 2 0 1 1 11 8H2";
	public static final String VISUALS = "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z";
	public static final String WORLD = "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z M2 12h20 M12 2a15 15 0 0 1 0 20 M12 2a15 15 0 0 0 0 20";
	public static final String UTILITY = "M14.7 6.3a1 1 0 0 0 0 1.4l1.6 1.6a1 1 0 0 0 1.4 0l3.77-3.77a6 6 0 0 1-7.94 7.94l-6.91 6.91a2.12 2.12 0 0 1-3-3l6.91-6.91a6 6 0 0 1 7.94-7.94l-3.76 3.76z";
	public static final String INTERNALS = "M9 9h6v6H9z M5 5h14v14H5z M9 2v3 M15 2v3 M9 19v3 M15 19v3 M2 9h3 M2 15h3 M19 9h3 M19 15h3";
	public static final String GEAR = "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 1 1-4 0v-.09A1.65 1.65 0 0 0 9 19.4a1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06A1.65 1.65 0 0 0 4.68 15a1.65 1.65 0 0 0-1.51-1H3a2 2 0 1 1 0-4h.09A1.65 1.65 0 0 0 4.6 9a1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06A1.65 1.65 0 0 0 9 4.68a1.65 1.65 0 0 0 1-1.51V3a2 2 0 1 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06A1.65 1.65 0 0 0 19.4 9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 1 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1Z";
	public static final String SEARCH = "M11 4a7 7 0 1 0 0 14 7 7 0 0 0 0-14Z M21 21l-4.3-4.3";
	public static final String CONFIGS = "M4 21v-7 M4 10V3 M12 21v-9 M12 8V3 M20 21v-5 M20 12V3 M1 14h6 M9 8h6 M17 16h6";
	public static final String FRIENDS = "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75";
	public static final String OVERLAYS = "M12 2 2 7l10 5 10-5-10-5Z M2 17l10 5 10-5 M2 12l10 5 10-5";
	public static final String HUD = "M3 3h18v18H3z M3 9h18 M9 21V9";
	public static final String BACK = "M15 18l-6-6 6-6";
	public static final String TRASH = "M3 6h18 M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6 M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2 M10 11v6 M14 11v6";
	public static final String CLOSE = "M18 6 6 18 M6 6l12 12";
	public static final String PLUS = "M12 5v14 M5 12h14";
	public static final String USER_PLUS = "M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z M19 8v6 M22 11h-6";
	public static final String FOLDER = "M20 20a2 2 0 0 0 2-2V8a2 2 0 0 0-2-2h-7.9a2 2 0 0 1-1.69-.9L9.6 3.9A2 2 0 0 0 7.93 3H4a2 2 0 0 0-2 2v13a2 2 0 0 0 2 2Z";
	public static final String CHAT = "M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z";
	public static final String MONITOR = "M4 3h16a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z M8 21h8 M12 17v4";
	public static final String SERVER = "M4 2h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2Z M4 14h16a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-4a2 2 0 0 1 2-2Z M6 6h.01 M6 18h.01";
	public static final String TIMER = "M10 2h4 M12 14l3-3 M12 6a8 8 0 1 0 0 16 8 8 0 0 0 0-16Z";
	public static final String SHIELD = "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z";
	public static final String BARS = "M4 8h16 M7 13h10 M9 18h6";
	public static final String ERASER = "M7 21l-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21 M22 21H7 M5 11l9 9";
	public static final String BELL_OFF = "M8.7 3A6 6 0 0 1 18 8a21.3 21.3 0 0 0 .6 5 M17 17H3s3-2 3-9a4.67 4.67 0 0 1 .3-1.7 M10.3 21a1.94 1.94 0 0 0 3.4 0 M2 2l20 20";
	public static final String STAR = "M12 2l3.09 6.26L22 9.27l-5 4.87 1.18 6.88L12 17.77l-6.18 3.25L7 14.14 2 9.27l6.91-1.01L12 2z";
	public static final String AT = "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-4 8";
	public static final String PANEL_BOTTOM = "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2Z M3 15h18";
	public static final String EYE_OFF = "M9.88 9.88a3 3 0 1 0 4.24 4.24 M10.73 5.08A10.43 10.43 0 0 1 12 5c7 0 10 7 10 7a13.16 13.16 0 0 1-1.67 2.68 M6.61 6.61A13.53 13.53 0 0 0 2 12s3 7 10 7a9.74 9.74 0 0 0 5.39-1.61 M2 2l20 20";
	public static final String LOG_IN = "M15 3h4a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2h-4 M10 17l5-5-5-5 M15 12H3";
	public static final String BOOK_X = "M4 19.5v-15A2.5 2.5 0 0 1 6.5 2H20v20H6.5a2.5 2.5 0 0 1 0-5H20 M14.5 7l-5 5 M9.5 7l5 5";
	public static final String ARCHIVE = "M3 3h18a1 1 0 0 1 1 1v3a1 1 0 0 1-1 1H3a1 1 0 0 1-1-1V4a1 1 0 0 1 1-1Z M4 8v11a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8 M10 12h4";
	public static final String PALETTE = "M12 22a1 1 0 0 1 0-20 10 9 0 0 1 10 9 5 5 0 0 1-5 5h-2.25a1.75 1.75 0 0 0-1.4 2.8l.3.4a1.75 1.75 0 0 1-1.4 2.8z M13.5 6.5h.01 M17.5 10.5h.01 M6.5 12.5h.01 M8.5 7.5h.01";
	public static final String TERMINAL = "M4 17l6-6-6-6 M12 19h8";
	public static final String REFRESH = "M21 12a9 9 0 0 0-9-9 9.75 9.75 0 0 0-6.74 2.74L3 8 M3 3v5h5 M3 12a9 9 0 0 0 9 9 9.75 9.75 0 0 0 6.74-2.74L21 16 M16 16h5v5";
	public static final String EXTERNAL = "M15 3h6v6 M10 14 21 3 M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6";
	public static final String HEART = "M19 14c1.49-1.46 3-3.21 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.76 0-3 .5-4.5 2-1.5-1.5-2.74-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4.05 3 5.5l7 7Z";
	public static final String FLAME = "M8.5 14.5A2.5 2.5 0 0 0 11 12c0-1.38-.5-2-1-3-1.07-2.14-.22-4.05 2-6 .5 2.5 2 4.9 4 6.5 2 1.6 3 3.5 3 5.5a7 7 0 1 1-14 0c0-1.15.43-2.29 1-3a2.5 2.5 0 0 0 2.5 2.5z";
	public static final String FEATHER = "M12.67 19a2 2 0 0 0 1.42-.59l6.15-6.17a6 6 0 0 0-8.49-8.49L5.59 9.91A2 2 0 0 0 5 11.33V18a1 1 0 0 0 1 1z M16 8 2 22 M17.5 15H9";
	public static final String GEM = "M6 3h12l4 6-10 13L2 9Z M11 3 8 9l4 13 4-13-3-6 M2 9h20";
	public static final String COINS = "M8 14a6 6 0 1 0 0-12 6 6 0 0 0 0 12Z M18.09 10.37A6 6 0 1 1 10.34 18 M7 6h1v4 M16.71 13.88l.7.71-2.82 2.82";
	public static final String TREE = "M12 22v-7 M9 15h6l-3-4h2l-3-4h2l-3-5-3 5h2l-3 4h2z";
	public static final String HOME = "M3 10l9-7 9 7v10a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z M9 22V12h6v10";
	public static final String SWORDS = "M14.5 17.5 3 6V3h3l11.5 11.5 M13 19l6-6 M16 16l4 4 M19 21l2-2 M14.5 6.5 18 3h3v3l-3.5 3.5 M5 14l4 4 M7 17l-3 3 M3 19l2 2";
	public static final String MESSAGE = "M7.9 20A9 9 0 1 0 4 16.1L2 22Z";
	public static final String USER = "M19 21v-2a4 4 0 0 0-4-4H9a4 4 0 0 0-4 4v2 M12 3a4 4 0 1 0 0 8 4 4 0 0 0 0-8Z";
	public static final String CHECK = "M20 6 9 17l-5-5";
	public static final String SHIELD_CHECK = "M20 13c0 5-3.5 7.5-7.66 8.95a1 1 0 0 1-.67-.01C7.5 20.5 4 18 4 13V6a1 1 0 0 1 1-1c2 0 4.5-1.2 6.24-2.72a1.17 1.17 0 0 1 1.52 0C14.51 3.81 17 5 19 5a1 1 0 0 1 1 1z M9 12l2 2 4-4";
	public static final String ALERT = "M10.29 3.86 1.82 18a2 2 0 0 0 1.71 3h16.94a2 2 0 0 0 1.71-3L13.71 3.86a2 2 0 0 0-3.42 0Z M12 9v4 M12 17h.01";
	public static final String FLAG = "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z M4 22v-7";
	public static final String EYE = "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z M12 9a3 3 0 1 0 0 6 3 3 0 0 0 0-6Z";
	public static final String CLOCK = "M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20Z M12 6v6l4 2";
	public static final String TROPHY = "M6 9H4.5a2.5 2.5 0 0 1 0-5H6 M18 9h1.5a2.5 2.5 0 0 0 0-5H18 M4 22h16 M10 14.66V17c0 .55-.47.98-.97 1.21C7.85 18.75 7 20.24 7 22 M14 14.66V17c0 .55.47.98.97 1.21C16.15 18.75 17 20.24 17 22 M18 2H6v7a6 6 0 0 0 12 0V2Z";
	public static final String INBOX = "M22 12h-6l-2 3h-4l-2-3H2 M5.45 5.11 2 12v6a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2v-6l-3.45-6.89A2 2 0 0 0 16.76 4H7.24a2 2 0 0 0-1.79 1.11z";
	public static final String VIDEO_OFF = "M10.66 6H14a2 2 0 0 1 2 2v2.5l5.25-3.06A.5.5 0 0 1 22 7.87v8.26 M16 16a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h2 M2 2l20 20";
	public static final String KEY = "M2.59 18.59A2 2 0 0 0 2 20v2h3v-2h2v-2h2l1.4-1.4a6.5 6.5 0 1 0-4-4Z M16.5 7.5h.01";
	public static final String FILTER = "M3 6h18 M7 12h10 M10 18h4";
	public static final String COPY = "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z M4 16a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2";
	public static final String WEB = "M12 2v20 M2 12h20 M5 5L19 19 M19 5L5 19 M12 7L15.5 8.5L17 12L15.5 15.5L12 17L8.5 15.5L7 12L8.5 8.5Z";
	public static final String MESSAGES = "M14 9a2 2 0 0 1-2 2H6l-4 4V4a2 2 0 0 1 2-2h8a2 2 0 0 1 2 2z M18 9h2a2 2 0 0 1 2 2v11l-4-4h-6a2 2 0 0 1-2-2v-1";

	private record Key(String path, int sizeQ, int strokeQ) {
	}

	private final Atlas atlas;
	private final Map<String, float[]> segments = new HashMap<>();
	private final Map<Key, Atlas.Region> cache = new HashMap<>();
	/** Sizes made for each icon and stroke. */
	private final Map<String, IntOpenHashSet> madeSizes = new HashMap<>();
	private final SizeChooser sizeChooser = new SizeChooser();
	private int generation = -1;

	public Icons(Atlas atlas) {
		this.atlas = atlas;
	}

	/**
	 * Draws an icon whose 24-unit viewBox maps to a {@code size}-unit square at
	 * (x, y) in local coordinates, stroked with {@code stroke} viewBox units.
	 */
	public void draw(Canvas cv, String path, float x, float y, float size, float stroke, int color) {
		if ((cv.withAlpha(color) >>> 24) == 0) return;
		float devPerUnit = 1f / cv.px();
		float px = size * devPerUnit;
		int strokeQ = Math.round(stroke * 100);
		checkGeneration();
		// while the icon's size animates, a near size already made is stretched instead of making a new one
		IntOpenHashSet made = madeSizes.computeIfAbsent(path + "|" + strokeQ, k -> new IntOpenHashSet());
		int wanted = Math.max(4, Math.round(px * 2));
		int sizeQ = sizeChooser.choose(wanted, made, made.contains(wanted), 0.75f, 1.35f);
		Atlas.Region r = region(path, sizeQ, strokeQ);
		if (r != null) made.add(sizeQ);
		if (r == null) return;
		float rasterPx = sizeQ / 2f;
		// the raster has one pixel of padding on every side
		float unit = size / rasterPx;
		float x0 = x - unit, y0 = y - unit;
		if (cv.axisAligned()) {
			float gs = cv.guiScale();
			float gx = Math.round(cv.tx(x0, y0) * gs) / gs, gy = Math.round(cv.ty(x0, y0) * gs) / gs;
			float s = px / rasterPx / gs;
			cv.texQuadGui(gx, gy, gx + r.w() * s, gy + r.h() * s, r.u0(), r.v0(), r.u1(), r.v1(), color);
		} else {
			cv.texQuad(x0, y0, x0 + r.w() * unit, y0 + r.h() * unit, r.u0(), r.v0(), r.u1(), r.v1(), color);
		}
	}

	private void checkGeneration() {
		if (generation != atlas.generation()) {
			cache.clear();
			madeSizes.clear();
			generation = atlas.generation();
		}
	}

	private Atlas.Region region(String path, int sizeQ, int strokeQ) {
		checkGeneration();
		Key key = new Key(path, sizeQ, strokeQ);
		Atlas.Region r = cache.get(key);
		if (r != null) return r;
		float px = sizeQ / 2f;
		int dim = (int) Math.ceil(px) + 2;
		float[] seg = segments.computeIfAbsent(path, Icons::flatten);
		float scale = px / 24f;
		float hw = strokeQ / 100f * scale / 2;
		float[] alpha = new float[dim * dim];
		int n = seg.length / 4;
		for (int yy = 0; yy < dim; yy++) {
			for (int xx = 0; xx < dim; xx++) {
				// pixel centre in viewBox units (one pixel of padding)
				float vx = (xx + 0.5f - 1) / scale, vy = (yy + 0.5f - 1) / scale;
				float best = Float.MAX_VALUE;
				for (int i = 0; i < n; i++) {
					float ax = seg[i * 4], ay = seg[i * 4 + 1], bx = seg[i * 4 + 2], by = seg[i * 4 + 3];
					float dx = bx - ax, dy = by - ay;
					float l2 = dx * dx + dy * dy;
					float t = l2 > 0 ? ((vx - ax) * dx + (vy - ay) * dy) / l2 : 0;
					t = Math.max(0, Math.min(1, t));
					float ex = ax + dx * t - vx, ey = ay + dy * t - vy;
					float d2 = ex * ex + ey * ey;
					if (d2 < best) best = d2;
				}
				float dist = (float) Math.sqrt(best) * scale;
				alpha[yy * dim + xx] = Math.max(0, Math.min(1, hw - dist + 0.5f));
			}
		}
		ByteBuffer buf = Atlas.whiteWithAlpha(alpha, dim, dim);
		try {
			r = atlas.add(dim, dim, buf);
		} finally {
			MemoryUtil.memFree(buf);
		}
		if (r != null) cache.put(key, r);
		return r;
	}

	// ------------------------------------------------------------ SVG paths

	/** Parses an SVG path into line segments (x0, y0, x1, y1 quadruples). */
	static float[] flatten(String d) {
		List<Float> out = new ArrayList<>();
		Tokenizer tk = new Tokenizer(d);
		float cx = 0, cy = 0, sx = 0, sy = 0;
		float lastC2x = 0, lastC2y = 0;
		char cmd = 0, prevCmd = 0;
		while (tk.more()) {
			if (tk.peekCommand()) cmd = tk.command();
			else if (cmd == 'M') cmd = 'L';
			else if (cmd == 'm') cmd = 'l';
			boolean rel = Character.isLowerCase(cmd);
			float ox = rel ? cx : 0, oy = rel ? cy : 0;
			switch (Character.toUpperCase(cmd)) {
				case 'M' -> {
					cx = ox + tk.num(); cy = oy + tk.num();
					sx = cx; sy = cy;
				}
				case 'L' -> {
					float nx = ox + tk.num(), ny = oy + tk.num();
					line(out, cx, cy, nx, ny);
					cx = nx; cy = ny;
				}
				case 'H' -> {
					float nx = (rel ? cx : 0) + tk.num();
					line(out, cx, cy, nx, cy);
					cx = nx;
				}
				case 'V' -> {
					float ny = (rel ? cy : 0) + tk.num();
					line(out, cx, cy, cx, ny);
					cy = ny;
				}
				case 'C' -> {
					float x1 = ox + tk.num(), y1 = oy + tk.num(), x2 = ox + tk.num(), y2 = oy + tk.num(), x = ox + tk.num(), y = oy + tk.num();
					cubic(out, cx, cy, x1, y1, x2, y2, x, y);
					lastC2x = x2; lastC2y = y2;
					cx = x; cy = y;
				}
				case 'S' -> {
					float x1 = cx, y1 = cy;
					char pu = Character.toUpperCase(prevCmd);
					if (pu == 'C' || pu == 'S') { x1 = 2 * cx - lastC2x; y1 = 2 * cy - lastC2y; }
					float x2 = ox + tk.num(), y2 = oy + tk.num(), x = ox + tk.num(), y = oy + tk.num();
					cubic(out, cx, cy, x1, y1, x2, y2, x, y);
					lastC2x = x2; lastC2y = y2;
					cx = x; cy = y;
				}
				case 'A' -> {
					float rx = tk.num(), ry = tk.num(), rot = tk.num();
					boolean large = tk.flag(), sweep = tk.flag();
					float x = ox + tk.num(), y = oy + tk.num();
					arc(out, cx, cy, rx, ry, rot, large, sweep, x, y);
					cx = x; cy = y;
				}
				case 'Z' -> {
					line(out, cx, cy, sx, sy);
					cx = sx; cy = sy;
				}
				default -> throw new IllegalArgumentException("Unsupported path command " + cmd + " in " + d);
			}
			prevCmd = cmd;
		}
		float[] res = new float[out.size()];
		for (int i = 0; i < res.length; i++) res[i] = out.get(i);
		return res;
	}

	private static void line(List<Float> out, float x0, float y0, float x1, float y1) {
		out.add(x0); out.add(y0); out.add(x1); out.add(y1);
	}

	private static void cubic(List<Float> out, float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3) {
		int n = 16;
		float px = x0, py = y0;
		for (int i = 1; i <= n; i++) {
			float t = i / (float) n, u = 1 - t;
			float x = u * u * u * x0 + 3 * u * u * t * x1 + 3 * u * t * t * x2 + t * t * t * x3;
			float y = u * u * u * y0 + 3 * u * u * t * y1 + 3 * u * t * t * y2 + t * t * t * y3;
			line(out, px, py, x, y);
			px = x; py = y;
		}
	}

	/** SVG elliptical arc, endpoint parameterization (SVG spec F.6.5). */
	private static void arc(List<Float> out, float x1, float y1, float rx, float ry, float rotDeg, boolean large, boolean sweep, float x2, float y2) {
		if (rx == 0 || ry == 0) {
			line(out, x1, y1, x2, y2);
			return;
		}
		double phi = Math.toRadians(rotDeg), cos = Math.cos(phi), sin = Math.sin(phi);
		double dx = (x1 - x2) / 2.0, dy = (y1 - y2) / 2.0;
		double x1p = cos * dx + sin * dy, y1p = -sin * dx + cos * dy;
		double arx = Math.abs(rx), ary = Math.abs(ry);
		double lambda = (x1p * x1p) / (arx * arx) + (y1p * y1p) / (ary * ary);
		if (lambda > 1) {
			arx *= Math.sqrt(lambda);
			ary *= Math.sqrt(lambda);
		}
		double num = arx * arx * ary * ary - arx * arx * y1p * y1p - ary * ary * x1p * x1p;
		double den = arx * arx * y1p * y1p + ary * ary * x1p * x1p;
		double coef = den == 0 ? 0 : Math.sqrt(Math.max(0, num / den));
		if (large == sweep) coef = -coef;
		double cxp = coef * arx * y1p / ary, cyp = -coef * ary * x1p / arx;
		double cx = cos * cxp - sin * cyp + (x1 + x2) / 2.0, cy = sin * cxp + cos * cyp + (y1 + y2) / 2.0;
		double th1 = angle(1, 0, (x1p - cxp) / arx, (y1p - cyp) / ary);
		double dth = angle((x1p - cxp) / arx, (y1p - cyp) / ary, (-x1p - cxp) / arx, (-y1p - cyp) / ary);
		if (!sweep && dth > 0) dth -= Math.PI * 2;
		else if (sweep && dth < 0) dth += Math.PI * 2;
		int n = Math.max(4, (int) Math.ceil(Math.abs(dth) / (Math.PI / 24)));
		float px = x1, py = y1;
		for (int i = 1; i <= n; i++) {
			double t = th1 + dth * i / n;
			double ex = arx * Math.cos(t), ey = ary * Math.sin(t);
			float x = (float) (cos * ex - sin * ey + cx), y = (float) (sin * ex + cos * ey + cy);
			if (i == n) { x = x2; y = y2; }
			line(out, px, py, x, y);
			px = x; py = y;
		}
	}

	private static double angle(double ux, double uy, double vx, double vy) {
		double a = Math.atan2(ux * vy - uy * vx, ux * vx + uy * vy);
		return a;
	}

	private static final class Tokenizer {
		private final String s;
		private int i;

		Tokenizer(String s) {
			this.s = s;
		}

		private void skip() {
			while (i < s.length() && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) i++;
		}

		boolean more() {
			skip();
			return i < s.length();
		}

		boolean peekCommand() {
			skip();
			return i < s.length() && Character.isLetter(s.charAt(i));
		}

		char command() {
			skip();
			return s.charAt(i++);
		}

		boolean flag() {
			skip();
			char ch = s.charAt(i++);
			return ch == '1';
		}

		float num() {
			skip();
			int start = i;
			if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
			boolean dot = false, exp = false;
			while (i < s.length()) {
				char ch = s.charAt(i);
				if (Character.isDigit(ch)) {
					i++;
				} else if (ch == '.' && !dot && !exp) {
					dot = true;
					i++;
				} else if ((ch == 'e' || ch == 'E') && !exp) {
					exp = true;
					i++;
					if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
				} else {
					break;
				}
			}
			return Float.parseFloat(s.substring(start, i));
		}
	}
}
