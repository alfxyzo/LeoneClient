package dev.alfxyz.leoneclient.features;

import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Filter;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.filter.AbstractFilter;

/** Keeps the warnings LeoneMC triggers by the thousand out of the game log. */
public final class LogFilter extends Module {
	public static final String TEAMS = "Team warnings", ENTITIES = "Unknown entities", TEXTURES = "Pack textures", SKINS = "Skin signatures";

	public final Setting.Chips hide = add(new Setting.Chips("hide", "Hide", "LOG", List.of(TEAMS, ENTITIES, TEXTURES, SKINS), List.of(TEAMS, ENTITIES, TEXTURES, SKINS)),
		"Team warnings: \"Requested creation of existing team\". Unknown entities: passengers for entities you cannot see. "
			+ "Pack textures: frame and texture warnings from the server resource pack. Skin signatures: invalid skin signatures.");
	private final AtomicInteger hidden = new AtomicInteger();

	public LogFilter() {
		super("log_filter", Category.CLIENT, "Log Filter", Icons.TERMINAL,
			"Stops LeoneMC filling your game log with thousands of harmless warnings, which keeps it readable and small.", true);
	}

	@Override
	public boolean leoneOnly() {
		return true;
	}

	/** Adds the filter to the root logger. */
	public void install() {
		try {
			LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
			ctx.getConfiguration().getRootLogger().addFilter(new AbstractFilter() {
				@Override
				public Filter.Result filter(LogEvent event) {
					return drops(event) ? Filter.Result.DENY : Filter.Result.NEUTRAL;
				}
			});
			ctx.updateLoggers();
		} catch (Throwable t) {
			LogUtils.getLogger().warn("Leone Client: could not install the log filter", t);
		}
	}

	private boolean drops(LogEvent event) {
		if (!event.getLevel().isMoreSpecificThan(Level.WARN) || !active()) return false;
		String msg = event.getMessage().getFormattedMessage();
		if (msg == null) return false;
		boolean drop = hide.has(TEAMS) && msg.startsWith("Requested creation of existing team")
			|| hide.has(ENTITIES) && msg.startsWith("Received passengers for unknown entity")
			|| hide.has(TEXTURES) && (msg.startsWith("Invalid frame index on sprite") || msg.startsWith("Missing textures in model")
				|| msg.startsWith("Missing texture references in model") || msg.contains("is not multiple of frame size"))
			|| hide.has(SKINS) && (msg.startsWith("Profile contained invalid signature") || msg.startsWith("Failed to verify signature on property"));
		if (drop) hidden.incrementAndGet();
		return drop;
	}

	@Override
	public String status() {
		int n = hidden.get();
		return n == 0 ? null : n + " lines hidden";
	}
}
