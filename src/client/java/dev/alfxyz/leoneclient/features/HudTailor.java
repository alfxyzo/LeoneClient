package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.hud.Sidebar;
import dev.alfxyz.leoneclient.hud.VanillaHudEditor;
import dev.alfxyz.leoneclient.mixin.BossOverlayAccessor;
import dev.alfxyz.leoneclient.mixin.HudFieldsAccessor;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * Edits Minecraft's own HUD: move, resize or hide each part of it, trim the scoreboard down to the lines
 * that matter, and show health, hunger and armour as compact numbers.
 */
public final class HudTailor extends Module {
	private static final Identifier HEART = Identifier.withDefaultNamespace("hud/heart/full");
	private static final Identifier FOOD = Identifier.withDefaultNamespace("hud/food_full");
	private static final Identifier ARMOUR = Identifier.withDefaultNamespace("hud/armor_full");
	public static final float MIN_SCALE = 0.5f, MAX_SCALE = 3f;

	/** The parts of Minecraft's HUD that can be edited. */
	public enum Part {
		HOTBAR("hotbar", "Hotbar", VanillaHudElements.HOTBAR),
		HEALTH("health", "Health", VanillaHudElements.HEALTH_BAR),
		ARMOUR("armour", "Armour", VanillaHudElements.ARMOR_BAR),
		HUNGER("hunger", "Hunger", VanillaHudElements.FOOD_BAR),
		AIR("air", "Air", VanillaHudElements.AIR_BAR),
		MOUNT("mount", "Mount health", VanillaHudElements.MOUNT_HEALTH),
		XP_BAR("xp_bar", "Experience bar", VanillaHudElements.INFO_BAR),
		XP_LEVEL("xp_level", "Level", VanillaHudElements.EXPERIENCE_LEVEL),
		ITEM_NAME("item_name", "Item name", VanillaHudElements.HELD_ITEM_TOOLTIP),
		CROSSHAIR("crosshair", "Crosshair", VanillaHudElements.CROSSHAIR),
		EFFECTS("effects", "Effects", VanillaHudElements.MOB_EFFECTS),
		BOSS_BARS("boss_bars", "Boss bars", VanillaHudElements.BOSS_BAR),
		SCOREBOARD("scoreboard", "Scoreboard", VanillaHudElements.SCOREBOARD),
		ACTION_BAR("action_bar", "Action bar", VanillaHudElements.OVERLAY_MESSAGE),
		TITLES("titles", "Titles", VanillaHudElements.TITLE_AND_SUBTITLE),
		TAB_LIST("tab_list", "Tab list", VanillaHudElements.PLAYER_LIST),
		SUBTITLES("subtitles", "Subtitles", VanillaHudElements.SUBTITLES);

		public final String id;
		public final String label;
		final Identifier element;

		Part(String id, String label, Identifier element) {
			this.id = id;
			this.label = label;
			this.element = element;
		}
	}

	/** Where a part has been moved to, in GUI pixels from its usual place, and its size. */
	public static final class Layout {
		public final Setting.Slider x, y, scale;

		Layout(Setting.Slider x, Setting.Slider y, Setting.Slider scale) {
			this.x = x;
			this.y = y;
			this.scale = scale;
		}

		public boolean moved() {
			return x.get() != 0 || y.get() != 0 || scale.get() != 1;
		}

		public void reset() {
			x.reset();
			y.reset();
			scale.reset();
		}
	}

	private static List<String> labels() {
		List<String> out = new ArrayList<>();
		for (Part p : Part.values()) out.add(p.label);
		return out;
	}

	public final Setting.Chips shown = add(new Setting.Chips("shown", "Shown", "ELEMENTS", labels(), labels()),
		"Which parts of the HUD are drawn. Open the editor to move and resize them, or right-click one there to hide it.");

	public final Setting.Toggle hideScores = add(new Setting.Toggle("sb_hide_scores", "Hide numbers", "SCOREBOARD", false),
		"Hides the red numbers down the right side of the scoreboard.");
	public final Setting.Toggle hideTitle = add(new Setting.Toggle("sb_hide_title", "Hide title", "SCOREBOARD", false),
		"Hides the scoreboard's heading, usually the server's name.");
	public final Setting.Toggle hideBlank = add(new Setting.Toggle("sb_hide_blank", "Hide blank lines", "SCOREBOARD", false),
		"Removes the empty spacer lines between sections.");
	public final Setting.Toggle hideAddress = add(new Setting.Toggle("sb_hide_address", "Hide server address", "SCOREBOARD", false),
		"Removes lines that are just a web or server address, such as the one at the bottom.");
	public final Setting.Slider maxLines = add(new Setting.Slider("sb_max_lines", "Most lines", "SCOREBOARD", 1, 15, 1, 15, "%.0f"),
		"The scoreboard never shows more lines than this, counting from the top.");
	public final Setting.Slider background = add(new Setting.Slider("sb_background", "Background", "SCOREBOARD", 0, 100, 5, 30, "%.0f%%"),
		"How dark the box behind the scoreboard is. The heading's box is a little darker.");
	public final Setting.Toggle shadow = add(new Setting.Toggle("sb_shadow", "Text shadow", "SCOREBOARD", false),
		"Draws the scoreboard's text with a shadow, which reads better with no background.");
	public final Setting.Text hideWith = add(new Setting.Text("sb_hide_with", "Hide lines with", "SCOREBOARD", "", "Rank, Discord", 300, ch -> true),
		"Hides every line containing any of these words, separated by commas. Upper or lower case does not matter.");
	public final Setting.Text rename = add(new Setting.Text("sb_rename", "Rename", "SCOREBOARD", "", "Balance > Money; Kills > K", 300, ch -> true),
		"Changes words on the scoreboard: \"old > new\", with a semicolon between each. Applies to the heading too.");
	/** Lines hidden by clicking them in the editor, one per line as "server<TAB>line". */
	public final Setting.Text hiddenLines = (Setting.Text) add(new Setting.Text("sb_hidden_lines", "Hidden lines", "SCOREBOARD", "", "", 20000, ch -> true),
		"").shownWhen(() -> false);

	public final Setting.Toggle slotKeys = add(new Setting.Toggle("slot_keys", "Slot keys", "HOTBAR", false),
		"Shows the key for each hotbar slot in its corner, handy with unusual hotbar keys.");

	public final Setting.Choice healthStyle = add(new Setting.Choice("health_style", "Style", "HEALTH", List.of("Hearts", "Number"), "Hearts"),
		"Number shows one heart and your health as a number, with absorption in gold, instead of rows of hearts.");
	public final Setting.Toggle healthHideFull = add(new Setting.Toggle("health_hide_full", "Hide when full", "HEALTH", false),
		"Hides your health while it is full and you have no absorption.");

	public final Setting.Choice hungerStyle = add(new Setting.Choice("hunger_style", "Style", "HUNGER", List.of("Icons", "Number"), "Icons"),
		"Number shows one drumstick and your hunger as a number.");
	public final Setting.Toggle saturation = (Setting.Toggle) add(new Setting.Toggle("hunger_saturation", "Saturation", "HUNGER", true),
		"Adds your saturation in grey after the number.").shownWhen(() -> hungerStyle.is("Number"));
	public final Setting.Toggle hungerHideFull = add(new Setting.Toggle("hunger_hide_full", "Hide when full", "HUNGER", false),
		"Hides hunger while it is full.");

	public final Setting.Choice armourStyle = add(new Setting.Choice("armour_style", "Style", "ARMOUR", List.of("Icons", "Number"), "Icons"),
		"Number shows one chestplate and your armour points as a number.");

	public final Setting.Toggle tabHeader = add(new Setting.Toggle("tab_header", "Hide header", "TAB LIST", false),
		"Hides the text above the player list.");
	public final Setting.Toggle tabFooter = add(new Setting.Toggle("tab_footer", "Hide footer", "TAB LIST", false),
		"Hides the text below the player list.");

	public final Setting.Chips effects = add(new Setting.Chips("screen_effects", "Hide", "SCREEN EFFECTS",
		List.of("Pumpkin", "Vignette", "Powder snow", "Portal", "Nausea", "Spyglass"), List.of()),
		"Screen-wide effects to stop drawing. Vignette is the dark edge of the screen.");

	private final Map<Part, Layout> layouts = new EnumMap<>(Part.class);
	private String hiddenParsedFrom = "";
	private final List<String> hiddenList = new ArrayList<>();

	public HudTailor() {
		super("hud_tailor", Category.HUD, "HUD Tailor", Icons.PANEL_BOTTOM,
			"Edit Minecraft's own HUD: move, resize or hide the hotbar, health, hunger, armour, scoreboard and more, "
				+ "and hide the scoreboard lines you do not need.", true);
		for (Part p : Part.values()) {
			Setting.Slider x = hidden(new Setting.Slider(p.id + "_x", p.label + " x", "LAYOUT", -4000, 4000, 0, 0, "%.0f"));
			Setting.Slider y = hidden(new Setting.Slider(p.id + "_y", p.label + " y", "LAYOUT", -4000, 4000, 0, 0, "%.0f"));
			Setting.Slider s = hidden(new Setting.Slider(p.id + "_scale", p.label + " size", "LAYOUT", MIN_SCALE, MAX_SCALE, 0.05f, 1, "%.2f"));
			layouts.put(p, new Layout(x, y, s));
		}
	}

	private Setting.Slider hidden(Setting.Slider s) {
		add(s, "").shownWhen(() -> false);
		return s;
	}

	@Override
	public String editorLabel() {
		return "Open editor";
	}

	@Override
	public Screen editor() {
		return new VanillaHudEditor();
	}

	@Override
	public boolean inModuleList() {
		return false;
	}

	@Override
	public @Nullable String status() {
		int hidden = 0, moved = 0;
		for (Part p : Part.values()) {
			if (!shown(p)) hidden++;
			else if (layout(p).moved()) moved++;
		}
		if (hidden == 0 && moved == 0) return null;
		List<String> bits = new ArrayList<>();
		if (moved > 0) bits.add(moved + " moved");
		if (hidden > 0) bits.add(hidden + " hidden");
		return String.join(", ", bits);
	}

	/** Wraps each part of Minecraft's HUD so it can be moved, resized or hidden. */
	public void register() {
		for (Part p : Part.values()) HudElementRegistry.replaceElement(p.element, vanilla -> (g, dt) -> draw(p, vanilla, g, dt));
	}

	public Layout layout(Part p) {
		return layouts.get(p);
	}

	public boolean shown(Part p) {
		return shown.has(p.label);
	}

	public void setShown(Part p, boolean on) {
		if (shown(p) != on) shown.flip(p.label);
	}

	/** Whether a screen effect (one of {@link #effects}' options) is hidden. */
	public boolean hides(String effect) {
		return active() && effects.has(effect);
	}

	// ------------------------------------------------------------------- drawing

	private void draw(Part p, HudElement vanilla, GuiGraphicsExtractor g, DeltaTracker dt) {
		if (!active()) {
			vanilla.extractRenderState(g, dt);
			return;
		}
		if (!shown(p)) return;
		Minecraft mc = Minecraft.getInstance();
		Player player = mc.getCameraEntity() instanceof Player pl ? pl : null;
		Runnable body = () -> vanilla.extractRenderState(g, dt);
		if (player != null) {
			switch (p) {
				case HEALTH -> {
					if (healthHideFull.get() && player.getHealth() >= player.getMaxHealth() && player.getAbsorptionAmount() <= 0) return;
					if (healthStyle.is("Number")) body = () -> healthNumber(g, mc, player);
				}
				case HUNGER -> {
					if (hungerHideFull.get() && player.getFoodData().getFoodLevel() >= 20) return;
					if (hungerStyle.is("Number")) body = () -> hungerNumber(g, mc, player);
				}
				case ARMOUR -> {
					if (armourStyle.is("Number")) body = () -> armourNumber(g, mc, player);
				}
				default -> {
				}
			}
		}
		Layout l = layout(p);
		if (!l.moved()) {
			body.run();
			if (p == Part.HOTBAR && slotKeys.get()) slotKeys(g, mc);
			return;
		}
		float[] r = bounds(p, mc, g.guiWidth(), g.guiHeight());
		float px = r[0] + r[2] / 2, py = r[1] + r[3] / 2, s = l.scale.get();
		var pose = g.pose();
		pose.pushMatrix();
		pose.translate(px + l.x.get(), py + l.y.get());
		pose.scale(s, s);
		pose.translate(-px, -py);
		body.run();
		if (p == Part.HOTBAR && slotKeys.get()) slotKeys(g, mc);
		pose.popMatrix();
	}

	private void healthNumber(GuiGraphicsExtractor g, Minecraft mc, Player player) {
		int x = g.guiWidth() / 2 - 91, y = g.guiHeight() - 39;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, HEART, x, y, 9, 9);
		String hp = String.valueOf(Mth.ceil(player.getHealth()));
		g.text(mc.font, hp, x + 11, y + 1, 0xFFFFFFFF, true);
		int abs = Mth.ceil(player.getAbsorptionAmount());
		if (abs > 0) g.text(mc.font, "+" + abs, x + 11 + mc.font.width(hp) + 3, y + 1, 0xFFFFAA00, true);
	}

	private String hungerText(Player player) {
		return String.valueOf(player.getFoodData().getFoodLevel());
	}

	private String saturationText(Player player) {
		return String.format(Locale.ROOT, "%.1f", player.getFoodData().getSaturationLevel());
	}

	private void hungerNumber(GuiGraphicsExtractor g, Minecraft mc, Player player) {
		int right = g.guiWidth() / 2 + 91, y = g.guiHeight() - 39;
		g.blitSprite(RenderPipelines.GUI_TEXTURED, FOOD, right - 9, y, 9, 9);
		String food = hungerText(player);
		int x = right - 11;
		if (saturation.get()) {
			String sat = saturationText(player);
			x -= mc.font.width(sat);
			g.text(mc.font, sat, x, y + 1, 0xFFAAAAAA, true);
			x -= 3;
		}
		g.text(mc.font, food, x - mc.font.width(food), y + 1, 0xFFFFFFFF, true);
	}

	private void armourNumber(GuiGraphicsExtractor g, Minecraft mc, Player player) {
		int armour = player.getArmorValue();
		if (armour <= 0) return;
		int x = g.guiWidth() / 2 - 91, y = armourY(player, g.guiHeight());
		g.blitSprite(RenderPipelines.GUI_TEXTURED, ARMOUR, x, y, 9, 9);
		g.text(mc.font, String.valueOf(armour), x + 11, y + 1, 0xFFFFFFFF, true);
	}

	private void slotKeys(GuiGraphicsExtractor g, Minecraft mc) {
		if (!(mc.getCameraEntity() instanceof Player)) return;
		KeyMapping[] keys = mc.options.keyHotbarSlots;
		Font font = mc.font;
		int cx = g.guiWidth() / 2, top = g.guiHeight() - 19;
		g.nextStratum();
		for (int i = 0; i < 9 && i < keys.length; i++) {
			String label = shortKey(keys[i].getTranslatedKeyMessage().getString());
			if (label.isEmpty()) continue;
			int x = cx - 90 + i * 20 + 2;
			var pose = g.pose();
			pose.pushMatrix();
			pose.translate(x, top);
			pose.scale(0.5f, 0.5f);
			g.text(font, label, 0, 0, 0xFFFFFFFF, true);
			pose.popMatrix();
		}
	}

	/** A key's name cut down to fit in the corner of a slot: "Mouse Button 5" is M5, "Keypad 0" is N0. */
	public static String shortKey(String name) {
		String n = name.trim();
		if (n.isEmpty() || n.equalsIgnoreCase("Not Bound")) return "";
		String lower = n.toLowerCase(Locale.ROOT);
		String digits = n.replaceAll("\\D", "");
		if (lower.startsWith("mouse") || lower.startsWith("button")) return "M" + digits;
		if (lower.startsWith("keypad") || lower.startsWith("numpad")) return "N" + digits;
		if (lower.startsWith("left ")) return "L" + shortKey(n.substring(5));
		if (lower.startsWith("right ")) return "R" + shortKey(n.substring(6));
		return n.length() <= 3 ? n.toUpperCase(Locale.ROOT) : n.substring(0, 3);
	}

	// ------------------------------------------------------------------- where parts are

	private static int armourY(Player player, int guiHeight) {
		float maxHealth = Math.max(player.getMaxHealth(), Mth.ceil(player.getHealth()));
		int absorption = Mth.ceil(player.getAbsorptionAmount());
		int rows = Mth.ceil((maxHealth + absorption) / 2.0F / 10.0F);
		int rowHeight = Math.max(10 - (rows - 2), 3);
		return guiHeight - 39 - (rows - 1) * rowHeight - 10;
	}

	/** Where a part draws before it is moved, in GUI pixels: x, y, width, height. */
	public float[] bounds(Part p, Minecraft mc, int w, int h) {
		int cx = w / 2;
		Player player = mc.getCameraEntity() instanceof Player pl ? pl : null;
		Font font = mc.font;
		switch (p) {
			case HOTBAR:
				return new float[] {cx - 91, h - 23, 182, 23};
			case HEALTH: {
				if (player == null) return new float[] {cx - 91, h - 39, 81, 9};
				if (healthStyle.is("Number")) {
					String hp = String.valueOf(Mth.ceil(player.getHealth()));
					int abs = Mth.ceil(player.getAbsorptionAmount());
					int tw = font.width(hp) + (abs > 0 ? 3 + font.width("+" + abs) : 0);
					return new float[] {cx - 91, h - 39, 11 + tw, 9};
				}
				float maxHealth = Math.max(player.getMaxHealth(), Mth.ceil(player.getHealth()));
				int rows = Mth.ceil((maxHealth + Mth.ceil(player.getAbsorptionAmount())) / 2.0F / 10.0F);
				int rowHeight = Math.max(10 - (rows - 2), 3);
				int extra = (rows - 1) * rowHeight;
				return new float[] {cx - 91, h - 39 - extra, 81, 9 + extra};
			}
			case ARMOUR: {
				int y = player != null ? armourY(player, h) : h - 49;
				if (armourStyle.is("Number") && player != null)
					return new float[] {cx - 91, y, 11 + font.width(String.valueOf(player.getArmorValue())), 9};
				return new float[] {cx - 91, y, 81, 9};
			}
			case HUNGER: {
				if (hungerStyle.is("Number") && player != null) {
					int tw = font.width(hungerText(player)) + (saturation.get() ? 3 + font.width(saturationText(player)) : 0);
					return new float[] {cx + 91 - 11 - tw, h - 39, 11 + tw, 9};
				}
				return new float[] {cx + 91 - 81, h - 39, 81, 9};
			}
			case AIR:
				return new float[] {cx + 91 - 81, h - 49, 81, 9};
			case MOUNT:
				return new float[] {cx + 91 - 81, h - 39, 81, 9};
			case XP_BAR:
				return new float[] {cx - 91, h - 29, 182, 5};
			case XP_LEVEL: {
				int level = player != null ? player.experienceLevel : 0;
				int tw = font.width(Component.translatable("gui.experience.level", Math.max(1, level)));
				return new float[] {cx - tw / 2f - 1, h - 36, tw + 2, 10};
			}
			case ITEM_NAME: {
				int tw = 80;
				HudFieldsAccessor hud = (HudFieldsAccessor) mc.gui.hud;
				ItemStack held = hud.leone$lastToolHighlight();
				if (held != null && !held.isEmpty()) tw = font.width(held.getHoverName());
				int y = h - 59 + (mc.gameMode != null && !mc.gameMode.canHurtPlayer() ? 14 : 0);
				return new float[] {cx - tw / 2f - 2, y - 2, tw + 4, 13};
			}
			case CROSSHAIR:
				return new float[] {(w - 15) / 2f, (h - 15) / 2f, 15, 15};
			case EFFECTS: {
				int good = 0, bad = 0;
				if (player != null) {
					for (MobEffectInstance e : player.getActiveEffects()) {
						if (!e.showIcon()) continue;
						if (e.getEffect().value().isBeneficial()) good++;
						else bad++;
					}
				}
				int cols = Math.max(2, Math.max(good, bad));
				return new float[] {w - 25 * cols, 1, 25 * cols, bad > 0 ? 50 : 24};
			}
			case BOSS_BARS: {
				int n = ((BossOverlayAccessor) mc.gui.hud.getBossOverlay()).leone$events().size();
				return new float[] {cx - 91, 2, 182, Math.max(1, Math.min(n, 6)) * 19};
			}
			case SCOREBOARD: {
				float[] r = Sidebar.lastBounds();
				return r != null ? r : new float[] {w - 90, h / 2f - 50, 89, 80};
			}
			case ACTION_BAR: {
				HudFieldsAccessor hud = (HudFieldsAccessor) mc.gui.hud;
				Component msg = hud.leone$overlayMessage();
				int tw = msg != null && hud.leone$overlayMessageTime() > 0 ? font.width(msg) : 120;
				return new float[] {cx - tw / 2f - 2, h - 68 - 6, tw + 4, 13};
			}
			case TITLES:
				return new float[] {cx - 130, h / 2f - 48, 260, 76};
			case TAB_LIST:
				return new float[] {cx - 150, 10, 300, Math.min(140, h / 2f)};
			case SUBTITLES:
			default:
				return new float[] {w - 162, h - 100, 160, 64};
		}
	}

	/** Whether a part has anything on screen right now (the air bar only does under water, titles only now and then). */
	public boolean present(Part p, Minecraft mc) {
		Player player = mc.getCameraEntity() instanceof Player pl ? pl : null;
		HudFieldsAccessor hud = (HudFieldsAccessor) mc.gui.hud;
		boolean bars = player != null && mc.gameMode != null && mc.gameMode.canHurtPlayer();
		return switch (p) {
			case HOTBAR, CROSSHAIR -> player != null;
			case HEALTH, HUNGER -> bars;
			case ARMOUR -> bars && player.getArmorValue() > 0;
			case AIR -> bars && (player.getAirSupply() < player.getMaxAirSupply() || player.isUnderWater());
			case MOUNT -> player != null && player.getVehicle() instanceof net.minecraft.world.entity.LivingEntity;
			case XP_BAR -> player != null && mc.gameMode != null && mc.gameMode.hasExperience();
			case XP_LEVEL -> player != null && player.experienceLevel > 0;
			case ITEM_NAME -> hud.leone$toolHighlightTimer() > 0;
			case EFFECTS -> player != null && player.getActiveEffects().stream().anyMatch(MobEffectInstance::showIcon);
			case BOSS_BARS -> !((BossOverlayAccessor) mc.gui.hud.getBossOverlay()).leone$events().isEmpty();
			case SCOREBOARD -> Sidebar.lastBounds() != null;
			case ACTION_BAR -> hud.leone$overlayMessageTime() > 0;
			case TITLES, TAB_LIST, SUBTITLES -> false;
		};
	}

	/** Where a part is on screen after it has been moved and resized, in GUI pixels. */
	public float[] screenBounds(Part p, Minecraft mc, int w, int h) {
		float[] r = bounds(p, mc, w, h);
		Layout l = layout(p);
		float s = l.scale.get(), px = r[0] + r[2] / 2, py = r[1] + r[3] / 2;
		return new float[] {px + l.x.get() - r[2] * s / 2, py + l.y.get() - r[3] * s / 2, r[2] * s, r[3] * s};
	}

	/** A point on screen, in the part's own unmoved GUI pixels. */
	public float[] toPart(Part p, Minecraft mc, int w, int h, float sx, float sy) {
		float[] r = bounds(p, mc, w, h);
		Layout l = layout(p);
		float s = l.scale.get(), px = r[0] + r[2] / 2, py = r[1] + r[3] / 2;
		return new float[] {px + (sx - px - l.x.get()) / s, py + (sy - py - l.y.get()) / s};
	}

	// ------------------------------------------------------------------- scoreboard lines

	/** Which server hidden scoreboard lines belong to: LeoneMC's server kind, or the address. */
	public static String scope() {
		String server = LeoneMC.server();
		if (server != null) {
			Category c = Category.forServer(server);
			return c == Category.OTHER ? server.toLowerCase(Locale.ROOT) : c.name().toLowerCase(Locale.ROOT);
		}
		Minecraft mc = Minecraft.getInstance();
		if (mc.getCurrentServer() != null) return mc.getCurrentServer().ip.toLowerCase(Locale.ROOT);
		return "local";
	}

	private List<String> hiddenEntries() {
		String v = hiddenLines.get();
		if (!v.equals(hiddenParsedFrom)) {
			hiddenParsedFrom = v;
			hiddenList.clear();
			for (String s : v.split("\n")) if (!s.isEmpty()) hiddenList.add(s);
		}
		return hiddenList;
	}

	/** Whether a scoreboard line (by its {@link Sidebar#key}) has been hidden on this server. */
	public boolean lineHidden(String key) {
		return hiddenEntries().contains(scope() + "\t" + key);
	}

	public void toggleLine(String key) {
		String entry = scope() + "\t" + key;
		List<String> list = new ArrayList<>(hiddenEntries());
		if (!list.remove(entry)) list.add(entry);
		String joined = String.join("\n", list);
		hiddenLines.value = joined.length() > hiddenLines.maxLength ? hiddenLines.value : joined;
	}

	/** How many scoreboard lines are hidden on this server. */
	public int hiddenHere() {
		String prefix = scope() + "\t";
		int n = 0;
		for (String s : hiddenEntries()) if (s.startsWith(prefix)) n++;
		return n;
	}

	public void showAllLinesHere() {
		String prefix = scope() + "\t";
		List<String> list = new ArrayList<>(hiddenEntries());
		list.removeIf(s -> s.startsWith(prefix));
		hiddenLines.value = String.join("\n", list);
	}

	/** Puts every part back where Minecraft draws it, at its usual size, and shows it. */
	public void resetLayout() {
		for (Part p : Part.values()) {
			layout(p).reset();
			setShown(p, true);
		}
	}
}
