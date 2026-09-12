package net.crafter.plugin.core;

import java.time.Instant;

public class UpdateCheckResult {

    private final boolean hasUpdate;
    private final String currentVersion;
    private final String latestVersion;
    private final Instant publishedAt;
    private final long daysElapsed;
    private final boolean expired;
    private final String downloadUrl;

    public UpdateCheckResult(boolean hasUpdate, String currentVersion, String latestVersion, Instant publishedAt, long daysElapsed, boolean expired, String downloadUrl) {
        this.hasUpdate = hasUpdate;
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.publishedAt = publishedAt;
        this.daysElapsed = daysElapsed;
        this.expired = expired;
        this.downloadUrl = downloadUrl;
    }

    public static UpdateCheckResult upToDate(String currentVersion) {
        return new UpdateCheckResult(false, currentVersion, currentVersion, null, 0, false, null);
    }

    public boolean hasUpdate() {
        return hasUpdate;
    }

    public String getCurrentVersion() {
        return currentVersion;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public long getDaysElapsed() {
        return daysElapsed;
    }

    public boolean isExpired() {
        return expired;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }
}
