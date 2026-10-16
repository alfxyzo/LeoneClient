package dev.alfxyz.leoneclient.features;

import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Interaction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.phys.AABB;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Blocks the video adverts Minehut puts on the servers it hosts, LeoneMC among them.
 *
 * <p>An advert is a billboard of item frames holding maps, which the server redraws many times a second
 * to play the video, with the soundtrack played from Minehut's ads resource pack (sound events named
 * {@code minehut.ad.<video>}). Leone Client recognises the billboard by its maps (a map redrawn that often
 * is a video; maps near an advert's sound count too), stops drawing it and lets clicks pass through it,
 * mutes the advert sounds, and can turn the ads pack down so there is nothing to download or load.
 */
public final class AdBlock extends Module {
	private static final Logger LOGGER = LogUtils.getLogger();
	/** A map redrawn this many times within {@link #VIDEO_WINDOW_MS} is playing a video. */
	private static final int VIDEO_UPDATES = 6;
	private static final long VIDEO_WINDOW_MS = 3_000;
	/** Maps in frames this close to where an advert sound plays belong to the advert. */
	private static final double SOUND_RADIUS = 12;

	public final Setting.Toggle hideBillboards = add(new Setting.Toggle("billboards", "Hide ad billboards", "ADVERTS", true),
		"Stops drawing the video billboards and lets you click through them, as if they were not there.");
	public final Setting.Toggle muteSounds = add(new Setting.Toggle("sounds", "Mute ad sounds", "ADVERTS", true),
		"Silences the adverts' soundtracks.");
	public final Setting.Toggle declinePack = add(new Setting.Toggle("pack", "Turn down the ads pack", "ADVERTS", true),
		"Turns down Minehut's ads resource pack (about 18 MB of advert soundtracks), so there is nothing to download or load for it.");

	/** Maps recognised as advert videos, by map id number. */
	private final Set<Integer> adMaps = new HashSet<>();
	/** When each map was last redrawn, for spotting videos. */
	private final Map<Integer, ArrayDeque<Long>> redraws = new HashMap<>();
	/** Entities currently hidden, by entity id. Rebuilt every few ticks on the client thread. */
	private volatile Set<Integer> hidden = Set.of();
	private int hiddenFrames;
	private int sounds;
	private int packs;
	private int ticks;

	public AdBlock() {
		super("ad_block", Category.SERVER, "Ad Block", Icons.VIDEO_OFF,
			"Removes Minehut's video adverts: hides the billboards, lets you click through them and mutes their sound.", true);
	}

	public void register() {
		ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> mc.execute(this::forget));
	}

	@Override
	public boolean hasKeybind() {
		return false;
	}

	@Override
	protected void onDisable() {
		hidden = Set.of();
		hiddenFrames = 0;
	}

	@Override
	public String status() {
		if (!enabled()) return null;
		List<String> parts = new ArrayList<>();
		if (hiddenFrames > 0) parts.add("billboard hidden");
		if (sounds > 0) parts.add(sounds + (sounds == 1 ? " sound" : " sounds") + " muted");
		if (packs > 0) parts.add("pack turned down");
		return parts.isEmpty() ? null : capitalise(String.join(", ", parts));
	}

	private void forget() {
		adMaps.clear();
		redraws.clear();
		hidden = Set.of();
		hiddenFrames = 0;
	}

	// ------------------------------------------------------------------ hooks

	/** Whether a sound is one of Minehut's adverts. */
	public static boolean isAdSound(Identifier id) {
		return id.getNamespace().equals("minehut") || id.getPath().startsWith("minehut.ad.");
	}

	/** A sound is about to play. True to silence it. Its position marks the billboard it belongs to. */
	public boolean onSound(SoundInstance sound) {
		if (!enabled()) return false;
		Identifier id = sound.getIdentifier();
		if (id == null || !isAdSound(id)) return false;
		if (!sound.isRelative() && sound.getAttenuation() != SoundInstance.Attenuation.NONE) {
			markFramesNear(sound.getX(), sound.getY(), sound.getZ());
		}
		if (!muteSounds.get()) return false;
		sounds++;
		LOGGER.info("Leone Client: Ad Block muted {}", id);
		return true;
	}

	/** Whether a server resource pack is Minehut's ads pack, to be turned down. */
	public boolean blocksPack(String url) {
		return enabled() && declinePack.get() && isAdPack(url);
	}

	public static boolean isAdPack(String url) {
		try {
			URI u = URI.create(url);
			String host = u.getHost() == null ? "" : u.getHost().toLowerCase(Locale.ROOT);
			String path = u.getPath() == null ? "" : u.getPath().toLowerCase(Locale.ROOT);
			return host.endsWith("minehut.com") && (host.startsWith("gsads") || path.contains("mhads"));
		} catch (Exception e) {
			return false;
		}
	}

	/** The ads pack was turned down. */
	public void onPackDeclined(String url) {
		packs++;
		LOGGER.info("Leone Client: Ad Block turned down Minehut's ads pack {}", url);
	}

	/**
	 * A map is about to be redrawn. Notes how often, to spot videos. True to skip redrawing it: a
	 * hidden advert's map is not worth the work.
	 */
	public boolean onMapData(MapId map, boolean hasPicture) {
		if (!enabled() || !hasPicture) return false;
		int id = map.id();
		if (adMaps.contains(id)) return hideBillboards.get();
		long now = System.currentTimeMillis();
		ArrayDeque<Long> times = redraws.computeIfAbsent(id, k -> new ArrayDeque<>());
		times.addLast(now);
		while (!times.isEmpty() && now - times.peekFirst() > VIDEO_WINDOW_MS) times.removeFirst();
		if (times.size() >= VIDEO_UPDATES) {
			adMaps.add(id);
			redraws.remove(id);
			LOGGER.info("Leone Client: Ad Block recognised map {} as a video ({} redraws in {} s)", id, VIDEO_UPDATES, VIDEO_WINDOW_MS / 1000);
		}
		// keep the bookkeeping small: forget maps that went quiet
		if (redraws.size() > 512) redraws.values().removeIf(q -> q.isEmpty() || now - q.peekLast() > VIDEO_WINDOW_MS);
		return false;
	}

	/** Whether to stop drawing an entity, and let clicks pass through it. */
	public boolean hides(Entity e) {
		return hidden.contains(e.getId()) && enabled() && hideBillboards.get();
	}

	@Override
	public void tick(Minecraft mc) {
		if (!enabled() || mc.level == null || ++ticks % 5 != 0) return;
		Set<Integer> next = new HashSet<>();
		List<AABB> boards = new ArrayList<>();
		AABB area = null;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ItemFrame frame && isAdMap(frame)) {
				next.add(e.getId());
				AABB box = e.getBoundingBox();
				boards.add(box.inflate(0.75));
				area = area == null ? box : area.minmax(box);
			}
		}
		// clickable boxes laid over the billboard belong to it too
		if (!boards.isEmpty()) {
			for (Entity e : mc.level.entitiesForRendering()) {
				if (e instanceof Interaction) {
					AABB box = e.getBoundingBox();
					for (AABB b : boards) {
						if (b.intersects(box)) {
							next.add(e.getId());
							break;
						}
					}
				}
			}
		}
		int frames = boards.size();
		if (frames != hiddenFrames && frames > 0 && area != null) {
			LOGGER.info("Leone Client: Ad Block hides a billboard of {} item frames ({} entities) around {}", frames, next.size(), area.getCenter());
		}
		hiddenFrames = frames;
		hidden = Set.copyOf(next);
	}

	private boolean isAdMap(ItemFrame frame) {
		MapId map = frame.getItem().get(DataComponents.MAP_ID);
		return map != null && adMaps.contains(map.id());
	}

	private void markFramesNear(double x, double y, double z) {
		Minecraft mc = Minecraft.getInstance();
		if (mc.level == null) return;
		AABB near = new AABB(x, y, z, x, y, z).inflate(SOUND_RADIUS);
		int marked = 0;
		for (Entity e : mc.level.entitiesForRendering()) {
			if (e instanceof ItemFrame frame && near.contains(e.position())) {
				MapId map = frame.getItem().get(DataComponents.MAP_ID);
				if (map != null && adMaps.add(map.id())) marked++;
			}
		}
		if (marked > 0) LOGGER.info("Leone Client: Ad Block marked {} maps beside an advert sound", marked);
	}

	private static String capitalise(String s) {
		return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
	}

	// ------------------------------------------------------------------ autotest

	public boolean debugIsAdMap(int id) {
		return adMaps.contains(id);
	}

	public void debugForget() {
		forget();
		sounds = 0;
		packs = 0;
	}

	public @Nullable Integer debugHiddenCount() {
		return hidden.size();
	}
}
