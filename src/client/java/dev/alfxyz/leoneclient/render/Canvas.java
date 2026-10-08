package dev.alfxyz.leoneclient.render;

import java.util.Arrays;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.renderer.RenderPipelines;

/**
 * Immediate-mode vector canvas. Shapes are tessellated on the CPU with a one
 * device-pixel feathered edge for anti-aliasing, transformed by a 2D affine
 * stack, optionally clipped to a rectangle, and collected into a single batch
 * that is submitted to the GUI render state as one element. Everything samples
 * the shared {@link Atlas}; untextured geometry samples its white block.
 */
public final class Canvas {
	public interface PolarField {
		/** Alpha multiplier at radius {@code r} and angle {@code theta} (radians, clockwise from +x). */
		float alpha(float r, float theta);
	}

	public interface PlaneField {
		float alpha(float x, float y);
	}

	private final Atlas atlas;
	private float[] data = new float[4 * 32768];
	private int[] colors = new int[32768];
	private int verts;
	private float minX, minY, maxX, maxY;

	// x' = a*x + c*y + e ; y' = b*x + d*y + f
	private float a = 1, b, c, d = 1, e, f;
	private float alpha = 1;
	private boolean clipping;
	private float cx0, cy0, cx1, cy1;
	private final float[] stack = new float[12 * 64];
	private int depth;

	private float guiScale = 1;
	private float wu, wv;

	public Canvas(Atlas atlas) {
		this.atlas = atlas;
	}

	public Atlas atlas() {
		return atlas;
	}

	public void begin(float guiScale) {
		this.guiScale = guiScale;
		this.verts = 0;
		this.a = 1; this.b = 0; this.c = 0; this.d = 1; this.e = 0; this.f = 0;
		this.alpha = 1;
		this.clipping = false;
		this.depth = 0;
		this.wu = atlas.whiteU;
		this.wv = atlas.whiteV;
		resetBounds();
	}

	private void resetBounds() {
		minX = minY = Float.POSITIVE_INFINITY;
		maxX = maxY = Float.NEGATIVE_INFINITY;
	}

	/** Submits everything drawn since the last flush as one GUI element. */
	public void flush(GuiGraphicsExtractor graphics) {
		if (verts == 0) return;
		int x0 = (int) Math.floor(Math.max(0, minX));
		int y0 = (int) Math.floor(Math.max(0, minY));
		int x1 = (int) Math.ceil(Math.min(graphics.guiWidth(), maxX));
		int y1 = (int) Math.ceil(Math.min(graphics.guiHeight(), maxY));
		if (x1 > x0 && y1 > y0) {
			ScreenRectangle bounds = new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
			ScreenRectangle scissor = graphics.scissorStack.peek();
			graphics.guiRenderState.addGuiElement(new CanvasElement(
				RenderPipelines.GUI_TEXTURED,
				atlas.textureSetup(),
				Arrays.copyOf(data, verts * 4),
				Arrays.copyOf(colors, verts),
				verts,
				scissor,
				scissor != null ? scissor.intersection(bounds) : bounds
			));
		}
		verts = 0;
		resetBounds();
	}

	// ------------------------------------------------------------------ state

	public void push() {
		if (depth >= 64) throw new IllegalStateException("Canvas stack overflow");
		int o = depth * 12;
		stack[o] = a; stack[o + 1] = b; stack[o + 2] = c; stack[o + 3] = d; stack[o + 4] = e; stack[o + 5] = f;
		stack[o + 6] = alpha;
		stack[o + 7] = clipping ? 1 : 0;
		stack[o + 8] = cx0; stack[o + 9] = cy0; stack[o + 10] = cx1; stack[o + 11] = cy1;
		depth++;
	}

	public void pop() {
		depth--;
		int o = depth * 12;
		a = stack[o]; b = stack[o + 1]; c = stack[o + 2]; d = stack[o + 3]; e = stack[o + 4]; f = stack[o + 5];
		alpha = stack[o + 6];
		clipping = stack[o + 7] != 0;
		cx0 = stack[o + 8]; cy0 = stack[o + 9]; cx1 = stack[o + 10]; cy1 = stack[o + 11];
	}

	public void translate(float x, float y) {
		e += a * x + c * y;
		f += b * x + d * y;
	}

	public void scale(float s) {
		scale(s, s);
	}

	public void scale(float sx, float sy) {
		a *= sx; b *= sx;
		c *= sy; d *= sy;
	}

	/** Rotates clockwise (CSS convention) by {@code deg} degrees. */
	public void rotate(float deg) {
		if (deg == 0) return;
		double r = Math.toRadians(deg);
		float cos = (float) Math.cos(r), sin = (float) Math.sin(r);
		float na = a * cos + c * sin, nb = b * cos + d * sin;
		float nc = -a * sin + c * cos, nd = -b * sin + d * cos;
		a = na; b = nb; c = nc; d = nd;
	}

	public void rotateAround(float deg, float px, float py) {
		if (deg == 0) return;
		translate(px, py);
		rotate(deg);
		translate(-px, -py);
	}

	public void scaleAround(float s, float px, float py) {
		if (s == 1) return;
		translate(px, py);
		scale(s);
		translate(-px, -py);
	}

	public void mulAlpha(float m) {
		alpha *= Math.max(0, Math.min(1, m));
	}

	public float alpha() {
		return alpha;
	}

	/** Restricts drawing to a rectangle given in local coordinates (axis-aligned in GUI space). */
	public void clipRect(float x0, float y0, float x1, float y1) {
		float ax = tx(x0, y0), ay = ty(x0, y0), bx = tx(x1, y1), by = ty(x1, y1);
		float nx0 = Math.min(ax, bx), ny0 = Math.min(ay, by), nx1 = Math.max(ax, bx), ny1 = Math.max(ay, by);
		if (clipping) {
			nx0 = Math.max(nx0, cx0); ny0 = Math.max(ny0, cy0);
			nx1 = Math.min(nx1, cx1); ny1 = Math.min(ny1, cy1);
		}
		clipping = true;
		cx0 = nx0; cy0 = ny0; cx1 = nx1; cy1 = ny1;
	}

	/** Current transform as {a, b, c, d, e, f}. */
	public float[] matrix() {
		return new float[] {a, b, c, d, e, f};
	}

	public float tx(float x, float y) {
		return a * x + c * y + e;
	}

	public float ty(float x, float y) {
		return b * x + d * y + f;
	}

	/** Uniform scale of the current transform (local unit to GUI unit). */
	public float currentScale() {
		return (float) Math.sqrt(Math.abs(a * d - b * c));
	}

	/** Size of one device pixel in local units. */
	public float px() {
		return 1f / (guiScale * currentScale());
	}

	public float guiScale() {
		return guiScale;
	}

	public boolean axisAligned() {
		return Math.abs(b) < 1e-5f && Math.abs(c) < 1e-5f;
	}

	/** Inverse-transforms a GUI point into local coordinates. */
	public float[] toLocal(float gx, float gy) {
		float det = a * d - b * c;
		float x = gx - e, y = gy - f;
		return new float[] {(d * x - c * y) / det, (-b * x + a * y) / det};
	}

	public int withAlpha(int argb) {
		if (alpha >= 1) return argb;
		int al = Math.round(((argb >>> 24) & 0xFF) * alpha);
		return (al << 24) | (argb & 0xFFFFFF);
	}

	// ------------------------------------------------------------ emission

	private final float[] qx = new float[4], qy = new float[4], qu = new float[4], qv = new float[4];
	private final int[] qc = new int[4];

	private void ensure(int more) {
		if (verts + more > colors.length) {
			int n = Math.max(colors.length * 2, verts + more);
			colors = Arrays.copyOf(colors, n);
			data = Arrays.copyOf(data, n * 4);
		}
	}

	private void put(float x, float y, float u, float v, int col) {
		int o = verts * 4;
		data[o] = x; data[o + 1] = y; data[o + 2] = u; data[o + 3] = v;
		colors[verts] = col;
		verts++;
		if (x < minX) minX = x;
		if (x > maxX) maxX = x;
		if (y < minY) minY = y;
		if (y > maxY) maxY = y;
	}

	/** Emits the quad held in qx..qc (already in GUI space, colours already alpha-scaled). */
	private void emitQ() {
		if ((qc[0] | qc[1] | qc[2] | qc[3]) >>> 24 == 0) return;
		// GUI pipelines cull back faces: wind every quad like vanilla's (negative shoelace area, y down)
		float area = 0;
		for (int i = 0; i < 4; i++) {
			int j = (i + 1) & 3;
			area += qx[i] * qy[j] - qx[j] * qy[i];
		}
		if (area > 0) {
			swapQ(1, 3);
		}
		if (!clipping || inside(0) && inside(1) && inside(2) && inside(3)) {
			ensure(4);
			for (int i = 0; i < 4; i++) put(qx[i], qy[i], qu[i], qv[i], qc[i]);
			return;
		}
		clipAndEmit();
	}

	private void swapQ(int i, int j) {
		float t = qx[i]; qx[i] = qx[j]; qx[j] = t;
		t = qy[i]; qy[i] = qy[j]; qy[j] = t;
		t = qu[i]; qu[i] = qu[j]; qu[j] = t;
		t = qv[i]; qv[i] = qv[j]; qv[j] = t;
		int c = qc[i]; qc[i] = qc[j]; qc[j] = c;
	}

	private boolean inside(int i) {
		return qx[i] >= cx0 && qx[i] <= cx1 && qy[i] >= cy0 && qy[i] <= cy1;
	}

	// polygon clipping scratch: x, y, u, v, r, g, b, a per vertex
	private float[] pa = new float[8 * 16], pb = new float[8 * 16];

	private void clipAndEmit() {
		int n = 4;
		for (int i = 0; i < 4; i++) {
			int o = i * 8, col = qc[i];
			pa[o] = qx[i]; pa[o + 1] = qy[i]; pa[o + 2] = qu[i]; pa[o + 3] = qv[i];
			pa[o + 4] = (col >> 16) & 0xFF; pa[o + 5] = (col >> 8) & 0xFF; pa[o + 6] = col & 0xFF; pa[o + 7] = (col >>> 24) & 0xFF;
		}
		n = clipPlane(pa, n, pb, 0, cx0, false); if (n == 0) return;
		n = clipPlane(pb, n, pa, 0, cx1, true); if (n == 0) return;
		n = clipPlane(pa, n, pb, 1, cy0, false); if (n == 0) return;
		n = clipPlane(pb, n, pa, 1, cy1, true); if (n < 3) return;
		ensure(((n - 1) / 2) * 4 + 4);
		int i = 1;
		while (i + 2 < n) {
			putP(pa, 0); putP(pa, i); putP(pa, i + 1); putP(pa, i + 2);
			i += 2;
		}
		if (i + 1 < n) {
			putP(pa, 0); putP(pa, i); putP(pa, i + 1); putP(pa, i + 1);
		}
	}

	private void putP(float[] p, int i) {
		int o = i * 8;
		int col = (Math.round(p[o + 7]) << 24) | (Math.round(p[o + 4]) << 16) | (Math.round(p[o + 5]) << 8) | Math.round(p[o + 6]);
		put(p[o], p[o + 1], p[o + 2], p[o + 3], col);
	}

	private static int clipPlane(float[] in, int n, float[] out, int axis, float bound, boolean max) {
		int m = 0;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			int oi = i * 8, oj = j * 8;
			float vi = in[oi + axis], vj = in[oj + axis];
			boolean ini = max ? vi <= bound : vi >= bound;
			boolean inj = max ? vj <= bound : vj >= bound;
			if (ini) {
				System.arraycopy(in, oi, out, m * 8, 8);
				m++;
			}
			if (ini != inj) {
				float t = (bound - vi) / (vj - vi);
				int oo = m * 8;
				for (int k = 0; k < 8; k++) out[oo + k] = in[oi + k] + (in[oj + k] - in[oi + k]) * t;
				m++;
			}
		}
		return m;
	}

	// ---------------------------------------------------------- primitives

	/** Untextured quad, vertices in local space, colours ARGB (global alpha applied here). */
	public void quad(float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3, int c0, int c1, int c2, int c3) {
		qx[0] = tx(x0, y0); qy[0] = ty(x0, y0);
		qx[1] = tx(x1, y1); qy[1] = ty(x1, y1);
		qx[2] = tx(x2, y2); qy[2] = ty(x2, y2);
		qx[3] = tx(x3, y3); qy[3] = ty(x3, y3);
		for (int i = 0; i < 4; i++) { qu[i] = wu; qv[i] = wv; }
		qc[0] = withAlpha(c0); qc[1] = withAlpha(c1); qc[2] = withAlpha(c2); qc[3] = withAlpha(c3);
		emitQ();
	}

	public void tri(float x0, float y0, float x1, float y1, float x2, float y2, int c0, int c1, int c2) {
		quad(x0, y0, x1, y1, x2, y2, x2, y2, c0, c1, c2, c2);
	}

	/** Textured, axis-aligned (in local space) quad. */
	public void texQuad(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {
		int col = withAlpha(color);
		qx[0] = tx(x0, y0); qy[0] = ty(x0, y0); qu[0] = u0; qv[0] = v0;
		qx[1] = tx(x0, y1); qy[1] = ty(x0, y1); qu[1] = u0; qv[1] = v1;
		qx[2] = tx(x1, y1); qy[2] = ty(x1, y1); qu[2] = u1; qv[2] = v1;
		qx[3] = tx(x1, y0); qy[3] = ty(x1, y0); qu[3] = u1; qv[3] = v0;
		qc[0] = qc[1] = qc[2] = qc[3] = col;
		emitQ();
	}

	/** Textured quad given directly in GUI space (used for pixel-snapped text). */
	public void texQuadGui(float x0, float y0, float x1, float y1, float u0, float v0, float u1, float v1, int color) {
		int col = withAlpha(color);
		qx[0] = x0; qy[0] = y0; qu[0] = u0; qv[0] = v0;
		qx[1] = x0; qy[1] = y1; qu[1] = u0; qv[1] = v1;
		qx[2] = x1; qy[2] = y1; qu[2] = u1; qv[2] = v1;
		qx[3] = x1; qy[3] = y0; qu[3] = u1; qv[3] = v0;
		qc[0] = qc[1] = qc[2] = qc[3] = col;
		emitQ();
	}

	public void image(Atlas.Region r, float x, float y, float w, float h, int color) {
		if (r == null || r.w() == 0) return;
		texQuad(x, y, x + w, y + h, r.u0(), r.v0(), r.u1(), r.v1(), color);
	}

	/** Hard-edged rectangle (no anti-aliasing). */
	public void fillRect(float x0, float y0, float x1, float y1, int color) {
		quad(x0, y0, x0, y1, x1, y1, x1, y0, color, color, color, color);
	}

	// --------------------------------------------------- anti-aliased fills

	private static int transparent(int col) {
		return col & 0x00FFFFFF;
	}

	private static int scaleAlpha(int col, float m) {
		int al = Math.round(((col >>> 24) & 0xFF) * Math.max(0, Math.min(1, m)));
		return (al << 24) | (col & 0xFFFFFF);
	}

	private float[] ox = new float[256], oy = new float[256];

	private void ensureOffsets(int n) {
		if (ox.length < n) {
			ox = new float[n * 2];
			oy = new float[n * 2];
		}
	}

	/** Computes outward miter offsets (unit edge distance) for a closed loop into ox/oy. */
	private void loopOffsets(float[] px, float[] py, int n) {
		ensureOffsets(n);
		double area = 0;
		for (int i = 0; i < n; i++) {
			int j = (i + 1) % n;
			area += px[i] * py[j] - px[j] * py[i];
		}
		float sign = area >= 0 ? 1 : -1;
		for (int i = 0; i < n; i++) {
			int ip = (i + n - 1) % n, in = (i + 1) % n;
			float n1x = (py[i] - py[ip]) * sign, n1y = -(px[i] - px[ip]) * sign;
			float n2x = (py[in] - py[i]) * sign, n2y = -(px[in] - px[i]) * sign;
			float l1 = (float) Math.hypot(n1x, n1y), l2 = (float) Math.hypot(n2x, n2y);
			if (l1 > 0) { n1x /= l1; n1y /= l1; }
			if (l2 > 0) { n2x /= l2; n2y /= l2; }
			if (l1 == 0) { n1x = n2x; n1y = n2y; }
			if (l2 == 0) { n2x = n1x; n2y = n1y; }
			float mx = n1x + n2x, my = n1y + n2y;
			float ml = (float) Math.hypot(mx, my);
			if (ml < 1e-6f) { mx = n1x; my = n1y; ml = 1; }
			mx /= ml; my /= ml;
			float dot = Math.max(0.25f, mx * n1x + my * n1y);
			ox[i] = mx / dot;
			oy[i] = my / dot;
		}
	}

	/** Anti-aliased convex polygon. */
	public void fillConvex(float[] px, float[] py, int n, int color) {
		if (n < 3) return;
		float h = px() / 2;
		loopOffsets(px, py, n);
		float[] ix = new float[n], iy = new float[n];
		float cxs = 0, cys = 0;
		for (int i = 0; i < n; i++) {
			ix[i] = px[i] - ox[i] * h;
			iy[i] = py[i] - oy[i] * h;
			cxs += ix[i];
			cys += iy[i];
		}
		cxs /= n;
		cys /= n;
		for (int i = 0; i + 1 < n; i += 2) {
			int i3 = (i + 2) % n;
			quad(cxs, cys, ix[i], iy[i], ix[i + 1], iy[i + 1], ix[i3], iy[i3], color, color, color, color);
		}
		if (n % 2 == 1) tri(cxs, cys, ix[n - 1], iy[n - 1], ix[0], iy[0], color, color, color);
		int t = transparent(color);
		for (int k = 0; k < n; k++) {
			int k2 = (k + 1) % n;
			quad(ix[k], iy[k], px[k] + ox[k] * h, py[k] + oy[k] * h, px[k2] + ox[k2] * h, py[k2] + oy[k2] * h, ix[k2], iy[k2], color, t, t, color);
		}
	}

	/**
	 * Anti-aliased band between two rails of equal length (e.g. a ring sector:
	 * outer arc and inner arc, both in the same angular direction).
	 */
	public void fillStrip(float[] ax, float[] ay, float[] bx, float[] by, int n, int color) {
		int m = n * 2;
		float[] lx = new float[m], ly = new float[m];
		for (int i = 0; i < n; i++) {
			lx[i] = ax[i]; ly[i] = ay[i];
			lx[m - 1 - i] = bx[i]; ly[m - 1 - i] = by[i];
		}
		float h = px() / 2;
		loopOffsets(lx, ly, m);
		float[] ix = new float[m], iy = new float[m];
		for (int i = 0; i < m; i++) {
			ix[i] = lx[i] - ox[i] * h;
			iy[i] = ly[i] - oy[i] * h;
		}
		for (int i = 0; i + 1 < n; i++) {
			int a0 = i, a1 = i + 1, b0 = m - 1 - i, b1 = m - 2 - i;
			quad(ix[a0], iy[a0], ix[a1], iy[a1], ix[b1], iy[b1], ix[b0], iy[b0], color, color, color, color);
		}
		int t = transparent(color);
		for (int k = 0; k < m; k++) {
			int k2 = (k + 1) % m;
			quad(ix[k], iy[k], lx[k] + ox[k] * h, ly[k] + oy[k] * h, lx[k2] + ox[k2] * h, ly[k2] + oy[k2] * h, ix[k2], iy[k2], color, t, t, color);
		}
	}

	/** Anti-aliased stroke centred on a polyline, with miter joins and butt caps. */
	public void stroke(float[] px, float[] py, int n, boolean closed, float width, int color) {
		if (n < 2) return;
		float aa = px();
		float hw = width / 2;
		float[] off;
		float[] al;
		if (width >= aa) {
			off = new float[] {-hw - aa / 2, -hw + aa / 2, hw - aa / 2, hw + aa / 2};
			al = new float[] {0, 1, 1, 0};
		} else {
			float e2 = hw + aa / 2;
			off = new float[] {-e2, 0, e2};
			al = new float[] {0, 2 * width / (width + aa), 0};
		}
		ensureOffsets(n);
		for (int i = 0; i < n; i++) {
			boolean hasPrev = closed || i > 0, hasNext = closed || i < n - 1;
			int ip = (i + n - 1) % n, in = (i + 1) % n;
			float n1x = 0, n1y = 0, n2x = 0, n2y = 0;
			if (hasPrev) {
				float dx = px[i] - px[ip], dy = py[i] - py[ip], l = (float) Math.hypot(dx, dy);
				if (l > 0) { n1x = dy / l; n1y = -dx / l; }
			}
			if (hasNext) {
				float dx = px[in] - px[i], dy = py[in] - py[i], l = (float) Math.hypot(dx, dy);
				if (l > 0) { n2x = dy / l; n2y = -dx / l; }
			}
			if (!hasPrev || n1x == 0 && n1y == 0) { n1x = n2x; n1y = n2y; }
			if (!hasNext || n2x == 0 && n2y == 0) { n2x = n1x; n2y = n1y; }
			float mx = n1x + n2x, my = n1y + n2y, ml = (float) Math.hypot(mx, my);
			if (ml < 1e-6f) { mx = n1x; my = n1y; ml = 1; }
			mx /= ml; my /= ml;
			float dot = Math.max(0.25f, mx * n1x + my * n1y);
			ox[i] = mx / dot;
			oy[i] = my / dot;
		}
		int segs = closed ? n : n - 1;
		for (int r = 0; r + 1 < off.length; r++) {
			int c0 = scaleAlpha(color, al[r]), c1 = scaleAlpha(color, al[r + 1]);
			float d0 = off[r], d1 = off[r + 1];
			for (int s = 0; s < segs; s++) {
				int i = s, j = (s + 1) % n;
				quad(px[i] + ox[i] * d0, py[i] + oy[i] * d0, px[j] + ox[j] * d0, py[j] + oy[j] * d0,
					px[j] + ox[j] * d1, py[j] + oy[j] * d1, px[i] + ox[i] * d1, py[i] + oy[i] * d1,
					c0, c0, c1, c1);
			}
		}
	}

	/** Dashed stroke along a polyline. */
	public void strokeDashed(float[] px, float[] py, int n, boolean closed, float width, float dash, float gap, int color) {
		int segs = closed ? n : n - 1;
		float[] sx = new float[n + 2], sy = new float[n + 2];
		float pos = 0;
		boolean on = true;
		float left = dash;
		int cnt = 0;
		sx[cnt] = px[0]; sy[cnt] = py[0]; cnt++;
		for (int s = 0; s < segs; s++) {
			int j = (s + 1) % n;
			float x0 = px[s], y0 = py[s], x1 = px[j], y1 = py[j];
			float len = (float) Math.hypot(x1 - x0, y1 - y0), t = 0;
			while (len - t > left) {
				t += left;
				float x = x0 + (x1 - x0) * t / len, y = y0 + (y1 - y0) * t / len;
				if (on) {
					if (cnt + 1 >= sx.length) { sx = Arrays.copyOf(sx, sx.length * 2); sy = Arrays.copyOf(sy, sy.length * 2); }
					sx[cnt] = x; sy[cnt] = y; cnt++;
					stroke(sx, sy, cnt, false, width, color);
				}
				on = !on;
				left = on ? dash : gap;
				cnt = 0;
				if (on) { sx[0] = x; sy[0] = y; cnt = 1; }
			}
			left -= len - t;
			if (on) {
				if (cnt + 1 >= sx.length) { sx = Arrays.copyOf(sx, sx.length * 2); sy = Arrays.copyOf(sy, sy.length * 2); }
				sx[cnt] = x1; sy[cnt] = y1; cnt++;
			}
			pos += len;
		}
		if (on && cnt > 1) stroke(sx, sy, cnt, false, width, color);
	}

	// ------------------------------------------------------------- circles

	private int segmentsFor(float radius, float sweep) {
		float rpx = radius / px();
		int n = (int) Math.ceil(Math.abs(sweep) * rpx / 3.0);
		return Math.max(8, Math.min(720, n));
	}

	/**
	 * Concentric bands between successive radii, each radius carrying an alpha
	 * multiplier; angles in radians clockwise from +x.
	 */
	public void bands(float cx, float cy, float[] radii, float[] alphas, float a0, float a1, int color) {
		int segs = segmentsFor(radii[radii.length - 1], a1 - a0);
		float[] cs = new float[segs + 1], sn = new float[segs + 1];
		for (int s = 0; s <= segs; s++) {
			double t = a0 + (a1 - a0) * s / (double) segs;
			cs[s] = (float) Math.cos(t);
			sn[s] = (float) Math.sin(t);
		}
		for (int k = 0; k + 1 < radii.length; k++) {
			float r0 = Math.max(0, radii[k]), r1 = Math.max(0, radii[k + 1]);
			int c0 = scaleAlpha(color, alphas[k]), c1 = scaleAlpha(color, alphas[k + 1]);
			if ((c0 | c1) >>> 24 == 0) continue;
			for (int s = 0; s < segs; s++) {
				quad(cx + cs[s] * r0, cy + sn[s] * r0, cx + cs[s + 1] * r0, cy + sn[s + 1] * r0,
					cx + cs[s + 1] * r1, cy + sn[s + 1] * r1, cx + cs[s] * r1, cy + sn[s] * r1,
					c0, c0, c1, c1);
			}
		}
	}

	/** Evenly spaced angles from a0 to a1 (radians), at least {@code minSegs} segments. */
	public float[] angles(float radius, float a0, float a1, int minSegs) {
		int segs = Math.max(minSegs, segmentsFor(radius, a1 - a0) / 2);
		float[] t = new float[segs + 1];
		for (int s = 0; s <= segs; s++) t[s] = a0 + (a1 - a0) * s / (float) segs;
		return t;
	}

	/** Mesh over radii x angles whose per-vertex alpha comes from a field. */
	public void polar(float cx, float cy, float[] radii, float a0, float a1, int minSegs, PolarField field, int color) {
		polar(cx, cy, radii, angles(radii[radii.length - 1], a0, a1, minSegs), field, color);
	}

	public void polar(float cx, float cy, float[] radii, float[] thetas, PolarField field, int color) {
		int segs = thetas.length - 1;
		int nr = radii.length;
		float[] al = new float[nr * (segs + 1)];
		float[] cs = new float[segs + 1], sn = new float[segs + 1];
		for (int s = 0; s <= segs; s++) {
			float t = thetas[s];
			cs[s] = (float) Math.cos(t);
			sn[s] = (float) Math.sin(t);
			for (int k = 0; k < nr; k++) al[k * (segs + 1) + s] = field.alpha(radii[k], t);
		}
		for (int k = 0; k + 1 < nr; k++) {
			float r0 = Math.max(0, radii[k]), r1 = Math.max(0, radii[k + 1]);
			for (int s = 0; s < segs; s++) {
				int i00 = k * (segs + 1) + s, i01 = i00 + 1, i10 = i00 + segs + 1, i11 = i10 + 1;
				if (al[i00] <= 0.002f && al[i01] <= 0.002f && al[i10] <= 0.002f && al[i11] <= 0.002f) continue;
				quad(cx + cs[s] * r0, cy + sn[s] * r0, cx + cs[s + 1] * r0, cy + sn[s + 1] * r0,
					cx + cs[s + 1] * r1, cy + sn[s + 1] * r1, cx + cs[s] * r1, cy + sn[s] * r1,
					scaleAlpha(color, al[i00]), scaleAlpha(color, al[i01]), scaleAlpha(color, al[i11]), scaleAlpha(color, al[i10]));
			}
		}
	}

	public void fillCircle(float cx, float cy, float r, int color) {
		float h = px() / 2;
		bands(cx, cy, new float[] {0, Math.max(0, r - h), r + h}, new float[] {1, 1, 0}, 0, (float) (Math.PI * 2), color);
	}

	/** Filled annulus between {@code r0} and {@code r1}. */
	public void ring(float cx, float cy, float r0, float r1, int color) {
		float h = px() / 2;
		if (r1 - r0 >= 2 * h) {
			bands(cx, cy, new float[] {r0 - h, r0 + h, r1 - h, r1 + h}, new float[] {0, 1, 1, 0}, 0, (float) (Math.PI * 2), color);
		} else {
			float w = r1 - r0, mid = (r0 + r1) / 2;
			bands(cx, cy, new float[] {mid - w / 2 - h, mid, mid + w / 2 + h}, new float[] {0, 2 * w / (w + 2 * h), 0}, 0, (float) (Math.PI * 2), color);
		}
	}

	/** Arc stroke with feathered ends (approximating round caps). Angles in degrees, clockwise from +x. */
	public void arc(float cx, float cy, float r, float width, float deg0, float deg1, int color) {
		float aa = px();
		float capDeg = (float) Math.toDegrees((width / 2) / r);
		float featherDeg = (float) Math.toDegrees(aa / r);
		float a0 = (float) Math.toRadians(deg0 - capDeg), a1 = (float) Math.toRadians(deg1 + capDeg);
		float fr = (float) Math.toRadians(featherDeg);
		float hw = width / 2, h = aa / 2;
		float[] radii = {r - hw - h, r - hw + h, r + hw - h, r + hw + h};
		float[] ral = {0, 1, 1, 0};
		float s0 = a0 - fr / 2, s1 = a1 + fr / 2;
		float[] mid = angles(r, s0 + fr, s1 - fr, 8);
		float[] thetas = new float[mid.length + 2];
		thetas[0] = s0;
		System.arraycopy(mid, 0, thetas, 1, mid.length);
		thetas[thetas.length - 1] = s1;
		polar(cx, cy, radii, thetas, (rr, t) -> {
			int idx = 0;
			for (int i = 0; i < 4; i++) if (Math.abs(radii[i] - rr) < 1e-4f) idx = i;
			float edge = Math.min(t - s0, s1 - t) / fr;
			return ral[idx] * Math.max(0, Math.min(1, edge));
		}, color);
	}

	// ------------------------------------------------------------- shapes

	public static int cornerSegments(float rPx) {
		return Math.max(2, Math.min(32, (int) Math.ceil(rPx * Math.PI / 2 / 2.5)));
	}

	/** Clockwise rounded-rectangle outline. Returns point count; points written to the arrays. */
	public int roundRectPath(float x, float y, float w, float h, float r, float[][] out) {
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		int seg = r <= 0 ? 0 : cornerSegments(r / px());
		int n = r <= 0 ? 4 : 4 * (seg + 1);
		float[] xs = new float[n], ys = new float[n];
		if (r <= 0) {
			xs[0] = x; ys[0] = y;
			xs[1] = x + w; ys[1] = y;
			xs[2] = x + w; ys[2] = y + h;
			xs[3] = x; ys[3] = y + h;
		} else {
			float[][] centres = {{x + w - r, y + r}, {x + w - r, y + h - r}, {x + r, y + h - r}, {x + r, y + r}};
			double[] start = {-Math.PI / 2, 0, Math.PI / 2, Math.PI};
			int k = 0;
			for (int q = 0; q < 4; q++) {
				for (int s = 0; s <= seg; s++) {
					double t = start[q] + (Math.PI / 2) * s / seg;
					xs[k] = centres[q][0] + (float) Math.cos(t) * r;
					ys[k] = centres[q][1] + (float) Math.sin(t) * r;
					k++;
				}
			}
		}
		out[0] = xs;
		out[1] = ys;
		return n;
	}

	private final float[][] path = new float[2][];

	public void fillRoundRect(float x, float y, float w, float h, float r, int color) {
		if (w <= 0 || h <= 0) return;
		int n = roundRectPath(x, y, w, h, r, path);
		fillConvex(path[0], path[1], n, color);
	}

	/** Border drawn inside the box, like a CSS border with box-sizing: border-box. */
	public void borderRoundRect(float x, float y, float w, float h, float r, float width, int color) {
		if (w <= 0 || h <= 0) return;
		float i = width / 2;
		int n = roundRectPath(x + i, y + i, w - width, h - width, r - i, path);
		stroke(path[0], path[1], n, true, width, color);
	}

	public void dashedBorderRoundRect(float x, float y, float w, float h, float r, float width, float dash, float gap, int color) {
		float i = width / 2;
		int n = roundRectPath(x + i, y + i, w - width, h - width, r - i, path);
		strokeDashed(path[0], path[1], n, true, width, dash, gap, color);
	}

	// ------------------------------------------------------------- shadows

	/**
	 * CSS outer box-shadow (no spread) of a rounded rectangle: blurred with
	 * sigma, offset by (ox, oy), and clipped to outside the element.
	 */
	public void boxShadow(float x, float y, float w, float h, float r, float offX, float offY, float sigma, int color) {
		float sx0 = x + offX, sy0 = y + offY, sx1 = sx0 + w, sy1 = sy0 + h;
		float ext = sigma * 3;
		float minx = Math.min(x, sx0 - ext), maxx = Math.max(x + w, sx1 + ext);
		float miny = Math.min(y, sy0 - ext), maxy = Math.max(y + h, sy1 + ext);
		r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
		float[] xs = stops(minx, maxx, new float[] {sx0, sx1}, sigma, new float[] {x, x + w, x + r, x + w - r});
		float[] ys = stops(miny, maxy, new float[] {sy0, sy1}, sigma, new float[] {y, y + h, y + r, y + h - r});
		final float k = (float) (1 / (sigma * Math.sqrt(2)));
		PlaneField field = (px, py) -> 0.25f * (erf((px - sx0) * k) - erf((px - sx1) * k)) * (erf((py - sy0) * k) - erf((py - sy1) * k));
		float[] fx = new float[xs.length * ys.length];
		for (int j = 0; j < ys.length; j++) for (int i = 0; i < xs.length; i++) fx[j * xs.length + i] = field.alpha(xs[i], ys[j]);
		float ex0 = x, ex1 = x + w, ey0 = y, ey1 = y + h;
		for (int j = 0; j + 1 < ys.length; j++) {
			for (int i = 0; i + 1 < xs.length; i++) {
				float mx = (xs[i] + xs[i + 1]) / 2, my = (ys[j] + ys[j + 1]) / 2;
				if (mx > ex0 && mx < ex1 && my > ey0 && my < ey1) continue;
				float a00 = fx[j * xs.length + i], a10 = fx[j * xs.length + i + 1], a01 = fx[(j + 1) * xs.length + i], a11 = fx[(j + 1) * xs.length + i + 1];
				if (a00 < 0.002f && a10 < 0.002f && a01 < 0.002f && a11 < 0.002f) continue;
				quad(xs[i], ys[j], xs[i + 1], ys[j], xs[i + 1], ys[j + 1], xs[i], ys[j + 1],
					scaleAlpha(color, a00), scaleAlpha(color, a10), scaleAlpha(color, a11), scaleAlpha(color, a01));
			}
		}
		if (r > 0) {
			// fill the bits of the corner squares that lie outside the rounded corners
			float[][] corners = {{x, y, x + r, y + r, (float) Math.PI}, {x + w, y, x + w - r, y + r, (float) (-Math.PI / 2)},
				{x + w, y + h, x + w - r, y + h - r, 0}, {x, y + h, x + r, y + h - r, (float) (Math.PI / 2)}};
			int seg = cornerSegments(r / px());
			for (float[] cn : corners) {
				float pxc = cn[0], pyc = cn[1], ccx = cn[2], ccy = cn[3], st = cn[4];
				int cp = scaleAlpha(color, field.alpha(pxc, pyc));
				for (int s = 0; s < seg; s++) {
					double t0 = st + (Math.PI / 2) * s / seg, t1 = st + (Math.PI / 2) * (s + 1) / seg;
					float x0 = ccx + (float) Math.cos(t0) * r, y0 = ccy + (float) Math.sin(t0) * r;
					float x1 = ccx + (float) Math.cos(t1) * r, y1 = ccy + (float) Math.sin(t1) * r;
					tri(pxc, pyc, x0, y0, x1, y1, cp, scaleAlpha(color, field.alpha(x0, y0)), scaleAlpha(color, field.alpha(x1, y1)));
				}
			}
		}
	}

	private static float[] stops(float min, float max, float[] edges, float sigma, float[] fixed) {
		float[] ks = {-3f, -2.25f, -1.6f, -1.1f, -0.7f, -0.35f, 0f, 0.35f, 0.7f, 1.1f, 1.6f, 2.25f, 3f};
		float[] out = new float[edges.length * ks.length + fixed.length + 2];
		int n = 0;
		out[n++] = min;
		out[n++] = max;
		for (float e : edges) for (float k : ks) out[n++] = e + k * sigma;
		for (float v : fixed) out[n++] = v;
		Arrays.sort(out, 0, n);
		float[] res = new float[n];
		int m = 0;
		for (int i = 0; i < n; i++) {
			float v = Math.max(min, Math.min(max, out[i]));
			if (m == 0 || v - res[m - 1] > 1e-3f) res[m++] = v;
		}
		return Arrays.copyOf(res, m);
	}

	/** Outer shadow / glow of a circle (CSS box-shadow on a round element). */
	public void circleShadow(float cx, float cy, float radius, float offX, float offY, float sigma, int color) {
		float maxR = radius + Math.abs(offX) + Math.abs(offY) + sigma * 3.2f;
		int nr = 14;
		float[] radii = new float[nr];
		for (int i = 0; i < nr; i++) {
			float t = i / (float) (nr - 1);
			radii[i] = radius + (maxR - radius) * (t * t * 0.6f + t * 0.4f);
		}
		final float scx = cx + offX, scy = cy + offY;
		polar(cx, cy, radii, 0, (float) (Math.PI * 2), 48, (r, t) -> {
			float x = cx + (float) Math.cos(t) * r - scx, y = cy + (float) Math.sin(t) * r - scy;
			return DiscBlur.value((float) Math.hypot(x, y), radius, sigma);
		}, color);
	}

	/** Blurred copy of a disc drawn everywhere (used for glows under small dots). */
	public void discGlow(float cx, float cy, float radius, float sigma, int color) {
		float maxR = radius + sigma * 3.2f;
		int nr = 12;
		float[] radii = new float[nr];
		for (int i = 0; i < nr; i++) radii[i] = maxR * i / (nr - 1f);
		polar(cx, cy, radii, 0, (float) (Math.PI * 2), 32, (r, t) -> DiscBlur.value(r, radius, sigma), color);
	}

	/** Gaussian glow around an arc stroke (CSS drop-shadow on an SVG arc). Degrees clockwise from +x. */
	public void arcGlow(float cx, float cy, float r, float width, float deg0, float deg1, float sigma, int color) {
		float ext = sigma * 3.2f + width / 2;
		int nr = 13;
		float[] radii = new float[nr];
		for (int i = 0; i < nr; i++) radii[i] = r - ext + 2 * ext * i / (nr - 1f);
		final float k = (float) (1 / (sigma * Math.sqrt(2)));
		final float hw = width / 2;
		final float t0 = (float) Math.toRadians(deg0), t1 = (float) Math.toRadians(deg1);
		float pad = ext / r;
		polar(cx, cy, radii, t0 - pad, t1 + pad, 64, (rr, t) -> {
			float d = rr - r;
			float across = 0.5f * (erf((d + hw) * k) - erf((d - hw) * k));
			float along;
			float arcPos = (t - t0) * r, arcLen = (t1 - t0) * r;
			along = 0.5f * (erf((arcPos + hw) * k) - erf((arcPos - arcLen - hw) * k));
			return across * along;
		}, color);
	}

	/** Gaussian glow of an axis-aligned rectangle (drawn everywhere, including underneath). */
	public void rectGlow(float x, float y, float w, float h, float sigma, int color) {
		float ext = sigma * 3;
		float[] xs = stops(x - ext, x + w + ext, new float[] {x, x + w}, sigma, new float[0]);
		float[] ys = stops(y - ext, y + h + ext, new float[] {y, y + h}, sigma, new float[0]);
		final float k = (float) (1 / (sigma * Math.sqrt(2)));
		for (int j = 0; j + 1 < ys.length; j++) {
			for (int i = 0; i + 1 < xs.length; i++) {
				float a00 = g(xs[i], x, x + w, k) * g(ys[j], y, y + h, k);
				float a10 = g(xs[i + 1], x, x + w, k) * g(ys[j], y, y + h, k);
				float a11 = g(xs[i + 1], x, x + w, k) * g(ys[j + 1], y, y + h, k);
				float a01 = g(xs[i], x, x + w, k) * g(ys[j + 1], y, y + h, k);
				if (a00 < 0.002f && a10 < 0.002f && a01 < 0.002f && a11 < 0.002f) continue;
				quad(xs[i], ys[j], xs[i + 1], ys[j], xs[i + 1], ys[j + 1], xs[i], ys[j + 1],
					scaleAlpha(color, a00), scaleAlpha(color, a10), scaleAlpha(color, a11), scaleAlpha(color, a01));
			}
		}
	}

	private static float g(float v, float lo, float hi, float k) {
		return 0.5f * (erf((v - lo) * k) - erf((v - hi) * k));
	}

	/** Abramowitz-Stegun 7.1.26. */
	public static float erf(float x) {
		float s = Math.signum(x);
		x = Math.abs(x);
		float t = 1f / (1f + 0.3275911f * x);
		float y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * (float) Math.exp(-x * x);
		return s * y;
	}
}
