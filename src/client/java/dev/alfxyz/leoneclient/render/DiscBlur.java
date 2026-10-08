package dev.alfxyz.leoneclient.render;

import java.util.HashMap;
import java.util.Map;

/**
 * Intensity of a uniform disc after a Gaussian blur, as a function of the
 * distance from its centre. Tabulated per (radius, sigma).
 */
final class DiscBlur {
	private static final int N = 128;
	private static final Map<Long, float[]> CACHE = new HashMap<>();

	private DiscBlur() {
	}

	static float value(float d, float radius, float sigma) {
		if (sigma <= 0) return d <= radius ? 1 : 0;
		float[] lut = table(radius, sigma);
		float max = radius + sigma * 4;
		if (d >= max) return 0;
		float p = d / max * (N - 1);
		int i = (int) p;
		float t = p - i;
		return lut[i] + (lut[Math.min(N - 1, i + 1)] - lut[i]) * t;
	}

	private static float[] table(float radius, float sigma) {
		long key = ((long) Float.floatToIntBits(Math.round(radius * 8) / 8f) << 32) | (Float.floatToIntBits(Math.round(sigma * 8) / 8f) & 0xFFFFFFFFL);
		float[] lut = CACHE.get(key);
		if (lut != null) return lut;
		if (CACHE.size() > 256) CACHE.clear();
		lut = new float[N];
		float max = radius + sigma * 4;
		double s2 = sigma * sigma;
		int steps = 64;
		for (int i = 0; i < N; i++) {
			double d = max * i / (N - 1.0);
			// I(d) = integral_0^R rho/s^2 * exp(-(d-rho)^2 / 2s^2) * I0e(d*rho/s^2) d rho  (Simpson)
			double h = radius / steps, sum = 0;
			for (int k = 0; k <= steps; k++) {
				double rho = k * h;
				double v = rho / s2 * Math.exp(-(d - rho) * (d - rho) / (2 * s2)) * i0e(d * rho / s2);
				sum += (k == 0 || k == steps) ? v : (k % 2 == 1 ? 4 * v : 2 * v);
			}
			lut[i] = (float) Math.min(1, sum * h / 3);
		}
		lut[N - 1] = 0;
		CACHE.put(key, lut);
		return lut;
	}

	/** Exponentially scaled modified Bessel function I0(x) * exp(-x), x >= 0. */
	private static double i0e(double x) {
		if (x <= 3.75) {
			double t = x / 3.75, t2 = t * t;
			double i0 = 1 + t2 * (3.5156229 + t2 * (3.0899424 + t2 * (1.2067492 + t2 * (0.2659732 + t2 * (0.0360768 + t2 * 0.0045813)))));
			return i0 * Math.exp(-x);
		}
		double t = 3.75 / x;
		return (0.39894228 + t * (0.01328592 + t * (0.00225319 + t * (-0.00157565 + t * (0.00916281 + t * (-0.02057706 + t * (0.02635537 + t * (-0.01647633 + t * 0.00392377)))))))) / Math.sqrt(x);
	}
}
