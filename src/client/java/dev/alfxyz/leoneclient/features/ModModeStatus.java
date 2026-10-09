package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.staffchat.StaffState;
import java.util.regex.Pattern;

/** A small HUD panel showing mod mode, vanish, whether staff chat is hidden from recordings, and punishments given. */
public final class ModModeStatus extends Module {
	/** "Punishment applied: ban on Name for Cheating (ID: AC468733)" */
	private static final Pattern PUNISHMENT = Pattern.compile("^Punishment applied: \\S+ on ");
	private int punishments;

	public ModModeStatus() {
		super("mod_mode", Category.STAFF, "Mod Mode Status", Icons.SHIELD_CHECK,
			"Shows mod mode, vanish, staff chat hiding and the punishments you gave this session on your HUD.", false);
	}

	public void track(String plain) {
		if (active() && PUNISHMENT.matcher(plain).find()) punishments++;
	}

	public StaffState.ModMode modMode() {
		return StaffState.modMode();
	}

	public boolean vanished() {
		return StaffState.vanished();
	}

	public int punishments() {
		return punishments;
	}

	@Override
	public String status() {
		StringBuilder sb = new StringBuilder();
		switch (StaffState.modMode()) {
			case ON -> sb.append("In mod mode");
			case OFF -> sb.append("Not in mod mode");
			case UNKNOWN -> { }
		}
		if (vanished()) sb.append(sb.isEmpty() ? "Vanished" : ", vanished");
		if (punishments > 0) sb.append(sb.isEmpty() ? "" : ", ").append(punishments).append(punishments == 1 ? " punishment" : " punishments");
		return sb.isEmpty() ? null : sb.toString();
	}
}
