package dev.alfxyz.leoneclient.utils;

import net.minecraft.text.Text;

public class ActionBarStore {
    private static Text combatActionBar = null;
    private static long combatActionBarTimestamp = 0;
    private static Text regularActionBar = null;
    private static long regularActionBarTimestamp = 0;

    public static void setCombatActionBar(Text message) {
        combatActionBar = message;
        combatActionBarTimestamp = System.currentTimeMillis();
    }

    public static Text getCombatActionBar() {
        if (combatActionBar != null && System.currentTimeMillis() - combatActionBarTimestamp < 2000) {
            return combatActionBar;
        }
        return null;
    }

    public static void setRegularActionBar(Text message) {
        regularActionBar = message;
        regularActionBarTimestamp = System.currentTimeMillis();
    }

    public static Text getRegularActionBar() {
        if (regularActionBar != null && System.currentTimeMillis() - regularActionBarTimestamp < 2000) {
            return regularActionBar;
        }
        return null;
    }
} 