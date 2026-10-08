package dev.alfxyz.leoneclient;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import dev.alfxyz.leoneclient.ui.LeoneScreen;

/** Opens the Leone Client menu from Mod Menu's config button. */
public final class ModMenuIntegration implements ModMenuApi {
	@Override
	public ConfigScreenFactory<?> getModConfigScreenFactory() {
		return LeoneScreen::new;
	}
}
