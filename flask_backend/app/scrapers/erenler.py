"""Erenler Market scraper'ı (yerel/bölgesel market örneği).

Seçiciler örnek niteliğindedir; gerçek site yapısına göre güncellenmelidir.
Başarısızlıkta akış otomatik olarak mock veriye düşer.
"""
from __future__ import annotations

from urllib.parse import quote

from bs4 import BeautifulSoup

from app.models import Item
from app.scrapers.base import BaseScraper
from app.scrapers._mock import mock_items


class ErenlerScraper(BaseScraper):
    store_name = "Erenler"
    BASE = "https://www.erenlermarket.com.tr"

    def search_url(self, query: str) -> str:
        return f"{self.BASE}/ara?q={quote(query)}"

    def parse(self, soup: BeautifulSoup) -> list[Item]:
        items: list[Item] = []
        for card in soup.select("div.product-item, div.urun"):
            name_el = card.select_one(".product-title, .urun-adi, h3")
            price_el = card.select_one(".price, .fiyat")
            img_el = card.select_one("img")
            if not (name_el and price_el):
                continue
            items.append(
                Item(
                    name=name_el.get_text(strip=True),
                    price=Item.normalize_price(price_el.get_text(strip=True)),
                    image=(img_el.get("src") if img_el else "") or "",
                    source=self.store_name,
                )
            )
        return items

    def mock_items(self, query: str) -> list[Item]:
        return mock_items(self.store_name, query)