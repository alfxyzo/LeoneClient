package dev.alfxyz.leoneclient.features;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.Locale;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.ContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import org.jspecify.annotations.Nullable;

/** Adds /ec &lt;page&gt; and /enderchest &lt;page&gt;: opens the ender chest menu and clicks that page. */
public final class EnderChestPages extends Module {
	/** Give up if the menu has not opened by then, so a later ender chest is not clicked by surprise. */
	private static final long TIMEOUT_MS = 5000;
	private @Nullable Integer requested;
	private long requestedAt;
	private int seenTicks;

	public EnderChestPages() {
		super("ender_chest_pages", Category.SERVER, "Ender Chest Pages", Icons.ARCHIVE,
			"Type /ec 3 or /enderchest 3 to open that ender chest page straight away.", true);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	public void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			for (String literal : new String[] {"ec", "enderchest"}) {
				dispatcher.register(ClientCommands.literal(literal)
					.then(ClientCommands.argument("page", IntegerArgumentType.integer(1, 9)).executes(ctx -> {
						open(literal, IntegerArgumentType.getInteger(ctx, "page"));
						return 1;
					})));
			}
		});
	}

	private void open(String literal, int page) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.getConnection() == null) return;
		if (!active()) {
			// not ours to handle: pass the command on unchanged
			mc.getConnection().sendCommand(literal + " " + page);
			return;
		}
		requested = page;
		requestedAt = System.currentTimeMillis();
		seenTicks = 0;
		mc.getConnection().sendCommand("enderchest");
	}

	@Override
	public void tick(Minecraft mc) {
		if (requested == null) return;
		if (System.currentTimeMillis() - requestedAt > TIMEOUT_MS || mc.player == null || mc.gameMode == null) {
			requested = null;
			return;
		}
		if (!(mc.gui.screen() instanceof ContainerScreen screen)) return;
		if (!screen.getTitle().getString().toLowerCase(Locale.ROOT).contains("ender chest")) return;
		// give the server a moment to send the menu's contents
		if (++seenTicks < 2) return;
		int slot = requested - 1;
		requested = null;
		var menu = screen.getMenu();
		if (slot < menu.slots.size()) mc.gameMode.handleContainerInput(menu.containerId, slot, 0, ContainerInput.PICKUP, mc.player);
	}
}
