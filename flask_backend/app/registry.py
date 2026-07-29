"""Şehir ve market kaydı.

Ulusal marketlerin fiyatı marketfiyati.org.tr'den (kamuya açık, mevzuat gereği
bildirilen veri) şube konumuyla birlikte gelir; bu yüzden her şehrin bir merkez
koordinatı var. Yerel marketlerin çoğunun web sitesi yoktur; bunların fiyatları
crowdsourced (kullanıcı katkısı) olarak toplanır.
"""
from __future__ import annotations

from app.text import fold

# Ulusal marketler: marketfiyati.org.tr kapsamındaki zincirler.
# Not: Kaynak, ilgili şehirde şubesi olmayan zinciri döndürmez — bu liste
# "olabilecekler" kümesidir, aramanın gerçek sonucu değil.
NATIONAL_MARKETS = [
    "Migros",
    "A101",
    "BİM",
    "ŞOK",
    "CarrefourSA",
    "Hakmar",
    "Tarım Kredi",
]

# Şehir merkezi koordinatları (lat, lon). Ulusal kaynak konum bazlı sorgu
# istiyor; kullanıcıdan konum izni almadan da anlamlı sonuç dönebilmek için
# şehir merkezini kullanıyoruz. İstemci gerçek konum gönderirse o tercih edilir.
CITY_COORDS: dict[str, tuple[float, float]] = {
    "adana": (37.0000, 35.3213),
    "ankara": (39.9334, 32.8597),
    "antalya": (36.8969, 30.7133),
    "bursa": (40.1826, 29.0665),
    "gaziantep": (37.0662, 37.3833),
    "istanbul": (41.0082, 28.9784),
    "izmir": (38.4237, 27.1428),
    "kayseri": (38.7312, 35.4787),
    "konya": (37.8746, 32.4932),
    "samsun": (41.2867, 36.3300),
    "tokat": (40.3167, 36.5544),
    "trabzon": (41.0027, 39.7168),
}

# Kayıtlı olmayan bir şehir gelirse Ankara merkez alınır (ülke ortasına yakın,
# boş sonuç dönmektense makul bir varsayılan).
DEFAULT_COORDS = CITY_COORDS["ankara"]


def city_key(city: str) -> str:
    """İstemciden gelen şehir adını kayıt anahtarına indirger.

    "İzmir" / "izmir" / "IZMIR" / "İSTANBUL" hepsi aynı şehri göstermeli;
    ayrıntı için bkz. `app.text.fold`.
    """
    return fold(city)


def coords_for(city: str) -> tuple[float, float]:
    """Şehir anahtarı için (enlem, boylam). Bilinmeyen şehirde varsayılan döner."""
    return CITY_COORDS.get(city_key(city), DEFAULT_COORDS)

# Yerel marketler: web sitesi yok → yalnızca crowdsourced veri.
# Şehir (küçük harf, TR karaktersiz anahtar) -> yerel market listesi.
LOCAL_MARKETS: dict[str, list[str]] = {
    "tokat": ["Erenler", "Mopaş"],
    "istanbul": ["Onur Market", "Hakmar", "Seyhanlar"],
    "ankara": ["Yeni Mağazacılık", "Beğendik"],
    "izmir": ["Özhan", "Pehlivanoğlu"],
    "bursa": ["Kadıoğlu", "Uludağ Market"],
    "antalya": ["Miroğlu", "Çağrı Market"],
    "adana": ["Nazar Market", "Emek Market"],
    "konya": ["Damla Market", "Beğendik"],
    "samsun": ["Ela Market", "Kilim Market"],
    "trabzon": ["Ceyar Market", "Aydınlar"],
    "gaziantep": ["Beğendik", "İmza Market"],
    "kayseri": ["Zafer Market", "Beğendik"],
}

CITY_LABELS: dict[str, str] = {
    "tokat": "Tokat",
    "istanbul": "İstanbul",
    "ankara": "Ankara",
    "izmir": "İzmir",
    "bursa": "Bursa",
    "antalya": "Antalya",
    "adana": "Adana",
    "konya": "Konya",
    "samsun": "Samsun",
    "trabzon": "Trabzon",
    "gaziantep": "Gaziantep",
    "kayseri": "Kayseri",
}


def cities() -> list[dict]:
    return [{"key": key, "label": CITY_LABELS.get(key, key.title())}
            for key in sorted(LOCAL_MARKETS.keys())]


def markets_for(city: str) -> dict[str, list[str]]:
    """Şehir için ulusal ve yerel market listelerini döndürür."""
    return {
        "national": list(NATIONAL_MARKETS),
        "local": LOCAL_MARKETS.get(city_key(city), []),
    }


def all_markets_for(city: str) -> list[str]:
    m = markets_for(city)
    return m["national"] + m["local"]
