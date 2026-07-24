"""Şehir ve market kaydı.

Ulusal marketler her şehirde vardır (online mağaza/scraping ile). Yerel marketlerin
çoğunun web sitesi yoktur; bunların fiyatları crowdsourced (kullanıcı katkısı) olarak
toplanır. Kayıt, hangi şehirde hangi marketlerin karşılaştırılabileceğini tanımlar.
"""
from __future__ import annotations

# Ulusal marketler: scraping ile veri gelir, her şehirde geçerli sayılır.
NATIONAL_MARKETS = ["Migros", "A101", "ŞOK", "CarrefourSA"]

# Yerel marketler: web sitesi yok → yalnızca crowdsourced veri.
# Şehir (küçük harf, TR karaktersiz anahtar) -> yerel market listesi.
LOCAL_MARKETS: dict[str, list[str]] = {
    "tokat": ["Erenler", "Mopaş"],
    "istanbul": ["Onur Market", "Hakmar"],
    "ankara": ["Yeni Mağazacılık"],
}

CITY_LABELS: dict[str, str] = {
    "tokat": "Tokat",
    "istanbul": "İstanbul",
    "ankara": "Ankara",
}


def cities() -> list[dict]:
    return [{"key": key, "label": CITY_LABELS.get(key, key.title())}
            for key in sorted(LOCAL_MARKETS.keys())]


def markets_for(city: str) -> dict[str, list[str]]:
    """Şehir için ulusal ve yerel market listelerini döndürür."""
    city_key = (city or "").strip().lower()
    return {
        "national": list(NATIONAL_MARKETS),
        "local": LOCAL_MARKETS.get(city_key, []),
    }


def all_markets_for(city: str) -> list[str]:
    m = markets_for(city)
    return m["national"] + m["local"]
