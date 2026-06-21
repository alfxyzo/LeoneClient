package dev.alfxyz.leoneclient.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.Map;

// All references to LeoneClientConfig.getAntiMuteCustomReplacements() and related config logic have been removed.

public class AntiMute {
    private static final List<Pattern> patterns = new ArrayList<>();
    private static final List<Pattern> ignoredPatterns = new ArrayList<>();

    static {
        // Discrimination strict
        patterns.add(Pattern.compile("n[i1l!u]+[g68][g68]+?[e3o]+r+[s\\$5]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(ph|f)[a@][g68][g68][i1]ng", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(ph|f)[a@][g68][g68]?[o0][t\\+][s\\$5]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("n+[i!1l]+[g68][g68]+?[a4]+", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("ch[i1]nk", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("n[e3][g68]r[o0][1i]d", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("n[e3][g68]r[o0][s5]", Pattern.CASE_INSENSITIVE));
        // Discrimination
        patterns.add(Pattern.compile("\\bn[i|1|!]+g+$.*\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\b(ph|f)[a@]g+g?[s\\$]\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\b(ph|f)[a@]g+[s\\$]?\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[e3][a4]n[e3]r", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\bc[o0][o0]+n.*\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("c[o0]tt[o0]n[ ]{0,1}p[i1|!]+ck[e3]+r", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("卍|卐", Pattern.CASE_INSENSITIVE));
        // Death wishes
        patterns.add(Pattern.compile("(h[a|4]ng|n[3|e]ck|k[1|i|l|!]ll)[ ]{0,1}y[o|0]urs[e|3]lf", Pattern.CASE_INSENSITIVE));
        // Death wishes punish
        patterns.add(Pattern.compile("\\bk+y+[s|\\$|5]+$.*\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("c[o|0]mm[i|1|!]t[ ]{0,1}su[i|1|!]c[i|1|!]de", Pattern.CASE_INSENSITIVE));
        // Advertisement
        patterns.add(Pattern.compile("m[1il]n[e3]hut.gg", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(?:[a-z0-9-]{0,61}[a-z0-9])\\.(?:[net|com|gg]{2,3})", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\/j[0o][i1l]n", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\(dot\\)", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("br[o|0]k[1|i]t[s|5]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("br[o|0]b[o|0]x[e|3]d", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("br[o|0]pr[o|0]xy", Pattern.CASE_INSENSITIVE));
        // Swears
        patterns.add(Pattern.compile("\\b[a|@][s|$][s|$]+\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("[a@][s\\$][s\\$]h[o0][l1][e3][s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[a@][s\\$][t\\+][a@]rd", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[e3][a@][s\\$][t\\+][i1][a@]?[l1]([i1][t\\+]y)?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[e3][a@][s\\$][t\\+][i1][l1][i1][t\\+]y", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[e3][s\\$][t\\+][i1][a@][l1]([i1][t\\+]y)?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[i1][t\\+]ch[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[i1][t\\+]ch[e3]r[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[i1][t\\+]ch[e3][s\\$]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[i1][t\\+]ch[i1]ng?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[l1][o0]wj[o0]b[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("c[l1][i1][t\\+]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("^(c|k|ck|q)[o0](c|k|ck|q)[s\\$]?$", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(c|k|ck|q)[o0](c|k|ck|q)[s\\$]u", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(c|k|ck|q)[o0](c|k|ck|q)[s\\$]u(c|k|ck|q)[e3]d", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(c|k|ck|q)[o0](c|k|ck|q)[s\\$]u(c|k|ck|q)[e3]r", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(c|k|ck|q)[o0](c|k|ck|q)[s\\$]u(c|k|ck|q)[i1]ng", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(c|k|ck|q)[o0](c|k|ck|q)[s\\$]u(c|k|ck|q)[s\\$]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("^cum[s\\$]?$", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("d[i1]ck", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("d[i1][l1]d[o0]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("d[i1][l1]d[o0][s\\$]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("d[i1]n(c|k|ck|q)", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("d[i1]n(c|k|ck|q)[s\\$]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("[e3]j[a@]cu[l1]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(ph|f)[e3][l1][l1]?[a@][t\\+][i1][o0]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(ph|f)u(c|k|ck|q)", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("(ph|f)u(c|k|ck|q)[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("g[a@]ngb[a@]ng[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("g[a@]ngb[a@]ng[e3]d", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("h[o0]m?m[o0]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("h[o0]rny", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("j[a@](c|k|ck|q)\\-?[o0](ph|f)(ph|f)?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("j[e3]rk\\-?[o0](ph|f)(ph|f)?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("j[i1!]zz", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("mast(e|ur)b(8|ait|ate)", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("n[i1]gg?[e3]r[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("[o0]rg[a@][s\\$][i1]m[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("[o0]rg[a@][s\\$]m[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("p[e3]nn?[i1][s\\$]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("p[i1][s\\$][s\\$]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("p[i1][s\\$][s\\$][o0](ph|f)(ph|f)", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("p[o0]rn", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("p[o0]rn[o0][s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("p[o0]rn[o0]gr[a@]phy", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("pu[s\\$][s\\$][i1][e3][s\\$]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("pu[s\\$][s\\$]y[s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("[s\\$][e3]x", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("[s\\$]h[i1][t\\+][s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("[s\\$][l1]u[t\\+][s\\$]?", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("r[3e]t[4a]rd", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("cunt$", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\bgay\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\bwhore\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("k[a|4]nk[e3]r", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("n[a|4]z[i|1]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("m[i1][e3]rd[a4]", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("\\bp[u0v]t[o0a4].*\\b", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("b[o0]n[e3]r", Pattern.CASE_INSENSITIVE));
        patterns.add(Pattern.compile("d[1i]ld[o0]", Pattern.CASE_INSENSITIVE));

        // Advertisement ignored patterns
        ignoredPatterns.add(Pattern.compile("leonemc", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("youtube", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("twitter", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("instagram", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("imgur", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("gyazo", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("prntscr", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("prnt\\.sc", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("cdn", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("pvphub", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("minemen", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("pvplegacy", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("mcpvp", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("sr\\.mod", Pattern.CASE_INSENSITIVE));
        ignoredPatterns.add(Pattern.compile("jr\\.mod", Pattern.CASE_INSENSITIVE));
    }

    public static class FilterResult {
        public final String filtered;
        public final boolean changed;
        public FilterResult(String filtered, boolean changed) {
            this.filtered = filtered;
            this.changed = changed;
        }
    }

    public static FilterResult filterMessageWithResult(String message) {
        boolean changed = false;
        String filtered = message;
        // Custom replacements logic removed.
        // If any ignored pattern matches, skip advertisement patterns
        boolean ignoreAd = false;
        for (Pattern ignored : ignoredPatterns) {
            if (ignored.matcher(filtered).find()) {
                ignoreAd = true;
                break;
            }
        }
        for (Pattern pattern : patterns) {
            if (ignoreAd && isAdPattern(pattern)) continue;
            String newFiltered = pattern.matcher(filtered).replaceAll("[redacted]");
            if (!newFiltered.equals(filtered)) changed = true;
            filtered = newFiltered;
        }
        return new FilterResult(filtered, changed);
    }

    public static String filterMessage(String message) {
        return filterMessageWithResult(message).filtered;
    }

    public static boolean shouldBlockMessage(String message) {
        // Check if any pattern matches the message
        for (Pattern pattern : patterns) {
            if (pattern.matcher(message).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean isAdPattern(Pattern pattern) {
        String s = pattern.pattern();
        return s.contains("min[e3]hut.gg") || s.contains("\\.(?:[net|com|gg]{2,3})") || s.contains("/j[0o][i1l]n") || s.contains("\\(dot\\)") || s.contains("br[o|0]k[1|i]t[s|5]") || s.contains("br[o|0]b[o|0]x[e|3]d") || s.contains("br[o|0]pr[o|0]xy");
    }
} 