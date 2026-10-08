package dev.alfxyz.leoneclient.ui;

import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;

/** A single-line text field model: value, caret and a select-all state. */
public final class TextInput {
	private String value = "";
	private int caret;
	private boolean allSelected;
	private final int maxLength;
	private final Predicate<Character> allowed;
	/** Time of the last edit or caret move, so the caret stays solid while typing. */
	public double lastActivity;

	public TextInput(int maxLength, Predicate<Character> allowed) {
		this.maxLength = maxLength;
		this.allowed = allowed;
	}

	public String value() {
		return value;
	}

	public int caret() {
		return caret;
	}

	public boolean allSelected() {
		return allSelected && !value.isEmpty();
	}

	public void set(String v) {
		value = v.length() > maxLength ? v.substring(0, maxLength) : v;
		caret = value.length();
		allSelected = false;
		touch();
	}

	public void clear() {
		set("");
	}

	private void touch() {
		lastActivity = System.nanoTime() / 1_000_000.0;
	}

	public boolean insert(String s) {
		StringBuilder clean = new StringBuilder();
		for (char c : s.toCharArray()) if (c >= ' ' && c != 127 && allowed.test(c)) clean.append(c);
		if (clean.isEmpty()) return false;
		if (allSelected()) {
			value = "";
			caret = 0;
		}
		allSelected = false;
		String next = value.substring(0, caret) + clean + value.substring(caret);
		if (next.length() > maxLength) return false;
		value = next;
		caret += clean.length();
		touch();
		return true;
	}

	/** Handles editing keys. Returns true when the key was consumed. */
	public boolean key(KeyEvent event) {
		int key = event.key();
		boolean ctrl = event.hasControlDown();
		if (event.isSelectAll()) {
			allSelected = true;
			touch();
			return true;
		}
		if (event.isPaste()) {
			insert(Minecraft.getInstance().keyboardHandler.getClipboard());
			return true;
		}
		if (event.isCopy()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(value);
			return true;
		}
		if (event.isCut()) {
			Minecraft.getInstance().keyboardHandler.setClipboard(value);
			clear();
			return true;
		}
		switch (key) {
			case GLFW.GLFW_KEY_BACKSPACE -> {
				if (allSelected()) {
					clear();
				} else if (caret > 0) {
					int from = ctrl ? wordStart(caret) : caret - 1;
					value = value.substring(0, from) + value.substring(caret);
					caret = from;
				}
				allSelected = false;
				touch();
				return true;
			}
			case GLFW.GLFW_KEY_DELETE -> {
				if (allSelected()) {
					clear();
				} else if (caret < value.length()) {
					value = value.substring(0, caret) + value.substring(caret + 1);
				}
				allSelected = false;
				touch();
				return true;
			}
			case GLFW.GLFW_KEY_LEFT -> {
				caret = allSelected() ? 0 : ctrl ? wordStart(caret) : Math.max(0, caret - 1);
				allSelected = false;
				touch();
				return true;
			}
			case GLFW.GLFW_KEY_RIGHT -> {
				caret = allSelected() ? value.length() : Math.min(value.length(), caret + 1);
				allSelected = false;
				touch();
				return true;
			}
			case GLFW.GLFW_KEY_HOME -> {
				caret = 0;
				allSelected = false;
				touch();
				return true;
			}
			case GLFW.GLFW_KEY_END -> {
				caret = value.length();
				allSelected = false;
				touch();
				return true;
			}
			default -> {
				return false;
			}
		}
	}

	private int wordStart(int from) {
		int i = from;
		while (i > 0 && value.charAt(i - 1) == ' ') i--;
		while (i > 0 && value.charAt(i - 1) != ' ') i--;
		return i;
	}
}
