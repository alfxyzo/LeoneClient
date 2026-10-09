package dev.alfxyz.leoneclient.features;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.Time;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import org.jspecify.annotations.Nullable;

/**
 * ElytraBox's custom items have cooldowns the game itself does not show: the Cage, the Cobweb Circle and
 * Knockback weapons. Each use starts a timer, unless the server refuses it straight away ("You cannot use
 * cage item here!"), and the server's own "You cannot use cage item for another 12 seconds!" sets the
 * timer to exactly what it says. Timers last through relogging, as the server's do.
 */
public final class ItemCooldowns extends Module {
	/** "You cannot use cobweb circle for another 37 seconds!", and "...use this..." for a Knockback weapon. */
	private static final Pattern FOR_ANOTHER = Pattern.compile("^You cannot use (.+?) for another ([0-9]+(?:\\.[0-9]+)?) seconds?[.!]*$", Pattern.CASE_INSENSITIVE);
	private static final Pattern HERE = Pattern.compile("^You cannot use (.+?) here!?$", Pattern.CASE_INSENSITIVE);
	/** A use counts once a few ticks pass without the server refusing it. */
	private static final int CONFIRM_TICKS = 6;
	/** A refusal that names no item ("this") belongs to the item used within this long. */
	private static final long GENERIC_MS = 3000;
	private static final long LINGER_MS = 4000;
	private static final Item RED_GLASS = BuiltInRegistries.ITEM.getValue(Identifier.withDefaultNamespace("red_stained_glass"));

	/** What an item does when used, and how it is recognised. */
	public enum Trigger { USE, ATTACK }

	/** One of ElytraBox's items with a cooldown. */
	public final class Tracker {
		public final String id, name;
		public final Item icon;
		final Trigger trigger;
		/** ElytraBox's cooldown for the item, in ms. */
		final long cooldown;
		/** Words the server uses for it in its messages. */
		final List<String> words;
		final boolean quiet;
		private final @Nullable Item item;
		private final @Nullable String customName;
		private final boolean knockback;
		/** When the timer ends and how long it ran, in epoch ms; 0 when idle. */
		long endsAt, length;
		long readyAt;

		Tracker(String id, String name, Item icon, Trigger trigger, long cooldown, @Nullable Item item, @Nullable String customName,
				boolean knockback, boolean quiet, String... words) {
			this.id = id;
			this.name = name;
			this.icon = icon;
			this.trigger = trigger;
			this.cooldown = cooldown;
			this.item = item;
			this.customName = customName;
			this.knockback = knockback;
			this.quiet = quiet;
			this.words = List.of(words);
		}

		boolean on() {
			return track.has(name);
		}

		boolean matches(ItemStack stack) {
			if (stack.isEmpty()) return false;
			if (item != null && !stack.is(item)) return false;
			if (customName != null && (stack.get(DataComponents.CUSTOM_NAME) == null || !Chat.plain(stack.getHoverName()).equalsIgnoreCase(customName))) return false;
			if (knockback) {
				for (var e : stack.getEnchantments().entrySet()) {
					Holder<Enchantment> h = e.getKey();
					if (h.is(Enchantments.KNOCKBACK) && e.getIntValue() > 0) return true;
				}
				return false;
			}
			return true;
		}

		public boolean running() {
			return endsAt > System.currentTimeMillis();
		}

		public long left() {
			return Math.max(0, endsAt - System.currentTimeMillis());
		}

		/** Fraction of the cooldown still to go, from 1 at the start to 0 when ready. */
		public float remaining() {
			return length <= 0 ? 0 : Math.min(1, (float) left() / length);
		}

		void start(long ms) {
			long now = System.currentTimeMillis();
			length = Math.max(1, ms);
			endsAt = now + length;
			readyAt = 0;
		}

		/** The server says how long is left: the timer shows exactly that. */
		void sync(long ms) {
			length = Math.max(cooldown, ms);
			endsAt = System.currentTimeMillis() + ms;
			readyAt = 0;
		}
	}

	private record Pending(Tracker tracker, int[] ticksLeft) {
	}

	public final Setting.Chips track = add(new Setting.Chips("track", "Track", "ITEMS", List.of("Cage", "Cobweb Circle", "Knockback"),
			List.of("Cage", "Cobweb Circle", "Knockback")),
		"Which items to time. Cage: the red glass Cage, 3 minutes. Cobweb Circle: the bone meal that circles you with cobwebs, 2 minutes. "
			+ "Knockback: any weapon with Knockback, 8 seconds from each hit.");
	public final Setting.Toggle onItem = add(new Setting.Toggle("on_item", "Countdown on the item", "SHOW", true),
		"Shades the item in your hotbar and inventory as the cooldown runs, with the seconds left on top, like vanilla cooldowns.");
	public final Setting.Toggle linger = add(new Setting.Toggle("linger", "Show Ready briefly", "SHOW", true),
		"After a cooldown ends, the panel shows the item as Ready for a few seconds before it goes.");
	public final Setting.Chips ready = add(new Setting.Chips("ready", "When ready", "READY", List.of("Sound", "Pop-up", "Chat"), List.of("Sound", "Pop-up")),
		"How to tell you an item is ready again. Knockback weapons only ever get a soft tick, as they are ready every few seconds.");
	public final Setting.Toggle hideMessages = add(new Setting.Toggle("hide_messages", "Hide the server's cooldown messages", "READY", false),
		"Hides \"You cannot use cage item for another 12 seconds!\" and the like, since the panel shows it. The timers still use them.");

	public final List<Tracker> trackers = List.of(
		new Tracker("cage", "Cage", RED_GLASS, Trigger.USE, 180_000, RED_GLASS, "Cage", false, false, "cage item", "cage"),
		new Tracker("cobweb_circle", "Cobweb Circle", Items.COBWEB, Trigger.USE, 120_000, Items.BONE_MEAL, "Cobweb Circle", false, false, "cobweb circle", "cobweb"),
		new Tracker("knockback", "Knockback", Items.STICK, Trigger.ATTACK, 8_000, null, null, true, true, "knockback", "this"));

	private final List<Pending> pending = new ArrayList<>();
	private @Nullable Tracker lastUsed;
	private long lastUsedAt;
	private boolean loaded, dirty;

	public ItemCooldowns() {
		super("item_cooldowns", Category.ELYTRABOX, "Item Cooldowns", Icons.CLOCK,
			"Times the Cage, the Cobweb Circle and Knockback weapons: a HUD panel, a countdown on the item itself, and a ping when each is ready again.", false);
		UseItemCallback.EVENT.register((player, level, hand) -> {
			if (level.isClientSide() && isYou(player)) used(hand, Trigger.USE);
			return InteractionResult.PASS;
		});
		UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
			if (level.isClientSide() && isYou(player)) used(hand, Trigger.USE);
			return InteractionResult.PASS;
		});
		AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
			if (level.isClientSide() && isYou(player) && entity instanceof LivingEntity && entity != player) used(hand, Trigger.ATTACK);
			return InteractionResult.PASS;
		});
	}

	private static boolean isYou(Player player) {
		return player != null && player == Minecraft.getInstance().player;
	}

	public @Nullable Tracker trackerFor(ItemStack stack) {
		for (Tracker t : trackers) if (t.on() && t.matches(stack)) return t;
		return null;
	}

	/** You used or hit with whatever is in that hand: a matching item's timer starts once the server does not refuse it. */
	void used(InteractionHand hand, Trigger trigger) {
		Minecraft mc = Minecraft.getInstance();
		if (!active() || mc.player == null) return;
		Tracker t = trackerFor(mc.player.getItemInHand(hand));
		if (t == null || t.trigger != trigger) return;
		lastUsed = t;
		lastUsedAt = System.currentTimeMillis();
		if (t.running()) return;
		for (Pending p : pending) if (p.tracker == t) return;
		pending.add(new Pending(t, new int[] {CONFIRM_TICKS}));
	}

	/** A server message: refusals cancel a use, and "for another N seconds" sets the timer. Returns whether to hide it. */
	public boolean onServerMessage(String plain) {
		if (!active()) return false;
		Matcher m = FOR_ANOTHER.matcher(plain.strip());
		if (m.matches()) {
			Tracker t = named(m.group(1));
			if (t == null) return false;
			cancel(t);
			t.sync(Math.round(Double.parseDouble(m.group(2)) * 1000));
			dirty = true;
			return hideMessages.get();
		}
		m = HERE.matcher(plain.strip());
		if (m.matches()) {
			Tracker t = named(m.group(1));
			if (t != null) cancel(t);
		}
		return false;
	}

	/** The tracker a message means: by its words, or for "this", the item just used. */
	private @Nullable Tracker named(String subject) {
		String s = subject.strip().toLowerCase(Locale.ROOT);
		boolean recent = lastUsed != null && System.currentTimeMillis() - lastUsedAt < GENERIC_MS;
		Tracker byWord = null;
		for (Tracker t : trackers) {
			if (!t.on() || !t.words.contains(s)) continue;
			if (recent && t == lastUsed) return t;
			if (byWord == null) byWord = t;
		}
		if (byWord != null && !s.equals("this")) return byWord;
		// "this" names nothing: whatever was used a moment ago, else a Knockback weapon (the usual one)
		return recent && lastUsed.on() ? lastUsed : byWord;
	}

	private void cancel(Tracker t) {
		pending.removeIf(p -> p.tracker == t);
	}

	@Override
	public void tick(Minecraft mc) {
		load();
		if (!active() || mc.player == null) {
			pending.clear();
			return;
		}
		// a click that opened a chest or a shop was not a use
		boolean container = mc.gui.screen() instanceof AbstractContainerScreen<?> s && !(s instanceof InventoryScreen) && !(s instanceof CreativeModeInventoryScreen);
		Iterator<Pending> it = pending.iterator();
		while (it.hasNext()) {
			Pending p = it.next();
			if (container) {
				it.remove();
			} else if (--p.ticksLeft[0] <= 0) {
				it.remove();
				p.tracker.start(p.tracker.cooldown);
				dirty = true;
			}
		}
		long now = System.currentTimeMillis();
		for (Tracker t : trackers) {
			if (t.endsAt != 0 && now >= t.endsAt && t.readyAt == 0) {
				t.readyAt = now;
				t.endsAt = 0;
				dirty = true;
				if (t.on()) announce(t, mc);
			}
		}
		if (dirty) save();
	}

	private void announce(Tracker t, Minecraft mc) {
		if (ready.has("Sound")) {
			if (t.quiet) mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_BIT.value(), 1.9f, 0.45f));
			else mc.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), 1.6f, 0.8f));
		}
		if (t.quiet) return;
		if (ready.has("Pop-up")) Notices.push(t.name, "Ready again", 0x4ADE80, Icons.CHECK);
		if (ready.has("Chat")) Chat.info(t.name + " is ready again.");
	}

	/** Running timers, soonest first, then (for a few seconds) the ones that just became ready. */
	public List<Tracker> shown() {
		List<Tracker> out = new ArrayList<>();
		if (!active()) return out;
		long now = System.currentTimeMillis();
		for (Tracker t : trackers) {
			if (!t.on()) continue;
			if (t.running() || linger.get() && t.readyAt != 0 && now - t.readyAt < LINGER_MS) out.add(t);
		}
		out.sort(Comparator.comparingLong((Tracker t) -> t.running() ? t.left() : Long.MAX_VALUE));
		return out;
	}

	/** For the countdown on an item: the running tracker this stack belongs to, or null. */
	public @Nullable Tracker runningFor(ItemStack stack) {
		if (!active() || !onItem.get()) return null;
		Tracker t = trackerFor(stack);
		return t != null && t.running() ? t : null;
	}

	/** For the autotest, which cannot right-click: as if the item in that hand were used. */
	public void debugUse(InteractionHand hand, Trigger trigger) {
		used(hand, trigger);
	}

	/** For the autotest: as if a tracker's timer had this long left. */
	public void debugLeft(String id, long ms) {
		for (Tracker t : trackers) if (t.id.equals(id)) t.sync(ms);
	}

	public void clearAll() {
		for (Tracker t : trackers) t.endsAt = t.readyAt = 0;
		pending.clear();
		dirty = true;
	}

	@Override
	public String status() {
		if (!enabled()) return null;
		List<String> parts = new ArrayList<>();
		for (Tracker t : trackers) if (t.on() && t.running()) parts.add(t.name + " " + Time.clock(t.left()));
		return parts.isEmpty() ? "All ready" : String.join(", ", parts);
	}

	// ------------------------------------------------------------------ saving

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient").resolve("cooldowns.json");
	}

	private void load() {
		if (loaded) return;
		loaded = true;
		try {
			if (!Files.isRegularFile(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
			long now = System.currentTimeMillis();
			for (Tracker t : trackers) {
				if (!o.has(t.id)) continue;
				JsonObject e = o.getAsJsonObject(t.id);
				long ends = e.get("endsAt").getAsLong(), length = e.get("length").getAsLong();
				if (ends > now) {
					t.endsAt = ends;
					t.length = length;
				}
			}
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not read {}", file(), e);
		}
	}

	private void save() {
		dirty = false;
		JsonObject o = new JsonObject();
		for (Tracker t : trackers) {
			if (!t.running()) continue;
			JsonObject e = new JsonObject();
			e.addProperty("endsAt", t.endsAt);
			e.addProperty("length", t.length);
			o.add(t.id, e);
		}
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), o.toString());
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not write {}", file(), e);
		}
	}
}
