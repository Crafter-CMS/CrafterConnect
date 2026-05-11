# CrafterConnect

CrafterConnect, **crafter.net.tr** altyapısını kullanan oyun sunucuları için geliştirilmiş, yüksek performanslı ve gerçek zamanlı bir teslimat (delivery), mağaza ve sandık entegrasyon eklentisidir.

## ⚠️ Önemli Güvenlik Uyarısı (Kritik)

CrafterConnect, oyuncuları sunucuya giriş yaptıkları **kullanıcı isimleri (username)** üzerinden doğrudan tanır ve işlem yapar. Bu nedenle:

- **Premium Sunucular:** Eklenti, yalnızca orijinal (Premium) sunucularda tam güvenlik sağlar.
- **Cracked Sunucular:** Eğer sunucunuz "cracked" ise, bu eklentiyi **mutlaka** `AuthMe`, `LimboAuth` veya benzeri bir yetkilendirme eklentisinin koruması altında kullanmalısınız.
- **Risk:** Yetkilendirme koruması olmayan sunucularda, bir oyuncu başkasının ismiyle giriş yaparak o oyuncunun sandığındaki eşyaları teslim alabilir veya kredisini harcayabilir. Bu durumdan Crafter sorumlu tutulamaz.

---

## 🚀 Kurulum ve Kullanım

### 1. Eklentiyi Yükleme

- **Spigot:** `spigot/target/crafter-connect-spigot-1.0.0.jar` dosyasını `plugins/` klasörüne kopyalayın.
- **Velocity:** `velocity/target/crafter-connect-velocity-1.0.0.jar` dosyasını `plugins/` klasörüne kopyalayın.

### 2. Yapılandırma (Config)

`plugins/CrafterConnect/config.yml` dosyasını panel bilgilerinizle düzenleyin:

```yaml
api-url: "api.crafter.net.tr"
website-id: "YOUR_WEBSITE_ID"
plugin-secret: "YOUR_SECRET_KEY"
server-id: "YOUR_SERVER_ID"
language: "tr" # tr veya en
```

### 3. Komutlar ve Kullanım

| Komut                          | Yetki           | Açıklama                                                        |
| :----------------------------- | :-------------- | :-------------------------------------------------------------- |
| `/crafter magaza`              | `crafter.user`  | Web sitenizdeki ürünleri oyun içinde kategori bazlı görüntüler. |
| `/crafter magaza <categoryId>` | `crafter.user`  | Doğrudan belirli bir kategoriyi açar.                           |
| `/crafter sandik`              | `crafter.user`  | Satın aldığınız ama henüz teslim almadığınız eşyaları listeler. |
| `/crafter status`              | `crafter.admin` | WebSocket bağlantı durumunu ve bekleyen komutları gösterir.     |
| `/crafter reload`              | `crafter.admin` | Yapılandırmayı ve dil dosyalarını yeniler.                      |

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

| Placeholder | Açıklama | Örnek Çıktı |
| :--- | :--- | :--- |
| `%crafterconnect_balance%` | Oyuncunun güncel kredisi | `150.00 Balance` |
| `%crafterconnect_role%` | Oyuncunun web sitesindeki rolü | `VIP` veya `Ziyaretçi` |
| `%crafterconnect_user_id%` | Oyuncunun benzersiz ID'si | `839f27e4-...` |
| `%crafterconnect_total_users%` | Sitedeki toplam kayıtlı üye | `1,245` |
| `%crafterconnect_last_purchase_user%` | Son alışveriş yapan kişi | `efesoroglu` |
| `%crafterconnect_last_purchase_product%` | Son satın alınan ürün adı | `VIP+ Paketi` |
| `%crafterconnect_top_loader_user%` | En çok kredi yükleyen kişi | `Ahmet123` |
| `%crafterconnect_top_loader_amount%` | En çok yüklenen miktar | `500.00` |

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
