package dev.alfxyz.leoneclient.staffchat;

import com.sun.jna.Native;
import com.sun.jna.platform.win32.WinDef.HWND;
import com.sun.jna.platform.win32.WinDef.POINT;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.win32.StdCallLibrary;

/** user32 functions that JNA's built-in User32 binding does not include. */
interface Win32Ext extends StdCallLibrary {
	Win32Ext INSTANCE = Native.load("user32", Win32Ext.class);

	/** Windows 10 version 2004+: the window is shown on the monitor but left out of every capture. */
	int WDA_EXCLUDEFROMCAPTURE = 0x00000011;

	boolean SetWindowDisplayAffinity(HWND hWnd, int dwAffinity);

	boolean GetWindowDisplayAffinity(HWND hWnd, IntByReference pdwAffinity);

	boolean ClientToScreen(HWND hWnd, POINT lpPoint);
}
