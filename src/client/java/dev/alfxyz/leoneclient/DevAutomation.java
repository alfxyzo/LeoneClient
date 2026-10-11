package dev.alfxyz.leoneclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.features.ActionBars;
import dev.alfxyz.leoneclient.features.AutoReconnect;
import dev.alfxyz.leoneclient.features.ChatTabs;
import dev.alfxyz.leoneclient.features.ItemCooldowns;
import dev.alfxyz.leoneclient.features.Timers;
import dev.alfxyz.leoneclient.features.RocketCheck;
import dev.alfxyz.leoneclient.features.WebEscape;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.mixin.ChatHistoryAccessor;
import dev.alfxyz.leoneclient.staffchat.StaffPlaceholder;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.HudEditorScreen;
import dev.alfxyz.leoneclient.module.Modules;
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
		serverChecks();
		webChecks();
		rocketChecks();

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
