package dev.alfxyz.leoneclient.features;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.minecraft.client.Minecraft;

/**
 * Turns /ec (number) and /enderchest (number) into /pv (number). While the
 * module is off the commands are not there at all, so they reach the server
 * exactly as typed.
 */
public final class EnderChestPages extends Module {
	public EnderChestPages() {
		super("ender_chest_pages", Category.SERVER, "Ender Chest Pages", Icons.ARCHIVE,
			"Type /ec (number) or /enderchest (number) to open that player vault, the same as /pv (number).", false);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	public void registerCommands() {
		ClientCommandRegistrationCallback.EVENT.register((dispatcher, context) -> {
			for (String literal : new String[] {"ec", "enderchest"}) {
				dispatcher.register(ClientCommands.literal(literal)
					// an unmet requirement makes the command unknown here, so Fabric passes it to the server
					.requires(source -> active())
					.then(ClientCommands.argument("number", IntegerArgumentType.integer(1, 99)).executes(ctx -> {
						Minecraft mc = Minecraft.getInstance();
						if (mc.getConnection() != null) mc.getConnection().sendCommand("pv " + IntegerArgumentType.getInteger(ctx, "number"));
						return 1;
					})));
			}
		});
	}
}
