package dev.alfxyz.leoneclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.hud.WebEscapeNote;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.ui.ChatLineActions;
import dev.alfxyz.leoneclient.ui.ChatTabBar;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.ui.LeoneScreen;
import com.mojang.brigadier.arguments.StringArgumentType;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import dev.alfxyz.leoneclient.staffchat.StaffState;
import dev.alfxyz.leoneclient.web.Account;
import dev.alfxyz.leoneclient.web.Friends;
import dev.alfxyz.leoneclient.web.LeoneWeb;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

public class LeoneClient implements ClientModInitializer {
	public static KeyMapping openKey;
	private static boolean sccWarned;

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(LeoneClientMod.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		LogUtils.getLogger().info("Leone Client {} by Alfxyz loaded", version);

		LeoneConfig.load();
		Friends.load();
		Account.load();
		LeoneMC.init();
		Modules.LOG_FILTER.install();
		Modules.ENDER_CHEST_PAGES.registerCommands();
		registerCommands();
		LeoneMC.onFreshJoin(() -> {
			StaffState.onFreshJoin();
			Friends.refresh(false);
			Account.refresh(false);
			// both would hide the same staff chat, so only the separate mod's runs; say so once
			if (!sccWarned && Account.staff() && FabricLoader.getInstance().isModLoaded("staffchatoverlay")) {
				sccWarned = true;
				Chat.info("The separate Staff Chat Overlay mod is installed, so Leone Client's own Staff Chat module stays off. "
					+ "Remove staffchatoverlay from your mods folder to use the built-in one.");
			}
		});
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> Account.refresh(false));
		// every server, including each switch between LeoneMC's servers, starts with a clean staff state
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> StaffState.onJoin());
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> {
			StaffState.onDisconnect();
			StaffChat.onDisconnect();
		});

		KeyMapping.Category category = KeyMapping.Category.register(LeoneClientMod.id("main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leoneclient.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new LeoneScreen());
				else LogUtils.getLogger().debug("Leone Client: menu key pressed over {}", mc.gui.screen());
			}
			pollBinds(mc);
			LeoneMC.tick(mc);
			StaffState.tick(mc);
			for (Module m : Modules.all()) m.tick(mc);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> {
			LeoneConfig.save();
			StaffChat.destroy();
		});
		Hud.register();
		WebEscapeNote.register();
		ChatTabBar.register();
		ChatLineActions.register();
		DevAutomation.init();
	}

	/** /leone opens the menu; /leone profile (name) shows a player's LeoneMC profile in it. */
	private static void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> dispatcher.register(ClientCommands.literal("leone")
			.executes(ctx -> {
				Minecraft mc = Minecraft.getInstance();
				mc.execute(() -> mc.gui.setScreen(new LeoneScreen()));
				return 1;
			})
			// what Leone Client knows about your staff state, and why
			.then(ClientCommands.literal("staff").executes(ctx -> {
				Chat.info(StaffState.describe());
				String chat = Modules.STAFF_CHAT.unavailable();
				if (chat == null) chat = Modules.STAFF_CHAT.active() ? "Staff Chat: " + Modules.STAFF_CHAT.status() + "." : "Staff Chat is off.";
				Chat.info(chat);
				return 1;
			}))
			.then(ClientCommands.literal("profile").executes(ctx -> {
				// no name: whoever you are looking at
				Minecraft.getInstance().execute(dev.alfxyz.leoneclient.features.PlayerLookup::lookUp);
				return 1;
			}).then(ClientCommands.argument("name", StringArgumentType.word()).executes(ctx -> {
				String name = StringArgumentType.getString(ctx, "name");
				LeoneWeb.lookup(name).whenComplete((found, err) -> Minecraft.getInstance().execute(() -> {
					if (err != null) Chat.info("Could not reach leonemc.net.");
					else if (found.isEmpty()) Chat.info("No LeoneMC player called " + name + ".");
					else Minecraft.getInstance().gui.setScreen(LeoneScreen.forPlayer(found.get().uuid(), found.get().name()));
				}));
				return 1;
			})))));
	}

	/** Keys pressed while playing (no screen open) since the last tick, for the module keybinds. */
	private static final IntArrayList pressed = new IntArrayList();

	/** From the keyboard handler, for every key event: notes presses made while playing. */
	public static void keyPressed(long window, int action, int key) {
		Minecraft mc = Minecraft.getInstance();
		if (action != GLFW.GLFW_PRESS || window != mc.getWindow().handle() || mc.gui.screen() != null || mc.player == null) return;
		if (!pressed.contains(key)) pressed.add(key);
	}

	/**
	 * Module keybinds: each press switches the module on or off (or does what the module does with
	 * it). Presses are caught as they happen rather than by checking the key every tick, so a quick
	 * tap is never missed.
	 */
	private static void pollBinds(Minecraft mc) {
		if (pressed.isEmpty()) return;
		int[] keys = pressed.toIntArray();
		pressed.clear();
		// whether you were playing was decided when the key went down; a menu opening since does not undo the press
		if (mc.player == null) return;
		for (Module m : Modules.all()) {
			if (m.bind < 0 || !m.toggleable() || !contains(keys, m.bind)) continue;
			boolean before = m.enabled();
			m.onBindPressed();
			if (m.enabled() != before) {
				LeoneConfig.save();
				if (Modules.INTERFACE.bindNotices.get()) {
					Notices.push(m.name, m.enabled() ? "Turned on" : "Turned off", m.enabled() ? Colors.ACCENT_RGB : 0x8E8E94, m.icon);
				}
			}
		}
	}

	private static boolean contains(int[] keys, int key) {
		for (int k : keys) if (k == key) return true;
		return false;
	}
}
