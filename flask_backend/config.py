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
    # USE_MOCK=false -> gerçek kaynak denenir; hata olursa mock veriye düşülür
    #                   (MOCK_FALLBACK=true iken).
    USE_MOCK = _as_bool(os.getenv("USE_MOCK"), default=True)
    MOCK_FALLBACK = _as_bool(os.getenv("MOCK_FALLBACK"), default=True)

    # --- Ulusal fiyat kaynağı -------------------------------------------
    # Zincir marketlerin mevzuat gereği bildirdiği, TÜBİTAK BİLGEM tarafından
    # kamuya açılan veri. Market sitelerini kazımanın hukuki yerine geçer.
    # Ayrıntı: docs/VERI_KAYNAGI.md
    MARKETFIYATI_BASE_URL = os.getenv(
        "MARKETFIYATI_BASE_URL", "https://api.marketfiyati.org.tr/api/v2"
    )
    # Şehir merkezinden kaç km yarıçapındaki şubeler taransın.
    MARKETFIYATI_DISTANCE_KM = float(os.getenv("MARKETFIYATI_DISTANCE_KM", "15"))
    MARKETFIYATI_PAGE_SIZE = int(os.getenv("MARKETFIYATI_PAGE_SIZE", "24"))

    # Eski doğrudan-scraping akışı. Varsayılan KAPALI: market sitelerini kazımak
    # TTK m.55 haksız rekabet, sui generis veritabanı hakkı ve site kullanım
    # şartları açısından risk taşır ve mağazaya çıkacak üründe taşınmamalı.
    # Yalnız yerel geliştirme/karşılaştırma için açılır.
    ENABLE_LEGACY_SCRAPERS = _as_bool(os.getenv("ENABLE_LEGACY_SCRAPERS"), default=False)

    # Ulusal sonuçların önbellek ömrü (saniye). 0 = önbellek kapalı.
    # Kaynak veriyi günlük mertebede tazeliyor; 6 saat tazelik ile kaynağa
    # bindirilen yük arasında makul bir denge (eski varsayılan 15 dk gereksizdi).
    SEARCH_CACHE_TTL = float(os.getenv("SEARCH_CACHE_TTL", "21600"))

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