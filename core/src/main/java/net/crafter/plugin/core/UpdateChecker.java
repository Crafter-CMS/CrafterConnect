package net.crafter.plugin.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.function.Consumer;

public class UpdateChecker {

    private final String currentVersion;
    private final String repo = "Crafter-CMS/CrafterConnect";

    public UpdateChecker(String currentVersion) {
        this.currentVersion = currentVersion;
    }

    public void checkForUpdates(Consumer<String> resultConsumer) {
        new Thread(() -> {
            try {
                HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                    JsonObject json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
                    String latestVersion = json.get("tag_name").getAsString();
                    
                    // "v1.0.0" -> "1.0.0" dönüşümü gerekebilir
                    String normalizedLatest = latestVersion.startsWith("v") ? latestVersion.substring(1) : latestVersion;
                    String normalizedCurrent = currentVersion.startsWith("v") ? currentVersion.substring(1) : currentVersion;

                    if (!normalizedCurrent.equalsIgnoreCase(normalizedLatest)) {
                        resultConsumer.accept(latestVersion);
                    }
                }
            } catch (Exception ignored) {
                // Hata durumunda sessizce geç (update checker kritik değil)
            }
        }).start();
    }
}
