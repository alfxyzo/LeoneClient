package dev.alfxyz.leoneclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.features.ActionBars;
import dev.alfxyz.leoneclient.features.AutoReconnect;
import dev.alfxyz.leoneclient.features.ChatTabs;
import dev.alfxyz.leoneclient.features.PackPrefetch;
import dev.alfxyz.leoneclient.features.HudTailor;
import dev.alfxyz.leoneclient.features.ItemCooldowns;
import dev.alfxyz.leoneclient.features.LiveAuctions;
import dev.alfxyz.leoneclient.features.Timers;
import dev.alfxyz.leoneclient.features.RocketCheck;
import dev.alfxyz.leoneclient.features.WebEscape;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.mixin.ChatHistoryAccessor;
import dev.alfxyz.leoneclient.staffchat.StaffPlaceholder;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.HudEditorScreen;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.features.ChatHooks;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import dev.alfxyz.leoneclient.staffchat.StaffState;
import dev.alfxyz.leoneclient.ui.ChatLineActions;
import dev.alfxyz.leoneclient.ui.LeoneScreen;
import dev.alfxyz.leoneclient.web.Friends;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.DisconnectedScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Development-only driver, enabled with -Dleoneclient.autotest=true: joins (or
 * creates) a test world, walks the menu through its pages, exercises the
 * features with simulated server messages, and saves screenshots to
 * leone-shots in the run folder. Not active in normal play.
 */
public final class DevAutomation {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String WORLD = "Leone Test";

	private record Step(long atMs, String label, Consumer<Minecraft> action) {
	}

	private static boolean worldRequested;
	private static int titleStage;
	private static long titleAt;
	private static long readyAt = -1;
	private static int stepIndex;
	private static final List<Step> STEPS = new ArrayList<>();
	private static Path shots;
	private static final InputConstants.Key RIGHT_SHIFT = InputConstants.Type.KEYSYM.getOrCreate(GLFW.GLFW_KEY_RIGHT_SHIFT);
	private static long t;
	private static int atlasGenBefore;

	private DevAutomation() {
	}

	public static void init() {
		if (!Boolean.getBoolean("leoneclient.autotest")) return;
		shots = Path.of("leone-shots");
		buildScript();
		ClientTickEvents.END_CLIENT_TICK.register(DevAutomation::tick);
	}

	private static void tick(Minecraft mc) {
		// the test window may be behind others; a pause menu would get in the way of every step
		mc.options.pauseOnLostFocus = false;
		if (!worldRequested && mc.level == null && mc.gui.screen() != null && mc.gui.screen().getClass().getSimpleName().contains("Onboarding")) {
			// a fresh game folder starts on the accessibility screen
			mc.options.onboardAccessibility = false;
			mc.options.save();
			mc.gui.setScreen(new TitleScreen());
			return;
		}
		long clock = System.currentTimeMillis();
		if (!worldRequested && mc.level == null && titleStage < 3) {
			// first the menu over the title screen, as Mod Menu opens it
			if (titleStage == 0 && mc.gui.screen() instanceof TitleScreen title && mc.gui.overlay() == null) {
				mc.options.tutorialStep = net.minecraft.client.tutorial.TutorialSteps.NONE;
				titleStage = 1;
				titleAt = clock;
				mc.gui.setScreen(new LeoneScreen(title));
			} else if (titleStage == 1 && clock - titleAt > 1800) {
				capture(mc, "00-title-screen-menu");
				titleStage = 2;
				titleAt = clock;
			} else if (titleStage == 2 && clock - titleAt > 400) {
				if (mc.gui.screen() instanceof LeoneScreen s) s.onClose();
				titleStage = 3;
				titleAt = clock;
			}
			return;
		}
		if (!worldRequested && mc.gui.screen() instanceof TitleScreen && mc.level == null && clock - titleAt > 800) {
			LOGGER.info("Leone autotest: menu closed back to {}", mc.gui.screen().getClass().getSimpleName());
			worldRequested = true;
			try {
				if (mc.getLevelSource().levelExists(WORLD)) {
					mc.createWorldOpenFlows().openWorld(WORLD, () -> mc.gui.setScreen(new TitleScreen()));
				} else {
					LevelSettings settings = new LevelSettings(WORLD, GameType.CREATIVE,
						new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
					mc.createWorldOpenFlows().createFreshLevel(WORLD, settings, new WorldOptions(20261008L, true, false),
						WorldPresets::createNormalWorldDimensions, new SelectWorldScreen(new TitleScreen()));
				}
			} catch (Exception e) {
				LOGGER.error("Leone autotest: could not open world", e);
			}
			return;
		}
		if (mc.player == null || mc.level == null) return;
		long now = System.currentTimeMillis();
		if (readyAt < 0) {
			readyAt = now + 6000; // let chunks load
			return;
		}
		while (stepIndex < STEPS.size() && now - readyAt >= STEPS.get(stepIndex).atMs) {
			Step s = STEPS.get(stepIndex++);
			try {
				LOGGER.info("Leone autotest: {}", s.label);
				s.action.accept(mc);
			} catch (Exception e) {
				LOGGER.error("Leone autotest step '{}' failed", s.label, e);
			}
		}
	}

	// ---------------------------------------------------------------- script

	private static void at(long delay, String label, Consumer<Minecraft> action) {
		t += delay;
		STEPS.add(new Step(t, label, action));
	}

	private static void shot(long delay, String name) {
		at(delay, "shot " + name, mc -> capture(mc, name));
	}

	private static LeoneScreen screen(Minecraft mc) {
		return mc.gui.screen() instanceof LeoneScreen s ? s : null;
	}

	private static void move(Minecraft mc, float[] p) {
		LeoneScreen s = screen(mc);
		if (s != null) s.mouseMoved(p[0], p[1]);
	}

	private static void click(Minecraft mc, float[] p, int button) {
		LeoneScreen s = screen(mc);
		if (s == null) return;
		s.mouseMoved(p[0], p[1]);
		MouseButtonEvent e = new MouseButtonEvent(p[0], p[1], new MouseButtonInfo(button, 0));
		s.mouseClicked(e, false);
		s.mouseReleased(e);
	}

	private static void key(Minecraft mc, int key) {
		LeoneScreen s = screen(mc);
		if (s != null) s.keyPressed(new KeyEvent(key, GLFW.glfwGetKeyScancode(key), 0));
	}

	private static void server(Minecraft mc, String text) {
		mc.gui.hud.getChat().addServerSystemMessage(Component.literal(text));
	}

	/** LeoneMC-style lines for every chat feature. */
	private static void chatLines(Minecraft mc) {
		String me = mc.getUser().getName();
		server(mc, "[Alert] We are currently looking for staff members to join our team. [Click to apply]");
		server(mc, "[Alert] Proxy restarting in 2 minutes. You will be disconnected.");
		server(mc, "[Alert] 2500 Gems LAVA RISING EVENT (EU) (EU West) starting in 3 minutes. [Click to join]");
		server(mc, "Friends | Friend_One has joined the server WildKits.");
		server(mc, "Friends | Friend_Two has left the server ElytraBox.");
		server(mc, "Owner Friend_One » hey " + me + ", want to duel?");
		server(mc, "☠ Victim_One was slain by " + me + " using Sword.");
		server(mc, "☠ Victim_Two was shot by " + me + ".");
		server(mc, "☠ " + me + " was slain by Victim_Two.");
		server(mc, "Anticheat > Cheater flagged Reach A (12.0x) (ElytraBox)");
		server(mc, "Anticheat > Cheater flagged Reach A (13.0x) (ElytraBox)");
		server(mc, "Anticheat > Cheater flagged Simulation (2.0x) (ElytraBox)");
		server(mc, "[Report] (WildKits) Bad_Guy was reported by Good_Guy for hacking.");
		server(mc, "[Staff] (ElytraBox) Helper_One: anyone free to check WildKits?");
		server(mc, "Punishment applied: ban on Cheater for Cheating (ID: 1A2B3C4D)");
		server(mc, "Connecting you to ElytraBox.");
	}

	/** Action bar, titles, commands and chat that pass through the HUD and network hooks. */
	private static void hudAndCommands(Minecraft mc) {
		mc.gui.hud.setOverlayMessage(Component.literal("§6Gems: §e1,250 §7| §bKills: 12"), false);
		mc.gui.hud.setOverlayMessage(Component.literal("§cCombat Tag §7| §f14.5"), false);
		mc.gui.hud.setTitle(Component.literal("Resource Pack Declined"));
		mc.gui.hud.setSubtitle(Component.literal("Please accept the resource pack"));
		mc.getConnection().sendChat("that was a shit play lol");
		mc.getConnection().sendCommand("ec 2");
		mc.getConnection().sendCommand("enderchest 3");
		mc.getConnection().sendCommand("msg Somebody you are gay");
		LoggerFactory.getLogger("Scoreboard").warn("Requested creation of existing team '{}'", "glow-GREEN");
	}

	private static void check(String what, boolean ok) {
		if (ok) LOGGER.info("Leone autotest CHECK pass: {}", what);
		else LOGGER.error("Leone autotest CHECK FAIL: {}", what);
	}

	/** Puts items in the hotbar on the integrated server, like LeoneMC's mod mode does. Null clears a slot. */
	private static net.minecraft.world.item.ItemStack hotbarStack(String[] named, int i) {
		if (i >= named.length || named[i] == null) return net.minecraft.world.item.ItemStack.EMPTY;
		String[] parts = named[i].split("\\|", 2);
		net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
			net.minecraft.resources.Identifier.withDefaultNamespace(parts[0]));
		net.minecraft.world.item.ItemStack stack = new net.minecraft.world.item.ItemStack(item);
		if (parts.length > 1) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(parts[1]));
		return stack;
	}

	private static void hotbar(Minecraft mc, String... named) {
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		// the client's copy changes at once, as a real join starts from a fresh inventory, and the server's to match
		if (mc.player != null) for (int i = 0; i < 9; i++) mc.player.getInventory().setItem(i, hotbarStack(named, i));
		server.execute(() -> {
			var player = server.getPlayerList().getPlayers().getFirst();
			for (int i = 0; i < 9; i++) player.getInventory().setItem(i, hotbarStack(named, i));
		});
	}

	private static final String[] NORMAL_HOTBAR = {"diamond_sword", "bow", "golden_apple", null, null, null, null, null, "cooked_beef"};
	private static final String[] MOD_HOTBAR = {"compass|Random Teleport", "packed_ice|Freeze", null, "book|Inspect Inventory", null, null, null, "ender_eye|Vanish", "clock|Online Staff"};

	/** Mod mode by message, learning its items, a silent join recognised from them, vanish timing and spoofing. */
	private static void staffStateChecks() {
		at(300, "staff: start clean", mc -> {
			StaffState.debugForget();
			StaffState.onJoin();
			Modules.STAFF_CHAT.setEnabled(true);
			Modules.STAFF_CHAT.reveal.value = true;
			hotbar(mc, NORMAL_HOTBAR);
			check("mod mode starts unknown after a join", StaffState.modMode() == StaffState.ModMode.UNKNOWN);
		});
		at(800, "staff: player chat cannot switch mod mode", mc -> {
			ChatHooks.incoming(Component.literal("You are now in mod mode"), net.minecraft.client.multiplayer.chat.GuiMessageSource.PLAYER);
			check("player chat saying 'You are now in mod mode' is ignored", StaffState.modMode() == StaffState.ModMode.UNKNOWN);
		});
		at(100, "staff: mod mode on", mc -> {
			server(mc, "You are now in mod mode");
			hotbar(mc, MOD_HOTBAR);
		});
		at(200, "staff: on by message", mc -> {
			check("mod mode on from the message", StaffState.modMode() == StaffState.ModMode.ON && StaffState.modSource() == StaffState.Source.MESSAGE);
			check("staff chat shown while in mod mode (reveal on)", StaffChat.revealed() && !StaffChat.isHidden());
			Modules.STAFF_CHAT.reveal.value = false;
		});
		at(100, "staff: reveal setting applies at once", mc -> {
			check("turning reveal off hides staff chat straight away", !StaffChat.revealed() && StaffChat.isHidden());
			Modules.STAFF_CHAT.reveal.value = true;
		});
		at(100, "staff: reveal back on", mc -> check("turning reveal on shows it straight away", StaffChat.revealed() && !StaffChat.isHidden()));
		at(1800, "staff: mod mode off", mc -> {
			server(mc, "You are no longer in mod mode");
			hotbar(mc, NORMAL_HOTBAR);
		});
		at(200, "staff: off by message", mc -> check("mod mode off from the message", StaffState.modMode() == StaffState.ModMode.OFF));
		// switched back on soon after, as people do when they only meant to check: it must still learn
		at(1600, "staff: back on quickly", mc -> {
			server(mc, "You are now in mod mode");
			hotbar(mc, MOD_HOTBAR);
		});
		at(1600, "staff: learned", mc -> {
			check("mod mode's hotbar items were learned from quick switches", StaffState.itemsLearned());
			LOGGER.info("Leone autotest: {}", StaffState.describe());
			// a silent join in mod mode, as with LeoneMC's "Enable Mod Mode on Join"
			StaffState.onJoin();
			hotbar(mc, MOD_HOTBAR);
		});
		at(1500, "staff: silent join recognised", mc -> {
			check("silent join in mod mode recognised from the hotbar", StaffState.modMode() == StaffState.ModMode.ON && StaffState.modSource() == StaffState.Source.ITEMS);
			StaffState.onJoin();
			hotbar(mc, NORMAL_HOTBAR);
		});
		at(1500, "staff: not yet settled", mc -> check("a join without mod mode items is unknown until the hotbar settles", StaffState.modMode() == StaffState.ModMode.UNKNOWN));
		at(8000, "staff: settled", mc -> {
			check("a join without mod mode items ends as not in mod mode", StaffState.modMode() == StaffState.ModMode.OFF);
			check("not revealed out of mod mode", !StaffChat.revealed());
		});
		for (int i = 0; i < 5; i++) at(i == 0 ? 100 : 1000, "staff: vanish action bar", mc -> mc.gui.hud.setOverlayMessage(Component.literal("§cYou are currently Vanished"), false));
		at(200, "staff: vanished", mc -> check("vanished while the action bar repeats", StaffState.vanished() && StaffChat.revealed()));
		at(2600, "staff: unvanished", mc -> {
			check("vanish ends within about two and a half seconds of the last action bar", !StaffState.vanished());
			LOGGER.info("Leone autotest: {}", StaffState.describe());
			Modules.STAFF_CHAT.setEnabled(false);
		});
		at(100, "staff: spoofed mentions", mc -> {
			String me = mc.getUser().getName();
			String before = String.valueOf(Modules.MENTIONS.status());
			server(mc, "[Staff] " + me + " has joined your server (from NA-Hub-01)");
			server(mc, "Infamous " + me + " has joined the lobby!");
			server(mc, "Victim_Three was killed by " + me + ".");
			server(mc, "Gold " + me + " » hello everyone");
			String afterSystem = String.valueOf(Modules.MENTIONS.status());
			check("joins, kills and your own messages are not mentions (" + before + " -> " + afterSystem + ")", before.equals(afterSystem));
			server(mc, "Bronze Friend_One » hey @" + me + " are you there");
			check("a player saying your name is a mention (" + Modules.MENTIONS.status() + ")", !before.equals(String.valueOf(Modules.MENTIONS.status())));
		});
	}

	/** Chat lines containing {@code part}, newest first (staff lines unwrapped). */
	private static List<String> chatWith(Minecraft mc, String part) {
		List<String> out = new ArrayList<>();
		for (var m : ((dev.alfxyz.leoneclient.mixin.ChatHistoryAccessor) mc.gui.hud.getChat()).leone$allMessages()) {
			Component c = m.content() instanceof dev.alfxyz.leoneclient.staffchat.StaffPlaceholder p ? p.real() : m.content();
			if (c.getString().contains(part)) out.add(c.getString());
		}
		return out;
	}

	private static void command(Minecraft mc, String cmd) {
		var server = mc.getSingleplayerServer();
		if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), cmd));
	}

	/** Timers from the sidebar, envoys and the Target; Chat Cleaner's hiding and stacking. */
	private static boolean tapWasOn;

	/** The disconnect screen counts down after a restart, and never after a ban; nothing actually connects. */
	private static void reconnectChecks() {
		at(200, "reconnect: reasons", mc -> {
			check("a kick is not reconnected", AutoReconnect.onPurpose("You were kicked from the server: Spamming"));
			check("a ban is not reconnected", AutoReconnect.onPurpose("You are permanently banned from this server!"));
			check("a login from elsewhere is not reconnected", AutoReconnect.onPurpose("You logged in from another location"));
			check("restarts and timeouts are reconnected", !AutoReconnect.onPurpose("Proxy restarting") && !AutoReconnect.onPurpose("Timed out")
				&& !AutoReconnect.onPurpose("Server closed") && !AutoReconnect.onPurpose("The server you were on has restarted"));
			Modules.AUTO_RECONNECT.setEnabled(true);
			Modules.AUTO_RECONNECT.delay.set(60);
			Modules.AUTO_RECONNECT.debugDropped(new ServerData("LeoneMC", "play.leonemc.net", ServerData.Type.OTHER), "Lifesteal");
			mc.gui.setScreen(new DisconnectedScreen(new TitleScreen(), Component.literal("Connection Lost"), Component.literal("Proxy restarting")));
		});
		at(400, "reconnect: countdown", mc -> check("the disconnect screen counts down to reconnecting",
			widgetTexts(mc).stream().anyMatch(t -> t.startsWith("Reconnecting in"))));
		shot(300, "48-reconnect");
		at(100, "reconnect: banned", mc -> {
			Modules.AUTO_RECONNECT.debugDropped(new ServerData("LeoneMC", "play.leonemc.net", ServerData.Type.OTHER), "Lifesteal");
			mc.gui.setScreen(new DisconnectedScreen(new TitleScreen(), Component.literal("Disconnected"), Component.literal("You are banned from LeoneMC")));
		});
		at(400, "reconnect: no countdown", mc -> {
			List<String> texts = widgetTexts(mc);
			check("after a ban there is only a Reconnect button, no countdown",
				texts.contains("Reconnect") && texts.stream().noneMatch(t -> t.startsWith("Reconnecting in")));
			mc.gui.setScreen(new DisconnectedScreen(new TitleScreen(), Component.literal("Disconnected"), Component.literal("Somewhere else failed")));
		});
		at(400, "reconnect: not ours", mc -> {
			check("another server failing to connect is left alone", widgetTexts(mc).stream().noneMatch(t -> t.startsWith("Reconnect")));
			Modules.AUTO_RECONNECT.debugForget();
			Modules.AUTO_RECONNECT.reset();
			mc.gui.setScreen(null);
		});
	}

	private static boolean has(int kinds, int wanted) {
		return (kinds & wanted) == wanted;
	}

	/** Each message the chat is drawing, newest first, as plain text. */
	private static List<String> shownTexts(Minecraft mc) {
		List<String> out = new ArrayList<>();
		GuiMessage last = null;
		for (GuiMessage.Line line : ((ChatHistoryAccessor) mc.gui.hud.getChat()).leone$lines()) {
			if (line.parent() == last) continue;
			last = line.parent();
			Component c = last.content();
			if (c instanceof StaffPlaceholder p) c = p.real();
			out.add(Chat.plain(c));
		}
		return out;
	}

	private static LiveAuctions.Auction auction() {
		return Modules.LIVE_AUCTIONS.current();
	}

	/** Live auctions in both of LeoneMC's formats: timing, top bid, outbid, hiding, joining late and the results. */
	private static void auctionChecks() {
		at(200, "auctions: start", mc -> {
			Modules.ALL_SERVERS.setEnabled(true);
			Modules.LIVE_AUCTIONS.setEnabled(true);
			Modules.LIVE_AUCTIONS.debugClear();
			Hud.byId("live_auctions").resetPosition();
			server(mc, "Auction » Seller_One is auctioning  Hawk Claw for $450 [Click to view item]");
			LiveAuctions.Auction a = auction();
			check("an auction start is picked up, item name cleaned", a != null && a.item.equals("Hawk Claw") && a.seller.equals("Seller_One") && a.topBid == 450);
			check("an auction runs for a minute from its start (" + (a == null ? -1 : a.left()) + " ms)", a != null && a.timed() && a.left() > 58_000 && a.left() <= 60_000);
			server(mc, "§6§lAuction §8» §f" + mc.getUser().getName() + " §7has bid §f$451§7. §a[Click to bid]");
		});
		at(200, "auctions: your bid", mc -> {
			LiveAuctions.Auction a = auction();
			check("your own bid makes you the top bidder", a != null && a.youLead() && a.youBid && a.topBid == 451);
			server(mc, "Auction » Bidder_Two has bid $1,475. [Click to bid]");
		});
		at(200, "auctions: outbid", mc -> {
			LiveAuctions.Auction a = auction();
			check("someone else's bid outbids you", a != null && !a.youLead() && a.outbidAt > 0 && a.topBid == 1475 && "Bidder_Two".equals(a.topBidder) && a.bids == 2);
			check("the bid key would bid the top bid plus a dollar (" + Modules.LIVE_AUCTIONS.nextBid() + ")", Modules.LIVE_AUCTIONS.nextBid() == 1476);
			Modules.LIVE_AUCTIONS.hideBids.value = true;
			check("bid lines can be hidden", ChatHooks.incoming(Component.literal("Auction » Bidder_Three has bid $1,500. [Click to bid]"),
				net.minecraft.client.multiplayer.chat.GuiMessageSource.SYSTEM_SERVER) == null && auction().topBid == 1500);
			Modules.LIVE_AUCTIONS.hideBids.value = false;
			Modules.LIVE_AUCTIONS.debugStartedAgo(36_000);
		});
		shot(500, "56-live-auction");
		at(100, "auctions: won", mc -> {
			Modules.LIVE_AUCTIONS.debugStartedAgo(24_000);
			server(mc, "Auction » Bidder_Three won the auction with a bid of $1,500!");
			LiveAuctions.Auction a = auction();
			check("the result ends the auction and stays a moment", a != null && a.end == LiveAuctions.End.SOLD && a.topBid == 1500);
			check("the sale is remembered for the item (" + Modules.LIVE_AUCTIONS.usualPrice("hawk claw") + ")", Modules.LIVE_AUCTIONS.pastSales("Hawk Claw").contains(1500.0));
			check("the length is learned from it (" + Modules.LIVE_AUCTIONS.duration() + " ms)", Math.abs(Modules.LIVE_AUCTIONS.duration() - 60_000) < 1500);
			Modules.LIVE_AUCTIONS.debugClear();
			server(mc, "Auction » Bidder_Two has bid $10. [Click to bid]");
		});
		at(200, "auctions: joined late", mc -> {
			LiveAuctions.Auction a = auction();
			check("a bid with no start seen shows the auction without a clock", a != null && !a.timed() && a.topBid == 10);
			server(mc, "Live Auction » Auction ending in 5");
			a = auction();
			check("the server's own countdown sets the exact end", a != null && a.timed() && a.left() > 4_000 && a.left() <= 5_000);
			server(mc, "Live Auction » Seller_One started an auction at $100 for 1x [Christmas Boots] [BID]");
			server(mc, "Live Auction » Bidder_Two bid $1,000 for [Christmas Boots] [Bid]");
			a = auction();
			check("the other auction format works too", a != null && a.item.equals("Christmas Boots") && a.timed() && a.topBid == 1000 && "Bidder_Two".equals(a.topBidder));
			server(mc, "Live Auction » Bidder_Two won [Christmas Boots] for $1000!");
			check("and its result", auction() != null && auction().end == LiveAuctions.End.SOLD);
			server(mc, "Auction » Seller_One is auctioning Switcher for $450 [Click to view item]");
			server(mc, "Auction » No one bid on the auction!");
			check("an auction nobody bid on ends with no bids", auction() != null && auction().end == LiveAuctions.End.NO_BIDS);
		});
		at(100, "auctions: done", mc -> {
			Modules.LIVE_AUCTIONS.debugClear();
			Modules.ALL_SERVERS.setEnabled(false);
		});
	}

	private static ItemCooldowns.Tracker tracker(String id) {
		return Modules.ITEM_COOLDOWNS.trackers.stream().filter(t -> t.id.equals(id)).findFirst().orElseThrow();
	}

	private static net.minecraft.core.BlockPos webFeet = net.minecraft.core.BlockPos.ZERO, webEye = net.minecraft.core.BlockPos.ZERO;

	/** Web Escape on your own player (the test world has nobody else): in a web up to the eyes, then only to the waist. */
	private static void webChecks() {
		at(200, "web: set up", mc -> {
			Modules.ALL_SERVERS.setEnabled(true);
			Category.pretendServer = "ElytraBox";
			Modules.WEB_ESCAPE.setEnabled(true);
			WebEscape.debugGliding = true;
			WebEscape.debugIncludeSelf = true;
			// a glass platform high in the sky, so the only blocks near are the webs and the glass
			command(mc, "execute at @p run fill ~-2 ~39 ~-2 ~2 ~39 ~2 minecraft:glass");
			command(mc, "execute at @p run tp @p ~ ~40 ~ 0 0");
		});
		at(1200, "web: webs", mc -> {
			// where the player really is now: glass under the feet, webs from the feet up to the eyes
			webFeet = net.minecraft.core.BlockPos.containing(mc.player.position());
			webEye = net.minecraft.core.BlockPos.containing(mc.player.getEyePosition(1));
			command(mc, "setblock " + webFeet.getX() + " " + (webFeet.getY() - 1) + " " + webFeet.getZ() + " minecraft:glass");
			for (int y = webFeet.getY(); y <= webEye.getY(); y++) command(mc, "setblock " + webFeet.getX() + " " + y + " " + webFeet.getZ() + " minecraft:cobweb");
		});
		at(700, "web: up to the eyes", mc -> {
			mc.player.setXRot(0);
			WebEscape.Look l = WebEscape.look(mc.player, 1);
			check("eyes inside a web: the crosshair is on the web, so a rocket would hit it (" + l.state() + ")", l.state() == WebEscape.State.BLOCKED && l.aimAtWeb());
			mc.options.setCameraType(net.minecraft.client.CameraType.THIRD_PERSON_BACK);
		});
		shot(600, "56-web-blocked");
		at(100, "web: head free", mc -> {
			for (int y = webFeet.getY() + 1; y <= webEye.getY(); y++) command(mc, "setblock " + webFeet.getX() + " " + y + " " + webFeet.getZ() + " minecraft:air");
		});
		at(600, "web: waist", mc -> {
			mc.player.setXRot(0);
			WebEscape.Look l = WebEscape.look(mc.player, 1);
			check("only the feet in a web, looking at air: a rocket would boost them out (" + l.state() + ", " + l.webs().size() + " web)", l.state() == WebEscape.State.CAN_ESCAPE && !l.webs().isEmpty());
			mc.player.setXRot(80);
			WebEscape.Look down = WebEscape.look(mc.player, 1);
			check("looking down at the web below: blocked again (" + down.state() + ")", down.state() == WebEscape.State.BLOCKED);
			mc.player.setXRot(0);
			WebEscape.debugGliding = false;
			check("not gliding: a rocket does nothing (" + WebEscape.look(mc.player, 1).state() + ")", WebEscape.look(mc.player, 1).state() == WebEscape.State.NOT_GLIDING);
			WebEscape.debugGliding = true;
		});
		shot(500, "57-web-can-escape");
		at(100, "web: settings", mc -> {
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			mc.gui.setScreen(new LeoneScreen());
		});
		at(800, "web: segment", mc -> click(mc, screen(mc).debugSegment(Category.shown().size() - 1), 0));
		at(700, "web: open settings", mc -> screen(mc).debugSettings(Modules.WEB_ESCAPE));
		shot(900, "58-web-settings-top");
		at(100, "web: scroll", mc -> { float[] p = screen(mc).debugPanel(300, 300); screen(mc).mouseScrolled(p[0], p[1], 0, -10); });
		shot(700, "59-web-settings-bottom");
		at(100, "web: done", mc -> {
			mc.gui.setScreen(null);
			mc.options.setCameraType(net.minecraft.client.CameraType.FIRST_PERSON);
			WebEscape.debugGliding = false;
			WebEscape.debugIncludeSelf = false;
			command(mc, "setblock " + webFeet.getX() + " " + webFeet.getY() + " " + webFeet.getZ() + " minecraft:air");
			command(mc, "execute at @p run tp @p ~ ~-40 ~");
			Modules.WEB_ESCAPE.reset();
			Category.pretendServer = null;
			Modules.ALL_SERVERS.setEnabled(false);
		});
	}

	private static net.minecraft.core.BlockPos rocketBase = net.minecraft.core.BlockPos.ZERO;

	/**
	 * Rocket Check on your own player: glide at a stone wall and boost anyway (as a cheat does: straight to
	 * "use item", whatever the crosshair is on), then boost facing open sky, which must not flag.
	 */
	private static void rocketChecks() {
		at(200, "rocket: set up", mc -> {
			Modules.ALL_SERVERS.setEnabled(true);
			Category.pretendServer = "ElytraBox";
			Modules.ROCKET_CHECK.setEnabled(true);
			RocketCheck.debugIncludeSelf = true;
			command(mc, "item replace entity @p armor.chest with minecraft:elytra");
			command(mc, "item replace entity @p weapon.mainhand with minecraft:firework_rocket 16");
			// a stone wall high in the sky, three blocks north of where you will be
			rocketBase = net.minecraft.core.BlockPos.containing(mc.player.position());
			command(mc, "fill " + (rocketBase.getX() - 3) + " " + (rocketBase.getY() + 60) + " " + (rocketBase.getZ() - 4) + " "
				+ (rocketBase.getX() + 3) + " " + (rocketBase.getY() + 90) + " " + (rocketBase.getZ() - 4) + " minecraft:stone");
		});
		at(800, "rocket: face the wall", mc -> command(mc, "tp @p " + (rocketBase.getX() + 0.5) + " " + (rocketBase.getY() + 84) + " " + (rocketBase.getZ() + 0.5) + " 180 0"));
		at(700, "rocket: aim", mc -> {
			RocketCheck.Aim a = RocketCheck.aimNow(mc.player, 0.3);
			check("facing the wall: the crosshair is on it, within reach (" + a + ")", a != null && a.block().equals("Stone") && a.distance() < 4.5);
			RocketCheck.Aim up = RocketCheck.aim(mc.player, mc.player.position(), 180, -70, 0.3);
			check("looking up at the sky: on air (" + up + ")", up == null);
			mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
				net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
			mc.player.startFallFlying();
		});
		at(150, "rocket: boost into the wall", mc -> {
			LOGGER.info("Leone autotest: gliding = {}", mc.player.isFallFlying());
			mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND);
		});
		at(700, "rocket: flagged", mc -> {
			var l = Modules.ROCKET_CHECK.recent();
			check("boosting while looking at the wall is flagged (" + l.size() + " flagged)", l.size() == 1 && l.getFirst().count == 1);
			if (!l.isEmpty()) LOGGER.info("Leone autotest: rocket flag = {}", RocketCheck.detail(l.getFirst()));
		});
		shot(300, "60-rocket-flagged");
		at(100, "rocket: face away", mc -> command(mc, "tp @p " + (rocketBase.getX() + 0.5) + " " + (rocketBase.getY() + 84) + " " + (rocketBase.getZ() + 0.5) + " 0 0"));
		at(500, "rocket: glide again", mc -> {
			check("facing away: on air (" + RocketCheck.aimNow(mc.player, 0.3) + ")", RocketCheck.aimNow(mc.player, 0.3) == null);
			mc.player.connection.send(new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(mc.player,
				net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
			mc.player.startFallFlying();
		});
		at(150, "rocket: boost into the sky", mc -> mc.gameMode.useItem(mc.player, net.minecraft.world.InteractionHand.MAIN_HAND));
		at(700, "rocket: not flagged", mc -> {
			var l = Modules.ROCKET_CHECK.recent();
			check("boosting at open sky is not flagged (still " + (l.isEmpty() ? 0 : l.getFirst().count) + ")", l.size() == 1 && l.getFirst().count == 1);
		});
		at(100, "rocket: settings", mc -> mc.gui.setScreen(new LeoneScreen()));
		at(800, "rocket: segment", mc -> click(mc, screen(mc).debugSegment(Category.shown().size() - 1), 0));
		at(700, "rocket: open settings", mc -> screen(mc).debugSettings(Modules.ROCKET_CHECK));
		shot(900, "61-rocket-settings");
		sliderChecks();
		at(100, "rocket: done", mc -> {
			mc.gui.setScreen(null);
			RocketCheck.debugIncludeSelf = false;
			command(mc, "fill " + (rocketBase.getX() - 3) + " " + (rocketBase.getY() + 60) + " " + (rocketBase.getZ() - 4) + " "
				+ (rocketBase.getX() + 3) + " " + (rocketBase.getY() + 90) + " " + (rocketBase.getZ() - 4) + " minecraft:air");
			command(mc, "item replace entity @p armor.chest with minecraft:air");
			command(mc, "item replace entity @p weapon.mainhand with minecraft:air");
			command(mc, "tp @p " + (rocketBase.getX() + 0.5) + " " + rocketBase.getY() + " " + (rocketBase.getZ() + 0.5));
			Modules.ROCKET_CHECK.reset();
			Category.pretendServer = null;
			Modules.ALL_SERVERS.setEnabled(false);
		});
	}

	private static net.minecraft.client.player.RemotePlayer farPlayer;

	/** The line under a name for a player thirty blocks away: there with Score Sight, gone without. */
	private static void scoreSightChecks() {
		at(300, "score sight: a far player", mc -> {
			var sb = mc.level.getScoreboard();
			var obj = sb.addObjective("leone_hp", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY, Component.literal("❤").withStyle(net.minecraft.ChatFormatting.RED),
				net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
			sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.BELOW_NAME, obj);
			sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly("Far_Player"), obj).set(7);
			farPlayer = new net.minecraft.client.player.RemotePlayer(mc.level, new com.mojang.authlib.GameProfile(java.util.UUID.randomUUID(), "Far_Player"));
			var p = mc.player;
			var look = p.getLookAngle();
			farPlayer.snapTo(p.getX() + look.x * 30, p.getY(), p.getZ() + look.z * 30, p.getYRot() + 180, 0);
			farPlayer.setId(987_654);
			mc.level.addEntity(farPlayer);
			Modules.SCORE_SIGHT.reset();
			Modules.SCORE_SIGHT.setEnabled(true);
		});
		at(300, "score sight: on", mc -> {
			check("Score Sight shows the line under a name thirty blocks away (" + scoreText(mc) + ")", scoreText(mc) != null);
			Modules.SCORE_SIGHT.colour.value = true;
			var line = scoreText(mc);
			check("health colouring makes 7 of 20 red", line != null && line.getString().startsWith("7") && line.toFlatList().stream()
				.anyMatch(c -> c.getStyle().getColor() != null && c.getStyle().getColor().getValue() == 0xFF5555));
			Modules.SCORE_SIGHT.custom.set(20);
			Modules.SCORE_SIGHT.range.set("Custom");
			check("a custom range of 20 blocks leaves it out at 30", scoreText(mc) == null);
			Modules.SCORE_SIGHT.range.set("Unlimited");
			Modules.SCORE_SIGHT.friendsOnly.value = true;
			check("only friends: a stranger's line stays hidden far away", scoreText(mc) == null);
			Modules.SCORE_SIGHT.friendsOnly.value = false;
			Modules.SCORE_SIGHT.setEnabled(false);
			check("without Score Sight the game shows it only within ten blocks", scoreText(mc) == null);
			Modules.SCORE_SIGHT.setEnabled(true);
		});
		shot(400, "65-score-sight");
		at(100, "score sight: tidy", mc -> {
			mc.level.removeEntity(farPlayer.getId(), net.minecraft.world.entity.Entity.RemovalReason.DISCARDED);
			var sb = mc.level.getScoreboard();
			var obj = sb.getObjective("leone_hp");
			if (obj != null) sb.removeObjective(obj);
			Modules.SCORE_SIGHT.reset();
		});
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Component scoreText(Minecraft mc) {
		var renderer = (net.minecraft.client.renderer.entity.EntityRenderer) mc.getEntityRenderDispatcher().getRenderer(farPlayer);
		var state = (net.minecraft.client.renderer.entity.state.EntityRenderState) renderer.createRenderState(farPlayer, 1f);
		return state.scoreText;
	}

	private static dev.alfxyz.leoneclient.hud.VanillaHudEditor tailor(Minecraft mc) {
		return mc.gui.screen() instanceof dev.alfxyz.leoneclient.hud.VanillaHudEditor e ? e : null;
	}

	private static float[] partCentre(Minecraft mc, HudTailor.Part p) {
		var w = mc.getWindow();
		float[] r = Modules.HUD_TAILOR.screenBounds(p, mc, w.getGuiScaledWidth(), w.getGuiScaledHeight());
		return new float[] {r[0] + r[2] / 2, r[1] + r[3] / 2};
	}

	private static void tailorClick(Minecraft mc, float x, float y, int button) {
		var e = tailor(mc);
		if (e == null) return;
		var ev = new MouseButtonEvent(x, y, new MouseButtonInfo(button, 0));
		e.mouseMoved(x, y);
		e.mouseClicked(ev, false);
		e.mouseReleased(ev);
	}

	private static List<String> sidebarKeys() {
		List<String> out = new ArrayList<>();
		for (var l : dev.alfxyz.leoneclient.hud.Sidebar.lines()) if (!l.hidden()) out.add(l.key());
		return out;
	}

	/** HUD Tailor: a server-like scoreboard trimmed, parts moved, hidden and resized in the editor, numbers for bars. */
	private static void hudTailorChecks() {
		at(300, "tailor: scoreboard", mc -> {
			var sb = mc.level.getScoreboard();
			var obj = sb.addObjective("leone_side", net.minecraft.world.scores.criteria.ObjectiveCriteria.DUMMY,
				Component.literal("LEONEMC").withStyle(net.minecraft.ChatFormatting.GOLD, net.minecraft.ChatFormatting.BOLD),
				net.minecraft.world.scores.criteria.ObjectiveCriteria.RenderType.INTEGER, false, null);
			sb.setDisplayObjective(net.minecraft.world.scores.DisplaySlot.SIDEBAR, obj);
			String[] lines = {"Balance: $1,234", "Kills: 12", " ", "Rank: Member", "Koth in: 4:59", "  ", "play.leonemc.net"};
			for (int i = 0; i < lines.length; i++)
				sb.getOrCreatePlayerScore(net.minecraft.world.scores.ScoreHolder.forNameOnly(lines[i]), obj).set(lines.length - i);
			command(mc, "item replace entity @p armor.chest with minecraft:iron_chestplate");
			command(mc, "effect give @p minecraft:speed 120 0 true");
			Modules.HUD_TAILOR.reset();
			Modules.HUD_TAILOR.setEnabled(true);
			Modules.HUD_TAILOR.showAllLinesHere();
		});
		at(300, "tailor: drawn", mc -> {
			check("HUD Tailor draws the scoreboard with every line (" + sidebarKeys() + ")", sidebarKeys().size() == 7);
			Modules.HUD_TAILOR.hideAddress.value = true;
			Modules.HUD_TAILOR.hideBlank.value = true;
			Modules.HUD_TAILOR.hideWith.value = "rank";
			Modules.HUD_TAILOR.rename.value = "Balance > Money";
		});
		at(200, "tailor: trimmed", mc -> {
			List<String> keys = sidebarKeys();
			check("address, blank and word filters trim the scoreboard (" + keys + ")", keys.size() == 3 && keys.contains("Kills: ##") && !keys.contains("Rank: Member"));
		});
		shot(200, "66-tailor-scoreboard");
		at(100, "tailor: open editor", mc -> mc.gui.setScreen(Modules.HUD_TAILOR.editor()));
		at(400, "tailor: hide a line by clicking it", mc -> {
			var line = dev.alfxyz.leoneclient.hud.Sidebar.lines().stream().filter(l -> l.key().equals("Kills: ##")).findFirst().orElse(null);
			check("the editor sees the scoreboard's lines", line != null);
			if (line == null) return;
			tailorClick(mc, line.x() + line.w() / 2, line.y() + line.h() / 2, 0);
			check("clicking a scoreboard line in the editor hides it here", Modules.HUD_TAILOR.lineHidden("Kills: ##") && Modules.HUD_TAILOR.hiddenHere() == 1);
		});
		at(200, "tailor: drag the hotbar", mc -> {
			var e = tailor(mc);
			if (e == null) return;
			float[] c = partCentre(mc, HudTailor.Part.HOTBAR);
			e.mouseMoved(c[0], c[1]);
			e.mouseClicked(new MouseButtonEvent(c[0], c[1], new MouseButtonInfo(0, 0)), false);
			e.mouseDragged(new MouseButtonEvent(c[0] + 60, c[1] - 30, new MouseButtonInfo(0, 0)), 60, -30);
			e.mouseReleased(new MouseButtonEvent(c[0] + 60, c[1] - 30, new MouseButtonInfo(0, 0)));
			var l = Modules.HUD_TAILOR.layout(HudTailor.Part.HOTBAR);
			check("dragging the hotbar moves it (" + l.x.get() + ", " + l.y.get() + ")", l.x.get() == 60 && l.y.get() == -30);
			check("a drag is not a line click", Modules.HUD_TAILOR.hiddenHere() == 1);
			float[] a = partCentre(mc, HudTailor.Part.ARMOUR);
			tailorClick(mc, a[0], a[1], 1);
			check("right-clicking the armour bar hides it", !Modules.HUD_TAILOR.shown(HudTailor.Part.ARMOUR));
			// near the bar's left end, clear of the level number in its middle
			float[] xr = Modules.HUD_TAILOR.screenBounds(HudTailor.Part.XP_BAR, mc, mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
			e.mouseScrolled(xr[0] + 10, xr[1] + 2, 0, 1);
			e.mouseScrolled(xr[0] + 10, xr[1] + 2, 0, 1);
			var xl = Modules.HUD_TAILOR.layout(HudTailor.Part.XP_BAR);
			check("scrolling resizes a part (" + xl.scale.get() + ")", Math.abs(xl.scale.get() - 1.1f) < 0.001f);
			check("short key names fit a slot", HudTailor.shortKey("Mouse Button 5").equals("M5") && HudTailor.shortKey("Keypad 0").equals("N0")
				&& HudTailor.shortKey("F").equals("F"));
			e.mouseMoved(c[0] + 60, c[1] - 30);
		});
		shot(400, "67-tailor-editor");
		at(100, "tailor: numbers", mc -> {
			mc.gui.setScreen(null);
			command(mc, "gamemode survival @p");
			command(mc, "effect give @p minecraft:absorption 60 0 true");
			mc.gui.hud.getChat().clearMessages(false);
			Modules.HUD_TAILOR.healthStyle.set("Number");
			Modules.HUD_TAILOR.hungerStyle.set("Number");
			Modules.HUD_TAILOR.slotKeys.value = true;
			Modules.HUD_TAILOR.setShown(HudTailor.Part.ARMOUR, true);
			Modules.HUD_TAILOR.armourStyle.set("Number");
			// clear of the chat box, which is drawn over that corner
			Modules.HUD_TAILOR.layout(HudTailor.Part.ARMOUR).x.set(230);
			Modules.HUD_TAILOR.effects.flip("Vignette");
		});
		shot(500, "68-tailor-numbers");
		at(100, "tailor: present parts", mc -> check("in survival the health, hunger and armour (" + mc.player.getArmorValue() + ") are on screen, the air bar is not",
			Modules.HUD_TAILOR.present(HudTailor.Part.HEALTH, mc) && Modules.HUD_TAILOR.present(HudTailor.Part.HUNGER, mc)
				&& Modules.HUD_TAILOR.present(HudTailor.Part.ARMOUR, mc) && !Modules.HUD_TAILOR.present(HudTailor.Part.AIR, mc)));
		at(100, "tailor: off is vanilla", mc -> Modules.HUD_TAILOR.setEnabled(false));
		shot(400, "69-tailor-off");
		at(100, "tailor: modify hud", mc -> mc.gui.setScreen(new HudEditorScreen()));
		at(400, "tailor: vanilla button", mc -> {
			if (!(mc.gui.screen() instanceof HudEditorScreen e)) return;
			float[] b = e.debugVanillaButton();
			e.mouseMoved(b[0], b[1]);
		});
		shot(200, "70-modify-hud-vanilla-button");
		at(100, "tailor: click vanilla", mc -> {
			if (!(mc.gui.screen() instanceof HudEditorScreen e)) return;
			float[] b = e.debugVanillaButton();
			e.mouseClicked(new MouseButtonEvent(b[0], b[1], new MouseButtonInfo(0, 0)), false);
			check("Modify HUD's Vanilla HUD button opens HUD Tailor's editor, switching HUD Tailor on", tailor(mc) != null && Modules.HUD_TAILOR.enabled());
		});
		at(400, "tailor: back to overlays", mc -> {
			var e = tailor(mc);
			if (e == null) return;
			float[] b = e.debugOverlaysButton();
			tailorClick(mc, b[0], b[1], 0);
			check("its Leone overlays button goes back to Modify HUD", mc.gui.screen() instanceof HudEditorScreen);
			mc.gui.setScreen(new dev.alfxyz.leoneclient.hud.VanillaHudEditor(true));
		});
		at(300, "tailor: done from modify hud", mc -> {
			var e = tailor(mc);
			if (e != null) e.onClose();
			check("Done returns to the menu", mc.gui.screen() instanceof LeoneScreen);
		});
		at(800, "tailor: close menu", mc -> mc.gui.setScreen(null));
		at(100, "tailor: tidy", mc -> {
			Modules.HUD_TAILOR.reset();
			var sb = mc.level.getScoreboard();
			var obj = sb.getObjective("leone_side");
			if (obj != null) sb.removeObjective(obj);
			command(mc, "item replace entity @p armor.chest with minecraft:air");
			command(mc, "effect clear @p");
			command(mc, "gamemode creative @p");
		});
	}

	/** Point {@code f} of the way along a slider's track, as drawn. */
	private static float[] along(Minecraft mc, Setting.Slider sl, float f) {
		float[] d = screen(mc).debugSlider(sl);
		return new float[] {d[0] + (d[1] - d[0]) * f, d[2]};
	}

	private static float[] badge(Minecraft mc, Setting.Slider sl) {
		float[] d = screen(mc).debugSlider(sl);
		return new float[] {d[3], d[4]};
	}

	/** Sliders on Rocket Check's settings page: click, drag, type a value, then reset the page. */
	private static void sliderChecks() {
		Setting.Slider leeway = Modules.ROCKET_CHECK.leeway, minutes = Modules.ROCKET_CHECK.panelMinutes;
		at(100, "sliders: click three quarters along", mc -> {
			LOGGER.info("Leone autotest: leeway slider drawn at {}", java.util.Arrays.toString(screen(mc).debugSlider(leeway)));
			click(mc, along(mc, leeway, 0.75f), 0);
			check("clicking a slider three quarters along sets it there (" + leeway.get() + ")", Math.abs(leeway.get() - 1.125f) <= 0.051f);
		});
		at(100, "sliders: drag", mc -> {
			float[] from = along(mc, leeway, 0.5f), to = along(mc, leeway, 0.2f);
			LeoneScreen s = screen(mc);
			s.mouseMoved(from[0], from[1]);
			s.mouseClicked(new MouseButtonEvent(from[0], from[1], new MouseButtonInfo(0, 0)), false);
			float mid = leeway.get();
			s.debugDrag(to[0], to[1]);
			s.mouseReleased(new MouseButtonEvent(to[0], to[1], new MouseButtonInfo(0, 0)));
			check("dragging a slider follows the mouse (" + mid + " then " + leeway.get() + ")", Math.abs(mid - 0.75f) <= 0.051f && Math.abs(leeway.get() - 0.3f) <= 0.051f);
		});
		shot(300, "62-slider-dragged");
		at(100, "sliders: type a value", mc -> {
			click(mc, badge(mc, minutes), 0);
			screen(mc).debugType("12");
		});
		shot(300, "63-slider-typing");
		at(100, "sliders: enter", mc -> key(mc, GLFW.GLFW_KEY_ENTER));
		at(100, "sliders: typed", mc -> check("typing 12 into a slider's value sets it (" + minutes.get() + ")", minutes.get() == 12));
		at(100, "sliders: type too much", mc -> {
			click(mc, badge(mc, leeway), 0);
			screen(mc).debugType("9");
			click(mc, along(mc, minutes, 0.5f), 0);
			// the click elsewhere moves Keep players for, then the typed leeway applies on the next frame
		});
		at(200, "sliders: clamped", mc -> check("a typed value past the end stops at the end (" + leeway.get() + ")", leeway.get() == 1.5f));
		at(100, "sliders: reset once", mc -> {
			minutes.set(12);
			click(mc, screen(mc).debugResetButton(), 0);
			check("one click on Reset to defaults only asks to confirm", leeway.get() == 1.5f && minutes.get() == 12);
		});
		shot(300, "64-reset-confirm");
		at(100, "sliders: reset twice", mc -> {
			click(mc, screen(mc).debugResetButton(), 0);
			check("a second click puts the settings back (" + leeway.get() + ", " + minutes.get() + ")", leeway.get() == 0.3f && minutes.get() == 5);
			check("resetting leaves the module on", Modules.ROCKET_CHECK.enabled());
		});
	}

	/** Each LeoneMC server's category shows only there, its modules run only there, and ElytraBox's item cooldowns. */
	private static void serverChecks() {
		at(200, "servers: names", mc -> {
			check("server names find their category", Category.forServer("ElytraBox") == Category.ELYTRABOX && Category.forServer("Event-02") == Category.EVENTS
				&& Category.forServer("NA-Hub-01") == Category.HUB && Category.forServer("NAE-Practice-Spawn-1") == Category.PRACTICE
				&& Category.forServer("Beachfest") == Category.EVENTS && Category.forServer("RandomKits") == Category.WILDKITS);
			check("a server Leone Client does not know gets a category of its own", Category.forServer("Skyblock") == Category.OTHER);
			check("off LeoneMC there is no server category", Category.current() == null && !Category.shown().contains(Category.ELYTRABOX));
			Modules.ALL_SERVERS.setEnabled(true);
			Category.pretendServer = "ElytraBox";
			Modules.ITEM_COOLDOWNS.setEnabled(true);
			Hud.byId("item_cooldowns").resetPosition();
			check("on ElytraBox its category shows, and only it", Category.shown().contains(Category.ELYTRABOX) && !Category.shown().contains(Category.LIFESTEAL));
			check("ElytraBox's modules run on ElytraBox", Modules.ITEM_COOLDOWNS.active());
			hotbar(mc, "red_stained_glass|Cage", "bone_meal|Cobweb Circle", "stick", null, null, null, null, null, null);
			mc.player.getInventory().setSelectedSlot(0);
		});
		at(300, "cooldowns: use the cage", mc -> Modules.ITEM_COOLDOWNS.debugUse(InteractionHand.MAIN_HAND, ItemCooldowns.Trigger.USE));
		at(600, "cooldowns: cage running", mc -> {
			ItemCooldowns.Tracker cage = tracker("cage");
			check("using the Cage starts its three minute cooldown (" + cage.left() + " ms)", cage.running() && cage.left() > 170_000);
			server(mc, "You cannot use cage item for another 37 seconds!");
		});
		at(200, "cooldowns: synced", mc -> {
			ItemCooldowns.Tracker cage = tracker("cage");
			check("the server's time left corrects the timer (" + cage.left() + " ms)", cage.left() > 35_000 && cage.left() <= 37_000);
			// a use in the last moment of our timer, which the server takes as its own has already ended
			Modules.ITEM_COOLDOWNS.debugLeft("cage", 150);
			Modules.ITEM_COOLDOWNS.debugUse(InteractionHand.MAIN_HAND, ItemCooldowns.Trigger.USE);
		});
		at(600, "cooldowns: use at the end", mc -> {
			ItemCooldowns.Tracker cage = tracker("cage");
			check("a use while the timer is just ending still starts a new one (" + cage.left() + " ms)", cage.left() > 170_000);
			// the refusal of an earlier use arrives just after a use the server took
			Modules.ITEM_COOLDOWNS.debugLeft("cage", 0);
			Modules.ITEM_COOLDOWNS.debugUse(InteractionHand.MAIN_HAND, ItemCooldowns.Trigger.USE);
			server(mc, "You cannot use cage item for another 0.0 seconds!");
		});
		at(600, "cooldowns: late refusal", mc -> {
			ItemCooldowns.Tracker cage = tracker("cage");
			check("a refusal of an earlier use does not cancel a later one the server took (" + cage.left() + " ms)", cage.left() > 170_000);
			Modules.ITEM_COOLDOWNS.debugUse(InteractionHand.MAIN_HAND, ItemCooldowns.Trigger.USE);
			server(mc, "You cannot use cage item for another 37 seconds!");
		});
		at(600, "cooldowns: refused mid-way", mc -> {
			ItemCooldowns.Tracker cage = tracker("cage");
			check("a use the server refuses part-way through does not restart the timer (" + cage.left() + " ms)", cage.left() > 35_000 && cage.left() <= 37_000);
			mc.player.getInventory().setSelectedSlot(1);
		});
		at(200, "cooldowns: cobweb refused", mc -> {
			Modules.ITEM_COOLDOWNS.debugUse(InteractionHand.MAIN_HAND, ItemCooldowns.Trigger.USE);
			server(mc, "You cannot use cobweb circle here!");
		});
		at(600, "cooldowns: not started", mc -> {
			check("a use the server refuses does not start a timer", !tracker("cobweb_circle").running());
			Modules.ITEM_COOLDOWNS.debugLeft("cobweb_circle", 1200);
			mc.player.getInventory().setSelectedSlot(0);
		});
		shot(400, "53-item-cooldowns");
		at(1600, "cooldowns: ready", mc -> {
			ItemCooldowns.Tracker web = tracker("cobweb_circle");
			check("a finished cooldown is ready, and shown as Ready for a moment", !web.running() && Modules.ITEM_COOLDOWNS.shown().contains(web));
			server(mc, "Supplydrops | Supplydrops will spawn in 4:59!");
			server(mc, "Reboot | The server is rebooting in 30 seconds");
			mc.gui.setScreen(new LeoneScreen());
		});
		at(800, "servers: elytrabox page", mc -> {
			var list = Modules.TIMERS.list();
			check("supply drops and reboots count down", list.stream().anyMatch(c -> c.kind == Timers.Kind.SUPPLY)
				&& list.stream().anyMatch(c -> c.kind == Timers.Kind.RESTART && c.title.equals("Server reboot")));
			click(mc, screen(mc).debugSegment(Category.shown().size() - 1), 0);
		});
		shot(1000, "54-category-elytrabox");
		at(100, "servers: switch", mc -> {
			mc.gui.setScreen(null);
			Category.pretendServer = "Lifesteal";
			server(mc, "Supplydrops | Someone has collected a supplydrop! (3 remaining)");
		});
		at(300, "servers: lifesteal", mc -> {
			check("on another server ElytraBox's modules stop, but stay switched on for later", !Modules.ITEM_COOLDOWNS.active() && Modules.ITEM_COOLDOWNS.enabled());
			check("supply drops go once they begin", Modules.TIMERS.list().stream().noneMatch(c -> c.kind == Timers.Kind.SUPPLY));
			mc.gui.setScreen(new LeoneScreen());
		});
		at(800, "servers: lifesteal page", mc -> click(mc, screen(mc).debugSegment(Category.shown().size() - 1), 0));
		shot(1000, "55-category-empty");
		at(100, "servers: done", mc -> {
			mc.gui.setScreen(null);
			Modules.ITEM_COOLDOWNS.clearAll();
			Modules.ITEM_COOLDOWNS.reset();
			Category.pretendServer = null;
			Modules.ALL_SERVERS.setEnabled(false);
			hotbar(mc, NORMAL_HOTBAR);
		});
	}

	/** LeoneMC is recognised by any address: plainly LeoneMC's ones at once, others by its signs, then remembered. */
	/**
	 * Pack Prefetch: the network pack was fetched at startup, and the game's own pack download takes a
	 * prefetched copy with the right hash instead of going to the network (the address here goes nowhere).
	 */
	private static void packPrefetchChecks() {
		at(200, "pack prefetch", mc -> {
			String network = Modules.PACK_PREFETCH.debugCopyOf(PackPrefetch.networkPack());
			check("the network pack was prefetched at startup (" + network + ")",
				network != null && java.nio.file.Files.isRegularFile(PackPrefetch.dir().resolve(network + ".zip")));
			try {
				byte[] content = ("Leone autotest pack " + System.nanoTime()).getBytes(java.nio.charset.StandardCharsets.UTF_8);
				com.google.common.hash.HashCode hash = com.google.common.hash.Hashing.sha1().hashBytes(content);
				java.nio.file.Path ours = PackPrefetch.dir().resolve(hash + ".zip");
				java.nio.file.Files.createDirectories(ours.getParent());
				java.nio.file.Files.write(ours, content);
				java.nio.file.Path target = mc.gameDirectory.toPath().resolve("downloads").resolve("leone-autotest").resolve(java.util.UUID.randomUUID().toString());
				java.nio.file.Files.createDirectories(target);
				java.nio.file.Path old = target.resolve("0123456789abcdef0123456789abcdef01234567");
				java.nio.file.Files.write(old, new byte[] {1});
				java.net.URL nowhere = java.net.URI.create("http://127.0.0.1:9/leone-autotest.zip").toURL();
				net.minecraft.util.HttpUtil.DownloadProgressListener quiet = new net.minecraft.util.HttpUtil.DownloadProgressListener() {
					@Override
					public void requestStart() {
					}

					@Override
					public void downloadStart(java.util.OptionalLong size) {
					}

					@Override
					public void downloadedBytes(long bytes) {
					}

					@Override
					public void requestFinished(boolean success) {
					}
				};
				java.nio.file.Path got = null;
				try {
					got = net.minecraft.util.HttpUtil.downloadFile(target, nowhere, java.util.Map.of(), com.google.common.hash.Hashing.sha1(), hash,
						1_000_000, java.net.Proxy.NO_PROXY, quiet);
				} catch (Exception e) {
					LOGGER.info("Leone autotest: prefetched download threw {}", e.toString());
				}
				check("the game takes the prefetched copy instead of downloading it",
					got != null && java.util.Arrays.equals(java.nio.file.Files.readAllBytes(got), content));
				check("and the older version beside it is tidied away", !java.nio.file.Files.exists(old));
				check("its card counts what was saved (" + Modules.PACK_PREFETCH.status() + ")",
					Modules.PACK_PREFETCH.status() != null && Modules.PACK_PREFETCH.status().startsWith("Saved"));
				com.google.common.hash.HashCode unknown = com.google.common.hash.Hashing.sha1().hashBytes(new byte[] {7, 7, 7});
				boolean downloaded;
				try {
					net.minecraft.util.HttpUtil.downloadFile(target, nowhere, java.util.Map.of(), com.google.common.hash.Hashing.sha1(), unknown,
						1_000_000, java.net.Proxy.NO_PROXY, quiet);
					downloaded = true;
				} catch (Exception e) {
					downloaded = false;
				}
				check("a pack with no copy here is left to the game's own download", !downloaded && !java.nio.file.Files.exists(target.resolve(unknown.toString())));
				var entry = new net.minecraft.client.multiplayer.ServerData("LeoneMC", "leonemc.net", net.minecraft.client.multiplayer.ServerData.Type.OTHER);
				entry.setResourcePackStatus(net.minecraft.client.multiplayer.ServerData.ServerPackStatus.DISABLED);
				Modules.PACK_PREFETCH.debugEntry(entry);
				check("with LeoneMC's packs switched off, the card says so as a warning (" + Modules.PACK_PREFETCH.status() + ")",
					"Server packs are off for LeoneMC".equals(Modules.PACK_PREFETCH.status()) && Modules.PACK_PREFETCH.statusWarns());
				check("and chat explains how to switch them on", Modules.PACK_PREFETCH.warnIfPacksOff());
				entry.setResourcePackStatus(net.minecraft.client.multiplayer.ServerData.ServerPackStatus.ENABLED);
				check("switching them on in the server list clears the warning at once",
					!Modules.PACK_PREFETCH.statusWarns() && !Modules.PACK_PREFETCH.warnIfPacksOff());
				entry.setResourcePackStatus(net.minecraft.client.multiplayer.ServerData.ServerPackStatus.PROMPT);
				check("asking first (Prompt) is not a warning", !Modules.PACK_PREFETCH.statusWarns());
				// left switched off for the screenshot of the card
				entry.setResourcePackStatus(net.minecraft.client.multiplayer.ServerData.ServerPackStatus.DISABLED);
				java.nio.file.Files.deleteIfExists(ours);
			} catch (Exception e) {
				LOGGER.error("Leone autotest CHECK FAIL: pack prefetch threw", e);
			}
		});
		at(100, "pack prefetch: open the menu", mc -> mc.gui.setScreen(new LeoneScreen()));
		at(600, "pack prefetch: server", mc -> click(mc, screen(mc).debugSegment(4), 0));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(1100, "73-pack-prefetch-packs-off");
		at(100, "pack prefetch: close", mc -> {
			Modules.PACK_PREFETCH.debugEntry(null);
			mc.gui.setScreen(null);
		});
		java.util.concurrent.atomic.AtomicInteger applied = new java.util.concurrent.atomic.AtomicInteger();
		at(100, "pack prefetch: reloads", mc -> {
			java.nio.file.Path a = java.nio.file.Path.of("network-a"), b = java.nio.file.Path.of("ads-b"), a2 = java.nio.file.Path.of("network-a2");
			java.util.function.Function<java.util.List<java.nio.file.Path>, net.minecraft.client.resources.server.PackReloadConfig.Callbacks> asked =
				paths -> new net.minecraft.client.resources.server.PackReloadConfig.Callbacks() {
					@Override
					public void onSuccess() {
						applied.incrementAndGet();
					}

					@Override
					public void onFailure(boolean isRecovery) {
					}

					@Override
					public java.util.List<net.minecraft.client.resources.server.PackReloadConfig.IdAndPath> packsToLoad() {
						return paths.stream().map(p -> new net.minecraft.client.resources.server.PackReloadConfig.IdAndPath(java.util.UUID.randomUUID(), p)).toList();
					}
				};
			var pp = Modules.PACK_PREFETCH;
			pp.debugLeone(true, true);
			boolean first = pp.skipReload(asked.apply(java.util.List.of(a))) || pp.skipReload(asked.apply(java.util.List.of(a, b)));
			check("joining LeoneMC the first time loads its packs as usual", !first);
			boolean leave = pp.skipReload(asked.apply(java.util.List.of()));
			check("leaving LeoneMC keeps its packs on, with no reload", leave && pp.lingering());
			boolean back = pp.skipReload(asked.apply(java.util.List.of(a))) && pp.skipReload(asked.apply(java.util.List.of(a, b)));
			check("coming back to the same packs skips both reloads (" + pp.status() + ")", back && !pp.lingering() && pp.status().contains("2 reloads"));
			check("an updated pack still reloads", !pp.skipReload(asked.apply(java.util.List.of(a2, b))));
			pp.debugLeone(false, false);
			check("packs another server asks for are never skipped", !pp.skipReload(asked.apply(java.util.List.of(a2, b))));
			check("and leaving that server takes them off as usual", !pp.skipReload(asked.apply(java.util.List.of())));
			pp.keepLoaded.value = false;
			pp.debugLeone(true, true);
			pp.skipReload(asked.apply(java.util.List.of(a)));
			check("with Keep Loaded off, leaving LeoneMC takes its packs off", !pp.skipReload(asked.apply(java.util.List.of())));
			pp.keepLoaded.value = true;
			pp.debugLeone(true, true);
			pp.skipReload(asked.apply(java.util.List.of(a)));
			pp.skipReload(asked.apply(java.util.List.of()));
			pp.debugLeone(false, false);
			pp.debugJoinedElsewhere(mc);
			check("joining another server or a world takes kept packs off", !pp.lingering());
			pp.debugLeone(null, true);
		});
		at(4000, "pack prefetch: applied", mc -> check("each skipped reload still tells the game the packs applied (" + applied.get() + ")", applied.get() == 4));
	}

	private static void networkChecks() {
		at(200, "network: addresses", mc -> {
			check("any address with leonemc in it is LeoneMC", LeoneMC.isLeoneAddress("play.leonemc.gg") && LeoneMC.isLeoneAddress("EU.LeoneMC.net:25565")
				&& LeoneMC.isLeoneAddress("leonemc.minehut.gg."));
			check("other servers, Minehut ones included, are not LeoneMC by address", !LeoneMC.isLeoneAddress("hypixel.net") && !LeoneMC.isLeoneAddress("someserver.minehut.gg"));
			LeoneMC.debugJoin("leone-alias.example");
			check("an unknown address is watched, not assumed", !LeoneMC.connected() && LeoneMC.debugProbing());
			// a hub's sidebar: a title, and the address on the last line
			command(mc, "scoreboard objectives add hub dummy \"Lobby\"");
			command(mc, "scoreboard objectives setdisplay sidebar hub");
			command(mc, "scoreboard players set site hub 0");
			command(mc, "scoreboard players display name site hub \"play.leonemc.net\"");
		});
		at(2500, "network: sidebar sign", mc -> {
			check("LeoneMC's sidebar is recognised at an unknown address", LeoneMC.connected() && !LeoneMC.debugProbing());
			check("and the address is remembered for next time", LeoneMC.isKnownAddress("leone-alias.example"));
			LeoneMC.debugForget("leone-alias.example");
			command(mc, "scoreboard objectives remove hub");
			LeoneMC.debugJoin("leone-alias-two.example");
			server(mc, " \nDiscord\n \n| Join our discord server for announcements\n \n→ discord.gg/leonemc\n ");
		});
		at(300, "network: announcement sign", mc -> {
			check("one of LeoneMC's announcement boxes is recognised too", LeoneMC.connected());
			LeoneMC.debugForget("leone-alias-two.example");
			LeoneMC.debugJoin("other-server.example");
			server(mc, "Gold Player_Two » come to play.leonemc.net");
		});
		at(2500, "network: player chat", mc -> {
			check("a player naming LeoneMC in chat does not make a server LeoneMC", !LeoneMC.connected());
			LeoneMC.debugForget("other-server.example");
			check("forgotten addresses are forgotten", !LeoneMC.isKnownAddress("leone-alias.example"));
		});
	}

	/** Sorting real LeoneMC lines into tabs, switching tabs, unread counts, replies following you, and the typing note. */
	private static void chatTabsChecks() {
		at(200, "tabs: sorting", mc -> {
			ChatTabs t = Modules.CHAT_TABS;
			String me = mc.getUser().getName();
			check("a private message to you is a private message, and for you", has(t.kinds("(From Player_Two): are you free?", false), ChatTabs.PRIVATE | ChatTabs.MENTION));
			check("your own private message is yours", has(t.kinds("(To Player_Two): give me a minute", false), ChatTabs.PRIVATE | ChatTabs.OWN));
			check("public chat is player chat", t.kinds("Gold Player_Two [9] » anyone selling keys", false) == ChatTabs.PLAYER);
			check("a player saying your name mentions you", has(t.kinds("Gold Player_Two » gg " + me, false), ChatTabs.PLAYER | ChatTabs.MENTION));
			int own = t.kinds("Owner " + me + " » hello " + me, false);
			check("your own chat is yours, never a mention", has(own, ChatTabs.PLAYER | ChatTabs.OWN) && !has(own, ChatTabs.MENTION));
			check("staff chat is staff chat", has(t.kinds("[Staff] (ElytraBox) Helper_One: anyone free?", false), ChatTabs.STAFF));
			check("the staff chat toggle belongs with staff chat", t.kinds("✔ You are now talking in staff chat.", false) == ChatTabs.STAFF);
			check("anticheat alerts and reports are alerts", t.kinds("Anticheat > Cheater flagged Reach A (13.0x) (ElytraBox)", false) == ChatTabs.ALERT
				&& t.kinds("[Report] (WildKits) Bad_Guy was reported by Good_Guy for hacking.", false) == ChatTabs.ALERT);
			check("deaths, events and command errors are the server's", t.kinds("☠ Victim_One was slain by Player_Two.", false) == ChatTabs.SERVER
				&& t.kinds("Envoys | An envoy event will start in 4:59!", false) == ChatTabs.SERVER
				&& t.kinds("Error: You already own a plot!", false) == ChatTabs.SERVER);
		});
		at(100, "tabs: on", mc -> {
			Modules.CHAT_TABS.setEnabled(true);
			Modules.CHAT_TABS.tabs.selected.add("Mentions");
			mc.gui.hud.getChat().clearMessages(false);
			server(mc, "Gold Player_Two » anyone selling keys");
			server(mc, "(From Player_Two): are you free?");
			server(mc, "(To Player_Two): give me a minute");
			server(mc, "[Staff] (ElytraBox) Helper_One: anyone free to check WildKits?");
			server(mc, "Anticheat > Cheater flagged Reach A (13.0x) (ElytraBox)");
			server(mc, "Envoys | An envoy event will start in 4:59!");
			Chat.info("Leone Client's own notices show in every tab.");
			Modules.CHAT_TABS.select(ChatTabs.Tab.MESSAGES);
		});
		at(200, "tabs: messages", mc -> {
			List<String> lines = shownTexts(mc);
			check("Messages shows the two private messages and the client notice, nothing else " + lines,
				lines.size() == 3 && lines.stream().allMatch(l -> l.startsWith("(") || l.startsWith("Leone")));
			server(mc, "[Staff] (WildKits) Helper_Two: on it");
			server(mc, "(From Player_Three): tpa?");
		});
		at(200, "tabs: unread", mc -> {
			ChatTabs t = Modules.CHAT_TABS;
			check("staff chat that arrives meanwhile is unread in Staff", t.unread(ChatTabs.Tab.STAFF) == 1);
			check("a message shown in the tab you are on is not unread anywhere", t.unread(ChatTabs.Tab.MENTIONS) == 0);
			mc.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
		});
		shot(700, "50-chat-tabs-messages");
		at(100, "tabs: reply", mc -> mc.getConnection().sendCommand("leonetestnothing"));
		at(900, "tabs: reply shown", mc -> {
			List<String> lines = shownTexts(mc);
			check("the reply to a command shows in the tab you are on " + lines, lines.stream().anyMatch(l -> l.toLowerCase(Locale.ROOT).contains("unknown")));
			server(mc, "✔ You are now talking in staff chat.");
			Modules.CHAT_TABS.select(ChatTabs.Tab.STAFF);
		});
		at(200, "tabs: staff", mc -> {
			check("the staff chat toggle is noticed", StaffState.talkingInStaffChat());
			check("Staff shows staff chat and the toggle " + shownTexts(mc), shownTexts(mc).stream().filter(l -> !l.startsWith("Leone")).allMatch(l -> l.startsWith("[Staff]") || l.contains("staff chat")));
		});
		shot(600, "51-chat-tabs-staff");
		at(100, "tabs: close", mc -> {
			server(mc, "❌ You are no longer talking in staff chat.");
			mc.gui.setScreen(null);
		});
		at(300, "lines: hover", mc -> {
			mc.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
			server(mc, "Gold Player_Two [VIP] » meet at koth");
		});
		at(300, "lines: find", mc -> {
			double scale = mc.options.chatScale().get();
			int lineHeight = (int) (9.0 * (mc.options.chatLineSpacing().get() + 1.0));
			float y = (float) ((mc.getWindow().getGuiScaledHeight() - 40) - lineHeight * scale / 2);
			var h = ChatLineActions.at(20, y);
			check("the message under the mouse is found " + (h == null ? null : h.text()), h != null && h.text().equals("Gold Player_Two [VIP] » meet at koth"));
			check("a message's player is its sender, not a rank or tag (" + ChatLineActions.person("Gold Player_Two [VIP] » meet at koth") + ")",
				"Player_Two".equals(ChatLineActions.person("Gold Player_Two [VIP] » meet at koth")));
			check("a private message's player is the other person", "Player_Three".equals(ChatLineActions.person("(From Player_Three): tpa?")));
			ChatLineActions.debugHeld = 2;
			ChatLineActions.debugMouse = new double[] {20, y};
		});
		shot(500, "52-chat-line-alt");
		at(100, "lines: done", mc -> {
			ChatLineActions.debugHeld = 0;
			ChatLineActions.debugMouse = null;
			mc.gui.setScreen(null);
		});
		at(300, "tabs: back to all", mc -> {
			check("the staff chat toggle going off is noticed", !StaffState.talkingInStaffChat());
			check("closing chat goes back to All", Modules.CHAT_TABS.current() == ChatTabs.Tab.ALL);
			check("All shows everything again (" + shownTexts(mc).size() + " messages)", shownTexts(mc).size() >= 11);
			Modules.CHAT_TABS.reset();
		});
	}

	private static List<String> widgetTexts(Minecraft mc) {
		List<String> out = new ArrayList<>();
		if (mc.gui.screen() == null) return out;
		for (var w : net.fabricmc.fabric.api.client.screen.v1.Screens.getWidgets(mc.gui.screen())) out.add(w.getMessage().getString());
		return out;
	}

	/** A module key tapped faster than a tick still counts, once per tap. */
	private static void keybindChecks() {
		at(200, "keybind: quick tap", mc -> {
			mc.gui.setScreen(null);
			Modules.MENTIONS.bind = GLFW.GLFW_KEY_KP_9;
			tapWasOn = Modules.MENTIONS.enabled();
			tap(mc, GLFW.GLFW_KEY_KP_9);
		});
		at(150, "keybind: result", mc -> {
			check("a quick tap of a module's key switches it", Modules.MENTIONS.enabled() != tapWasOn);
			tap(mc, GLFW.GLFW_KEY_KP_9);
		});
		at(150, "keybind: back", mc -> {
			check("a second tap switches it back", Modules.MENTIONS.enabled() == tapWasOn);
			// Q is Minecraft's drop key: the settings say so
			Modules.MENTIONS.bind = GLFW.GLFW_KEY_Q;
			mc.gui.setScreen(new LeoneScreen());
		});
		at(700, "keybind: chat", mc -> click(mc, screen(mc).debugSegment(1), 0));
		at(900, "keybind: settings", mc -> screen(mc).debugSettings(Modules.MENTIONS));
		at(900, "keybind: hover warning", mc -> move(mc, screen(mc).debugPanel(110, 160)));
		shot(900, "49-key-clash");
		at(100, "keybind: clear", mc -> {
			Modules.MENTIONS.bind = -1;
			mc.gui.setScreen(null);
		});
	}

	/** Presses and releases a key through Minecraft's own keyboard handler, both within one tick. */
	private static void tap(Minecraft mc, int key) {
		try {
			var press = net.minecraft.client.KeyboardHandler.class.getDeclaredMethod("keyPress", long.class, int.class, KeyEvent.class);
			press.setAccessible(true);
			press.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_PRESS, new KeyEvent(key, 0, 0));
			press.invoke(mc.keyboardHandler, mc.getWindow().handle(), GLFW.GLFW_RELEASE, new KeyEvent(key, 0, 0));
		} catch (ReflectiveOperationException e) {
			check("tapping a key through the keyboard handler", false);
		}
	}

	private static void eventAndCleanerChecks() {
		at(200, "events: sidebar countdown", mc -> {
			command(mc, "scoreboard objectives add leone dummy \"Elytra Box\"");
			command(mc, "scoreboard objectives setdisplay sidebar leone");
			command(mc, "scoreboard players set koth leone 3");
			command(mc, "scoreboard players display name koth leone \"Koth in: 00:34:04\"");
			command(mc, "scoreboard players set keyall leone 2");
			command(mc, "scoreboard players display name keyall leone \"Key All: 00:00:50\"");
		});
		at(2600, "events: read", mc -> {
			var list = Modules.TIMERS.list();
			boolean koth = list.stream().anyMatch(c -> c.title.equals("KOTH") && Math.abs(c.endsAt - System.currentTimeMillis() - 34 * 60_000 - 4000) < 5000);
			check("the sidebar's KOTH countdown is followed", koth);
			check("the sidebar's Key All countdown is followed", list.stream().anyMatch(c -> c.title.equals("Key All")));
			server(mc, "Envoys | An envoy event will start in 4:59!");
			server(mc, "TARGET! Some_Player is now the target! Eliminate them to win +250.0 Tokens!");
		});
		at(200, "events: chat", mc -> {
			var list = Modules.TIMERS.list();
			check("an announced envoy event counts down", list.stream().anyMatch(c -> c.kind == dev.alfxyz.leoneclient.features.Timers.Kind.ENVOY));
			check("the Target is shown first, live", !list.isEmpty() && list.getFirst().kind == dev.alfxyz.leoneclient.features.Timers.Kind.TARGET && list.getFirst().live);
		});
		shot(300, "45-timers-events");
		at(100, "events: target down", mc -> {
			server(mc, "TARGET! Some_Player was eliminated by Other_Player, earning them +250.0 Tokens!");
			server(mc, "Envoys | An envoy event has begun! 12 envoys have spawned around KOTH! (/warp koth)");
		});
		at(200, "events: cleared", mc -> {
			var list = Modules.TIMERS.list();
			check("the Target goes once eliminated", list.stream().noneMatch(c -> c.kind == dev.alfxyz.leoneclient.features.Timers.Kind.TARGET));
			check("the envoy countdown goes once it begins", list.stream().noneMatch(c -> c.kind == dev.alfxyz.leoneclient.features.Timers.Kind.ENVOY));
			command(mc, "scoreboard objectives remove leone");
		});
		at(100, "cleaner: broadcasts", mc -> {
			String me = mc.getUser().getName();
			server(mc, "Crates | Other_Player has opened a KOTH Crate and won a Eagle Key.");
			server(mc, "Crates | You received a Feather from Bluebird Key.");
			server(mc, "Voting | Other_Player has voted using /vote and received a vote key for doing so!");
			server(mc, "▶ Other_Player has won a coinflip worth 60,000 against Third_Player.");
			server(mc, "▶ " + me + " has won a coinflip worth 20,000 against Other_Player.");
			server(mc, "Discord\n \n| Join our discord server for announcements\n| and much more!");
			for (int i = 0; i < 3; i++) server(mc, "The arena is closing soon");
		});
		at(200, "cleaner: result", mc -> {
			String me = mc.getUser().getName();
			check("other players' crate openings are hidden", chatWith(mc, "Other_Player has opened a KOTH Crate").isEmpty());
			check("your own crate reward still shows", !chatWith(mc, "You received a Feather").isEmpty());
			check("votes and announcement boxes are hidden", chatWith(mc, "has voted using").isEmpty() && chatWith(mc, "Join our discord").isEmpty());
			check("a coinflip you won still shows, others' do not", !chatWith(mc, me + " has won a coinflip").isEmpty() && chatWith(mc, "Other_Player has won a coinflip").isEmpty());
			List<String> arena = chatWith(mc, "The arena is closing soon");
			check("three identical lines become one with [x3] " + arena, arena.size() == 1 && arena.getFirst().endsWith("[x3]"));
			// the Staff page lists who was flagged and the reports, with their buttons
			server(mc, "Anticheat > Cheater flagged Reach A (14.0x) (ElytraBox)");
			server(mc, "[Report] (WildKits) Bad_Guy was reported by Good_Guy for hacking.");
			mc.gui.setScreen(new LeoneScreen());
		});
		at(700, "staff page", mc -> click(mc, screen(mc).debugSegment(6), 0));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(1200, "47-staff-recent");
		at(100, "close staff page", mc -> mc.gui.setScreen(null));
	}

	private static void buildScript() {
		t = 0;
		at(0, "look", mc -> {
			mc.player.setYRot(135);
			mc.player.setXRot(8);
		});
		at(10, "fps", mc -> LOGGER.info("Leone autotest: baseline fps (menu closed) = {}", mc.getFps()));

		// ---- phase 1: every module off. Nothing may break, and every line must pass through untouched.
		at(100, "all modules off", mc -> {
			for (var m : Modules.all()) m.setEnabled(false);
			LOGGER.info("Leone autotest: modules on = {}", Modules.all().stream().filter(m -> m.toggleable() && m.enabled()).count());
		});
		at(200, "off: chat lines", DevAutomation::chatLines);
		at(300, "off: hud and commands", DevAutomation::hudAndCommands);
		at(800, "off: survived", mc -> LOGGER.info("Leone autotest: phase 1 finished, screen = {}", mc.gui.screen()));
		shot(100, "05-all-off-chat");

		// ---- phase 2: everything on
		at(200, "all modules on", mc -> {
			for (var m : Modules.all()) m.setEnabled(true);
			Modules.STAFF_CHAT.setEnabled(false);
			Hud.byId("module_list").setEnabled(false);
			Hud.byId("server").setEnabled(true);
			// the developer's own account, looked up by its Minecraft name (leonemc.net knows it by another)
			Friends.chooseAccount("Alfxz", err -> LOGGER.warn("Leone autotest: friends lookup failed: {}", err));
		});
		at(300, "clear chat", mc -> mc.gui.hud.getChat().clearMessages(false));

		// ---- menu
		at(500, "open via key binding", mc -> KeyMapping.click(RIGHT_SHIFT));
		at(300, "check menu", mc -> {
			LOGGER.info("Leone autotest: after the key, screen = {}, open key = {}", mc.gui.screen(), LeoneClient.openKey.saveString());
			if (screen(mc) == null) mc.gui.setScreen(new LeoneScreen());
		});
		at(100, "park mouse", mc -> move(mc, screen(mc).designToGui(1400, 120)));
		shot(1300, "10-wheel-staff");
		String[] cats = {"combat", "chat", "friends", "hud", "server", "client", "staff"};
		for (int i = 0; i < 7; i++) {
			int seg = i;
			at(100, "open " + cats[i], mc -> click(mc, screen(mc).debugSegment(seg), 0));
			at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
			shot(1100, "1" + i + "-category-" + cats[i]);
		}
		at(100, "staff chat settings", mc -> click(mc, screen(mc).debugTile(0), 1));
		shot(800, "20-settings-staff-chat");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(400, "client", mc -> click(mc, screen(mc).debugSegment(5), 0));
		at(900, "log filter settings", mc -> click(mc, screen(mc).debugTile(2), 1));
		shot(800, "21-settings-log-filter");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(400, "combat", mc -> click(mc, screen(mc).debugSegment(0), 0));
		at(900, "combat timer settings", mc -> click(mc, screen(mc).debugTile(0), 1));
		at(500, "hover keybind row", mc -> move(mc, screen(mc).debugPanel(60, 160)));
		shot(900, "22-settings-keybind");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(400, "hub back", mc -> click(mc, screen(mc).debugHub(), 0));
		at(900, "settle", mc -> { });
		// scrolling over the wheel turns it a category at a time
		at(100, "wheel scroll down", mc -> {
			float[] hub = screen(mc).debugHub();
			screen(mc).mouseScrolled(hub[0], hub[1], 0, -1);
		});
		at(150, "wheel scroll down again", mc -> {
			float[] hub = screen(mc).debugHub();
			screen(mc).mouseScrolled(hub[0], hub[1], 0, -1);
		});
		at(150, "wheel scroll up", mc -> {
			check("scrolling down over the wheel opens the next category (" + screen(mc).debugCategory() + ")", screen(mc).debugCategory() == 1);
			float[] hub = screen(mc).debugHub();
			screen(mc).mouseScrolled(hub[0], hub[1], 0, 1);
		});
		at(150, "wheel scrolled", mc -> check("scrolling up turns it back (" + screen(mc).debugCategory() + ")", screen(mc).debugCategory() == 0));
		at(700, "hub back again", mc -> click(mc, screen(mc).debugHub(), 0));
		at(900, "settle", mc -> { });

		// dock
		at(100, "dock friends", mc -> click(mc, screen(mc).debugDock(2), 0));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(4000, "30-friends-presence");
		at(100, "dock players", mc -> click(mc, screen(mc).debugDock(3), 0));
		at(300, "type", mc -> screen(mc).debugType("Alfxz"));
		shot(2000, "31-players-suggestions");
		at(100, "enter", mc -> key(mc, GLFW.GLFW_KEY_ENTER));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(2500, "32-players-profile");
		at(100, "players: second game mode", mc -> click(mc, screen(mc).debugPanel(150, 38 + 18 + 38 + 18 + 112 + 18 + 15), 0));
		shot(900, "32b-players-mode");
		at(100, "dock overlays", mc -> click(mc, screen(mc).debugDock(5), 0));
		shot(900, "33-overlays");
		at(100, "fps settings", mc -> screen(mc).debugOverlaySettings("fps"));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(900, "33b-settings-fps");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(300, "staff status settings", mc -> screen(mc).debugOverlaySettings("staff_status"));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(900, "33c-settings-staff-status");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(100, "modify hud", mc -> click(mc, screen(mc).debugDock(6), 0));
		at(1200, "resize watermark by scrolling", mc -> {
			if (mc.gui.screen() instanceof HudEditorScreen e) {
				float s = Hud.scale();
				e.mouseMoved(20 * s, 20 * s);
				for (int i = 0; i < 5; i++) e.mouseScrolled(20 * s, 20 * s, 0, 1);
				LOGGER.info("Leone autotest: watermark scale = {}", Hud.byId("watermark").scale);
			}
		});
		shot(500, "34-hud-editor-resized");
		at(100, "done", mc -> {
			if (mc.gui.screen() instanceof HudEditorScreen e) e.onClose();
		});
		shot(40, "35a-back-from-editor-start");
		shot(120, "35b-back-from-editor-middle");
		shot(400, "35c-back-from-editor-end");
		at(940, "close", mc -> key(mc, GLFW.GLFW_KEY_ESCAPE));
		at(600, "check closed", mc -> LOGGER.info("Leone autotest: screen after escape = {}", mc.gui.screen()));

		// ---- features with everything on
		at(200, "on: chat lines", DevAutomation::chatLines);
		at(100, "on: repeat counted", mc -> {
			var messages = ((dev.alfxyz.leoneclient.mixin.ChatHistoryAccessor) mc.gui.hud.getChat()).leone$allMessages();
			List<String> reach = new ArrayList<>();
			for (var m : messages) {
				Component c = m.content() instanceof dev.alfxyz.leoneclient.staffchat.StaffPlaceholder p ? p.real() : m.content();
				if (c.getString().contains("Cheater flagged Reach A")) reach.add(c.getString());
			}
			check("a repeated alert becomes one line with a count " + reach, reach.size() == 1 && reach.getFirst().endsWith("[x2]"));
		});
		at(200, "on: hud and commands", DevAutomation::hudAndCommands);
		for (int i = 0; i < 10; i++) {
			float secs = 14.0f - i * 0.25f;
			at(250, "combat bar " + secs, mc -> mc.gui.hud.setOverlayMessage(Component.literal("§cCombat Tag §7| §f" + String.format(Locale.ROOT, "%.1f", secs)), false));
		}
		at(10, "combat state", mc -> LOGGER.info("Leone autotest: tagged={} remaining={} effect={}", ActionBars.tagged(),
			ActionBars.remainingSeconds(), mc.player.hasEffect(LeoneClientMod.combatTag)));
		shot(100, "40-hud-panels");
		at(100, "chat open", mc -> mc.gui.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE));
		shot(500, "41-chat-on");
		at(100, "chat closed", mc -> mc.gui.setScreen(null));

		// staff chat: hidden from the game frame (the protected window draws it)
		at(200, "staff chat on", mc -> Modules.STAFF_CHAT.setEnabled(true));
		at(400, "staff lines", mc -> {
			server(mc, "[Staff] (ElytraBox) Helper_One: this line should be blank in the screenshot");
			server(mc, "[Report] (Lifesteal) Someone was reported by Other for flying.");
			server(mc, "A normal chat line stays visible");
		});
		at(300, "chat open", mc -> mc.gui.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE));
		shot(700, "42-staff-chat-hidden");
		at(10, "staff chat state", mc -> LOGGER.info("Leone autotest: staff chat available={} protected={} hidden={}",
			StaffChat.available(), StaffChat.protectedWindow(), StaffChat.isHidden()));
		at(100, "chat closed", mc -> mc.gui.setScreen(null));

		at(100, "statuses", mc -> {
			for (var m : Modules.all()) if (m.status() != null) LOGGER.info("Leone autotest: status {} = {}", m.name, m.status());
			LOGGER.info("Leone autotest: friends state={} count={} account={}", Friends.state(), Friends.list().size(), Friends.accountName());
			LOGGER.info("Leone autotest: session kills={} deaths={} streak={}", Modules.SESSION_STATS.kills(), Modules.SESSION_STATS.deaths(), Modules.SESSION_STATS.streak());
			LOGGER.info("Leone autotest: timers={}", Modules.TIMERS.list().size());
		});
		staffStateChecks();
		eventAndCleanerChecks();
		keybindChecks();
		reconnectChecks();
		chatTabsChecks();
		networkChecks();
		packPrefetchChecks();
		serverChecks();
		auctionChecks();
		webChecks();
		rocketChecks();
		scoreSightChecks();
		hudTailorChecks();

		// a full atlas is wiped before the next frame, and drawing carries on (heads, icons and text come back)
		at(200, "atlas: fill it", mc -> {
			var atlas = dev.alfxyz.leoneclient.render.Gfx.atlas();
			atlasGenBefore = atlas.generation();
			java.nio.ByteBuffer block = org.lwjgl.system.MemoryUtil.memAlloc(512 * 512 * 4);
			try {
				int n = 0;
				while (atlas.add(512, 512, block) != null && n < 200) n++;
				LOGGER.info("Leone autotest: atlas took {} blocks before it was full", n);
			} finally {
				org.lwjgl.system.MemoryUtil.memFree(block);
			}
		});
		at(300, "atlas: recovered", mc -> {
			var atlas = dev.alfxyz.leoneclient.render.Gfx.atlas();
			java.nio.ByteBuffer one = org.lwjgl.system.MemoryUtil.memAlloc(64 * 64 * 4);
			try {
				check("a full atlas is wiped at the next frame and takes images again", atlas.generation() > atlasGenBefore && atlas.add(64, 64, one) != null);
			} finally {
				org.lwjgl.system.MemoryUtil.memFree(one);
			}
		});

		// ---- README images: the menu as a player without a staff rank sees it, with no friends loaded
		at(200, "docs: daytime", mc -> {
			var server = mc.getSingleplayerServer();
			if (server != null) server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "time set 1000"));
		});
		at(600, "docs: as a player", mc -> {
			System.setProperty("leoneclient.staff", "false");
			Friends.setAccount(null);
			Hud.byId("watermark").setScale(1);
			mc.gui.hud.getChat().clearMessages(false);
			mc.gui.setScreen(new LeoneScreen());
		});
		at(700, "docs: chat", mc -> click(mc, screen(mc).debugSegment(1), 0));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(1100, "docs-menu");
		at(100, "docs: server", mc -> click(mc, screen(mc).debugSegment(4), 0));
		at(900, "docs: auto join settings", mc -> click(mc, screen(mc).debugTile(0), 1));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(900, "docs-settings");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(400, "hub back", mc -> click(mc, screen(mc).debugHub(), 0));
		at(900, "docs: servers", mc -> click(mc, screen(mc).debugDock(1), 0));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(2500, "docs-servers");
		at(100, "docs: modify hud", mc -> click(mc, screen(mc).debugDock(6), 0));
		at(10, "park", mc -> {
			if (mc.gui.screen() instanceof HudEditorScreen e) e.mouseMoved(800 * Hud.scale(), 600 * Hud.scale());
		});
		shot(1200, "docs-hud");
		at(100, "docs: done", mc -> {
			if (mc.gui.screen() instanceof HudEditorScreen e) e.onClose();
		});
		at(1200, "docs: close", mc -> {
			mc.gui.setScreen(null);
			System.setProperty("leoneclient.staff", "true");
		});

		at(300, "transfer from the network thread", mc -> {
			Thread t = new Thread(() -> {
				try {
					mc.getConnection().handleTransfer(new net.minecraft.network.protocol.common.ClientboundTransferPacket("localhost", 1));
					LOGGER.info("Leone autotest: transfer handled from {} without error", Thread.currentThread().getName());
				} catch (Throwable e) {
					LOGGER.error("Leone autotest: transfer threw", e);
				}
			}, "Leone autotest transfer");
			t.start();
		});
		at(1500, "after transfer", mc -> LOGGER.info("Leone autotest: screen after transfer = {}", mc.gui.screen()));
		shot(100, "50-after-transfer");
		at(1500, "done", mc -> LOGGER.info("Leone autotest: done, screenshots in {}", shots.toAbsolutePath()));
		if (!Boolean.getBoolean("leoneclient.autotest.stay")) at(1500, "quit", Minecraft::stop);
	}

	private static void capture(Minecraft mc, String name) {
		try {
			Files.createDirectories(shots);
		} catch (Exception e) {
			LOGGER.error("Leone autotest: cannot create {}", shots, e);
			return;
		}
		Path file = shots.resolve(name + ".png");
		Screenshot.takeScreenshot(mc.gameRenderer.mainRenderTarget(), (NativeImage img) -> {
			try (img) {
				img.writeToFile(file);
			} catch (Exception e) {
				LOGGER.error("Leone autotest: cannot write {}", file, e);
			}
		});
	}
}
