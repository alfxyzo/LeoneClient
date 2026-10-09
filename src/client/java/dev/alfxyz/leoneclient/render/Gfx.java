package dev.alfxyz.leoneclient.render;

import dev.alfxyz.leoneclient.LeoneClientMod;
import dev.alfxyz.leoneclient.module.Modules;

/** Rendering resources shared by the menu, the HUD and the HUD editor. Created lazily on the render thread. */
public final class Gfx {
	private static Atlas atlas;
	private static TextRenderer text;
	private static Icons icons;
	private static Picture logo;
	private static int frame;

	private Gfx() {
	}

	public static synchronized void ensure() {
		if (atlas == null) {
			atlas = new Atlas();
			text = new TextRenderer(atlas);
			text.load();
			icons = new Icons(atlas);
			logo = new Picture(atlas, LeoneClientMod.id("textures/gui/logo.png"));
		}
		text.setVanilla(Modules.INTERFACE.minecraftFont());
	}

	/** Start of a frame, before anything draws. */
	public static synchronized void startFrame() {
		frame++;
		if (atlas != null) atlas.startFrame();
	}

	/** Counts frames, for telling a size that holds still from one that is animating. */
	public static int frame() {
		return frame;
	}

	public static Atlas atlas() {
		ensure();
		return atlas;
	}

	public static TextRenderer text() {
		ensure();
		return text;
	}

	public static Icons icons() {
		ensure();
		return icons;
	}

	public static Picture logo() {
		ensure();
		return logo;
	}

	public static Canvas newCanvas() {
		return new Canvas(atlas());
	}
}
