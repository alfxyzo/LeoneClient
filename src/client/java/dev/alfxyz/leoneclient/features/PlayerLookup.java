package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.LeoneScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** A key that opens the LeoneMC profile of the player you are looking at. */
public final class PlayerLookup extends Module {
	/** How far, and how close to the middle of the screen, a player can be to count as looked at. */
	private static final double RANGE = 96, MIN_COS = 0.996;

	public PlayerLookup() {
		super("player_lookup", Category.FRIENDS, "Player Lookup", Icons.USER,
			"Press its key while looking at a player to open their LeoneMC profile: rank, where they are, playtime and stats.", false);
	}

	@Override
	public void onBindPressed() {
		if (!active()) {
			toggle();
			return;
		}
		lookUp();
	}

	/** Opens the profile of the player under the crosshair, or says there is nobody there. */
	public static void lookUp() {
		Minecraft mc = Minecraft.getInstance();
		Player p = lookedAt(mc);
		if (p == null) {
			Chat.info("Look at a player, then press the key again.");
			return;
		}
		mc.gui.setScreen(LeoneScreen.forPlayer(p.getUUID(), p.getGameProfile().name()));
	}

	/**
	 * The player under the crosshair, or failing that the one nearest the middle of the screen within
	 * range. Server NPCs, which are not in the player list, do not count.
	 */
	static @Nullable Player lookedAt(Minecraft mc) {
		if (mc.player == null || mc.level == null || mc.getConnection() == null) return null;
		if (mc.crosshairPickEntity instanceof Player p && p != mc.player && listed(mc, p)) return p;
		Vec3 eye = mc.player.getEyePosition(), look = mc.player.getViewVector(1);
		Player best = null;
		double bestCos = MIN_COS;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player || !listed(mc, p)) continue;
			Vec3 to = p.getBoundingBox().getCenter().subtract(eye);
			double d = to.length();
			if (d < 0.5 || d > RANGE) continue;
			double cos = to.scale(1 / d).dot(look);
			if (cos > bestCos) {
				best = p;
				bestCos = cos;
			}
		}
		return best;
	}

	private static boolean listed(Minecraft mc, Player p) {
		return mc.getConnection().getPlayerInfo(p.getUUID()) != null;
	}

	@Override
	public String status() {
		return active() && bind < 0 ? "Give it a key in its settings" : null;
	}
}
