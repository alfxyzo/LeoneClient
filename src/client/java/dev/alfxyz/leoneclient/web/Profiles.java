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

	/** Makes sure a profile no older than {@code maxAgeMs} is cached or on its way. */
	public static void want(UUID uuid, long maxAgeMs) {
		Entry e = cache.get(uuid);
		if (e != null && System.currentTimeMillis() - e.at() < maxAgeMs) return;
		if (loading(uuid) || failed(uuid)) return;
		queue.addLast(uuid);
		pump();
	}

	/** Stores a profile fetched elsewhere, so it is not fetched twice. */
	public static void put(Profile profile) {
		cache.put(profile.uuid(), new Entry(profile, false, profile.fetched()));
	}

	private static void pump() {
		while (inflight.size() < MAX_CONCURRENT && !queue.isEmpty()) {
			UUID uuid = queue.pollFirst();
			inflight.add(uuid);
			LeoneWeb.profile(uuid).whenComplete((result, err) -> Minecraft.getInstance().execute(() -> {
				inflight.remove(uuid);
				if (err != null) {
					failedAt.put(uuid, System.currentTimeMillis());
				} else {
					failedAt.remove(uuid);
					cache.put(uuid, new Entry(result.orElse(null), result.isEmpty(), System.currentTimeMillis()));
				}
				pump();
			}));
		}
	}
}
