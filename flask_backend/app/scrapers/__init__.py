"""Scraper kayıt tablosu — EMEKLİ (deprecated).

Ulusal market fiyatları artık `app.sources.marketfiyati` üzerinden, kamuya
açık ve mevzuat gereği paylaşılan veriden geliyor. Market sitelerini doğrudan
kazımak TTK m.55 (haksız rekabet), sui generis veritabanı hakkı ve site kullanım
şartları açısından risk taşıdığı için varsayılan olarak **kapalı**.

Bu paket iki sebeple duruyor:
  1. Eski `/migros/<ürün>` tarzı endpoint'ler hâlâ yanıt vermeli (eski istemci
     sürümleri sahada). Kapalıyken bu uçlar mock/boş döner.
  2. Yerel `erenler` gibi, açık izin alınabilecek küçük marketler için iskelet.

Açmak için: ENABLE_LEGACY_SCRAPERS=true (yalnız yerel geliştirme).
"""
from app.scrapers.migros import MigrosScraper
from app.scrapers.a101 import A101Scraper
from app.scrapers.sok import SokScraper
from app.scrapers.carrefour import CarrefourScraper
from app.scrapers.erenler import ErenlerScraper

SCRAPERS = {
    "migros": MigrosScraper(),
    "a101": A101Scraper(),
    "sok": SokScraper(),
    "carrefour": CarrefourScraper(),
    "erenler": ErenlerScraper(),
}

# Geriye dönük uyumluluk için duruyor; `services.search()` artık kullanmıyor.
NATIONAL_KEYS = ["migros", "a101", "sok", "carrefour"]

__all__ = ["SCRAPERS", "NATIONAL_KEYS"]
