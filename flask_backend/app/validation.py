"""Crowdsourced fiyat gönderimlerinin doğrulanması.

Bu uçtan gelen veri **güvenilmez**: raf etiketi fotoğrafını OCR okuyor ve sonucu
kimse onaylamadan yayına giriyor. Doğrulama olmadan tek bir kötü okuma bütün
uygulamayı bozuyordu:

- `Item.normalize_price("bozuk")` sözleşme gereği `"0.00"` döndürür. Arama
  sonuçları fiyata göre sıralandığı için bu satır **her aramada birinci** olur,
  "EN UCUZ" rozetini alır ve sepet optimizasyonunda o marketi ücretsiz gösterir.
- Market adı serbest metindi: gönderim, şehirde var olmayan bir markete —
  hatta rastgele bir dizgeye — düşebiliyordu.
- Ondalık kayması (15,95 yerine 1595) tek başına fiyat geçmişini de bozuyordu.

Kural seti bilinçli olarak **geniş**: amaç dolandırıcıyı değil, bariz bozuk
okumayı elemek. Yerel marketin ulusal zincirden gerçekten ucuz olması bu
uygulamanın var oluş sebebi, o yüzden "şüpheli ucuz" diye eleme yapılmaz.
"""
from __future__ import annotations

import threading
import time

from app import registry
from app.models import Item

# Bir raf etiketinin makul aralığı. Alt sınır sıfırı ve negatifi eler; üst sınır
# ondalık kaymasını (1595,00) yakalar. Market ürünü için 20.000 ₺ fazlasıyla
# cömert — pahalı içki/elektronik bile bunun altında kalır.
MIN_PRICE = 0.10
MAX_PRICE = 20_000.0

MAX_NAME_LENGTH = 80
MIN_NAME_LENGTH = 2


class ValidationError(ValueError):
    """Gönderim reddedildi. `message` doğrudan kullanıcıya gösterilebilir."""

    def __init__(self, message: str):
        super().__init__(message)
        self.message = message


def clean_submission(data: dict) -> dict:
    """Ham gönderim gövdesini doğrulayıp normalleştirir.

    @return db.add_crowd_price'a doğrudan verilebilecek alanlar
    @raise ValidationError kullanıcıya gösterilecek Türkçe gerekçeyle
    """
    city = str(data.get("city") or "").strip()
    market = str(data.get("market") or "").strip()
    name = str(data.get("name") or "").strip()
    raw_price = data.get("price")

    if not city or not market or not name or raw_price in (None, ""):
        raise ValidationError("city, market, name, price zorunludur")

    key = registry.city_key(city)
    if key not in registry.LOCAL_MARKETS:
        raise ValidationError(f"Bilinmeyen şehir: {city}")

    # Ulusal zincirler de kabul edilir: kullanıcı marketfiyati.org.tr'nin
    # bilmediği bir şubede farklı bir etiket görmüş olabilir. Kabul edilmeyen,
    # o şehirle hiç ilgisi olmayan bir market adı.
    allowed = registry.all_markets_for(city)
    if market not in allowed:
        raise ValidationError(f"{city} için tanımsız market: {market}")

    if not MIN_NAME_LENGTH <= len(name) <= MAX_NAME_LENGTH:
        raise ValidationError(
            f"Ürün adı {MIN_NAME_LENGTH}-{MAX_NAME_LENGTH} karakter olmalı"
        )

    # `normalize_price` okunamayan girdide "0.00" döndürür — burada bu bir hata
    # değil, *sinyal*: OCR fiyatı çıkaramamış demektir.
    price = float(Item.normalize_price(str(raw_price)))
    if not MIN_PRICE <= price <= MAX_PRICE:
        raise ValidationError(
            f"Fiyat {MIN_PRICE:.2f} - {MAX_PRICE:.0f} ₺ aralığında olmalı "
            f"(okunan: {price:.2f} ₺)"
        )

    image = data.get("image")
    return {
        "city": city,
        "market": market,
        "name": name,
        "price": price,
        "image": image if isinstance(image, str) and image.strip() else None,
    }


def is_plausible(price: float, national_prices: list[float]) -> bool:
    """Crowdsourced fiyat, aynı aramanın ulusal fiyatlarına göre makul mü?

    `clean_submission` mutlak sınırlara bakar ve ürünün ne olduğunu bilmez:
    "1595,00 ₺ süt" onun için geçerlidir. Bu kontrol bağlamı kullanır ve okuma
    anında çalışır — böylece kural değişince eski kayıtlar da yeniden süzülür,
    veritabanını geri dönülmez biçimde budamayız.

    Eşikler kasıtlı olarak gevşek (0,2x - 5x): yerel marketin ulusal zincirin
    yarı fiyatına satması olağan ve uygulamanın tam da göstermek istediği şey.
    Elenen, büyüklük mertebesi kaçmış okumalar.
    """
    if price <= 0:
        return False
    if not national_prices:
        # Karşılaştıracak bir şey yok; mutlak sınırlar zaten uygulandı.
        return True
    return min(national_prices) * 0.2 <= price <= max(national_prices) * 5


# --- Hız sınırı -------------------------------------------------------------
#
# Süreç içi ve IP başına. Kapsam dar tutuldu: amaç kötü niyetli bir botu
# durdurmak değil (o iş ters vekil/WAF katmanına ait), döngüye girmiş bir
# istemcinin veritabanını saniyeler içinde doldurmasını engellemek.
# Çok işçili dağıtımda her işçinin kendi sayacı olur; sınır işçi sayısıyla
# çarpılır, bu kabul edilebilir.

RATE_LIMIT_MAX = 20
RATE_LIMIT_WINDOW = 600.0  # saniye

_rate_lock = threading.Lock()
_hits: dict[str, list[float]] = {}


def check_rate_limit(client_id: str) -> bool:
    """@return gönderime izin varsa True. Sınır aşıldıysa False."""
    now = time.monotonic()
    cutoff = now - RATE_LIMIT_WINDOW

    with _rate_lock:
        recent = [t for t in _hits.get(client_id, []) if t > cutoff]
        if len(recent) >= RATE_LIMIT_MAX:
            _hits[client_id] = recent
            return False
        recent.append(now)
        _hits[client_id] = recent

        # Sözlük sınırsız büyümesin: penceresi dolmuş istemcileri at.
        if len(_hits) > 1000:
            for key in [k for k, v in _hits.items() if not v or max(v) <= cutoff]:
                del _hits[key]

    return True


def reset_rate_limit() -> None:
    """Testler için."""
    with _rate_lock:
        _hits.clear()
