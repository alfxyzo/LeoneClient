package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.web.Friends;
import dev.alfxyz.leoneclient.web.LeoneWeb.Friend;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import org.jspecify.annotations.Nullable;

/** Makes the names of your LeoneMC friends stand out in chat. */
public final class FriendHighlight extends Module {
	public final Setting.Choice style = add(new Setting.Choice("style", "Style", "LOOK", List.of("Colour", "Bold", "Underline"), "Colour"),
		"Colour uses the theme colour, Bold and Underline keep the name's own colour.");
	public final Setting.Toggle hover = add(new Setting.Toggle("hover", "Hover details", "LOOK", true),
		"Hovering a friend's name in chat shows whether they are online and where.");
	private @Nullable List<Friend> patternFor;
	private @Nullable Pattern pattern;

	public FriendHighlight() {
		super("friend_highlight", Category.FRIENDS, "Friend Highlight", Icons.STAR,
			"Makes your LeoneMC friends' names stand out wherever they appear in chat.", true);
	}

	private @Nullable Pattern pattern() {
		List<Friend> list = Friends.list();
		if (list != patternFor) {
			patternFor = list;
			List<String> names = new ArrayList<>();
			for (Friend f : list) names.add(f.name());
			pattern = names.isEmpty() ? null : ChatStyle.words(names);
		}
		return pattern;
	}

	public Component handle(Component message) {
		if (!enabled()) return message;
		Pattern p = pattern();
		if (p == null) return message;
		return ChatStyle.restyle(message, p, this::styled);
	}

	private Style styled(Style s, String name) {
		Style out = switch (style.get()) {
			case "Bold" -> s.withBold(true);
			case "Underline" -> s.withUnderlined(true);
			default -> s.withColor(Colors.ACCENT_RGB);
		};
		if (hover.get() && s.getHoverEvent() == null) out = out.withHoverEvent(new HoverEvent.ShowText(details(name)));
		return out;
	}

	private static Component details(String name) {
		Friend f = Friends.byName(name);
		MutableComponent c = Component.literal(f != null ? f.name() : name).withColor(f != null ? f.color() : 0xFFFFFF)
			.append(Component.literal("  LeoneMC friend").withStyle(ChatFormatting.GRAY));
		if (f == null) return c;
		Friends.Location at = Friends.location(f.name());
		String where;
		if (Friends.online(f)) where = at != null && at.online() ? "Online on " + at.server() : "Online";
		else where = "Offline";
		return c.append(Component.literal("\n" + where).withStyle(Friends.online(f) ? ChatFormatting.GREEN : ChatFormatting.DARK_GRAY));
	}
}
