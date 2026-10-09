package dev.alfxyz.leoneclient.ui;

import dev.alfxyz.leoneclient.hud.Overlay;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.HashMap;
import java.util.Map;

/**
 * Lets the settings view show a standalone overlay's settings, as it does a module's. It stands in for
 * the overlay only on that page and is never one of the modules, so it is not saved or listed as one.
 */
final class OverlaySettings extends Module {
	private static final Map<Overlay, OverlaySettings> CACHE = new HashMap<>();
	final Overlay overlay;

	private OverlaySettings(Overlay o) {
		super("overlay_" + o.id, Category.HUD, o.name, Icons.OVERLAYS,
			o.description + ". Drag it into place with Modify HUD, and scroll over it there to resize it.", false);
		this.overlay = o;
		settings.addAll(o.settings);
	}

	/** What to open for an overlay: the module it belongs to, or its own settings. */
	static Module of(Overlay o) {
		Module owner = o.owner();
		return owner != null ? owner : CACHE.computeIfAbsent(o, OverlaySettings::new);
	}

	@Override
	public boolean enabled() {
		return overlay.enabled();
	}

	@Override
	public void setEnabled(boolean on) {
		overlay.setEnabled(on);
	}

	@Override
	public void toggle() {
		overlay.setEnabled(!overlay.enabled());
	}

	@Override
	public String breadcrumb() {
		return "Overlays";
	}

	@Override
	public boolean inModuleList() {
		return false;
	}

	@Override
	public boolean hasKeybind() {
		return false;
	}
}
