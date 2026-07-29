"""HTTP rotaları.

Geriye dönük uyumlu tekil-market uçları + yeni sürüm uçları:

    GET  /health                     -> servis durumu
    GET  /cities                     -> desteklenen şehirler
    GET  /markets/<city>             -> şehirdeki ulusal + yerel marketler
    GET  /search/<city>/<itemName>   -> birleşik arama (ulusal + crowdsourced)
    POST /prices                     -> crowdsourced fiyat gönder (OCR sonucu)
    POST /basket/optimize            -> sepeti marketler arası optimize et
    GET  /history/<market>/<itemName>-> fiyat geçmişi

    GET  /migros|a101|sok|carrefour|erenler/<itemName>  (eski uyumluluk)
"""
from __future__ import annotations

from flask import Blueprint, jsonify, request

from app import cache, db, services
from app import registry
from app.models import Item
from app.scrapers import SCRAPERS
from config import Config

api = Blueprint("api", __name__)


@api.get("/health")
def health():
    return jsonify(
        {
            "status": "ok",
            "stores": list(SCRAPERS.keys()),
            # Hangi kaynağın çalıştığını deploy sonrası tek bakışta görebilmek için.
            "source": {
                "national": "marketfiyati.org.tr",
                "mock": Config.USE_MOCK,
                "legacyScrapers": Config.ENABLE_LEGACY_SCRAPERS,
            },
            # Önbelleğin gerçekten dolduğunu deploy sonrası görebilmek için.
            "cache": {"entries": cache.size(), "ttl": Config.SEARCH_CACHE_TTL},
        }
    )


@api.get("/cities")
def cities():
    return jsonify(registry.cities())


@api.get("/markets/<city>")
def markets(city: str):
    return jsonify(registry.markets_for(city))


@api.get("/search/<city>/<path:item_name>")
def search(city: str, item_name: str):
    items = services.search(city, item_name)
    response = jsonify([it.to_dict() for it in items])
    # Fiyatlar günlük tazeleniyor; istemci "Son güncelleme" rozetini bundan çizer.
    # Gövde şeması (List<Item>) sabit sözleşme olduğu için zaman damgası başlıkla
    # taşınır — aynı önbellek girdisinden gelir, ek istek yok.
    updated_at = services.national_updated_at(city, item_name)
    if updated_at:
        response.headers["X-Data-Updated"] = updated_at
    return response


@api.post("/prices")
def submit_price():
    """Crowdsourced fiyat gönderimi (raf etiketi OCR sonucu)."""
    data = request.get_json(silent=True) or {}
    required = ("city", "market", "name", "price")
    if not all(data.get(k) not in (None, "") for k in required):
        return jsonify({"error": "city, market, name, price zorunludur"}), 400

    price = Item.normalize_price(str(data["price"]))
    db.add_crowd_price(
        city=data["city"],
        market=data["market"],
        name=data["name"],
        price=float(price),
        image=data.get("image"),
    )
    return jsonify({"status": "ok"}), 201


@api.post("/basket/optimize")
def basket_optimize():
    data = request.get_json(silent=True) or {}
    city = data.get("city", "")
    items = data.get("items", [])
    if not isinstance(items, list) or not items:
        return jsonify({"error": "items boş olamaz"}), 400
    return jsonify(services.optimize_basket(city, items))


@api.get("/history/<market>/<path:item_name>")
def history(market: str, item_name: str):
    return jsonify(services.history(market, item_name))


# --- Geriye dönük uyumluluk: tekil market araması ---
@api.get("/<store>/<path:item_name>")
def search_store(store: str, item_name: str):
    scraper = SCRAPERS.get(store.lower())
    if scraper is None:
        return jsonify({"error": f"Bilinmeyen market: {store}"}), 404
    items = scraper.fetch(item_name)
    return jsonify([it.to_dict() for it in items])
