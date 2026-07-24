"""İş mantığı: arama toplama, sepet optimizasyonu, fiyat geçmişi.

- Ulusal marketler scraping/mock ile;
- Yerel marketler crowdsourced (SQLite) veriyle birleştirilir.
"""
from __future__ import annotations

import hashlib
from datetime import datetime, timedelta

from app import cache, db
from app.models import Item
from app.scrapers import SCRAPERS, NATIONAL_KEYS
from config import Config


def _fetch_national(name: str) -> list[Item]:
    """Dört ulusal marketten scraping/mock sonuçları. Pahalı olan kısım budur."""
    results: list[Item] = []
    for key in NATIONAL_KEYS:
        results.extend(SCRAPERS[key].fetch(name))
    return results


def search(city: str, name: str) -> list[Item]:
    """Bir şehir+ürün için ulusal ve yerel (crowdsourced) sonuçları birleştirir.

    Ulusal kısım önbelleğe alınır (şehirden bağımsız — aynı ulusal zincir her
    şehirde aynı fiyatı verir), yerel kısım her çağrıda tazeden okunur.
    """
    key = f"national:{name.strip().lower()}"
    national = cache.get_or_set(
        key,
        ttl=Config.SEARCH_CACHE_TTL,
        producer=lambda: _fetch_national(name),
    )

    # Önbellekten gelen liste paylaşılan bir nesne: kopyalanmadan üstüne
    # eklenirse sonraki isteklerde yerel sonuçlar birikir.
    results: list[Item] = list(national)

    # Yerel marketler (crowdsourced)
    for row in db.latest_crowd_prices(city, name):
        results.append(
            Item(
                name=row["name"],
                price=Item.normalize_price(str(row["price"])),
                image=row.get("image") or "",
                source=row["market"],
            )
        )

    results.sort(key=lambda it: float(it.price))
    return results


def optimize_basket(city: str, item_names: list[str]) -> dict:
    """Alışveriş listesini marketler arasında en ucuza dağıtır.

    İki çıktı üretir:
    - byMarket: her marketin sepet toplamı (tek markette alma senaryosu)
    - optimalSplit: her ürünü en ucuz marketten alma senaryosu
    """
    names = [n.strip() for n in item_names if n and n.strip()]

    # market -> { ürün adı -> en düşük fiyat }
    table: dict[str, dict[str, float]] = {}
    for name in names:
        best_per_market: dict[str, float] = {}
        for it in search(city, name):
            price = float(it.price)
            if it.source not in best_per_market or price < best_per_market[it.source]:
                best_per_market[it.source] = price
        for market, price in best_per_market.items():
            table.setdefault(market, {})[name] = price

    # Tek market senaryosu
    by_market = []
    for market, prices in table.items():
        by_market.append(
            {
                "market": market,
                "total": round(sum(prices.values()), 2),
                "foundCount": len(prices),
                "totalCount": len(names),
                "complete": len(prices) == len(names),
                "items": [
                    {"name": n, "price": f"{prices[n]:.2f}" if n in prices else None}
                    for n in names
                ],
            }
        )
    by_market.sort(key=lambda r: (not r["complete"], r["total"]))

    # Optimal dağıtım (her ürün en ucuz marketten)
    optimal_items = []
    grand_total = 0.0
    for name in names:
        best_market, best_price = None, None
        for market, prices in table.items():
            if name in prices and (best_price is None or prices[name] < best_price):
                best_market, best_price = market, prices[name]
        if best_price is not None:
            grand_total += best_price
        optimal_items.append(
            {
                "name": name,
                "market": best_market,
                "price": f"{best_price:.2f}" if best_price is not None else None,
            }
        )

    return {
        "byMarket": by_market,
        "optimalSplit": {"items": optimal_items, "total": round(grand_total, 2)},
    }


def history(market: str, name: str) -> list[dict]:
    """Fiyat geçmişi noktaları. Kayıt yoksa demo için sentetik seri üretir."""
    stored = db.price_history(market, name)
    if stored:
        return [{"price": f"{r['price']:.2f}", "date": r["created"][:10]} for r in stored]
    return _synthetic_history(market, name)


def _synthetic_history(market: str, name: str, days: int = 14) -> list[dict]:
    """Deterministik, hafif dalgalanan 14 günlük demo fiyat serisi."""
    seed = int(hashlib.md5(f"{market}:{name}".encode("utf-8")).hexdigest()[:6], 16)
    base = 20 + (seed % 8000) / 100.0
    today = datetime.now().date()
    series = []
    for i in range(days):
        day = today - timedelta(days=days - 1 - i)
        # Yavaş yükseliş + küçük salınım
        drift = 1 + (i * 0.015)
        wobble = 1 + (((seed >> i) % 7) - 3) / 100.0
        price = base * drift * wobble
        series.append({"price": f"{price:.2f}", "date": day.isoformat()})
    return series
