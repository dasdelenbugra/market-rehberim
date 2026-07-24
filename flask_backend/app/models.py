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


@dataclass
class Item:
    name: str
    price: str
    image: str
    # `from` Python'da ayrılmış bir kelime olduğu için alan adı `source`,
    # JSON'a çevirirken `from` anahtarına eşlenir.
    source: str

    def to_dict(self) -> dict:
        data = asdict(self)
        data["from"] = data.pop("source")
        return data

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