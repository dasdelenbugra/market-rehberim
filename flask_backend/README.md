# Market Rehberim — Backend (Flask)

Market Rehberim Android uygulamasının fiyat karşılaştırma verisini sağlayan REST API.
Farklı marketlerde (Migros, A101, Erenler) ürün araması yapar ve normalleştirilmiş
sonuç döndürür.

## Özellikler

- **Uygulama fabrikası (application factory)** deseni + Blueprint tabanlı rotalar
- **Genişletilebilir scraper mimarisi** — yeni market eklemek tek bir sınıf + tek satır kayıt
- **Çift modlu veri kaynağı**: gerçek web scraping (`requests` + `BeautifulSoup`) veya
  örnek (mock) veri; scraping başarısız olursa otomatik mock'a düşer
- **Fiyat normalleştirme** — `"12,50 TL"` → `"12.50"` (istemci `toDouble()` ile sıralar)
- **CORS** açık, **/health** sağlık kontrolü, **pytest** duman testleri

## Mimari

```
flask_backend/
├── app.py                # Geliştirme giriş noktası (python app.py)
├── config.py             # Ortam değişkeni tabanlı yapılandırma
├── requirements.txt
├── Dockerfile
├── app/
│   ├── __init__.py       # create_app() fabrikası
│   ├── routes.py         # /<store>/<itemName>, /health
│   ├── models.py         # Item modeli + fiyat normalleştirme
│   └── scrapers/
│       ├── __init__.py   # SCRAPERS kayıt tablosu
│       ├── base.py       # BaseScraper (fetch akışı, hata yönetimi)
│       ├── _mock.py      # Örnek veri üreticisi
│       ├── migros.py
│       ├── a101.py
│       └── erenler.py
└── tests/
    └── test_api.py
```

## Kurulum ve Çalıştırma (Windows)

```powershell
cd flask_backend
python -m venv .venv
.venv\Scripts\Activate.ps1
pip install -r requirements.txt

copy .env.example .env      # ardından .env'i düzenleyin
python app.py
```

Sunucu varsayılan olarak `http://0.0.0.0:5454` üzerinde çalışır.

### Docker ile (Linux)

```bash
docker build -t market-rehberim-backend .
docker run -p 5454:5454 -e USE_MOCK=true market-rehberim-backend
```

## Yapılandırma (.env)

| Değişken         | Varsayılan | Açıklama |
|------------------|-----------|----------|
| `HOST`           | `0.0.0.0` | Dinlenen adres |
| `PORT`           | `5454`    | Port |
| `DEBUG`          | `false`   | Flask debug modu |
| `USE_MOCK`       | `true`    | `true`: her zaman örnek veri; `false`: gerçek scraping |
| `MOCK_FALLBACK`  | `true`    | Scraping başarısız olursa örnek veriye düş |
| `REQUEST_TIMEOUT`| `10`      | HTTP istek zaman aşımı (sn) |

> **Geliştirme ve testlerde** `USE_MOCK=true` önerilir: ağ ve site yapısına bağlı
> olmadan her zaman tutarlı sonuç verir. Canlı scraping için `USE_MOCK=false` yapın
> (seçiciler `app/scrapers/*.py` içinde güncellenebilir).

## API

| Method | Endpoint                        | Açıklama |
|--------|---------------------------------|----------|
| GET    | `/health`                       | Servis durumu ve market listesi |
| GET    | `/cities`                       | Desteklenen şehirler |
| GET    | `/markets/<city>`               | Şehirdeki ulusal + yerel marketler |
| GET    | `/search/<city>/<itemName>`     | **Birleşik arama** (ulusal scraping + yerel crowdsourced) |
| POST   | `/prices`                       | **Crowdsourced fiyat gönder** (raf etiketi OCR sonucu) |
| POST   | `/basket/optimize`              | **Sepet optimizasyonu** (marketler arası en ucuz dağıtım) |
| GET    | `/history/<market>/<itemName>`  | **Fiyat geçmişi** (grafik verisi) |
| GET    | `/<market>/<itemName>`          | Tekil market araması (eski uyumluluk) |

### Örnekler

```bash
# Birleşik arama (Tokat + süt)
curl http://localhost:5454/search/tokat/süt

# Crowdsourced fiyat gönderimi
curl -X POST http://localhost:5454/prices \
  -H "Content-Type: application/json" \
  -d '{"city":"tokat","market":"Erenler","name":"zeytin","price":"45,90 TL"}'

# Sepet optimizasyonu
curl -X POST http://localhost:5454/basket/optimize \
  -H "Content-Type: application/json" \
  -d '{"city":"tokat","items":["süt","ekmek","yumurta"]}'
```

Sepet optimizasyonu çıktısı: her marketin sepet toplamı (`byMarket`) **ve** her ürünü
en ucuz marketten alma senaryosu (`optimalSplit`).

## ☁️ Bulut Deploy (Render — ücretsiz)

Yayınlanan Android uygulaması `localhost`'a erişemez; backend genel bir adreste
olmalı. Bu repo Render için hazırdır (`render.yaml`, `Procfile`, `runtime.txt`):

1. Kodu GitHub'a push edin.
2. [render.com](https://render.com) → **New → Blueprint** → repoyu seçin.
   `render.yaml` otomatik algılanır (kök dizin `flask_backend`).
3. Deploy sonrası size `https://market-rehberim-backend.onrender.com` gibi bir adres verilir.
4. Bu adresi Android `local.properties` → `backend.baseUrl` alanına yazın (sonu `/` ile).

> Alternatif: Railway/Fly.io de aynı `Procfile` ile çalışır. Ücretsiz katmanda
> dosya sistemi kalıcı değildir — crowdsourced SQLite verisi yeniden başlatmada
> sıfırlanabilir; kalıcılık için Render Disk veya harici bir veritabanı ekleyin.

## Testler

```powershell
.venv\Scripts\Activate.ps1
pytest -q
```

## Yeni Market Ekleme

1. `app/scrapers/` altında `BaseScraper`'dan türeyen bir sınıf yazın
   (`store_name`, `search_url`, `parse`, `mock_items` doldurun).
2. `app/scrapers/__init__.py` içindeki `SCRAPERS` sözlüğüne ekleyin.
3. Android tarafında `ItemRemoteSource`'a ilgili endpoint'i ve `ItemRepository`'ye
   çağrıyı ekleyin.

## Yasal Not

Canlı scraping modu (`USE_MOCK=false`) kullanılırken hedef sitelerin kullanım
şartlarına ve `robots.txt` kurallarına uyulmalıdır.
```
