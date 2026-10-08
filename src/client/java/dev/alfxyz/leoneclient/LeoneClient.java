package dev.alfxyz.leoneclient;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.ui.Colors;
import dev.alfxyz.leoneclient.ui.LeoneScreen;
import dev.alfxyz.leoneclient.web.Friends;
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

	@Override
	public void onInitializeClient() {
		String version = FabricLoader.getInstance().getModContainer(LeoneClientMod.MOD_ID)
			.map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("unknown");
		LogUtils.getLogger().info("Leone Client {} by Alfxyz loaded", version);

		LeoneConfig.load();
		Friends.load();
		LeoneMC.init();
		Modules.LOG_FILTER.install();
		Modules.ENDER_CHEST_PAGES.registerCommands();
		LeoneMC.onFreshJoin(() -> Friends.refresh(false));

		KeyMapping.Category category = KeyMapping.Category.register(LeoneClientMod.id("main"));
		openKey = KeyMappingHelper.registerKeyMapping(new KeyMapping("key.leoneclient.open", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
		ClientTickEvents.END_CLIENT_TICK.register(mc -> {
			while (openKey.consumeClick()) {
				if (mc.gui.screen() == null) mc.gui.setScreen(new LeoneScreen());
			}
			pollBinds(mc);
			for (Module m : Modules.all()) m.tick(mc);
		});
		ClientLifecycleEvents.CLIENT_STOPPING.register(mc -> LeoneConfig.save());
		Hud.register();
		DevAutomation.init();
	}

	/** Module keybinds: Toggle flips the module on each press, Hold keeps it on while the key is down. */
	private static void pollBinds(Minecraft mc) {
		boolean free = mc.gui.screen() == null && mc.player != null;
		for (Module m : Modules.all()) {
			if (m.bind < 0 || !m.toggleable()) {
				m.bindDown = false;
				continue;
			}
			boolean down = free && InputConstants.isKeyDown(mc.getWindow(), m.bind);
			boolean before = m.enabled();
			if (m.bindMode == Module.BindMode.TOGGLE) {
				if (down && !m.bindDown) m.toggle();
			} else if (down != m.bindDown) {
				m.setEnabled(down);
			}
			m.bindDown = down;
			if (m.enabled() != before && Modules.INTERFACE.bindNotices.get()) {
				Notices.push(m.name, m.enabled() ? "Turned on" : "Turned off", m.enabled() ? Colors.ACCENT_RGB : 0x8E8E94, m.icon);
			}
		}
	}
}
