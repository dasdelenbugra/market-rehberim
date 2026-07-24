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
    # USE_MOCK=false -> önce gerçek scraping denenir; hata olursa mock veriye düşülür
    #                   (MOCK_FALLBACK=true iken).
    USE_MOCK = _as_bool(os.getenv("USE_MOCK"), default=True)
    MOCK_FALLBACK = _as_bool(os.getenv("MOCK_FALLBACK"), default=True)

    # Ulusal scraping sonuçlarının önbellek ömrü (saniye). 0 = önbellek kapalı.
    # Market fiyatları gün içinde nadiren değişir; 15 dakika tazelik ile scraping
    # yükü arasında makul bir denge.
    SEARCH_CACHE_TTL = float(os.getenv("SEARCH_CACHE_TTL", "900"))

    # HTTP scraping ayarları
    REQUEST_TIMEOUT = float(os.getenv("REQUEST_TIMEOUT", "10"))
    USER_AGENT = os.getenv(
        "USER_AGENT",
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
        "(KHTML, like Gecko) Chrome/124.0 Safari/537.36",
    )