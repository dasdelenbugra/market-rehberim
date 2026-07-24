"""Migros (Sanal Market) scraper'ı.

NOT: Market siteleri sık sık HTML yapısını değiştirir ve dinamik (JS ile)
içerik yükleyebilir. Aşağıdaki CSS seçicileri örnek/başlangıç niteliğindedir;
gerçek ortamda site yapısına göre güncellenmelidir. Seçiciler tutmazsa akış
otomatik olarak mock veriye düşer (bkz. BaseScraper.fetch).
"""
from __future__ import annotations

from urllib.parse import quote

from bs4 import BeautifulSoup

from app.models import Item
from app.scrapers.base import BaseScraper
from app.scrapers._mock import mock_items


class MigrosScraper(BaseScraper):
    store_name = "Migros"
    BASE = "https://www.migros.com.tr"

    def search_url(self, query: str) -> str:
        return f"{self.BASE}/arama?q={quote(query)}"

    def parse(self, soup: BeautifulSoup) -> list[Item]:
        items: list[Item] = []
        for card in soup.select("mat-card.product-card, div.product-card"):
            name_el = card.select_one(".product-name, .name")
            price_el = card.select_one(".price, .sale-price")
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