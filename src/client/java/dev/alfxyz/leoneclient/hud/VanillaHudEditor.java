package dev.alfxyz.leoneclient.hud;

import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.alfxyz.leoneclient.LeoneConfig;
import dev.alfxyz.leoneclient.features.HudTailor;
import dev.alfxyz.leoneclient.features.HudTailor.Layout;
import dev.alfxyz.leoneclient.features.HudTailor.Part;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.ui.LeoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * HUD Tailor's editor: Minecraft's own HUD stays live underneath, and each part of it can be dragged,
 * scrolled to resize, right-clicked to hide, and scoreboard lines clicked to hide them.
 */
public class VanillaHudEditor extends Screen {
	private static final float SNAP = 4;
	private static final double CONFIRM_MS = 3000;

	private @Nullable Part dragging;
	private float grabX, grabY, startX, startY;
	private boolean moved, snapX, snapY;
	private Sidebar.@Nullable Line pressedLine;
	private double mouseX, mouseY;
	private int[] resetBtn = new int[4], doneBtn = new int[4], linesBtn = new int[4];
	private double resetArmedAt = -1;

	public VanillaHudEditor() {
		super(Component.literal("HUD Tailor"));
	}

	private static HudTailor t() {
		return Modules.HUD_TAILOR;
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		g.fill(0, 0, width, height, 0x38000000);
	}

	private float[] rect(Part p) {
		return t().screenBounds(p, Minecraft.getInstance(), width, height);
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

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		Part hovered = dragging != null ? dragging : partAt(mouseX, mouseY);
		Sidebar.Line line = dragging == null && hovered == Part.SCOREBOARD ? lineAt(mouseX, mouseY) : null;

		if (dragging != null && (snapX || snapY)) {
			if (snapX) g.fill(width / 2, 0, width / 2 + 1, height, Colors.accent(0.8f));
			if (snapY) g.fill(0, height / 2, width, height / 2 + 1, Colors.accent(0.8f));
		}

		for (Part p : Part.values()) {
			float[] r = rect(p);
			int x = Math.round(r[0]) - 1, y = Math.round(r[1]) - 1, w = Math.round(r[2]) + 2, h = Math.round(r[3]) + 2;
			boolean shown = t().shown(p), present = t().present(p, mc);
			if (!shown) g.fill(x, y, x + w, y + h, 0x30000000);
			int col = p == hovered ? Colors.ACCENT : shown && present ? Colors.white(0.4f) : Colors.white(0.12f);
			g.outline(x, y, w, h, col);
			if (p == hovered) continue;
			// a part with nothing showing just now is named, so you can still find and place it
			String label = !shown ? p.label + " (hidden)" : !present ? p.label : null;
			if (label != null && font.width(label) <= w + 20)
				g.text(font, label, x + w / 2 - font.width(label) / 2, y + h / 2 - 4, shown ? 0x66FFFFFF : 0x99FFFFFF, true);
		}

		if (line != null) {
			float[] r = rect(Part.SCOREBOARD);
			float s = t().layout(Part.SCOREBOARD).scale.get();
			float[] tl = toScreen(Part.SCOREBOARD, line.x(), line.y());
			g.fill(Math.round(tl[0]), Math.round(tl[1]), Math.round(tl[0] + line.w() * s), Math.round(tl[1] + line.h() * s), Colors.accent(0.3f));
			label(g, font, line.hidden() ? "Click to show this line" : "Click to hide this line", r);
		} else if (hovered != null) {
			Layout l = t().layout(hovered);
			String label = hovered.label + "  " + Math.round(l.scale.get() * 100) + "%" + (t().shown(hovered) ? "" : "  hidden");
			label(g, font, label, rect(hovered));
		}

		toolbar(g, font);

		if (dragging != null) g.requestCursor(CursorTypes.RESIZE_ALL);
		else if (line != null || inside(resetBtn) || inside(doneBtn) || inside(linesBtn)) g.requestCursor(CursorTypes.POINTING_HAND);
		else if (hovered != null) g.requestCursor(CursorTypes.RESIZE_ALL);
	}

	private float[] toScreen(Part p, float x, float y) {
		Minecraft mc = Minecraft.getInstance();
		float[] r = t().bounds(p, mc, width, height);
		Layout l = t().layout(p);
		float s = l.scale.get(), px = r[0] + r[2] / 2, py = r[1] + r[3] / 2;
		return new float[] {px + l.x.get() + (x - px) * s, py + l.y.get() + (y - py) * s};
	}

	/** A name tag above a box, or below it when there is no room above. */
	private void label(GuiGraphicsExtractor g, Font font, String text, float[] r) {
		int tw = font.width(text) + 8;
		int x = Math.max(2, Math.min(width - tw - 2, Math.round(r[0])));
		int y = r[1] - 14 < 2 ? Math.round(r[1] + r[3]) + 3 : Math.round(r[1]) - 14;
		g.fill(x, y, x + tw, y + 12, Colors.ACCENT);
		g.text(font, text, x + 4, y + 2, 0xFFFFFFFF, false);
	}

	private void toolbar(GuiGraphicsExtractor g, Font font) {
		String[] hints = {
			"Drag to move, scroll to resize",
			"Right-click to hide or show",
			"Middle-click puts one back",
			"Click a scoreboard line to hide it"};
		boolean armed = resetArmedAt >= 0 && now() - resetArmedAt < CONFIRM_MS;
		String reset = armed ? "Click again" : "Reset all";
		int hidden = t().hiddenHere();
		String lines = hidden == 1 ? "Show 1 hidden line" : "Show " + hidden + " hidden lines";
		int w = font.width("HUD Tailor");
		for (String h : hints) w = Math.max(w, font.width(h));
		w = Math.max(w, font.width(reset) + font.width("Done") + 24);
		w += 16;
		int x = 6, y = 6;
		int h = 12 + 4 + hints.length * 10 + 6 + 16 + (hidden > 0 ? 20 : 0) + 6;
		g.fill(x, y, x + w, y + h, 0xD0101014);
		g.outline(x, y, w, h, Colors.white(0.12f));
		g.text(font, "HUD Tailor", x + 8, y + 7, Colors.ACCENT, true);
		int ty = y + 22;
		for (String s : hints) {
			g.text(font, s, x + 8, ty, 0xFFB8B8C0, false);
			ty += 10;
		}
		ty += 4;
		int bw = font.width(reset) + 12;
		resetBtn = new int[] {x + 8, ty, bw, 16};
		button(g, font, reset, resetBtn, armed ? 0xFFB33A3A : 0xFF2A2A32);
		int dw = font.width("Done") + 12;
		doneBtn = new int[] {x + w - 8 - dw, ty, dw, 16};
		button(g, font, "Done", doneBtn, Colors.ACCENT);
		ty += 20;
		if (hidden > 0) {
			linesBtn = new int[] {x + 8, ty, font.width(lines) + 12, 16};
			button(g, font, lines, linesBtn, 0xFF2A2A32);
		} else {
			linesBtn = new int[4];
		}
	}

	private void button(GuiGraphicsExtractor g, Font font, String text, int[] r, int colour) {
		boolean hov = inside(r);
		g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], hov ? Colors.white(0.25f) : colour);
		if (hov) g.fill(r[0] + 1, r[1] + 1, r[0] + r[2] - 1, r[1] + r[3] - 1, colour);
		g.text(font, text, r[0] + 6, r[1] + 4, 0xFFFFFFFF, false);
	}

	private boolean inside(int[] r) {
		return r[2] > 0 && mouseX >= r[0] && mouseX < r[0] + r[2] && mouseY >= r[1] && mouseY < r[1] + r[3];
	}

	private static double now() {
		return System.nanoTime() / 1e6;
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
		if (event.button() == 0) {
			if (inside(doneBtn)) {
				onClose();
				return true;
			}
			if (inside(resetBtn)) {
				if (resetArmedAt >= 0 && now() - resetArmedAt < CONFIRM_MS) {
					resetArmedAt = -1;
					t().resetLayout();
					LeoneConfig.save();
				} else {
					resetArmedAt = now();
				}
				return true;
			}
			if (inside(linesBtn)) {
				t().showAllLinesHere();
				LeoneConfig.save();
				return true;
			}
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
		Minecraft.getInstance().gui.setScreen(LeoneScreen.returningTo(Modules.HUD_TAILOR));
	}
}
