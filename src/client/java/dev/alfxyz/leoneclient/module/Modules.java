package dev.alfxyz.leoneclient.module;

import dev.alfxyz.leoneclient.features.ActionBar;
import dev.alfxyz.leoneclient.features.AlertFilter;
import dev.alfxyz.leoneclient.features.AllServers;
import dev.alfxyz.leoneclient.features.AntiMute;
import dev.alfxyz.leoneclient.features.AutoJoin;
import dev.alfxyz.leoneclient.features.BookBlocker;
import dev.alfxyz.leoneclient.features.CombatBar;
import dev.alfxyz.leoneclient.features.CombatLogGuard;
import dev.alfxyz.leoneclient.features.CombatTimer;
import dev.alfxyz.leoneclient.features.EnderChestPages;
import dev.alfxyz.leoneclient.features.FriendActivity;
import dev.alfxyz.leoneclient.features.FriendHighlight;
import dev.alfxyz.leoneclient.features.Interface;
import dev.alfxyz.leoneclient.features.LogFilter;
import dev.alfxyz.leoneclient.features.Mentions;
import dev.alfxyz.leoneclient.features.PackWarnings;
import java.util.ArrayList;
import java.util.List;

/** Every module, in the order they appear in their category. */
public final class Modules {
	public static final CombatTimer COMBAT_TIMER = new CombatTimer();
	public static final CombatBar COMBAT_BAR = new CombatBar();
	public static final CombatLogGuard COMBAT_LOG_GUARD = new CombatLogGuard();
	public static final AntiMute ANTI_MUTE = new AntiMute();
	public static final AlertFilter ALERT_FILTER = new AlertFilter();
	public static final Mentions MENTIONS = new Mentions();
	public static final FriendHighlight FRIEND_HIGHLIGHT = new FriendHighlight();
	public static final FriendActivity FRIEND_ACTIVITY = new FriendActivity();
	public static final ActionBar ACTION_BAR = new ActionBar();
	public static final PackWarnings PACK_WARNINGS = new PackWarnings();
	public static final AutoJoin AUTO_JOIN = new AutoJoin();
	public static final BookBlocker BOOK_BLOCKER = new BookBlocker();
	public static final EnderChestPages ENDER_CHEST_PAGES = new EnderChestPages();
	public static final Interface INTERFACE = new Interface();
	public static final AllServers ALL_SERVERS = new AllServers();
	public static final LogFilter LOG_FILTER = new LogFilter();

	private static final List<Module> ALL = List.of(
		COMBAT_TIMER, COMBAT_BAR, COMBAT_LOG_GUARD,
		ANTI_MUTE, ALERT_FILTER, MENTIONS,
		FRIEND_HIGHLIGHT, FRIEND_ACTIVITY,
		ACTION_BAR, PACK_WARNINGS,
		AUTO_JOIN, BOOK_BLOCKER, ENDER_CHEST_PAGES,
		INTERFACE, ALL_SERVERS, LOG_FILTER);

	private Modules() {
	}

	public static List<Module> of(Category c) {
		List<Module> out = new ArrayList<>();
		for (Module m : ALL) if (m.category == c) out.add(m);
		return out;
	}

	public static List<Module> all() {
		return ALL;
	}
}
