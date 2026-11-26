# CrafterConnect

CrafterConnect, **crafter.net.tr** altyapısını kullanan oyun sunucuları için geliştirilmiş, yüksek performanslı ve gerçek zamanlı bir teslimat (delivery), mağaza ve sandık entegrasyon eklentisidir.

## ⚠️ Önemli Güvenlik Uyarısı (Kritik)

CrafterConnect, oyuncuları sunucuya giriş yaptıkları **kullanıcı isimleri (username)** üzerinden doğrudan tanır ve işlem yapar. Bu nedenle:

- **Premium Sunucular:** Eklenti, yalnızca orijinal (Premium) sunucularda tam güvenlik sağlar.
- **Cracked Sunucular:** Eğer sunucunuz "cracked" ise, bu eklentiyi **mutlaka** `AuthMe`, `LimboAuth` veya benzeri bir yetkilendirme eklentisinin koruması altında kullanmalısınız.
- **Risk:** Yetkilendirme koruması olmayan sunucularda, bir oyuncu başkasının ismiyle giriş yaparak o oyuncunun sandığındaki eşyaları teslim alabilir veya kredisini harcayabilir. Bu durumdan Crafter sorumlu tutulamaz.

---

## 🚀 Kurulum ve Kullanım (2.0)

### 1. Eklentiyi yükleme

| Platform | Dosya | Klasör |
| :-- | :-- | :-- |
| Paper / Spigot / Folia (1.18+) | `crafter-connect-spigot-2.0.0.jar` | sunucunun `plugins/` klasörü |
| Velocity | `crafter-connect-velocity-2.0.0.jar` | proxy'nin `plugins/` klasörü |
| BungeeCord / Waterfall | `crafter-connect-bungee-2.0.0.jar` | proxy'nin `plugins/` klasörü |

Proxy'de teslimat komutları proxy konsolunda çalışır. Arka sunucuda çalışması gereken komutlar (ör. `give`) için
arka sunuculara da Paper eklentisini kurun; her kurulum Crafter'da ayrı bir sunucu olarak bağlanır. Proxy'de de
eklenti varsa arka sunuculardaki `config.yml` içinde `report-players: false` yapın (oyuncular iki kez sayılmaz).

### 2. Bağlama

1. Panelde **Ayarlar → Sunucular → Sunucu bağla** ile bağlantı kodu üretin (15 dakika geçerli, tek kullanımlık).
2. Konsolda (ya da `crafter.admin` yetkisiyle oyunda) çalıştırın:

```text
/crafter connect K7QM-4XPD
```

Eklenti kendini kaydeder, sunucuya özel **sunucu token'ını** (`cct_…`) `config.yml` dosyasına yazar ve dosyanın
iznini yalnızca sunucu kullanıcısına (600) düşürür. Token loglara ve sohbete yazılmaz. Website ID, secret ya da
server ID kopyalamanız gerekmez. Makine değişince ya da token sızdıysa panelde **Yeniden bağla** ile yeni kod üretip
aynı komutu çalıştırın; eski token hemen geçersiz olur.

**Eski kurulumlar:** `website-id`, `plugin-secret`, `server-id` dolu ve `server-token` boşsa eklenti eski yöntemle
bağlanmaya devam eder ve konsolda "Panelden yeni kod üretip /crafter connect çalıştırın" uyarısı gösterir.

### 3. Yapılandırma (`config.yml`)

```yaml
api-url: "api.crafter.net.tr"
server-token: ""                 # /crafter connect yazar; elle girmeyin, paylaşmayın
language: "tr"                   # tr veya en
allowed-command-prefixes: []     # doluysa yalnızca bu öneklerle başlayan komutlar çalışır (örn. ["lp user", "give"])
report-players: true             # proxy arkasındaki sunucularda false
debug-mode: false
```

### 4. Komutlar ve yetkiler

| Komut | Yetki | Açıklama |
| :-- | :-- | :-- |
| `/crafter connect <kod>` | `crafter.admin` | Sunucuyu bağlantı koduyla Crafter'a bağlar (konsoldan da çalışır). |
| `/crafter disconnect` | `crafter.admin` | Bağlantıyı keser ve sunucu token'ını siler. |
| `/crafter status` | `crafter.admin` | Bağlantı, mağaza, sunucu ID, sürüm ve bekleyen komutlar. |
| `/crafter reload` | `crafter.admin` | Ayar ve dil dosyalarını yeniler, yeniden bağlanır. |
| `/crafter magaza [kategori]` | `crafter.shop` | Oyun içi mağaza (Paper/Spigot). |
| `/crafter sandik` | `crafter.chest` | Satın alınan ürünlerin sandığı (Paper/Spigot). |

> [!NOTE]
> Varsayılan olarak tüm oyunculara yetki vermek isterseniz, LuckPerms gibi bir plugin ile `crafter.shop` ve `crafter.chest` yetkilerini `default` grubuna ekleyebilirsiniz.

### 5. Teslimat ve güvenlik

- **Kalıcı kuyruk:** Oyuncu çevrimdışıysa komut `plugins/CrafterConnect/data/commands.json` dosyasında bekler ve
  oyuncu girince çalışır (ad büyük/küçük harf duyarsız; Microsoft ile doğrulanmış oyuncuda UUID ile). Sunucu kapalıyken
  gelen komutları Crafter saklar ve sunucu bağlanınca sırayla gönderir.
- **Bir kez çalıştırma:** Her komutun `commandId`'si saklanır; aynı komut tekrar gelirse çalıştırılmaz, yalnızca onay
  yinelenir. Onaylar bağlantı yokken diske yazılır ve bağlanınca gönderilir.
- **Komut koruması:** Oyuncu adı Java (`[A-Za-z0-9_]{1,16}`) ya da Bedrock/Floodgate (`.` önekli, kelimeler arasında
  tek boşluk) kuralına uymalı; tırnak, `; & | $ @ %`, satır sonu ve kontrol karakteri içeren komut çalışmaz.
  `allowed-command-prefixes` ve paneldeki liste doluysa komut ikisine de uymalı. Reddedilen komut Crafter'a bildirilir ve
  sipariş incelemeye düşer.
- Komutlar konsol yetkisiyle ana thread'de (Folia'da global bölge zamanlayıcısında) çalışır.
- **Bedrock (Geyser/Floodgate):** Floodgate kuruluysa öneki ondan okunur ve Crafter'a bildirilir.

---

## 🏗️ Sistem Özellikleri

- **Real-time Delivery:** Satın alımlar sayfa yenilemeye gerek kalmadan anında sunucuya iletilir.
- **Gelişmiş GUI:** Dinamik olarak hesaplanan, kategorize edilmiş ve şık tasarımlı mağaza menüsü.
- **Sandık Sistemi:** Web sitesinden alınan ürünler sunucuda güvenle saklanır, oyuncu istediği zaman `/sandik` ile teslim alır.
- **%100 Yerelleştirme:** Tüm mesajlar, buton isimleri ve placeholder çıktıları `lang/tr.yml` veya `lang/en.yml` üzerinden düzenlenebilir.

---

## 📊 PlaceholderAPI Entegrasyonu

Eklentimiz PlaceholderAPI (PAPI) ile tam entegre çalışır. Bu sayede web sitenizdeki verileri Scoreboard, TabList veya Hologram gibi diğer eklentilerde kullanabilirsiniz.

### Kullanılabilir Placeholderlar

| Placeholder                              | Açıklama                       | Örnek Çıktı            |
| :--------------------------------------- | :----------------------------- | :--------------------- |
| `%crafterconnect_balance%`               | Oyuncunun güncel kredisi       | `150.00 Balance`       |
| `%crafterconnect_role%`                  | Oyuncunun web sitesindeki rolü | `VIP` veya `Ziyaretçi` |
| `%crafterconnect_user_id%`               | Oyuncunun benzersiz ID'si      | `839f27e4-...`         |
| `%crafterconnect_total_users%`           | Sitedeki toplam kayıtlı üye    | `1,245`                |
| `%crafterconnect_last_purchase_user%`    | Son alışveriş yapan kişi       | `efesoroglu`           |
| `%crafterconnect_last_purchase_product%` | Son satın alınan ürün adı      | `VIP+ Paketi`          |
| `%crafterconnect_top_loader_user%`       | En çok kredi yükleyen kişi     | `Ahmet123`             |
| `%crafterconnect_top_loader_amount%`     | En çok yüklenen miktar         | `500.00`               |

> [!TIP]
> Bakiye formatı ve varsayılan metinler (Ziyaretçi vb.) `lang/tr.yml` içerisinden tamamen özelleştirilebilir.

### Nasıl Kullanılır?

1. Sunucunuzda **PlaceholderAPI** eklentisinin yüklü olduğundan emin olun.
2. CrafterConnect yüklendiğinde otomatik olarak PAPI'ye kayıt olacaktır.
3. Herhangi bir uyumlu eklentide (örn: DeluxeMenus, TAB, FeatherBoard) yukarıdaki değişkenleri kullanmaya başlayabilirsiniz.

---

## 🛠️ Teknik Altyapı

- **Asenkron Mimari:** Tüm ağ işlemleri ana sunucu thread'ini yormadan (TPS etkilemeden) çalışır.
- **Çift Yönlü İletişim:** Sadece sunucu backend'den veri çekmez, backend de sunucuya anlık paketler gönderir.
- **Maven Modülleri:** `core` (Ortak Mantık), `spigot` (Oyun içi UI), `velocity` (Proxy Desteği).

**Derleme Komutu:**

```bash
mvn clean package
```

---

## 📜 Lisans

Bu proje **MIT Lisansı** ile lisanslanmıştır. Detaylar için `LICENSE` dosyasına göz atabilirsiniz.
