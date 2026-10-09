package dev.alfxyz.leoneclient.hud;

import com.google.gson.JsonObject;
import dev.alfxyz.leoneclient.LeoneClientMod;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Gfx;
import java.util.List;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.effect.MobEffectInstance;

/** Registry, layout, persistence and in-game rendering of the overlays. */
public final class Hud {
	public static final List<Overlay> ALL = Overlays.create();
	private static Canvas canvas;

	private Hud() {
	}

	public static void register() {
		HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT, LeoneClientMod.id("overlays"), Hud::render);
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			if (mc.player == null) return;
			for (Overlay o : ALL) o.tick(mc);
		});
	}

	/** Design-space scale: GUI units per design px. */
	public static float scale() {
		return Minecraft.getInstance().getWindow().getGuiScaledHeight() / 900f;
	}

	/** Width of the screen in design px. */
	public static float designWidth() {
		return Minecraft.getInstance().getWindow().getGuiScaledWidth() / scale();
	}

	/** Stored offsets of an overlay, in design px. */
	private static float unit(Overlay o) {
		return o.guiPixels() ? 1 / scale() : 1;
	}

	/** Top-left corner of an overlay of the given size, clamped to the screen. */
	public static float[] position(Overlay o, float w, float h) {
		float sw = designWidth(), sh = 900;
		float u = unit(o), ox = o.ox * u, oy = o.oy * u;
		float x = switch (o.ax) {
			case Overlay.START -> ox;
			case Overlay.CENTER -> (sw - w) / 2 + ox;
			default -> sw - w - ox;
		};
		float y = switch (o.ay) {
			case Overlay.START -> oy;
			case Overlay.CENTER -> (sh - h) / 2 + oy;
			default -> sh - h - oy;
		};
		return new float[] {Math.max(0, Math.min(sw - w, x)), Math.max(0, Math.min(sh - h, y))};
	}

	/** Stores a new top-left position, re-anchoring to the nearest third of the screen. */
	public static void place(Overlay o, float x, float y, float w, float h) {
		float sw = designWidth(), sh = 900;
		float cx = x + w / 2, cy = y + h / 2;
		o.ax = cx < sw / 3 ? Overlay.START : cx > sw * 2 / 3 ? Overlay.END : Overlay.CENTER;
		o.ay = cy < sh / 3 ? Overlay.START : cy > sh * 2 / 3 ? Overlay.END : Overlay.CENTER;
		float ox = switch (o.ax) {
			case Overlay.START -> x;
			case Overlay.CENTER -> x - (sw - w) / 2;
			default -> sw - w - x;
		};
		float oy = switch (o.ay) {
			case Overlay.START -> y;
			case Overlay.CENTER -> y - (sh - h) / 2;
			default -> sh - h - y;
		};
		float u = unit(o);
		o.ox = ox / u;
		o.oy = oy / u;
	}

	/** Prepares a canvas in design space for overlay drawing. */
	public static Canvas begin(Canvas cv) {
		Minecraft mc = Minecraft.getInstance();
		cv.begin(mc.getWindow().getGuiScale());
		cv.push();
		cv.scale(scale());
		return cv;
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		Overlays.CLICKS.poll(mc);
		if (mc.gui.hud.isHidden() || mc.gui.screen() instanceof HudEditorScreen) return;
		boolean any = false;
		for (Overlay o : ALL) any |= o.enabled() && o.shown();
		if (!any) return;
		Gfx.ensure();
		Modules.INTERFACE.apply();
		if (canvas == null) canvas = Gfx.newCanvas();
		Canvas cv = begin(canvas);
		Gfx.text().setGraphics(g);
		Overlay.Context ctx = new Overlay.Context(cv, Gfx.text(), mc, g, System.nanoTime() / 1e6, false);
		for (Overlay o : ALL) {
			if (!o.enabled() || !o.shown()) continue;
			drawAt(ctx, o);
		}
		cv.pop();
		cv.flush(g);
		Gfx.text().setGraphics(null);
	}

	/**
	 * Where the vanilla status effect icons are, in design px {x0, y0, x1, y1},
	 * or null when there are none: one row of beneficial and one of harmful effects
	 * along the top right.
	 */
	private static float[] effectsBox(Minecraft mc) {
		if (mc.player == null) return null;
		int good = 0, bad = 0;
		for (MobEffectInstance e : mc.player.getActiveEffects()) {
			if (!e.showIcon()) continue;
			if (e.getEffect().value().isBeneficial()) good++;
			else bad++;
		}
		if (good + bad == 0) return null;
		float s = scale(), gw = mc.getWindow().getGuiScaledWidth();
		float top = mc.isDemo() ? 16 : 1;
		float bottom = top + (bad > 0 ? 26 + 24 : 24);
		float w = 25 * Math.max(good, bad) + 2;
		return new float[] {(gw - w) / s, 0, gw / s, bottom / s};
	}

	/** Draws one overlay at its stored position and size; returns its rect {x, y, w, h}. */
	public static float[] drawAt(Overlay.Context ctx, Overlay o) {
		float sc = o.scale;
		float w = o.width(ctx) * sc, h = o.height(ctx) * sc;
		float[] p = position(o, w, h);
		if (o.ay == Overlay.START) {
			// keep top-anchored overlays clear of the status effect icons
			float[] fx = effectsBox(ctx.mc);
			if (fx != null && p[0] < fx[2] && p[0] + w > fx[0] && p[1] < fx[3]) p[1] = Math.min(900 - h, fx[3] + 6);
		}
		ctx.alignEnd = o.ax == Overlay.END;
		ctx.current = o;
		if (w > 0 && h > 0) {
			ctx.cv.push();
			ctx.cv.translate(p[0], p[1]);
			ctx.cv.scale(sc);
			o.draw(ctx, 0, 0, w / sc, h / sc);
			ctx.cv.pop();
		}
		return new float[] {p[0], p[1], w, h};
	}

	public static JsonObject save() {
		JsonObject root = new JsonObject();
		for (Overlay o : ALL) root.add(o.id, o.save());
		return root;
	}

	public static void load(JsonObject root) {
		for (Overlay o : ALL) {
			if (root.has(o.id) && root.get(o.id).isJsonObject()) o.load(root.getAsJsonObject(o.id));
		}
		// the Timers panel used to start on top of LeoneMC's sidebar; layouts never moved from there get the new place
		Overlay timers = byId("timers");
		if (timers.ax == Overlay.END && timers.ay == Overlay.CENTER && timers.ox == 8 && timers.oy == -110) timers.resetPosition();
	}

	public static Overlay byId(String id) {
		for (Overlay o : ALL) if (o.id.equals(id)) return o;
		throw new IllegalArgumentException(id);
	}
}
