package dev.alfxyz.leoneclient;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;

public class LeoneClientConfig {
    private static int regularActionBarY = 60;
    private static int combatActionBarY = 70;
    private static String autoJoinServer = "Remain in hub";
    private static boolean mergeActionBars = false;
    private static int afkTimeoutMinutes = 5;
    private static boolean afkEnabled = true;
    private static boolean cooldownNotifyActionBar = true;
    private static boolean cooldownNotifyChat = true;
    private static boolean cooldownCountdownActionBar = true;
    private static boolean cooldownReadyActionBar = true;
    private static boolean cooldownCountdownChat = false;
    private static boolean cooldownReadyChat = true;
    private static boolean actionBarEnabled = true;

    private static final File CONFIG_FILE = new File(FabricLoader.getInstance().getConfigDir().toFile(), "leoneclient.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static {
        load();
    }

    public static int getRegularActionBarY() {
        return regularActionBarY;
    }
    public static int getCombatActionBarY() {
        return combatActionBarY;
    }
    public static String getAutoJoinServer() {
        return autoJoinServer;
    }
    public static boolean getMergeActionBars() {
        return mergeActionBars;
    }
    public static int getAfkTimeoutMinutes() { return afkTimeoutMinutes; }
    public static boolean isAfkEnabled() { return afkEnabled; }
    public static boolean isCooldownNotifyActionBar() { return cooldownNotifyActionBar; }
    public static boolean isCooldownNotifyChat() { return cooldownNotifyChat; }
    public static boolean isCooldownCountdownActionBar() { return cooldownCountdownActionBar; }
    public static boolean isCooldownReadyActionBar() { return cooldownReadyActionBar; }
    public static boolean isCooldownCountdownChat() { return cooldownCountdownChat; }
    public static boolean isCooldownReadyChat() { return cooldownReadyChat; }
    public static boolean isActionBarEnabled() { return actionBarEnabled; }
    public static void setRegularActionBarY(int y) {
        regularActionBarY = y;
        save();
    }
    public static void setCombatActionBarY(int y) {
        combatActionBarY = y;
        save();
    }
    public static void setAutoJoinServer(String server) {
        autoJoinServer = server;
        save();
    }
    public static void setMergeActionBars(boolean merge) {
        mergeActionBars = merge;
        save();
    }
    public static void setAfkTimeoutMinutes(int min) { afkTimeoutMinutes = min; save(); }
    public static void setAfkEnabled(boolean enabled) { afkEnabled = enabled; save(); }
    public static void setCooldownNotifyActionBar(boolean enabled) { cooldownNotifyActionBar = enabled; save(); }
    public static void setCooldownNotifyChat(boolean enabled) { cooldownNotifyChat = enabled; save(); }
    public static void setCooldownCountdownActionBar(boolean enabled) { cooldownCountdownActionBar = enabled; save(); }
    public static void setCooldownReadyActionBar(boolean enabled) { cooldownReadyActionBar = enabled; save(); }
    public static void setCooldownCountdownChat(boolean enabled) { cooldownCountdownChat = enabled; save(); }
    public static void setCooldownReadyChat(boolean enabled) { cooldownReadyChat = enabled; save(); }
    public static void setActionBarEnabled(boolean enabled) { actionBarEnabled = enabled; save(); }

    public static void load() {
        if (CONFIG_FILE.exists()) {
            try (FileReader reader = new FileReader(CONFIG_FILE)) {
                ConfigData data = GSON.fromJson(reader, ConfigData.class);
                if (data != null) {
                    regularActionBarY = data.regularActionBarY;
                    combatActionBarY = data.combatActionBarY;
                    autoJoinServer = data.autoJoinServer;
                    mergeActionBars = data.mergeActionBars;
                    if (data.afkTimeoutMinutes > 0) afkTimeoutMinutes = data.afkTimeoutMinutes;
                    afkEnabled = data.afkEnabled;
                    cooldownNotifyActionBar = data.cooldownNotifyActionBar;
                    cooldownNotifyChat = data.cooldownNotifyChat;
                    cooldownCountdownActionBar = data.cooldownCountdownActionBar;
                    cooldownReadyActionBar = data.cooldownReadyActionBar;
                    cooldownCountdownChat = data.cooldownCountdownChat;
                    cooldownReadyChat = data.cooldownReadyChat;
                    actionBarEnabled = data.actionBarEnabled;
                }
            } catch (IOException ignored) {}
        }
    }

    public static void save() {
        try (FileWriter writer = new FileWriter(CONFIG_FILE)) {
            ConfigData data = new ConfigData();
            data.regularActionBarY = regularActionBarY;
            data.combatActionBarY = combatActionBarY;
            data.autoJoinServer = autoJoinServer;
            data.mergeActionBars = mergeActionBars;
            data.afkTimeoutMinutes = afkTimeoutMinutes;
            data.afkEnabled = afkEnabled;
            data.cooldownNotifyActionBar = cooldownNotifyActionBar;
            data.cooldownNotifyChat = cooldownNotifyChat;
            data.cooldownCountdownActionBar = cooldownCountdownActionBar;
            data.cooldownReadyActionBar = cooldownReadyActionBar;
            data.cooldownCountdownChat = cooldownCountdownChat;
            data.cooldownReadyChat = cooldownReadyChat;
            data.actionBarEnabled = actionBarEnabled;
            GSON.toJson(data, writer);
        } catch (IOException ignored) {}
    }

    private static class ConfigData {
        int regularActionBarY = 60;
        int combatActionBarY = 70;
        String autoJoinServer = "Remain in hub";
        boolean mergeActionBars = false;
        int afkTimeoutMinutes = 5;
        boolean afkEnabled = true;
        boolean cooldownNotifyActionBar = true;
        boolean cooldownNotifyChat = true;
        boolean cooldownCountdownActionBar = true;
        boolean cooldownReadyActionBar = true;
        boolean cooldownCountdownChat = false;
        boolean cooldownReadyChat = true;
        boolean actionBarEnabled = true;
    }
} 