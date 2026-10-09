package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.features.ChatTabs;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.mixin.ChatHistoryAccessor;
import dev.alfxyz.leoneclient.mixin.ChatScreenAccessor;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Gfx;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import dev.alfxyz.leoneclient.staffchat.StaffPlaceholder;
import dev.alfxyz.leoneclient.web.LeoneWeb;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Built into the open chat: hold Ctrl over a message and press C to copy it, or hold Alt and click it to
 * open the LeoneMC profile of the player it is from or about. Holding the key highlights the message and
 * says what will happen, so nothing is a surprise; without it, chat behaves exactly as normal.
 */
public final class ChatLineActions {
	private static @Nullable Canvas canvas;
	/** Where the mouse was when chat opened: Minecraft centres it, which may happen to be over a message. */
	private static double @Nullable [] openedAt;

	private ChatLineActions() {
	}

	/** A message under the mouse: the message, and the top and bottom of its lines in GUI px. */
	public record Hovered(GuiMessage message, String text, float top, float bottom, float right) {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof ChatScreen chat)) return;
			if (openedAt == null) openedAt = mouse();
			ScreenEvents.remove(screen).register(s -> openedAt = null);
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> draw(g, mx, my));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !copy(chat, e));
			ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !profile(e));
		});
	}

	/** The message under a point in GUI px, laid out as vanilla draws the open chat. */
	public static @Nullable Hovered at(double x, double y) {
		Minecraft mc = Minecraft.getInstance();
		ChatComponent chat = mc.gui.hud.getChat();
		ChatHistoryAccessor history = (ChatHistoryAccessor) chat;
		double scale = mc.options.chatScale().get();
		int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		int bottom = (int) Math.floor((mc.getWindow().getGuiScaledHeight() - 40) / scale);
		float right = (float) ((ChatComponent.getWidth(mc.options.chatWidth().get()) + 8) * scale);
		if (x < 0 || x > right) return null;
		double above = bottom - y / scale;
		if (above < 0) return null;
		int i = (int) (above / lineHeight);
		List<GuiMessage.Line> lines = history.leone$lines();
		int scroll = history.leone$scroll();
		int shown = Math.min(lines.size() - scroll, chat.getLinesPerPage());
		if (i >= shown) return null;
		GuiMessage message = lines.get(i + scroll).parent();
		// the message's other lines sit next to this one: newer lines below, older ones above
		int first = i, last = i;
		while (first > 0 && lines.get(first - 1 + scroll).parent() == message) first--;
		while (last + 1 < shown && lines.get(last + 1 + scroll).parent() == message) last++;
		Component content = message.content();
		if (content instanceof StaffPlaceholder placeholder) content = placeholder.real();
		float top = (float) ((bottom - (last + 1) * lineHeight) * scale), low = (float) ((bottom - first * lineHeight) * scale);
		return new Hovered(message, Chat.plain(content), top, low, right);
	}

	/** For the autotest, which cannot hold a key down: 1 acts as if Ctrl is held, 2 as if Alt is. */
	public static int debugHeld;
	/** For the autotest: where to act as if the mouse is, in GUI px. */
	public static double @Nullable [] debugMouse;

	private static boolean ctrl() {
		if (debugHeld != 0) return debugHeld == 1;
		var w = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_CONTROL)
			|| InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_CONTROL);
	}

	private static boolean alt() {
		if (debugHeld != 0) return debugHeld == 2;
		var w = Minecraft.getInstance().getWindow();
		return InputConstants.isKeyDown(w, GLFW.GLFW_KEY_LEFT_ALT)
			|| InputConstants.isKeyDown(w, GLFW.GLFW_KEY_RIGHT_ALT);
	}

	private static double[] mouse() {
		Minecraft mc = Minecraft.getInstance();
		var w = mc.getWindow();
		return new double[] {mc.mouseHandler.xpos() * w.getGuiScaledWidth() / w.getScreenWidth(),
			mc.mouseHandler.ypos() * w.getGuiScaledHeight() / w.getScreenHeight()};
	}

	/** Pointing at something on purpose: the mouse has moved since chat opened. */
	private static boolean pointing(double x, double y) {
		double[] o = openedAt;
		if (o == null) return true;
		if (Math.abs(x - o[0]) <= 1 && Math.abs(y - o[1]) <= 1) return false;
		openedAt = null;
		return true;
	}

	public static @Nullable String person(String text) {
		var conn = Minecraft.getInstance().getConnection();
		return ChatTabs.person(text, name -> conn != null && conn.getPlayerInfo(name) != null);
	}

	// ------------------------------------------------------------------ drawing

	private static void draw(GuiGraphicsExtractor g, int mouseX, int mouseY) {
		boolean c = ctrl(), a = alt();
		double mx = debugMouse != null ? debugMouse[0] : mouseX, my = debugMouse != null ? debugMouse[1] : mouseY;
		if (c == a || !pointing(mx, my)) return;
		Hovered h = at(mx, my);
		if (h == null) return;
		String who = a ? person(h.text) : null;
		if (a && who == null) return;
		Minecraft mc = Minecraft.getInstance();
		Gfx.ensure();
		if (canvas == null) canvas = Gfx.newCanvas();
		Canvas cv = Hud.begin(canvas);
		TextRenderer text = Gfx.text();
		text.setGraphics(g);
		float u = Hud.scale();
		float k = (float) Math.clamp(mc.options.chatScale().get(), 0.75, 1.25);
		cv.fillRect(0, h.top / u, h.right / u, h.bottom / u, Colors.white(0.1f));
		cv.fillRect(0, h.top / u, 2.5f * k, h.bottom / u, Colors.accent(0.9f));
		String label = c ? "Ctrl+C to copy" : "Click for " + who + "'s profile";
		Style st = Style.of(11.5f * k, Weight.REGULAR);
		float ph = 20 * k, icon = 12 * k, pw = 8 * k + icon + 6 * k + text.width(label, st) + 9 * k;
		float px = h.right / u + 6 * k, py = h.top / u + Math.min(0, (h.bottom - h.top) / u - ph) / 2;
		if (px + pw > Hud.designWidth() - 4) px = h.right / u - pw - 6 * k;
		py = Math.max(2, py);
		cv.fillRoundRect(px, py, pw, ph, 7 * k, Colors.glass(0.85f));
		cv.borderRoundRect(px, py, pw, ph, 7 * k, 1, Colors.white(0.14f));
		Gfx.icons().draw(cv, c ? Icons.COPY : Icons.USER, px + 8 * k, py + (ph - icon) / 2, icon, 1.8f, Colors.TEXT);
		text.draw(cv, label, px + 8 * k + icon + 6 * k, text.baselineFor(st, py + ph / 2), st, Colors.TEXT);
		cv.pop();
		cv.flush(g);
		text.setGraphics(null);
	}

	// ------------------------------------------------------------------ actions

	/** Ctrl+C over a message, while nothing is selected in the input, copies the message. */
	private static boolean copy(ChatScreen screen, KeyEvent e) {
		if (e.key() != GLFW.GLFW_KEY_C || (e.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0) return false;
		if (!((ChatScreenAccessor) screen).leone$input().getHighlighted().isEmpty()) return false;
		double[] m = mouse();
		Hovered h = pointing(m[0], m[1]) ? at(m[0], m[1]) : null;
		if (h == null) return false;
		Minecraft.getInstance().keyboardHandler.setClipboard(h.text);
		Notices.push("Copied", h.text.length() > 60 ? h.text.substring(0, 57) + "…" : h.text, Colors.ACCENT_RGB, Icons.CHECK);
		return true;
	}

	/** Alt+click on a message opens the profile of the player it is from or about. */
	private static boolean profile(MouseButtonEvent e) {
		if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !alt()) return false;
		Hovered h = at(e.x(), e.y());
		if (h == null) return false;
		String who = person(h.text);
		if (who == null) return false;
		Minecraft mc = Minecraft.getInstance();
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(who);
		if (info != null) {
			open(info.getProfile().id(), info.getProfile().name());
			return true;
		}
		LeoneWeb.lookup(who).whenComplete((found, err) -> mc.execute(() -> {
			if (err == null && found.isPresent()) open(found.get().uuid(), found.get().name());
			else Chat.info(err != null ? "Could not reach leonemc.net." : "No LeoneMC player called " + who + ".");
		}));
		return true;
	}

	private static void open(UUID uuid, String name) {
		Minecraft.getInstance().gui.setScreen(LeoneScreen.forPlayer(uuid, name));
	}
}
