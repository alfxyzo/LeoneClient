package dev.alfxyz.leoneclient.anim;

/**
 * A float that eases towards a target, like a CSS transition: when the target
 * changes, it animates from wherever it currently is.
 */
public final class Anim {
	private float from;
	private float to;
	private double start;
	private double duration;
	private Ease ease;

	public Anim(float value) {
		this.from = value;
		this.to = value;
		this.duration = 0;
		this.ease = Ease.LINEAR;
	}

	public float get(double now) {
		if (duration <= 0) return to;
		double t = (now - start) / duration;
		if (t >= 1) return to;
		if (t <= 0) return from;
		return from + (to - from) * ease.apply((float) t);
	}

	public float target() {
		return to;
	}

	public boolean done(double now) {
		return duration <= 0 || now - start >= duration;
	}

	/** Starts a transition to {@code target} unless that is already the target. */
	public void set(float target, double now, double durationMs, Ease ease) {
		if (target == to) return;
		this.from = get(now);
		this.to = target;
		this.start = now;
		this.duration = durationMs;
		this.ease = ease;
	}

	/** Like {@link #set} but restarts even when the target is unchanged. */
	public void restart(float fromValue, float target, double now, double durationMs, Ease ease) {
		this.from = fromValue;
		this.to = target;
		this.start = now;
		this.duration = durationMs;
		this.ease = ease;
	}

	public void snap(float value) {
		this.from = value;
		this.to = value;
		this.duration = 0;
	}
}
