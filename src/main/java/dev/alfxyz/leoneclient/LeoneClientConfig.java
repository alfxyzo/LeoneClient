package dev.alfxyz.leoneclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import net.fabricmc.loader.api.FabricLoader;

public class LeoneClientConfig {
    private static int regularActionBarY = 49;
    private static int combatActionBarY = 65;
    private static String autoJoinServer = "Remain in hub";
    private static boolean mergeActionBars = false;
    private static boolean actionBarEnabled = true;
    private static boolean combatTimerWidget = true;
    private static boolean hideCombatBarWhenEffect = false;
    private static boolean hideChatAlerts = true;
    private static boolean antiMuteEnabled = true;
    private static String antiMuteReplacement = "[redacted]";
    private static boolean filterDiscrimination = true;
    private static boolean filterDeathWishes = true;
    private static boolean filterSwears = true;
    private static boolean filterAdvertisements = true;

    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "leoneclient.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static { load(); }

    public static int getRegularActionBarY() { return regularActionBarY; }
    public static int getCombatActionBarY() { return combatActionBarY; }
    public static String getAutoJoinServer() { return autoJoinServer; }
    public static boolean getMergeActionBars() { return mergeActionBars; }
    public static boolean isActionBarEnabled() { return actionBarEnabled; }
    public static boolean isCombatTimerWidget() { return combatTimerWidget; }
    public static boolean isHideCombatBarWhenEffect() { return hideCombatBarWhenEffect; }
    public static boolean isHideChatAlerts() { return hideChatAlerts; }
    public static boolean isAntiMuteEnabled() { return antiMuteEnabled; }
    public static String getAntiMuteReplacement() { return antiMuteReplacement; }
    public static boolean isFilterDiscrimination() { return filterDiscrimination; }
    public static boolean isFilterDeathWishes() { return filterDeathWishes; }
    public static boolean isFilterSwears() { return filterSwears; }
    public static boolean isFilterAdvertisements() { return filterAdvertisements; }

    public static void setRegularActionBarY(int y) { regularActionBarY = Math.max(1, y); save(); }
    public static void setCombatActionBarY(int y) { combatActionBarY = Math.max(1, y); save(); }
    public static void setAutoJoinServer(String server) { autoJoinServer = server; save(); }
    public static void setMergeActionBars(boolean merge) { mergeActionBars = merge; save(); }
    public static void setActionBarEnabled(boolean e) { actionBarEnabled = e; save(); }
    public static void setCombatTimerWidget(boolean e) { combatTimerWidget = e; save(); }
    public static void setHideCombatBarWhenEffect(boolean e) { hideCombatBarWhenEffect = e; save(); }
    public static void setHideChatAlerts(boolean e) { hideChatAlerts = e; save(); }
    public static void setAntiMuteEnabled(boolean e) { antiMuteEnabled = e; save(); }
    public static void setAntiMuteReplacement(String s) { antiMuteReplacement = s.isEmpty() ? "[redacted]" : s; save(); }
    public static void setFilterDiscrimination(boolean e) { filterDiscrimination = e; save(); }
    public static void setFilterDeathWishes(boolean e) { filterDeathWishes = e; save(); }
    public static void setFilterSwears(boolean e) { filterSwears = e; save(); }
    public static void setFilterAdvertisements(boolean e) { filterAdvertisements = e; save(); }

    public static void load() {
        if (!CONFIG_FILE.exists()) return;
        try (FileReader reader = new FileReader(CONFIG_FILE)) {
            ConfigData data = GSON.fromJson(reader, ConfigData.class);
            if (data == null) return;
            regularActionBarY = data.regularActionBarY;
            combatActionBarY = data.combatActionBarY;
            autoJoinServer = data.autoJoinServer;
            mergeActionBars = data.mergeActionBars;
            actionBarEnabled = data.actionBarEnabled;
            combatTimerWidget = data.combatTimerWidget;
            hideCombatBarWhenEffect = data.hideCombatBarWhenEffect;
            hideChatAlerts = data.hideChatAlerts;
            antiMuteEnabled = data.antiMuteEnabled;
            if (data.antiMuteReplacement != null && !data.antiMuteReplacement.isEmpty())
                antiMuteReplacement = data.antiMuteReplacement;
            filterDiscrimination = data.filterDiscrimination;
            filterDeathWishes = data.filterDeathWishes;
            filterSwears = data.filterSwears;
            filterAdvertisements = data.filterAdvertisements;
        } catch (IOException ignored) {}
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            ConfigData data = new ConfigData();
            data.regularActionBarY = regularActionBarY;
            data.combatActionBarY = combatActionBarY;
            data.autoJoinServer = autoJoinServer;
            data.mergeActionBars = mergeActionBars;
            data.actionBarEnabled = actionBarEnabled;
            data.combatTimerWidget = combatTimerWidget;
            data.hideCombatBarWhenEffect = hideCombatBarWhenEffect;
            data.hideChatAlerts = hideChatAlerts;
            data.antiMuteEnabled = antiMuteEnabled;
            data.antiMuteReplacement = antiMuteReplacement;
            data.filterDiscrimination = filterDiscrimination;
            data.filterDeathWishes = filterDeathWishes;
            data.filterSwears = filterSwears;
            data.filterAdvertisements = filterAdvertisements;
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }

    private static class ConfigData {
        int regularActionBarY = 49;
        int combatActionBarY = 65;
        String autoJoinServer = "Remain in hub";
        boolean mergeActionBars = false;
        boolean actionBarEnabled = true;
        boolean combatTimerWidget = true;
        boolean hideCombatBarWhenEffect = false;
        boolean hideChatAlerts = true;
        boolean antiMuteEnabled = true;
        String antiMuteReplacement = "[redacted]";
        boolean filterDiscrimination = true;
        boolean filterDeathWishes = true;
        boolean filterSwears = true;
        boolean filterAdvertisements = true;
    }
}
