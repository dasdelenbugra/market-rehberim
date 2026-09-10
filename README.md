# 🛒 Market Rehberim

[![testler](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/tests.yml/badge.svg)](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/tests.yml)
[![android](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/android.yml/badge.svg)](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/android.yml)
[![alaka ölçümü](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/relevance.yml/badge.svg)](https://github.com/dasdelenbugra/market-rehberim/actions/workflows/relevance.yml)

**Market Rehberim**, yerel odaklı bir **market fiyat karşılaştırma ve alışveriş
asistanı** uygulamasıdır. Aynı ürünü yedi ulusal zincirde (A101, BİM, CarrefourSA,
Hakmar, Migros, Tarım Kredi, ŞOK) **ve** şehrinizdeki yerel marketlerde
karşılaştırır, en ucuzunu bulur. 81 il desteklenir.

> Akakçe/Cimri'den farkı: **yerel/şehir bazlı odak**, **sepet optimizasyonu**,
> **crowdsourced (topluluk katkılı) fiyat** ve **kamera ile ürün/barkod tanıma**.
> Modern Android (MVVM, Hilt, Room, Coroutines/Flow, ML Kit) + temiz Flask backend.

---

## ✨ Özellikler

| Özellik | Açıklama |
|---------|----------|
| 🔍 **Birleşik arama** | Tek aramada ulusal + yerel marketler; "EN UCUZ" rozeti |
| 🎯 **Alaka sıralaması** | "muz" muzu getirir; muzlu gofret ve muzlu süt ayrı bölüme düşer |
| 🔤 **Eş anlamlı sözlüğü** | "çöp poşeti" → "çöp torbası"; ölçümle kurulur, kullanımla beslenir |
| ✍️ **Yazım önerisi** | Boş sonuçta "Bunu mu demek istediniz: çikolata" |
| ⚖️ **Birim fiyat** | ₺/kg, ₺/L — 350 gr'lık paket ucuz görünüp 1 kg'ı yenmesin |
| 🏷️ **Barkod tarama** | Kamera → barkod → Open Food Facts → şehirdeki fiyatlar |
| 📸 **Kamera ile tanıma** | Ürün fotoğrafı → ML Kit nesne tanıma → Türkçe otomatik arama |
| 🛒 **Sepet optimizasyonu** | Liste → her marketin toplamı **+** ürün bazlı en ucuz dağıtım |
| 📸 **Crowdsourced fiyat (OCR)** | Raf etiketi fotoğrafı → ML Kit metin tanıma → fiyat gönder |
| 🕒 **Son güncelleme** | Fiyatın ne kadar taze olduğu açıkça yazılır; "anlık" vaadi verilmez |
| 🏙️ **81 il** | Şehir seçimi yerel market kapsamını ve şube konumunu belirler |
| ⭐ **Favoriler + geçmiş** | Room ile yerel kalıcılık; anasayfada gösterim |
| 📉 **Fiyat geçmişi** | Kütüphanesiz özel çizgi grafik (`LineChartView`) |
| 🔔 **Fiyat düşüşü bildirimi** | WorkManager ile günlük takip; favori ucuzlayınca bildirim |

---

## 📐 Mimari

```
┌──────────────────────────────┐        HTTP/JSON         ┌────────────────────────────┐
│      Android (MVVM)          │  ────────────────────▶   │      Flask Backend         │
│                              │  /products/<şehir>/<ür>  │                            │
│  Fragment → ViewModel        │  /search/<şehir>/<ürün>  │  Route → Service           │
│    → Repository → Retrofit   │  /barcode/<şehir>/<kod>  │    ├─ marketfiyati.org.tr  │
│    → Room (favori/geçmiş)    │  /basket/optimize        │    │   (ulusal, resmî veri) │
│  Hilt · Coroutines/Flow      │  POST /prices (OCR)      │    ├─ Open Food Facts      │
│                              │  /history/<market>/<ür>  │    │   (barkod → ürün)     │
│                              │  ◀────────────────────   │    └─ SQLite (crowdsourced)│
└──────────────────────────────┘                          └────────────────────────────┘
```

`/products` gruplanmış sonucu döndürür (arama ekranı), `/search` düz listeyi
(ürün detayı ve sepet optimizasyonu). İkisi ayrı durur çünkü `/search`
sözleşmesini bozmadan gruplama eklenmesi gerekiyordu.

Gövde şeması sabit tutulup yan bilgiler **yanıt başlığıyla** taşınır:
`X-Data-Updated` (fiyatların tazeliği) ve `X-Search-Suggestion` (yazım önerisi).
Tüketicisi olmayan alanla istemci sözleşmesini genişletmemek için.

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
| **Sunum mantığı** | Filtre, sıralama, bölümleme | `ResultShaper` (Android bağımlılığı yok → doğrudan test edilir) |
| **DI** | Hilt modülleri | `AppModule`, `DatabaseModule` |

---

## 🎯 Arama Kalitesi

Projenin en çok uğraşılan kısmı. Başlangıçtaki somut şikâyet: **"muz" araması
muzu, muzlu sütü ve muzlu gofreti tek listede karıştırıyor, "EN UCUZ" rozeti
8 ₺'lik gofrete gidiyordu.** Kullanıcının aradığı ürünle rozeti alan ürün
farklıysa uygulamanın tek vaadi çürür.

Zincir dört halkadan oluşuyor; her biri farklı bir başarısızlığı karşılıyor:

| Sorun | Çözüm | Nerede |
|-------|-------|--------|
| Kelime katalogda yok | Eş anlamlı çevirisi | [`synonyms.py`](flask_backend/app/synonyms.py) |
| Kelime yanlış yazılmış | Öneri (yalnız boş sonuçta) | [`suggest.py`](flask_backend/app/suggest.py) |
| Sonuçlar alakasız | Kategori tohumlamalı sıralama | [`products.py`](flask_backend/app/products.py) |
| Hiçbiri tutmadı | Sonuçsuz arama kaydı → yeni aday | [`tools/search_gaps.py`](flask_backend/tools/search_gaps.py) |

**Alaka sıralaması** iki sinyali birleştirir, çünkü ikisi de tek başına
yetmiyor. *Ad sezgisi*: Türkçe tamlamalarda baş isim sonda durur — "Yerli Muz"
muzdur, "Muz Aromalı Süt" süttür. Ama "Hero Baby Elma Muz" da sonu "muz" olan
kısa bir ad, oysa bebek maması. *Kaynak kategorisi*: tek başına da yetmiyor,
çünkü "muz" sonuçlarında en kalabalık kategoriler Süt ve Bebek Mamaları; Meyve
yalnız iki üründe. Çoğunluğa bakmak yanlış cevap verir.

Birleşimi çalışıyor: ad sezgisi **tohum** olur (baş ismi sorgu olan adaylardan
en kısa adlısı → "Yerli Muz" → *Meyve*), kategori **genelleştirir** (ana liste
o kategoriden oluşur). Böylece bebek maması elenir, ad sezgisinin kaçırdığı
ürünler ("Yumurta M Boy 30 Adet") geri kazanılır.

**Her adım ölçülerek eklendi**, tahminle değil. Ölçüm aracı depoda:

```powershell
cd flask_backend && python tools/check_relevance.py     # 33 sorgu, 64 beklenti
```

Ölçüm bazı planları da iptal etti: kaynağın diakritiksiz yazımı zaten kusursuz
karşıladığı (`sut` = `süt`, birebir 494 sonuç) ve yazım hatalarının çoğunu
yakaladığı (12 hatanın 11'i) ölçülünce, yazılacak düzeltme kodunun büyük kısmı
gereksiz çıktı.

**Susmak konuşmaktan iyidir.** Markette gerçekten bulunmayan ürünlere (pırasa,
süpürge, naftalin) bilerek öneri üretilmez: yanlış öneri, öneri vermemekten
kötüdür — "pırasa" arayanı "pirinç"e yollamak kullanıcıyı istemediği ürüne
götürür.

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
- **Çift modlu backend:** gerçek kaynak (`USE_MOCK=false`) veya örnek veri. Boş
  sonuçta mock'a düşme **kapatıldı**: uydurma bir fiyatın gerçekmiş gibi
  görünmesi, boş ekrandan çok daha zararlı. Mock yalnız bilerek açılır.
- **Türkçe metin tuzakları tek yerde:** `str.lower()` Türkçe'de güvenilir değil —
  `"İSTANBUL".lower()` birleşik karakter üretir ve `"istanbul"` anahtarıyla
  eşleşmez. Karşılaştırma için `fold()`, kullanıcıya gösterim için `lower_tr()`
  (`app/text.py`). Şehir anahtarı ve market adı eşleştirmesi bu tuzağa
  düşüyordu.

---

## 🚀 Çalıştırma

### 1) Backend (önce)
```powershell
cd flask_backend
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt
copy .env.example .env        # USE_MOCK=false → gerçek fiyatlar (ağ gerekir)
python app.py                 # http://0.0.0.0:5454
```
`USE_MOCK=true` örnek veriyle çalıştırır (ağ gerektirmez, demo/geliştirme için).
Kaynağın şu an beklenen alanları döndürdüğünü doğrulamak için:
`python tools/check_marketfiyati.py süt --city istanbul`
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

**Backend:** Python · Flask · Flask-CORS · requests · SQLite · pytest · Docker
· marketfiyati.org.tr (ulusal fiyat) · Open Food Facts (barkod → ürün)

---

## 🧪 Testler
```powershell
cd flask_backend && pytest -q      # Backend (156 test)
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
├── flask_backend/
│   ├── app/
│   │   ├── routes.py             # HTTP uçları
│   │   ├── services.py           # arama toplama, sepet, geçmiş
│   │   ├── products.py           # gruplama + alaka sıralaması
│   │   ├── synonyms.py           # kullanıcı terimi → katalog terimi
│   │   ├── suggest.py            # "bunu mu demek istediniz"
│   │   ├── units.py              # birim fiyat (₺/kg, ₺/L)
│   │   ├── text.py               # Türkçe normalizasyon (fold / lower_tr)
│   │   ├── db.py                 # SQLite: crowdsourced, geçmiş, arama boşlukları
│   │   └── sources/              # marketfiyati.org.tr, Open Food Facts
│   ├── tools/                    # canlı ölçüm araçları (ağa çıkar)
│   ├── tests/                    # 156 test (ağa çıkmaz)
│   └── docs/VERI_KAYNAGI.md      # veri kaynağı + hukuki gerekçe
├── .github/workflows/            # CI: tests · android · relevance
├── RELEASE.md · PRIVACY.md · STORE_LISTING.md
└── README.md
```

---

## 🗺️ Gelecek İyileştirmeler
- [x] Fiyat düşüşü bildirimleri (WorkManager)
- [x] Backend'de yanıt önbelleği
- [x] ViewModel/saf mantık birim testleri
- [x] Ulusal fiyatların hukuken güvenli, resmî kaynağa taşınması
- [x] Ürün kartında "son güncelleme" bilgisi (`X-Data-Updated`)
- [x] Alaka sıralaması — "muz" araması muzu getirsin
- [x] Eş anlamlı sözlüğü + yazım önerisi
- [x] Sürekli entegrasyon (backend · Android · haftalık canlı ölçüm)
- [x] Barkod tarama (Open Food Facts)
- [x] 81 il desteği
- [ ] Kalıcı veritabanı (PostgreSQL) — Render'ın diski kalıcı olmadığı için
      crowdsourced veri yeniden deploy'da siliniyor
- [ ] Eş anlamlı sözlüğünü gerçek kullanım verisiyle beslemek
      (`tools/search_gaps.py` topluyor, yeterli veri birikmesi bekleniyor)
- [ ] Kullanıcının gerçek konumuyla sorgu (şehir merkezi yerine en yakın şube)
- [ ] Enstrümantasyon (UI) testleri — emülatörlü CI gerektiriyor
- [ ] Çok kelimeli sorgularda yazım önerisi (hangi kelimenin hatalı olduğu
      belirsiz; ölçülmeden eklenmeyecek)

---

## 📄 Lisans / Not
MarketRehberim aktif olarak geliştirilen bir projedir. Ulusal market fiyatları,
zincir marketlerin mevzuat gereği bildirdiği ve TÜBİTAK BİLGEM tarafından
kamuoyuna açılan veriden alınır; market siteleri doğrudan kazınmaz. Kaynaklar ve
sorumlu kullanım kuralları:
[`flask_backend/docs/VERI_KAYNAGI.md`](flask_backend/docs/VERI_KAYNAGI.md)

Market adları ve logoları ilgili şirketlerin tescilli markalarıdır; bu uygulama
onlarla bağlantılı değildir.
