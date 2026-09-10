"""Uygulama yapılandırması.

Ortam değişkenleri `.env` dosyasından okunur (bkz. `.env.example`).
"""
import os

from dotenv import load_dotenv

load_dotenv()


def _as_bool(value: str, default: bool = False) -> bool:
    if value is None:
        return default
    return value.strip().lower() in {"1", "true", "yes", "on"}


class Config:
    # Sunucu
    HOST = os.getenv("HOST", "0.0.0.0")
    PORT = int(os.getenv("PORT", "5454"))
    DEBUG = _as_bool(os.getenv("DEBUG"), default=False)

    # Veri kaynağı davranışı
    # USE_MOCK=true  -> her zaman örnek (mock) veri döndürülür, ağ isteği yapılmaz.
    # USE_MOCK=false -> gerçek kaynak kullanılır. Yalnızca **ağ/şema hatasında**
    #                   ve MOCK_FALLBACK=true iken mock'a düşülür; sonuç boşsa
    #                   asla düşülmez (uydurma ürün, boş ekrandan kötü).
    # MOCK_FALLBACK varsayılanı false: örnek veri gerçek fiyat gibi görünmesin.
    USE_MOCK = _as_bool(os.getenv("USE_MOCK"), default=True)
    MOCK_FALLBACK = _as_bool(os.getenv("MOCK_FALLBACK"), default=False)

    # --- Ulusal fiyat kaynağı -------------------------------------------
    # Zincir marketlerin mevzuat gereği bildirdiği, TÜBİTAK BİLGEM tarafından
    # kamuya açılan veri. Market sitelerini kazımanın hukuki yerine geçer.
    # Ayrıntı: docs/VERI_KAYNAGI.md
    MARKETFIYATI_BASE_URL = os.getenv(
        "MARKETFIYATI_BASE_URL", "https://api.marketfiyati.org.tr/api/v2"
    )
    # Şehir merkezinden kaç km yarıçapındaki şubeler taransın.
    MARKETFIYATI_DISTANCE_KM = float(os.getenv("MARKETFIYATI_DISTANCE_KM", "15"))
    # 24 azdı: kaynak kendi alaka sırasıyla ilk sayfayı döndürüyor ve "domates"
    # gibi geniş sorgularda öbür marketlerin taze ürünü sayfaya sığmıyordu —
    # kullanıcı "diğer marketler yok" sanıyordu.
    MARKETFIYATI_PAGE_SIZE = int(os.getenv("MARKETFIYATI_PAGE_SIZE", "60"))

    # Eski doğrudan-scraping akışı. Varsayılan KAPALI: market sitelerini kazımak
    # TTK m.55 haksız rekabet, sui generis veritabanı hakkı ve site kullanım
    # şartları açısından risk taşır ve mağazaya çıkacak üründe taşınmamalı.
    # Yalnız yerel geliştirme/karşılaştırma için açılır.
    ENABLE_LEGACY_SCRAPERS = _as_bool(os.getenv("ENABLE_LEGACY_SCRAPERS"), default=False)

    # --- Barkod çözümleme (Open Food Facts) -----------------------------
    # Fiyat kaynağı barkod tutmuyor; barkod önce burada ürün adına çevrilir.
    # Açık veri (ODbL), anahtar gerektirmez. Ayrıntı: app/sources/openfoodfacts.py
    OPENFOODFACTS_BASE_URL = os.getenv(
        "OPENFOODFACTS_BASE_URL", "https://world.openfoodfacts.org/api/v2"
    )
    # Bir barkodun ürün adı değişmez; kısa TTL kaynağa boşuna yük bindirir.
    BARCODE_CACHE_TTL = float(os.getenv("BARCODE_CACHE_TTL", "604800"))  # 7 gün

    # Ulusal sonuçların önbellek ömrü (saniye). 0 = önbellek kapalı.
    # Kaynak veriyi günlük mertebede tazeliyor; 6 saat tazelik ile kaynağa
    # bindirilen yük arasında makul bir denge (eski varsayılan 15 dk gereksizdi).
    SEARCH_CACHE_TTL = float(os.getenv("SEARCH_CACHE_TTL", "21600"))

    # --- Arama boşluğu kaydı ---------------------------------------------
    # Sonuçsuz kalan aramalar sayılır; eş anlamlı sözlüğü (app/synonyms.py)
    # böylece tahminle değil gerçek kullanımla beslenir. Rapor:
    #   python tools/search_gaps.py
    # Kimlik bilgisi tutulmaz, yalnız terim ve sayaç — bkz. db.record_search_gap.
    LOG_SEARCH_GAPS = _as_bool(os.getenv("LOG_SEARCH_GAPS"), default=True)
    # Bu sayıdan az sonuç dönen arama "boşluk" sayılır. Sıfır en net sinyal ama
    # tek tük sonuç da çoğu zaman yanlış terim demek ("hıyar" 1 sonuç veriyordu).
    SEARCH_GAP_THRESHOLD = int(os.getenv("SEARCH_GAP_THRESHOLD", "3"))

    # HTTP ayarları
    REQUEST_TIMEOUT = float(os.getenv("REQUEST_TIMEOUT", "10"))
    # Kamuya açık API'ye kendimizi tanıtarak gideriz; tarayıcı taklidi yapmayız.
    CLIENT_USER_AGENT = os.getenv(
        "CLIENT_USER_AGENT",
        "MarketRehberim/1.0 (+https://github.com/; iletisim: uygulama sahibi)",
    )
    # Yalnız eski scraper akışı için (ENABLE_LEGACY_SCRAPERS=true iken).
    USER_AGENT = os.getenv(
        "USER_AGENT",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    )