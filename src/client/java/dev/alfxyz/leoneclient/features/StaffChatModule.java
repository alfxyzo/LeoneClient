package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import dev.alfxyz.leoneclient.staffchat.StaffState;
import dev.alfxyz.leoneclient.staffchat.StaffRules;
import java.util.ArrayList;
import java.util.List;
import net.fabricmc.loader.api.FabricLoader;

/** Hides staff chat from screen capture while keeping it in your chat on your own screen. */
public final class StaffChatModule extends Module {
	public final Setting.Toggle reveal = add(new Setting.Toggle("reveal", "Show while vanished or in mod mode", "WHEN", true),
		"While you are vanished or in mod mode, staff chat is shown normally so it can be recorded. Press this module's key to hide it again until you leave.");
	public final Setting.Toggle notes = add(new Setting.Toggle("notes", "Status notes", "WHEN", true),
		"Shows a short note above the action bar, on your screen only, when hiding turns on or off.");
	public final Setting.Text extra = add(new Setting.Text("extra", "Extra line starts", "LINES", "", "for example [Helper]", 120, c -> true),
		"More line starts to treat as staff chat, separated by commas. Staff chat, alerts, reports, lookups and punishment feedback are already covered.");
	private String extraFrom;
	private List<String> extraList = List.of();

	public StaffChatModule() {
		super("staff_chat", Category.STAFF, "Staff Chat", Icons.VIDEO_OFF,
			"Keeps staff chat in your chat on your screen, but hides its text from OBS, Medal, Discord and other screen capture.", false);
	}

	/** Copies the settings into the rules the staff chat code reads. */
	public void applyTo(StaffRules rules) {
		rules.revealWhileVanished = reveal.get();
		rules.showStatusMessages = notes.get();
		if (!extra.get().equals(extraFrom)) {
			extraFrom = extra.get();
			List<String> list = new ArrayList<>();
			for (String s : extraFrom.split(",")) if (!s.isBlank()) list.add(s.strip());
			extraList = List.copyOf(list);
		}
		rules.extraStartsWith = extraList;
	}

	/** Only while it can actually work here: on Windows, and without the separate Staff Chat Overlay mod. */
	@Override
	public boolean active() {
		return super.active() && StaffChat.available();
	}

	@Override
	public void onBindPressed() {
		if (active() && StaffChat.revealed()) StaffChat.toggleWhileRevealed();
		else toggle();
	}

	@Override
	public String bindHint() {
		return "While staff chat is showing because you are vanished or in mod mode, the key hides it again (and shows it). "
			+ "Otherwise it switches Staff Chat on or off.";
	}

	@Override
	protected void onEnable() {
		if (StaffChat.protectedWindow()) StaffChat.noteHiding(true);
	}

	@Override
	protected void onDisable() {
		if (StaffChat.protectedWindow()) StaffChat.noteHiding(false);
	}

	@Override
	public String unavailable() {
		if (StaffChat.available()) return null;
		return FabricLoader.getInstance().isModLoaded("staffchatoverlay")
			? "The separate Staff Chat Overlay mod is installed and does this instead. Remove it to use this module."
			: "Only works on Windows";
	}

	@Override
	public String status() {
		if (!StaffChat.available()) return unavailable();
		if (!active()) return null;
		if (!StaffChat.protectedWindow()) return "Windows has not confirmed the protection yet, so staff chat is shown normally";
		if (!StaffChat.isHidden()) return StaffState.inModMode() ? "Visible while in mod mode" : "Visible while vanished";
		return StaffChat.revealed() ? "Hidden again by your key until you leave" : "Hidden from recordings";
	}
}
