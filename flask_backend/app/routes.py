"""HTTP rotaları.

Geriye dönük uyumlu tekil-market uçları + yeni sürüm uçları:

    GET  /health                     -> servis durumu
    GET  /cities                     -> desteklenen şehirler
    GET  /markets/<city>             -> şehirdeki ulusal + yerel marketler
    GET  /search/<city>/<itemName>   -> birleşik arama (ulusal + crowdsourced)
    GET  /barcode/<city>/<code>      -> barkoddan ürün + fiyatlar
    POST /prices                     -> crowdsourced fiyat gönder (OCR sonucu)
    POST /basket/optimize            -> sepeti marketler arası optimize et
    GET  /history/<market>/<itemName>-> fiyat geçmişi

    GET  /migros|a101|sok|carrefour|erenler/<itemName>  (eski uyumluluk)
"""
from __future__ import annotations

from flask import Blueprint, jsonify, request

from app import cache, db, products as products_mod, services, validation
from app import registry
from app.scrapers import SCRAPERS
from app.sources import openfoodfacts
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


@api.get("/products/<city>/<path:item_name>")
def products(city: str, item_name: str):
    """Arama sonuçları **ürüne göre gruplanmış** hali.

    `/search` düz liste döndürmeye devam ediyor: ürün detayındaki market
    karşılaştırması ve sepet optimizasyonu onu kullanıyor, o sözleşme bozulmadı.
    Arama ekranı ise gruplu görünüme ihtiyaç duyuyor — gerekçe: `app.products`.
    """
    items = services.search(city, item_name)
    response = jsonify(products_mod.group(items, item_name))
    updated_at = services.national_updated_at(city, item_name)
    if updated_at:
        response.headers["X-Data-Updated"] = updated_at
    return response


@api.get("/barcode/<city>/<code>")
def barcode(city: str, code: str):
    """Barkodu okunan ürünün şehirdeki fiyatları.

    Gövde neden düz liste değil: `/search` bir sorgu metniyle çağrılır, istemci
    ne aradığını zaten bilir. Barkodda bilmiyor — ekranda "şu ürünü okudum"
    diyebilmesi için çözümlenen adı da döndürmek gerekiyor. Yeni bir uç olduğu
    için nesne döndürmek serbest; `/search`'ün liste sözleşmesi bozulmadı.

    404, "barkodu tanımadım" demektir ve "ürünü tanıdım ama fiyat bulamadım"
    (200 + boş `items`) durumundan ayrılır — kullanıcıya söylenecek şey farklı.
    """
    if not openfoodfacts.is_valid(code):
        return jsonify({"error": "Geçersiz barkod."}), 400

    product, items = services.search_by_barcode(city, code)
    if product is None:
        return jsonify({"error": "Bu barkod veritabanında yok."}), 404

    response = jsonify(
        {
            "barcode": product.barcode,
            "product": product.label,
            "items": [it.to_dict() for it in items],
        }
    )
    updated_at = services.national_updated_at(city, product.query)
    if updated_at:
        response.headers["X-Data-Updated"] = updated_at
    return response


@api.post("/prices")
def submit_price():
    """Crowdsourced fiyat gönderimi (raf etiketi OCR sonucu).

    Gövde doğrulanmadan yazılmaz: bu uç, kimsenin onaylamadığı OCR çıktısını
    doğrudan arama sonuçlarına sokan tek yol. Gerekçe için bkz. `app.validation`.
    """
    # `remote_addr` ters vekil arkasında hep aynı gelebilir; hız sınırı bu
    # durumda sıkılaşır ama açılmaz — güvenli taraf.
    client_id = request.headers.get("X-Forwarded-For", request.remote_addr or "-")
    client_id = client_id.split(",")[0].strip()
    if not validation.check_rate_limit(client_id):
        return jsonify({"error": "Çok fazla gönderim. Biraz sonra tekrar dene."}), 429

    try:
        clean = validation.clean_submission(request.get_json(silent=True) or {})
    except validation.ValidationError as err:
        return jsonify({"error": err.message}), 400

    db.add_crowd_price(**clean)
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
