package dev.alfxyz.leoneclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.features.ActionBars;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.HudEditorScreen;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.features.ChatHooks;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import dev.alfxyz.leoneclient.staffchat.StaffState;
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
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
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

	private DevAutomation() {
	}

	public static void init() {
		if (!Boolean.getBoolean("leoneclient.autotest")) return;
		shots = Path.of("leone-shots");
		buildScript();
		ClientTickEvents.END_CLIENT_TICK.register(DevAutomation::tick);
	}

	private static void tick(Minecraft mc) {
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
	private static void hotbar(Minecraft mc, String... named) {
		var server = mc.getSingleplayerServer();
		if (server == null) return;
		server.execute(() -> {
			var player = server.getPlayerList().getPlayers().getFirst();
			for (int i = 0; i < 9; i++) {
				net.minecraft.world.item.ItemStack stack = net.minecraft.world.item.ItemStack.EMPTY;
				if (i < named.length && named[i] != null) {
					String[] parts = named[i].split("\\|", 2);
					net.minecraft.world.item.Item item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(
						net.minecraft.resources.Identifier.withDefaultNamespace(parts[0]));
					stack = new net.minecraft.world.item.ItemStack(item);
					if (parts.length > 1) stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(parts[1]));
				}
				player.getInventory().setItem(i, stack);
			}
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
		at(3600, "staff: mod mode off", mc -> {
			server(mc, "You are no longer in mod mode");
			hotbar(mc, NORMAL_HOTBAR);
		});
		at(200, "staff: off by message", mc -> check("mod mode off from the message", StaffState.modMode() == StaffState.ModMode.OFF));
		at(3600, "staff: learned", mc -> {
			check("mod mode's hotbar items were learned", StaffState.itemsLearned());
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
		at(1500, "close", mc -> key(mc, GLFW.GLFW_KEY_ESCAPE));
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
