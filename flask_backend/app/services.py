"""İş mantığı: arama toplama, sepet optimizasyonu, fiyat geçmişi.

- Ulusal marketler marketfiyati.org.tr (kamuya açık kaynak) ile;
- Yerel marketler crowdsourced (SQLite) veriyle birleştirilir.
"""
from __future__ import annotations

from concurrent.futures import ThreadPoolExecutor

from app import cache, db, registry, validation
from app.models import Item
from app.sources import BARCODE_SOURCE, NATIONAL_SOURCE, BarcodeProduct
from config import Config


def _fetch_national(city: str, name: str) -> tuple[list[Item], str | None]:
    """Şehir merkezine yakın zincir şubelerinin fiyatları + son güncelleme zamanı.

    Pahalı olan kısım budur; sonucu `_national_cached` önbelleğe alır.

    Taze çekilen fiyatlar aynı anda geçmişe de işlenir. Buraya konmasının sebebi
    yalnızca önbellek ıskalandığında çalışması: `search()` içine konsaydı her
    istek aynı fiyatı yeniden yazmaya çalışırdı.
    """
    latitude, longitude = registry.coords_for(city)
    items, updated_at = NATIONAL_SOURCE.fetch_with_meta(name, latitude, longitude)
    _record_history(items)
    return items, updated_at


def _record_history(items: list[Item]) -> None:
    """Fiyat geçmişini besler. Grafik verisi ancak buradan birikir.

    Hata yutulur: geçmiş kaydı yan iş, başarısız olması aramayı düşürmemeli.
    """
    rows = []
    for it in items:
        try:
            rows.append((it.source, it.name, float(it.price)))
        except (TypeError, ValueError):
            continue
    try:
        db.record_daily_prices(rows)
    except Exception:  # noqa: BLE001 - geçmiş kaydı aramayı bloklamamalı
        pass


def _national_cached(city: str, name: str) -> tuple[list[Item], str | None]:
    """Ulusal (fiyatlar, güncelleme) çiftini önbellekten döndürür.

    Önbellek anahtarı **şehri de içerir**: kaynak şube bazlı fiyat döndürdüğü için
    aynı zincirin fiyatı şehirden şehre değişebiliyor; eski şehirden bağımsız
    anahtar yanlış sonuç verirdi. Fiyatlar ve güncelleme zamanı aynı girdide
    tutulur — `search()` ile `national_updated_at()` tek çekimi paylaşır.
    """
    key = f"national:{registry.city_key(city)}:{name.strip().lower()}"
    return cache.get_or_set(
        key,
        ttl=Config.SEARCH_CACHE_TTL,
        producer=lambda: _fetch_national(city, name),
    )


def national_updated_at(city: str, name: str) -> str | None:
    """Ulusal fiyatların son indekslenme zamanı (ISO metin) — 'son güncelleme' rozeti.

    `search()` ile aynı önbellek girdisini okur; ek bir kaynak isteği yapmaz.
    """
    return _national_cached(city, name)[1]


def search(city: str, name: str) -> list[Item]:
    """Bir şehir+ürün için ulusal ve yerel (crowdsourced) sonuçları birleştirir.

    Ulusal kısım önbelleğe alınır (bkz. `_national_cached`). Yerel kısım her
    çağrıda tazeden okunur — kullanıcı raf etiketini gönderdikten hemen sonra
    kendi katkısını görmeli.
    """
    national, _ = _national_cached(city, name)

    # Önbellekten gelen liste paylaşılan bir nesne: kopyalanmadan üstüne
    # eklenirse sonraki isteklerde yerel sonuçlar birikir.
    results: list[Item] = list(national)

    # Ulusal fiyatlar, crowdsourced kayıtlar için ölçek referansı. Süzme yazma
    # anında değil burada yapılıyor: kural sonradan değişirse eski kayıtlar da
    # yeniden değerlendirilsin, veritabanını geri dönülmez biçimde budamayalım.
    reference = []
    for it in national:
        try:
            reference.append(float(it.price))
        except (TypeError, ValueError):
            continue

    # Yerel marketler (crowdsourced)
    for row in db.latest_crowd_prices(city, name):
        price = Item.normalize_price(str(row["price"]))
        if not validation.is_plausible(float(price), reference):
            continue
        results.append(
            Item(
                name=row["name"],
                price=price,
                image=row.get("image") or "",
                source=row["market"],
            )
        )

    results.sort(key=lambda it: float(it.price))
    return results


def resolve_barcode(barcode: str) -> BarcodeProduct | None:
    """Barkodu ürün adına çevirir (önbellekli).

    Bir barkodun ürün adı değişmediği için TTL uzun tutulur; aynı rafı tarayan
    kullanıcılar kaynağa tekrar tekrar gitmesin.
    """
    key = f"barcode:{barcode.strip()}"
    return cache.get_or_set(
        key,
        ttl=Config.BARCODE_CACHE_TTL,
        producer=lambda: BARCODE_SOURCE.resolve(barcode),
    )


def search_by_barcode(city: str, barcode: str) -> tuple[BarcodeProduct | None, list[Item]]:
    """Barkodu okunan ürünün şehirdeki fiyatları.

    İki adım: barkod → ürün adı (Open Food Facts), ürün adı → fiyatlar (ulusal
    kaynak). Ürün çözümlenemezse `(None, [])` döner; çağıran bunu "barkodu
    tanımadım" diye ayırt eder — "ürün var ama fiyat yok" ile aynı şey değil.

    Marka yedeği: tam ad sıfır sonuç verdiğinde yalnız markayla tekrar denenir.
    Open Food Facts'teki adlar kullanıcı katkısı olduğu için market
    katalogundakiyle birebir tutmayabiliyor ("Heinz Domates Ketçabı" → 0, ama
    "Heinz" → ketçap dahil tüm Heinz ürünleri). Yaklaşık sonuç, boş ekrandan iyi.
    """
    product = resolve_barcode(barcode)
    if product is None:
        return None, []

    items = search(city, product.query)
    if not items and product.brand and product.brand.lower() != product.query.lower():
        items = search(city, product.brand)
    return product, items


def _search_many(city: str, names: list[str]) -> list[tuple[str, list[Item]]]:
    """Birden çok ürünü paralel arar; sıra korunur.

    Sepet optimizasyonu ürün başına bir `search()` yapıyordu ve her biri önbellek
    ıskaladığında ulusal kaynağa gidiyordu: on ürünlük bir sepet **ardışık** on
    ağ çağrısı demekti ve kullanıcı yarım dakika bekliyordu. İş tamamen G/Ç
    beklemesi olduğu için iş parçacıkları burada uygundur (GIL darboğaz değil).

    Sınır 5: kaynağa aynı anda onlarca istek atıp hız sınırına takılmak,
    beklemekten daha kötü. Tek ürünlük sepette havuz hiç kurulmaz.
    """
    if len(names) <= 1:
        return [(name, search(city, name)) for name in names]

    with ThreadPoolExecutor(max_workers=min(5, len(names))) as pool:
        # `map` girdi sırasını korur; sonuçların sepet listesiyle hizalı kalması
        # `optimalSplit` çıktısının sırası için önemli.
        results = list(pool.map(lambda n: search(city, n), names))
    return list(zip(names, results))


def optimize_basket(city: str, item_names: list[str]) -> dict:
    """Alışveriş listesini marketler arasında en ucuza dağıtır.

    İki çıktı üretir:
    - byMarket: her marketin sepet toplamı (tek markette alma senaryosu)
    - optimalSplit: her ürünü en ucuz marketten alma senaryosu
    """
    names = [n.strip() for n in item_names if n and n.strip()]

    # market -> { ürün adı -> en düşük fiyat }
    table: dict[str, dict[str, float]] = {}
    for name, items in _search_many(city, names):
        best_per_market: dict[str, float] = {}
        for it in items:
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
    """Fiyat geçmişi noktaları. Kayıt yoksa boş liste döner.

    Eskiden burada, kayıt bulunamayınca `_synthetic_history()` ile uydurma bir
    14 günlük seri üretiliyordu. Kaldırıldı: ulusal fiyatlar geçmişe hiç
    yazılmadığı için o yedek pratikte **her zaman** devreye giriyordu ve
    kullanıcı gerçek sanılan bir grafiğe bakıyordu. Veri yoksa istemci grafiği
    gizler — uydurma fiyat göstermektense hiç göstermemek doğru.
    """
    stored = db.price_history(market, name)
    return [{"price": f"{r['price']:.2f}", "date": r["created"][:10]} for r in stored]
