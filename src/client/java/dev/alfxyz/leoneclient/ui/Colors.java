package dev.alfxyz.leoneclient.ui;

/** ARGB helpers and the menu palette. The accent follows the theme chosen in Interface. */
public final class Colors {
	/** Leone pink, from the logo. */
	public static final int DEFAULT_ACCENT = 0xFC46A8;
	public static int ACCENT_RGB = DEFAULT_ACCENT;
	public static int ACCENT = 0xFF000000 | ACCENT_RGB;
	public static final int GLASS_RGB = 0x0E0E12;
	public static final int TEXT = 0xFFEDEDED;
	public static final int TEXT_SECONDARY = 0xFFB4B4BA;
	public static final int TEXT_HINT = 0xFF8E8E94;
	public static final int TEXT_MUTED = 0xFFA1A1A8;
	public static final int TEXT_DOCK = 0xFFC8C8CC;
	public static final int TEXT_DIM = 0xFF80808A;
	public static final int WHITE = 0xFFFFFFFF;
	public static final int KNOB_OFF = 0xFF8A8A92;

	private Colors() {
	}

	public static void setAccent(int rgb) {
		ACCENT_RGB = rgb & 0xFFFFFF;
		ACCENT = 0xFF000000 | ACCENT_RGB;
	}

	public static int rgba(int rgb, float a) {
		int al = Math.round(Math.max(0, Math.min(1, a)) * 255);
		return (al << 24) | (rgb & 0xFFFFFF);
	}

	public static int accent(float a) {
		return rgba(ACCENT_RGB, a);
	}

	public static int white(float a) {
		return rgba(0xFFFFFF, a);
	}

	public static int black(float a) {
		return rgba(0x000000, a);
	}

	public static int glass(float a) {
		return rgba(GLASS_RGB, a);
	}

	public static int alpha(int argb, float m) {
		int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0, Math.min(1, m)));
		return (al << 24) | (argb & 0xFFFFFF);
	}

	/** Interpolates in premultiplied space, like CSS colour transitions. */
	public static int lerp(int c0, int c1, float t) {
		float a0 = ((c0 >>> 24) & 0xFF) / 255f, a1 = ((c1 >>> 24) & 0xFF) / 255f;
		float a = a0 + (a1 - a0) * t;
		if (a <= 0) return 0;
		float r = (((c0 >> 16) & 0xFF) * a0 + (((c1 >> 16) & 0xFF) * a1 - ((c0 >> 16) & 0xFF) * a0) * t) / a;
		float g = (((c0 >> 8) & 0xFF) * a0 + (((c1 >> 8) & 0xFF) * a1 - ((c0 >> 8) & 0xFF) * a0) * t) / a;
		float b = ((c0 & 0xFF) * a0 + ((c1 & 0xFF) * a1 - (c0 & 0xFF) * a0) * t) / a;
		return (Math.round(a * 255) << 24) | (clamp(r) << 16) | (clamp(g) << 8) | clamp(b);
	}

	private static int clamp(float v) {
		return Math.max(0, Math.min(255, Math.round(v)));
	}
}
