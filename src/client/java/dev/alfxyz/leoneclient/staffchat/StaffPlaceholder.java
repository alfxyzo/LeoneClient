package dev.alfxyz.leoneclient.staffchat;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.util.FormattedCharSequence;

import java.util.List;

/**
 * Stored in the chat history in place of a staff message. To anything that reads it, it is an empty
 * message, so no other mod or vanilla feature can display the staff text. The chat splits it into
 * {@link StaffLine}s that carry the real text.
 */
public final class StaffPlaceholder implements Component {
	private final Component real;

	StaffPlaceholder(Component real) {
		this.real = real;
	}

	public Component real() {
		return real;
	}

	@Override
	public Style getStyle() {
		return Style.EMPTY;
	}

	@Override
	public ComponentContents getContents() {
		return PlainTextContents.EMPTY;
	}

	@Override
	public List<Component> getSiblings() {
		return List.of();
	}

	@Override
	public FormattedCharSequence getVisualOrderText() {
		return FormattedCharSequence.EMPTY;
	}
}
