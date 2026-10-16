package dev.alfxyz.leoneclient.features;

import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Friends;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;

/**
 * Shows the line under other players' names (LeoneMC puts their health there) from as far away as their
 * name, instead of only within ten blocks.
 */
public final class ScoreSight extends Module {
	private static final Pattern NUMBER = Pattern.compile("\\d+(?:\\.\\d+)?");

	public final Setting.Choice range = add(new Setting.Choice("range", "Range", "DISTANCE",
		List.of("Name tag range", "Custom", "Unlimited"), "Name tag range"),
		"Name tag range shows it wherever the player's name shows. Unlimited shows it for every player the server sends you.");
	public final Setting.Slider custom = (Setting.Slider) add(new Setting.Slider("custom_range", "Custom range", "DISTANCE", 10, 256, 2, 64, "%.0f blocks"),
		"How far away the line under a name still shows.").shownWhen(() -> range.is("Custom"));
	public final Setting.Toggle needName = add(new Setting.Toggle("need_name", "Only with a name", "WHO", true),
		"Beyond the usual ten blocks, only shows the line when the player's name is showing too, so hidden players stay hidden.");
	public final Setting.Toggle friendsOnly = add(new Setting.Toggle("friends_only", "Only friends", "WHO", false),
		"Beyond the usual ten blocks, only shows the line for your LeoneMC friends.");
	public final Setting.Toggle colour = add(new Setting.Toggle("colour", "Colour health", "LOOK", false),
		"When the line is health (a number with a heart), colours the number green, yellow or red by how much is left.");

	public ScoreSight() {
		super("score_sight", Category.COMBAT, "Score Sight", Icons.EYE,
			"See the line under players' names, such as their health, from as far as you can see their name, not only when you are close.", true);
	}

	@Override
	public String status() {
		return switch (range.get()) {
			case "Custom" -> custom.display();
			case "Unlimited" -> "Unlimited";
			default -> null;
		};
	}

	/** How far the line under this entity's name shows, given the game's own distance. */
	public double belowNameDistance(Entity entity, double vanilla) {
		if (!active() || !(entity instanceof Player) || entity == Minecraft.getInstance().player) return vanilla;
		double far = switch (range.get()) {
			case "Unlimited" -> Double.POSITIVE_INFINITY;
			case "Custom" -> custom.get();
			default -> ((LivingEntity) entity).getAttributeValue(Attributes.NAME_TAG_DISTANCE);
		};
		return Math.max(vanilla, far);
	}

	/** Called once the game has worked out a living entity's name lines. */
	public void afterNameTags(LivingEntity entity, EntityRenderState state) {
		if (!active() || !(entity instanceof Player player) || state.scoreText == null) return;
		double vanilla = entity.getAttributeValue(Attributes.BELOW_NAME_DISTANCE);
		if (state.distanceToCameraSq >= vanilla * vanilla) {
			// only lines we brought in from further away are filtered; close up the game decides
			if (needName.get() && state.nameTag == null || friendsOnly.get() && !Friends.isFriend(player.getUUID())) {
				state.scoreText = null;
				return;
			}
		}
		if (colour.get()) state.scoreText = coloured(state.scoreText, player);
	}

	private static Component coloured(Component line, Player player) {
		String plain = line.getString();
		if (plain.indexOf('❤') < 0) return line;
		var m = NUMBER.matcher(plain);
		if (!m.find()) return line;
		double value;
		try {
			value = Double.parseDouble(m.group());
		} catch (NumberFormatException e) {
			return line;
		}
		float max = Math.max(1, player.getMaxHealth());
		double frac = value / max;
		int rgb = frac > 0.6 ? 0x55FF55 : frac > 0.3 ? 0xFFFF55 : 0xFF5555;
		return ChatStyle.restyle(line, NUMBER, (style, text) -> style.withColor(rgb));
	}
}
