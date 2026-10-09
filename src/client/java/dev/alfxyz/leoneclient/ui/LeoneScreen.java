package dev.alfxyz.leoneclient.ui;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import dev.alfxyz.leoneclient.LeoneClient;
import dev.alfxyz.leoneclient.LeoneConfig;
import dev.alfxyz.leoneclient.anim.Anim;
import dev.alfxyz.leoneclient.anim.ColorAnim;
import dev.alfxyz.leoneclient.anim.Ease;
import dev.alfxyz.leoneclient.hud.HudEditorScreen;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Gfx;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * The Leone Client menu: a radial wheel of categories with a side panel and a
 * dock. All geometry is laid out in a 1600x900 design space and scaled by
 * guiHeight / 900.
 */
public class LeoneScreen extends Screen {
	/** What the side panel shows. */
	public enum Kind { CATEGORY, SEARCH, SERVERS, FRIENDS, PLAYERS, CONFIGS, OVERLAYS }

	/** When each module last refused to switch on, for its card's shake. */
	private final java.util.Map<String, Double> denied = new java.util.HashMap<>();

	private static @Nullable LeoneScreen active;
	private static Canvas sharedCanvas;

	// ------------------------------------------------------------- geometry
	private static final float CX = 800, CY = 410;
	private static final float R_IN = 124, R_OUT = 254, R_LABEL = 189, R_ARC = 264, R_DOT = 110, R_HUB = 95;
	private static final float PANEL_X = 712, PANEL_W = 740, PANEL_PAD = 22, PANEL_R = 18;

	// ---------------------------------------------------------------- state
	private final @Nullable Screen parent;
	private double openedAt = -1;
	private double closingAt = -1;
	private @Nullable Supplier<Screen> afterClose;
	private @Nullable Kind initialKind;

	private boolean panelOpen;
	private Kind kind = Kind.CATEGORY;
	private int catIdx;
	private boolean panelShown;
	private Kind shownKind = Kind.CATEGORY;
	private int shownCat;
	private boolean panelOut;
	private double panelInAt, panelOutAt, connectorAt, settingsAt;
	private @Nullable Module settingsFor;
	private float[] panelRect = new float[4];

	private int hoverCat = -1;
	private boolean hubHover;
	private float ringTarget;
	private float pointerTarget;

	private final Anim ringRot = new Anim(0);
	private final Anim groupX = new Anim(0);
	private final Anim groupScale = new Anim(1);
	private final Anim pointer = new Anim(0);
	private final Anim pointerOpacity = new Anim(0);
	private final Anim arcOpacity = new Anim(0);
	/** The wheel's categories (the Staff category only for LeoneMC staff), and the angle each segment covers. */
	private final List<Category> cats = Category.shown();
	private final int segs = cats.size();
	private final float seg = 360f / segs;
	private final ColorAnim[] segFill = new ColorAnim[segs];
	private final ColorAnim[] segLabel = new ColorAnim[segs];
	private final Anim[] segScale = new Anim[segs];
	private final ColorAnim hubBorder = new ColorAnim(Colors.white(0.12f));
	private final ColorAnim hubGlow = new ColorAnim(Colors.white(0.05f));
	private final Anim logoScale = new Anim(1);
	private final Anim logoOpacity = new Anim(1);
	private int lastArc;

	private @Nullable Module binding;
	private double bindCapturedAt = -1;
	private Setting.@Nullable Slider dragging;
	private float dragX0, dragW;

	private double mouseX, mouseY;

	private final Ui ui;
	private final CategoryPage[] categoryPages = new CategoryPage[segs];
	private final SearchPage search = new SearchPage(this);
	private final ServersPage servers = new ServersPage(this);
	private final PlayersPage players = new PlayersPage(this);
	private final ConfigsPage configs = new ConfigsPage(this);
	private final FriendsPage friends = new FriendsPage(this);
	private final OverlaysPage overlays = new OverlaysPage(this);
	private final ModuleSettingsView settingsView = new ModuleSettingsView(this);
	private @Nullable String tipShown;
	private double tipSince;

	public LeoneScreen() {
		this(null, null);
	}

	/** Opens the menu over another screen, which it returns to when closed (used by Mod Menu). */
	public LeoneScreen(@Nullable Screen parent) {
		this(parent, null);
	}

	/** Opens the menu with a dock page already showing (used when returning from the HUD editor). */
	public LeoneScreen(@Nullable Kind initial) {
		this(null, initial);
	}

	private LeoneScreen(@Nullable Screen parent, @Nullable Kind initial) {
		super(Component.literal("Leone Client"));
		this.parent = parent;
		this.initialKind = initial;
		for (int i = 0; i < segs; i++) {
			segFill[i] = new ColorAnim(Colors.glass(0.66f));
			segLabel[i] = new ColorAnim(Colors.TEXT_DOCK);
			segScale[i] = new Anim(1);
			categoryPages[i] = new CategoryPage(this, cats.get(i));
		}
		Gfx.ensure();
		if (sharedCanvas == null) sharedCanvas = Gfx.newCanvas();
		ui = new Ui(sharedCanvas, Gfx.text(), Gfx.icons());
	}

	Ui ui() {
		return ui;
	}

	private static double now() {
		return System.nanoTime() / 1_000_000.0;
	}

	private static float turn(float prev, float target) {
		float d = (((target - prev) % 360) + 540) % 360 - 180;
		return prev + d;
	}

	// ------------------------------------------------------------ lifecycle

	@Override
	protected void init() {
		Gfx.ensure();
		active = this;
		Minecraft mc = Minecraft.getInstance();
		Window w = mc.getWindow();
		mouseX = MouseHandler.getScaledXPos(w, mc.mouseHandler.xpos());
		mouseY = MouseHandler.getScaledYPos(w, mc.mouseHandler.ypos());
	}

	@Override
	public void removed() {
		if (active == this) active = null;
		LeoneConfig.save();
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}

	@Override
	public void onClose() {
		startClose();
	}

	private boolean closing() {
		return closingAt >= 0;
	}

	private void startClose() {
		if (closing()) return;
		closingAt = now();
		binding = null;
		dragging = null;
		ui.focused = null;
		setHoverCat(-1, closingAt);
		hubHover = false;
	}

	/** 0..1 strength of the background treatment (blur and dim). */
	private float backdrop(double now, double duration) {
		if (openedAt < 0) return 0;
		if (closing()) {
			float open = Ease.progress(closingAt, openedAt, 0, duration, Ease.EASE);
			return open * (1 - Ease.progress(now, closingAt, 0, 300, Ease.EASE));
		}
		return Ease.progress(now, openedAt, 0, duration, Ease.EASE);
	}

	/** Blur radius the game renderer should use this frame, or -1 to leave it alone. */
	public static int blurRadiusOverride() {
		LeoneScreen s = active;
		if (s == null || Minecraft.getInstance().gui.screen() != s) return -1;
		if (Minecraft.getInstance().options.getMenuBackgroundBlurriness() <= 0 || !Modules.INTERFACE.blur.get()) return 0;
		// CSS blur(9px) at 900px tall; MC's blur is 3 box passes, variance r(r+1)
		double sigma = 9.0 * Minecraft.getInstance().getWindow().getHeight() / 900.0;
		double r = (-1 + Math.sqrt(1 + 4 * sigma * sigma)) / 2;
		return (int) Math.round(r * s.backdrop(now(), 520));
	}

	// -------------------------------------------------------------- render

	private float scale() {
		return height / 900f;
	}

	private float offsetX() {
		return width / 2f - 800 * scale();
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor g, int mx, int my, float a) {
		double now = now();
		if (openedAt < 0) openedAt = now;
		if (minecraft.level == null) extractPanorama(g, a);
		if (blurRadiusOverride() >= 1) g.blurBeforeThisStratum();
		float k = backdrop(now, 480);
		if (k <= 0) return;
		Canvas cv = ui.cv;
		cv.begin(Minecraft.getInstance().getWindow().getGuiScale());
		cv.push();
		cv.translate(offsetX(), 0);
		cv.scale(scale());
		drawScrim(cv, k);
		cv.pop();
		cv.flush(g);
	}

	/** radial-gradient(ellipse 55% 62% at 50% 45%, rgba(8,8,11,.62), rgba(8,8,11,.30)). */
	private void drawScrim(Canvas cv, float k) {
		int segs = 96;
		float[] ts = {0, 0.15f, 0.3f, 0.45f, 0.6f, 0.75f, 0.9f, 1f, 4f};
		int base = 0x08080B;
		cv.push();
		cv.translate(800, 405);
		cv.scale(880, 558);
		for (int r = 0; r + 1 < ts.length; r++) {
			float t0 = ts[r], t1 = ts[r + 1];
			int c0 = Colors.rgba(base, (0.62f + (0.30f - 0.62f) * Math.min(1, t0)) * k);
			int c1 = Colors.rgba(base, (0.62f + (0.30f - 0.62f) * Math.min(1, t1)) * k);
			for (int s = 0; s < segs; s++) {
				double a0 = Math.PI * 2 * s / segs, a1 = Math.PI * 2 * (s + 1) / segs;
				float x0 = (float) Math.cos(a0), y0 = (float) Math.sin(a0), x1 = (float) Math.cos(a1), y1 = (float) Math.sin(a1);
				cv.quad(x0 * t0, y0 * t0, x1 * t0, y1 * t0, x1 * t1, y1 * t1, x0 * t1, y0 * t1, c0, c0, c1, c1);
			}
		}
		cv.pop();
	}

	/** Dev profiling: total nanoseconds spent building frames, and the frame count. */
	public static long profileNanos, profileFrames;

	@Override
	public void extractRenderState(GuiGraphicsExtractor g, int mx, int my, float a) {
		long t0 = System.nanoTime();
		extractMenu(g);
		profileNanos += System.nanoTime() - t0;
		profileFrames++;
	}

	private void extractMenu(GuiGraphicsExtractor g) {
		Gfx.ensure();
		Modules.INTERFACE.apply();
		double now = now();
		if (openedAt < 0) openedAt = now;
		if (initialKind != null) {
			openPage(initialKind, 0);
			initialKind = null;
		}
		if (panelOut && panelShown && now - panelOutAt >= 260) {
			panelShown = false;
			panelOut = false;
			settingsFor = null;
		}

		Canvas cv = ui.cv;
		TextRenderer text = ui.text;
		text.setGraphics(g);
		cv.begin(Minecraft.getInstance().getWindow().getGuiScale());
		ui.beginFrame(now, mouseX, mouseY);

		float s = scale(), ox = offsetX();
		float mdx = (float) ((mouseX - ox) / s), mdy = (float) (mouseY / s);

		cv.push();
		cv.translate(ox, 0);
		cv.scale(s);
		float closeP = closing() ? Ease.progress(now, closingAt, 0, 300, Ease.EASE) : 0;
		cv.scaleAround(1 - 0.05f * closeP, CX, CY);
		cv.mulAlpha(1 - closeP);

		drawPulses(cv, now);
		drawWheel(cv, now, mdx, mdy);
		drawConnector(cv, now);
		drawPanel(cv, now);
		drawDock(cv, now);
		cv.pop();
		drawTooltip(cv, now);
		cv.flush(g);
		text.setGraphics(null);

		if (!closing()) {
			if (ui.wantText) g.requestCursor(CursorTypes.IBEAM);
			else if (ui.wantPointer) g.requestCursor(CursorTypes.POINTING_HAND);
		}
		if (closing() && closeP >= 1) {
			Minecraft mc = Minecraft.getInstance();
			Supplier<Screen> next = afterClose;
			mc.execute(() -> {
				if (mc.gui.screen() == this) mc.gui.setScreen(next != null ? next.get() : parent);
			});
		}
	}

	/** Draws the tooltip for whatever has been hovered for a moment. */
	private void drawTooltip(Canvas cv, double now) {
		String tip = closing() ? null : ui.tipText;
		if (tip == null || !tip.equals(tipShown)) {
			tipShown = tip;
			tipSince = now;
		}
		if (tip == null) return;
		float p = Ease.progress(now, tipSince, 380, 160, Ease.EASE);
		if (p <= 0) return;
		TextRenderer text = ui.text;
		float s = scale(), ox = offsetX();
		float mx = (float) ((mouseX - ox) / s), my = (float) (mouseY / s);
		float maxW = 300;
		List<String> lines = ui.wrap(tip, Ui.TIP, maxW, 6);
		float w = 0;
		for (String l : lines) w = Math.max(w, text.width(l, Ui.TIP));
		float lh = text.lineHeight(Ui.TIP);
		float bw = w + 24, bh = lines.size() * lh + 18;
		float left = -ox / s, rightEdge = (width - ox) / s;
		float x = mx + 14, y = my + 20;
		if (x + bw > rightEdge - 8) x = mx - 14 - bw;
		if (x < left + 8) x = left + 8;
		if (y + bh > 892) y = my - 12 - bh;
		cv.push();
		cv.translate(ox, 0);
		cv.scale(s);
		cv.mulAlpha(p);
		cv.translate(0, 4 * (1 - p));
		cv.boxShadow(x, y, bw, bh, 10, 0, 8, 14, Colors.black(0.35f));
		cv.fillRoundRect(x, y, bw, bh, 10, Colors.glass(0.94f));
		cv.borderRoundRect(x, y, bw, bh, 10, 1, Colors.white(0.12f));
		for (int i = 0; i < lines.size(); i++) text.draw(cv, lines.get(i), x + 12, y + 9 + i * lh + text.ascent(Ui.TIP), Ui.TIP, Colors.TEXT);
		cv.pop();
	}

	// ---------------------------------------------------------------- open

	private void drawPulses(Canvas cv, double now) {
		pulse(cv, now, 0, 900, Colors.white(0.6f));
		pulse(cv, now, 140, 1050, Colors.ACCENT);
	}

	private void pulse(Canvas cv, double now, double delay, double dur, int color) {
		double t = now - openedAt - delay;
		if (t < 0 || t > dur) return;
		float p = Ease.progress(now, openedAt, delay, dur, Ease.PULSE);
		float sc = 0.35f + 1.85f * p;
		cv.push();
		cv.mulAlpha(0.65f * (1 - p));
		cv.ring(CX, CY, 148 * sc, 150 * sc, color);
		cv.pop();
	}

	// --------------------------------------------------------------- wheel

	private int selectedSegment() {
		return panelOpen && kind == Kind.CATEGORY ? catIdx : -1;
	}

	private void setHoverCat(int i, double now) {
		if (i == hoverCat) return;
		if (i >= 0) {
			pointerTarget = turn(pointerTarget, i * seg);
			pointer.set(pointerTarget, now, 280, Ease.SNAP);
			if (Modules.INTERFACE.sounds.get()) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.0F, 0.12F));
			}
		}
		hoverCat = i;
	}

	private void updateWheelStyles(double now) {
		int selected = selectedSegment();
		for (int i = 0; i < segs; i++) {
			boolean hov = hoverCat == i;
			boolean sel = selected == i;
			boolean dimmed = panelOpen && !sel;
			int fill = Colors.glass(0.66f);
			if (sel) fill = Colors.accent(0.36f);
			else if (hov) fill = dimmed ? Colors.rgba(0x2E2E36, 0.72f) : Colors.accent(0.30f);
			else if (dimmed) fill = Colors.glass(0.5f);
			segFill[i].set(fill, now, 180, Ease.EASE);
			segLabel[i].set(sel || hov ? Colors.WHITE : dimmed ? Colors.TEXT_DIM : Colors.TEXT_DOCK, now, 180, Ease.EASE);
			segScale[i].set(hov && !sel ? 1.06f : 1f, now, 200, Ease.SNAP);
		}
		arcOpacity.set(selected >= 0 || hoverCat >= 0 ? 1 : 0, now, 160, Ease.EASE);
		pointerOpacity.set(hoverCat >= 0 && !panelOpen ? 1 : 0, now, 160, Ease.EASE);
		hubBorder.set(hubHover ? Colors.accent(0.85f) : Colors.white(0.12f), now, 200, Ease.EASE);
		hubGlow.set(panelOpen || hoverCat >= 0 || hubHover ? Colors.accent(0.24f) : Colors.white(0.05f), now, 300, Ease.EASE);
		logoScale.set(hubHover ? 0.88f : 1f, now, 240, Ease.SNAP);
		logoOpacity.set(hubHover ? 0.82f : 1f, now, 200, Ease.EASE);
	}

	private void drawWheel(Canvas cv, double now, float mdx, float mdy) {
		float gx = groupX.get(now), gs = groupScale.get(now), rot = ringRot.get(now);

		if (!closing()) {
			float wx = CX + (mdx - CX - gx) / gs, wy = CY + (mdy - CY) / gs;
			float dx = wx - CX, dy = wy - CY, dist = (float) Math.hypot(dx, dy);
			int hc = -1;
			if (dist >= R_IN && dist <= R_OUT) {
				double ang = Math.toDegrees(Math.atan2(dy, dx)) - rot;
				hc = Math.floorMod((int) Math.round((ang + 90) / seg), segs);
			}
			setHoverCat(hc, now);
			hubHover = dist <= R_HUB;
			if (hc >= 0 || hubHover) ui.wantPointer = true;
		}
		updateWheelStyles(now);

		cv.push();
		cv.translate(CX + gx, CY);
		cv.scale(gs);
		cv.translate(-CX, -CY);

		float ringFade = Ease.progress(now, openedAt, 320, 600, Ease.EASE);
		if (ringFade > 0) cv.ring(CX, CY, 301, 302, Colors.white(0.07f * ringFade));

		int n = 48;
		float[][] ox = new float[segs][n], oy = new float[segs][n], ix = new float[segs][n], iy = new float[segs][n];
		float dO = (float) Math.toDegrees(3 / R_OUT), dI = (float) Math.toDegrees(3 / R_IN);
		for (int i = 0; i < segs; i++) {
			float mid = -90 + i * seg;
			for (int j = 0; j < n; j++) {
				float t = j / (n - 1f);
				double ao = Math.toRadians(mid - seg / 2 + dO + (seg - 2 * dO) * t), ai = Math.toRadians(mid - seg / 2 + dI + (seg - 2 * dI) * t);
				ox[i][j] = CX + (float) Math.cos(ao) * R_OUT;
				oy[i][j] = CY + (float) Math.sin(ao) * R_OUT;
				ix[i][j] = CX + (float) Math.cos(ai) * R_IN;
				iy[i][j] = CY + (float) Math.sin(ai) * R_IN;
			}
		}

		TextRenderer text = ui.text;
		for (int i = 0; i < segs; i++) {
			float p = Ease.progress(now, openedAt, 150 + 330f / segs * i, 560, Ease.SNAP);
			if (p <= 0) continue;
			cv.push();
			cv.rotateAround(rot - 34 * (1 - p), CX, CY);
			cv.scaleAround(0.72f + 0.28f * p, CX, CY);
			cv.mulAlpha(p);
			cv.fillStrip(ox[i], oy[i], ix[i], iy[i], n, segFill[i].get(now));

			double mid = Math.toRadians(-90 + i * seg);
			float lx = CX + (float) Math.cos(mid) * R_LABEL, ly = CY + (float) Math.sin(mid) * R_LABEL;
			cv.push();
			cv.translate(lx, ly);
			cv.rotate(-rot);
			cv.scale(segScale[i].get(now));
			int col = segLabel[i].get(now);
			String count = onCount(cats.get(i));
			float blockH = 26 + 7 + text.lineHeight(Ui.LABEL) + 2 + text.lineHeight(Ui.SMALL);
			float top = -blockH / 2;
			ui.icons.draw(cv, cats.get(i).icon, -13, top, 26, 1.7f, col);
			String name = cats.get(i).displayName;
			text.draw(cv, name, -text.width(name, Ui.LABEL) / 2, top + 33 + text.ascent(Ui.LABEL), Ui.LABEL, col);
			float cy = top + 33 + text.lineHeight(Ui.LABEL) + 2;
			text.draw(cv, count, -text.width(count, Ui.SMALL) / 2, cy + text.ascent(Ui.SMALL), Ui.SMALL, Colors.alpha(col, 0.62f));
			cv.pop();
			cv.pop();
		}

		float svgFade = Ease.progress(now, openedAt, 420, 500, Ease.EASE);
		if (svgFade > 0) {
			cv.push();
			cv.rotateAround(rot, CX, CY);
			cv.mulAlpha(svgFade);
			for (int i = 0; i < segs; i++) {
				float[] lx = new float[n * 2], ly = new float[n * 2];
				for (int j = 0; j < n; j++) {
					lx[j] = ox[i][j];
					ly[j] = oy[i][j];
					lx[2 * n - 1 - j] = ix[i][j];
					ly[2 * n - 1 - j] = iy[i][j];
				}
				cv.stroke(lx, ly, 2 * n, true, 1, Colors.white(0.14f));
			}
			float ao = arcOpacity.get(now);
			int selected = selectedSegment();
			if (selected >= 0) lastArc = selected;
			else if (hoverCat >= 0) lastArc = hoverCat;
			if (ao > 0) {
				float mid = -90 + lastArc * seg;
				float d = (float) Math.toDegrees(3 / R_ARC) + 1.5f;
				cv.push();
				cv.mulAlpha(ao);
				cv.arcGlow(CX, CY, R_ARC, 3, mid - seg / 2 + d, mid + seg / 2 - d, 3, Colors.accent(0.7f));
				cv.arc(CX, CY, R_ARC, 3, mid - seg / 2 + d, mid + seg / 2 - d, Colors.ACCENT);
				cv.pop();
			}
			cv.pop();
		}

		float po = pointerOpacity.get(now);
		if (po > 0) {
			cv.push();
			cv.rotateAround(pointer.get(now), CX, CY);
			cv.mulAlpha(po);
			cv.discGlow(CX, CY - R_DOT, 5, 6, Colors.accent(0.7f));
			cv.fillCircle(CX, CY - R_DOT, 5, Colors.ACCENT);
			cv.pop();
		}

		drawHub(cv, now);
		cv.pop();
	}

	private static String onCount(Category c) {
		int on = 0, all = 0;
		for (Module m : Modules.of(c)) {
			if (!m.toggleable()) continue;
			all++;
			if (m.enabled()) on++;
		}
		return all == 0 ? "settings" : on + " of " + all + " on";
	}

	private void drawHub(Canvas cv, double now) {
		// hub entrance: 0% {0, scale .2, -120deg} 65% {1, 1.08, 8deg} 100% {1, 1, 0}; easing per keyframe segment
		double t = (now - openedAt - 40) / 720.0;
		if (t <= 0) return;
		float op, sc, rt;
		if (t < 0.65) {
			float q = Ease.SNAP.apply((float) (t / 0.65));
			op = q;
			sc = 0.2f + 0.88f * q;
			rt = -120 + 128 * q;
		} else {
			float q = Ease.SNAP.apply((float) Math.min(1, (t - 0.65) / 0.35));
			op = 1;
			sc = 1.08f - 0.08f * q;
			rt = 8 - 8 * q;
		}
		cv.push();
		cv.mulAlpha(op);
		cv.scaleAround(sc, CX, CY);
		cv.rotateAround(rt, CX, CY);
		cv.circleShadow(CX, CY, R_HUB, 0, 24, 30, Colors.black(0.45f));
		cv.circleShadow(CX, CY, R_HUB, 0, 0, 35, hubGlow.get(now));
		cv.ring(CX, CY, R_HUB, R_HUB + 8, Colors.black(0.16f));
		cv.fillCircle(CX, CY, R_HUB, Colors.glass(0.74f));
		cv.ring(CX, CY, R_HUB - 1, R_HUB, hubBorder.get(now));
		cv.push();
		cv.scaleAround(logoScale.get(now), CX, CY);
		cv.mulAlpha(logoOpacity.get(now));
		Gfx.logo().drawGlow(cv, CX - 44, CY - 44, 88, 9, Colors.accent(0.45f));
		Gfx.logo().draw(cv, CX - 44, CY - 44, 88, Colors.WHITE);
		cv.pop();
		cv.pop();
	}

	private void drawConnector(Canvas cv, double now) {
		if (!panelOpen || kind != Kind.CATEGORY) return;
		float p = Ease.progress(now, connectorAt, 380, 280, Ease.EASE);
		if (p <= 0) return;
		float w = 29 * p;
		cv.rectGlow(683, 409, w, 2, 5, Colors.accent(0.7f));
		cv.fillRoundRect(683, 409, w, 2, 1, Colors.ACCENT);
	}

	// --------------------------------------------------------------- panel

	private Page page(Kind k, int cat) {
		return switch (k) {
			case CATEGORY -> categoryPages[cat];
			case SEARCH -> search;
			case SERVERS -> servers;
			case PLAYERS -> players;
			case CONFIGS -> configs;
			case FRIENDS -> friends;
			case OVERLAYS -> overlays;
		};
	}

	private Page shownPage() {
		return page(shownKind, shownCat);
	}

	private float contentHeight() {
		if (settingsFor != null) return settingsView.height(ui, settingsFor);
		return shownPage().height(ui);
	}

	private void drawPanel(Canvas cv, double now) {
		if (!panelShown) return;
		float h = contentHeight() + PANEL_PAD * 2 + 2;
		float top = CY - h / 2;
		float alpha, tx, clip;
		if (panelOut) {
			float p = Ease.progress(now, panelOutAt, 0, 240, Ease.EASE);
			alpha = 1 - p;
			tx = -28 * p;
			clip = 1;
		} else {
			float p = Ease.progress(now, panelInAt, 120, 560, Ease.SNAP);
			alpha = p;
			tx = -40 * (1 - p);
			clip = p;
		}
		panelRect = new float[] {cv.tx(PANEL_X + tx, top), cv.ty(PANEL_X + tx, top), cv.tx(PANEL_X + tx + PANEL_W, top + h), cv.ty(PANEL_X + tx + PANEL_W, top + h)};
		if (alpha <= 0) return;
		cv.push();
		cv.translate(tx, 0);
		cv.mulAlpha(alpha);
		if (clip < 1) cv.clipRect(PANEL_X - 1, top - 1, PANEL_X + PANEL_W * clip, top + h + 1);
		cv.fillRoundRect(PANEL_X, top, PANEL_W, h, PANEL_R, Colors.glass(0.78f));
		cv.borderRoundRect(PANEL_X, top, PANEL_W, h, PANEL_R, 1, Colors.white(0.1f));
		float x0 = PANEL_X + 1 + PANEL_PAD, y0 = top + 1 + PANEL_PAD;
		ui.interactive = !panelOut && !closing();
		ui.hit(PANEL_X, top, PANEL_W, h, null, null);
		if (settingsFor != null) settingsView.draw(ui, x0, y0, settingsFor, settingsAt);
		else shownPage().draw(ui, x0, y0);
		cv.pop();
	}

	private void beginContent(double at, boolean first) {
		ui.contentAt = at;
		ui.contentBase = first ? 320 : 40;
	}

	// ---------------------------------------------------------------- dock

	private enum DockItem {
		SEARCH(null, Icons.SEARCH, Kind.SEARCH),
		SERVERS("Servers", Icons.SERVER, Kind.SERVERS),
		FRIENDS("Friends", Icons.FRIENDS, Kind.FRIENDS),
		PLAYERS("Players", Icons.USER, Kind.PLAYERS),
		CONFIGS("Configs", Icons.CONFIGS, Kind.CONFIGS),
		OVERLAYS("Overlays", Icons.OVERLAYS, Kind.OVERLAYS),
		HUD("Modify HUD", Icons.HUD, null);

		final @Nullable String label;
		final String icon;
		final @Nullable Kind kind;

		DockItem(@Nullable String label, String icon, @Nullable Kind kind) {
			this.label = label;
			this.icon = icon;
			this.kind = kind;
		}
	}

	private void dockClick(DockItem item) {
		if (item.kind == null) {
			openHudEditor();
		} else if (panelOpen && kind == item.kind && settingsFor == null) {
			back();
		} else {
			openPage(item.kind, 0);
		}
	}

	private void drawDock(Canvas cv, double now) {
		float p = Ease.progress(now, openedAt, 320, 520, Ease.SNAP);
		if (p <= 0) return;
		TextRenderer text = ui.text;
		DockItem[] items = DockItem.values();
		float[] itemW = new float[items.length];
		float inner = 0;
		for (int i = 0; i < items.length; i++) {
			itemW[i] = items[i].label == null ? 36 : 10 + 15 + 7 + text.width(items[i].label, Ui.DOCK) + 12;
			inner += itemW[i] + (i > 0 ? 2 : 0);
			if (i == 0) inner += 2 + 7;
		}
		String cfg = LeoneConfig.activeConfig;
		float cfgW = 10 + 7 + 8 + text.width(cfg, Ui.DOCK) + 12;
		inner += 2 + 7 + 2 + cfgW;
		float w = inner + 10 + 2, h = 48;
		float x = 800 - w / 2, y = 900 - 26 - h;
		cv.push();
		cv.translate(0, 26 * (1 - p));
		cv.mulAlpha(p);
		cv.boxShadow(x, y, w, h, 14, 0, 16, 20, Colors.black(0.35f));
		cv.fillRoundRect(x, y, w, h, 14, Colors.glass(0.7f));
		cv.borderRoundRect(x, y, w, h, 14, 1, Colors.white(0.09f));
		ui.interactive = !closing();
		ui.hit(x, y, w, h, null, null);
		float cx = x + 1 + 5, mid = y + h / 2;
		for (int i = 0; i < items.length; i++) {
			DockItem item = items[i];
			if (i > 0) cx += 2;
			boolean activeItem = item.kind != null && panelOpen && kind == item.kind;
			dockButton(cv, item.name(), cx, mid - 18, itemW[i], 36, activeItem, () -> dockClick(item));
			int fg = activeItem || ui.hovered(cx, mid - 18, itemW[i], 36) ? Colors.WHITE : Colors.TEXT_DOCK;
			if (item.label == null) {
				ui.icons.draw(cv, item.icon, cx + 10, mid - 8, 16, 2, fg);
			} else {
				ui.icons.draw(cv, item.icon, cx + 10, mid - 7.5f, 15, 1.8f, fg);
				text.draw(cv, item.label, cx + 10 + 15 + 7, text.baselineFor(Ui.DOCK, mid), Ui.DOCK, fg);
			}
			cx += itemW[i];
			if (i == 0) {
				cx += 2;
				cv.fillRect(cx + 3, mid - 9, cx + 4, mid + 9, Colors.white(0.1f));
				cx += 7;
			}
		}
		cx += 2;
		cv.fillRect(cx + 3, mid - 9, cx + 4, mid + 9, Colors.white(0.1f));
		cx += 7 + 2;
		dockButton(cv, "config", cx, mid - 18, cfgW, 36, false, () -> openPage(Kind.CONFIGS, 0));
		cv.discGlow(cx + 10 + 3.5f, mid, 3.5f, 4, Colors.accent(0.7f));
		cv.fillCircle(cx + 10 + 3.5f, mid, 3.5f, Colors.ACCENT);
		text.draw(cv, cfg, cx + 10 + 7 + 8, text.baselineFor(Ui.DOCK, mid), Ui.DOCK, Colors.TEXT);
		cv.pop();
	}

	private void dockButton(Canvas cv, String key, float x, float y, float w, float h, boolean activeItem, Runnable onClick) {
		boolean hov = ui.hovered(x, y, w, h);
		ColorAnim bg = ui.color("dock#" + key, 0);
		bg.set(activeItem ? Colors.accent(0.22f) : hov ? Colors.white(0.07f) : Colors.white(0f), ui.now, 150, Ease.EASE);
		cv.fillRoundRect(x, y, w, h, 10, bg.get(ui.now));
		if (activeItem) cv.borderRoundRect(x, y, w, h, 10, 1, Colors.accent(0.45f));
		ui.hit(x, y, w, h, onClick, null);
	}

	// ------------------------------------------------------------- actions

	/** Shows a page in the side panel, sliding the wheel aside if needed. */
	void openPage(Kind k, int cat) {
		double now = now();
		if (panelOpen && kind == k && (k != Kind.CATEGORY || catIdx == cat) && settingsFor == null) return;
		boolean switching = panelOpen;
		boolean wasCategory = panelOpen && kind == Kind.CATEGORY;
		kind = k;
		catIdx = cat;
		panelOpen = true;
		settingsFor = null;
		binding = null;
		dragging = null;
		ui.focused = null;
		shownKind = k;
		shownCat = cat;
		if (switching && panelShown && !panelOut) {
			beginContent(now + 30, false);
		} else {
			panelShown = true;
			panelOut = false;
			panelInAt = now;
			beginContent(now, true);
		}
		if (k == Kind.CATEGORY) {
			if (!wasCategory) connectorAt = now;
			ringTarget = turn(ringTarget, 90 - seg * cat);
		} else {
			ringTarget = turn(ringTarget, 0);
		}
		ringRot.set(ringTarget, now, 680, Ease.SNAP);
		groupX.set(-330, now, 640, Ease.SNAP);
		groupScale.set(0.8f, now, 640, Ease.SNAP);
		page(k, cat).opened();
	}

	private void back() {
		double now = now();
		panelOpen = false;
		panelOut = true;
		panelOutAt = now;
		binding = null;
		ui.focused = null;
		ringTarget = turn(ringTarget, 0);
		ringRot.set(ringTarget, now, 680, Ease.SNAP);
		groupX.set(0, now, 640, Ease.SNAP);
		groupScale.set(1, now, 640, Ease.SNAP);
	}

	void openHudEditor() {
		afterClose = HudEditorScreen::new;
		startClose();
	}

	/**
	 * Switches a module on or off. One that cannot work here (its card says why) is not switched on:
	 * its card shakes instead.
	 */
	void toggle(Module m) {
		if (!m.enabled() && m.unavailable() != null) {
			denied.put(m.key(), now());
			if (Modules.INTERFACE.sounds.get()) {
				Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BASS.value(), 0.7F, 0.5F));
			}
			return;
		}
		m.toggle();
	}

	/** Sideways offset, in design pixels, of a card whose module just refused to switch on. */
	float shake(Module m) {
		Double at = denied.get(m.key());
		if (at == null) return 0;
		double t = (now() - at) / 420.0;
		if (t >= 1) {
			denied.remove(m.key());
			return 0;
		}
		return (float) (Math.sin(t * Math.PI * 6) * 6 * (1 - t));
	}

	/** Shows a player's profile on the Players page. */
	void openPlayer(java.util.UUID uuid, String name) {
		openPage(Kind.PLAYERS, 0);
		players.show(uuid, name);
	}

	/** Opens the menu straight onto a player's profile (the /leone profile command). */
	public static LeoneScreen forPlayer(java.util.UUID uuid, String name) {
		LeoneScreen s = new LeoneScreen(Kind.PLAYERS);
		s.players.show(uuid, name);
		return s;
	}

	/** Index of a category on this wheel, or -1 when it is not shown. */
	public int segmentOf(Category c) {
		return cats.indexOf(c);
	}

	void openSettings(Module m) {
		settingsFor = m;
		settingsAt = now();
		binding = null;
		ui.focused = null;
	}

	void closeSettings() {
		settingsFor = null;
		binding = null;
		beginContent(now() + 30, false);
	}

	void focus(@Nullable TextInput in) {
		ui.focused = in;
		if (in != null) in.lastActivity = now();
	}

	@Nullable Module binding() {
		return binding;
	}

	void setBinding(@Nullable Module m) {
		binding = m;
	}

	void beginDrag(Canvas cv, Setting.Slider sl, float x, float w) {
		dragging = sl;
		dragX0 = cv.tx(x, 0);
		dragW = cv.tx(x + w, 0) - dragX0;
		updateDrag(mouseX);
	}

	private void updateDrag(double gx) {
		if (dragging == null || dragW <= 0) return;
		dragging.set(dragging.min + (float) ((gx - dragX0) / dragW) * (dragging.max - dragging.min));
	}

	// ------------------------------------------------- dev automation hooks

	/** GUI coordinates of a design-space point. */
	public float[] designToGui(float dx, float dy) {
		return new float[] {offsetX() + dx * scale(), dy * scale()};
	}

	/** Centre of a segment's label, at the wheel's resting transform for the current state. */
	public float[] debugSegment(int i) {
		float gx = groupX.target(), gs = groupScale.target(), rot = ringRot.target();
		double a = Math.toRadians(-90 + i * seg + rot);
		return designToGui(CX + gx + (float) Math.cos(a) * R_LABEL * gs, CY + (float) Math.sin(a) * R_LABEL * gs);
	}

	public float[] debugHub() {
		return designToGui(CX + groupX.target(), CY);
	}

	/** Centre of module card {@code i} in the open category. */
	public float[] debugTile(int i) {
		float top = CY - (contentHeight() + PANEL_PAD * 2 + 2) / 2;
		float x0 = PANEL_X + 1 + PANEL_PAD, y0 = top + 1 + PANEL_PAD + 38 + 18;
		return designToGui(x0 + (i % 2) * (ModuleCard.W + ModuleCard.GAP) + ModuleCard.W / 3, y0 + (i / 2) * (ModuleCard.H + ModuleCard.GAP) + ModuleCard.H / 2);
	}

	/** Design-space point inside the panel content, relative to its top-left. */
	public float[] debugPanel(float dx, float dy) {
		float top = CY - (contentHeight() + PANEL_PAD * 2 + 2) / 2;
		return designToGui(PANEL_X + 1 + PANEL_PAD + dx, top + 1 + PANEL_PAD + dy);
	}

	public float[] debugBackButton() {
		float top = CY - (contentHeight() + PANEL_PAD * 2 + 2) / 2;
		return designToGui(PANEL_X + 1 + PANEL_PAD + 19, top + 1 + PANEL_PAD + 19);
	}

	/** Centre of a dock item: 0 search, 1 servers, 2 friends, 3 players, 4 configs, 5 overlays, 6 modify HUD. */
	public float[] debugDock(int i) {
		TextRenderer text = ui.text;
		DockItem[] items = DockItem.values();
		float[] itemW = new float[items.length];
		float inner = 0;
		for (int k = 0; k < items.length; k++) {
			itemW[k] = items[k].label == null ? 36 : 10 + 15 + 7 + text.width(items[k].label, Ui.DOCK) + 12;
			inner += itemW[k] + (k > 0 ? 2 : 0);
			if (k == 0) inner += 2 + 7;
		}
		inner += 2 + 7 + 2 + 10 + 7 + 8 + text.width(LeoneConfig.activeConfig, Ui.DOCK) + 12;
		float cx = 800 - (inner + 12) / 2 + 6;
		for (int k = 0; k < i; k++) cx += itemW[k] + 2 + (k == 0 ? 9 : 0);
		return designToGui(cx + itemW[i] / 2, 900 - 26 - 24);
	}

	/** Focuses the shown page's text field. */
	public void debugFocusInput() {
		if (panelShown) focus(shownPage().input());
	}

	/** Centre of overlay tile {@code i} on the Overlays page. */
	public float[] debugOverlayTile(int i) {
		float top = CY - (contentHeight() + PANEL_PAD * 2 + 2) / 2;
		float x0 = PANEL_X + 1 + PANEL_PAD, y0 = top + 1 + PANEL_PAD + 38 + 18;
		float tw = (Page.W - 16) / 3;
		return designToGui(x0 + (i % 3) * (tw + 8) + tw / 2, y0 + (i / 3) * (56 + 8) + 28);
	}

	/** Types text into the focused field (or starts a search), as if from the keyboard. */
	public void debugType(String s) {
		for (int i = 0; i < s.length(); i++) charTyped(new CharacterEvent(s.charAt(i)));
	}

	// --------------------------------------------------------------- input

	@Override
	public void mouseMoved(double x, double y) {
		mouseX = x;
		mouseY = y;
	}

	@Override
	public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
		mouseX = event.x();
		mouseY = event.y();
		if (closing() || openedAt < 0) return true;
		int button = event.button();
		if (binding != null) {
			binding = null;
			return true;
		}
		for (int i = ui.hits.size() - 1; i >= 0; i--) {
			Ui.Hit h = ui.hits.get(i);
			if (!h.contains(mouseX, mouseY)) continue;
			if (h.input() == null) ui.focused = null;
			Runnable r = button == 0 ? h.left() : button == 1 ? h.right() : null;
			if (r != null) r.run();
			return true;
		}
		ui.focused = null;
		if (button != 0) return true;
		double now = now();
		float s = scale(), ox = offsetX();
		float mdx = (float) ((mouseX - ox) / s), mdy = (float) (mouseY / s);
		float gx = groupX.get(now), gs = groupScale.get(now);
		float wx = CX + (mdx - CX - gx) / gs, wy = CY + (mdy - CY) / gs;
		float dist = (float) Math.hypot(wx - CX, wy - CY);
		if (dist <= R_HUB) {
			if (panelOpen) back();
			else startClose();
		} else if (dist >= R_IN && dist <= R_OUT) {
			double ang = Math.toDegrees(Math.atan2(wy - CY, wx - CX)) - ringRot.get(now);
			openPage(Kind.CATEGORY, Math.floorMod((int) Math.round((ang + 90) / seg), segs));
		}
		return true;
	}

	@Override
	public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
		mouseX = event.x();
		mouseY = event.y();
		if (dragging != null) {
			updateDrag(event.x());
			return true;
		}
		return false;
	}

	@Override
	public boolean mouseReleased(MouseButtonEvent event) {
		dragging = null;
		return true;
	}

	@Override
	public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
		if (closing() || !panelShown || panelOut || settingsFor != null) return false;
		if (x >= panelRect[0] && x < panelRect[2] && y >= panelRect[1] && y < panelRect[3]) {
			return shownPage().scroll(scrollY);
		}
		return false;
	}

	@Override
	public boolean keyPressed(KeyEvent event) {
		if (closing()) return true;
		if (binding != null) {
			int key = event.key();
			if (key == GLFW.GLFW_KEY_ESCAPE) {
				binding = null;
			} else if (key == GLFW.GLFW_KEY_BACKSPACE || key == GLFW.GLFW_KEY_DELETE) {
				binding.bind = -1;
				binding = null;
			} else if (key != InputConstants.UNKNOWN.getValue()) {
				binding.bind = key;
				binding = null;
			}
			bindCapturedAt = now();
			return true;
		}
		TextInput focused = ui.focused;
		if (focused != null) {
			if (event.isEscape()) {
				if (!focused.value().isEmpty()) focused.clear();
				else ui.focused = null;
				return true;
			}
			if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
				if (panelShown && settingsFor == null) shownPage().submit();
				else ui.focused = null;
				return true;
			}
			focused.key(event);
			// typing (including Shift for capitals) must not trigger menu shortcuts
			return true;
		}
		if (event.isEscape() || LeoneClient.openKey.matches(event)) {
			startClose();
			return true;
		}
		if (event.key() == GLFW.GLFW_KEY_F && event.hasControlDown()) {
			openPage(Kind.SEARCH, 0);
			return true;
		}
		return false;
	}

	@Override
	public boolean charTyped(CharacterEvent event) {
		if (closing() || binding != null || now() - bindCapturedAt < 100) return true;
		TextInput focused = ui.focused;
		if (focused != null) {
			focused.insert(event.codepointAsString());
			return true;
		}
		if (Character.isLetterOrDigit(event.codepoint())) {
			if (panelOpen && kind == Kind.SEARCH && settingsFor == null) {
				search.query.insert(event.codepointAsString());
			} else {
				openPage(Kind.SEARCH, 0);
				search.query.set(event.codepointAsString());
			}
			focus(search.query);
			return true;
		}
		return false;
	}
}
