# Değişiklikler / Changelog

## 2.0.0

### Türkçe

- **Bağlantı kodu ile kurulum:** `/crafter connect <kod>`. Website ID, secret ya da server ID kopyalanmaz. Sunucuya
  özel token (`cct_…`) `config.yml` dosyasına yalnızca sunucu kullanıcısının okuyabileceği izinle yazılır; loglanmaz.
- Yeni komutlar: `/crafter connect`, `/crafter disconnect`; `/crafter status` mağaza, sunucu ID, sürüm ve bekleyen
  komutları gösterir.
- **BungeeCord / Waterfall** desteği (yeni modül). Paper/Spigot için **Folia** uyumu.
- **Bedrock (Geyser/Floodgate):** Floodgate öneki okunur ve Crafter'a bildirilir; önekli ve boşluklu Bedrock adları
  teslim edilir.
- **Kalıcı kuyruk:** çevrimdışı oyuncu komutları, çalıştırılan komut id'leri ve gönderilemeyen onaylar
  `data/commands.json` dosyasında saklanır. Aynı komut iki kez çalışmaz.
- Komutlar ana thread'i bekletmeden sırayla çalışır; yeniden bağlanma üstel bekleme ile yapılır.
- Komut koruması: izinli komut önekleri (yerel + panel), kontrol karakteri ve oyuncu adı kontrolü.
- Sunucu her 30 saniyede durumunu (çevrimiçi oyuncu) bildirir.
- 1.x'teki "yeni sürümden 7 gün sonra eklentiyi kapatma" kaldırıldı; asgari sürümün altındaysa konsolda uyarı gösterilir.
- Eski kurulumlar (`website-id`, `plugin-secret`, `server-id`) güncellemeden sonra çalışmaya devam eder.
- **Oynama süresi (Paper/Spigot):** süre oyunun kendi istatistiğinden (`PLAY_ONE_MINUTE`) 5 dakikada bir, girişte
  ve çıkışta gönderilir; AFK süresi ayrı sayılır (Essentials, CMI ya da hareketsizlik eşiği). Bağlantı yokken
  `data/playtime.json` dosyasında bekler; çökmede süre kaybolmaz ya da şişmez. Velocity/BungeeCord süre göndermez.
  Ayarlar: `playtime.*`.

### English

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
