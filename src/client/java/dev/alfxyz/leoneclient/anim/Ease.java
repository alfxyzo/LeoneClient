package dev.alfxyz.leoneclient.anim;

/**
 * CSS-style cubic-bezier timing functions.
 */
public final class Ease {
	/** The menu's main curve: cubic-bezier(0.2, 0.9, 0.25, 1). */
	public static final Ease SNAP = new Ease(0.2, 0.9, 0.25, 1.0);
	/** CSS "ease". */
	public static final Ease EASE = new Ease(0.25, 0.1, 0.25, 1.0);
	/** Pulse ring curve: cubic-bezier(.15, .8, .3, 1). */
	public static final Ease PULSE = new Ease(0.15, 0.8, 0.3, 1.0);
	public static final Ease LINEAR = new Ease(0.0, 0.0, 1.0, 1.0);

	private final double x1, y1, x2, y2;
	private final boolean linear;

	public Ease(double x1, double y1, double x2, double y2) {
		this.x1 = x1;
		this.y1 = y1;
		this.x2 = x2;
		this.y2 = y2;
		this.linear = x1 == y1 && x2 == y2;
	}

	public float apply(float t) {
		if (t <= 0) return 0;
		if (t >= 1) return 1;
		if (linear) return t;
		double u = solveX(t);
		return (float) sample(u, y1, y2);
	}

	private static double sample(double u, double a, double b) {
		double inv = 1 - u;
		return 3 * inv * inv * u * a + 3 * inv * u * u * b + u * u * u;
	}

	private static double slope(double u, double a, double b) {
		double inv = 1 - u;
		return 3 * inv * inv * a + 6 * inv * u * (b - a) + 3 * u * u * (1 - b);
	}

	private double solveX(double x) {
		double u = x;
		for (int i = 0; i < 8; i++) {
			double err = sample(u, x1, x2) - x;
			if (Math.abs(err) < 1e-6) return u;
			double d = slope(u, x1, x2);
			if (Math.abs(d) < 1e-6) break;
			u -= err / d;
		}
		double lo = 0, hi = 1;
		u = x;
		for (int i = 0; i < 40; i++) {
			double v = sample(u, x1, x2);
			if (Math.abs(v - x) < 1e-6) break;
			if (v < x) lo = u; else hi = u;
			u = (lo + hi) / 2;
		}
		return u;
	}

	/** Eased progress of an animation that starts {@code delay} ms after {@code start}. */
	public static float progress(double now, double start, double delay, double duration, Ease ease) {
		double t = (now - start - delay) / duration;
		return ease.apply((float) Math.max(0, Math.min(1, t)));
	}
}
