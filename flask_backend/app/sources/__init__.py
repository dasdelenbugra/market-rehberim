"""Ulusal fiyat veri kaynakları.

`scrapers/` paketinden farkı: burası **izinli/kamuya açık** kaynaklardan veri
çeker. Market sitelerini doğrudan kazımak yerine, zincir marketlerin mevzuat
gereği bildirdiği ve TÜBİTAK BİLGEM tarafından kamuya açılan veriyi kullanır.

Bkz. `docs/VERI_KAYNAGI.md`.
"""
from app.sources.marketfiyati import MarketFiyatiSource
from app.sources.openfoodfacts import BarcodeProduct, OpenFoodFactsSource

#: Varsayılan ulusal kaynak.
NATIONAL_SOURCE = MarketFiyatiSource()

#: Barkod → ürün adı çözümleyicisi. Fiyat kaynağı barkod tutmadığı için ayrı.
BARCODE_SOURCE = OpenFoodFactsSource()

__all__ = [
    "MarketFiyatiSource",
    "NATIONAL_SOURCE",
    "OpenFoodFactsSource",
    "BARCODE_SOURCE",
    "BarcodeProduct",
]
