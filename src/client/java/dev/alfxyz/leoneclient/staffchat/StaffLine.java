package dev.alfxyz.leoneclient.staffchat;

import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.FormattedCharSink;

import java.util.ArrayList;
import java.util.List;

/**
 * One wrapped line of a staff message inside the chat. While staff chat is hidden it yields no
 * characters, so Minecraft draws an empty chat line (background only) and the hidden window draws
 * the text on top. When hiding is off it yields the real text and behaves like any other line,
 * including hover and click.
 */
public final class StaffLine implements FormattedCharSequence {
	private final FormattedCharSequence real;

	StaffLine(FormattedCharSequence real) {
		this.real = real;
	}

	/** Wraps each line of a split staff message. */
	public static List<FormattedCharSequence> wrapAll(List<FormattedCharSequence> lines) {
		List<FormattedCharSequence> result = new ArrayList<>(lines.size());
		for (FormattedCharSequence line : lines) {
			result.add(new StaffLine(line));
		}
		return result;
	}

	public FormattedCharSequence real() {
		return real;
	}

	@Override
	public boolean accept(FormattedCharSink sink) {
		return StaffChat.isHidden() || real.accept(sink);
	}
}
