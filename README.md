# 🛒 Market Rehberim

**Market Rehberim**, yerel odaklı bir **market fiyat karşılaştırma ve alışveriş
asistanı** uygulamasıdır. Aynı ürünü ulusal marketlerde (Migros, A101, ŞOK,
CarrefourSA) **ve** şehrinizdeki yerel marketlerde karşılaştırır, en ucuzunu bulur.

> Akakçe/Cimri'den farkı: **yerel/şehir bazlı odak**, **sepet optimizasyonu**,
> **crowdsourced (topluluk katkılı) fiyat** ve **kamera ile ürün tanıma**.
> Modern Android (MVVM, Hilt, Room, Coroutines/Flow, ML Kit) + temiz Flask backend.

---

## ✨ Özellikler

| Özellik | Açıklama |
|---------|----------|
| 🔍 **Birleşik arama** | Tek aramada ulusal + yerel marketler; "EN UCUZ" rozeti |
| 🎚️ **Filtrele / sırala** | Markete göre filtre, fiyat/isim sıralaması |
| 📸 **Kamera ile tanıma** | Ürün fotoğrafı → ML Kit nesne tanıma → Türkçe otomatik arama |
| 🛒 **Sepet optimizasyonu** | Liste → her marketin toplamı **+** ürün bazlı en ucuz dağıtım |
| 📸 **Crowdsourced fiyat (OCR)** | Raf etiketi fotoğrafı → ML Kit metin tanıma → fiyat gönder |
| 🏙️ **Şehir seçimi** | Yerel market kapsamını belirler (Tokat, İstanbul, Ankara…) |
| ⭐ **Favoriler + geçmiş** | Room ile yerel kalıcılık; anasayfada gösterim |
| 📉 **Fiyat geçmişi** | Kütüphanesiz özel çizgi grafik (`LineChartView`) |
| 🔔 **Fiyat düşüşü bildirimi** | WorkManager ile günlük takip; favori ucuzlayınca bildirim |

---

## 📐 Mimari

```
┌──────────────────────────────┐        HTTP/JSON        ┌────────────────────────────┐
│      Android (MVVM)          │  ───────────────────▶   │      Flask Backend         │
│                              │  /search/<şehir>/<ürün> │                            │
│  Fragment → ViewModel        │  /basket/optimize       │  Route → Service           │
│    → Repository → Retrofit   │  POST /prices (OCR)     │    ├─ Scraper (ulusal)     │
│    → Room (favori/geçmiş)    │  /history/<market>/<ür> │    └─ SQLite (crowdsourced)│
│  Hilt · Coroutines/Flow      │  ◀───────────────────   │  requests + BeautifulSoup  │
└──────────────────────────────┘                         └────────────────────────────┘
```

### Android katmanları

| Katman | Sorumluluk | Örnek |
|--------|------------|-------|
| **UI** | Görünüm, durum gözlemleme | `SearchFragment`, `ProductDetailFragment`, `BasketFragment`, `CrowdsourceFragment` |
| **ViewModel** | UI durumu, iş akışı | `SearchViewModel`, `BasketViewModel`, `HomeViewModel` |
| **Repository** | Uzak + yerel veri | `ItemRepository`, `FavoritesRepository`, `SearchHistoryRepository` |
| **Remote / Local** | Retrofit API / Room | `ItemRemoteSource` / `AppDatabase`, DAO'lar |
| **DI** | Hilt modülleri | `AppModule`, `DatabaseModule` |

---

## 🧠 Öne Çıkan Teknik Detaylar

- **Sunucu tarafı birleşik arama:** `/search/<şehir>/<ürün>` ulusal scraping + yerel
  crowdsourced veriyi tek yanıtta toplar; istemci sadeleşir.
- **Sepet optimizasyonu:** hem tek-market sepet toplamı hem ürün bazlı en ucuz dağıtım.
- **Cihaz üstü yapay zekâ:** ML Kit görüntü etiketleme (nesne) + metin tanıma (OCR),
  fotoğraflar cihazdan çıkmaz.
- **Kütüphanesiz grafik:** tema uyumlu özel `LineChartView` (Canvas) — ek bağımlılık yok.
- **Arka plan fiyat takibi:** `PriceWatchWorker` (Hilt destekli `CoroutineWorker`) günde
  bir kez favorileri yeniden arar; kayıtlı fiyatın altına inen ürün için bildirim
  gönderip referans fiyatı tazeler (aynı düşüş her gün tekrar bildirilmez).
  Bildirim izni açılışta değil, ilk favori eklenirken istenir.
- **Sunucu tarafı önbellek:** ulusal scraping sonuçları süreç içi TTL önbelleğinde
  (varsayılan 15 dk). Crowdsourced kısım önbelleğe alınmaz — kullanıcı gönderdiği
  fiyatı anında görmeli. On ürünlük bir sepet optimizasyonu 40 scraping isteği
  yerine önbellekten karşılanır.
- **Yaşam döngüsü güvenli akış:** `repeatOnLifecycle(STARTED)`; `ListAdapter`+`DiffUtil`.
- **Yayına hazır:** R8 kod küçültme + kaynak küçültme, ağ güvenliği yapılandırması,
  yapılandırılabilir backend adresi (`BuildConfig.BASE_URL`), imzalama yapılandırması.
- **Çift modlu backend:** gerçek scraping veya örnek veri; hata olursa otomatik fallback.

---

## 🚀 Çalıştırma

### 1) Backend (önce)
```powershell
cd flask_backend
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env        # USE_MOCK=true önerilir
python app.py                 # http://0.0.0.0:5454
```
Doğrulama: `curl http://localhost:5454/health` · Ayrıntı: [`flask_backend/README.md`](flask_backend/README.md)

### 2) Android
1. Backend'i çalıştırın.
2. `local.properties` (opsiyonel): emülatörde varsayılan `http://10.0.2.2:5454/` kullanılır;
   fiziksel cihazda `backend.baseUrl=http://<PC-IP>:5454/`.
3. Android Studio → **Run** (veya `./gradlew installDebug`).

---

## 🧰 Teknoloji Yığını

**Android:** Kotlin · MVVM · Hilt · Coroutines & Flow · Room · Retrofit + Gson · Glide ·
ML Kit (görüntü etiketleme + metin tanıma) · WorkManager · Jetpack Navigation · Material 3 ·
Splash Screen API

**Backend:** Python · Flask · Flask-CORS · requests · BeautifulSoup · SQLite · pytest · Docker

---

## 🧪 Testler
```powershell
cd flask_backend && pytest -q      # Backend (16 test: API + önbellek)
./gradlew testDebugUnitTest        # Android birim (26 test)
```

Android birim testleri Android bağımlılığı olmayan saf mantığı hedefler:
`ResultShaper` (filtre + sıralama), `PriceParser` (OCR fiyat ayıklama),
`LabelTranslator` (etiket çevirisi), `BasketStore`, `Item`. Yerel duyarlı
davranışlar Türkçe yerel altında da doğrulanır — `"I".lowercase()` Türkçe'de
`"ı"` ürettiği ve `"%.2f"` virgüllü ondalık verdiği için bunlar teoride değil
pratikte kırılan yerlerdi.

---

## 📦 Yayın (Play Store)
Yayın imzalama, R8, gizlilik politikası ve mağaza metinleri için:
- [`RELEASE.md`](RELEASE.md) — imzalama + AAB üretimi + Play Console adımları
- [`PRIVACY.md`](PRIVACY.md) — gizlilik politikası
- [`STORE_LISTING.md`](STORE_LISTING.md) — mağaza açıklamaları
- Backend bulut deploy: [`flask_backend/README.md`](flask_backend/README.md) → Render

```bash
./gradlew bundleRelease   # app/build/outputs/bundle/release/app-release.aab
```

---

## 📁 Proje Yapısı
```
MarketRehberim/
├── app/                          # Android uygulaması
│   └── src/main/java/com/marketrehberim/
│       ├── data/
│       │   ├── model/            # Item (Parcelable)
│       │   ├── remote/           # ItemRemoteSource + DTO'lar
│       │   ├── local/            # Room (DB, DAO, entity), CityStore
│       │   └── repository/       # Item, Favorites, SearchHistory
│       ├── di/                   # Hilt (AppModule, DatabaseModule)
│       ├── work/                 # PriceWatchWorker (günlük fiyat takibi)
│       ├── notification/         # PriceAlerts (bildirim kanalı + gönderim)
│       └── ui/
│           ├── view/             # home, search, detail, basket, crowdsource, main
│           ├── viewmodel/        # Search, Home, Basket, ProductDetail, Crowdsource
│           ├── adapter/          # Item, Favorite, MarketBasket
│           └── widget/           # LineChartView (özel grafik)
├── flask_backend/                # Flask REST API (ayrı README + deploy)
├── RELEASE.md · PRIVACY.md · STORE_LISTING.md
└── README.md
```

---

## 🗺️ Gelecek İyileştirmeler
- [x] Fiyat düşüşü bildirimleri (WorkManager)
- [x] Backend'de yanıt önbelleği
- [x] ViewModel/saf mantık birim testleri
- [ ] Kalıcı veritabanı (PostgreSQL) — Render'ın diski kalıcı olmadığı için
      crowdsourced veri yeniden deploy'da siliniyor
- [ ] Gerçek scraper seçicilerinin canlı site yapısına göre güncellenmesi
- [ ] Enstrümantasyon (UI) testleri

---

## 📄 Lisans / Not
Eğitim ve portföy amaçlı geliştirilmiştir. Gerçek scraping modu kullanılırken hedef
sitelerin kullanım şartlarına ve `robots.txt` kurallarına uyulmalıdır.
