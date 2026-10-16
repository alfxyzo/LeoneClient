package dev.alfxyz.leoneclient.hud;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.alfxyz.leoneclient.LeoneConfig;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.features.HudTailor;
import dev.alfxyz.leoneclient.features.HudTailor.Layout;
import dev.alfxyz.leoneclient.features.HudTailor.Part;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Gfx;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.ui.LeoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * HUD Tailor's editor, styled like Modify HUD: Minecraft's own HUD stays live underneath, and each part of
 * it can be dragged, scrolled to resize, right-clicked to hide, and scoreboard lines clicked to hide them.
 * Parts are worked out in GUI pixels; everything here is drawn in the menu's 900-tall design space.
 */
public class VanillaHudEditor extends Screen {
	private static final Style TITLE = Style.of(14, Weight.SEMIBOLD);
	private static final Style HINT = Style.of(12, Weight.REGULAR);
	private static final Style BUTTON = Style.of(12.5f, Weight.REGULAR);
	private static final Style TAG = Style.of(11.5f, Weight.REGULAR);
	/** Snapping distance in GUI pixels. */
	private static final float SNAP = 4;
	private static final double CONFIRM_MS = 3000;

	private final Canvas cv = Gfx.newCanvas();
	private final double openedAt = now();
	private @Nullable Part dragging;
	private float grabX, grabY, startX, startY;
	private boolean moved, snapX, snapY;
	private Sidebar.@Nullable Line pressedLine;
	private double mouseX, mouseY;
	/** Toolbar and buttons, in design px. */
	private float[] toolbar = new float[4], resetBtn = new float[4], doneBtn = new float[4], linesBtn = new float[4], overlaysBtn = new float[4];
	private double resetArmedAt = -1;
	/** The toolbar fades out of the way while you work on a part underneath it. */
	private final Anim toolbarAlpha = new Anim(1);
	private boolean toolbarFaded;
	/** Opened from Modify HUD rather than from HUD Tailor's settings page, so Done goes back to the Overlays page. */
	private final boolean fromModifyHud;

	public VanillaHudEditor() {
		this(false);
	}

	public VanillaHudEditor(boolean fromModifyHud) {
		super(Component.literal("HUD Tailor"));
		this.fromModifyHud = fromModifyHud;
		// editing the vanilla HUD only shows while HUD Tailor is on
		if (!Modules.HUD_TAILOR.enabled()) {
			Modules.HUD_TAILOR.setEnabled(true);
			LeoneConfig.save();
		}
	}

	private static HudTailor t() {
		return Modules.HUD_TAILOR;
	}

	private static double now() {
		return System.nanoTime() / 1e6;
	}

	/** GUI pixels per design px. */
	private static float s() {
		return Hud.scale();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		float k = Ease.progress(now(), openedAt, 0, 250, Ease.EASE);
		Canvas c = Hud.begin(cv);
		c.fillRect(0, 0, Hud.designWidth(), 900, Colors.black(0.22f * k));
		c.pop();
		c.flush(g);
	}

	/** A part's box on screen, in GUI pixels. */
	private float[] rect(Part p) {
		return t().screenBounds(p, Minecraft.getInstance(), width, height);
	}

	/** The same box in design px. */
	private float[] design(float[] gui) {
		float s = s();
		return new float[] {gui[0] / s, gui[1] / s, gui[2] / s, gui[3] / s};
	}

	/**
	 * The part under a point: one with something on screen before an empty placeholder, then the smallest,
	 * so a small part inside a larger box can still be caught.
	 */
	private @Nullable Part partAt(double x, double y) {
		Minecraft mc = Minecraft.getInstance();
		Part best = null;
		float bestScore = Float.MAX_VALUE;
		for (Part p : Part.values()) {
			float[] r = rect(p);
			if (x < r[0] - 2 || x >= r[0] + r[2] + 2 || y < r[1] - 2 || y >= r[1] + r[3] + 2) continue;
			float score = r[2] * r[3] + (t().shown(p) && t().present(p, mc) ? 0 : 1e7f);
			if (score < bestScore) {
				best = p;
				bestScore = score;
			}
		}
		return best;
	}

	private Sidebar.@Nullable Line lineAt(double x, double y) {
		if (!t().shown(Part.SCOREBOARD)) return null;
		Minecraft mc = Minecraft.getInstance();
		float[] at = t().toPart(Part.SCOREBOARD, mc, width, height, (float) x, (float) y);
		for (Sidebar.Line l : Sidebar.lines()) {
			if (at[0] >= l.x() && at[0] < l.x() + l.w() && at[1] >= l.y() && at[1] < l.y() + l.h()) return l;
		}
		return null;
	}

	/** A point in a part's own unmoved GUI pixels, on screen in GUI pixels. */
	private float[] toScreen(Part p, float x, float y) {
		Minecraft mc = Minecraft.getInstance();
		float[] r = t().bounds(p, mc, width, height);
		Layout l = t().layout(p);
		float s = l.scale.get(), px = r[0] + r[2] / 2, py = r[1] + r[3] / 2;
		return new float[] {px + l.x.get() + (x - px) * s, py + l.y.get() + (y - py) * s};
	}

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		Gfx.ensure();
		Modules.INTERFACE.apply();
		Minecraft mc = Minecraft.getInstance();
		double now = now();
		TextRenderer text = Gfx.text();
		text.setGraphics(g);
		Canvas c = Hud.begin(cv);
		float sw = Hud.designWidth();
		float mdx = (float) (mouseX / s()), mdy = (float) (mouseY / s());
		c.mulAlpha(Ease.progress(now, openedAt, 0, 250, Ease.EASE));

		Part hovered = dragging != null ? dragging : toolbarHit(mdx, mdy) ? null : partAt(mouseX, mouseY);
		Sidebar.Line line = dragging == null && hovered == Part.SCOREBOARD ? lineAt(mouseX, mouseY) : null;

		// centre guides while dragging, lit when the part snaps to them
		if (dragging != null) {
			c.fillRect(sw / 2 - 0.5f, 0, sw / 2 + 0.5f, 900, snapX ? Colors.accent(0.9f) : Colors.white(0.12f));
			c.fillRect(0, 449.5f, sw, 450.5f, snapY ? Colors.accent(0.9f) : Colors.white(0.12f));
		}

		for (Part p : Part.values()) {
			if (p == hovered) continue;
			float[] r = design(rect(p));
			boolean shown = t().shown(p), present = t().present(p, mc);
			if (!shown) {
				c.fillRoundRect(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 8, Colors.black(0.35f));
				c.dashedBorderRoundRect(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 8, 1, 4, 3, Colors.white(0.22f));
				placeholder(c, text, p.label, r, true);
			} else if (present) {
				c.dashedBorderRoundRect(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 8, 1, 4, 3, Colors.white(0.35f));
			} else {
				// nothing showing just now (titles, the tab list...): a faint box, named when there is room
				c.dashedBorderRoundRect(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 8, 1, 3, 4, Colors.white(0.14f));
				placeholder(c, text, p.label, r, false);
			}
		}

		if (hovered != null) {
			float[] r = design(rect(hovered));
			c.borderRoundRect(r[0] - 3, r[1] - 3, r[2] + 6, r[3] + 6, 8, 1.5f, Colors.accent(0.95f));
			if (line != null) {
				float sc = t().layout(Part.SCOREBOARD).scale.get();
				float[] tl = toScreen(Part.SCOREBOARD, line.x(), line.y());
				float lx = tl[0] / s(), ly = tl[1] / s(), lw = line.w() * sc / s(), lh = line.h() * sc / s();
				c.fillRoundRect(lx, ly, lw, lh, 4, Colors.accent(0.28f));
				tag(c, text, sw, line.hidden() ? "Click to show this line" : "Click to hide this line", r);
			} else {
				Layout l = t().layout(hovered);
				String name = hovered.label + "  " + Math.round(l.scale.get() * 100) + "%" + (t().shown(hovered) ? "" : "  ·  hidden")
					+ (l.moved() ? "  ·  middle-click to put back" : "");
				tag(c, text, sw, name, r);
			}
		}

		float[] hr = hovered != null ? design(rect(hovered)) : null;
		toolbarFaded = dragging != null && hr != null && hr[0] - 3 < toolbar[0] + toolbar[2] && hr[0] + hr[2] + 3 > toolbar[0]
			&& hr[1] - 3 < toolbar[1] + toolbar[3] && hr[1] + hr[3] + 3 > toolbar[1];
		toolbarAlpha.set(toolbarFaded ? 0.15f : 1, now, 160, Ease.EASE);
		c.push();
		c.mulAlpha(toolbarAlpha.get(now));
		drawToolbar(c, text, sw, mdx, mdy, now);
		c.pop();
		c.pop();
		c.flush(g);
		text.setGraphics(null);

		boolean overButton = !toolbarFaded && (inside(resetBtn, mdx, mdy) || inside(doneBtn, mdx, mdy)
			|| inside(linesBtn, mdx, mdy) || inside(overlaysBtn, mdx, mdy));
		if (dragging != null) g.requestCursor(CursorTypes.RESIZE_ALL);
		else if (overButton || line != null) g.requestCursor(CursorTypes.POINTING_HAND);
		else if (hovered != null) g.requestCursor(CursorTypes.RESIZE_ALL);
	}

	/** A part's name inside its box, for parts that are hidden or have nothing showing. */
	private static void placeholder(Canvas c, TextRenderer text, String label, float[] r, boolean hidden) {
		float tw = text.width(label, TAG);
		// small bars (health, air, item name) sit close together, so only roomy boxes are named; hover names the rest
		if (r[3] < 40 || tw + (hidden ? 22 : 0) > r[2] - 12) return;
		float cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2;
		if (hidden) {
			float w = 16 + 6 + tw;
			Gfx.icons().draw(c, Icons.EYE_OFF, cx - w / 2, cy - 7, 14, 1.6f, Colors.white(0.6f));
			text.draw(c, label, cx - w / 2 + 20, text.baselineFor(TAG, cy), TAG, Colors.white(0.6f));
		} else {
			text.draw(c, label, cx - tw / 2, text.baselineFor(TAG, cy), TAG, Colors.white(0.38f));
		}
	}

	/** The accent name tag above a box, or below it when there is no room above. */
	private static void tag(Canvas c, TextRenderer text, float sw, String label, float[] r) {
		float tw = text.width(label, TAG) + 14;
		float ty = r[1] - 3 - 22 < 0 ? r[1] + r[3] + 7 : r[1] - 3 - 22;
		float tx = Math.max(2, Math.min(sw - tw - 2, r[0] - 3));
		c.fillRoundRect(tx, ty, tw, 18, 6, Colors.ACCENT);
		text.draw(c, label, tx + 7, text.baselineFor(TAG, ty + 9), TAG, Colors.WHITE);
	}

	private boolean toolbarHit(float mdx, float mdy) {
		return !toolbarFaded && inside(toolbar, mdx, mdy);
	}

	private void drawToolbar(Canvas c, TextRenderer text, float sw, float mdx, float mdy, double now) {
		String title = "HUD Tailor";
		String hint = "Drag to move · Scroll to resize · Right-click to hide · Click a scoreboard line to hide it";
		boolean armed = resetArmedAt >= 0 && now - resetArmedAt < CONFIRM_MS;
		String reset = armed ? "Click again" : "Reset";
		int hidden = t().hiddenHere();
		String lines = hidden == 1 ? "Show 1 line" : "Show " + hidden + " lines";
		String overlays = "Leone overlays";
		float overlaysW = 24 + text.width(overlays, BUTTON);
		float linesW = hidden > 0 ? 24 + text.width(lines, BUTTON) : 0;
		float resetW = 24 + text.width(reset, BUTTON), doneW = 24 + text.width("Done", BUTTON);
		float buttons = overlaysW + 6 + (hidden > 0 ? linesW + 6 : 0) + resetW + 6 + doneW;
		// a narrow window keeps the buttons and drops the hint
		float hintW = text.width(hint, HINT);
		float base = 14 + 16 + 10 + text.width(title, TITLE) + 14 + 18 + buttons + 7;
		boolean showHint = base + hintW <= sw - 24;
		float w = base + (showHint ? hintW : -14);
		float h = 44, x = sw / 2 - w / 2, y = 18;
		toolbar = new float[] {x, y, w, h};
		c.boxShadow(x, y, w, h, 14, 0, 12, 16, Colors.black(0.35f));
		c.fillRoundRect(x, y, w, h, 14, Colors.glass(0.82f));
		c.borderRoundRect(x, y, w, h, 14, 1, Colors.white(0.1f));
		float cx = x + 14, mid = y + h / 2;
		Gfx.icons().draw(c, Icons.PANEL_BOTTOM, cx, mid - 8, 16, 1.8f, Colors.accent(1));
		cx += 16 + 10;
		text.draw(c, title, cx, text.baselineFor(TITLE, mid), TITLE, Colors.TEXT);
		cx += text.width(title, TITLE) + 14;
		if (showHint) {
			text.draw(c, hint, cx, text.baselineFor(HINT, mid), HINT, Colors.TEXT_HINT);
			cx += hintW;
		}
		cx += 18 - (showHint ? 0 : 14);
		overlaysBtn = ghost(c, text, overlays, cx, mid, overlaysW, mdx, mdy, false);
		cx += overlaysW + 6;
		if (hidden > 0) {
			linesBtn = ghost(c, text, lines, cx, mid, linesW, mdx, mdy, false);
			cx += linesW + 6;
		} else {
			linesBtn = new float[4];
		}
		resetBtn = ghost(c, text, reset, cx, mid, resetW, mdx, mdy, armed);
		cx += resetW + 6;
		doneBtn = new float[] {cx, mid - 15, doneW, 30};
		boolean dh = inside(doneBtn, mdx, mdy);
		c.fillRoundRect(cx, mid - 15, doneW, 30, 9, dh ? Colors.ACCENT : Colors.accent(0.85f));
		text.draw(c, "Done", cx + 12, text.baselineFor(BUTTON, mid), BUTTON, Colors.WHITE);
	}

	/** A quiet toolbar button, or a red one while it waits for a second click. */
	private static float[] ghost(Canvas c, TextRenderer text, String label, float x, float mid, float w, float mdx, float mdy, boolean danger) {
		float[] r = {x, mid - 15, w, 30};
		boolean hov = inside(r, mdx, mdy);
		if (danger) {
			c.fillRoundRect(x, mid - 15, w, 30, 9, Colors.rgba(0xFF5A5A, hov ? 0.26f : 0.16f));
			c.borderRoundRect(x, mid - 15, w, 30, 9, 1, Colors.rgba(0xFF5A5A, 0.5f));
		} else {
			c.fillRoundRect(x, mid - 15, w, 30, 9, Colors.white(hov ? 0.1f : 0.06f));
			c.borderRoundRect(x, mid - 15, w, 30, 9, 1, Colors.white(0.1f));
		}
		text.draw(c, label, x + 12, text.baselineFor(BUTTON, mid), BUTTON, danger ? 0xFFFF9A9A : Colors.TEXT);
		return r;
	}

	private static boolean inside(float[] r, float x, float y) {
		return r[2] > 0 && x >= r[0] && x < r[0] + r[2] && y >= r[1] && y < r[1] + r[3];
	}

	/** Dev automation: the centre of the Leone overlays button, in GUI coordinates. */
	public float[] debugOverlaysButton() {
		return new float[] {(overlaysBtn[0] + overlaysBtn[2] / 2) * s(), (overlaysBtn[1] + overlaysBtn[3] / 2) * s()};
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
		if (toolbarHit(mdx, mdy)) {
			if (event.button() != 0) return true;
			if (inside(doneBtn, mdx, mdy)) {
				onClose();
			} else if (inside(resetBtn, mdx, mdy)) {
				if (resetArmedAt >= 0 && now() - resetArmedAt < CONFIRM_MS) {
					resetArmedAt = -1;
					t().resetLayout();
					LeoneConfig.save();
				} else {
					resetArmedAt = now();
				}
			} else if (inside(overlaysBtn, mdx, mdy)) {
				LeoneConfig.save();
				Minecraft.getInstance().gui.setScreen(new HudEditorScreen());
			} else if (inside(linesBtn, mdx, mdy)) {
				t().showAllLinesHere();
				LeoneConfig.save();
			}
			return true;
		}
		Part p = partAt(mouseX, mouseY);
		if (p == null) return true;
		switch (event.button()) {
			case 0 -> {
				Layout l = t().layout(p);
				dragging = p;
				grabX = (float) mouseX;
				grabY = (float) mouseY;
				startX = l.x.get();
				startY = l.y.get();
				moved = false;
				pressedLine = p == Part.SCOREBOARD ? lineAt(mouseX, mouseY) : null;
			}
			case 1 -> {
				t().setShown(p, !t().shown(p));
				LeoneConfig.save();
			}
			case 2 -> {
				t().layout(p).reset();
				LeoneConfig.save();
			}
			default -> {
			}
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mouseX = event.x();
		mouseY = event.y();
		if (dragging == null) return false;
		float ddx = (float) mouseX - grabX, ddy = (float) mouseY - grabY;
		if (!moved && Math.abs(ddx) < 2 && Math.abs(ddy) < 2) return true;
		moved = true;
		Layout l = t().layout(dragging);
		l.x.set(startX + ddx);
		l.y.set(startY + ddy);
		// snap the box's centre to the middle of the screen, and keep it on screen
		float[] r = rect(dragging);
		float cx = r[0] + r[2] / 2, cy = r[1] + r[3] / 2;
		snapX = Math.abs(cx - width / 2f) < SNAP;
		snapY = Math.abs(cy - height / 2f) < SNAP;
		if (snapX) l.x.set(l.x.get() + width / 2f - cx);
		if (snapY) l.y.set(l.y.get() + height / 2f - cy);
		r = rect(dragging);
		if (r[0] < 0) l.x.set(l.x.get() - r[0]);
		if (r[1] < 0) l.y.set(l.y.get() - r[1]);
		if (r[0] + r[2] > width) l.x.set(l.x.get() - (r[0] + r[2] - width));
		if (r[1] + r[3] > height) l.y.set(l.y.get() - (r[1] + r[3] - height));
		l.x.value = Math.round(l.x.get());
		l.y.value = Math.round(l.y.get());
		return true;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		if (dragging != null) {
			if (!moved && pressedLine != null) t().toggleLine(pressedLine.key());
			dragging = null;
			pressedLine = null;
			snapX = snapY = false;
			LeoneConfig.save();
		}
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		mouseX = x;
		mouseY = y;
		if (toolbarHit((float) (x / s()), (float) (y / s()))) return false;
		Part p = partAt(x, y);
		if (p == null || scrollY == 0 || dragging != null) return false;
		Layout l = t().layout(p);
		l.scale.set(l.scale.get() + (scrollY > 0 ? 0.05f : -0.05f));
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
		Minecraft.getInstance().gui.setScreen(fromModifyHud
			? LeoneScreen.returningTo(LeoneScreen.Kind.OVERLAYS)
			: LeoneScreen.returningTo(Modules.HUD_TAILOR));
	}
}
