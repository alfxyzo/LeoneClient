package dev.alfxyz.leoneclient.utils;

import net.minecraft.text.Text;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ActionBarStore {
    private static Text combatActionBar = null;
    private static long combatActionBarTimestamp = 0;
    private static Text regularActionBar = null;
    private static long regularActionBarTimestamp = 0;
    private static long combatTagEndMs = 0;

    private static float lastParsedSeconds = -1;
    private static long lastParsedTimestamp = 0;

    public static void setCombatActionBar(Text message) {
        combatActionBar = message;
        combatActionBarTimestamp = System.currentTimeMillis();
        float secs = parseCombatSeconds(message.getString());
        if (secs > 0) {
            long now = System.currentTimeMillis();
            boolean hasPrev = lastParsedSeconds > 0
                && lastParsedTimestamp > 0
                && (now - lastParsedTimestamp) < 5000;

            if (hasPrev) {
                float elapsed = (now - lastParsedTimestamp) / 1000f;
                float expectedSecs = lastParsedSeconds - elapsed;
                if (secs > expectedSecs + 3.0f) {
                    combatTagEndMs = now + (long)(secs * 1000);
                } else if (secs > expectedSecs + 0.5f) {
                    combatTagEndMs = 0;
                } else {
                    combatTagEndMs = now + (long)(secs * 1000);
                }
            } else {
                combatTagEndMs = now + (long)(secs * 1000);
            }

            lastParsedSeconds = secs;
            lastParsedTimestamp = now;
        }
    }

    public static Text getCombatActionBar() {
        if (combatActionBar != null && System.currentTimeMillis() - combatActionBarTimestamp < 3500) {
            return combatActionBar;
        }
        return null;
    }

    public static void setRegularActionBar(Text message) {
        regularActionBar = message;
        regularActionBarTimestamp = System.currentTimeMillis();
    }

    public static Text getRegularActionBar() {
        if (regularActionBar != null && System.currentTimeMillis() - regularActionBarTimestamp < 3500) {
            return regularActionBar;
        }
        return null;
    }

    public static boolean isCombatTagActive() {
        long now = System.currentTimeMillis();
        if (combatTagEndMs <= now) return false;
        if ((now - combatActionBarTimestamp) >= 1200) return false;
        return true;
    }

    public static float getCombatTagRemainingSeconds() {
        return Math.max(0, (combatTagEndMs - System.currentTimeMillis()) / 1000f);
    }

    private static final Pattern MM_SS = Pattern.compile("(\\d+):(\\d{2})s?\\s*$");
    private static final Pattern SECS  = Pattern.compile("(\\d+(?:\\.\\d+)?)s?\\s*$");

    private static float parseCombatSeconds(String raw) {
        String clean = raw.replaceAll("§.", "").trim();
        Matcher m = MM_SS.matcher(clean);
        if (m.find()) {
            try { return Integer.parseInt(m.group(1)) * 60 + Integer.parseInt(m.group(2)); }
            catch (NumberFormatException ignored) {}
        }
        m = SECS.matcher(clean);
        if (m.find()) {
            try { return Float.parseFloat(m.group(1)); }
            catch (NumberFormatException ignored) {}
        }
        return 0;
    }
}
