package dev.alfxyz.leoneclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.features.AutoJoin;
import dev.alfxyz.leoneclient.features.MuteFilter;
import dev.alfxyz.leoneclient.hud.Hud;
import dev.alfxyz.leoneclient.hud.Overlay;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Persistence. Everything lives in config/leoneclient/:
 * <ul>
 * <li>client.json: active config and overlay layout</li>
 * <li>friends.json: the LeoneMC friends list cache (see {@link dev.alfxyz.leoneclient.web.Friends})</li>
 * <li>configs/&lt;name&gt;.json: one file per config profile (module states and settings)</li>
 * </ul>
 */
public final class LeoneConfig {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Pattern NAME = Pattern.compile("[A-Za-z0-9 _-]{1,24}");

	public static String activeConfig = "Main";

	public record Profile(String name, int enabled, long modified) {
	}

	private LeoneConfig() {
	}

	private static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient");
	}

	private static Path configsDir() {
		return dir().resolve("configs");
	}

	private static Path profileFile(String name) {
		return configsDir().resolve(name + ".json");
	}

	// ------------------------------------------------------------------ load

	public static void load() {
		Path clientFile = dir().resolve("client.json");
		boolean firstRun = !Files.isRegularFile(clientFile);
		JsonObject client = read(clientFile);
		if (client != null) {
			if (client.has("activeConfig")) activeConfig = client.get("activeConfig").getAsString();
			if (client.has("overlays")) Hud.load(client.getAsJsonObject("overlays"));
		}
		if (!isValidName(activeConfig)) activeConfig = "Main";
		applyProfile(read(profileFile(activeConfig)));
		if (firstRun) importLegacy();
	}

	private static void applyProfile(@Nullable JsonObject root) {
		for (Module m : Modules.all()) m.reset();
		if (root == null) return;
		JsonObject mods = root.has("modules") ? root.getAsJsonObject("modules") : new JsonObject();
		for (Module m : Modules.all()) {
			if (!mods.has(m.key()) || !mods.get(m.key()).isJsonObject()) continue;
			JsonObject o = mods.getAsJsonObject(m.key());
			try {
				if (o.has("enabled")) m.setEnabled(o.get("enabled").getAsBoolean());
				if (o.has("visible")) m.visible = o.get("visible").getAsBoolean();
				if (o.has("bind")) m.bind = o.get("bind").getAsInt();
			} catch (RuntimeException e) {
				LOGGER.warn("Leone Client: bad saved state for {}", m.key(), e);
			}
			JsonObject settings = o.has("settings") && o.get("settings").isJsonObject() ? o.getAsJsonObject("settings") : new JsonObject();
			for (Setting s : m.settings) {
				JsonElement e = settings.get(s.id);
				if (e == null) continue;
				try {
					s.load(e);
				} catch (RuntimeException ex) {
					LOGGER.warn("Leone Client: bad saved value for {}.{}", m.key(), s.id, ex);
				}
			}
		}
	}

	/**
	 * Carries settings over from the old single-file config/leoneclient.json, once,
	 * the first time this version runs.
	 */
	private static void importLegacy() {
		JsonObject old = read(FabricLoader.getInstance().getConfigDir().resolve("leoneclient.json"));
		if (old == null) return;
		try {
			if (old.has("autoJoinServer")) {
				String server = old.get("autoJoinServer").getAsString();
				boolean on = !server.equalsIgnoreCase("Remain in hub");
				Modules.AUTO_JOIN.setEnabled(on);
				if (on) {
					String match = null;
					for (String opt : Modules.AUTO_JOIN.server.options) if (opt.equalsIgnoreCase(server)) match = opt;
					if (match != null && !match.equals(AutoJoin.CUSTOM)) {
						Modules.AUTO_JOIN.server.set(match);
					} else {
						Modules.AUTO_JOIN.server.set(AutoJoin.CUSTOM);
						Modules.AUTO_JOIN.custom.value = server;
					}
				}
			}
			if (old.has("actionBarEnabled")) {
				boolean bars = old.get("actionBarEnabled").getAsBoolean();
				Modules.ACTION_BAR.setEnabled(bars);
				Modules.COMBAT_BAR.setEnabled(bars);
			}
			if (old.has("mergeActionBars")) Modules.COMBAT_BAR.merge.value = old.get("mergeActionBars").getAsBoolean();
			if (old.has("combatTimerWidget")) Modules.COMBAT_TIMER.setEnabled(old.get("combatTimerWidget").getAsBoolean());
			if (old.has("hideCombatBarWhenEffect")) Modules.COMBAT_BAR.hideWithTimer.value = old.get("hideCombatBarWhenEffect").getAsBoolean();
			if (old.has("hideChatAlerts")) Modules.ALERT_FILTER.setEnabled(old.get("hideChatAlerts").getAsBoolean());
			if (old.has("antiMuteEnabled")) Modules.ANTI_MUTE.setEnabled(old.get("antiMuteEnabled").getAsBoolean());
			if (old.has("antiMuteReplacement")) Modules.ANTI_MUTE.replacement.value = old.get("antiMuteReplacement").getAsString();
			String[][] groups = {{"filterDiscrimination", MuteFilter.DISCRIMINATION}, {"filterDeathWishes", MuteFilter.DEATH_WISHES},
				{"filterSwears", MuteFilter.SWEARS}, {"filterAdvertisements", MuteFilter.ADS}};
			for (String[] g : groups) {
				if (!old.has(g[0])) continue;
				if (old.get(g[0]).getAsBoolean()) Modules.ANTI_MUTE.filters.selected.add(g[1]);
				else Modules.ANTI_MUTE.filters.selected.remove(g[1]);
			}
			// the old bars stored the gap between the text and the bottom of the screen
			if (old.has("regularActionBarY")) placeBar(Hud.byId("action_bar"), old.get("regularActionBarY").getAsInt());
			if (old.has("combatActionBarY")) placeBar(Hud.byId("combat_bar"), old.get("combatActionBarY").getAsInt());
			LOGGER.info("Leone Client: imported settings from the previous version");
		} catch (RuntimeException e) {
			LOGGER.warn("Leone Client: could not import config/leoneclient.json", e);
		}
		save();
	}

	private static void placeBar(Overlay o, int textGap) {
		o.ax = Overlay.CENTER;
		o.ay = Overlay.END;
		o.ox = 0;
		o.oy = Math.max(0, textGap + 3 - 13);
	}

	// ------------------------------------------------------------------ save

	public static void save() {
		saveClient();
		saveProfile(activeConfig);
	}

	public static void saveClient() {
		JsonObject root = new JsonObject();
		root.addProperty("activeConfig", activeConfig);
		root.add("overlays", Hud.save());
		write(dir().resolve("client.json"), root);
	}

	private static void saveProfile(String name) {
		JsonObject root = new JsonObject();
		JsonObject mods = new JsonObject();
		for (Module m : Modules.all()) {
			JsonObject o = new JsonObject();
			o.addProperty("enabled", m.enabled());
			o.addProperty("visible", m.visible);
			o.addProperty("bind", m.bind);
			if (!m.settings.isEmpty()) {
				JsonObject settings = new JsonObject();
				for (Setting s : m.settings) settings.add(s.id, s.save());
				o.add("settings", settings);
			}
			mods.add(m.key(), o);
		}
		root.add("modules", mods);
		write(profileFile(name), root);
	}

	// -------------------------------------------------------------- profiles

	public static boolean isValidName(String name) {
		return name != null && NAME.matcher(name).matches() && !name.isBlank() && name.equals(name.strip());
	}

	public static boolean exists(String name) {
		return listProfiles().stream().anyMatch(p -> p.name().equalsIgnoreCase(name));
	}

	/** All saved profiles, the active one first, then most recently modified. */
	public static List<Profile> listProfiles() {
		saveProfile(activeConfig);
		List<Profile> out = new ArrayList<>();
		try (Stream<Path> files = Files.list(configsDir())) {
			for (Path p : (Iterable<Path>) files::iterator) {
				String file = p.getFileName().toString();
				if (!file.endsWith(".json")) continue;
				String name = file.substring(0, file.length() - 5);
				int enabled = 0;
				JsonObject root = read(p);
				if (root != null && root.has("modules")) {
					for (var e : root.getAsJsonObject("modules").entrySet()) {
						Module m = Modules.all().stream().filter(x -> x.key().equals(e.getKey())).findFirst().orElse(null);
						if (m == null || !m.toggleable() || !e.getValue().isJsonObject()) continue;
						JsonObject o = e.getValue().getAsJsonObject();
						if (o.has("enabled") && o.get("enabled").getAsBoolean()) enabled++;
					}
				}
				out.add(new Profile(name, enabled, Files.getLastModifiedTime(p).toMillis()));
			}
		} catch (IOException e) {
			LOGGER.warn("Leone Client: could not list configs", e);
		}
		out.sort(Comparator.comparing((Profile p) -> !p.name().equals(activeConfig)).thenComparing(p -> -p.modified()));
		return out;
	}

	/** Saves the current state, then loads {@code name} and makes it active. */
	public static void switchTo(String name) {
		if (name.equals(activeConfig)) return;
		saveProfile(activeConfig);
		applyProfile(read(profileFile(name)));
		activeConfig = name;
		saveClient();
	}

	/** Saves the current state under a new name and makes it active. */
	public static boolean create(String name) {
		if (!isValidName(name) || exists(name)) return false;
		saveProfile(activeConfig);
		activeConfig = name;
		saveProfile(name);
		saveClient();
		return true;
	}

	public static boolean delete(String name) {
		if (name.equals(activeConfig)) return false;
		try {
			return Files.deleteIfExists(profileFile(name));
		} catch (IOException e) {
			LOGGER.warn("Leone Client: could not delete config {}", name, e);
			return false;
		}
	}

	public static Path configsFolder() {
		try {
			Files.createDirectories(configsDir());
		} catch (IOException ignored) {
		}
		return configsDir();
	}

	// ------------------------------------------------------------------- io

	private static @Nullable JsonObject read(Path path) {
		if (!Files.isRegularFile(path)) return null;
		try {
			JsonElement e = JsonParser.parseString(Files.readString(path));
			return e.isJsonObject() ? e.getAsJsonObject() : null;
		} catch (Exception e) {
			LOGGER.warn("Leone Client: could not read {}", path, e);
			return null;
		}
	}

	private static void write(Path path, JsonElement json) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, GSON.toJson(json));
		} catch (IOException e) {
			LOGGER.warn("Leone Client: could not write {}", path, e);
		}
	}
}
