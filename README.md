# 🛒 Market Rehberim

[![testler](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/tests.yml/badge.svg)](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/tests.yml)
[![android](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/android.yml/badge.svg)](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/android.yml)
[![alaka ölçümü](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/relevance.yml/badge.svg)](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/relevance.yml)

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
│    → Repository → Retrofit   │  POST /prices (OCR)     │    ├─ marketfiyati.org.tr  │
│    → Room (favori/geçmiş)    │  /history/<market>/<ür> │    │   (ulusal, resmî veri) │
│  Hilt · Coroutines/Flow      │  ◀───────────────────   │    └─ SQLite (crowdsourced)│
└──────────────────────────────┘                         └────────────────────────────┘
```

> **Ulusal fiyatlar nereden geliyor?** Zincir marketlerin Perakende Yönetmeliği
> uyarınca bildirmek zorunda olduğu ve TÜBİTAK BİLGEM tarafından kamuya açılan
> veriden. Doğrudan site scraping'i kapatıldı — gerekçe ve alternatif kaynaklar:
> [`flask_backend/docs/VERI_KAYNAGI.md`](flask_backend/docs/VERI_KAYNAGI.md)

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

- **Sunucu tarafı birleşik arama:** `/search/<şehir>/<ürün>` ulusal (resmî kaynak) +
  yerel crowdsourced veriyi tek yanıtta toplar; istemci sadeleşir.
- **Sepet optimizasyonu:** hem tek-market sepet toplamı hem ürün bazlı en ucuz dağıtım.
- **Cihaz üstü yapay zekâ:** ML Kit görüntü etiketleme (nesne) + metin tanıma (OCR),
  fotoğraflar cihazdan çıkmaz.
- **Kütüphanesiz grafik:** tema uyumlu özel `LineChartView` (Canvas) — ek bağımlılık yok.
- **Arka plan fiyat takibi:** `PriceWatchWorker` (Hilt destekli `CoroutineWorker`) günde
  bir kez favorileri yeniden arar; kayıtlı fiyatın altına inen ürün için bildirim
  gönderip referans fiyatı tazeler (aynı düşüş her gün tekrar bildirilmez).
  Bildirim izni açılışta değil, ilk favori eklenirken istenir.
- **Sunucu tarafı önbellek:** ulusal sonuçlar süreç içi TTL önbelleğinde
  (varsayılan 6 saat — kaynak zaten günlük tazeleniyor, daha sık sormak yeni veri
  getirmez). Anahtar şehri içerir, çünkü kaynak şube bazlı fiyat döndürür.
  Crowdsourced kısım önbelleğe alınmaz — kullanıcı gönderdiği fiyatı anında
  görmeli. On ürünlük bir sepet optimizasyonu tek tek istek yerine önbellekten
  karşılanır.
- **Yaşam döngüsü güvenli akış:** `repeatOnLifecycle(STARTED)`; `ListAdapter`+`DiffUtil`.
- **Yayına hazır:** R8 kod küçültme + kaynak küçültme, ağ güvenliği yapılandırması,
  yapılandırılabilir backend adresi (`BuildConfig.BASE_URL`), imzalama yapılandırması.
- **Çift modlu backend:** gerçek kaynak veya örnek veri; hata olursa otomatik fallback.

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
cd flask_backend && pytest -q      # Backend (139 test)
./gradlew testDebugUnitTest        # Android birim (39 test)
```

### Canlı ölçüm

Birim testler ayrıştırıcıyı sabit veriyle doğrular; asıl soruyu cevaplamazlar:
*gerçek katalogda doğru ürünler ana listeye çıkıyor mu?* Onun için ayrı araçlar
var (ağa çıkarlar, bu yüzden birim testlerden ayrı dururlar):

```powershell
cd flask_backend
python tools/check_relevance.py    # 33 sorgu, 64 beklenti — sıralama ölçümü
python tools/search_gaps.py        # sonuçsuz aramalar — eş anlamlı adayları
python tools/check_marketfiyati.py süt --city istanbul   # kaynak şeması
```

### CI

| Workflow | Ne zaman | Neden |
|----------|----------|-------|
| [`tests.yml`](.github/workflows/tests.yml) | Her push / PR | Backend; hızlı, ağ istemez (Python 3.9 + 3.12) |
| [`android.yml`](.github/workflows/android.yml) | Android dosyaları değişince | Gradle dakikalar sürüyor; backend değişiminde çalışmasın |
| [`relevance.yml`](.github/workflows/relevance.yml) | Haftalık + elle | *"Kaynak değişti mi?"* sorusunu izler |

Alaka ölçümü bilerek her push'ta çalışmıyor: canlı kaynağa istek atıyor, kod
sağlamken kaynak takıldığında kırmızı yanardı. Sık sık haksız yere kırmızı yanan
bir CI'a insan bakmayı bırakır — o da CI'ı olmamış hâle getirir.

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
- [x] Ulusal fiyatların hukuken güvenli, resmî kaynağa taşınması
- [ ] Ürün kartında "son güncelleme" rozeti (`MarketFiyatiSource.last_indexed`)
- [ ] Kullanıcının gerçek konumuyla sorgu (şehir merkezi yerine en yakın şube)
- [ ] Enstrümantasyon (UI) testleri

---

## 📄 Lisans / Not
MarketRehberim aktif olarak geliştirilen bir projedir. Ulusal market fiyatları,
zincir marketlerin mevzuat gereği bildirdiği ve TÜBİTAK BİLGEM tarafından
kamuoyuna açılan veriden alınır; market siteleri doğrudan kazınmaz. Kaynaklar ve
sorumlu kullanım kuralları:
[`flask_backend/docs/VERI_KAYNAGI.md`](flask_backend/docs/VERI_KAYNAGI.md)

Market adları ve logoları ilgili şirketlerin tescilli markalarıdır; bu uygulama
onlarla bağlantılı değildir.
