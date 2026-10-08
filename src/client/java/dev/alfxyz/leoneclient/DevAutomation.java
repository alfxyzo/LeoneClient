package dev.alfxyz.leoneclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.features.ActionBars;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.HudEditorScreen;
import dev.alfxyz.leoneclient.module.Modules;
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
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.worldselection.SelectWorldScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.InteractionHand;
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
		// LeoneMC-only features run in singleplayer, and Auto Join fires when the world loads
		Modules.ALL_SERVERS.setEnabled(true);
		Modules.AUTO_JOIN.setEnabled(true);
		Hud.byId("module_list").setEnabled(true);
		Hud.byId("server").setEnabled(true);
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

	private static void buildScript() {
		t = 0;
		at(0, "look", mc -> {
			mc.player.setYRot(135);
			mc.player.setXRot(8);
		});
		at(10, "fps", mc -> LOGGER.info("Leone autotest: baseline fps (menu closed) = {}", mc.getFps()));
		at(400, "auto join should have sent /server", mc -> LOGGER.info("Leone autotest: auto join target {}", Modules.AUTO_JOIN.target()));
		at(10, "friends of a real account", mc -> Friends.chooseAccount("Decameter", err -> LOGGER.warn("Leone autotest: friends lookup failed: {}", err)));

		// ---- menu
		at(500, "open via key binding", mc -> KeyMapping.click(RIGHT_SHIFT));
		at(10, "park mouse", mc -> move(mc, screen(mc).designToGui(1400, 120)));
		shot(330, "01-open-anim");
		shot(1200, "02-wheel");
		at(10, "hover segment", mc -> move(mc, screen(mc).debugSegment(2)));
		shot(400, "03-wheel-hover");
		String[] cats = {"combat", "chat", "friends", "hud", "server", "client"};
		for (int i = 0; i < 6; i++) {
			int seg = i;
			at(100, "open " + cats[i], mc -> click(mc, screen(mc).debugSegment(seg), 0));
			at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
			shot(1100, "1" + i + "-category-" + cats[i]);
		}
		// settings pages
		at(100, "chat", mc -> click(mc, screen(mc).debugSegment(1), 0));
		at(900, "anti-mute settings", mc -> click(mc, screen(mc).debugTile(0), 1));
		shot(700, "20-settings-anti-mute");
		at(10, "hover a setting for its tooltip", mc -> move(mc, screen(mc).debugPanel(400, 160)));
		shot(900, "21-settings-tooltip");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(400, "server", mc -> click(mc, screen(mc).debugSegment(4), 0));
		at(900, "auto join settings", mc -> click(mc, screen(mc).debugTile(0), 1));
		shot(700, "22-settings-auto-join");
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(400, "client", mc -> click(mc, screen(mc).debugSegment(5), 0));
		at(900, "interface settings", mc -> click(mc, screen(mc).debugTile(0), 0));
		shot(700, "23-settings-interface");
		at(100, "purple theme", mc -> Modules.INTERFACE.theme.set("Purple"));
		shot(400, "24-theme-purple");
		at(100, "pink theme", mc -> Modules.INTERFACE.theme.set("Leone Pink"));
		at(100, "back", mc -> click(mc, screen(mc).debugBackButton(), 0));
		at(400, "hub back", mc -> click(mc, screen(mc).debugHub(), 0));
		at(900, "settle", mc -> { });

		// dock
		at(100, "dock search", mc -> click(mc, screen(mc).debugDock(0), 0));
		at(300, "type", mc -> screen(mc).debugType("friend"));
		shot(700, "30-search");
		at(100, "esc clears", mc -> key(mc, GLFW.GLFW_KEY_ESCAPE));
		at(100, "dock servers", mc -> click(mc, screen(mc).debugDock(1), 0));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(1000, "31-servers");
		at(100, "dock friends", mc -> click(mc, screen(mc).debugDock(2), 0));
		at(10, "park", mc -> move(mc, screen(mc).designToGui(1500, 60)));
		shot(2500, "32-friends");
		at(10, "hover a friend", mc -> move(mc, screen(mc).debugPanel(110, 230)));
		shot(900, "33-friends-hover");
		at(100, "dock configs", mc -> click(mc, screen(mc).debugDock(3), 0));
		shot(900, "34-configs");
		at(100, "dock overlays", mc -> click(mc, screen(mc).debugDock(4), 0));
		shot(900, "35-overlays");
		at(100, "modify hud", mc -> click(mc, screen(mc).debugDock(5), 0));
		shot(1200, "36-hud-editor");
		at(100, "done", mc -> {
			if (mc.gui.screen() instanceof HudEditorScreen e) e.onClose();
		});
		at(1500, "close", mc -> key(mc, GLFW.GLFW_KEY_ESCAPE));
		at(600, "check closed", mc -> LOGGER.info("Leone autotest: screen after escape = {}", mc.gui.screen()));

		// ---- features, with messages as LeoneMC would send them
		at(200, "action bar", mc -> mc.gui.hud.setOverlayMessage(Component.literal("§6Gems: §e1,250 §7| §bKills: 12"), false));
		for (int i = 0; i < 12; i++) {
			float secs = 14.5f - i * 0.25f;
			at(250, "combat bar " + secs, mc -> {
				mc.gui.hud.setOverlayMessage(Component.literal("§cCombat Tag §7| §f" + String.format(Locale.ROOT, "%.1f", secs)), false);
				if (secs > 13) mc.gui.hud.setOverlayMessage(Component.literal("§6Gems: §e1,250 §7| §bKills: 12"), false);
			});
		}
		at(10, "combat state", mc -> LOGGER.info("Leone autotest: tagged={} remaining={} effect={}", ActionBars.tagged(),
			ActionBars.remainingSeconds(), mc.player.hasEffect(LeoneClientMod.combatTag)));
		shot(100, "40-action-and-combat-bars");
		at(100, "pause while tagged", mc -> mc.gui.setScreen(new PauseScreen(true)));
		at(300, "keep tag fresh", mc -> mc.gui.hud.setOverlayMessage(Component.literal("§cCombat Tag §7| §f11.0"), false));
		at(50, "try to disconnect", mc -> mc.disconnectFromWorld(ClientLevel.DEFAULT_QUIT_MESSAGE));
		shot(500, "41-combat-log-guard");
		at(100, "stay", mc -> mc.gui.setScreen(null));
		at(100, "chat lines", mc -> {
			server(mc, "[Alert] We are currently looking for staff members to join our team. [Click to apply]");
			server(mc, "[Alert] Proxy restarting in 30 seconds. You will be disconnected.");
			server(mc, "Friends | Decameter has joined the server WildKits.");
			server(mc, "Friends | AgentAri has left the server ElytraBox.");
			server(mc, "[Owner] Decameter: hey " + mc.getUser().getName() + ", want to duel?");
			server(mc, "Connecting you to ElytraBox.");
		});
		at(300, "anti-mute", mc -> mc.getConnection().sendChat("that was a shit play lol"));
		at(300, "title filter", mc -> {
			mc.gui.hud.setTitle(Component.literal("Resource Pack Declined"));
			mc.gui.hud.setSubtitle(Component.literal("Please accept the resource pack"));
		});
		at(100, "log filter", mc -> LoggerFactory.getLogger("Scoreboard").warn("Requested creation of existing team '{}'", "glow-GREEN"));
		at(100, "ender chest command", mc -> mc.getConnection().sendCommand("ec 3"));
		at(100, "book", mc -> {
			mc.getConnection().sendCommand("give @s written_book[written_book_content={title:\"News\",author:\"LeoneMC\",pages:[\"Welcome\"]}]");
		});
		at(800, "use book", mc -> mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND));
		at(600, "chat open", mc -> mc.gui.openChatScreen(net.minecraft.client.gui.components.ChatComponent.ChatMethod.MESSAGE));
		shot(500, "42-chat");
		at(100, "chat closed", mc -> mc.gui.setScreen(null));
		shot(300, "43-hud");
		at(100, "statuses", mc -> {
			for (var m : Modules.all()) if (m.status() != null) LOGGER.info("Leone autotest: status {} = {}", m.name, m.status());
			LOGGER.info("Leone autotest: friends state={} count={} account={}", Friends.state(), Friends.list().size(), Friends.accountName());
		});
		at(100, "done", mc -> LOGGER.info("Leone autotest: done, screenshots in {}", shots.toAbsolutePath()));
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
