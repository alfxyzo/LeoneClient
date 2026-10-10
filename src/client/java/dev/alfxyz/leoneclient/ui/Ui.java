package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * Per-frame drawing context for the menu's panel pages: the canvas, type
 * styles, hit registration, persistent transition state and shared widgets.
 * All coordinates are design px in the current canvas transform.
 */
public final class Ui {
	public static final Style LABEL = new Style(14, Weight.SEMIBOLD, 0.01f);
	public static final Style H2 = new Style(22, Weight.SEMIBOLD, -0.01f);
	public static final Style STAT = new Style(18, Weight.SEMIBOLD, -0.01f);
	public static final Style HINT = Style.of(12, Weight.REGULAR);
	public static final Style TILE = Style.of(13.5f, Weight.REGULAR);
	public static final Style DOCK = Style.of(13, Weight.REGULAR);
	public static final Style BODY = Style.of(14, Weight.REGULAR);
	public static final Style BODY_STRONG = Style.of(14, Weight.SEMIBOLD);
	public static final Style DESC = Style.of(13.5f, Weight.REGULAR);
	public static final Style BUTTON = Style.of(12.5f, Weight.REGULAR);
	public static final Style SEGMENT = Style.of(12, Weight.REGULAR);
	public static final Style CAPS = new Style(11, Weight.SEMIBOLD, 0.06f);
	public static final Style VALUE = Style.of(13, Weight.REGULAR);
	public static final Style SMALL = Style.of(11.5f, Weight.REGULAR);
	public static final Style INPUT = Style.of(14, Weight.REGULAR);
	public static final Style BADGE = Style.of(10.5f, Weight.SEMIBOLD);
	public static final Style CARD_TITLE = Style.of(14.5f, Weight.SEMIBOLD);
	public static final Style CARD_TEXT = Style.of(12.5f, Weight.REGULAR);
	public static final Style TIP = Style.of(12.5f, Weight.REGULAR);

	public enum Btn { GHOST, ACCENT, DANGER }

	public record Hit(float x0, float y0, float x1, float y1, @Nullable Runnable left, @Nullable Runnable right, @Nullable TextInput input) {
		boolean contains(double x, double y) {
			return x >= x0 && x < x1 && y >= y0 && y < y1;
		}
	}

	public final Canvas cv;
	public final TextRenderer text;
	public final Icons icons;
	public double now;
	public double mouseX, mouseY;
	public boolean interactive;
	/** Entrance timing for page items: items appear from {@code contentAt + contentBase + 16 * index}. */
	public double contentAt;
	public float contentBase;
	public @Nullable TextInput focused;

	final List<Hit> hits = new ArrayList<>();
	boolean wantPointer, wantText;
	/** Tooltip requested by whatever the mouse is over this frame. */
	@Nullable String tipText;
	private final Map<String, Anim> anims = new HashMap<>();
	private final Map<String, ColorAnim> colors = new HashMap<>();

	Ui(Canvas cv, TextRenderer text, Icons icons) {
		this.cv = cv;
		this.text = text;
		this.icons = icons;
	}

	void beginFrame(double now, double mouseX, double mouseY) {
		this.now = now;
		this.mouseX = mouseX;
		this.mouseY = mouseY;
		hits.clear();
		wantPointer = false;
		wantText = false;
		tipText = null;
	}

	// ------------------------------------------------------------- timing

	/** Entrance progress (0..1) of the i-th item on the current page. */
	public float stagger(int i) {
		if (now < contentAt) return 0;
		return Ease.progress(now, contentAt, contentBase + i * 16, 380, Ease.SNAP);
	}

	public Anim anim(String key, float initial) {
		return anims.computeIfAbsent(key, k -> new Anim(initial));
	}

	public ColorAnim color(String key, int initial) {
		return colors.computeIfAbsent(key, k -> new ColorAnim(initial));
	}

	/** Runs {@code body} faded and lifted by an entrance progress. */
	public void entering(float p, Runnable body) {
		if (p <= 0) return;
		cv.push();
		cv.translate(0, 8 * (1 - p));
		cv.mulAlpha(p);
		body.run();
		cv.pop();
	}

	// -------------------------------------------------------------- input

	/** While set, {x0, y0, x1, y1} in GUI px: only this part of the screen can be hovered or clicked (a scrolled area). */
	public float @Nullable [] clip;

	private boolean inClip(double gx, double gy) {
		return clip == null || gx >= clip[0] && gx < clip[2] && gy >= clip[1] && gy < clip[3];
	}

	public boolean hovered(float x, float y, float w, float h) {
		if (!interactive || !inClip(mouseX, mouseY)) return false;
		float[] p = cv.toLocal((float) mouseX, (float) mouseY);
		return p[0] >= x && p[0] < x + w && p[1] >= y && p[1] < y + h;
	}

	public void hit(float x, float y, float w, float h, @Nullable Runnable left, @Nullable Runnable right) {
		hit(x, y, w, h, left, right, null);
	}

	private void hit(float x, float y, float w, float h, @Nullable Runnable left, @Nullable Runnable right, @Nullable TextInput input) {
		if (!interactive) return;
		float ax = cv.tx(x, y), ay = cv.ty(x, y), bx = cv.tx(x + w, y + h), by = cv.ty(x + w, y + h);
		float x0 = Math.min(ax, bx), y0 = Math.min(ay, by), x1 = Math.max(ax, bx), y1 = Math.max(ay, by);
		if (clip != null) {
			// scrolled out of sight: only the visible part can be clicked
			x0 = Math.max(x0, clip[0]);
			y0 = Math.max(y0, clip[1]);
			x1 = Math.min(x1, clip[2]);
			y1 = Math.min(y1, clip[3]);
			if (x1 <= x0 || y1 <= y0) return;
		}
		Hit hit = new Hit(x0, y0, x1, y1, left, right, input);
		hits.add(hit);
		if (hit.contains(mouseX, mouseY)) {
			if (input != null) wantText = true;
			else if (left != null || right != null) wantPointer = true;
		}
	}

	// ------------------------------------------------------------ widgets

	public void card(float x, float y, float w, float h) {
		cv.fillRoundRect(x, y, w, h, 12, Colors.white(0.035f));
		cv.borderRoundRect(x, y, w, h, 12, 1, Colors.white(0.07f));
	}

	/** Page header: accent icon box and a title, vertically centred on a 38px row. */
	public void header(String icon, String title, float x, float y) {
		cv.fillRoundRect(x, y, 38, 38, 11, Colors.accent(0.2f));
		cv.borderRoundRect(x, y, 38, 38, 11, 1, Colors.accent(0.6f));
		icons.draw(cv, icon, x + 10, y + 10, 18, 1.8f, Colors.WHITE);
		text.draw(cv, title, x + 50, text.baselineFor(H2, y + 19), H2, Colors.TEXT);
	}

	public float buttonWidth(String label, @Nullable String icon, Style st) {
		float w = 2 + 12 * 2 + text.width(label, st);
		if (icon != null) w += 15 + 7;
		return w;
	}

	/** A pill-ish button. Returns its width. */
	public float button(String key, float x, float y, float h, String label, @Nullable String icon, Btn kind, boolean enabled, Runnable onClick) {
		float w = buttonWidth(label, icon, BUTTON);
		boolean hov = enabled && hovered(x, y, w, h);
		int bg, border, fg;
		switch (kind) {
			case ACCENT -> {
				bg = hov ? Colors.ACCENT : Colors.accent(0.85f);
				border = Colors.accent(0.0f);
				fg = Colors.WHITE;
			}
			case DANGER -> {
				bg = Colors.rgba(0xFF5A5A, hov ? 0.26f : 0.16f);
				border = Colors.rgba(0xFF5A5A, 0.5f);
				fg = 0xFFFF9A9A;
			}
			default -> {
				bg = Colors.white(hov ? 0.1f : 0.06f);
				border = Colors.white(0.1f);
				fg = Colors.TEXT;
			}
		}
		ColorAnim bgAnim = color(key + "#bg", bg);
		bgAnim.set(bg, now, 150, Ease.EASE);
		cv.push();
		if (!enabled) cv.mulAlpha(0.4f);
		cv.fillRoundRect(x, y, w, h, Math.min(9, h / 2), bgAnim.get(now));
		if ((border >>> 24) != 0) cv.borderRoundRect(x, y, w, h, Math.min(9, h / 2), 1, border);
		float tx = x + 1 + 12;
		if (icon != null) {
			icons.draw(cv, icon, tx, y + h / 2 - 7.5f, 15, 1.8f, fg);
			tx += 15 + 7;
		}
		text.draw(cv, label, tx, text.baselineFor(BUTTON, y + h / 2), BUTTON, fg);
		cv.pop();
		if (enabled) hit(x, y, w, h, onClick, null);
		return w;
	}

	/** Square icon-only button. */
	public void iconButton(String key, float x, float y, float size, String icon, float iconSize, int color, int hoverColor, Runnable onClick) {
		boolean hov = hovered(x, y, size, size);
		ColorAnim bg = color(key + "#ibg", 0);
		bg.set(hov ? Colors.white(0.08f) : Colors.white(0f), now, 150, Ease.EASE);
		cv.fillRoundRect(x, y, size, size, Math.min(9, size / 2), bg.get(now));
		icons.draw(cv, icon, x + (size - iconSize) / 2, y + (size - iconSize) / 2, iconSize, 1.8f, hov ? hoverColor : color);
		hit(x, y, size, size, onClick, null);
	}

	/** A single-line text field. */
	public void input(TextInput in, float x, float y, float w, float h, String placeholder, @Nullable String icon) {
		input(in, x, y, w, h, placeholder, icon, null);
	}

	/** A single-line text field with an optional status message shown at its right edge. */
	public void input(TextInput in, float x, float y, float w, float h, String placeholder, @Nullable String icon, @Nullable String status) {
		boolean focus = focused == in && interactive;
		boolean hov = hovered(x, y, w, h);
		ColorAnim border = color("input#" + System.identityHashCode(in), Colors.white(0.1f));
		border.set(focus ? Colors.accent(0.6f) : hov ? Colors.white(0.16f) : Colors.white(0.1f), now, 150, Ease.EASE);
		cv.fillRoundRect(x, y, w, h, 11, Colors.white(focus ? 0.07f : 0.05f));
		cv.borderRoundRect(x, y, w, h, 11, 1, border.get(now));
		float tx = x + 14;
		if (icon != null) {
			icons.draw(cv, icon, tx, y + h / 2 - 8, 16, 2, focus ? Colors.TEXT : Colors.TEXT_HINT);
			tx += 16 + 10;
		}
		float right = x + w - 14;
		float base = text.baselineFor(INPUT, y + h / 2);
		if (status != null && !in.value().isEmpty()) {
			float sw = text.width(status, SMALL);
			text.draw(cv, status, right - sw, text.baselineFor(SMALL, y + h / 2), SMALL, 0xFFFF9A9A);
			right -= sw + 12;
		}
		String v = in.value();
		if (v.isEmpty()) {
			text.draw(cv, placeholder, tx, base, INPUT, Colors.TEXT_HINT);
		}
		float caretX = tx + text.width(v.substring(0, in.caret()), INPUT);
		float scroll = Math.max(0, caretX - right + 2);
		cv.push();
		cv.clipRect(tx - 1, y, right + 1, y + h);
		if (in.allSelected()) {
			float sw = text.width(v, INPUT);
			cv.fillRoundRect(tx - scroll - 1, y + h / 2 - 10, sw + 2, 20, 4, Colors.accent(0.45f));
		}
		if (!v.isEmpty()) text.draw(cv, v, tx - scroll, base, INPUT, Colors.TEXT);
		if (focus && !in.allSelected()) {
			double since = now - in.lastActivity;
			boolean on = since < 500 || ((long) ((since - 500) / 530)) % 2 == 1;
			if (on) cv.fillRect(caretX - scroll, y + h / 2 - 9, caretX - scroll + 1.5f, y + h / 2 + 9, Colors.WHITE);
		}
		cv.pop();
		hit(x, y, w, h, () -> focused = in, null, in);
	}

	/** A switch; {@code t} is 0 (off) to 1 (on). Small: 26x14, big: 34x20. */
	public void switchToggle(float x, float y, boolean big, float t) {
		float w = big ? 34 : 26, h = big ? 20 : 14, knob = big ? 14 : 10;
		float inset = big ? 2 : 1;
		float offL = 1 + inset, onL = 1 + (big ? 16 : 13);
		cv.fillRoundRect(x, y, w, h, h / 2, Colors.lerp(Colors.white(0.08f), Colors.ACCENT, t));
		cv.borderRoundRect(x, y, w, h, h / 2, 1, Colors.lerp(Colors.white(0.16f), Colors.ACCENT, t));
		float kx = x + offL + (onL - offL) * t;
		cv.fillCircle(kx + knob / 2, y + 1 + inset + knob / 2, knob / 2, Colors.lerp(Colors.KNOB_OFF, Colors.WHITE, t));
	}

	/** Small caps section label. */
	public void caps(String label, float x, float y) {
		text.draw(cv, label, x, y + text.ascent(CAPS), CAPS, Colors.TEXT_HINT);
	}

	public void centred(String s, Style st, float x, float y, float w, float h, int color) {
		text.draw(cv, s, x + (w - text.width(s, st)) / 2, text.baselineFor(st, y + h / 2), st, color);
	}

	// ------------------------------------------------------------ text layout

	private record Wrapped(List<String> lines, boolean cut) {
	}

	private final Map<String, List<String>> wrapCache = new LinkedHashMap<>(256, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, List<String>> eldest) {
			return size() > 512;
		}
	};

	/**
	 * Breaks {@code s} into lines no wider than {@code maxW}, balanced so the lines are of similar
	 * length (no single word left on the last line). The last allowed line ends in an ellipsis if cut.
	 */
	public List<String> wrap(String s, Style st, float maxW, int maxLines) {
		String key = s + '|' + st + '|' + maxW + '|' + maxLines + (text.vanilla() ? "v" : "s");
		List<String> cached = wrapCache.get(key);
		if (cached != null) return cached;
		Wrapped w = greedy(s, st, maxW, maxLines);
		if (w.lines().size() >= 2 && !w.cut()) {
			float lo = maxW * 0.4f, hi = maxW;
			for (int i = 0; i < 8; i++) {
				float mid = (lo + hi) / 2;
				Wrapped t = greedy(s, st, mid, maxLines);
				if (t.lines().size() == w.lines().size() && !t.cut()) hi = mid;
				else lo = mid;
			}
			w = greedy(s, st, hi, maxLines);
		}
		wrapCache.put(key, w.lines());
		return w.lines();
	}

	private Wrapped greedy(String s, Style st, float maxW, int maxLines) {
		List<String> lines = new ArrayList<>();
		String[] words = s.split(" ");
		StringBuilder line = new StringBuilder();
		for (int i = 0; i < words.length; i++) {
			String candidate = line.isEmpty() ? words[i] : line + " " + words[i];
			if (text.width(candidate, st) <= maxW || line.isEmpty()) {
				line.setLength(0);
				line.append(candidate);
				continue;
			}
			if (lines.size() == maxLines - 1) {
				StringBuilder rest = new StringBuilder(line);
				for (int j = i; j < words.length; j++) rest.append(' ').append(words[j]);
				lines.add(text.fit(rest.toString(), st, maxW));
				return new Wrapped(lines, true);
			}
			lines.add(line.toString());
			line.setLength(0);
			line.append(words[i]);
		}
		boolean cut = false;
		if (!line.isEmpty()) {
			String last = line.toString(), fitted = text.fit(last, st, maxW);
			cut = !fitted.equals(last);
			lines.add(fitted);
		}
		return new Wrapped(lines, cut);
	}

	/** Draws wrapped text from the top of its first line; returns the height used. */
	public float paragraph(String s, Style st, float x, float y, float maxW, int maxLines, int color) {
		List<String> lines = wrap(s, st, maxW, maxLines);
		float lh = text.lineHeight(st);
		for (int i = 0; i < lines.size(); i++) text.draw(cv, lines.get(i), x, y + i * lh + text.ascent(st), st, color);
		return lines.size() * lh;
	}

	public float paragraphHeight(String s, Style st, float maxW, int maxLines) {
		return wrap(s, st, maxW, maxLines).size() * text.lineHeight(st);
	}

	// --------------------------------------------------------------- tooltips

	/** Shows {@code tip} as a tooltip while the rectangle is hovered. */
	public void tip(float x, float y, float w, float h, @Nullable String tip) {
		if (tip == null || tip.isEmpty() || !hovered(x, y, w, h)) return;
		tipText = tip;
	}

	// ------------------------------------------------------------ small parts

	public float badgeWidth(String label) {
		return text.width(label, BADGE) + 12;
	}

	/** A small rounded tag, for example "LeoneMC" on a module card. */
	public float badge(String label, float x, float centreY, int color, boolean strong) {
		float w = badgeWidth(label), h = 18;
		cv.fillRoundRect(x, centreY - h / 2, w, h, 9, Colors.rgba(color, strong ? 0.22f : 0.08f));
		cv.borderRoundRect(x, centreY - h / 2, w, h, 9, 1, Colors.rgba(color, strong ? 0.55f : 0.18f));
		text.draw(cv, label, x + 6, text.baselineFor(BADGE, centreY), BADGE, strong ? Colors.WHITE : Colors.TEXT_HINT);
		return w;
	}

	public float chipWidth(String label) {
		return 1 + 12 + text.width(label, BUTTON) + 12 + 1;
	}

	/** A selectable pill. */
	public void chip(String key, String label, float x, float y, boolean selected, Runnable onClick) {
		float w = chipWidth(label);
		boolean hov = hovered(x, y, w, 28);
		ColorAnim bg = color(key + "#chip", 0);
		bg.set(selected ? Colors.accent(hov ? 0.28f : 0.2f) : Colors.white(hov ? 0.08f : 0.04f), now, 150, Ease.EASE);
		cv.fillRoundRect(x, y, w, 28, 14, bg.get(now));
		cv.borderRoundRect(x, y, w, 28, 14, 1, selected ? Colors.accent(0.6f) : Colors.white(hov ? 0.16f : 0.1f));
		text.draw(cv, label, x + 13, text.baselineFor(BUTTON, y + 14), BUTTON, selected ? Colors.WHITE : Colors.TEXT_MUTED);
		hit(x, y, w, 28, onClick, null);
	}
}
