"""Scraper kayıt tablosu.

Endpoint anahtarı -> scraper örneği eşlemesi. Rotalar bu sözlük üzerinden
çalışır, böylece yeni market eklemek tek satırlık bir kayıt işlemidir.

`erenler` yerel bir markettir; geriye dönük uyumluluk için endpoint'i korunur,
ancak yeni sürümde yerel market fiyatları öncelikle crowdsourced toplanır.
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

# Ulusal (scraping ile veri gelen) marketlerin scraper anahtarları.
NATIONAL_KEYS = ["migros", "a101", "sok", "carrefour"]

__all__ = ["SCRAPERS", "NATIONAL_KEYS"]
