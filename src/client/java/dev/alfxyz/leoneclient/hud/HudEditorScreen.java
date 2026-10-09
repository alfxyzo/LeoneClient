package dev.alfxyz.leoneclient.hud;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.alfxyz.leoneclient.LeoneConfig;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Gfx;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.ui.LeoneScreen;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/** Drag overlays around the screen and resize them. Esc or Done returns to the menu. */
public class HudEditorScreen extends Screen {
	private static final Style TITLE = Style.of(14, Weight.SEMIBOLD);
	private static final Style HINT = Style.of(12, Weight.REGULAR);
	private static final Style BUTTON = Style.of(12.5f, Weight.REGULAR);
	private static final Style TAG = Style.of(11.5f, Weight.REGULAR);
	/** How far from an overlay's bottom-right corner its resize grip can be caught. */
	private static final float SNAP = 6, MARGIN = 8, GRIP = 14;

	private final Canvas cv = Gfx.newCanvas();
	private final double openedAt = System.nanoTime() / 1e6;
	private final Map<Overlay, float[]> rects = new HashMap<>();
	private @Nullable Overlay dragging;
	private float grabX, grabY;
	/** The overlay being resized by its corner handle, the top-left it keeps, and its unscaled size. */
	private @Nullable Overlay resizing;
	private float anchorX, anchorY, baseW, baseH, startScale, startDist;
	private boolean snapCenterX, snapCenterY;
	private double mouseX, mouseY;
	private float[] resetBtn = new float[4], doneBtn = new float[4], toolbar = new float[4];
	/** The toolbar fades out of the way while you work on an overlay underneath it. */
	private final Anim toolbarAlpha = new Anim(1);
	private boolean toolbarFaded;

	public HudEditorScreen() {
		super(Component.literal("Modify HUD"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	private static double now() {
		return System.nanoTime() / 1e6;
	}

	private float s() {
		return Hud.scale();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float k = Ease.progress(now(), openedAt, 0, 250, Ease.EASE);
		Canvas c = Hud.begin(cv);
		c.fillRect(0, 0, Hud.designWidth(), 900, Colors.black(0.3f * k));
		c.pop();
		c.flush(g);
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		Gfx.ensure();
		double now = now();
		TextRenderer text = Gfx.text();
		text.setGraphics(g);
		Canvas c = Hud.begin(cv);
		float sw = Hud.designWidth();
		float mdx = (float) (mouseX / s()), mdy = (float) (mouseY / s());
		float fade = Ease.progress(now, openedAt, 0, 250, Ease.EASE);
		c.mulAlpha(fade);

		// guides
		if (dragging != null) {
			c.fillRect(sw / 2 - 0.5f, 0, sw / 2 + 0.5f, 900, snapCenterX ? Colors.accent(0.9f) : Colors.white(0.12f));
			c.fillRect(0, 449.5f, sw, 450.5f, snapCenterY ? Colors.accent(0.9f) : Colors.white(0.12f));
		}

		Overlay.Context ctx = new Overlay.Context(c, text, Minecraft.getInstance(), g, now, true);
		rects.clear();
		Overlay hovered = null;
		for (Overlay o : Hud.ALL) {
			if (!o.enabled()) continue;
			float[] r = Hud.drawAt(ctx, o);
			rects.put(o, r);
			if (mdx >= r[0] - 3 && mdx < r[0] + r[2] + 3 && mdy >= r[1] - 3 && mdy < r[1] + r[3] + 3) hovered = o;
		}
		boolean anyEnabled = !rects.isEmpty();
		Overlay gripHover = dragging == null && resizing == null ? handleAt(mdx, mdy) : null;
		for (Map.Entry<Overlay, float[]> e : rects.entrySet()) {
			float[] r = e.getValue();
			Overlay held = dragging != null ? dragging : resizing;
			boolean active = e.getKey() == held || held == null && (e.getKey() == hovered || e.getKey() == gripHover);
			drawGrip(c, r, e.getKey() == resizing || e.getKey() == gripHover, active);
			if (active) {
				c.borderRoundRect(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 9, 1.5f, Colors.accent(0.95f));
				String name = e.getKey().name + "  " + Math.round(e.getKey().scale * 100) + "%";
				float tw = text.width(name, TAG) + 14;
				float ty = r[1] - 3 - 22 < 0 ? r[1] + r[3] + 7 : r[1] - 3 - 22;
				float tx = Math.max(2, Math.min(sw - tw - 2, r[0] - 3));
				c.fillRoundRect(tx, ty, tw, 18, 6, Colors.ACCENT);
				text.draw(c, name, tx + 7, text.baselineFor(TAG, ty + 9), TAG, Colors.WHITE);
			} else {
				c.dashedBorderRoundRect(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 9, 1, 4, 3, Colors.white(0.35f));
			}
		}

		Overlay focus = dragging != null ? dragging : resizing != null ? resizing : hovered;
		float[] hr = focus != null ? rects.get(focus) : null;
		toolbarFaded = hr != null && hr[0] - 3 < toolbar[0] + toolbar[2] && hr[0] + hr[2] + 3 > toolbar[0]
			&& hr[1] - 3 < toolbar[1] + toolbar[3] && hr[1] + hr[3] + 3 > toolbar[1];
		toolbarAlpha.set(toolbarFaded ? 0.15f : 1, now, 160, Ease.EASE);
		c.push();
		c.mulAlpha(toolbarAlpha.get(now));
		drawToolbar(c, text, sw, mdx, mdy, anyEnabled);
		c.pop();
		c.pop();
		c.flush(g);
		text.setGraphics(null);
		boolean overButton = !toolbarFaded && (inside(resetBtn, mdx, mdy) || inside(doneBtn, mdx, mdy));
		if (dragging != null || resizing != null) g.requestCursor(CursorTypes.RESIZE_ALL);
		else if (gripHover != null) g.requestCursor(CursorTypes.POINTING_HAND);
		else if (hovered != null) g.requestCursor(CursorTypes.RESIZE_ALL);
		else if (overButton) g.requestCursor(CursorTypes.POINTING_HAND);
	}

	/** The topmost overlay under a design-space point, or null. */
	private @Nullable Overlay overlayAt(float mdx, float mdy) {
		Overlay found = null;
		for (Overlay o : Hud.ALL) {
			float[] r = rects.get(o);
			if (r != null && mdx >= r[0] - 3 && mdx < r[0] + r[2] + 3 && mdy >= r[1] - 3 && mdy < r[1] + r[3] + 3) found = o;
		}
		return found;
	}

	/**
	 * The resize grip: a bright bracket traced along the bottom-right corner of the overlay's frame, so it
	 * reads as part of the overlay without covering anything in it.
	 */
	private static void drawGrip(Canvas c, float[] r, boolean hot, boolean active) {
		float x1 = r[0] + r[2] + 3, y1 = r[1] + r[3] + 3, rad = 9, arm = 7;
		int n = 8;
		float[] px = new float[n + 3], py = new float[n + 3];
		px[0] = x1 - rad - arm;
		py[0] = y1;
		for (int k = 0; k <= n; k++) {
			double a = Math.PI / 2 * (1 - k / (double) n);
			px[k + 1] = (float) (x1 - rad + rad * Math.cos(a));
			py[k + 1] = (float) (y1 - rad + rad * Math.sin(a));
		}
		px[n + 2] = x1;
		py[n + 2] = y1 - rad - arm;
		int col = hot ? Colors.ACCENT : Colors.white(active ? 0.95f : 0.75f);
		c.stroke(px, py, n + 3, false, hot ? 3 : 2.2f, col);
	}

	/** The overlay whose resize handle is under the mouse, or null. */
	private @Nullable Overlay handleAt(float mdx, float mdy) {
		// topmost first, with a little room around the grip so it is easy to catch
		for (int i = Hud.ALL.size() - 1; i >= 0; i--) {
			float[] r = rects.get(Hud.ALL.get(i));
			if (r == null) continue;
			float x1 = r[0] + r[2] + 3, y1 = r[1] + r[3] + 3;
			if (mdx >= x1 - GRIP && mdx <= x1 + 6 && mdy >= y1 - GRIP && mdy <= y1 + 6) return Hud.ALL.get(i);
		}
		return null;
	}

	/** Changes an overlay's size while keeping its top-left corner where it is. */
	private void resize(Overlay o, float[] rect, float newScale) {
		float bw = rect[2] / o.scale, bh = rect[3] / o.scale;
		o.setScale(newScale);
		Hud.place(o, rect[0], rect[1], bw * o.scale, bh * o.scale);
	}

	private static boolean inside(float[] r, float x, float y) {
		return x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
	}

	private void drawToolbar(Canvas c, TextRenderer text, float sw, float mdx, float mdy, boolean anyEnabled) {
		String title = "Modify HUD";
		String hint = anyEnabled ? "Drag to move · Scroll, or drag the corner grip, to resize · Right-click to hide" : "No overlays enabled. Turn some on in Overlays.";
		float resetW = 24 + text.width("Reset", BUTTON), doneW = 24 + text.width("Done", BUTTON);
		float w = 14 + 16 + 10 + text.width(title, TITLE) + 14 + text.width(hint, HINT) + 18 + resetW + 6 + doneW + 7;
		float h = 44, x = sw / 2 - w / 2, y = 18;
		toolbar = new float[] {x, y, w, h};
		c.boxShadow(x, y, w, h, 14, 0, 12, 16, Colors.black(0.35f));
		c.fillRoundRect(x, y, w, h, 14, Colors.glass(0.82f));
		c.borderRoundRect(x, y, w, h, 14, 1, Colors.white(0.1f));
		float cx = x + 14, mid = y + h / 2;
		Gfx.icons().draw(c, Icons.HUD, cx, mid - 8, 16, 1.8f, Colors.accent(1));
		cx += 16 + 10;
		text.draw(c, title, cx, text.baselineFor(TITLE, mid), TITLE, Colors.TEXT);
		cx += text.width(title, TITLE) + 14;
		text.draw(c, hint, cx, text.baselineFor(HINT, mid), HINT, Colors.TEXT_HINT);
		cx += text.width(hint, HINT) + 18;
		resetBtn = new float[] {cx, mid - 15, resetW, 30};
		boolean rh = inside(resetBtn, mdx, mdy);
		c.fillRoundRect(cx, mid - 15, resetW, 30, 9, Colors.white(rh ? 0.1f : 0.06f));
		c.borderRoundRect(cx, mid - 15, resetW, 30, 9, 1, Colors.white(0.1f));
		text.draw(c, "Reset", cx + 12, text.baselineFor(BUTTON, mid), BUTTON, Colors.TEXT);
		cx += resetW + 6;
		doneBtn = new float[] {cx, mid - 15, doneW, 30};
		boolean dh = inside(doneBtn, mdx, mdy);
		c.fillRoundRect(cx, mid - 15, doneW, 30, 9, dh ? Colors.ACCENT : Colors.accent(0.85f));
		text.draw(c, "Done", cx + 12, text.baselineFor(BUTTON, mid), BUTTON, Colors.WHITE);
	}

	/** Dev automation: drags an overlay so its top-left lands at a design-space point. */
	public void debugDrag(String id, float toX, float toY) {
		for (Map.Entry<Overlay, float[]> e : rects.entrySet()) {
			if (!e.getKey().id.equals(id)) continue;
			float[] r = e.getValue();
			double sx = (r[0] + 4) * s(), sy = (r[1] + 4) * s();
			MouseButtonEvent down = new MouseButtonEvent(sx, sy, new net.minecraft.client.input.MouseButtonInfo(0, 0));
			mouseClicked(down, false);
			double ex = (toX + 4) * s(), ey = (toY + 4) * s();
			mouseDragged(new MouseButtonEvent(ex, ey, new net.minecraft.client.input.MouseButtonInfo(0, 0)), ex - sx, ey - sy);
			mouseMoved(ex, ey);
		}
	}

	public void debugRelease() {
		mouseReleased(new MouseButtonEvent(mouseX, mouseY, new net.minecraft.client.input.MouseButtonInfo(0, 0)));
	}

	@Override
	public void mouseMoved(double x, double y) {
		mouseX = x;
		mouseY = y;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouseX = event.x();
		mouseY = event.y();
		float mdx = (float) (mouseX / s()), mdy = (float) (mouseY / s());
		if (event.button() == 0 && !toolbarFaded && inside(doneBtn, mdx, mdy)) {
			onClose();
			return true;
		}
		if (event.button() == 0 && !toolbarFaded && inside(resetBtn, mdx, mdy)) {
			for (Overlay o : Hud.ALL) o.resetPosition();
			LeoneConfig.save();
			return true;
		}
		Overlay handle = event.button() == 0 ? handleAt(mdx, mdy) : null;
		if (handle != null) {
			float[] r = rects.get(handle);
			resizing = handle;
			anchorX = r[0];
			anchorY = r[1];
			baseW = r[2] / handle.scale;
			baseH = r[3] / handle.scale;
			startScale = handle.scale;
			startDist = Math.max(4, (float) Math.hypot(mdx - r[0], mdy - r[1]));
			return true;
		}
		List<Overlay> order = new ArrayList<>(rects.keySet());
		order.sort((a, b) -> Hud.ALL.indexOf(b) - Hud.ALL.indexOf(a));
		for (Overlay o : order) {
			float[] r = rects.get(o);
			if (mdx >= r[0] - 3 && mdx < r[0] + r[2] + 3 && mdy >= r[1] - 3 && mdy < r[1] + r[3] + 3) {
				if (event.button() == 1) {
					o.setEnabled(false);
					LeoneConfig.save();
				} else if (event.button() == 0) {
					dragging = o;
					grabX = mdx - r[0];
					grabY = mdy - r[1];
				}
				return true;
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mouseX = event.x();
		mouseY = event.y();
		if (resizing != null) {
			float mdx = (float) (mouseX / s()), mdy = (float) (mouseY / s());
			float dist = (float) Math.hypot(mdx - anchorX, mdy - anchorY);
			resizing.setScale(startScale * dist / startDist);
			Hud.place(resizing, anchorX, anchorY, baseW * resizing.scale, baseH * resizing.scale);
			return true;
		}
		if (dragging == null) return false;
		float[] r = rects.get(dragging);
		if (r == null) return true;
		float sw = Hud.designWidth();
		float w = r[2], h = r[3];
		float x = (float) (mouseX / s()) - grabX, y = (float) (mouseY / s()) - grabY;
		snapCenterX = Math.abs(x + w / 2 - sw / 2) < SNAP;
		snapCenterY = Math.abs(y + h / 2 - 450) < SNAP;
		if (snapCenterX) x = sw / 2 - w / 2;
		else if (Math.abs(x - MARGIN) < SNAP) x = MARGIN;
		else if (Math.abs(x + w - (sw - MARGIN)) < SNAP) x = sw - MARGIN - w;
		if (snapCenterY) y = 450 - h / 2;
		else if (Math.abs(y - MARGIN) < SNAP) y = MARGIN;
		else if (Math.abs(y + h - (900 - MARGIN)) < SNAP) y = 900 - MARGIN - h;
		x = Math.max(0, Math.min(sw - w, x));
		y = Math.max(0, Math.min(900 - h, y));
		Hud.place(dragging, x, y, w, h);
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (resizing != null) {
			resizing = null;
			LeoneConfig.save();
			return true;
		}
		if (dragging != null) {
			dragging = null;
			snapCenterX = snapCenterY = false;
			LeoneConfig.save();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		mouseX = x;
		mouseY = y;
		Overlay o = overlayAt((float) (x / s()), (float) (y / s()));
		if (o == null || scrollY == 0 || dragging != null || resizing != null) return false;
		float[] r = rects.get(o);
		if (r == null) return false;
		resize(o, r, o.scale + (scrollY > 0 ? 0.1f : -0.1f));
		LeoneConfig.save();
		return true;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (event.isEscape()) {
			onClose();
			return true;
		}
		return false;
	}

	@Override
	public void onClose() {
		LeoneConfig.save();
		Minecraft.getInstance().gui.setScreen(new LeoneScreen(LeoneScreen.Kind.OVERLAYS));
	}
}
