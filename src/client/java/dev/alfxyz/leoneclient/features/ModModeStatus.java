package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A small HUD panel showing mod mode, vanish, whether staff chat is hidden from recordings, and punishments given. */
public final class ModModeStatus extends Module {
	/** "Punishment applied: ban on Name for Cheating (ID: AC468733)" */
	private static final Pattern PUNISHMENT = Pattern.compile("^Punishment applied: (\\S+) on ");
	private int punishments;
	private String lastType = "";

	public ModModeStatus() {
		super("mod_mode", Category.STAFF, "Mod Mode Status", Icons.SHIELD_CHECK,
			"Shows mod mode, vanish, staff chat hiding and the punishments you gave this session on your HUD.", false);
	}

	public void track(String plain) {
		if (!active()) return;
		Matcher m = PUNISHMENT.matcher(plain);
		if (m.find()) {
			punishments++;
			lastType = m.group(1).toLowerCase(Locale.ROOT);
		}
	}

	public boolean modMode() {
		return active() && StaffChat.inStaffMode();
	}

	public boolean vanished() {
		return active() && StaffChat.vanished();
	}

	public int punishments() {
		return punishments;
	}

	@Override
	public String status() {
		if (!modMode() && !vanished() && punishments == 0) return null;
		StringBuilder sb = new StringBuilder();
		if (modMode()) sb.append("Mod mode");
		if (vanished()) sb.append(sb.isEmpty() ? "" : ", ").append("vanished");
		if (punishments > 0) sb.append(sb.isEmpty() ? "" : ", ").append(punishments).append(punishments == 1 ? " punishment" : " punishments");
		return sb.toString();
	}
}
