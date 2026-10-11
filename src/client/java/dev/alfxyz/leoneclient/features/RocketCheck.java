package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.mixin.FireworkRocketAccessor;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Friends;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Flags players who boost with a rocket while their crosshair is on a block, which the game (as ElytraBox
 * runs it) does not allow. How a right-click with a rocket goes:
 * <ul>
 * <li>The client picks what the crosshair is on: the first block outline along the player's look, up to
 * their block reach (4.5 blocks), unless an entity is nearer than that block.</li>
 * <li>On a block, the client uses the rocket on the block: it launches from the block instead of boosting.
 * Only with the crosshair on air (or on an entity) does it send a plain "use item", and only that boosts.</li>
 * <li>The server boosts whoever sends "use item" while gliding: it does not check the crosshair. A client
 * that always sends "use item" therefore boosts through walls.</li>
 * <li>A boost spawns a rocket attached to the player, which everyone nearby is sent. A rocket launched
 * from a block is not attached to anyone.</li>
 * </ul>
 * So when an attached rocket appears, this works out what that player's crosshair was on, the way their
 * client would have, from their latest position and rotation. If it stays on a block within their reach
 * for the moment around the boost, they are flagged. Players in Adventure mode are left out: they cannot
 * use a rocket on a block, so theirs boosts anyway.
 */
public final class RocketCheck extends Module {
	/** Ticks of aim checked from when the rocket appears: the boost's own rotation can reach us a tick after the rocket. */
	private static final int SAMPLES = 3;
	/** A player has to have been in view this long, so a rocket already burning when they come into range is not judged. */
	private static final int SETTLE_TICKS = 20;
	/** At most one sound in this long. */
	private static final long SOUND_GAP_MS = 1500;

	public final Setting.Slider leeway = add(new Setting.Slider("leeway", "Leeway", "CHECK", 0, 1.5f, 0.05f, 0.3f, "%.2f blocks"),
		"Only flags when the block is at least this much inside their reach, so lag and rounding in what the server sends you do not cause a flag.");
	public final Setting.Toggle ignoreFriends = add(new Setting.Toggle("ignore_friends", "Leave friends out", "CHECK", false),
		"Never flags players on your Leone friends list.");
	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound", "ALERT", true),
		"A short alarm when a player is flagged.");
	public final Setting.Toggle popup = add(new Setting.Toggle("popup", "Pop-up", "ALERT", false),
		"Also shows each flag as a pop-up in the Notifications overlay.");
	public final Setting.Toggle chat = add(new Setting.Toggle("chat", "Chat message", "ALERT", false),
		"Also writes each flag in chat, which only you see.");
	public final Setting.Slider panelPlayers = add(new Setting.Slider("panel_players", "Players listed", "PANEL", 1, 10, 1, 5, "%.0f"),
		"The most players the Rocket Check panel lists, most recently flagged first.");
	public final Setting.Slider panelMinutes = add(new Setting.Slider("panel_minutes", "Keep players for", "PANEL", 1, 30, 1, 5, "%.0f min"),
		"How long a player stays on the panel after their last flag.");

	/** For the autotest, alone in its world: judge your own rockets too. */
	public static boolean debugIncludeSelf;

	/** Where a player's crosshair was: the block, its name, and how far from their eyes. */
	public record Aim(BlockPos pos, String block, double distance) {
	}

	/** A flagged player: how often, and the last block they rocketed through. */
	public static final class Suspect {
		public final UUID uuid;
		public final String name;
		public int count;
		public long lastAt;
		public String block = "";
		public double distance;

		public Suspect(UUID uuid, String name) {
			this.uuid = uuid;
			this.name = name;
		}
	}

	/** A rocket seen boosting a player, waiting on a few ticks of their aim. */
	private static final class Pending {
		final UUID player;
		final List<Aim> aims = new ArrayList<>();

		Pending(UUID player) {
			this.player = player;
		}
	}

	/** Rockets already looked at, by entity id. */
	private final IntOpenHashSet rockets = new IntOpenHashSet();
	/** The tick each player came into view. */
	private final Map<UUID, Long> seenSince = new HashMap<>();
	private final List<Pending> pending = new ArrayList<>();
	private final Map<UUID, Suspect> suspects = new HashMap<>();
	private long ticks, soundAt;
	private @Nullable Level lastLevel;

	public RocketCheck() {
		super("rocket_check", Category.ELYTRABOX, "Rocket Check", Icons.ROCKET,
			"Flags players who boost with a rocket while looking at a block within reach, which only a cheat lets them do, on a HUD panel of its own.", false);
	}

	// ------------------------------------------------------------------ the rules

	/**
	 * The block a player's crosshair is on, worked out as their client does it, from where they stand
	 * ({@code pos}, their feet) and where they look; null when it is on air or on an entity, which lets
	 * a rocket boost. {@code leeway} is taken off their block reach.
	 */
	public static @Nullable Aim aim(Player p, Vec3 pos, float yRot, float xRot, double leeway) {
		Level level = p.level();
		double blockReach = p.blockInteractionRange(), entityReach = p.entityInteractionRange();
		double max = Math.max(blockReach, entityReach);
		Vec3 eye = pos.add(0, p.getEyeHeight(), 0);
		Vec3 view = p.calculateViewVector(xRot, yRot);
		BlockHitResult block = level.clip(new ClipContext(eye, eye.add(view.scale(max)), ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		if (block.getType() != HitResult.Type.BLOCK) return null;
		double blockDistSq = block.getLocation().distanceToSqr(eye);
		double dist = Math.sqrt(blockDistSq);
		// an entity in front of the block takes the click, and a rocket then boosts whether or not it is in reach
		AABB sweep = p.getBoundingBox().move(pos.subtract(p.position())).expandTowards(view.scale(dist)).inflate(1);
		EntityHitResult entity = ProjectileUtil.getEntityHitResult(p, eye, eye.add(view.scale(dist)), sweep, EntitySelector.CAN_BE_PICKED, blockDistSq);
		if (entity != null && entity.getLocation().distanceToSqr(eye) < blockDistSq) return null;
		// a block beyond their reach leaves the crosshair on air
		if (dist >= blockReach - leeway) return null;
		BlockPos at = block.getBlockPos();
		return new Aim(at, level.getBlockState(at).getBlock().getName().getString(), dist);
	}

	/** The same, from the player's latest position and rotation as the server sent them (not the smoothed one drawn). */
	public static @Nullable Aim aimNow(Player p, double leeway) {
		var target = p.getInterpolation();
		return aim(p, target.position(), target.yRot(), target.xRot(), leeway);
	}

	// ------------------------------------------------------------------ watching

	@Override
	public void tick(Minecraft mc) {
		if (!active() || mc.level == null || mc.player == null) {
			clearWatch();
			return;
		}
		if (mc.level != lastLevel) {
			clearWatch();
			lastLevel = mc.level;
		}
		ticks++;
		for (Player p : mc.level.players()) seenSince.putIfAbsent(p.getUUID(), ticks);
		seenSince.keySet().removeIf(id -> mc.level.getPlayerByUUID(id) == null);

		IntOpenHashSet present = new IntOpenHashSet();
		for (Entity e : mc.level.entitiesForRendering()) {
			if (!(e instanceof FireworkRocketEntity rocket)) continue;
			present.add(rocket.getId());
			if (rockets.contains(rocket.getId())) continue;
			OptionalInt attached = rocket.getEntityData().get(FireworkRocketAccessor.leone$attachedTo());
			// what it is attached to comes with the rocket; give it a couple of ticks in case it is late
			if (attached.isEmpty() && rocket.tickCount <= 2) continue;
			rockets.add(rocket.getId());
			if (attached.isEmpty()) continue;
			if (mc.level.getEntity(attached.getAsInt()) instanceof Player p && watched(mc, p)) pending.add(new Pending(p.getUUID()));
		}
		rockets.retainAll(present);

		for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
			Pending pe = it.next();
			Player p = mc.level.getPlayerByUUID(pe.player);
			Aim a = p == null ? null : aimNow(p, leeway.get());
			// on air at any point around the boost: the rocket could have been legitimate
			if (a == null) {
				it.remove();
				continue;
			}
			pe.aims.add(a);
			if (pe.aims.size() >= SAMPLES) {
				it.remove();
				flag(mc, p, pe.aims.getFirst());
			}
		}
	}

	/** Whether a rocket boosting this player is judged at all. */
	private boolean watched(Minecraft mc, Player p) {
		if (p == mc.player && !debugIncludeSelf || p.isSpectator()) return false;
		if (p != mc.player && ticks - seenSince.getOrDefault(p.getUUID(), ticks) < SETTLE_TICKS) return false;
		if (ignoreFriends.get() && Friends.isFriend(p.getUUID())) return false;
		PlayerInfo info = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(p.getUUID());
		return info == null || info.getGameMode() != GameType.ADVENTURE;
	}

	/** Records a flag and raises the alerts that are on. Public for the autotest. */
	public void flag(Minecraft mc, Player p, Aim a) {
		String name = p.getGameProfile().name();
		Suspect s = suspects.computeIfAbsent(p.getUUID(), id -> new Suspect(id, name));
		s.count++;
		s.lastAt = System.currentTimeMillis();
		s.block = a.block();
		s.distance = a.distance();
		String detail = detail(s);
		if (sound.get() && s.lastAt - soundAt > SOUND_GAP_MS) {
			soundAt = s.lastAt;
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 0.7f, 0.8f));
		}
		if (popup.get()) Notices.player(name + " rocketed into a wall", detail, 0xF87171, p.getUUID());
		if (chat.get()) {
			Chat.info(Component.literal(name).withStyle(ChatFormatting.RED)
				.append(Component.literal(" rocketed while looking at " + detail.toLowerCase(Locale.ROOT) + " (×" + s.count + ")").withStyle(ChatFormatting.GRAY)));
		}
	}

	/** "Stone, 2.3 blocks away". */
	public static String detail(Suspect s) {
		return s.block + ", " + String.format(Locale.ROOT, "%.1f", s.distance) + " blocks away";
	}

	/** Players flagged recently, most recent first. */
	public List<Suspect> recent() {
		long cutoff = System.currentTimeMillis() - Math.round(panelMinutes.get()) * 60_000L;
		suspects.values().removeIf(s -> s.lastAt < cutoff);
		List<Suspect> out = new ArrayList<>(suspects.values());
		out.sort(Comparator.comparingLong((Suspect s) -> s.lastAt).reversed());
		return out;
	}

	@Override
	public String status() {
		if (!enabled()) return null;
		int n = recent().size();
		return n == 0 ? "No flags" : n + (n == 1 ? " player" : " players") + " flagged";
	}

	private void clearWatch() {
		rockets.clear();
		seenSince.clear();
		pending.clear();
		lastLevel = null;
	}

	@Override
	protected void onDisable() {
		clearWatch();
		suspects.clear();
	}
}
