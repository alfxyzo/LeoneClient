package dev.alfxyz.leoneclient.staffchat;

import static dev.alfxyz.leoneclient.staffchat.StaffChat.LOGGER;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.GDI32;
import com.sun.jna.platform.win32.User32;
import com.sun.jna.platform.win32.WinDef.HBITMAP;
import com.sun.jna.platform.win32.WinDef.HDC;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.platform.win32.WinDef.RECT;
import com.sun.jna.platform.win32.WinGDI;
import com.sun.jna.platform.win32.WinNT.HANDLE;
import com.sun.jna.platform.win32.WinUser.BLENDFUNCTION;
import com.sun.jna.platform.win32.WinUser.SIZE;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import org.lwjgl.glfw.GLFWNativeWin32;

/**
 * A borderless, transparent, click-through window owned by the game window, so it always sits just above
 * the game (and is covered by anything covering the game). It is marked WDA_EXCLUDEFROMCAPTURE: it shows
 * on your monitor but not in OBS, Medal, Discord screen share or screenshots taken by capture tools.
 *
 * It lives on the render thread, the same thread as the game window, whose message loop also serves it.
 */
final class OverlayWindow {
	private static final int WS_POPUP = 0x80000000;
	private static final int WS_EX_TRANSPARENT = 0x00000020;
	private static final int WS_EX_TOOLWINDOW = 0x00000080;
	private static final int WS_EX_LAYERED = 0x00080000;
	private static final int WS_EX_NOACTIVATE = 0x08000000;
	private static final int SW_HIDE = 0;
	private static final int SW_SHOWNOACTIVATE = 4;
	private static final int ULW_ALPHA = 0x00000002;
	private static final byte AC_SRC_OVER = 0x00;
	private static final byte AC_SRC_ALPHA = 0x01;

	private User32 user32;
	private HWND game;
	private HWND hwnd;
	private HDC screenDc;
	private HDC memDc;
	private HBITMAP dib;
	private HANDLE previousBitmap;
	private Pointer dibBits;
	private int dibWidth;
	private int dibHeight;
	private boolean shown;
	private boolean protectedOk;
	private boolean attempted;
	private final POINT origin = new POINT();
	private final RECT clientRect = new RECT();

	/** True only while the window exists and Windows has confirmed it is excluded from capture. */
	boolean isProtected() {
		return protectedOk;
	}

	/** True once creation has been tried, whether or not it worked. */
	boolean attempted() {
		return attempted;
	}

	/** Creates the window over the game's GLFW window. Must run on the render thread. */
	void create(long gameWindow) {
		attempted = true;
		try {
			user32 = User32.INSTANCE;
			long handle = GLFWNativeWin32.glfwGetWin32Window(gameWindow);
			if (handle == 0) {
				LOGGER.error("Could not find the Minecraft window, so staff chat will be shown normally.");
				return;
			}
			game = new HWND(new Pointer(handle));
			hwnd = user32.CreateWindowEx(WS_EX_LAYERED | WS_EX_TRANSPARENT | WS_EX_TOOLWINDOW | WS_EX_NOACTIVATE,
				"STATIC", "Leone Client staff chat", WS_POPUP, 0, 0, 1, 1, game, null, null, null);
			if (hwnd == null) {
				LOGGER.error("Could not create the staff chat window (Windows error {}), so staff chat will be shown normally.", Native.getLastError());
				return;
			}

			// The key line: Windows still draws this window on the monitor, but leaves it out of captures.
			if (!Win32Ext.INSTANCE.SetWindowDisplayAffinity(hwnd, Win32Ext.WDA_EXCLUDEFROMCAPTURE)) {
				LOGGER.error("Windows refused to hide the staff chat window from capture (error {}). Windows 10 version 2004 or newer is required.", Native.getLastError());
				destroy();
				return;
			}
			IntByReference affinity = new IntByReference();
			if (!Win32Ext.INSTANCE.GetWindowDisplayAffinity(hwnd, affinity) || affinity.getValue() != Win32Ext.WDA_EXCLUDEFROMCAPTURE) {
				LOGGER.error("Could not confirm the staff chat window is hidden from capture, so staff chat will be shown normally.");
				destroy();
				return;
			}

			screenDc = user32.GetDC(null);
			memDc = GDI32.INSTANCE.CreateCompatibleDC(screenDc);
			if (memDc == null) {
				LOGGER.error("Could not set up drawing for the staff chat window, so staff chat will be shown normally.");
				destroy();
				return;
			}
			protectedOk = true;
			LOGGER.info("Staff chat window is running and hidden from screen capture.");
		} catch (Throwable t) {
			LOGGER.error("Could not start the staff chat window, so staff chat will be shown normally.", t);
			destroy();
		}
	}

	/** The game's client area: screen x, screen y, width, height. Null if Windows cannot tell us. */
	int[] clientArea() {
		origin.x = 0;
		origin.y = 0;
		if (!user32.GetClientRect(game, clientRect) || !Win32Ext.INSTANCE.ClientToScreen(game, origin)) {
			return null;
		}
		return new int[] {origin.x, origin.y, clientRect.right - clientRect.left, clientRect.bottom - clientRect.top};
	}

	/** Shows premultiplied ARGB pixels at a screen position. */
	void present(int[] pixels, int width, int height, int screenX, int screenY) {
		if (dib == null || width != dibWidth || height != dibHeight) {
			createBitmap(width, height);
		}
		// Premultiplied ARGB ints are laid out as BGRA bytes in memory, which is what Windows expects.
		dibBits.write(0, pixels, 0, width * height);
		BLENDFUNCTION blend = new BLENDFUNCTION();
		blend.BlendOp = AC_SRC_OVER;
		blend.SourceConstantAlpha = (byte) 255;
		blend.AlphaFormat = AC_SRC_ALPHA;
		user32.UpdateLayeredWindow(hwnd, screenDc, new POINT(screenX, screenY), new SIZE(width, height), memDc, new POINT(0, 0), 0, blend, ULW_ALPHA);
		if (!shown) {
			user32.ShowWindow(hwnd, SW_SHOWNOACTIVATE);
			shown = true;
		}
	}

	void hide() {
		if (shown) {
			user32.ShowWindow(hwnd, SW_HIDE);
			shown = false;
		}
	}

	void destroy() {
		protectedOk = false;
		try {
			if (user32 == null) {
				return;
			}
			if (hwnd != null) {
				user32.ShowWindow(hwnd, SW_HIDE);
			}
			if (memDc != null) {
				releaseBitmap();
				GDI32.INSTANCE.DeleteDC(memDc);
				memDc = null;
			}
			if (screenDc != null) {
				user32.ReleaseDC(null, screenDc);
				screenDc = null;
			}
			if (hwnd != null) {
				user32.DestroyWindow(hwnd);
				hwnd = null;
			}
		} catch (Throwable ignored) {
			// shutting down anyway
		}
	}

	private void createBitmap(int width, int height) {
		releaseBitmap();
		WinGDI.BITMAPINFO info = new WinGDI.BITMAPINFO();
		info.bmiHeader.biSize = info.bmiHeader.size();
		info.bmiHeader.biWidth = width;
		info.bmiHeader.biHeight = -height; // negative = rows stored top to bottom
		info.bmiHeader.biPlanes = 1;
		info.bmiHeader.biBitCount = 32;
		info.bmiHeader.biCompression = WinGDI.BI_RGB;

		PointerByReference bits = new PointerByReference();
		dib = GDI32.INSTANCE.CreateDIBSection(memDc, info, WinGDI.DIB_RGB_COLORS, bits, null, 0);
		if (dib == null) {
			throw new IllegalStateException("CreateDIBSection failed (Windows error " + Native.getLastError() + ")");
		}
		previousBitmap = GDI32.INSTANCE.SelectObject(memDc, dib);
		dibBits = bits.getValue();
		dibWidth = width;
		dibHeight = height;
	}

	private void releaseBitmap() {
		if (dib != null) {
			GDI32.INSTANCE.SelectObject(memDc, previousBitmap);
			GDI32.INSTANCE.DeleteObject(dib);
			dib = null;
			dibBits = null;
		}
	}
}
