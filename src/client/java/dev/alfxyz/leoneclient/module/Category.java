package dev.alfxyz.leoneclient.module;

import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Account;
import java.util.ArrayList;
import java.util.List;

/** The wheel segments, clockwise from the top. */
public enum Category {
	COMBAT("Combat", Icons.COMBAT),
	CHAT("Chat", Icons.CHAT),
	FRIENDS("Friends", Icons.FRIENDS),
	HUD("HUD", Icons.MONITOR),
	SERVER("Server", Icons.SERVER),
	CLIENT("Client", Icons.INTERNALS),
	/** Only shown to LeoneMC staff. */
	STAFF("Staff", Icons.SHIELD_CHECK);

	public final String displayName;
	public final String icon;

	Category(String displayName, String icon) {
		this.displayName = displayName;
		this.icon = icon;
	}

	public boolean visible() {
		return this != STAFF || Account.staff();
	}

	/** The categories on the wheel for this player. */
	public static List<Category> shown() {
		List<Category> out = new ArrayList<>();
		for (Category c : values()) if (c.visible()) out.add(c);
		return out;
	}
}
