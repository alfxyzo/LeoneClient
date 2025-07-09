package dev.alfxyz.leoneclient.api;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.alfxyz.leoneclient.LeoneClient;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.scoreboard.Scoreboard;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class Minehut {
    public static Map<String, String> playerRank = new HashMap<>();

    public static Boolean inLobby() {
        if (MinecraftClient.getInstance().player == null) return null;

        
        Scoreboard scoreboard = MinecraftClient.getInstance().player.getScoreboard();
        ArrayList<String> scores = new ArrayList<>();
        for (ScoreboardObjective objective : scoreboard.getObjectives()) {
            scores.add(objective.getDisplayName().toString());
        }

        return scores.toString().toLowerCase().contains("minehut");
    }

    
    @Deprecated
    public static JsonObject getServer(ClientPlayerEntity player, String serverName) {
        try {
            URL apiURL = new URL("https://api.minehut.com/server/" + serverName + "?byName=true");
            HttpURLConnection connection = (HttpURLConnection) apiURL.openConnection();
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() == 500) {
                player.sendMessage(Text.literal("Server not found! (" + connection.getResponseCode() + ")").styled(style -> style
                        .withColor(Formatting.RED)), false);
                return null;
            } else if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) { 
                player.sendMessage(Text.literal("Server not found! API may be down. (" + connection.getResponseCode() + ")").styled(style -> style
                        .withColor(Formatting.RED)), false);
                return null;
            }

            JsonObject obj = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject().get("server").getAsJsonObject();

            connection.disconnect();

            return obj;
        } catch (Exception e) {
            LeoneClient.log.error("Error while looking up server: {} - {}", serverName, e);
            player.sendMessage(Text.literal("Response to minehut api was unsuccessful. Server name: " + serverName), false);
            return null;
        }
    }

    
    @Deprecated
    public static String getRank(String uuid) {
        try {
            
            if (playerRank.containsKey(uuid)) {
                
                return playerRank.get(uuid);
            }

            URL apiURL = new URI("https://api.minehut.com/users/" + uuid).toURL();
            HttpURLConnection connection = (HttpURLConnection) apiURL.openConnection();
            connection.setRequestMethod("GET");

            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                LeoneClient.log.error("Player ({}) not found! ({})", uuid, connection.getResponseCode());
                return null;
            }

            BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));

            String json;
            json = in.readLine();

            in.close();

            connection.disconnect();

            
            int start = json.indexOf("rank\":\"") + 7;
            int end = json.indexOf("\",\"", start);

            String rank = json.substring(start, end);
            playerRank.put(uuid, rank);

            return rank;
        } catch (Exception e) {
            LeoneClient.log.error("Response to minehut api was unsuccessful. Player uuid: {} {}", uuid, e);
            return null;
        }
    }
}
