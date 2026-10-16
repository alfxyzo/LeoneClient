package dev.alfxyz.leoneclient.hud;

import dev.alfxyz.leoneclient.features.HudTailor;
import dev.alfxyz.leoneclient.module.Modules;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import org.jspecify.annotations.Nullable;

/**
 * The scoreboard on the right of the screen, drawn the way Minecraft draws it but with HUD Tailor's
 * trimming: lines hidden by hand or by word, blank and address lines dropped, renamed words.
 */
public final class Sidebar {
	private static final Comparator<PlayerScoreEntry> ORDER = Comparator.comparing(PlayerScoreEntry::value).reversed()
		.thenComparing(PlayerScoreEntry::owner, String.CASE_INSENSITIVE_ORDER);
	private static final Pattern ADDRESS = Pattern.compile("(?i)^\\s*(?:www\\.|play\\.|mc\\.)?[a-z0-9-]+(?:\\.[a-z0-9-]+)*\\.(?:net|com|org|gg|co|uk|me|io|us|eu|xyz|club)\\s*$");
	private static final Pattern LEGACY = Pattern.compile("§.");

	/** A line drawn last frame, in unmoved GUI pixels, for the editor to click. */
	public record Line(String key, float x, float y, float w, float h, boolean hidden) {
	}

	private static float @Nullable [] lastBounds;
	private static long lastDrawn;
	private static final List<Line> lines = new ArrayList<>();

	private Sidebar() {
	}

	/** The scoreboard's box last frame, or null when none has been drawn lately. */
	public static float @Nullable [] lastBounds() {
		return System.currentTimeMillis() - lastDrawn < 500 ? lastBounds : null;
	}

	public static List<Line> lines() {
		return System.currentTimeMillis() - lastDrawn < 500 ? lines : List.of();
	}

	/** A line's identity for hiding it: its text with numbers blanked, so a changing value still matches. */
	public static String key(Component line) {
		return LEGACY.matcher(line.getString()).replaceAll("").replaceAll("\\d", "#").strip();
	}

	private record Entry(Component name, Component score, int scoreWidth, String key, boolean hidden) {
	}

	public static void draw(GuiGraphicsExtractor g, Objective objective) {
		HudTailor t = Modules.HUD_TAILOR;
		Minecraft mc = Minecraft.getInstance();
		Font font = mc.font;
		boolean editor = mc.gui.screen() instanceof VanillaHudEditor;
		Scoreboard scoreboard = objective.getScoreboard();
		NumberFormat format = objective.numberFormatOrDefault(StyledFormat.SIDEBAR_DEFAULT);
		List<String[]> renames = renames(t.rename.get());
		List<String> words = words(t.hideWith.get());

		List<PlayerScoreEntry> scores = new ArrayList<>(scoreboard.listPlayerScores(objective));
		scores.removeIf(PlayerScoreEntry::isHidden);
		scores.sort(ORDER);
		List<Entry> entries = new ArrayList<>();
		int visible = 0, max = Math.round(t.maxLines.get());
		for (PlayerScoreEntry score : scores) {
			if (entries.size() >= 15) break;
			PlayerTeam team = scoreboard.getPlayersTeam(score.owner());
			Component name = PlayerTeam.formatNameForTeam(team, score.ownerName());
			String key = key(name);
			String plain = LEGACY.matcher(name.getString()).replaceAll("");
			boolean hidden = t.lineHidden(key)
				|| t.hideBlank.get() && plain.isBlank()
				|| t.hideAddress.get() && ADDRESS.matcher(plain).matches()
				|| containsAny(plain, words)
				|| visible >= max;
			if (hidden && !editor) continue;
			if (!hidden) visible++;
			Component shown = renamed(name, renames);
			Component scoreText = t.hideScores.get() ? Component.empty() : score.formatValue(format);
			if (hidden) shown = Component.literal(shown.getString()).withStyle(ChatFormatting.DARK_GRAY, ChatFormatting.STRIKETHROUGH);
			int scoreWidth = font.width(scoreText);
			entries.add(new Entry(shown, scoreText, scoreWidth, key, hidden));
		}

		Component title = renamed(objective.getDisplayName(), renames);
		boolean showTitle = !t.hideTitle.get();
		int biggest = showTitle ? font.width(title) : 0;
		int spacer = font.width(": ");
		for (Entry e : entries) biggest = Math.max(biggest, font.width(e.name) + (e.scoreWidth > 0 ? spacer + e.scoreWidth : 0));

		int count = entries.size();
		lines.clear();
		lastDrawn = System.currentTimeMillis();
		if (count == 0 && !showTitle) {
			lastBounds = null;
			return;
		}
		int height = count * 9;
		int bottom = g.guiHeight() / 2 + height / 3;
		int left = g.guiWidth() - biggest - 3;
		int right = g.guiWidth() - 3 + 2;
		float bg = t.background.get() / 100f;
		int body = (int) (Math.min(1, bg) * 255) << 24;
		int head = (int) (Math.min(1, bg + 0.1f) * 255) << 24;
		int headerY = bottom - count * 9;
		boolean shadow = t.shadow.get();
		if (showTitle) {
			if (bg > 0) g.fill(left - 2, headerY - 9 - 1, right, headerY - 1, head);
			g.text(font, title, left + biggest / 2 - font.width(title) / 2, headerY - 9, -1, shadow);
		}
		if (count > 0 && bg > 0) g.fill(left - 2, headerY - 1, right, bottom, body);
		for (int i = 0; i < count; i++) {
			Entry e = entries.get(i);
			int y = bottom - (count - i) * 9;
			g.text(font, e.name, left, y, -1, shadow);
			if (e.scoreWidth > 0) g.text(font, e.score, right - e.scoreWidth, y, e.hidden ? 0xFF555555 : -1, shadow);
			lines.add(new Line(e.key, left - 2, y - 1, right - left + 2, 9, e.hidden));
		}
		int top = showTitle ? headerY - 10 : headerY - 1;
		lastBounds = new float[] {left - 2, top, right - left + 2, bottom - top};
	}

	private static boolean containsAny(String plain, List<String> words) {
		if (words.isEmpty()) return false;
		String lower = plain.toLowerCase(Locale.ROOT);
		for (String w : words) if (lower.contains(w)) return true;
		return false;
	}

	private static List<String> words(String setting) {
		List<String> out = new ArrayList<>();
		for (String w : setting.split(",")) {
			String s = w.strip().toLowerCase(Locale.ROOT);
			if (!s.isEmpty()) out.add(s);
		}
		return out;
	}

	private static List<String[]> renames(String setting) {
		List<String[]> out = new ArrayList<>();
		for (String rule : setting.split("[;\n]")) {
			int i = rule.indexOf('>');
			if (i <= 0) continue;
			String from = rule.substring(0, i).strip(), to = rule.substring(i + 1).strip();
			if (!from.isEmpty()) out.add(new String[] {from, to});
		}
		return out;
	}

	/** The line with each rename applied, keeping its colours where the old words sat in one colour. */
	static Component renamed(Component line, List<String[]> renames) {
		if (renames.isEmpty()) return line;
		String plain = line.getString();
		boolean any = false;
		for (String[] r : renames) any |= plain.contains(r[0]);
		if (!any) return line;
		MutableComponent out = Component.empty();
		boolean[] done = {false};
		Style[] first = {null};
		line.visit((style, text) -> {
			String t = text;
			for (String[] r : renames) {
				if (t.contains(r[0])) {
					t = t.replace(r[0], r[1]);
					done[0] = true;
				}
			}
			if (first[0] == null && !text.isEmpty()) first[0] = style;
			out.append(Component.literal(t).withStyle(style));
			return Optional.<FormattedText>empty();
		}, Style.EMPTY);
		if (done[0]) return out;
		// the words span two colours: fall back to the first colour for the whole line
		String replaced = plain;
		for (String[] r : renames) replaced = replaced.replace(r[0], r[1]);
		return Component.literal(replaced).withStyle(first[0] != null ? first[0] : Style.EMPTY);
	}
}
