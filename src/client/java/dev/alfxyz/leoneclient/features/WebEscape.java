package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.TextGizmo;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EntitySelector;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Shows whether a player stuck in cobwebs can rocket out yet. It works this out the way the game does:
 * <ul>
 * <li>They are stuck while their hitbox (shrunk by 0.00001) overlaps a cobweb block. Each tick that
 * happens, their speed is reset and their movement cut to a quarter sideways and a twentieth up.</li>
 * <li>A rocket only boosts someone who is gliding with an elytra.</li>
 * <li>A rocket goes into whatever block their crosshair is on, within their reach of 4.5 blocks, instead
 * of boosting them. A cobweb's outline is the whole block, so while their eyes are inside a web they are
 * always looking at it. Only once their crosshair is on air (or on a player nearer than any block) does
 * the rocket boost them out.</li>
 * </ul>
 */
public final class WebEscape extends Module {
	/** How long a player who has just got out stays shown, in green. */
	private static final long OUT_MS = 1200;
	/** At most one sound per player in this long. */
	private static final long SOUND_GAP_MS = 1500;

	public enum State {
		/** Gliding, and the crosshair is on air: a rocket boosts them out. */
		CAN_ESCAPE,
		/** The crosshair is on a block (usually the web): a rocket would go into it. */
		BLOCKED,
		/** Not gliding: a rocket does nothing. */
		NOT_GLIDING,
		/** Out of the web. */
		OUT
	}

	/** What one player's situation is this frame: their state, hitbox, eyes, where the aim ends, and the webs they are in. */
	public record Look(State state, AABB box, Vec3 eye, Vec3 aimEnd, @Nullable BlockPos aimBlock, boolean aimAtWeb, List<BlockPos> webs) {
	}

	public final Setting.Chips show = add(new Setting.Chips("show", "Show", "SHOW", List.of("Hitbox", "Aim line", "Webs", "Label"),
			List.of("Hitbox", "Aim line", "Webs", "Label")),
		"Hitbox: their hitbox, coloured by whether they can rocket out. Aim line: where their crosshair goes, up to their reach, ending where it meets a block. "
			+ "Webs: the cobwebs holding them. Label: the state in words above them.");
	public final Setting.Slider range = add(new Setting.Slider("range", "Range", "SHOW", 8, 64, 4, 32, "%.0f blocks"),
		"How far away players are shown.");
	public final Setting.Toggle lingerOut = add(new Setting.Toggle("linger_out", "Show when they get out", "SHOW", true),
		"Keeps a player shown in green for a moment after they leave the web, so you see the moment they escape.");
	public final Setting.Choice colours = add(new Setting.Choice("colours", "Colours", "LOOK", List.of("Green and red", "Blue and orange"), "Green and red"),
		"Blue and orange are easier to tell apart for colour-blind players.");
	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound when they can rocket out", "ALERT", true),
		"A short ping the moment a player in a web becomes able to rocket out.");
	public final Setting.Toggle self = add(new Setting.Toggle("self", "Show for yourself", "ALERT", true),
		"While you are in a web, a note under your crosshair says whether a rocket would boost you out or go into the block you are looking at.");

	/** For the autotest, alone in its world: treat players as gliding, and draw your own player too. */
	public static boolean debugGliding, debugIncludeSelf;

	private final Map<UUID, State> lastState = new HashMap<>();
	private final Map<UUID, Long> outSince = new HashMap<>();
	private final Map<UUID, Long> soundAt = new HashMap<>();

	public WebEscape() {
		super("web_escape", Category.ELYTRABOX, "Web Escape", Icons.WEB,
			"Shows when a player stuck in cobwebs can rocket out: their hitbox, where their crosshair is, and whether it is on the web or on air.", false);
	}

	// ------------------------------------------------------------------ the rules

	/** Whether a hitbox is in a cobweb, as the game decides it: the box shrunk by 0.00001 overlaps a cobweb block. */
	public static List<BlockPos> websTouching(Level level, AABB box) {
		List<BlockPos> out = new ArrayList<>();
		AABB b = box.deflate(1.0E-5);
		for (BlockPos p : BlockPos.betweenClosed(BlockPos.containing(b.minX, b.minY, b.minZ), BlockPos.containing(b.maxX, b.maxY, b.maxZ))) {
			BlockState state = level.getBlockState(p);
			if (state.is(Blocks.COBWEB)) out.add(p.immutable());
		}
		return out;
	}

	/** Works out one player's situation, at this frame's point between ticks. */
	public static Look look(Player p, float partialTick) {
		Level level = p.level();
		Vec3 offset = p.getPosition(partialTick).subtract(p.position());
		AABB box = p.getBoundingBox().move(offset);
		List<BlockPos> webs = websTouching(level, box);
		Vec3 eye = p.getEyePosition(partialTick);
		Vec3 view = p.getViewVector(partialTick);
		double reach = p.blockInteractionRange();
		Vec3 end = eye.add(view.scale(reach));
		// what their crosshair is on: the first block outline along the ray, unless a player is nearer
		BlockHitResult block = level.clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, p));
		boolean onBlock = block.getType() == HitResult.Type.BLOCK && block.getLocation().closerThan(eye, reach);
		if (onBlock) {
			double blockDistSq = block.getLocation().distanceToSqr(eye);
			double entityReach = Math.min(p.entityInteractionRange(), Math.sqrt(blockDistSq));
			Vec3 entityEnd = eye.add(view.scale(entityReach));
			AABB sweep = box.expandTowards(view.scale(entityReach)).inflate(1);
			EntityHitResult entity = ProjectileUtil.getEntityHitResult(p, eye, entityEnd, sweep, EntitySelector.CAN_BE_PICKED, entityReach * entityReach);
			if (entity != null && entity.getLocation().distanceToSqr(eye) < blockDistSq) onBlock = false;
		}
		State state;
		if (webs.isEmpty()) state = State.OUT;
		else if (!p.isFallFlying() && !debugGliding) state = State.NOT_GLIDING;
		else state = onBlock ? State.BLOCKED : State.CAN_ESCAPE;
		BlockPos aimBlock = onBlock ? block.getBlockPos() : null;
		boolean atWeb = aimBlock != null && level.getBlockState(aimBlock).is(Blocks.COBWEB);
		return new Look(state, box, eye, onBlock ? block.getLocation() : end, aimBlock, atWeb, webs);
	}

	// ------------------------------------------------------------------ drawing

	private int good() {
		return colours.is("Blue and orange") ? 0xFF38BDF8 : 0xFF4ADE80;
	}

	private int bad() {
		return colours.is("Blue and orange") ? 0xFFFB923C : 0xFFF87171;
	}

	private static final int AMBER = 0xFFFBBF24;

	public int colour(State s) {
		return switch (s) {
			case CAN_ESCAPE, OUT -> good();
			case BLOCKED -> bad();
			case NOT_GLIDING -> AMBER;
		};
	}

	public static String words(Look l) {
		return switch (l.state()) {
			case CAN_ESCAPE -> "CAN ROCKET OUT";
			case BLOCKED -> l.aimAtWeb() ? "LOOKING AT WEB" : "LOOKING AT A BLOCK";
			case NOT_GLIDING -> "NOT GLIDING";
			case OUT -> "OUT";
		};
	}

	/** Called while the game gathers this frame's world drawings: one set for each player in a web nearby. */
	public void emit(float partialTick) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.level == null || mc.player == null) return;
		long now = System.currentTimeMillis();
		double max = range.get();
		for (AbstractClientPlayer p : mc.level.players()) {
			if (p == mc.player && !debugIncludeSelf || p.isSpectator() || p != mc.player && p.isInvisibleTo(mc.player) || p.distanceTo(mc.player) > max) continue;
			Look l = look(p, partialTick);
			UUID id = p.getUUID();
			State before = lastState.put(id, l.state());
			if (l.state() == State.OUT) {
				if (before != null && before != State.OUT) outSince.put(id, now);
				Long since = outSince.get(id);
				if (!lingerOut.get() || since == null || now - since > OUT_MS) continue;
			} else {
				outSince.remove(id);
				if (l.state() == State.CAN_ESCAPE && before != null && before != State.CAN_ESCAPE && sound.get()
					&& now - soundAt.getOrDefault(id, 0L) > SOUND_GAP_MS) {
					soundAt.put(id, now);
					mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.0f, 0.6f));
				}
			}
			draw(l);
		}
	}

	private void draw(Look l) {
		int c = colour(l.state());
		if (show.has("Webs")) {
			for (BlockPos w : l.webs()) Gizmos.cuboid(w, 0.002f, GizmoStyle.stroke(0x66FFFFFF, 1.5f));
		}
		if (show.has("Hitbox")) Gizmos.cuboid(l.box(), GizmoStyle.strokeAndFill(c, 2.5f, (c & 0xFFFFFF) | 0x26000000));
		if (show.has("Aim line") && l.state() != State.OUT) {
			Gizmos.line(l.eye(), l.aimEnd(), c, 2.5f);
			if (l.aimBlock() != null) Gizmos.point(l.aimEnd(), c, 6f);
			else Gizmos.arrow(l.eye().lerp(l.aimEnd(), 0.75), l.aimEnd(), c, 2.5f);
		}
		if (show.has("Label")) {
			Vec3 top = new Vec3((l.box().minX + l.box().maxX) / 2, l.box().maxY + 0.45, (l.box().minZ + l.box().maxZ) / 2);
			Gizmos.billboardText(words(l), top, TextGizmo.Style.forColorAndCentered(c).withScale(0.4f));
		}
	}

	/** For the note under your own crosshair: your state, or null while you are not in a web. */
	public @Nullable State yours() {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || !self.get() || mc.player == null || mc.level == null) return null;
		if (websTouching(mc.level, mc.player.getBoundingBox()).isEmpty()) return null;
		if (!mc.player.isFallFlying()) return State.NOT_GLIDING;
		// your real crosshair target, exactly what a right-click would use
		return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK ? State.BLOCKED : State.CAN_ESCAPE;
	}

	@Override
	protected void onDisable() {
		lastState.clear();
		outSince.clear();
	}
}
