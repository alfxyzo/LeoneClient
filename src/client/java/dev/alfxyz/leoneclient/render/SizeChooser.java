package dev.alfxyz.leoneclient.render;

import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntIterable;

/**
 * Picks raster sizes for things drawn at a changing size, like a label in a panel that zooms in. A size is
 * rasterized exactly once it has been drawn in a few frames in a row; until then a nearby size that is
 * already made is stretched to fit. An animation then leaves a handful of copies in the atlas instead of
 * one per frame, while anything that holds still is as sharp as ever.
 */
final class SizeChooser {
	private static final int SETTLE_FRAMES = 3;
	private final Int2IntOpenHashMap runs = new Int2IntOpenHashMap(), seenAt = new Int2IntOpenHashMap();

	SizeChooser() {
		seenAt.defaultReturnValue(Integer.MIN_VALUE);
	}

	/** True once {@code size} has been asked for in a few frames in a row, so it is worth rasterizing exactly. */
	boolean settled(int size) {
		int frame = Gfx.frame();
		int last = seenAt.get(size);
		if (last != frame) {
			runs.put(size, last == frame - 1 ? runs.get(size) + 1 : 1);
			seenAt.put(size, frame);
			if (seenAt.size() > 4096) {
				runs.clear();
				seenAt.clear();
			}
		}
		return runs.get(size) >= SETTLE_FRAMES;
	}

	/**
	 * The size to draw from: {@code wanted} if it is made or has settled, otherwise the nearest made size
	 * between {@code wanted * down} and {@code wanted * up}, or {@code wanted} when there is none.
	 */
	int choose(int wanted, IntIterable made, boolean isMade, float down, float up) {
		boolean settled = settled(wanted);
		if (isMade || settled) return wanted;
		int best = -1;
		for (var it = made.iterator(); it.hasNext(); ) {
			int s = it.nextInt();
			if (s < wanted * down || s > wanted * up) continue;
			if (best < 0 || Math.abs(s - wanted) < Math.abs(best - wanted)) best = s;
		}
		return best < 0 ? wanted : best;
	}
}
