package dev.alfxyz.leoneclient.web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.PlayerInfo;

/**
 * Minecraft usernames by UUID. leonemc.net can show a player under a name LeoneMC gives them rather
 * than their Minecraft one, but commands such as /msg need the Minecraft name.
 */
public final class Names {
	private static final Map<UUID, String> KNOWN = new ConcurrentHashMap<>();

	private Names() {
	}

	/**
	 * The player's Minecraft name: from your own account or this server's player list when they are
	 * there, otherwise from Mojang (once per session). Falls back to {@code shown} if that fails.
	 * Call on the render thread.
	 */
	public static CompletableFuture<String> minecraft(UUID uuid, String shown) {
		Minecraft mc = Minecraft.getInstance();
		if (uuid.equals(mc.getUser().getProfileId())) return CompletableFuture.completedFuture(mc.getUser().getName());
		if (mc.getConnection() != null) {
			PlayerInfo info = mc.getConnection().getPlayerInfo(uuid);
			if (info != null) return CompletableFuture.completedFuture(info.getProfile().name());
		}
		String known = KNOWN.get(uuid);
		if (known != null) return CompletableFuture.completedFuture(known);
		String url = "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.toString().replace("-", "");
		return Http.get(url, "application/json").thenApply(r -> {
			if (r.status() != 200) return shown;
			JsonObject o = JsonParser.parseString(r.text()).getAsJsonObject();
			String name = o.get("name").getAsString();
			KNOWN.put(uuid, name);
			return name;
		}).completeOnTimeout(shown, 4, TimeUnit.SECONDS).exceptionally(err -> shown);
	}

	/** Closes the menu and opens chat with "/msg name " ready to type after. */
	public static void message(UUID uuid, String shown) {
		minecraft(uuid, shown).thenAccept(name -> Minecraft.getInstance().execute(() -> {
			Minecraft mc = Minecraft.getInstance();
			mc.gui.setScreen(null);
			mc.gui.openChatScreen(ChatComponent.ChatMethod.MESSAGE);
			// replace, rather than add to, whatever draft the chat box restored
			if (mc.gui.screen() instanceof ChatScreen chat) chat.insertText("/msg " + name + " ", true);
		}));
	}
}
