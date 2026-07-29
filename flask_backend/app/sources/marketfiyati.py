"""marketfiyati.org.tr (TÜBİTAK BİLGEM) veri kaynağı.

Neden bu kaynak
---------------
200'den fazla şubesi olan hızlı tüketim zincirleri, Perakende Ticarette
Uygulanacak İlke ve Kurallar Hakkında Yönetmelik değişikliği uyarınca ürün ve
şube verilerini Ticaret Bakanlığı'nın belirlediği sisteme aktarmakla yükümlü.
Bu veri TÜBİTAK BİLGEM tarafından işlenip marketfiyati.org.tr üzerinden
kamuoyuna açılıyor — açıkça "tüketicinin fiyat karşılaştırması yapabilmesi"
amacıyla. Kapsam: A101, BİM, CarrefourSA, HAKMAR, Migros, Tarım Kredi, ŞOK.

Market sitelerini tek tek kazımaya göre farkı:
  * Kaynak zaten kamuya açılmış veri — haksız rekabet / veritabanı hakkı /
    site kullanım şartları ihlali riski yok.
  * HTML seçicileri kırılmıyor; şema stabil.
  * Şube (depot) düzeyinde konum bilgisi geliyor → şehir bazlı karşılaştırma.

Tazelik
-------
Marketler veriyi gün içinde besliyor; **anlık değil**. Kayıtlarda gelen zaman
damgası `Item`'a taşınamıyor (Android sözleşmesi sabit), bu yüzden fiyatlarla
**aynı istekte** toplanıp `fetch_with_meta()` ile ayrıca döndürülür. Rota bunu
`X-Data-Updated` yanıt başlığına koyar; UI'da "son güncelleme" olarak gösterilir.

Ağ hatası, kaynağın kapanması veya şema değişimi durumunda akış sessizce boş
listeye (veya `MOCK_FALLBACK` açıksa mock veriye) düşer — servis ayakta kalır.
"""
from __future__ import annotations

import logging
from datetime import datetime
from typing import Any, Iterable

import requests

from app.models import Item
from app.scrapers._mock import mock_items
from app.text import fold
from config import Config

logger = logging.getLogger(__name__)

#: Kaynak API'nin döndürdüğü market adlarını uygulama genelinde tek biçime çeker.
#: Anahtarlar `fold()` çıktısıyla karşılaştırılır: küçük harf, ASCII, yalnız
#: harf/rakam. Bu yüzden burada da boşluk ve noktalama bulunmamalı.
_MARKET_ALIASES = {
    "a101": "A101",
    "bimbirlesikmagazalar": "BİM",
    "bim": "BİM",
    "carrefoursa": "CarrefourSA",
    "carrefour": "CarrefourSA",
    "hakmarexpress": "Hakmar",
    "hakmar": "Hakmar",
    "migros": "Migros",
    "turkiyetarimkredikooperatifleri": "Tarım Kredi",
    "tarimkredikoop": "Tarım Kredi",
    "tarimkredi": "Tarım Kredi",
    "sokmarket": "ŞOK",
    "sok": "ŞOK",
}

def _canonical_market(raw: str) -> str:
    """'MIGROS', 'Migros Ticaret A.Ş.' → 'Migros'. Bilinmeyen ad olduğu gibi döner."""
    if not raw:
        return "Bilinmeyen"
    key = fold(raw)
    # Uzun takma adlar önce denenir: 'tarimkredi' kısa eşleşmesi
    # 'turkiyetarimkredikooperatifleri' girdisini kaçırmasın diye hem ön ek
    # hem de içerme kontrolü yapılır.
    for alias in sorted(_MARKET_ALIASES, key=len, reverse=True):
        if key.startswith(alias) or alias in key:
            return _MARKET_ALIASES[alias]
    return raw.strip()


#: Kaynağın gördüğümüz zaman damgası biçimleri. İlk eşleşen kazanır; damga bu
#: biçimlerin hiçbirine uymazsa yok sayılır (ISO'ya çeviremediğimizi göstermeyiz).
_STAMP_FORMATS = (
    "%Y-%m-%dT%H:%M:%S",
    "%Y-%m-%dT%H:%M",
    "%d.%m.%Y %H:%M:%S",
    "%d.%m.%Y %H:%M",
    "%d.%m.%Y",
)


def _parse_stamp(raw: str) -> datetime | None:
    """'25.07.2026 03:10' / '2026-07-25T03:10:00' → datetime. Tanınmazsa None."""
    text = raw.strip()
    for fmt in _STAMP_FORMATS:
        try:
            return datetime.strptime(text, fmt)
        except ValueError:
            continue
    return None


def _first(mapping: dict, *keys: str, default: Any = None) -> Any:
    """Sözlükten ilk dolu anahtarı çeker.

    Kaynak API alan adlarını sürüm sürüm değiştirebiliyor (`marketAdi` /
    `depotName` / `market`). Tek bir ada bağlanmak yerine adayları sırayla
    deniyoruz — şema oynadığında kaynak sessizce boş dönmesin.
    """
    for key in keys:
        value = mapping.get(key)
        if value not in (None, "", []):
            return value
    return default


class MarketFiyatiSource:
    """Ulusal zincir fiyatları için kamuya açık kaynak istemcisi."""

    name = "marketfiyati.org.tr"

    def fetch(self, query: str, latitude: float, longitude: float) -> list[Item]:
        """Sadece fiyat listesi isteyen çağrılar için `fetch_with_meta` sarmalayıcısı."""
        return self.fetch_with_meta(query, latitude, longitude)[0]

    def fetch_with_meta(
        self, query: str, latitude: float, longitude: float
    ) -> tuple[list[Item], str | None]:
        """Fiyatlar **ve** en yeni indeksleme zamanını (ISO metin) tek istekte döndürür.

        İkinci değer 'son güncelleme' rozetini besler; kaynak günlük tazelendiği
        için ayrı bir sorgu israf olurdu. Hata durumları yukarı sızmaz: boş liste
        (veya izinliyse mock) + `None` döner.
        """
        if Config.USE_MOCK:
            return self._mock(query), None

        try:
            payload = self._request(query, latitude, longitude)
            items = self._parse(payload)
            if not items and Config.MOCK_FALLBACK:
                logger.warning("marketfiyati: '%s' için sonuç yok, mock'a düşülüyor", query)
                return self._mock(query), None
            return items, self._latest_index_time(payload)
        except Exception as exc:  # ağ/şema hatasını yut, servisi ayakta tut
            logger.warning("marketfiyati kaynak hatası (%s): %s", query, exc)
            if Config.MOCK_FALLBACK:
                return self._mock(query), None
            return [], None

    # --- İç akış --------------------------------------------------------
    def _request(self, query: str, latitude: float, longitude: float) -> dict:
        body = {
            "keywords": query.strip(),
            "latitude": latitude,
            "longitude": longitude,
            "distance": Config.MARKETFIYATI_DISTANCE_KM,
            "size": Config.MARKETFIYATI_PAGE_SIZE,
            "pages": 0,
        }
        response = requests.post(
            f"{Config.MARKETFIYATI_BASE_URL}/search",
            json=body,
            timeout=Config.REQUEST_TIMEOUT,
            headers={
                "Content-Type": "application/json",
                "Accept": "application/json, text/plain, */*",
                "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8",
                "Origin": "https://marketfiyati.org.tr",
                "Referer": "https://marketfiyati.org.tr/",
                "Sec-Fetch-Site": "same-site",
                "Sec-Fetch-Mode": "cors",
                "Sec-Fetch-Dest": "empty",
                "User-Agent": (
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) "
                    "Chrome/124.0.0.0 Safari/537.36"
                ),
            },
        )
        response.raise_for_status()
        return response.json() or {}

    def _parse(self, payload: dict) -> list[Item]:
        products = _first(payload, "content", "products", "data", default=[]) or []

        # (market, ürün adı) -> en ucuz kayıt. Aynı zincirin birden çok şubesi
        # dönebiliyor; kullanıcıya markette bulabileceği en düşük fiyat gösterilir.
        best: dict[tuple[str, str], Item] = {}

        for product in products:
            if not isinstance(product, dict):
                continue
            title = str(_first(product, "title", "name", "productName", default="")).strip()
            if not title:
                continue
            image = str(_first(product, "imageUrl", "image", "imageURL", default="") or "")

            for depot in self._depots(product):
                market = _canonical_market(
                    str(_first(depot, "marketAdi", "market", "depotName", "name", default=""))
                )
                raw_price = _first(depot, "price", "unitPrice", "salePrice")
                if raw_price is None:
                    continue
                price = Item.normalize_price(raw_price)
                if float(price) <= 0:
                    continue

                key = (market, title.lower())
                current = best.get(key)
                if current is None or float(price) < float(current.price):
                    best[key] = Item(name=title, price=price, image=image, source=market)

        items = list(best.values())
        items.sort(key=lambda it: float(it.price))
        return items

    @staticmethod
    def _depots(product: dict) -> Iterable[dict]:
        depots = _first(
            product,
            "productDepotInfoList",
            "productDepotInfo",
            "depotInfoList",
            "depots",
            default=[],
        ) or []
        if isinstance(depots, dict):
            depots = [depots]
        return [d for d in depots if isinstance(d, dict)]

    @staticmethod
    def _mock(query: str) -> list[Item]:
        items: list[Item] = []
        for store in ("Migros", "A101", "ŞOK", "CarrefourSA"):
            items.extend(mock_items(store, query))
        items.sort(key=lambda it: float(it.price))
        return items

    def _latest_index_time(self, payload: dict) -> str | None:
        """Yanıttaki en yeni indeksleme zamanı, ISO 8601 metni olarak.

        Kaynak farklı biçimler kullanabildiği için (`2026-07-25T03:10:00` ya da
        `25.07.2026 03:10`) her damga ayrıştırılıp en yenisi ISO'ya çevrilir;
        böylece istemci tek bir biçimle uğraşır. Metinleri ham hâlde `max()` ile
        karşılaştırmak yanlış olurdu ('27.07' > '05.08' sözlük sırasında). Hiçbir
        damga ayrıştırılamazsa `None` döner — biçimini bilmediğimizi göstermeyiz.
        """
        latest: datetime | None = None
        for product in _first(payload, "content", "products", "data", default=[]) or []:
            if not isinstance(product, dict):
                continue
            for depot in self._depots(product):
                raw = _first(depot, "indexTime", "updateTime", "lastUpdate", "date")
                if not raw:
                    continue
                stamp = _parse_stamp(str(raw))
                if stamp is not None and (latest is None or stamp > latest):
                    latest = stamp
        return latest.isoformat() if latest is not None else None
