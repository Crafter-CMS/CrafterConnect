# Changelog

## 2.0.0

- **Setup with a connection code:** `/crafter connect <code>`. No website ID, secret or server ID to copy. The
  per-server token (`cct_…`) is written to `config.yml` with owner-only permissions and is never logged.
- New commands: `/crafter connect`, `/crafter disconnect`; `/crafter status` shows the store, server ID, version and
  pending commands.
- **BungeeCord / Waterfall** support (new module). **Folia** support for Paper/Spigot.
- **Bedrock (Geyser/Floodgate):** the Floodgate prefix is read and reported to Crafter; prefixed Bedrock names,
  including names with spaces, are delivered.
- **Durable queue:** offline-player commands, executed command ids and unsent confirmations are stored in
  `data/commands.json`. The same command never runs twice.
- Commands run in order without blocking the main thread; reconnects use exponential backoff.
- Command guard: allowed command prefixes (local + dashboard), control characters and player name checks.
- The server reports its status (online players) every 30 seconds.
- The 1.x "disable the plugin 7 days after a new release" behaviour is removed; a console warning is shown when the
  version is below the minimum.
- Existing installs (`website-id`, `plugin-secret`, `server-id`) keep working after the update.
- **Playtime (Paper/Spigot):** taken from the game's own statistic (`PLAY_ONE_MINUTE`) every 5 minutes, on join and
  on quit; AFK time is counted separately (Essentials, CMI or an idle threshold). Queued in `data/playtime.json`
  while offline; a crash neither loses nor inflates playtime. Velocity/BungeeCord do not report playtime.
  Settings: `playtime.*`.
