package dev.alfxyz.leoneclient.module;

import dev.alfxyz.leoneclient.render.Icons;

/** The six wheel segments, clockwise from the top. */
public enum Category {
	COMBAT("Combat", Icons.COMBAT),
	CHAT("Chat", Icons.CHAT),
	FRIENDS("Friends", Icons.FRIENDS),
	HUD("HUD", Icons.MONITOR),
	SERVER("Server", Icons.SERVER),
	CLIENT("Client", Icons.INTERNALS);

	public final String displayName;
	public final String icon;

	Category(String displayName, String icon) {
		this.displayName = displayName;
		this.icon = icon;
	}
}
