package dev.alfxyz.leoneclient.api;

import dev.alfxyz.leoneclient.LeoneClient;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Minecraft {
    private static final Map<String, String> uuidOfPlayers = new HashMap<>();
    private static final long CACHE_EXPIRY_MINUTES = 30;
    private static final Map<String, Long> cacheTimestamps = new HashMap<>();

    public static String getUUID(String ign) {
        if (ign == null || ign.trim().isEmpty()) {
            LeoneClient.log.error("Invalid IGN provided: {}", ign);
            return null;
        }

        
        if (uuidOfPlayers.containsKey(ign)) {
            long timestamp = cacheTimestamps.getOrDefault(ign, 0L);
            if (System.currentTimeMillis() - timestamp < TimeUnit.MINUTES.toMillis(CACHE_EXPIRY_MINUTES)) {
                return uuidOfPlayers.get(ign);
            }
        }

        HttpURLConnection connection = null;
        try {
            URL apiURL = URI.create("https://api.mojang.com/users/profiles/minecraft/" + ign).toURL();
            connection = (HttpURLConnection) apiURL.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);

            int responseCode = connection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                LeoneClient.log.error("Player ({}) not found! ({})", ign, responseCode);
                return null;
            }

            try (BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()))) {
                String inputLine;
                StringBuilder response = new StringBuilder();
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }

                
                String json = response.toString().replaceAll(" ", "");
                int start = json.indexOf("id\":\"") + 5;
                if (start < 5) {
                    LeoneClient.log.error("Invalid API response format for player: {}", ign);
                    return null;
                }

                int end = json.indexOf("\",\"", start);
                String uuid = json.substring(start, end);

                
                uuidOfPlayers.put(ign, uuid);
                cacheTimestamps.put(ign, System.currentTimeMillis());

                return uuid;
            }
        } catch (Exception e) {
            LeoneClient.log.error("Failed to get UUID for player {} - {}", ign, e.toString());
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static String insertUUIDDashes(String uuid) {
        if (uuid == null || uuid.length() != 32) {
            return uuid;
        }
        return uuid.substring(0, 8) + "-" +
                uuid.substring(8, 12) + "-" +
                uuid.substring(12, 16) + "-" +
                uuid.substring(16, 20) + "-" +
                uuid.substring(20);
    }
}