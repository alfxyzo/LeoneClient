package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.hud.Notices;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.Colors;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/** Sends you to a server of your choice when you log in to LeoneMC. */
public final class AutoJoin extends Module {
	public static final String CUSTOM = "Custom";
	public final Setting.Choice server = add(new Setting.Choice("server", "Server", "WHERE",
			List.of("ElytraBox", "WildKits", "CoreRaiding", "InsaneKits", "Lifesteal", "Gens", "Survival", CUSTOM), "ElytraBox"),
		"The server to join after logging in. Pick Custom to type any server name.");
	public final Setting.Text custom = add(new Setting.Text("custom", "Server name", "WHERE", "", "for example practice", 32,
			c -> Character.isLetterOrDigit(c) || c == '-' || c == '_'),
		"The name used with /server.");
	public final Setting.Slider delay = add(new Setting.Slider("delay", "Delay", "TIMING", 0, 5, 0.25f, 0.25f, "%.2f s"),
		"How long to wait in the hub before switching.");
	private @Nullable String pending;
	private long dueAt;

	public AutoJoin() {
		super("auto_join", Category.SERVER, "Auto Join", Icons.LOG_IN, "Takes you straight to your favourite server when you log in to LeoneMC.", false);
		custom.shownWhen(() -> server.is(CUSTOM));
		LeoneMC.onFreshJoin(() -> {
			String target = target();
			if (!active() || target == null) return;
			pending = target;
			dueAt = System.currentTimeMillis() + (long) (delay.get() * 1000);
		});
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	public @Nullable String target() {
		String t = server.is(CUSTOM) ? custom.get().strip() : server.get();
		return t.isEmpty() ? null : t;
	}

	@Override
	public void tick(Minecraft mc) {
		if (pending == null || System.currentTimeMillis() < dueAt) return;
		String target = pending;
		pending = null;
		if (mc.player == null || mc.getConnection() == null) return;
		mc.getConnection().sendCommand("server " + target.toLowerCase(Locale.ROOT));
		Notices.push("Auto Join", "Sending you to " + target, Colors.ACCENT_RGB, Icons.LOG_IN);
	}

	@Override
	public String status() {
		return target();
	}
}
