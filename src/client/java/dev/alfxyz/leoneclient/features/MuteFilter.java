package dev.alfxyz.leoneclient.features;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The patterns LeoneMC's chat filter mutes for, and replacement of whatever
 * matches. Matches are first swapped for a private-use marker so a pattern
 * can never match the replacement text, then the markers become the
 * replacement.
 */
public final class MuteFilter {
	public static final String DISCRIMINATION = "Discrimination", DEATH_WISHES = "Death wishes", SWEARS = "Swears", ADS = "Advertisements";
	private static final char MARK = '';

	private static final List<Pattern> discrimination = new ArrayList<>();
	private static final List<Pattern> deathWishes = new ArrayList<>();
	private static final List<Pattern> swears = new ArrayList<>();
	private static final List<Pattern> ads = new ArrayList<>();
	private static final List<Pattern> adsIgnored = new ArrayList<>();

	static {
		discrimination.add(p("n[i1l!u]+[g68][g68]+?[e3o]+r+[s\\$5]?"));
		discrimination.add(p("(ph|f)[a@][g68][g68][i1]ng"));
		discrimination.add(p("(ph|f)[a@][g68][g68]?[o0][t\\+][s\\$5]?"));
		discrimination.add(p("n+[i!1l]+[g68][g68]+?[a4]+"));
		discrimination.add(p("ch[i1]nk"));
		discrimination.add(p("n[e3][g68]r[o0][1i]d"));
		discrimination.add(p("n[e3][g68]r[o0][s5]"));
		// the server's rules end in ".*\b"; replacing the word alone is enough to stop them matching
		discrimination.add(p("\\bn[i|1!]+g+$"));
		discrimination.add(p("\\b(ph|f)[a@]g+g?[s\\$]\\b"));
		discrimination.add(p("\\b(ph|f)[a@]g+[s\\$]?\\b"));
		discrimination.add(p("b[e3][a4]n[e3]r"));
		discrimination.add(p("\\bc[o0][o0]+n\\w*"));
		discrimination.add(p("c[o0]tt[o0]n ?p[i1|!]+ck[e3]+r"));
		discrimination.add(p("卍|卐"));
		discrimination.add(p("k[a4]nk[e3]r"));
		discrimination.add(p("n[a4]z[i1]"));

		deathWishes.add(p("(h[a4]ng|n[3e]ck|k[1il!]ll) ?y[o0]urs[e3]lf"));
		deathWishes.add(p("\\bk+y+[s\\$5]+\\b"));
		deathWishes.add(p("c[o0]mm[i1!]t ?su[i1!]c[i1!]de"));

		swears.add(p("\\b[a@][s$][s$]+\\b"));
		swears.add(p("[a@][s$][s$]h[o0][l1][e3][s$]?"));
		swears.add(p("b[a@][s$][t+][a@]rd"));
		swears.add(p("b[e3][a@][s$][t+][i1][a@]?[l1]([i1][t+]y)?"));
		swears.add(p("b[i1][t+]ch([s$]|[e3][rs$]|[e3][s$]|[i1]ng?)?"));
		swears.add(p("b[l1][o0]wj[o0]b[s$]?"));
		swears.add(p("c[l1][i1][t+]"));
		swears.add(p("^(ck|c|k|q)[o0](ck|c|k|q)[s$]?$"));
		swears.add(p("(ck|c|k|q)[o0](ck|c|k|q)[s$]u(ck|c|k|q)[e3]r[s$]?"));
		swears.add(p("(ck|c|k|q)[o0](ck|c|k|q)[s$]u(ck|c|k|q)[i1]ng"));
		swears.add(p("(ck|c|k|q)[o0](ck|c|k|q)[s$]u(ck|c|k|q)[e3]d"));
		swears.add(p("(ck|c|k|q)[o0](ck|c|k|q)[s$]u(ck|c|k|q)[s$]"));
		swears.add(p("(ck|c|k|q)[o0](ck|c|k|q)[s$]u\\b"));
		swears.add(p("^cum[s$]?$"));
		swears.add(p("d[i1](ck|c|k)[s$]?"));
		swears.add(p("d[i1][l1]d[o0][s$]?"));
		swears.add(p("d[i1]n(ck|c|k|q)[s$]?"));
		swears.add(p("[e3]j[a@]cu[l1]"));
		swears.add(p("(ph|f)[e3][l1][l1]?[a@][t+][i1][o0]"));
		swears.add(p("(ph|f)[uv](ck|c|k|q)[s$]?"));
		swears.add(p("g[a@]ngb[a@]ng([e3]d|[s$])?"));
		swears.add(p("h[o0]m+[o0]"));
		swears.add(p("h[o0]rny"));
		swears.add(p("j[a@](ck|c|k|q)-?[o0](ph|f)(ph|f)?"));
		swears.add(p("j[e3]rk-?[o0](ph|f)(ph|f)?"));
		swears.add(p("j[i1!]zz"));
		swears.add(p("mast(e|ur)b(8|ait|ate)"));
		swears.add(p("n[i1]gg?[e3]r[s$]?"));
		swears.add(p("[o0]rg[a@][s$][i1]?m[s$]?"));
		swears.add(p("p[e3]nn?[i1][s$]"));
		swears.add(p("p[i1][s$]{2}([o0](ph|f){2})?"));
		swears.add(p("p[o0]rn([o0]([s$]|gr[a@]phy)?)?"));
		swears.add(p("pu[s$]{2}([i1][e3][s$]|y[s$]?)"));
		swears.add(p("[s$][e3]x"));
		swears.add(p("[s$]h[i1][t+][s$]?"));
		swears.add(p("[s$][l1]u[t+][s$]?"));
		swears.add(p("r[3e]t[4a]rd"));
		swears.add(p("cunt[s$]?"));
		swears.add(p("\\bg[4a]y\\b"));
		swears.add(p("wh[o0]r[e3][s$]?"));
		swears.add(p("w[a4]nk[e3]r[s$]?"));
		swears.add(p("m[i1][e3]rd[a4]"));
		swears.add(p("\\bp[uv0]t[o0a4]\\S*\\b"));
		swears.add(p("b[o0]n[e3]r[s$]?"));

		ads.add(p("m[1il]n[e3]hut\\.gg"));
		ads.add(p("(?:[a-z0-9-]{0,61}[a-z0-9])\\.(?:net|com|gg)"));
		ads.add(p("/j[0o][i1l]n"));
		ads.add(p("\\(dot\\)"));
		ads.add(p("br[o0]k[1i]t[s5]"));
		ads.add(p("br[o0]b[o0]x[e3]d"));
		ads.add(p("br[o0]pr[o0]xy"));

		adsIgnored.add(p("leonemc"));
		adsIgnored.add(p("youtube"));
		adsIgnored.add(p("twitter"));
		adsIgnored.add(p("instagram"));
		adsIgnored.add(p("imgur"));
		adsIgnored.add(p("gyazo"));
		adsIgnored.add(p("prntscr"));
		adsIgnored.add(p("prnt\\.sc"));
		adsIgnored.add(p("cdn"));
		adsIgnored.add(p("pvphub"));
		adsIgnored.add(p("minemen"));
		adsIgnored.add(p("pvplegacy"));
		adsIgnored.add(p("mcpvp"));
		adsIgnored.add(p("sr\\.mod"));
		adsIgnored.add(p("jr\\.mod"));
	}

	private MuteFilter() {
	}

	private static Pattern p(String regex) {
		return Pattern.compile(regex, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
	}

	public record Result(String text, List<String> matched) {
		public boolean changed() {
			return !matched.isEmpty();
		}
	}

	/** Filters {@code message} with the enabled groups (see the group name constants). */
	public static Result filter(String message, String replacement, Set<String> groups) {
		String result = message.replace(MARK, ' ');
		Set<String> found = new LinkedHashSet<>();
		if (groups.contains(DISCRIMINATION)) result = apply(result, discrimination, found);
		if (groups.contains(DEATH_WISHES)) result = apply(result, deathWishes, found);
		if (groups.contains(SWEARS)) result = apply(result, swears, found);
		if (groups.contains(ADS)) {
			String original = message;
			boolean safeLink = adsIgnored.stream().anyMatch(ig -> ig.matcher(original).find());
			if (!safeLink) result = apply(result, ads, found);
		}
		if (found.isEmpty()) return new Result(message, List.of());
		return new Result(result.replace(String.valueOf(MARK), replacement), new ArrayList<>(found));
	}

	private static String apply(String input, List<Pattern> group, Set<String> found) {
		String result = input;
		for (Pattern pat : group) {
			Matcher m = pat.matcher(result);
			if (!m.find()) continue;
			StringBuilder sb = new StringBuilder();
			do {
				if (m.group().isEmpty()) continue;
				found.add(m.group());
				m.appendReplacement(sb, String.valueOf(MARK));
			} while (m.find());
			m.appendTail(sb);
			result = sb.toString();
		}
		return result;
	}
}
