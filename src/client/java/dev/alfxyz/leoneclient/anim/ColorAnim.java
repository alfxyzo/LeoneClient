package dev.alfxyz.leoneclient.anim;

import dev.alfxyz.leoneclient.ui.Colors;

/** An ARGB colour with a CSS-style transition. */
public final class ColorAnim {
	private int from, to;
	private double start, duration;
	private Ease ease = Ease.EASE;

	public ColorAnim(int value) {
		this.from = value;
		this.to = value;
	}

	public int get(double now) {
		if (duration <= 0) return to;
		double t = (now - start) / duration;
		if (t >= 1) return to;
		if (t <= 0) return from;
		return Colors.lerp(from, to, ease.apply((float) t));
	}

	public void set(int target, double now, double durationMs, Ease ease) {
		if (target == to) return;
		this.from = get(now);
		this.to = target;
		this.start = now;
		this.duration = durationMs;
		this.ease = ease;
	}
}
