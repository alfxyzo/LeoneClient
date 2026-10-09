package dev.alfxyz.leoneclient.hud;

import dev.alfxyz.leoneclient.LeoneClientMod;
import dev.alfxyz.leoneclient.features.WebEscape;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Gfx;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import dev.alfxyz.leoneclient.ui.Colors;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import org.jspecify.annotations.Nullable;

/** Web Escape's note under your crosshair while you are in a web: whether a rocket would get you out. */
public final class WebEscapeNote {
	private static final Style ST = Style.of(12, Weight.SEMIBOLD);
	private static @Nullable Canvas canvas;

	private WebEscapeNote() {
	}

	public static void register() {
		HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, LeoneClientMod.id("web_escape"), WebEscapeNote::render);
	}

	private static void render(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
		WebEscape.State s = Modules.WEB_ESCAPE.yours();
		if (s == null) return;
		String text = switch (s) {
			case CAN_ESCAPE -> "Rocket now to get out";
			case BLOCKED -> "A rocket would hit the block you are looking at";
			case NOT_GLIDING -> "Glide first: rockets need an elytra";
			case OUT -> "";
		};
		int color = Modules.WEB_ESCAPE.colour(s);
		Gfx.ensure();
		if (canvas == null) canvas = Gfx.newCanvas();
		Canvas cv = Hud.begin(canvas);
		TextRenderer t = Gfx.text();
		t.setGraphics(g);
		float h = 24, icon = 14, w = 10 + icon + 7 + t.width(text, ST) + 11;
		float x = (Hud.designWidth() - w) / 2, y = 450 + 28;
		cv.fillRoundRect(x, y, w, h, 8, Colors.glass(0.72f));
		cv.borderRoundRect(x, y, w, h, 8, 1, Colors.rgba(color & 0xFFFFFF, 0.5f));
		Gfx.icons().draw(cv, Icons.WEB, x + 10, y + (h - icon) / 2, icon, 1.8f, color);
		t.draw(cv, text, x + 10 + icon + 7, t.baselineFor(ST, y + h / 2), ST, color);
		cv.pop();
		cv.flush(g);
		t.setGraphics(null);
	}
}
