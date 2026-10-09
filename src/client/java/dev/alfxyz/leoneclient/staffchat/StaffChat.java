package dev.alfxyz.leoneclient.staffchat;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Modules;
import java.util.Locale;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides, once per frame, whether staff chat is hidden from capture or drawn normally. Staff lines
 * stay in the chat on your screen, but their text is drawn by a separate window that Windows leaves
 * out of screen capture, so recordings show those lines empty.
 *
 * Outside vanish and mod mode, the Staff Chat module decides. While vanished or in mod mode, staff chat
 * is shown so it can be recorded; the module's key can hide it again for that stretch only, and leaving
 * vanish or mod mode always returns to the state from before.
 *
 * Safety rule: staff chat is only hidden while the window exists and Windows has confirmed it is
 * excluded from capture. Otherwise it is drawn normally, exactly as if this feature were not there.
 */
public final class StaffChat {
	public static final Logger LOGGER = LoggerFactory.getLogger("LeoneClient/StaffChat");
	/** After mod mode ends, vanish action bar messages still in flight are ignored for this long. */
	private static final long IGNORE_VANISH_AFTER_LEAVING_MS = 2000;
	private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).startsWith("windows");

	static final StaffRules rules = new StaffRules();
	private static final OverlayWindow window = new OverlayWindow();

	/** Set by the key while vanished or in mod mode; cleared when that ends. */
	private static boolean hideWhileRevealed;
	private static boolean staffMode;
	/** True once the vanish text has been seen during the current mod mode. */
	private static boolean vanishSeenInStaffMode;
	private static long vanishSeenAt;
	private static long ignoreVanishUntil;

	// Values for the current frame.
	private static boolean hidden;
	private static boolean revealed;
	private static boolean wasActive;
	private static boolean firstFrame = true;

	private StaffChat() {
	}

	/** False when this cannot work here: not Windows, or the separate Staff Chat Overlay mod is installed. */
	public static boolean available() {
		return WINDOWS && !FabricLoader.getInstance().isModLoaded("staffchatoverlay");
	}

	/** True while the Staff Chat module is on for a staff member and the feature can work here. */
	public static boolean active() {
		return available() && Modules.STAFF_CHAT.active();
	}

	static OverlayWindow window() {
		return window;
	}

	/** True if staff lines are hidden in this frame and drawn by the protected window instead. */
	public static boolean isHidden() {
		return hidden;
	}

	/** True while vanished or in mod mode, when staff chat is shown so it can be recorded. */
	public static boolean revealed() {
		return revealed;
	}

	/** True while the protected window is running. */
	public static boolean protectedWindow() {
		return window.isProtected();
	}

	public static boolean inStaffMode() {
		return staffMode;
	}

	public static boolean vanished() {
		return vanishSeenAt != 0 && System.currentTimeMillis() - vanishSeenAt < rules.vanishGraceSeconds * 1000L;
	}

	/** Swaps a staff message for a placeholder before it enters the chat history. */
	public static Component wrapIfStaff(Component message) {
		if (message == null || message instanceof StaffPlaceholder) {
			return message;
		}
		try {
			String text = message.getString();
			noticeStaffMode(text);
			// staff lines are marked even while the module is off (they show normally then), so
			// switching it on later also hides the staff chat already on screen
			return available() && Category.STAFF.visible() && rules.isStaff(text) ? new StaffPlaceholder(message) : message;
		} catch (RuntimeException e) {
			LOGGER.error("Could not check a chat message, so it was left as it is", e);
			return message;
		}
	}

	/** Called for every action bar message. */
	public static void onActionBar(Component message) {
		if (message != null && System.currentTimeMillis() >= ignoreVanishUntil && rules.isVanishText(message.getString())) {
			vanishSeenAt = System.currentTimeMillis();
		}
	}

	/** Leaving a server ends mod mode and vanish, so neither can carry over to the next server. */
	public static void onDisconnect() {
		staffMode = false;
		vanishSeenInStaffMode = false;
		vanishSeenAt = 0;
		hideWhileRevealed = false;
	}

	private static void noticeStaffMode(String text) {
		if (rules.isStaffModeOff(text)) {
			// Leaving mod mode ends vanish too, at once, instead of waiting for the action bar to stop.
			staffMode = false;
			vanishSeenInStaffMode = false;
			vanishSeenAt = 0;
			ignoreVanishUntil = System.currentTimeMillis() + IGNORE_VANISH_AFTER_LEAVING_MS;
		} else if (rules.isStaffModeOn(text)) {
			staffMode = true;
			vanishSeenInStaffMode = false;
			ignoreVanishUntil = 0;
		}
	}

	/** Called on the render thread at the start of every frame. */
	public static void beginFrame() {
		boolean active = active();
		Modules.STAFF_CHAT.applyTo(rules);
		if (active && !window.attempted()) window.create(Minecraft.getInstance().getWindow().handle());
		boolean vanishedNow = vanished();
		if (staffMode && vanishedNow) {
			vanishSeenInStaffMode = true;
		} else if (staffMode && vanishSeenInStaffMode && !vanishedNow) {
			// Mod mode vanished you and the vanish text has stopped, for example after a server switch
			// that never sent the leave message. Treat mod mode as over rather than leave staff chat visible.
			staffMode = false;
			vanishSeenInStaffMode = false;
		}
		boolean nowRevealed = rules.revealWhileVanished && (staffMode || vanishedNow);
		if (!nowRevealed) {
			hideWhileRevealed = false;
		}
		boolean wantHidden = nowRevealed ? hideWhileRevealed : true;
		boolean nowHidden = active && window.isProtected() && wantHidden;

		if (!firstFrame && active && wasActive && nowRevealed != revealed) {
			if (nowRevealed) {
				status(note(staffMode ? "Mod mode: " : "Vanished: ", "staff chat visible in recordings", ChatFormatting.GOLD));
			} else {
				status(note("Staff chat: ", "hidden from recordings", ChatFormatting.GREEN));
			}
		}
		firstFrame = false;
		wasActive = active;
		revealed = nowRevealed;
		hidden = nowHidden;
		if (!active) window.hide();
	}

	/** The module's key while vanished or in mod mode: hides staff chat again for that stretch only. */
	public static void toggleWhileRevealed() {
		hideWhileRevealed = !hideWhileRevealed;
		status(hideWhileRevealed
			? note("Staff chat: ", "hidden from recordings", ChatFormatting.GREEN)
			: note("Staff chat: ", staffMode ? "visible in recordings until you leave mod mode" : "visible in recordings while vanished", ChatFormatting.GOLD));
		beginFrame();
	}

	/** Shown when the module is switched on or off outside vanish and mod mode. */
	public static void noteHiding(boolean on) {
		status(on ? note("Staff chat: ", "hidden from recordings", ChatFormatting.GREEN)
			: note("Staff chat: ", "visible in recordings", ChatFormatting.RED));
	}

	private static Component note(String start, String end, ChatFormatting colour) {
		return Component.literal(start).append(Component.literal(end).withStyle(colour));
	}

	private static void status(Component text) {
		LOGGER.info(text.getString());
		if (rules.showStatusMessages) {
			OverlayFrame.showStatus(text);
		}
	}

	public static void destroy() {
		window.destroy();
	}
}
