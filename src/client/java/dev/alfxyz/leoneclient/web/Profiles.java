package dev.alfxyz.leoneclient.web;

import dev.alfxyz.leoneclient.web.LeoneWeb.Profile;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/**
 * Profiles fetched from leonemc.net, kept for a while and fetched a few at a
 * time. Everything here runs on the render thread.
 */
public final class Profiles {
	private static final int MAX_CONCURRENT = 4;
	private static final long RETRY_FAILED_MS = 60_000;

	private record Entry(@Nullable Profile profile, boolean missing, long at) {
	}

	private static final Map<UUID, Entry> cache = new HashMap<>();
	private static final Set<UUID> inflight = new HashSet<>();
	private static final Deque<UUID> queue = new ArrayDeque<>();
	private static final Map<UUID, Long> failedAt = new HashMap<>();
	/** Profiles asked for with their statistics, until fetched. */
	private static final Set<UUID> withStats = new HashSet<>();

	private Profiles() {
	}

	/** The cached profile, however old, or null. */
	public static @Nullable Profile get(UUID uuid) {
		Entry e = cache.get(uuid);
		return e == null ? null : e.profile();
	}

	/** True when the player has never joined LeoneMC. */
	public static boolean missing(UUID uuid) {
		Entry e = cache.get(uuid);
		return e != null && e.missing();
	}

	public static boolean loading(UUID uuid) {
		return inflight.contains(uuid) || queue.contains(uuid);
	}

	public static boolean failed(UUID uuid) {
		Long at = failedAt.get(uuid);
		return at != null && System.currentTimeMillis() - at < RETRY_FAILED_MS;
	}

	/**
	 * Makes sure a profile no older than {@code maxAgeMs} is cached or on its way; with {@code stats}, one
	 * that has its statistics (a profile fetched only for someone's presence leaves them out).
	 */
	public static void want(UUID uuid, long maxAgeMs, boolean stats) {
		Entry e = cache.get(uuid);
		boolean enough = e != null && (e.missing() || !stats || e.profile() != null && e.profile().statsLoaded());
		if (enough && System.currentTimeMillis() - e.at() < maxAgeMs) return;
		if (stats) withStats.add(uuid);
		if (loading(uuid) || failed(uuid)) return;
		queue.addLast(uuid);
		pump();
	}

	/** Stores a profile fetched elsewhere, so it is not fetched twice. */
	public static void put(Profile profile) {
		cache.put(profile.uuid(), new Entry(keep(profile), false, profile.fetched()));
	}

	/** A lighter profile (no statistics or page extras) keeps those from the cached one, so they do not flicker away. */
	private static Profile keep(Profile p) {
		Entry old = cache.get(p.uuid());
		Profile o = old == null ? null : old.profile();
		if (o == null || (p.statsLoaded() || !o.statsLoaded()) && (p.pageLoaded() || !o.pageLoaded())) return p;
		boolean stats = !p.statsLoaded() && o.statsLoaded(), page = !p.pageLoaded() && o.pageLoaded();
		return new Profile(p.uuid(), p.name(), page ? o.color() : p.color(), p.rank(), p.rankColor(), p.friends(), p.online(), p.server(), p.lastSeen(),
			p.joined(), page ? o.playtimeMs() : p.playtimeMs(), page ? o.views() : p.views(), stats ? o.stats() : p.stats(), p.fetched(),
			p.statsLoaded() || o.statsLoaded(), p.pageLoaded() || o.pageLoaded());
	}

	private static void pump() {
		while (inflight.size() < MAX_CONCURRENT && !queue.isEmpty()) {
			UUID uuid = queue.pollFirst();
			inflight.add(uuid);
			boolean stats = withStats.remove(uuid);
			// the Players page shows everything; presence needs only the API
			LeoneWeb.profile(uuid, stats, stats).whenComplete((result, err) -> Minecraft.getInstance().execute(() -> {
				inflight.remove(uuid);
				if (err != null) {
					failedAt.put(uuid, System.currentTimeMillis());
				} else {
					failedAt.remove(uuid);
					cache.put(uuid, new Entry(result.map(Profiles::keep).orElse(null), result.isEmpty(), System.currentTimeMillis()));
				}
				pump();
			}));
		}
	}
}
