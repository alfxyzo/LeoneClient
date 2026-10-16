package dev.alfxyz.leoneclient.features;

import com.google.common.hash.HashCode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.LeoneMC;
import dev.alfxyz.leoneclient.mixin.DownloadedPackSourceAccessor;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.web.Http;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.SharedConstants;
import net.minecraft.WorldVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.resources.server.PackReloadConfig;
import net.minecraft.server.packs.PackType;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Downloads LeoneMC's server resource packs in the background, before you join, so joining does not wait
 * on them. LeoneMC updates its pack often, and the game only keeps the version it last downloaded, so
 * without this nearly every update means sitting through a fresh download on the join screen.
 *
 * <p>Leone Client remembers the packs LeoneMC sends (starting with the network pack on GitHub), checks
 * them now and then (a quick "has it changed?" request that downloads nothing when it has not), and keeps
 * the newest copy of each. When the game is about to download a pack whose SHA-1 matches a copy here, the
 * copy is put where the game looks first, and the game uses it after checking the hash itself. Packs
 * that do not match are downloaded by the game as usual.
 *
 * <p>Loading a pack this size takes a resource reload of several seconds, and the game does one when you
 * leave (to take the packs off) and again for each pack when you come back. With Keep Loaded, LeoneMC's
 * packs stay on after you leave, and when LeoneMC asks for packs that are already on, the reload is
 * skipped. A changed pack, another server or singleplayer reloads as usual.
 */
public final class PackPrefetch extends Module {
	private static final Logger LOGGER = LogUtils.getLogger();
	private static final String NETWORK_PACK = "https://github.com/Leone-Gaming/Textures/raw/refs/heads/main/Network.zip";
	/** The game's own limit for a server pack. */
	private static final long MAX_SIZE = 262_144_000L;
	private static final int MAX_PACKS = 6;
	/** A pack LeoneMC has not sent for this long is forgotten. */
	private static final long FORGET_MS = 30L * 24 * 60 * 60 * 1000;
	private static final long FIRST_CHECK_DELAY_MS = 5_000;

	public final Setting.Slider interval = add(new Setting.Slider("interval", "Check every", "DOWNLOADS", 5, 60, 5, 10, "%.0f min"),
		"How often to ask whether LeoneMC's packs have changed. A check with no change downloads nothing.");
	public final Setting.Toggle keepLoaded = add(new Setting.Toggle("keep_loaded", "Keep loaded between joins", "LOADING", true),
		"Leaves LeoneMC's packs on after you leave, so relogging does not reload them. They come off when you join another server or a world.");
	public final Setting.Toggle tidy = add(new Setting.Toggle("tidy", "Remove old versions", "DOWNLOADS", true),
		"When a pack is handed to the game, deletes the older versions of it from the game's download folder.");

	private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
		Thread t = new Thread(r, "Leone Client pack prefetch");
		t.setDaemon(true);
		t.setPriority(Thread.MIN_PRIORITY);
		return t;
	});
	private final List<Entry> packs = new ArrayList<>();
	private boolean loaded;
	private volatile boolean busy;
	private volatile @Nullable String progress;
	private long startedAt;
	private long lastRun;
	/** Bytes the game did not have to download this session. */
	private volatile long saved;
	/** The server-list entry LeoneMC was last joined from, whose pack setting can change while we watch. */
	private @Nullable ServerData leoneEntry;
	/** The pack files the game has loaded as server packs, and whether LeoneMC asked for them. */
	private List<Path> active = List.of();
	private boolean activeForLeone;
	/** True while LeoneMC's packs are still on after leaving LeoneMC. */
	private boolean lingering;
	private int reloadsSkipped;
	/** For the autotest: treat the connection as LeoneMC (true), as not (false), or look (null). */
	private @Nullable Boolean debugOnLeone;
	/** When to say in chat that the packs are off: a moment after joining, so the server's welcome does not bury it. */
	private long warnAt;

	/** A pack and what is known about it. Guarded by {@code packs}. */
	private static final class Entry {
		final String url;
		@Nullable String etag;
		@Nullable String sha1;
		long size;
		long seen;

		Entry(String url) {
			this.url = url;
		}
	}

	public PackPrefetch() {
		super("pack_prefetch", Category.SERVER, "Pack Prefetch", Icons.DOWNLOAD,
			"Downloads LeoneMC's resource packs before you join and keeps them loaded between joins, so joining does not wait on them.", true);
	}

	public void register() {
		ClientLifecycleEvents.CLIENT_STARTED.register(mc -> startedAt = System.currentTimeMillis());
		LeoneMC.onFreshJoin(() -> {
			ServerData server = Minecraft.getInstance().getCurrentServer();
			if (server == null || !LeoneMC.connected()) return;
			leoneEntry = server;
			warnAt = System.currentTimeMillis() + 3_000;
		});
		// on any other server or in a world, LeoneMC's packs kept on after leaving come off
		ClientPlayConnectionEvents.JOIN.register((handler, sender, mc) -> {
			if (!onLeone()) unloadLingering(mc);
		});
	}

	/** Whether the server list has LeoneMC's resource packs switched off, as the last LeoneMC entry joined says. */
	public boolean packsOff() {
		ServerData e = leoneEntry;
		return e != null && e.getResourcePackStatus() == ServerData.ServerPackStatus.DISABLED;
	}

	/** Says in chat when LeoneMC's packs are switched off, and how to switch them on. True when it warned. */
	public boolean warnIfPacksOff() {
		if (!enabled() || !packsOff()) return false;
		Chat.info("LeoneMC's resource packs are switched off for this server in your server list, so its custom textures "
			+ "will be missing and Pack Prefetch cannot help. To switch them on: Multiplayer, select this server, Edit, "
			+ "set Server Resource Packs to Enabled, then rejoin.");
		return true;
	}

	@Override
	public boolean hasKeybind() {
		return false;
	}

	@Override
	protected void onEnable() {
		lastRun = 0;
	}

	@Override
	public void tick(Minecraft mc) {
		long now = System.currentTimeMillis();
		if (warnAt != 0 && now >= warnAt) {
			warnAt = 0;
			if (LeoneMC.connected()) warnIfPacksOff();
		}
		if (lingering && (!enabled() || !keepLoaded.get()) && !onLeone()) unloadLingering(mc);
		if (!enabled() || startedAt == 0 || busy) return;
		if (now - startedAt < FIRST_CHECK_DELAY_MS) return;
		if (lastRun != 0 && now - lastRun < (long) (interval.get() * 60_000)) return;
		lastRun = now;
		refresh();
	}

	/** Checks every known pack for a newer version, in the background. */
	public void refresh() {
		if (busy) return;
		busy = true;
		worker.execute(() -> {
			try {
				load();
				for (Entry e : snapshot()) fetch(e);
				save();
			} catch (Exception e) {
				LOGGER.warn("Leone Client: pack prefetch failed", e);
			} finally {
				progress = null;
				busy = false;
			}
		});
	}

	@Override
	public String status() {
		if (!enabled()) return null;
		if (packsOff()) return "Server packs are off for LeoneMC";
		String p = progress;
		if (p != null) return p;
		if (saved > 0 || reloadsSkipped > 0) {
			List<String> parts = new ArrayList<>();
			if (saved > 0) parts.add(mb(saved));
			if (reloadsSkipped > 0) parts.add(reloadsSkipped + (reloadsSkipped == 1 ? " reload" : " reloads"));
			return "Saved " + String.join(" and ", parts) + " this session";
		}
		int ready;
		synchronized (packs) {
			ready = (int) packs.stream().filter(e -> e.sha1 != null).count();
		}
		return ready == 0 ? null : ready == 1 ? "1 pack ready" : ready + " packs ready";
	}

	@Override
	public boolean statusWarns() {
		return packsOff();
	}

	// ------------------------------------------------------------------ hooks

	/** The server asked for a pack. Remembers it when the server is LeoneMC, and checks it soon if it is new. */
	public void onPackPushed(URL url, @Nullable String hash) {
		if (!enabled() || !onLeone()) return;
		String u = url.toString();
		if (!u.startsWith("https://") && !u.startsWith("http://")) return;
		boolean stale;
		synchronized (packs) {
			load();
			Entry e = find(u);
			if (e == null) {
				e = new Entry(u);
				packs.add(e);
			}
			e.seen = System.currentTimeMillis();
			stale = hash != null && !hash.equalsIgnoreCase(e.sha1);
			packs.sort((a, b) -> Long.compare(b.seen, a.seen));
			while (packs.size() > MAX_PACKS) packs.removeLast();
		}
		// the game downloads this one itself; check again in a minute so the copy here catches up
		if (stale) lastRun = Math.min(lastRun, System.currentTimeMillis() - (long) (interval.get() * 60_000) + 60_000);
		worker.execute(this::save);
	}

	/**
	 * The game is about to download a pack into {@code targetDir} and will first look for
	 * {@code targetDir/<hash>}. When a copy with that hash is here, puts it there. Runs on the game's
	 * download thread.
	 */
	public void supply(Path targetDir, @Nullable HashCode hash) {
		if (!enabled() || hash == null) return;
		String sha1 = hash.toString().toLowerCase(Locale.ROOT);
		Path ours = dir().resolve(sha1 + ".zip");
		Path target = targetDir.resolve(hash.toString());
		try {
			if (!Files.isRegularFile(ours)) return;
			if (Files.isRegularFile(target)) return;
			Files.createDirectories(targetDir);
			Path tmp = targetDir.resolve(sha1 + ".leoneclient.tmp");
			Files.copy(ours, tmp, StandardCopyOption.REPLACE_EXISTING);
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			long size = Files.size(target);
			saved += size;
			LOGGER.info("Leone Client: handed the game its server pack {} from Pack Prefetch ({})", sha1, mb(size));
			if (tidy.get()) tidyOld(targetDir, target);
		} catch (Exception e) {
			LOGGER.warn("Leone Client: could not hand over prefetched pack {}", sha1, e);
		}
	}

	/**
	 * The game is about to reload its resources for a new set of server packs. Returns true to skip it: when
	 * leaving LeoneMC (its packs stay on), or when LeoneMC asks for packs that are all on already. The
	 * game is then told the packs applied, as it would be after the reload.
	 */
	public boolean skipReload(PackReloadConfig.Callbacks callbacks) {
		List<Path> want = callbacks.packsToLoad().stream().map(PackReloadConfig.IdAndPath::path).toList();
		if (enabled() && keepLoaded.get()) {
			boolean skip = false;
			if (want.isEmpty() && !active.isEmpty() && activeForLeone) {
				lingering = true;
				skip = true;
				LOGGER.info("Leone Client: keeping LeoneMC's server packs loaded after leaving");
			} else if (!want.isEmpty() && onLeone() && activeForLeone && active.containsAll(want)) {
				lingering = false;
				reloadsSkipped++;
				skip = true;
				LOGGER.info("Leone Client: LeoneMC's server packs are already loaded, skipping the reload");
			}
			if (skip) {
				Minecraft.getInstance().schedule(callbacks::onSuccess);
				return true;
			}
		}
		active = want;
		activeForLeone = !want.isEmpty() && onLeone();
		lingering = false;
		return false;
	}

	/** Takes off LeoneMC's packs that were kept on after leaving. */
	private void unloadLingering(Minecraft mc) {
		if (!lingering) return;
		lingering = false;
		active = List.of();
		activeForLeone = false;
		LOGGER.info("Leone Client: taking off LeoneMC's server packs kept from the last visit");
		((DownloadedPackSourceAccessor) mc.getDownloadedPackSource()).leoneclient$setPackSource(output -> {
		});
		mc.reloadResourcePacks();
	}

	/** Deletes the other versions the game kept of this pack (its folder holds one file per version). */
	private static void tidyOld(Path targetDir, Path keep) {
		try (Stream<Path> files = Files.list(targetDir)) {
			for (Path f : files.toList()) {
				String n = f.getFileName().toString();
				if (!f.equals(keep) && n.matches("[0-9a-f]{40}")) Files.deleteIfExists(f);
			}
		} catch (Exception e) {
			LOGGER.debug("Leone Client: could not tidy {}", targetDir, e);
		}
	}

	private boolean onLeone() {
		if (debugOnLeone != null) return debugOnLeone;
		if (LeoneMC.connected()) return true;
		ServerData server = Minecraft.getInstance().getCurrentServer();
		return server != null && LeoneMC.isKnownAddress(server.ip);
	}

	// ------------------------------------------------------------------ downloading

	private void fetch(Entry e) {
		String etag;
		String had;
		synchronized (packs) {
			etag = e.etag;
			had = e.sha1;
		}
		// Ad Block turns the ads pack down, so there is no point keeping it
		if (Modules.AD_BLOCK.blocksPack(e.url)) return;
		// without our copy, a "not changed" reply would leave us with nothing
		if (had == null || !Files.isRegularFile(dir().resolve(had + ".zip"))) {
			etag = null;
			had = null;
		}
		Path tmp = null;
		try {
			Files.createDirectories(dir());
			tmp = Files.createTempFile(dir(), "download", ".tmp");
			progress = "Checking for updates";
			Http.FileReply reply = Http.download(e.url, headers(), etag, tmp, MAX_SIZE);
			if (reply.status() == 304) return;
			if (reply.status() != 200) {
				LOGGER.info("Leone Client: pack prefetch got {} for {}", reply.status(), e.url);
				return;
			}
			String sha1 = sha1(tmp);
			long size = Files.size(tmp);
			Path file = dir().resolve(sha1 + ".zip");
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
			tmp = null;
			synchronized (packs) {
				e.etag = reply.etag();
				e.sha1 = sha1;
				e.size = size;
			}
			if (!sha1.equals(had)) LOGGER.info("Leone Client: prefetched server pack {} ({}) from {}", sha1, mb(size), e.url);
			deleteUnused();
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		} catch (Exception ex) {
			LOGGER.info("Leone Client: could not prefetch {}: {}", e.url, ex.toString());
		} finally {
			if (tmp != null) {
				try {
					Files.deleteIfExists(tmp);
				} catch (Exception ignored) {
				}
			}
		}
	}

	/** The headers the game sends with a pack download, apart from who you are. */
	private static Map<String, String> headers() {
		WorldVersion v = SharedConstants.getCurrentVersion();
		return Map.of(
			"X-Minecraft-Version", v.name(),
			"X-Minecraft-Version-ID", v.id(),
			"X-Minecraft-Pack-Format", String.valueOf(v.packVersion(PackType.CLIENT_RESOURCES)));
	}

	private static String sha1(Path file) throws Exception {
		MessageDigest md = MessageDigest.getInstance("SHA-1");
		try (InputStream in = Files.newInputStream(file)) {
			byte[] buf = new byte[1 << 16];
			for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
		}
		return HexFormat.of().formatHex(md.digest());
	}

	/** Deletes copies no remembered pack points to any more. */
	private void deleteUnused() {
		List<String> keep;
		synchronized (packs) {
			keep = packs.stream().map(e -> e.sha1).filter(s -> s != null).map(s -> s + ".zip").toList();
		}
		try (Stream<Path> files = Files.list(dir())) {
			for (Path f : files.toList()) {
				String n = f.getFileName().toString();
				if (n.endsWith(".zip") && !keep.contains(n)) Files.deleteIfExists(f);
			}
		} catch (Exception e) {
			LOGGER.debug("Leone Client: could not clean {}", dir(), e);
		}
	}

	// ------------------------------------------------------------------ saving

	/** Where the copies are kept, one file per version named by its SHA-1. */
	public static Path dir() {
		return FabricLoader.getInstance().getConfigDir().resolve("leoneclient").resolve("packs");
	}

	private static Path file() {
		return dir().resolve("packs.json");
	}

	/** For the autotest: the SHA-1 of the newest copy of a pack, or null. */
	public @Nullable String debugCopyOf(String url) {
		synchronized (packs) {
			Entry e = find(url);
			return e == null ? null : e.sha1;
		}
	}

	/** For the autotest: as if on LeoneMC (true) or not (false), or null to look; and a fresh start. */
	public void debugLeone(@Nullable Boolean onLeone, boolean reset) {
		debugOnLeone = onLeone;
		if (reset) {
			active = List.of();
			activeForLeone = false;
			lingering = false;
			reloadsSkipped = 0;
		}
	}

	public boolean lingering() {
		return lingering;
	}

	/** For the autotest: as if another server or a world was joined. */
	public void debugJoinedElsewhere(Minecraft mc) {
		unloadLingering(mc);
	}

	/** For the autotest: as if LeoneMC was last joined from this server-list entry. */
	public void debugEntry(@Nullable ServerData entry) {
		leoneEntry = entry;
	}

	/** The network pack on GitHub, which is always looked after. */
	public static String networkPack() {
		return NETWORK_PACK;
	}

	private @Nullable Entry find(String url) {
		for (Entry e : packs) if (e.url.equals(url)) return e;
		return null;
	}

	private List<Entry> snapshot() {
		synchronized (packs) {
			return List.copyOf(packs);
		}
	}

	private void load() {
		synchronized (packs) {
			if (loaded) return;
			loaded = true;
			try {
				if (Files.isRegularFile(file())) {
					JsonObject o = JsonParser.parseString(Files.readString(file())).getAsJsonObject();
					long now = System.currentTimeMillis();
					for (var el : o.getAsJsonArray("packs")) {
						JsonObject p = el.getAsJsonObject();
						Entry e = new Entry(p.get("url").getAsString());
						e.etag = p.has("etag") ? p.get("etag").getAsString() : null;
						e.sha1 = p.has("sha1") ? p.get("sha1").getAsString() : null;
						e.size = p.has("size") ? p.get("size").getAsLong() : 0;
						e.seen = p.has("seen") ? p.get("seen").getAsLong() : now;
						if (now - e.seen < FORGET_MS || e.url.equals(NETWORK_PACK)) packs.add(e);
					}
				}
			} catch (Exception e) {
				LOGGER.warn("Leone Client: could not read {}", file(), e);
			}
			if (find(NETWORK_PACK) == null) packs.add(new Entry(NETWORK_PACK));
		}
	}

	private void save() {
		JsonArray list = new JsonArray();
		synchronized (packs) {
			if (!loaded) return;
			for (Entry e : packs) {
				JsonObject p = new JsonObject();
				p.addProperty("url", e.url);
				if (e.etag != null) p.addProperty("etag", e.etag);
				if (e.sha1 != null) p.addProperty("sha1", e.sha1);
				p.addProperty("size", e.size);
				p.addProperty("seen", e.seen);
				list.add(p);
			}
		}
		JsonObject o = new JsonObject();
		o.add("packs", list);
		try {
			Files.createDirectories(dir());
			Files.writeString(file(), o.toString());
		} catch (Exception e) {
			LOGGER.warn("Leone Client: could not write {}", file(), e);
		}
	}

	private static String mb(long bytes) {
		return String.format(Locale.ROOT, "%.1f MB", bytes / 1_048_576.0);
	}
}
