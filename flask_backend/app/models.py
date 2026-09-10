"""Veri modelleri.

`Item`, Android istemcisinin beklediği JSON şemasıyla birebir eşleşir:

    {
        "name":  "Ürün adı",
        "price": "12.50",   # nokta ondalıklı, string
        "image": "https://...",
        "from":  "Migros"
    }
"""
from dataclasses import dataclass, asdict

from app import units


@dataclass
class Item:
    name: str
    price: str
    image: str
    # `from` Python'da ayrılmış bir kelime olduğu için alan adı `source`,
    # JSON'a çevirirken `from` anahtarına eşlenir.
    source: str
    #: Kaynağın ürün kategorisi ("Meyve", "Bebek Mamaları", "Yumurta").
    #: Alaka katmanını belirlerken kullanılır (bkz. `app.products`). Kaynağın
    #: bildirmediği kayıtlarda (crowdsourced, yerel market) boş kalır — bu
    #: durumda alaka yalnız ada bakar.
    category: str = ""

    def to_dict(self) -> dict:
        """JSON gövdesi.

        `unitPrice`/`unit` alanları ada göre türetildiği için burada hesaplanır:
        `Item` üreten her yer (dört kaynak + crowdsourced okuma) ayrı ayrı
        doldurmak zorunda kalmasın. Miktar çıkarılamayan üründe `None` kalırlar
        ve istemci satırı göstermez — bkz. `app.units`.

        `category` bilerek dışarı verilmez: sınıflandırma sunucuda bitiyor,
        istemcinin tüketicisi yok. Sözleşmeyi tüketicisi olmayan alanla
        genişletmiyoruz.
        """
        data = asdict(self)
        data["from"] = data.pop("source")
        data.pop("category", None)

        derived = units.unit_price(self.price_value, self.name)
        data["unitPrice"], data["unit"] = derived if derived else (None, None)
        return data

    @property
    def price_value(self) -> float:
        """Sayısal fiyat; ayrıştırılamazsa 0.0."""
        try:
            return float(self.price)
        except (TypeError, ValueError):
            return 0.0

    @staticmethod
    def normalize_price(raw: str) -> str:
        """Ham fiyat metnini '12.50' biçimine getirir.

        Örn: '12,50 TL' -> '12.50', '1.299,00' -> '1299.00'.
        Android tarafı bu değeri `toDouble()` ile sıralar, bu yüzden
        her zaman geçerli bir sayı üretmek kritik.
        """
        if raw is None:
            return "0.00"
        cleaned = "".join(ch for ch in str(raw) if ch.isdigit() or ch in ",.")
        if not cleaned:
            return "0.00"
        # Binlik ayıracı olarak nokta, ondalık olarak virgül kullanan TR biçimi
        if "," in cleaned:
            cleaned = cleaned.replace(".", "").replace(",", ".")
        try:
            return f"{float(cleaned):.2f}"
        except ValueError:
            return "0.00"