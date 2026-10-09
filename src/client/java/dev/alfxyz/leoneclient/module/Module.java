package dev.alfxyz.leoneclient.module;

import dev.alfxyz.leoneclient.LeoneMC;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;

/** A feature that can be switched on and off, with its own settings. */
public abstract class Module {
	public final String id;
	public final Category category;
	public final String name;
	public final String description;
	public final String icon;
	public final boolean defaultEnabled;
	public final List<Setting> settings = new ArrayList<>();

	private boolean enabled;
	public boolean visible = true;
	/** GLFW key code that toggles the module, or -1 when unbound. */
	public int bind = -1;

	protected Module(String id, Category category, String name, String icon, String description, boolean enabled) {
		this.id = id;
		this.category = category;
		this.name = name;
		this.icon = icon;
		this.description = description;
		this.defaultEnabled = enabled;
		this.enabled = enabled;
	}

	protected <T extends Setting> T add(T setting, String description) {
		setting.description = description;
		settings.add(setting);
		return setting;
	}

	/** False for modules that only hold settings, which are always on. */
	public boolean toggleable() {
		return true;
	}

	/** The small heading above the name on the settings page. */
	public String breadcrumb() {
		return category.displayName;
	}

	/** Whether the module can appear in the Module List overlay (and so offers that switch). */
	public boolean inModuleList() {
		return true;
	}

	/** Whether the module can be given a key. */
	public boolean hasKeybind() {
		return true;
	}

	/** What the module's key does, for the tooltip on its Keybind row. */
	public String bindHint() {
		return "A key that switches " + name + " on or off while you play.";
	}

	/** True when the module only does anything while connected to LeoneMC. */
	public boolean leoneOnly() {
		return false;
	}

	public boolean enabled() {
		return enabled || !toggleable();
	}

	/** Enabled, on LeoneMC if the module needs it, and for staff if it is a Staff module. */
	public boolean active() {
		return enabled() && (!leoneOnly() || LeoneMC.active()) && category.visible();
	}

	/** The module's keybind was pressed. Switches it on or off unless a module does something else. */
	public void onBindPressed() {
		toggle();
	}

	public void setEnabled(boolean on) {
		if (!toggleable() || on == enabled) return;
		enabled = on;
		if (on) onEnable();
		else onDisable();
	}

	public void toggle() {
		setEnabled(!enabled);
	}

	protected void onEnable() {
	}

	protected void onDisable() {
	}

	/** Called every client tick. */
	public void tick(Minecraft mc) {
	}

	/** A short live status shown on the module's card, or null. */
	public String status() {
		return null;
	}

	/** Why the module cannot work here, shown on its card even while it is off, or null. */
	public String unavailable() {
		return null;
	}

	public void reset() {
		setEnabled(defaultEnabled);
		visible = true;
		bind = -1;
		for (Setting s : settings) s.reset();
	}

	public String key() {
		return id;
	}
}
