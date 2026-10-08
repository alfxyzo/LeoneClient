package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.ui.Colors;
import java.util.List;

/** The look and feel of Leone Client's menu and HUD. Always on. */
public final class Interface extends Module {
	private static final List<String> THEMES = List.of("Leone Pink", "Leone Blue", "Purple", "Royal Blue", "Emerald");
	private static final int[] THEME_RGB = {Colors.DEFAULT_ACCENT, 0x3AA6F2, 0xA855F7, 0x3B6CFF, 0x22C38E};

	public final Setting.Choice theme = add(new Setting.Choice("theme", "Accent", "LOOK", THEMES, "Leone Pink"),
		"The colour used for highlights, switches and selections.");
	public final Setting.Choice font = add(new Setting.Choice("font", "Font", "LOOK", List.of("System", "Minecraft"), "System"),
		"System uses Segoe UI where available. Minecraft uses the game's own font.");
	public final Setting.Toggle sounds = add(new Setting.Toggle("sounds", "Sounds", "FEEL", true),
		"A soft click when hovering the wheel.");
	public final Setting.Toggle blur = add(new Setting.Toggle("blur", "Background blur", "FEEL", true),
		"Blurs the game behind the menu. The blur strength comes from the game's own Menu Background Blur option.");
	public final Setting.Toggle bindNotices = add(new Setting.Toggle("bind_notices", "Keybind notifications", "FEEL", true),
		"Shows a notification when a keybind turns a module on or off.");

	public Interface() {
		super("interface", Category.CLIENT, "Interface", Icons.PALETTE, "Colours, font and feel of the menu. The key that opens it (Right Shift at first) is under Leone Client in Controls.", true);
	}

	@Override
	public boolean toggleable() {
		return false;
	}

	/** Applies the chosen accent. Cheap; called every frame. */
	public void apply() {
		int i = THEMES.indexOf(theme.get());
		Colors.setAccent(THEME_RGB[Math.max(0, i)]);
	}

	public boolean minecraftFont() {
		return font.is("Minecraft");
	}
}
