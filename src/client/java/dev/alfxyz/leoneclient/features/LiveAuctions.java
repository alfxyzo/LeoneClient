package dev.alfxyz.leoneclient.features;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Friends;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.Holder;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import org.jspecify.annotations.Nullable;

/**
 * LeoneMC's live auctions, where a player puts an item up in chat and everyone bids for a minute. Times each
 * auction from its start line (it always ends 60 seconds later, however late the bids), shows the top bid and
 * who holds it, warns when you are outbid and counts down the last seconds. It learns the length by watching
 * auctions end, and remembers what each item sold for, so the panel can say what it usually goes for.
 * <p>
 * Two formats are known. ElytraBox: "Auction » Name is auctioning Item for $450", "Auction » Name has bid $451.",
 * "Auction » Name won the auction with a bid of $475!", "Auction » No one bid on the auction!". WildKits (a
 * December event): "Live Auction » Name started an auction at $100 for 1x [Item]", "... bid $1000 for [Item]",
 * "... Auction ending in 5", "... Name won [Item] for $1000!".
 */
public final class LiveAuctions extends Module {
	private static final String MONEY = "\\$([0-9][0-9,]*(?:\\.[0-9]+)?)";
	private static final Pattern START = Pattern.compile("^Auction\\s*» (\\S+) is auctioning (.+?) for " + MONEY);
	private static final Pattern BID = Pattern.compile("^Auction\\s*» (\\S+) has bid " + MONEY);
	private static final Pattern WON = Pattern.compile("^Auction\\s*» (\\S+) won the auction with a bid of " + MONEY);
	private static final Pattern NO_BIDS = Pattern.compile("^(?:Live )?Auction\\s*» No one bid", Pattern.CASE_INSENSITIVE);
	private static final Pattern LIVE_START = Pattern.compile("^Live Auction\\s*» (\\S+) started an auction at " + MONEY + " for (?:([0-9]+)x )?\\[(.+?)]");
	private static final Pattern LIVE_BID = Pattern.compile("^Live Auction\\s*» (\\S+) bid " + MONEY + " for \\[(.+?)]");
	private static final Pattern LIVE_WON = Pattern.compile("^Live Auction\\s*» (\\S+) won \\[(.+?)] for " + MONEY);
	private static final Pattern ENDING = Pattern.compile("^Live Auction\\s*» Auction ending in ([0-9]+)");

	/** What LeoneMC's auctions have always lasted; learning only moves it if the server changes. */
	private static final long DEFAULT_MS = 60_000;
	private static final int DURATION_SAMPLES = 15, SALES_KEPT = 12, ITEMS_KEPT = 300;
	/** An auction whose end line never came (you left, or missed it) is dropped this long after it should have ended. */
	private static final long LOST_MS = 8000;
	/** How long the result stays on the panel. */
	public static final long RESULT_MS = 4000;
	/** A second press of the bid key within this long sends the bid. */
	private static final long CONFIRM_MS = 2500;

	public enum End { SOLD, NO_BIDS, LOST }

	/** One auction, as far as chat has told us. */
	public static final class Auction {
		public final String item, seller;
		public final double startPrice;
		/** When it started and when it ends (epoch ms); 0 when we joined part way and do not know. */
		public long startAt, endsAt;
		public double topBid;
		public @Nullable String topBidder;
		public int bids;
		/** When chat last said anything about it (epoch ms). */
		public long lastAt;
		public boolean youBid;
		/** When you were last outbid, for the panel's flash; 0 when never. */
		public long outbidAt;
		public @Nullable End end;
		public long endedAt;

		public Auction(String item, String seller, double startPrice, long startAt, long endsAt) {
			this.item = item;
			this.seller = seller;
			this.startPrice = startPrice;
			this.startAt = startAt;
			this.endsAt = endsAt;
			this.topBid = startPrice;
			this.lastAt = System.currentTimeMillis();
		}

		public boolean timed() {
			return endsAt > 0;
		}

		public long left() {
			return timed() ? Math.max(0, endsAt - System.currentTimeMillis()) : 0;
		}

		/** Fraction of the auction still to go, 1 at the start and 0 at the end. */
		public float remaining() {
			if (!timed() || startAt <= 0) return timed() ? Math.min(1, left() / (float) DEFAULT_MS) : 1;
			return Math.max(0, Math.min(1, left() / (float) Math.max(1, endsAt - startAt)));
		}

		public boolean youLead() {
			return topBidder != null && Friends.isYourName(topBidder);
		}

		public boolean ended() {
			return end != null;
		}
	}

	public final Setting.Chips alerts = add(new Setting.Chips("alerts", "Alerts", "ALERTS",
			List.of("Start", "Outbid", "Won"), List.of("Outbid", "Won")),
		"Start: a soft chime when an auction begins. Outbid: a low note and a pop-up when someone outbids you. Won: a bell and a pop-up when you win.");
	public final Setting.Choice countdown = add(new Setting.Choice("countdown", "Countdown ticks", "ALERTS",
			List.of("Off", "Last 5 seconds", "Last 10 seconds"), "Last 5 seconds"),
		"A tick each second as the auction ends, rising in pitch, so you can time a last-moment bid without watching the clock.");
	public final Setting.Choice soundsFor = add(new Setting.Choice("sounds_for", "Countdown for", "ALERTS",
			List.of("Every auction", "Auctions you bid on"), "Every auction"),
		"Every auction: tick for every auction. Auctions you bid on: only once you have bid, so auctions you ignore stay quiet.");
	public final Setting.Slider volume = add(new Setting.Slider("volume", "Volume", "ALERTS", 0, 100, 5, 70, "%.0f%%"),
		"How loud the auction sounds are.");
	public final Setting.Choice increment = add(new Setting.Choice("increment", "Bid key adds", "BID KEY",
			List.of("$1", "$10", "$50", "$100", "$1,000", "1%", "5%", "10%"), "$1"),
		"What the bid key adds to the top bid. Give Live Auctions a key on its Keybind row: pressing it bids the top bid plus this, with /liveauction bid.");
	public final Setting.Toggle confirm = add(new Setting.Toggle("confirm", "Press twice to bid", "BID KEY", true),
		"The first press shows what you would bid; a second press within a couple of seconds sends it. Turn off to bid on the first press.");
	public final Setting.Toggle hideBids = add(new Setting.Toggle("hide_bids", "Hide bid lines", "CHAT", false),
		"Hides \"Name has bid $N\" from chat, since the panel shows the top bid. Auction starts and results still show.");
	public final Setting.Toggle hideAll = add(new Setting.Toggle("hide_all", "Hide all auction lines", "CHAT", false),
		"Hides every live auction line from chat, starts and results too. The panel and alerts keep working.");

	private @Nullable Auction current;
	private final List<Long> durations = new ArrayList<>();
	/** What each item sold for, newest last, by its lower-case name. */
	private final Map<String, List<Double>> sales = new LinkedHashMap<>();
	private int lastTick = -1;
	private long armedAt;
	private long armedBid;
	private boolean loaded;

	public LiveAuctions() {
		super("live_auctions", Category.SERVER, "Live Auctions", Icons.COINS,
			"A countdown for live auctions in chat: the top bid and who holds it, a warning when you are outbid, ticks in the last seconds, "
				+ "what the item usually sells for, and a key to bid.", true);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	@Override
	public String bindHint() {
		return "Press it during an auction to bid the top bid plus the amount set under Bid key adds.";
	}

	@Override
	public void onBindPressed() {
		if (!enabled()) toggle();
		bidKey();
	}

	// ------------------------------------------------------------------ chat

	/** A server message: returns whether to hide it from chat. */
	public boolean onServerMessage(String plain) {
		if (!active() || !plain.contains("Auction")) return false;
		load();
		long now = System.currentTimeMillis();
		Matcher m;
		if ((m = START.matcher(plain)).find()) {
			begin(new Auction(cleanItem(m.group(2)), m.group(1), money(m.group(3)), now, now + duration()));
			return hideAll.get();
		}
		if ((m = LIVE_START.matcher(plain)).find()) {
			String count = m.group(3);
			String item = cleanItem(m.group(4));
			begin(new Auction(count == null || count.equals("1") ? item : count + "x " + item, m.group(1), money(m.group(2)), now, now + duration()));
			return hideAll.get();
		}
		if ((m = BID.matcher(plain)).find() || (m = LIVE_BID.matcher(plain)).find()) {
			bid(m.group(1), money(m.group(2)), m.groupCount() >= 3 ? cleanItem(m.group(3)) : null, now);
			return hideAll.get() || hideBids.get();
		}
		if ((m = WON.matcher(plain)).find()) {
			finish(End.SOLD, m.group(1), money(m.group(2)), now);
			return hideAll.get();
		}
		if ((m = LIVE_WON.matcher(plain)).find()) {
			finish(End.SOLD, m.group(1), money(m.group(3)), now);
			return hideAll.get();
		}
		if (NO_BIDS.matcher(plain).find()) {
			finish(End.NO_BIDS, null, 0, now);
			return hideAll.get();
		}
		if ((m = ENDING.matcher(plain)).find()) {
			// the server's own countdown: the exact end
			if (current != null && !current.ended()) current.endsAt = now + Long.parseLong(m.group(1)) * 1000;
			return hideAll.get();
		}
		return false;
	}

	private void begin(Auction a) {
		current = a;
		lastTick = -1;
		armedAt = 0;
		if (alerts.has("Start")) play(SoundEvents.NOTE_BLOCK_CHIME, 1.4f, 0.45f);
	}

	private void bid(String bidder, double amount, @Nullable String item, long now) {
		Auction a = current;
		if (a == null || a.ended()) {
			// joined part way through: the bids still count, but the time left is unknown
			a = current = new Auction(item != null ? item : "Auction", "", amount, 0, 0);
			lastTick = -1;
		}
		boolean wasYours = a.youLead();
		a.topBidder = bidder;
		a.topBid = amount;
		a.bids++;
		a.lastAt = now;
		armedAt = 0;
		if (Friends.isYourName(bidder)) {
			a.youBid = true;
		} else if (wasYours) {
			a.outbidAt = now;
			if (alerts.has("Outbid")) {
				play(SoundEvents.NOTE_BLOCK_BASS, 0.7f, 0.9f);
				Notices.push("Outbid", bidder + " bid " + money(amount) + " on " + a.item, 0xF87171, Icons.ALERT);
			}
		}
	}

	private void finish(End how, @Nullable String winner, double amount, long now) {
		Auction a = current;
		if (a == null || a.ended()) return;
		if (how == End.SOLD && winner != null) {
			a.topBidder = winner;
			a.topBid = amount;
		}
		a.end = how;
		a.endedAt = now;
		// only an auction seen from its start teaches the length
		if (how != End.LOST && a.startAt > 0) {
			long length = now - a.startAt;
			if (length >= 20_000 && length <= 600_000) {
				durations.add(length);
				while (durations.size() > DURATION_SAMPLES) durations.removeFirst();
			}
		}
		if (how == End.SOLD && !a.item.isEmpty() && !a.item.equals("Auction")) {
			List<Double> list = sales.computeIfAbsent(key(a.item), k -> new ArrayList<>());
			list.add(a.topBid);
			while (list.size() > SALES_KEPT) list.removeFirst();
			while (sales.size() > ITEMS_KEPT) sales.remove(sales.keySet().iterator().next());
		}
		if (how != End.LOST) save();
		if (how == End.SOLD && a.youLead() && alerts.has("Won")) {
			play(SoundEvents.NOTE_BLOCK_BELL, 1.2f, 0.9f);
			Notices.push("You won the auction", a.item + " for " + money(a.topBid), 0x4ADE80, Icons.TROPHY);
		}
	}

	// ------------------------------------------------------------------ tick

	@Override
	public void tick(Minecraft mc) {
		Auction a = current;
		if (a == null) return;
		if (!active() || mc.player == null) {
			current = null;
			return;
		}
		long now = System.currentTimeMillis();
		if (a.ended()) {
			if (now - a.endedAt > RESULT_MS) current = null;
			return;
		}
		// an auction joined part way has no clock, so it goes once chat has been quiet about it for a whole auction
		if (a.timed() ? now > a.endsAt + LOST_MS : now - a.lastAt > duration() + LOST_MS) {
			finish(End.LOST, null, 0, now);
			return;
		}
		if (a.timed() && !countdown.is("Off") && (a.youBid || soundsFor.is("Every auction"))) {
			int from = countdown.is("Last 10 seconds") ? 10 : 5;
			int second = (int) ((a.endsAt - now + 999) / 1000);
			if (second >= 1 && second <= from && second != lastTick) {
				lastTick = second;
				// rises as it nears the end, and gets louder
				play(SoundEvents.NOTE_BLOCK_HAT, 2.0f - second / 10f * 1.1f, 0.55f + 0.45f * (1 - second / (float) from));
			}
		}
		if (armedAt != 0 && now - armedAt > CONFIRM_MS) armedAt = 0;
	}

	// ------------------------------------------------------------------ bidding

	/** What the bid key would bid now: the top bid plus the chosen step, in whole dollars (the server takes no others). */
	public long nextBid() {
		Auction a = current;
		if (a == null) return 0;
		String step = increment.get();
		double add = step.endsWith("%")
			? a.topBid * Double.parseDouble(step.substring(0, step.length() - 1)) / 100
			: Double.parseDouble(step.replace("$", "").replace(",", ""));
		return (long) Math.ceil(a.topBid + Math.max(1, add));
	}

	private void bidKey() {
		Minecraft mc = Minecraft.getInstance();
		Auction a = current;
		if (!active() || mc.getConnection() == null) return;
		if (a == null || a.ended()) {
			Notices.push("No auction running", "The bid key works while an auction is on", 0x8E8E94, Icons.COINS);
			return;
		}
		if (a.youLead()) {
			Notices.push("You have the top bid", money(a.topBid) + " on " + a.item, 0x4ADE80, Icons.CHECK);
			return;
		}
		long amount = nextBid();
		long now = System.currentTimeMillis();
		// a second press only sends the amount it was shown, not a higher one that a new bid made since
		if (confirm.get() && (armedAt == 0 || now - armedAt > CONFIRM_MS || armedBid != amount)) {
			armedAt = now;
			armedBid = amount;
			Notices.push("Press again to bid " + money(amount), a.item + " · top bid " + money(a.topBid), 0xFBBF24, Icons.COINS);
			return;
		}
		armedAt = 0;
		mc.getConnection().sendCommand("liveauction bid " + amount);
	}

	// ------------------------------------------------------------------ reading

	public @Nullable Auction current() {
		return active() ? current : null;
	}

	/** How long auctions last: what the last ones did, or a minute until some have been seen. */
	public long duration() {
		load();
		if (durations.isEmpty()) return DEFAULT_MS;
		List<Long> sorted = new ArrayList<>(durations);
		Collections.sort(sorted);
		int n = sorted.size();
		return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2;
	}

	/** What an item has sold for before, oldest first; empty when it has not been seen. */
	public List<Double> pastSales(String item) {
		load();
		List<Double> l = sales.get(key(item));
		return l == null ? List.of() : List.copyOf(l);
	}

	/** The middle of an item's past prices, or 0 when it has none. */
	public double usualPrice(String item) {
		List<Double> l = new ArrayList<>(pastSales(item));
		if (l.isEmpty()) return 0;
		Collections.sort(l);
		int n = l.size();
		return n % 2 == 1 ? l.get(n / 2) : (l.get(n / 2 - 1) + l.get(n / 2)) / 2;
	}

	@Override
	public String status() {
		if (!enabled()) return null;
		Auction a = current();
		if (a == null) return "No auction";
		if (a.ended()) return a.end == End.SOLD ? "Sold for " + money(a.topBid) : "Ended";
		return a.item + " · " + money(a.topBid) + (a.timed() ? " · " + (a.left() + 999) / 1000 + "s" : "");
	}

	/** "$1,250", "$12.5K", "$3.2M": short enough for the panel. */
	public static String money(double v) {
		if (v >= 1e9) return "$" + trim(v / 1e9) + "B";
		if (v >= 1e6) return "$" + trim(v / 1e6) + "M";
		if (v >= 100_000) return "$" + trim(v / 1e3) + "K";
		if (Math.abs(v - Math.rint(v)) < 0.005) return String.format(Locale.ROOT, "$%,d", (long) Math.rint(v));
		return String.format(Locale.ROOT, "$%,.2f", v);
	}

	private static String trim(double v) {
		String s = String.format(Locale.ROOT, "%.2f", v);
		return s.replaceAll("\\.?0+$", "");
	}

	private static double money(String s) {
		try {
			return Double.parseDouble(s.replace(",", ""));
		} catch (NumberFormatException e) {
			return 0;
		}
	}

	/** An item name without the server's icon characters and doubled spaces. */
	private static String cleanItem(String s) {
		StringBuilder out = new StringBuilder();
		s.codePoints().filter(cp -> Character.getType(cp) != Character.PRIVATE_USE).forEach(out::appendCodePoint);
		return out.toString().replaceAll("\\s+", " ").strip();
	}

	private static String key(String item) {
		return item.toLowerCase(Locale.ROOT);
	}

	private void play(Holder<SoundEvent> sound, float pitch, float loudness) {
		float v = volume.get() / 100f * loudness;
		if (v <= 0.001f) return;
		Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound.value(), pitch, v));
	}

	// ------------------------------------------------------------------ testing

	/** For the autotest: as if this auction's start line came in {@code agoMs} ago. */
	public void debugStartedAgo(long agoMs) {
		if (current != null) {
			current.startAt -= agoMs;
			current.endsAt -= agoMs;
		}
	}

	/** For the autotest and the HUD editor's sample. */
	public void debugClear() {
		current = null;
		armedAt = 0;
	}

	// ------------------------------------------------------------------ saving

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient").resolve("auctions.json");
	}

	private void load() {
		if (loaded) return;
		loaded = true;
		try {
			if (!Files.isRegularFile(file())) return;
			JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
			if (o.has("durations")) for (JsonElement e : o.getAsJsonArray("durations")) durations.add(e.getAsLong());
			if (o.has("sales")) {
				for (var e : o.getAsJsonObject("sales").entrySet()) {
					List<Double> l = new ArrayList<>();
					for (JsonElement p : e.getValue().getAsJsonArray()) l.add(p.getAsDouble());
					sales.put(e.getKey(), l);
				}
			}
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not read {}", file(), e);
		}
	}

	private void save() {
		JsonObject o = new JsonObject();
		JsonArray d = new JsonArray();
		for (long v : durations) d.add(v);
		o.add("durations", d);
		JsonObject s = new JsonObject();
		for (var e : sales.entrySet()) {
			JsonArray l = new JsonArray();
			for (double v : e.getValue()) l.add(v);
			s.add(e.getKey(), l);
		}
		o.add("sales", s);
		try {
			Files.createDirectories(file().getParent());
			Files.writeString(file(), o.toString());
		} catch (Exception e) {
			LogUtils.getLogger().warn("Leone Client: could not write {}", file(), e);
		}
	}
}
