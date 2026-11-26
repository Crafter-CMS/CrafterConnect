package net.crafter.plugin.core;

/** Result of comparing with the latest version on GitHub Releases (informational only; never disables the plugin) */
public final class UpdateCheckResult {
    private final boolean hasUpdate;
    private final String currentVersion;
    private final String latestVersion;
    private final String downloadUrl;

    public UpdateCheckResult(boolean hasUpdate, String currentVersion, String latestVersion, String downloadUrl) {
        this.hasUpdate = hasUpdate;
        this.currentVersion = currentVersion;
        this.latestVersion = latestVersion;
        this.downloadUrl = downloadUrl;
    }

    public static UpdateCheckResult upToDate(String currentVersion) {
        return new UpdateCheckResult(false, currentVersion, currentVersion, UpdateChecker.RELEASES_URL);
    }

    public boolean hasUpdate() { return hasUpdate; }
    public String getCurrentVersion() { return currentVersion; }
    public String getLatestVersion() { return latestVersion; }
    public String getDownloadUrl() { return downloadUrl; }
}
