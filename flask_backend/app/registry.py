"""Şehir ve market kaydı.

Ulusal marketlerin fiyatı marketfiyati.org.tr'den (kamuya açık, mevzuat gereği
bildirilen veri) şube konumuyla birlikte gelir; bu yüzden her şehrin bir merkez
koordinatı var. Yerel marketlerin çoğunun web sitesi yoktur; bunların fiyatları
crowdsourced (kullanıcı katkısı) olarak toplanır.
"""
from __future__ import annotations

from app.text import fold

# Ulusal marketler: marketfiyati.org.tr kapsamındaki zincirler ve il kapsamları.
#
# `None` = 81 ilin tamamında şubesi var (BİM/A101/ŞOK/Migros ülke çapında;
# Tarım Kredi kooperatif ağıyla her bölgede). Küme = yalnız o illerde şubesi
# var. Eskiden 7 zincir her ilde listeleniyordu ve Sivas'ta Hakmar (Marmara
# bölge zinciri) görünüyordu — kullanıcı şehrinde olmayan bir marketi arayıp
# boş sonuç alıyordu.
#
# Not: Bu liste anasayfadaki "şehrindeki marketler" çiplerini ve crowdsourced
# gönderim doğrulamasını besler; aramanın kendisi konum bazlıdır ve kaynak,
# şubesi olmayan zinciri zaten döndürmez. Kapsamlar elle tutulur ve
# yaklaşıktır; zincir yeni ile girerse buraya eklenir.
NATIONAL_COVERAGE: dict[str, set[str] | None] = {
    "Migros": None,
    "A101": None,
    "BİM": None,
    "ŞOK": None,
    "Tarım Kredi": None,
    # Batı ve güney illeri + büyükşehirler ağırlıklı.
    "CarrefourSA": {
        "adana", "ankara", "antalya", "aydin", "balikesir", "bursa",
        "canakkale", "denizli", "edirne", "eskisehir", "gaziantep", "hatay",
        "istanbul", "izmir", "kahramanmaras", "kayseri", "kirklareli",
        "kocaeli", "konya", "manisa", "mersin", "mugla", "osmaniye",
        "sakarya", "samsun", "sanliurfa", "tekirdag", "trabzon", "yalova",
    },
    # Marmara bölge zinciri.
    "Hakmar": {
        "bursa", "duzce", "istanbul", "kocaeli", "sakarya", "tekirdag",
        "yalova",
    },
}

# Geriye dönük uyum: zincirlerin tam listesi (kapsamdan bağımsız).
NATIONAL_MARKETS = list(NATIONAL_COVERAGE)

# 81 il: (etiket, enlem, boylam). Ulusal kaynak (marketfiyati.org.tr) ülke
# çapında veri döndürdüğü için her il desteklenir; yerel market listesi ayrıdır
# (`LOCAL_MARKETS`) ve yalnız bilinen şehirlerde doludur.
#
# Anahtar/etiket tutarlılığı: sözlükler bu listeden `fold()` ile üretilir —
# elle iki listeyi senkron tutmak, "İğdır" gibi bir ilde anahtarın sessizce
# kaymasıyla sonuçlanırdı.
PROVINCES: list[tuple[str, float, float]] = [
    ("Adana", 37.0000, 35.3213),
    ("Adıyaman", 37.7648, 38.2786),
    ("Afyonkarahisar", 38.7507, 30.5567),
    ("Ağrı", 39.7191, 43.0503),
    ("Aksaray", 38.3687, 34.0370),
    ("Amasya", 40.6499, 35.8353),
    ("Ankara", 39.9334, 32.8597),
    ("Antalya", 36.8969, 30.7133),
    ("Ardahan", 41.1105, 42.7022),
    ("Artvin", 41.1828, 41.8183),
    ("Aydın", 37.8560, 27.8416),
    ("Balıkesir", 39.6484, 27.8826),
    ("Bartın", 41.6344, 32.3375),
    ("Batman", 37.8812, 41.1351),
    ("Bayburt", 40.2552, 40.2249),
    ("Bilecik", 40.1501, 29.9831),
    ("Bingöl", 38.8854, 40.4980),
    ("Bitlis", 38.4006, 42.1095),
    ("Bolu", 40.7392, 31.6089),
    ("Burdur", 37.7203, 30.2908),
    ("Bursa", 40.1826, 29.0665),
    ("Çanakkale", 40.1553, 26.4142),
    ("Çankırı", 40.6013, 33.6134),
    ("Çorum", 40.5506, 34.9556),
    ("Denizli", 37.7765, 29.0864),
    ("Diyarbakır", 37.9144, 40.2306),
    ("Düzce", 40.8438, 31.1565),
    ("Edirne", 41.6818, 26.5623),
    ("Elazığ", 38.6810, 39.2264),
    ("Erzincan", 39.7500, 39.5000),
    ("Erzurum", 39.9000, 41.2700),
    ("Eskişehir", 39.7767, 30.5206),
    ("Gaziantep", 37.0662, 37.3833),
    ("Giresun", 40.9128, 38.3895),
    ("Gümüşhane", 40.4386, 39.5086),
    ("Hakkari", 37.5744, 43.7408),
    ("Hatay", 36.4018, 36.3498),
    ("Iğdır", 39.8880, 44.0048),
    ("Isparta", 37.7648, 30.5566),
    ("İstanbul", 41.0082, 28.9784),
    ("İzmir", 38.4237, 27.1428),
    ("Kahramanmaraş", 37.5858, 36.9371),
    ("Karabük", 41.2061, 32.6204),
    ("Karaman", 37.1759, 33.2287),
    ("Kars", 40.6013, 43.0975),
    ("Kastamonu", 41.3887, 33.7827),
    ("Kayseri", 38.7312, 35.4787),
    ("Kırıkkale", 39.8468, 33.5153),
    ("Kırklareli", 41.7333, 27.2167),
    ("Kırşehir", 39.1425, 34.1709),
    ("Kilis", 36.7184, 37.1212),
    ("Kocaeli", 40.8533, 29.8815),
    ("Konya", 37.8746, 32.4932),
    ("Kütahya", 39.4167, 29.9833),
    ("Malatya", 38.3552, 38.3095),
    ("Manisa", 38.6191, 27.4289),
    ("Mardin", 37.3212, 40.7245),
    ("Mersin", 36.8000, 34.6333),
    ("Muğla", 37.2153, 28.3636),
    ("Muş", 38.9462, 41.7539),
    ("Nevşehir", 38.6939, 34.6857),
    ("Niğde", 37.9667, 34.6833),
    ("Ordu", 40.9839, 37.8764),
    ("Osmaniye", 37.0742, 36.2478),
    ("Rize", 41.0201, 40.5234),
    ("Sakarya", 40.6940, 30.4358),
    ("Samsun", 41.2867, 36.3300),
    ("Siirt", 37.9333, 41.9500),
    ("Sinop", 42.0231, 35.1531),
    ("Sivas", 39.7477, 37.0179),
    ("Şanlıurfa", 37.1591, 38.7969),
    ("Şırnak", 37.4187, 42.4918),
    ("Tekirdağ", 40.9833, 27.5167),
    ("Tokat", 40.3167, 36.5544),
    ("Trabzon", 41.0027, 39.7168),
    ("Tunceli", 39.3074, 39.4388),
    ("Uşak", 38.6823, 29.4082),
    ("Van", 38.4891, 43.4089),
    ("Yalova", 40.6500, 29.2667),
    ("Yozgat", 39.8181, 34.8147),
    ("Zonguldak", 41.4564, 31.7987),
]

# Şehir merkezi koordinatları (lat, lon). Ulusal kaynak konum bazlı sorgu
# istiyor; kullanıcıdan konum izni almadan da anlamlı sonuç dönebilmek için
# şehir merkezini kullanıyoruz. İstemci gerçek konum gönderirse o tercih edilir.
CITY_COORDS: dict[str, tuple[float, float]] = {
    fold(label): (lat, lon) for label, lat, lon in PROVINCES
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

# Yerel/bölgesel marketler: ulusal kaynakta (marketfiyati.org.tr yalnız 7 büyük
# zinciri kapsar) fiyatları YOK → fiyatlar yalnızca crowdsourced ("Fiyat
# Bildir") toplanır. Şehir anahtarı -> market listesi.
#
# Liste elle derlenir ve eksiktir; kullanıcı kendi şehrinin zincirini bildirirse
# buraya eklenir (örn. Pekdemir, Aydın kullanıcısının talebiyle eklendi).
LOCAL_MARKETS: dict[str, list[str]] = {
    "tokat": ["Erenler", "Mopaş"],
    # Hakmar yerel değil, kapsamı sınırlı ulusal zincir (NATIONAL_COVERAGE);
    # burada da listelenirse İstanbul'da çip iki kez görünür.
    "istanbul": ["Onur Market", "Seyhanlar"],
    "ankara": ["Yeni Mağazacılık", "Altunbilekler"],
    "izmir": ["Pehlivanoğlu", "Gürmar"],
    # Özhan Bursa zinciridir; yanlışlıkla İzmir'de listeleniyordu.
    "bursa": ["Özhan", "Kadıoğlu", "Uludağ Market"],
    "antalya": ["Miroğlu", "Çağrı Market"],
    "adana": ["Nazar Market", "Emek Market"],
    "konya": ["Adese", "Damla Market"],
    "samsun": ["Ela Market", "Kilim Market"],
    "trabzon": ["Ceyar Market", "Aydınlar"],
    "gaziantep": ["İmza Market"],
    "kayseri": ["Zafer Market"],
    # Pekdemir: Denizli merkezli, Aydın/Muğla'da da yaygın bölge zinciri.
    "aydin": ["Pekdemir"],
    "denizli": ["Pekdemir"],
    "mugla": ["Pekdemir"],
}

CITY_LABELS: dict[str, str] = {fold(label): label for label, _, _ in PROVINCES}

# Türk alfabesi sırası. `sorted()` Unicode kod noktasına göre sıralar ve
# "Çanakkale"yi Z'nin arkasına, "İstanbul"u listenin sonuna atar; kullanıcı
# ise il listesinde nüfus cüzdanındaki alfabeyi bekler.
_TR_ALPHABET = "abcçdefgğhıijklmnoöprsştuüvyz"
_TR_ORDER = {ch: i for i, ch in enumerate(_TR_ALPHABET)}


def _turkish_sort_key(label: str) -> tuple:
    lowered = label.translate(str.maketrans("İIÇĞÖŞÜ", "iıçğöşü")).lower()
    return tuple(_TR_ORDER.get(ch, len(_TR_ALPHABET)) for ch in lowered)


def cities() -> list[dict]:
    """Tüm iller, Türk alfabesine göre sıralı."""
    return [
        {"key": fold(label), "label": label}
        for label, _, _ in sorted(PROVINCES, key=lambda p: _turkish_sort_key(p[0]))
    ]


def markets_for(city: str) -> dict[str, list[str]]:
    """Şehir için ulusal ve yerel market listelerini döndürür.

    Ulusal liste il kapsamına göre süzülür: Sivas'ta Hakmar listelenmez.
    """
    key = city_key(city)
    return {
        "national": [
            market for market, coverage in NATIONAL_COVERAGE.items()
            if coverage is None or key in coverage
        ],
        "local": LOCAL_MARKETS.get(key, []),
    }


def all_markets_for(city: str) -> list[str]:
    m = markets_for(city)
    return m["national"] + m["local"]
