package dev.alfxyz.leoneclient.hud;

import com.google.gson.JsonObject;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import dev.alfxyz.leoneclient.render.TextRenderer;
import dev.alfxyz.leoneclient.ui.Colors;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import org.joml.Matrix3x2f;
import org.jspecify.annotations.Nullable;

/**
 * A HUD overlay. Sizes and positions are in design px (the menu's 900-tall
 * design space). The position is stored relative to the nearest screen edge
 * or centre on each axis, so layouts survive window and GUI-scale changes.
 * Overlays that draw vanilla text store their offsets in GUI pixels instead,
 * so they keep their place next to the vanilla HUD at any window size.
 */
public abstract class Overlay {
	public static final int START = 0, CENTER = 1, END = 2;
	protected static final Style VALUE = Style.of(13, Weight.REGULAR);
	protected static final Style VALUE_STRONG = Style.of(13, Weight.SEMIBOLD);
	protected static final float PILL_H = 26;

	public final String id;
	public final String name;
	public final String description;
	private final int defAx, defAy;
	private final float defOx, defOy;
	private final boolean defaultEnabled;

	private boolean enabled;
	/** This overlay's own settings; overlays that belong to a module keep theirs on the module. */
	public final List<Setting> settings = new ArrayList<>();
	public int ax, ay;
	public float ox, oy;
	/** Size multiplier set in the HUD editor. */
	public float scale = 1;
	public static final float MIN_SCALE = 0.5f, MAX_SCALE = 2.5f;

	protected Overlay(String id, String name, String description, boolean enabled, int ax, int ay, float ox, float oy) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.defaultEnabled = enabled;
		this.enabled = enabled;
		this.defAx = ax;
		this.defAy = ay;
		this.defOx = ox;
		this.defOy = oy;
		resetPosition();
	}

	protected <T extends Setting> T add(T setting, String description) {
		setting.description = description;
		settings.add(setting);
		return setting;
	}

	/** The module this overlay belongs to, whose settings it uses, or null for a standalone overlay. */
	public @Nullable Module owner() {
		return null;
	}

	/** Whether the shared glass pill is drawn behind this overlay. */
	public boolean background() {
		return true;
	}

	public void resetPosition() {
		ax = defAx;
		ay = defAy;
		ox = defOx;
		oy = defOy;
		scale = 1;
	}

	public void setScale(float s) {
		scale = Math.max(MIN_SCALE, Math.min(MAX_SCALE, Math.round(s * 20) / 20f));
	}

	/** True when {@link #ox} and {@link #oy} are GUI pixels rather than design px. */
	public boolean guiPixels() {
		return false;
	}

	public boolean enabled() {
		return enabled;
	}

	public void setEnabled(boolean on) {
		enabled = on;
	}

	/** False when this overlay is not offered to this player at all (Staff overlays for non-staff). */
	public boolean available() {
		return true;
	}

	/** False while the overlay has nothing to do, even though it is on (it then also leaves the HUD editor). */
	public boolean shown() {
		return true;
	}

	/** Called every client tick while in a world. */
	public void tick(Minecraft mc) {
	}

	public abstract float width(Context c);

	public abstract float height(Context c);

	public abstract void draw(Context c, float x, float y, float w, float h);

	/** Rendering context for overlays. */
	public static final class Context {
		public final Canvas cv;
		public final TextRenderer text;
		public final Minecraft mc;
		public final GuiGraphicsExtractor g;
		public final double now;
		/** True inside the HUD editor, where empty overlays draw a placeholder. */
		public final boolean editor;
		/** Whether the overlay being drawn is anchored to the right edge. */
		public boolean alignEnd;
		/** The overlay being drawn. */
		public @Nullable Overlay current;

		public Context(Canvas cv, TextRenderer text, Minecraft mc, GuiGraphicsExtractor g, double now, boolean editor) {
			this.cv = cv;
			this.text = text;
			this.mc = mc;
			this.g = g;
			this.now = now;
			this.editor = editor;
		}

		/** Design px per GUI pixel. */
		public float guiPx() {
			return 1 / Hud.scale();
		}

		/** The glass pill every simple overlay sits on, unless the overlay has its background turned off. */
		public void pill(float x, float y, float w, float h) {
			if (current != null && !current.background()) return;
			cv.fillRoundRect(x, y, w, h, 8, Colors.glass(0.62f));
			cv.borderRoundRect(x, y, w, h, 8, 1, Colors.white(0.08f));
		}

		/** Width of a "label value" pill. */
		public float pairWidth(String value, String unit) {
			return 10 + text.width(value, VALUE_STRONG) + (unit.isEmpty() ? 0 : 5 + text.width(unit, VALUE)) + 10;
		}

		/** Draws a value in white followed by a dimmer unit, inside a pill. */
		public void pair(float x, float y, float w, float h, String value, String unit) {
			pair(x, y, w, h, value, unit, Colors.TEXT);
		}

		/** The same, with the value in {@code valueColor}. */
		public void pair(float x, float y, float w, float h, String value, String unit, int valueColor) {
			pill(x, y, w, h);
			float base = text.baselineFor(VALUE, y + h / 2);
			text.draw(cv, value, x + 10, base, VALUE_STRONG, valueColor);
			if (!unit.isEmpty()) text.draw(cv, unit, x + 10 + text.width(value, VALUE_STRONG) + 5, base, VALUE, Colors.TEXT_HINT);
		}

		/** Width of a vanilla text component in design px. */
		public float componentWidth(Component c) {
			return mc.font.width(c) * guiPx();
		}

		/** Draws a vanilla text component at native GUI size with its top-left at (x, y) in design px. */
		public void component(Component c, float x, float y, int color, boolean shadow) {
			if ((cv.withAlpha(color) >>> 24) < 4) return;
			cv.flush(g);
			float[] m = cv.matrix();
			var pose = g.pose();
			pose.pushMatrix();
			pose.set(new Matrix3x2f(m[0], m[1], m[2], m[3], m[4], m[5]));
			pose.translate(x, y);
			pose.scale(guiPx(), guiPx());
			g.text(mc.font, c, 0, 0, cv.withAlpha(color), shadow);
			pose.popMatrix();
		}
	}

	JsonObject save() {
		JsonObject o = new JsonObject();
		o.addProperty("enabled", enabled);
		o.addProperty("ax", ax);
		o.addProperty("ay", ay);
		o.addProperty("ox", ox);
		o.addProperty("oy", oy);
		o.addProperty("scale", scale);
		if (!settings.isEmpty()) {
			JsonObject set = new JsonObject();
			for (Setting s : settings) set.add(s.id, s.save());
			o.add("settings", set);
		}
		return o;
	}

	void load(JsonObject o) {
		if (o.has("enabled")) enabled = o.get("enabled").getAsBoolean();
		if (o.has("ax")) ax = Math.max(START, Math.min(END, o.get("ax").getAsInt()));
		if (o.has("ay")) ay = Math.max(START, Math.min(END, o.get("ay").getAsInt()));
		if (o.has("ox")) ox = o.get("ox").getAsFloat();
		if (o.has("oy")) oy = o.get("oy").getAsFloat();
		if (o.has("scale")) setScale(o.get("scale").getAsFloat());
		if (o.has("settings") && o.get("settings").isJsonObject()) {
			JsonObject set = o.getAsJsonObject("settings");
			for (Setting s : settings) {
				if (!set.has(s.id)) continue;
				try {
					s.load(set.get(s.id));
				} catch (RuntimeException ignored) {
					// a bad value keeps the default
				}
			}
		}
	}

	void resetEnabled() {
		enabled = defaultEnabled;
	}

	/** Puts every setting back to its default. */
	public void resetSettings() {
		for (Setting s : settings) s.reset();
	}
}
