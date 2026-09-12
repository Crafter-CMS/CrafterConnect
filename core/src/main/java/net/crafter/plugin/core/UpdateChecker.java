package net.crafter.plugin.core;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Consumer;

public class UpdateChecker {

    public static final long EXPIRATION_GRACE_PERIOD_DAYS = 7;
    public static final String DEFAULT_REPO = "Crafter-CMS/CrafterConnect";
    public static final String RELEASES_URL = "https://github.com/" + DEFAULT_REPO + "/releases";

    private final String currentVersion;
    private final String repo;

    public UpdateChecker(String currentVersion) {
        this(currentVersion, DEFAULT_REPO);
    }

    public UpdateChecker(String currentVersion, String repo) {
        this.currentVersion = currentVersion;
        this.repo = repo;
    }

    public void checkForUpdates(Consumer<UpdateCheckResult> resultConsumer) {
        new Thread(() -> {
            UpdateCheckResult result = checkSync();
            if (resultConsumer != null) {
                resultConsumer.accept(result);
            }
        }, "crafter-update-checker").start();
    }

    public UpdateCheckResult checkSync() {
        try {
            HttpURLConnection connection = (HttpURLConnection) new URL("https://api.github.com/repos/" + repo + "/releases/latest").openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "application/vnd.github.v3+json");
            connection.setRequestProperty("User-Agent", "CrafterConnect-UpdateChecker");
            connection.setConnectTimeout(6000);
            connection.setReadTimeout(6000);

            if (connection.getResponseCode() == HttpURLConnection.HTTP_OK) {
                JsonObject json = JsonParser.parseReader(new InputStreamReader(connection.getInputStream())).getAsJsonObject();
                return parseReleaseJson(json, currentVersion, Instant.now());
            }
        } catch (Exception ignored) {
            // Ağ kesintisi veya rate-limit durumunda eklentinin çalışmasını bozma
        }
        return UpdateCheckResult.upToDate(currentVersion);
    }

    public static UpdateCheckResult parseReleaseJson(JsonObject json, String currentVersion, Instant now) {
        if (json == null || !json.has("tag_name")) {
            return UpdateCheckResult.upToDate(currentVersion);
        }

        String rawLatest = json.get("tag_name").getAsString();
        String downloadUrl = json.has("html_url") ? json.get("html_url").getAsString() : RELEASES_URL;

        if (!isNewerVersion(rawLatest, currentVersion)) {
            return UpdateCheckResult.upToDate(currentVersion);
        }

        Instant publishedAt = null;
        if (json.has("published_at") && !json.get("published_at").isJsonNull()) {
            try {
                publishedAt = Instant.parse(json.get("published_at").getAsString());
            } catch (Exception ignored) {}
        }

        long daysElapsed = 0;
        boolean expired = false;
        if (publishedAt != null) {
            Duration duration = Duration.between(publishedAt, now);
            daysElapsed = Math.max(0, duration.toDays());
            expired = daysElapsed >= EXPIRATION_GRACE_PERIOD_DAYS;
        }

        return new UpdateCheckResult(true, currentVersion, rawLatest, publishedAt, daysElapsed, expired, downloadUrl);
    }

    public static boolean isNewerVersion(String latest, String current) {
        if (latest == null || current == null) return false;

        String v1 = normalizeVersion(latest);
        String v2 = normalizeVersion(current);

        String[] parts1 = v1.split("[.-]");
        String[] parts2 = v2.split("[.-]");

        int length = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < length; i++) {
            int num1 = i < parts1.length ? parseNumber(parts1[i]) : 0;
            int num2 = i < parts2.length ? parseNumber(parts2[i]) : 0;

            if (num1 > num2) return true;
            if (num1 < num2) return false;
        }

        return false;
    }

    private static String normalizeVersion(String v) {
        String trimmed = v.trim();
        if (trimmed.startsWith("v") || trimmed.startsWith("V")) {
            trimmed = trimmed.substring(1);
        }
        return trimmed;
    }

    private static int parseNumber(String s) {
        try {
            return Integer.parseInt(s.replaceAll("\\D+", ""));
        } catch (Exception e) {
            return 0;
        }
    }
}
