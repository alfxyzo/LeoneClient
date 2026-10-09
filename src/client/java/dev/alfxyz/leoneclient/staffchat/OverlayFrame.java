package dev.alfxyz.leoneclient.staffchat;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ARGB;
import net.minecraft.util.FormattedCharSequence;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.alfxyz.leoneclient.staffchat.StaffChat.LOGGER;

/**
 * Collects, during each frame, every hidden staff line Minecraft lays out in the chat (with its exact
 * pose, height and opacity) and anything drawn over the chat. At the end of the frame it redraws the
 * overlay window if anything changed. Everything here runs on the render thread.
 */
public final class OverlayFrame {
	/** One hidden line: its pose and y in the chat, and its text alpha (0 to 255). */
	private record LineDraw(StaffLine line, Matrix3x2f pose, int y, int alpha) {}

	private static final Matrix3x2f IDENTITY = new Matrix3x2f();
	private static final long STATUS_SHOW_MS = 2000;
	private static final long STATUS_FADE_MS = 500;

	private static final List<LineDraw> LINES = new ArrayList<>();
	/** Areas, in GUI coordinates, where Minecraft draws over the chat (suggestion lists, tooltips). */
	private static final List<float[]> OCCLUDERS = new ArrayList<>();

	private static Component statusText;
	private static long statusShownAt;
	private static List<Object> lastRefs;
	private static float[] lastNumbers;
	private static boolean stopped;

	// Kept for the self-test and for troubleshooting.
	static TextRaster.Image lastImage;
	static int lastImageScreenX;
	static int lastImageScreenY;
	/** Runs after every frame's overlay update when set. Only the self-test sets it. */
	static Runnable afterFrame;

	private OverlayFrame() {
	}

	/** Start of a frame. */
	public static void begin() {
		LINES.clear();
		OCCLUDERS.clear();
		StaffChat.beginFrame();
	}

	/** Minecraft is about to draw a chat line. Hidden staff lines are remembered so the overlay can draw them. */
	public static void collectLine(FormattedCharSequence text, Matrix3x2fc pose, int y, float opacity) {
		collectLine(text, pose, y, ARGB.as8BitChannel(opacity));
	}

	/** The same, with the text's alpha as Minecraft puts it in the text colour (0 to 255). */
	public static void collectLine(FormattedCharSequence text, Matrix3x2fc pose, int y, int alpha) {
		// Minecraft skips text whose alpha rounds to zero, so the overlay does too.
		if (text instanceof StaffLine line && StaffChat.isHidden() && alpha != 0) {
			LINES.add(new LineDraw(line, new Matrix3x2f(pose), y, alpha));
		}
	}

	/** Minecraft is drawing something over the chat in this rectangle (GUI coordinates before the pose). */
	public static void occlude(Matrix3x2fc pose, float x0, float y0, float x1, float y1) {
		Vector2f a = pose.transformPosition(x0, y0, new Vector2f());
		Vector2f b = pose.transformPosition(x1, y1, new Vector2f());
		OCCLUDERS.add(new float[] {Math.min(a.x, b.x), Math.min(a.y, b.y), Math.max(a.x, b.x), Math.max(a.y, b.y)});
	}

	static void showStatus(Component text) {
		statusText = text;
		statusShownAt = System.currentTimeMillis();
	}

	/** End of a frame: redraw the overlay window if needed. */
	public static void end() {
		OverlayWindow window = StaffChat.window();
		if (!stopped && window != null && window.isProtected()) {
			try {
				update(window);
			} catch (Throwable t) {
				stopped = true;
				LOGGER.error("The staff chat window stopped working, so staff chat is now shown normally and can be recorded.", t);
				window.destroy();
			}
		}
		if (afterFrame != null) {
			afterFrame.run();
		}
	}

	private static void update(OverlayWindow window) {
		Minecraft minecraft = Minecraft.getInstance();
		Window gameWindow = minecraft.getWindow();
		if (gameWindow.isMinimized() || minecraft.gui.overlay() != null) {
			hide(window);
			return;
		}

		// Other screens (inventory, pause menu...) are drawn over the chat, so staff lines are not shown with them.
		Screen screen = minecraft.gui.screen();
		List<LineDraw> lines = screen == null || screen instanceof ChatScreen ? LINES : List.of();
		float statusAlpha = statusAlpha(System.currentTimeMillis());
		if (ARGB.as8BitChannel(statusAlpha) < 4) {
			statusAlpha = 0.0F;
		}
		if (lines.isEmpty() && statusAlpha <= 0.0F) {
			hide(window);
			return;
		}

		int[] client = window.clientArea();
		if (client == null || client[2] <= 0 || client[3] <= 0 || gameWindow.getWidth() <= 0) {
			hide(window);
			return;
		}
		// GUI units to window pixels. The framebuffer normally matches the client area exactly.
		float scale = (float) gameWindow.getGuiScale() * (client[2] / (float) gameWindow.getWidth());

		List<Object> refs = new ArrayList<>();
		FloatList numbers = new FloatList();
		numbers.add(scale, client[0], client[1], client[2], client[3]);
		for (LineDraw line : lines) {
			refs.add(line.line());
			Matrix3x2f p = line.pose();
			numbers.add(p.m00, p.m01, p.m10, p.m11, p.m20, p.m21, line.y(), line.alpha());
		}
		for (float[] rect : OCCLUDERS) {
			numbers.add(rect[0], rect[1], rect[2], rect[3]);
		}
		refs.add(statusText);
		numbers.add(ARGB.as8BitChannel(statusAlpha));
		float[] numberArray = numbers.toArray();
		boolean texturesUpdated = GlyphAtlas.consumeUpdated();
		if (!texturesUpdated && refs.equals(lastRefs) && Arrays.equals(numberArray, lastNumbers)) {
			return; // nothing changed since the last frame
		}

		TextRaster raster = new TextRaster();
		for (LineDraw line : lines) {
			raster.addText(minecraft.font, line.line().real(), line.pose(), scale, 0, line.y(), line.alpha() << 24 | 0xFFFFFF, true);
		}
		if (statusAlpha > 0.0F) {
			FormattedCharSequence text = statusText.getVisualOrderText();
			int x = gameWindow.getGuiScaledWidth() / 2 - minecraft.font.width(text) / 2;
			int y = gameWindow.getGuiScaledHeight() - 84; // just above the action bar
			raster.addText(minecraft.font, text, IDENTITY, scale, x, y, ARGB.white(statusAlpha), true);
		}
		if (raster.missingTexture()) {
			// A glyph texture copy arrives in a frame or two. Hide rather than show text in the wrong place.
			hide(window);
			return;
		}

		List<float[]> occluders = new ArrayList<>();
		for (float[] rect : OCCLUDERS) {
			occluders.add(new float[] {rect[0] * scale, rect[1] * scale, rect[2] * scale, rect[3] * scale});
		}
		TextRaster.Image image = raster.render(client[2], client[3], occluders);
		if (image == null) {
			window.hide();
		} else {
			window.present(image.pixels(), image.width(), image.height(), client[0] + image.x(), client[1] + image.y());
		}
		lastImage = image;
		lastImageScreenX = image == null ? 0 : client[0] + image.x();
		lastImageScreenY = image == null ? 0 : client[1] + image.y();
		lastRefs = refs;
		lastNumbers = numberArray;
	}

	private static void hide(OverlayWindow window) {
		window.hide();
		lastImage = null;
		lastRefs = null;
		lastNumbers = null;
	}

	private static float statusAlpha(long now) {
		if (statusText == null) {
			return 0.0F;
		}
		long age = now - statusShownAt;
		if (age < STATUS_SHOW_MS) {
			return 1.0F;
		}
		if (age < STATUS_SHOW_MS + STATUS_FADE_MS) {
			return 1.0F - (age - STATUS_SHOW_MS) / (float) STATUS_FADE_MS;
		}
		statusText = null;
		return 0.0F;
	}

	private static final class FloatList {
		private float[] values = new float[64];
		private int size;

		void add(float... more) {
			if (size + more.length > values.length) {
				values = Arrays.copyOf(values, Math.max(values.length * 2, size + more.length));
			}
			System.arraycopy(more, 0, values, size, more.length);
			size += more.length;
		}

		float[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}
}
