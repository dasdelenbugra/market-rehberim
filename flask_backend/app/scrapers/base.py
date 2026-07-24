"""Tüm market scraper'ları için ortak taban sınıf.

Yeni bir market eklemek için:
1. `BaseScraper`'dan türeyen bir sınıf yazın,
2. `store_name`, `search_url()` ve `parse()` üyelerini doldurun,
3. `scrapers/__init__.py` içindeki `SCRAPERS` sözlüğüne kaydedin.
"""
from __future__ import annotations

import logging
from abc import ABC, abstractmethod

import requests
from bs4 import BeautifulSoup

from config import Config
from app.models import Item

logger = logging.getLogger(__name__)


class BaseScraper(ABC):
    #: Kaynak market adı. Sonuçların `from` alanında görünür.
    store_name: str = "Unknown"

    @abstractmethod
    def search_url(self, query: str) -> str:
        """Arama sorgusu için hedef URL'yi döndürür."""

    @abstractmethod
    def parse(self, soup: BeautifulSoup) -> list[Item]:
        """Sayfa HTML'ini `Item` listesine çevirir."""

    @abstractmethod
    def mock_items(self, query: str) -> list[Item]:
        """Ağ olmadan dönecek örnek veriyi üretir."""

    # --- Ortak akış -----------------------------------------------------
    def fetch(self, query: str) -> list[Item]:
        """Yapılandırmaya göre gerçek veya mock veri döndürür.

        Hata durumları yukarı sızmaz; boş liste veya (izinliyse) mock döner.
        """
        if Config.USE_MOCK:
            return self.mock_items(query)

        try:
            html = self._download(self.search_url(query))
            soup = BeautifulSoup(html, "lxml")
            items = self.parse(soup)
            if not items and Config.MOCK_FALLBACK:
                logger.warning("%s: sonuç yok, mock veriye düşülüyor", self.store_name)
                return self.mock_items(query)
            return items
        except Exception as exc:  # ağ/parse hatalarını yut, servisi ayakta tut
            logger.warning("%s scraping hatası: %s", self.store_name, exc)
            if Config.MOCK_FALLBACK:
                return self.mock_items(query)
            return []

    def _download(self, url: str) -> str:
        response = requests.get(
            url,
            headers={"User-Agent": Config.USER_AGENT},
            timeout=Config.REQUEST_TIMEOUT,
        )
        response.raise_for_status()
        return response.text