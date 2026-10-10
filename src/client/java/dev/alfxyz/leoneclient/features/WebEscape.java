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
	public final Setting.Choice who = add(new Setting.Choice("who", "Players", "SHOW", List.of("Everyone in webs", "Only who you aim at"), "Everyone in webs"),
		"Everyone in webs: every player stuck in a web near you. Only who you aim at: just the one under your crosshair, for a busy fight.");
	public final Setting.Toggle lingerOut = add(new Setting.Toggle("linger_out", "Show when they get out", "SHOW", true),
		"Keeps a player shown in green for a moment after they leave the web, so you see the moment they escape.");
	public final Setting.Choice colours = add(new Setting.Choice("colours", "Colours", "LOOK", List.of("Green and red", "Blue and orange"), "Green and red"),
		"Blue and orange are easier to tell apart for colour-blind players.");
	public final Setting.Slider lineWidth = add(new Setting.Slider("line_width", "Line width", "LOOK", 1, 6, 0.5f, 2.5f, "%.1f"),
		"How thick the hitbox and aim lines are.");
	public final Setting.Slider labelSize = add(new Setting.Slider("label_size", "Label size", "LOOK", 0.2f, 1, 0.05f, 0.4f, "%.2f"),
		"How big the words above a player are.");
	public final Setting.Toggle fill = add(new Setting.Toggle("fill", "Shade the hitbox", "LOOK", true),
		"Fills the hitbox lightly in its colour, as well as outlining it.");
	public final Setting.Toggle sound = add(new Setting.Toggle("sound", "Sound when they can rocket out", "ALERT", true),
		"A short ping the moment a player in a web becomes able to rocket out.");
	public final Setting.Toggle self = add(new Setting.Toggle("self", "Note when you are in a web", "YOUR NOTE", true),
		"While you are in a web, a note says whether a rocket would boost you out or go into the block you are looking at. Move and resize it with Modify HUD.");
	public final Setting.Choice noteStyle = add(new Setting.Choice("note_style", "Style", "YOUR NOTE", List.of("Full", "Short", "Icon only"), "Full"),
		"Full: your wording below. Short: Rocket ready, Rocket blocked or Not gliding. Icon only: just the web, in the state's colour.");
	public final Setting.Chips noteStates = add(new Setting.Chips("note_states", "Show it when", "YOUR NOTE", List.of("Can rocket out", "Blocked", "Not gliding"),
			List.of("Can rocket out", "Blocked", "Not gliding")),
		"Which of your states the note appears for.");
	public final Setting.Text readyText = add(new Setting.Text("ready_text", "Can rocket out", "YOUR NOTE", "Rocket now to get out", "Rocket now to get out", 60, c -> true),
		"What the note says when a rocket would boost you out.");
	public final Setting.Text blockedText = add(new Setting.Text("blocked_text", "Blocked", "YOUR NOTE", "A rocket would hit the block you are looking at",
			"A rocket would hit the block you are looking at", 60, c -> true),
		"What the note says when your crosshair is on a block, so a rocket would go into it.");
	public final Setting.Text glideText = add(new Setting.Text("glide_text", "Not gliding", "YOUR NOTE", "Glide first: rockets need an elytra",
			"Glide first: rockets need an elytra", 60, c -> true),
		"What the note says when you are not gliding, so a rocket would do nothing.");
	public final Setting.Toggle noteBackground = add(new Setting.Toggle("note_background", "Background", "YOUR NOTE", true),
		"Draws the glass backdrop behind the note.");
	public final Setting.Toggle selfSound = add(new Setting.Toggle("self_sound", "Sound when you can rocket out", "YOUR NOTE", false),
		"A short ping the moment a rocket would get you out of the web you are in.");

	/** For the autotest, alone in its world: treat players as gliding, and draw your own player too. */
	public static boolean debugGliding, debugIncludeSelf;

	private final Map<UUID, State> lastState = new HashMap<>();
	private final Map<UUID, Long> outSince = new HashMap<>();
	private final Map<UUID, Long> soundAt = new HashMap<>();

	public WebEscape() {
		super("web_escape", Category.ELYTRABOX, "Web Escape", Icons.WEB,
			"Shows when a player stuck in cobwebs can rocket out: their hitbox, where their crosshair is, and whether it is on the web or on air.", false);
		readyText.shownWhen(() -> !noteStyle.is("Icon only") && !noteStyle.is("Short"));
		blockedText.shownWhen(() -> !noteStyle.is("Icon only") && !noteStyle.is("Short"));
		glideText.shownWhen(() -> !noteStyle.is("Icon only") && !noteStyle.is("Short"));
	}

	/** What your note says for a state, in the chosen style. */
	public String noteText(State s) {
		if (noteStyle.is("Icon only")) return "";
		if (noteStyle.is("Short")) return switch (s) {
			case CAN_ESCAPE -> "Rocket ready";
			case BLOCKED -> "Rocket blocked";
			case NOT_GLIDING -> "Not gliding";
			case OUT -> "";
		};
		String t = switch (s) {
			case CAN_ESCAPE -> readyText.get();
			case BLOCKED -> blockedText.get();
			case NOT_GLIDING -> glideText.get();
			case OUT -> "";
		};
		return t.isBlank() ? defaultText(s) : t.strip();
	}

	/** The wording used when yours is left empty. */
	private static String defaultText(State s) {
		return switch (s) {
			case CAN_ESCAPE -> "Rocket now to get out";
			case BLOCKED -> "A rocket would hit the block you are looking at";
			case NOT_GLIDING -> "Glide first: rockets need an elytra";
			case OUT -> "";
		};
	}

	/** Whether the note is wanted for a state. */
	public boolean noteFor(State s) {
		return switch (s) {
			case CAN_ESCAPE -> noteStates.has("Can rocket out");
			case BLOCKED -> noteStates.has("Blocked");
			case NOT_GLIDING -> noteStates.has("Not gliding");
			case OUT -> false;
		};
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
		boolean aimedOnly = who.is("Only who you aim at");
		var aimed = mc.crosshairPickEntity;
		for (AbstractClientPlayer p : mc.level.players()) {
			if (aimedOnly && p != aimed && !(debugIncludeSelf && p == mc.player)) continue;
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
		float w = lineWidth.get();
		if (show.has("Webs")) {
			for (BlockPos web : l.webs()) Gizmos.cuboid(web, 0.002f, GizmoStyle.stroke(0x66FFFFFF, Math.max(1, w * 0.6f)));
		}
		if (show.has("Hitbox")) Gizmos.cuboid(l.box(), fill.get() ? GizmoStyle.strokeAndFill(c, w, (c & 0xFFFFFF) | 0x26000000) : GizmoStyle.stroke(c, w));
		if (show.has("Aim line") && l.state() != State.OUT) {
			Gizmos.line(l.eye(), l.aimEnd(), c, w);
			if (l.aimBlock() != null) Gizmos.point(l.aimEnd(), c, 2 + w * 1.6f);
			else Gizmos.arrow(l.eye().lerp(l.aimEnd(), 0.75), l.aimEnd(), c, w);
		}
		if (show.has("Label")) {
			Vec3 top = new Vec3((l.box().minX + l.box().maxX) / 2, l.box().maxY + 0.45, (l.box().minZ + l.box().maxZ) / 2);
			Gizmos.billboardText(words(l), top, TextGizmo.Style.forColorAndCentered(c).withScale(labelSize.get()));
		}
	}

	private @Nullable State yoursBefore;

	@Override
	public void tick(Minecraft mc) {
		State now = yours();
		if (now == State.CAN_ESCAPE && yoursBefore != null && yoursBefore != State.CAN_ESCAPE && selfSound.get()) {
			mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 2.2f, 0.6f));
		}
		yoursBefore = now;
	}

	/** For the note about your own player: your state, or null while you are not in a web. */
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
