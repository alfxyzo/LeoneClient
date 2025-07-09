package dev.alfxyz.leoneclient.utils;

import java.util.HashMap;
import java.util.Map;

public class CooldownManager {
    public enum CustomItem {
        CAGE,
        COBWEB_CIRCLE,
        PULL_PUSH_WEAPON_RIGHT,
        PULL_PUSH_WEAPON_LEFT
    }

    private static final Map<CustomItem, Long> cooldownEndTimes = new HashMap<>();
    private static final Map<CustomItem, Long> cooldownDurations = new HashMap<>();
    static {
        cooldownDurations.put(CustomItem.CAGE, 3 * 60 * 1000L); // 3 minutes
        cooldownDurations.put(CustomItem.COBWEB_CIRCLE, 2 * 60 * 1000L); // 2 minutes
        cooldownDurations.put(CustomItem.PULL_PUSH_WEAPON_RIGHT, 2 * 60 * 1000L); // 2 minutes
        cooldownDurations.put(CustomItem.PULL_PUSH_WEAPON_LEFT, 20 * 1000L); // 20 seconds
    }

    public static void startCooldown(CustomItem item) {
        long now = System.currentTimeMillis();
        cooldownEndTimes.put(item, now + cooldownDurations.get(item));
    }

    public static long getRemaining(CustomItem item) {
        long now = System.currentTimeMillis();
        return Math.max(0, cooldownEndTimes.getOrDefault(item, 0L) - now);
    }

    public static boolean isReady(CustomItem item) {
        return getRemaining(item) == 0;
    }

    public static void clearCooldown(CustomItem item) {
        cooldownEndTimes.remove(item);
    }

    public static void resetCooldown(CustomItem item) {
        setCooldown(item, 0);
    }

    public static void setCooldown(CustomItem item, long ms) {
        long now = System.currentTimeMillis();
        cooldownEndTimes.put(item, now + ms);
    }

    public static String formatTime(long ms) {
        long seconds = ms / 1000;
        long min = seconds / 60;
        long sec = seconds % 60;
        if (min > 0) return String.format("%d:%02d", min, sec);
        return String.format("%ds", sec);
    }
} 