package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;

/** Lets the LeoneMC-only modules run on any server and in singleplayer. */
public final class AllServers extends Module {
	public AllServers() {
		super("all_servers", Category.CLIENT, "All Servers", Icons.WORLD,
			"Runs the LeoneMC-only modules everywhere, including singleplayer. Handy for trying them out.", false);
	}
}
