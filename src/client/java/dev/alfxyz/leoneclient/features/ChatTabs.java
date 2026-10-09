package dev.alfxyz.leoneclient.features;

import com.google.common.collect.MapMaker;
import dev.alfxyz.leoneclient.Chat;
import dev.alfxyz.leoneclient.mixin.ChatHistoryAccessor;
import dev.alfxyz.leoneclient.module.Category;
import dev.alfxyz.leoneclient.module.Module;
import dev.alfxyz.leoneclient.module.Modules;
import dev.alfxyz.leoneclient.module.Setting;
import dev.alfxyz.leoneclient.render.Icons;
import dev.alfxyz.leoneclient.staffchat.StaffChat;
import dev.alfxyz.leoneclient.staffchat.StaffPlaceholder;
import dev.alfxyz.leoneclient.web.Account;
import dev.alfxyz.leoneclient.web.Friends;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * Tabs above the open chat, each showing one kind of message: players talking, private messages, staff
 * chat and so on. They only change what you read. Every message is still kept, All shows everything,
 * and your own Leone Client notices show in every tab.
 */
public final class ChatTabs extends Module {
	// What a line is. A line can be several things at once, such as a friend's message that mentions you.
	public static final int CLIENT = 1, PLAYER = 1 << 1, PRIVATE = 1 << 2, STAFF = 1 << 3, ALERT = 1 << 4, SERVER = 1 << 5,
		FRIEND = 1 << 6, MENTION = 1 << 7, OWN = 1 << 8;

	public enum Tab {
		ALL("All", ~0, false, "Nothing in chat yet"),
		CHAT("Chat", PLAYER, false, "Nobody has said anything yet"),
		MESSAGES("Messages", PRIVATE, true, "No private messages yet"),
		MENTIONS("Mentions", MENTION, true, "Nobody has mentioned you yet"),
		FRIENDS("Friends", FRIEND, true, "Nothing from your friends yet"),
		STAFF("Staff", ChatTabs.STAFF, true, "No staff chat yet"),
		ALERTS("Alerts", ALERT, true, "No alerts or reports yet"),
		SERVER("Server", ChatTabs.SERVER, false, "Nothing from the server yet"),
		CUSTOM("Custom", 0, true, "Nothing with your words yet");

		final int kinds;
		/** Quiet tabs count what you have not seen; busy ones (Chat, Server) would only ever show a big number. */
		public final boolean counts;
		public final String empty;
		private final String label;

		Tab(String label, int kinds, boolean counts, String empty) {
			this.label = label;
			this.kinds = kinds;
			this.counts = counts;
			this.empty = empty;
		}
	}

	/** Private messages on LeoneMC: "(From Name): message" and "(To Name): message". */
	private static final Pattern LEONE_PM = Pattern.compile("^\\((From|To) ([A-Za-z0-9_.]{3,17})\\): .*", Pattern.DOTALL);
	/** Vanilla's /msg, which some servers keep. */
	private static final Pattern WHISPER_IN = Pattern.compile("^([A-Za-z0-9_.]{3,17}) whispers to you: .*", Pattern.DOTALL);
	private static final Pattern WHISPER_OUT = Pattern.compile("^You whisper to ([A-Za-z0-9_.]{3,17}): .*", Pattern.DOTALL);
	/** Staff chat itself, as against alerts and staff tools: "[Staff] (Server) Name: message", "[SC] ...", "[Admin] ...". */
	private static final Pattern STAFF_CHAT = Pattern.compile("^\\[(?:Staff|SC|Admin)\\]", Pattern.CASE_INSENSITIVE);
	private static final Pattern STAFF_SENDER = Pattern.compile("^\\[[^\\]]{1,10}\\] (?:\\([^)]{1,32}\\) )?([A-Za-z0-9_.]{3,17}): (.*)$", Pattern.DOTALL);
	/** Public chat: "Rank Name [tag] » message" on LeoneMC, "<Name> message" elsewhere. */
	private static final Pattern ARROW_CHAT = Pattern.compile("^(.{1,80}?) » (.*)$", Pattern.DOTALL);
	private static final Pattern ANGLE_CHAT = Pattern.compile("^<([^>]{1,40})> (.*)$", Pattern.DOTALL);
	/** Plugins that write like players ("Grim » ...", "CCBlueX » ..."). */
	private static final Set<String> NOT_PLAYERS = Set.of("grim", "removal", "tg", "anticheat", "server", "ccbluex", "leone");
	/** LeoneMC's friend notices, and Friend Activity's short form of them. */
	private static final Pattern FRIEND_NOTICE = Pattern.compile("^Friends \\| ([A-Za-z0-9_.]{3,17}) has .*", Pattern.DOTALL);
	private static final Pattern FRIEND_SHORT = Pattern.compile("^[+-] ([A-Za-z0-9_.]{3,17}) (?:joined|left) .+");
	private static final Pattern NAME_TOKEN = Pattern.compile("[A-Za-z0-9_]{3,16}");
	/** How long after you send something the server's lines count as its reply, and how many at most. */
	private static final long REPLY_MS = 1500;
	private static final int MAX_REPLY_LINES = 10;

	public final Setting.Chips tabs = add(new Setting.Chips("tabs", "Tabs", "TABS",
			List.of("Chat", "Messages", "Mentions", "Friends", "Server", "Custom"), List.of("Chat", "Messages", "Server")),
		"The tabs after All. Chat: players talking in public chat. Messages: private messages to and from you. "
			+ "Mentions: lines where a player mentions you, and private messages sent to you. Friends: your LeoneMC friends talking, joining and leaving. "
			+ "Server: everything the server says itself, such as broadcasts, events, kills and replies to commands. Custom: lines with your own words.");
	public final Setting.Chips staffTabs = add(new Setting.Chips("staff_tabs", "Staff tabs", "TABS", List.of("Staff", "Alerts"), List.of("Staff", "Alerts")),
		"Staff: staff chat ([Staff], [SC] and [Admin] lines). Alerts: anticheat alerts, reports, help requests, punishments, banned join attempts and the replies of staff tools.");
	public final Setting.Text customName = add(new Setting.Text("custom_name", "Custom tab name", "CUSTOM", "Watch", "Watch", 12,
			c -> Character.isLetterOrDigit(c) || c == ' '),
		"What the Custom tab is called.");
	public final Setting.Text customWords = add(new Setting.Text("custom_words", "Words", "CUSTOM", "", "koth, envoy, target", 120, c -> true),
		"The Custom tab shows lines containing any of these words or phrases, separated by commas. Capitals do not matter.");
	public final Setting.Toggle unread = add(new Setting.Toggle("unread", "Unread counts", "BEHAVIOUR", true),
		"Shows how many messages each quiet tab (Messages, Mentions, Friends, Staff, Alerts, Custom) has that you have not seen because you were on another tab.");
	public final Setting.Toggle replies = add(new Setting.Toggle("replies", "Replies follow you", "BEHAVIOUR", true),
		"The server's reply to a command you just sent, and your own message, also show in the tab you are on, so nothing you do seems to vanish.");
	public final Setting.Toggle keep = add(new Setting.Toggle("keep", "Keep the tab when chat closes", "BEHAVIOUR", false),
		"Normally chat goes back to All when you close it, so you never miss anything while playing. With this on it stays on your tab, and the tab's name shows at the bottom of the chat.");

	/** What a message is, worked out once, and the tab it was a reply in (or -1). */
	private record Info(int kinds, String lower, int replyTab) {
	}

	/** Keyed by the message itself (weakly, by identity), so it goes when the chat forgets the message. */
	private final Map<GuiMessage, Info> infos = new MapMaker().weakKeys().makeMap();
	private Tab current = Tab.ALL;
	private final int[] unreadCount = new int[Tab.values().length];
	private final boolean[] unreadForYou = new boolean[Tab.values().length];
	private long sentAt;
	private int repliesLeft;
	private boolean closing;
	/** What the chat lines were last built for; when it changes they are rebuilt. */
	private String builtFor = "";
	private @Nullable Pattern custom;
	private String customKey = "\u0000";

	public ChatTabs() {
		super("chat_tabs", Category.CHAT, "Chat Tabs", Icons.MESSAGES,
			"Tabs above the open chat for player chat, private messages, mentions, friends, the server and, for staff, staff chat and alerts.", false);
		staffTabs.shownWhen(Category.STAFF::visible);
		customName.shownWhen(() -> tabs.has("Custom"));
		customWords.shownWhen(() -> tabs.has("Custom"));
		ClientSendMessageEvents.CHAT.register(message -> sent());
		ClientSendMessageEvents.COMMAND.register(command -> sent());
	}

	private void sent() {
		sentAt = System.currentTimeMillis();
		repliesLeft = MAX_REPLY_LINES;
	}

	// ------------------------------------------------------------------ tabs

	/** The tabs to show, in order: All first, the staff ones only for staff, Custom only once it has words. */
	public List<Tab> visibleTabs() {
		List<Tab> out = new ArrayList<>();
		out.add(Tab.ALL);
		boolean staff = Category.STAFF.visible();
		for (Tab t : Tab.values()) {
			if (t == Tab.ALL) continue;
			boolean on = switch (t) {
				case STAFF, ALERTS -> staff && staffTabs.has(t.label);
				case CUSTOM -> tabs.has(t.label) && customPattern() != null;
				default -> tabs.has(t.label);
			};
			if (on) out.add(t);
		}
		return out;
	}

	/** The tab being shown: All while the module is off, or when the chosen tab has been taken away. */
	public Tab current() {
		if (!active()) return Tab.ALL;
		if (current != Tab.ALL && !visibleTabs().contains(current)) current = Tab.ALL;
		return current;
	}

	public String label(Tab t) {
		if (t != Tab.CUSTOM) return t.label;
		String name = customName.get().strip();
		return name.isEmpty() ? "Custom" : name;
	}

	public void select(Tab t) {
		if (!active() || t == current()) return;
		current = t;
		unreadCount[t.ordinal()] = 0;
		unreadForYou[t.ordinal()] = false;
		rebuild(true);
	}

	/** The next (or previous) visible tab, wrapping round. */
	public void cycle(int step) {
		List<Tab> list = visibleTabs();
		int i = list.indexOf(current());
		select(list.get(Math.floorMod(i + step, list.size())));
	}

	public int unread(Tab t) {
		return unread.get() && t.counts ? unreadCount[t.ordinal()] : 0;
	}

	/** Whether the unread messages in a tab include one aimed at you (a mention or a private message to you). */
	public boolean unreadForYou(Tab t) {
		return unread(t) > 0 && unreadForYou[t.ordinal()];
	}

	/** A chat screen went away; whether chat closed is known on the next tick, once the new screen is in place. */
	public void chatMayHaveClosed() {
		closing = true;
	}

	// -------------------------------------------------------------- messages

	/** Whether a message is shown in the current tab. Called while the chat lines are rebuilt. */
	public boolean shows(GuiMessage message) {
		Tab t = current();
		return t == Tab.ALL || shows(info(message), t);
	}

	/** A new message: works out what it is, counts it as unread where it is not seen, and says whether to draw it. */
	public boolean arrived(GuiMessage message) {
		// while the module is off nothing is sorted; anything needed later is worked out then
		if (!active()) return true;
		Info info = classify(message, true);
		infos.put(message, info);
		Tab now = current();
		if (shows(info, now)) return true;
		// not seen now: unread in each tab that would show it
		for (Tab t : visibleTabs()) {
			if (t == now || !shows(info, t)) continue;
			unreadCount[t.ordinal()]++;
			if ((info.kinds & MENTION) != 0) unreadForYou[t.ordinal()] = true;
		}
		return false;
	}

	private boolean shows(Info info, Tab t) {
		if (t == Tab.ALL || (info.kinds & CLIENT) != 0 || info.replyTab == t.ordinal()) return true;
		if (t == Tab.CUSTOM) {
			Pattern p = customPattern();
			return p != null && p.matcher(info.lower).find();
		}
		return (info.kinds & t.kinds) != 0;
	}

	private Info info(GuiMessage message) {
		Info info = infos.get(message);
		if (info == null) {
			// from before the module was on, or restored after a server switch
			info = classify(message, false);
			infos.put(message, info);
		}
		return info;
	}

	private Info classify(GuiMessage message, boolean fresh) {
		Component content = message.content();
		if (content instanceof StaffPlaceholder placeholder) content = placeholder.real();
		String plain = Chat.plain(content);
		int kinds = message.source() == GuiMessageSource.SYSTEM_CLIENT ? CLIENT : kinds(plain, message.source() == GuiMessageSource.PLAYER);
		int replyTab = -1;
		if (fresh && replies.get() && (kinds & (SERVER | OWN)) != 0 && repliesLeft > 0 && System.currentTimeMillis() - sentAt < REPLY_MS) {
			repliesLeft--;
			replyTab = current().ordinal();
		}
		return new Info(kinds, plain.toLowerCase(Locale.ROOT), replyTab);
	}

	/** What a line from the server is. Public for the autotest. */
	public int kinds(String plain, boolean signed) {
		Matcher m = LEONE_PM.matcher(plain);
		if (m.matches()) return privateKinds(m.group(1).equals("From"), m.group(2));
		m = WHISPER_IN.matcher(plain);
		if (m.matches()) return privateKinds(true, m.group(1));
		m = WHISPER_OUT.matcher(plain);
		if (m.matches()) return privateKinds(false, m.group(1));
		if (StaffChat.isStaffText(plain)) {
			// switching the staff chat toggle belongs with staff chat, not with the alerts
			if (plain.contains("talking in staff chat")) return STAFF;
			if (!STAFF_CHAT.matcher(plain).lookingAt()) return ALERT;
			m = STAFF_SENDER.matcher(plain);
			return STAFF | (m.matches() ? senderKinds(m.group(1), m.group(2)) : 0);
		}
		m = ARROW_CHAT.matcher(plain);
		if (m.matches() && !NOT_PLAYERS.contains(m.group(1).strip().toLowerCase(Locale.ROOT))) return PLAYER | senderKinds(m.group(1), m.group(2));
		m = ANGLE_CHAT.matcher(plain);
		if (m.matches()) return PLAYER | senderKinds(m.group(1), m.group(2));
		if (signed) return PLAYER;
		int kinds = SERVER;
		if (FRIEND_NOTICE.matcher(plain).matches()) kinds |= FRIEND;
		m = FRIEND_SHORT.matcher(plain);
		if (m.matches() && Friends.isFriend(m.group(1))) kinds |= FRIEND;
		return kinds;
	}

	/**
	 * The player a line is from or about, for opening their profile: the first name in it of a player on
	 * this server (other than you), or else whoever wrote it. Null when there is nobody.
	 */
	public static @Nullable String person(String plain, Predicate<String> here) {
		Matcher t = NAME_TOKEN.matcher(plain);
		while (t.find()) if (!isYou(t.group()) && here.test(t.group())) return t.group();
		Matcher m = LEONE_PM.matcher(plain);
		if (m.matches()) return m.group(2);
		m = WHISPER_IN.matcher(plain);
		if (m.matches()) return m.group(1);
		m = WHISPER_OUT.matcher(plain);
		if (m.matches()) return m.group(1);
		m = STAFF_SENDER.matcher(plain);
		if (m.matches()) return isYou(m.group(1)) ? null : m.group(1);
		m = ARROW_CHAT.matcher(plain);
		if (!m.matches()) m = ANGLE_CHAT.matcher(plain);
		if (m.matches()) {
			// the sender part holds a rank too, and tags in brackets after the name; the name is the last word outside them
			String last = null;
			Matcher n = NAME_TOKEN.matcher(m.group(1).replaceAll("\\[[^\\]]*\\]", " "));
			while (n.find()) last = n.group();
			return last == null || isYou(last) ? null : last;
		}
		return null;
	}

	private static int privateKinds(boolean toYou, String other) {
		int kinds = PRIVATE | (toYou ? MENTION : OWN);
		if (Friends.isFriend(other)) kinds |= FRIEND;
		return kinds;
	}

	/** Yours, or else whether it mentions you and whether a friend sent it. The sender part may hold a rank and tags too. */
	private static int senderKinds(String sender, String body) {
		Matcher names = NAME_TOKEN.matcher(sender);
		List<String> tokens = new ArrayList<>();
		while (names.find()) tokens.add(names.group());
		for (String t : tokens) if (isYou(t)) return OWN;
		int kinds = Modules.MENTIONS.namesYou(body) ? MENTION : 0;
		for (String t : tokens) {
			if (Friends.isFriend(t)) {
				kinds |= FRIEND;
				break;
			}
		}
		return kinds;
	}

	private static boolean isYou(String name) {
		if (name.equalsIgnoreCase(Minecraft.getInstance().getUser().getName())) return true;
		String display = Friends.displayName();
		if (display != null && name.equalsIgnoreCase(display)) return true;
		String account = Account.name();
		return account != null && name.equalsIgnoreCase(account);
	}

	private @Nullable Pattern customPattern() {
		String words = customWords.get();
		if (!words.equals(customKey)) {
			customKey = words;
			List<String> parts = new ArrayList<>();
			for (String w : words.split(",")) if (!w.isBlank()) parts.add(Pattern.quote(w.strip().toLowerCase(Locale.ROOT)));
			custom = parts.isEmpty() ? null : Pattern.compile(String.join("|", parts));
		}
		return custom;
	}

	// ------------------------------------------------------------------ upkeep

	@Override
	public void tick(Minecraft mc) {
		// chat closed (not swapped for another chat screen): back to All, unless the tab is kept
		if (closing) {
			closing = false;
			if (!(mc.gui.screen() instanceof ChatScreen) && !keep.get()) select(Tab.ALL);
		}
		// rebuild the lines whenever what they should show has changed: a tab, the Custom words, the module itself
		String key = current() + "\n" + (current() == Tab.CUSTOM ? customWords.get() : "");
		if (!key.equals(builtFor)) {
			builtFor = key;
			rebuild(false);
		}
	}

	@Override
	protected void onDisable() {
		current = Tab.ALL;
		Arrays.fill(unreadCount, 0);
		Arrays.fill(unreadForYou, false);
	}

	private void rebuild(boolean toBottom) {
		builtFor = current() + "\n" + (current() == Tab.CUSTOM ? customWords.get() : "");
		Minecraft mc = Minecraft.getInstance();
		var chat = mc.gui.hud.getChat();
		if (toBottom) chat.resetChatScroll();
		((ChatHistoryAccessor) chat).leone$refreshLines();
	}

	/** Whether the current tab has nothing to show, for its empty note. */
	public boolean currentEmpty() {
		return ((ChatHistoryAccessor) Minecraft.getInstance().gui.hud.getChat()).leone$lines().isEmpty();
	}

	@Override
	public String status() {
		if (!enabled()) return null;
		int n = visibleTabs().size();
		return n + " tabs" + (current() != Tab.ALL ? ", showing " + label(current()) : "");
	}
}
