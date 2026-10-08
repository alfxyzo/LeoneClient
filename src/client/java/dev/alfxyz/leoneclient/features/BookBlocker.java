package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import net.minecraft.client.Minecraft;

/** Stops LeoneMC from opening books on you, while still letting you read your own. */
public final class BookBlocker extends Module {
	/** A book that opens this soon after right-clicking was opened by you. */
	private static final long OWN_WINDOW_MS = 1500;

	public final Setting.Toggle allowOwn = add(new Setting.Toggle("allow_own", "Allow your own books", "BEHAVIOUR", true),
		"Books still open when you right-click one yourself. Turn off to block every book.");
	public final Setting.Toggle notify = add(new Setting.Toggle("notify", "Notify", "BEHAVIOUR", true),
		"Shows a notification when a book was blocked.");
	private volatile long lastUse;
	private int blocked;

	public BookBlocker() {
		super("book_blocker", Category.SERVER, "Book Blocker", Icons.BOOK_X,
			"Stops the server from opening books on you, such as the news book when you join.", true);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	@Override
	public void tick(Minecraft mc) {
		if (mc.options.keyUse.isDown() && mc.gui.screen() == null) lastUse = System.currentTimeMillis();
	}

	/** Called from the network thread when the server opens a book. Returns true to block it. */
	public boolean blocks() {
		if (!active()) return false;
		if (allowOwn.get() && System.currentTimeMillis() - lastUse < OWN_WINDOW_MS) return false;
		blocked++;
		if (notify.get()) Notices.push("Book blocked", "The server tried to open a book", 0xF59E0B, Icons.BOOK_X);
		return true;
	}

	@Override
	public String status() {
		return blocked == 0 ? null : blocked == 1 ? "1 book blocked" : blocked + " books blocked";
	}
}
