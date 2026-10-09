package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Lets the LeoneMC-only modules run on any server and in singleplayer. */
public final class AllServers extends Module {
	private static final String NONE = "None";
	public final Setting.Choice server = add(new Setting.Choice("pretend", "Act as if on", "SERVER", List.of(NONE, "ElytraBox", "WildKits",
			"CoreRaiding", "InsaneKits", "Lifesteal", "Gens", "Survival", "MoneyDupe", "KnockbackFFA", "Practice", "Events", "Hub"), NONE),
		"Off LeoneMC, shows that server's category and runs its modules, so you can set them up or try them in singleplayer.");

	public AllServers() {
		super("all_servers", Category.CLIENT, "All Servers", Icons.WORLD,
			"Runs the LeoneMC-only modules everywhere, including singleplayer. Handy for trying them out.", false);
	}

	/** The LeoneMC server to act as if on while off LeoneMC, or null. */
	public @Nullable String pretend() {
		return server.is(NONE) ? null : server.get();
	}
}
