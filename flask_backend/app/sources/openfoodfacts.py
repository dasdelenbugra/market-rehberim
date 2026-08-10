"""Open Food Facts — barkodu ürün adına çevirir.

Neden ayrı bir kaynak
---------------------
Ulusal fiyat kaynağı (marketfiyati.org.tr) barkod tutmuyor. Döndürdüğü `id`
alanı `1YG9` gibi opak bir koddur ve barkodu anahtar kelime olarak aratmak sıfır
sonuç verir. Barkodla arama bu yüzden iki adımdır:

    barkod --(burası)--> ürün adı --(services.search)--> marketlerdeki fiyatlar

Open Food Facts açık veridir (ODbL), kayıt/anahtar istemez ve Türkiye için
8000'i aşkın ürünü barkoduyla birlikte tutar. Kapsam tam değildir; bulunamayan
barkodda `None` döner ve istemci kullanıcıyı elle aramaya yönlendirir —
uydurma isim üretmeyiz.

Nazik kullanım: OFF, kendini tanıtan bir `User-Agent` bekliyor ve sonuçlar
pratikte hiç değişmediği için (bir barkodun ürün adı sabittir) uzun süreli
önbelleğe alınır.
"""
from __future__ import annotations

import logging
import re
from dataclasses import dataclass

import requests

from config import Config

logger = logging.getLogger(__name__)

#: EAN-8/UPC-A/EAN-13/GTIN-14: yalnız rakam, 8-14 hane.
_BARCODE_RE = re.compile(r"^\d{8,14}$")


def is_valid(barcode: str) -> bool:
    """Kaynağa gitmeden önce biçim kontrolü — okuma hatası ağ isteğine dönüşmesin."""
    return bool(_BARCODE_RE.match(barcode.strip()))


@dataclass(frozen=True)
class BarcodeProduct:
    """Çözümlenen ürün.

    `query` ile `label` ayrı: ilki kaynağa gönderilen arama metni, ikincisi
    kullanıcıya "şunu okudum" diye gösterilecek ad. Şimdilik aynı üretiliyorlar
    ama arama metnini sadeleştirmek gerektiğinde etiket bozulmasın.
    """

    barcode: str
    name: str
    brand: str

    @property
    def query(self) -> str:
        """Fiyat aramasında kullanılacak metin: marka + ürün adı."""
        if not self.brand:
            return self.name
        # Ad zaten markayla başlıyorsa tekrar etme ("Ülker Ülker Çikolata").
        if self.brand.lower() in self.name.lower():
            return self.name
        return f"{self.brand} {self.name}".strip()

    @property
    def label(self) -> str:
        """Kullanıcıya gösterilecek ad."""
        return self.query


class OpenFoodFactsSource:
    """Barkod → ürün adı çözümleyicisi."""

    name = "openfoodfacts.org"

    def resolve(self, barcode: str) -> BarcodeProduct | None:
        """Barkodun ürün adı; bilinmiyorsa veya kaynağa ulaşılamazsa `None`.

        Hata yukarı sızmaz: barkod okuma tamamen ek bir kolaylık, kaynağın
        çökmesi uygulamanın arama akışını düşürmemeli.
        """
        code = barcode.strip()
        if not is_valid(code):
            return None

        try:
            payload = self._request(code)
        except Exception as exc:  # noqa: BLE001 - ağ/şema hatası aramayı bozmasın
            logger.warning("openfoodfacts kaynak hatası (%s): %s", code, exc)
            return None

        if payload.get("status") != 1:
            return None

        product = payload.get("product") or {}
        # Türkçe ad varsa o tercih edilir: kaynağımız Türkçe katalog, İngilizce
        # ad ("Full Fat Milk") ile arama sıfır sonuç verir.
        name = str(
            product.get("product_name_tr") or product.get("product_name") or ""
        ).strip()
        if not name:
            return None

        # `brands` virgülle ayrılmış olabiliyor ("Eti, Eti Lifalif"); ilki asıl marka.
        brand = str(product.get("brands") or "").split(",")[0].strip()
        return BarcodeProduct(barcode=code, name=name, brand=brand)

    def _request(self, code: str) -> dict:
        response = requests.get(
            f"{Config.OPENFOODFACTS_BASE_URL}/product/{code}.json",
            params={"fields": "code,product_name,product_name_tr,brands"},
            timeout=Config.REQUEST_TIMEOUT,
            headers={
                "Accept": "application/json",
                # OFF kendini tanıtan istemci bekliyor; tarayıcı taklidi yapmayız.
                "User-Agent": Config.CLIENT_USER_AGENT,
            },
        )
        response.raise_for_status()
        return response.json() or {}
