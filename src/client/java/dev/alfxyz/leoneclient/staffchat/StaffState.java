package dev.alfxyz.leoneclient.staffchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.LeoneMC;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Your own staff state on the LeoneMC server you are on: mod mode and vanish. Everything else (the
 * Staff Status panel, Staff Chat's "show while vanished or in mod mode") reads it from here.
 *
 * <p>Mod mode comes from LeoneMC's own messages whenever it changes while you play. Joining a server
 * with "Enable Mod Mode on Join" turned on is silent, so the items mod mode puts in your hotbar are
 * learned the first time you switch mod mode on and off, and recognised when you join later. Until
 * they are learned, mod mode after a silent join is unknown rather than guessed.
 *
 * <p>Vanish comes from the "You are currently Vanished" action bar, which LeoneMC repeats while you
 * are vanished. You count as vanished from the first one until they stop coming; how long that takes
 * adapts to how often the server repeats it.
 *
 * <p>Only server messages count (never player chat), and everything runs on the render thread.
 */
public final class StaffState {
	public enum ModMode { ON, OFF, UNKNOWN }

	/** How the current mod mode value was worked out. */
	public enum Source { NONE, MESSAGE, ITEMS }

	private static final String MOD_ON = "you are now in mod mode";
	private static final String MOD_OFF = "you are no longer in mod mode";
	/** Answers that only come while in mod mode, or only while out of it. */
	private static final List<String> IMPLIES_ON = List.of("you cannot use this command while in staff mode", "you can only disable mod mode in spawn");
	private static final List<String> IMPLIES_OFF = List.of("you can only enable mod mode in spawn");
	private static final String VANISH_TEXT = "you are currently vanished";

	/** After leaving mod mode, vanish messages still on their way are ignored for this long. */
	private static final long IGNORE_VANISH_AFTER_LEAVING_MS = 1500;
	private static final long DEFAULT_VANISH_GRACE_MS = 3000, MIN_VANISH_GRACE_MS = 1500, MAX_VANISH_GRACE_MS = 6000;
	/**
	 * Once LeoneMC has said you are in or out of mod mode, the hotbar counts as settled for that state
	 * after this long (the server swaps the items straight away), and is read again every few ticks.
	 */
	private static final long SETTLED_MS = 1000;
	/**
	 * Without mod mode's items in the hotbar this long after joining, mod mode counts as off. LeoneMC can
	 * take a few seconds after a join to put you in mod mode.
	 */
	private static final long ITEMS_SETTLE_MS = 8000;

	private static ModMode modMode = ModMode.UNKNOWN;
	private static Source modSource = Source.NONE;
	private static long modChangedAt;

	private static long vanishFirstAt, vanishLastAt, ignoreVanishUntil;
	private static final Deque<Long> vanishGaps = new ArrayDeque<>();

	private static long joinedAt;
	private static boolean inWorld;

	// learning mod mode's items: what the hotbar held in mod mode, and out of it
	private static Set<String> learned = Set.of();
	/** The latest settled hotbar in and out of mod mode, each with the server it was on. */
	private static Snapshot lastOn, lastOff;
	/** When LeoneMC last said mod mode switched (or which it is). */
	private static long messageAt;
	private static int ticks;
	private static boolean loaded;

	private record Snapshot(String server, Set<String> items) {
	}

	private StaffState() {
	}

	// ------------------------------------------------------------------ reading

	public static ModMode modMode() {
		return modMode;
	}

	public static Source modSource() {
		return modSource;
	}

	public static boolean inModMode() {
		return modMode == ModMode.ON;
	}

	/** When mod mode last changed (including to unknown on joining a server). */
	public static long modChangedAt() {
		return modChangedAt;
	}

	public static boolean vanished() {
		return vanishLastAt != 0 && System.currentTimeMillis() - vanishLastAt < vanishGraceMs();
	}

	/** True once mod mode's hotbar items are known, so joins can be recognised. */
	public static boolean itemsLearned() {
		return !learned.isEmpty();
	}

	/** One line for /leone staff: what is known and why. */
	public static String describe() {
		String mode = switch (modMode) {
			case ON -> "on";
			case OFF -> "off";
			case UNKNOWN -> "unknown";
		};
		String how = switch (modSource) {
			case MESSAGE -> " (from LeoneMC's message)";
			case ITEMS -> " (recognised from your hotbar)";
			case NONE -> learned.isEmpty() ? " (no message since you joined; switch mod mode once so its items can be learned)" : "";
		};
		return "Mod mode " + mode + how + ". " + (vanished() ? "Vanished." : "Not vanished.")
			+ (learned.isEmpty() ? "" : " Mod mode items learned: " + learned.size() + ".");
	}

	// ------------------------------------------------------------------ events

	/** A message from the server (never player chat). */
	public static void onServerMessage(String plain) {
		String text = plain.strip().toLowerCase(Locale.ROOT);
		if (text.startsWith(MOD_ON)) {
			setModMode(ModMode.ON, Source.MESSAGE);
		} else if (text.startsWith(MOD_OFF)) {
			setModMode(ModMode.OFF, Source.MESSAGE);
			// leaving mod mode unvanishes you; drop vanish at once rather than when the action bar stops
			vanishFirstAt = vanishLastAt = 0;
			ignoreVanishUntil = System.currentTimeMillis() + IGNORE_VANISH_AFTER_LEAVING_MS;
		} else if (startsWithAny(text, IMPLIES_ON)) {
			if (modMode != ModMode.ON) setModMode(ModMode.ON, Source.MESSAGE);
		} else if (startsWithAny(text, IMPLIES_OFF)) {
			if (modMode != ModMode.OFF) setModMode(ModMode.OFF, Source.MESSAGE);
		}
	}

	/** An action bar message. */
	public static void onActionBar(String plain) {
		long now = System.currentTimeMillis();
		if (now < ignoreVanishUntil || !plain.toLowerCase(Locale.ROOT).contains(VANISH_TEXT)) return;
		if (vanishLastAt != 0 && now - vanishLastAt < MAX_VANISH_GRACE_MS) {
			vanishGaps.addLast(now - vanishLastAt);
			while (vanishGaps.size() > 8) vanishGaps.removeFirst();
		} else {
			vanishFirstAt = now;
		}
		vanishLastAt = now;
	}

	/** Joined a server, including a switch between LeoneMC's servers: mod mode and vanish belong to the server. */
	public static void onJoin() {
		ensureLoaded();
		inWorld = true;
		joinedAt = System.currentTimeMillis();
		vanishFirstAt = vanishLastAt = 0;
		ignoreVanishUntil = 0;
		setModMode(ModMode.UNKNOWN, Source.NONE);
	}

	public static void onDisconnect() {
		inWorld = false;
		vanishFirstAt = vanishLastAt = 0;
		setModMode(ModMode.UNKNOWN, Source.NONE);
	}

	/** Every client tick. */
	public static void tick(Minecraft mc) {
		if (!inWorld || mc.player == null) return;
		long now = System.currentTimeMillis();
		ticks++;
		if (modSource == Source.MESSAGE) {
			// keep the settled hotbar of whichever state LeoneMC confirmed, so switching quickly still teaches
			if (now - messageAt >= SETTLED_MS && ticks % 5 == 0) learn(inModMode(), new Snapshot(String.valueOf(LeoneMC.server()), hotbar(mc)));
			return;
		}
		// without a message, look at the hotbar twice a second
		if (learned.isEmpty() || ticks % 10 != 0) return;
		Set<String> items = hotbar(mc);
		int matches = 0;
		for (String s : learned) if (items.contains(s)) matches++;
		int need = Math.min(learned.size(), Math.max(2, (int) Math.ceil(learned.size() * 0.6)));
		if (matches >= need) {
			if (modMode != ModMode.ON) setModMode(ModMode.ON, Source.ITEMS);
		} else if (now - joinedAt >= ITEMS_SETTLE_MS) {
			if (modMode != ModMode.OFF) setModMode(ModMode.OFF, Source.ITEMS);
		}
	}

	// ------------------------------------------------------------------ internals

	private static void setModMode(ModMode mode, Source source) {
		if (mode != modMode) modChangedAt = System.currentTimeMillis();
		if (source == Source.MESSAGE) messageAt = System.currentTimeMillis();
		modMode = mode;
		modSource = source;
	}

	private static long vanishGraceMs() {
		if (vanishGaps.size() < 2) return DEFAULT_VANISH_GRACE_MS;
		long[] gaps = vanishGaps.stream().mapToLong(Long::longValue).toArray();
		Arrays.sort(gaps);
		long median = gaps[gaps.length / 2];
		return Math.max(MIN_VANISH_GRACE_MS, Math.min(MAX_VANISH_GRACE_MS, median * 2 + 400));
	}

	private static boolean startsWithAny(String text, List<String> starts) {
		for (String s : starts) if (text.startsWith(s)) return true;
		return false;
	}

	/** The hotbar and off hand, as item id plus shown name, so renamed staff tools are told apart from ordinary items. */
	private static Set<String> hotbar(Minecraft mc) {
		Set<String> out = new HashSet<>();
		Inventory inv = mc.player.getInventory();
		for (int i = 0; i < 9; i++) add(out, inv.getItem(i));
		add(out, mc.player.getOffhandItem());
		return out;
	}

	private static void add(Set<String> out, ItemStack stack) {
		if (stack.isEmpty()) return;
		out.add(BuiltInRegistries.ITEM.getKey(stack.getItem()) + "|" + Chat.plain(stack.getHoverName()));
	}

	/**
	 * Mod mode's items are the ones in the hotbar in mod mode that are not there out of it, compared on
	 * the same server so that one server's ordinary items are not taken for staff tools.
	 */
	private static void learn(boolean on, Snapshot snapshot) {
		if (on) lastOn = snapshot;
		else lastOff = snapshot;
		if (lastOn == null || lastOff == null || !lastOn.server().equals(lastOff.server())) return;
		Set<String> kit = new HashSet<>(lastOn.items());
		kit.removeAll(lastOff.items());
		// one odd item is not enough to recognise mod mode by
		if (kit.size() < 2 || kit.equals(learned)) return;
		learned = Set.copyOf(kit);
		StaffChat.LOGGER.info("Learned {} items that mod mode puts in your hotbar: {}", learned.size(), learned.stream().sorted().toList());
		save();
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient").resolve("staff.json");
	}

	private static void ensureLoaded() {
		if (loaded) return;
		loaded = true;
		try {
			if (!Files.isRegularFile(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
			List<String> items = new ArrayList<>();
			if (o.has("modModeItems")) for (JsonElement e : o.getAsJsonArray("modModeItems")) items.add(e.getAsString());
			learned = items.size() >= 2 ? Set.copyOf(items) : Set.of();
		} catch (Exception e) {
			StaffChat.LOGGER.warn("Could not read {}", file(), e);
		}
	}

	private static void save() {
		JsonObject o = new JsonObject();
		JsonArray items = new JsonArray();
		for (String s : learned.stream().sorted().toList()) items.add(s);
		o.add("modModeItems", items);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), o.toString());
		} catch (Exception e) {
			StaffChat.LOGGER.warn("Could not write {}", file(), e);
		}
	}

	/** Dev automation only. */
	public static void debugForget() {
		learned = Set.of();
		lastOn = lastOff = null;
		try {
			Files.deleteIfExists(file());
		} catch (Exception ignored) {
		}
	}
}
