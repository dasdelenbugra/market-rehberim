"""API testleri (mock modda, geçici DB ile — ağ gerektirmez)."""
import os
import tempfile

os.environ["USE_MOCK"] = "true"
os.environ["DB_PATH"] = os.path.join(tempfile.gettempdir(), "mr_test.sqlite")
# Her test oturumunda temiz başla
if os.path.exists(os.environ["DB_PATH"]):
    os.remove(os.environ["DB_PATH"])

import pytest

from app import create_app
from app.models import Item


@pytest.fixture()
def client():
    app = create_app()
    app.config.update(TESTING=True)
    return app.test_client()


def test_health(client):
    resp = client.get("/health")
    assert resp.status_code == 200
    assert resp.get_json()["status"] == "ok"


def test_cities(client):
    data = client.get("/cities").get_json()
    assert any(c["key"] == "tokat" for c in data)


def test_markets_for_city(client):
    data = client.get("/markets/tokat").get_json()
    assert "Migros" in data["national"]
    assert "Erenler" in data["local"]


def test_search_aggregates_markets(client):
    data = client.get("/search/tokat/süt").get_json()
    assert isinstance(data, list) and data
    markets = {row["from"] for row in data}
    # Ulusal marketler birleşik gelmeli
    assert {"Migros", "A101"} <= markets
    # Fiyata göre artan sıralı
    prices = [float(r["price"]) for r in data]
    assert prices == sorted(prices)


def test_submit_crowd_price_then_appears_in_search(client):
    resp = client.post(
        "/prices",
        json={"city": "tokat", "market": "Erenler", "name": "zeytin", "price": "45,90 TL"},
    )
    assert resp.status_code == 201
    data = client.get("/search/tokat/zeytin").get_json()
    assert any(r["from"] == "Erenler" and r["name"] == "zeytin" for r in data)


def test_submit_price_validation(client):
    assert client.post("/prices", json={"city": "tokat"}).status_code == 400


def test_basket_optimize(client):
    resp = client.post(
        "/basket/optimize",
        json={"city": "tokat", "items": ["süt", "ekmek", "yumurta"]},
    )
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["byMarket"]
    assert body["optimalSplit"]["total"] >= 0
    # Optimal toplam, en ucuz tek market toplamından büyük olamaz
    best_single = min(r["total"] for r in body["byMarket"] if r["complete"])
    assert body["optimalSplit"]["total"] <= best_single + 0.01


def test_history_synthetic(client):
    data = client.get("/history/Migros/süt").get_json()
    assert len(data) == 14
    assert {"price", "date"} <= data[0].keys()


def test_price_normalization():
    assert Item.normalize_price("12,50 TL") == "12.50"
    assert Item.normalize_price("1.299,00") == "1299.00"
    assert Item.normalize_price("bozuk") == "0.00"
