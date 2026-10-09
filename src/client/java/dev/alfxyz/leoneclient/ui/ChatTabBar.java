package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.LeoneClientMod;
import dev.alfxyz.leoneclient.features.ChatTabs;
import dev.alfxyz.leoneclient.features.ChatTabs.Tab;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.mixin.ChatScreenAccessor;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.render.Canvas;
import dev.alfxyz.leoneclient.render.Gfx;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.render.TextRenderer;
import dev.alfxyz.leoneclient.render.TextRenderer.Style;
import dev.alfxyz.leoneclient.render.TextRenderer.Weight;
import dev.alfxyz.leoneclient.staffchat.StaffState;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenKeyboardEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.ChatVisiblity;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * What Leone Client adds to the open chat: the Chat Tabs bar along the top of the chat, a note when the
 * chosen tab is empty, and a note above the input saying where typing goes when that could be mistaken
 * (staff chat switched on, or a tab like Staff or Private that only filters what you read). With the
 * tab kept after closing chat, its name shows in the same place.
 */
public final class ChatTabBar {
	private static final int AMBER = 0xFFFBBF24;
	private static @Nullable Canvas canvas;
	/** The tabs drawn last frame and where, in GUI px {x0, y0, x1, y1}, for clicks and scrolling. */
	private static final List<Tab> drawn = new ArrayList<>();
	private static final List<float[]> rects = new ArrayList<>();
	private static float @Nullable [] bar;

	private ChatTabBar() {
	}

	public static void register() {
		ScreenEvents.AFTER_INIT.register((mc, screen, w, h) -> {
			if (!(screen instanceof ChatScreen chat)) return;
			// Fabric makes these afresh on every init, resizes included, so nothing is registered twice
			ScreenEvents.afterExtract(screen).register((s, g, mx, my, delta) -> draw(chat, g, mx, my));
			ScreenMouseEvents.allowMouseClick(screen).register((s, e) -> !click(e));
			ScreenMouseEvents.allowMouseScroll(screen).register((s, mx, my, h2, v) -> !scroll(mx, my, v));
			ScreenKeyboardEvents.allowKeyPress(screen).register((s, e) -> !key(e));
			ScreenEvents.remove(screen).register(s -> {
				drawn.clear();
				rects.clear();
				bar = null;
				// the new screen is not in place yet; the next tick tells whether chat really closed
				Modules.CHAT_TABS.chatMayHaveClosed();
			});
		});
		HudElementRegistry.attachElementAfter(VanillaHudElements.CHAT, LeoneClientMod.id("chat_tab"), ChatTabBar::keptTab);
	}

	// ------------------------------------------------------------------ geometry

	private static boolean chatShown(Minecraft mc) {
		return mc.options.chatVisibility().get() != ChatVisiblity.HIDDEN && !mc.gui.hud.isHidden();
	}

	/** Tabs follow the chat's own size setting, within reason. */
	private static float sizeFactor(Minecraft mc) {
		return (float) Math.clamp(mc.options.chatScale().get(), 0.75, 1.25);
	}

	/** The bottom of the chat lines and the top of the full open chat area, in GUI px, as vanilla lays them out. */
	private static float[] chatArea(Minecraft mc) {
		double scale = mc.options.chatScale().get();
		int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
		float bottom = mc.getWindow().getGuiScaledHeight() - 40;
		float top = (float) (bottom - mc.gui.hud.getChat().getLinesPerPage() * lineHeight * scale);
		return new float[] {bottom, top};
	}

	private static Style tabStyle(float k) {
		return Style.of(12.5f * k, Weight.REGULAR);
	}

	// ------------------------------------------------------------------ drawing

	private static void draw(ChatScreen screen, GuiGraphicsExtractor g, int mouseX, int mouseY) {
		Minecraft mc = Minecraft.getInstance();
		drawn.clear();
		rects.clear();
		bar = null;
		if (!chatShown(mc)) return;
		ChatTabs tabs = Modules.CHAT_TABS;
		String typing = ((ChatScreenAccessor) screen).leone$input().getValue();
		Note note = typingNote(typing);
		if (!tabs.active() && note == null) return;
		Gfx.ensure();
		Modules.INTERFACE.apply();
		if (canvas == null) canvas = Gfx.newCanvas();
		Canvas cv = Hud.begin(canvas);
		TextRenderer text = Gfx.text();
		text.setGraphics(g);
		float u = Hud.scale(), k = sizeFactor(mc);
		float[] area = chatArea(mc);
		if (tabs.active()) {
			drawTabs(cv, text, tabs, area[1] / u, mouseX / u, mouseY / u, k, u);
			Tab now = tabs.current();
			if (now != Tab.ALL && tabs.currentEmpty()) {
				Style st = Style.of(12f * k, Weight.REGULAR);
				String msg = now.empty;
				float w = 10 * k + text.width(msg, st) + 10 * k, h = 22 * k, x = 4 / u, y = area[0] / u - h - 4 * k;
				cv.fillRoundRect(x, y, w, h, 7 * k, Colors.glass(0.55f));
				text.draw(cv, msg, x + 10 * k, text.baselineFor(st, y + h / 2), st, Colors.TEXT_HINT);
			}
		}
		if (note != null) drawNote(cv, text, note, mc, k, u);
		cv.pop();
		cv.flush(g);
		text.setGraphics(null);
	}

	private static void drawTabs(Canvas cv, TextRenderer text, ChatTabs tabs, float chatTop, float mx, float my, float k, float u) {
		Style st = tabStyle(k), badgeSt = Style.of(10.5f * k, Weight.SEMIBOLD);
		float h = 24 * k, pad = 9 * k, gap = 4 * k;
		float x = 4 / u, y = Math.max(4, chatTop - 4 * k - h);
		Tab now = tabs.current();
		for (Tab t : tabs.visibleTabs()) {
			String label = tabs.label(t);
			int unread = t == now ? 0 : tabs.unread(t);
			String count = unread > 99 ? "99+" : String.valueOf(unread);
			float badgeW = unread > 0 ? Math.max(15 * k, text.width(count, badgeSt) + 8 * k) : 0;
			float w = pad + text.width(label, st) + (unread > 0 ? 6 * k + badgeW : 0) + pad;
			boolean selected = t == now;
			boolean hover = mx >= x && mx < x + w && my >= y && my < y + h;
			cv.fillRoundRect(x, y, w, h, 8 * k, selected ? Colors.accent(0.9f) : Colors.glass(hover ? 0.78f : 0.6f));
			if (!selected) cv.borderRoundRect(x, y, w, h, 8 * k, 1, Colors.white(hover ? 0.18f : 0.08f));
			text.draw(cv, label, x + pad, text.baselineFor(st, y + h / 2), st, selected ? Colors.WHITE : hover ? Colors.TEXT : Colors.TEXT_MUTED);
			if (unread > 0) {
				float bx = x + w - pad - badgeW, bh = 15 * k, by = y + (h - bh) / 2;
				cv.fillRoundRect(bx, by, badgeW, bh, bh / 2, tabs.unreadForYou(t) ? Colors.accent(1) : Colors.white(0.2f));
				text.draw(cv, count, bx + (badgeW - text.width(count, badgeSt)) / 2, text.baselineFor(badgeSt, by + bh / 2), badgeSt, Colors.WHITE);
			}
			drawn.add(t);
			rects.add(new float[] {x * u, y * u, (x + w) * u, (y + h) * u});
			x += w + gap;
		}
		if (!rects.isEmpty()) bar = new float[] {rects.getFirst()[0], rects.getFirst()[1], rects.getLast()[2], rects.getLast()[3]};
	}

	private record Note(String text, String icon, int color) {
	}

	/**
	 * Where typing goes, when it could be mistaken: into staff chat while LeoneMC's staff chat toggle is
	 * on, or into public chat while a tab like Staff or Private is showing. Not while typing a command.
	 */
	private static @Nullable Note typingNote(String typing) {
		if (typing.startsWith("/")) return null;
		if (Category.STAFF.visible() && StaffState.talkingInStaffChat()) {
			return new Note("Typing goes to staff chat", Icons.SHIELD, AMBER);
		}
		ChatTabs tabs = Modules.CHAT_TABS;
		if (!tabs.active()) return null;
		return switch (tabs.current()) {
			case STAFF, ALERTS, MESSAGES, MENTIONS -> new Note("Typing goes to public chat", Icons.CHAT, Colors.TEXT_MUTED);
			default -> null;
		};
	}

	/** A small note just above the input line, at the left, clear of the hotbar. */
	private static void drawNote(Canvas cv, TextRenderer text, Note note, Minecraft mc, float k, float u) {
		Style st = Style.of(11.5f * k, Weight.REGULAR);
		float h = 20 * k, icon = 12 * k;
		float w = 8 * k + icon + 6 * k + text.width(note.text, st) + 9 * k;
		float x = 4 / u, y = (mc.getWindow().getGuiScaledHeight() - 14) / u - 3 * k - h;
		cv.fillRoundRect(x, y, w, h, 7 * k, Colors.glass(0.72f));
		cv.borderRoundRect(x, y, w, h, 7 * k, 1, Colors.rgba(note.color & 0xFFFFFF, 0.35f));
		Gfx.icons().draw(cv, note.icon, x + 8 * k, y + (h - icon) / 2, icon, 1.8f, note.color);
		text.draw(cv, note.text, x + 8 * k + icon + 6 * k, text.baselineFor(st, y + h / 2), st, note.color);
	}

	/** With the tab kept after closing chat, its name at the bottom left, so filtered chat never looks broken. */
	private static void keptTab(GuiGraphicsExtractor g, DeltaTracker dt) {
		Minecraft mc = Minecraft.getInstance();
		ChatTabs tabs = Modules.CHAT_TABS;
		if (!tabs.active() || tabs.current() == Tab.ALL || mc.gui.screen() instanceof ChatScreen || !chatShown(mc)) return;
		Gfx.ensure();
		if (canvas == null) canvas = Gfx.newCanvas();
		Canvas cv = Hud.begin(canvas);
		TextRenderer text = Gfx.text();
		text.setGraphics(g);
		float k = sizeFactor(mc);
		drawNote(cv, text, new Note("Chat tab: " + tabs.label(tabs.current()), Icons.MESSAGES, Colors.TEXT_MUTED), mc, k, Hud.scale());
		cv.pop();
		cv.flush(g);
		text.setGraphics(null);
	}

	// ------------------------------------------------------------------ input

	private static boolean click(MouseButtonEvent e) {
		if (e.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
		for (int i = 0; i < rects.size(); i++) {
			float[] r = rects.get(i);
			if (e.x() >= r[0] && e.x() < r[2] && e.y() >= r[1] && e.y() < r[3]) {
				if (drawn.get(i) != Modules.CHAT_TABS.current()) {
					Modules.CHAT_TABS.select(drawn.get(i));
					clickSound();
				}
				return true;
			}
		}
		return false;
	}

	/** Scrolling over the bar moves between tabs (down for the next one), as on the menu's wheel. */
	private static boolean scroll(double mx, double my, double amount) {
		if (bar == null || amount == 0 || mx < bar[0] || mx >= bar[2] || my < bar[1] || my >= bar[3]) return false;
		Modules.CHAT_TABS.cycle(amount < 0 ? 1 : -1);
		clickSound();
		return true;
	}

	/** Ctrl+Tab and Ctrl+Shift+Tab move between tabs. Tab on its own still completes names and commands. */
	private static boolean key(KeyEvent e) {
		if (e.key() != GLFW.GLFW_KEY_TAB || (e.modifiers() & GLFW.GLFW_MOD_CONTROL) == 0 || !Modules.CHAT_TABS.active()) return false;
		Modules.CHAT_TABS.cycle((e.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0 ? -1 : 1);
		clickSound();
		return true;
	}

	private static void clickSound() {
		if (!Modules.INTERFACE.sounds.get()) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(), 1.6f, 0.2f));
	}
}
