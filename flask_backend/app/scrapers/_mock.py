"""Örnek (mock) veri üreticisi.

Gerçek scraping kapalıyken veya başarısız olduğunda kullanılır. Her market
için sorguya bağlı, deterministik ama gerçekçi görünen ürünler üretir; böylece
demo sırasında fiyat karşılaştırma davranışı net görülür.
"""
from __future__ import annotations

import hashlib

from app.models import Item

# Sorgudan bağımsız, marketten markete değişen taban fiyat çarpanları
_STORE_FACTORS = {
    "Migros": 1.00,
    "A101": 0.92,
    "ŞOK": 0.90,
    "CarrefourSA": 0.95,
    "Erenler": 0.88,
    "Mopaş": 0.87,
}

# `.png` uzantısı şart: uzantısız istek SVG döndürüyor, Glide ise SVG'yi
# ek bir decoder olmadan çözemiyor ve resim sessizce boş kalıyor.
_PLACEHOLDER_IMG = "https://placehold.co/200x200.png?text={label}"


def _base_price(query: str) -> float:
    """Sorgu metninden deterministik bir taban fiyat türetir (10–110 TL)."""
    digest = hashlib.md5(query.strip().lower().encode("utf-8")).hexdigest()
    return 10 + (int(digest[:4], 16) % 10000) / 100.0


def mock_items(store: str, query: str, variants: int = 3) -> list[Item]:
    query = (query or "ürün").strip()
    factor = _STORE_FACTORS.get(store, 1.0)
    base = _base_price(query)

    items: list[Item] = []
    for i in range(variants):
        price = base * factor * (1 + i * 0.15)
        label = f"{query.title()} {i + 1}"
        items.append(
            Item(
                name=f"{label} - {store}",
                price=Item.normalize_price(f"{price:.2f}"),
                image=_PLACEHOLDER_IMG.format(label=store),
                source=store,
            )
        )
    return items