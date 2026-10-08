package dev.alfxyz.leoneclient.ui;

import net.minecraft.client.input.KeyEvent;
import org.jspecify.annotations.Nullable;

/** Content shown in the menu's side panel. Coordinates are design px. */
abstract class Page {
	static final float W = 694;
	protected final LeoneScreen screen;

	Page(LeoneScreen screen) {
		this.screen = screen;
	}

	/** Height of the content area. */
	abstract float height(Ui ui);

	/** Draws the content with its top-left at (x, y); width is {@link #W}. */
	abstract void draw(Ui ui, float x, float y);

	/** Called when the page is shown. */
	void opened() {
	}

	/** Enter while this page's text field is focused. */
	void submit() {
	}

	boolean scroll(double amount) {
		return false;
	}

	boolean key(KeyEvent event) {
		return false;
	}

	/** The field that type-to-search or Ctrl+F should focus, if any. */
	@Nullable TextInput input() {
		return null;
	}
}
