package dev.alfxyz.leoneclient;

import java.util.Locale;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/** Client-side chat messages from Leone Client, and small chat text helpers. */
public final class Chat {
	/** Brand colours, from the LeoneMC logo. */
	public static final int PINK = 0xFC46A8, BLUE = 0x7CD4FF;
	private static final Pattern CODES = Pattern.compile("§.");

	private Chat() {
	}

	/** The "Leone »" prefix every client message starts with. */
	public static MutableComponent prefix() {
		return Component.literal("Leone").withColor(PINK).withStyle(ChatFormatting.BOLD)
			.append(Component.literal(" » ").withStyle(s -> s.withBold(false).withColor(ChatFormatting.DARK_GRAY)));
	}

	/** Shows a message in chat that only this client sees. */
	public static void info(Component message) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;
		mc.player.sendSystemMessage(prefix().append(message.copy().withStyle(s -> s.withBold(false))));
	}

	public static void info(String message) {
		info(Component.literal(message).withStyle(ChatFormatting.GRAY));
	}

	/** Plain text of a chat line with legacy colour codes removed and whitespace trimmed. */
	public static String plain(Component c) {
		return CODES.matcher(c.getString()).replaceAll("").strip();
	}

	public static String lower(String s) {
		return s.toLowerCase(Locale.ROOT);
	}
}
