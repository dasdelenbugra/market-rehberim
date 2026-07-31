"""API testleri (mock modda, geçici DB ile — ağ gerektirmez).

Ortam kurulumu ve `client` fixture'ı `conftest.py`'de.
"""
from app import cache, db, validation
from app.models import Item


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


def _submission(**overrides):
    body = {"city": "tokat", "market": "Erenler", "name": "zeytin", "price": "45,90 TL"}
    body.update(overrides)
    return body


def test_submit_rejects_unreadable_price(client):
    """OCR fiyatı çıkaramazsa `normalize_price` "0.00" döner.

    Bu satır kaydedilseydi fiyat sıralamasında **her aramada birinci** olur,
    "EN UCUZ" rozetini alır ve sepet optimizasyonunda o marketi bedava
    gösterirdi. Reddedilmesi gerekir.
    """
    resp = client.post("/prices", json=_submission(price="okunamadı"))
    assert resp.status_code == 400
    assert "Fiyat" in resp.get_json()["error"]


def test_submit_rejects_decimal_shift(client):
    """15,95 yerine 1595,00 okunması tipik bir OCR hatası; üst sınır bunu eler."""
    assert client.post("/prices", json=_submission(price="195000")).status_code == 400


def test_submit_rejects_unknown_market(client):
    """Market adı serbest metindi; şehirde olmayan bir markete fiyat düşebiliyordu."""
    resp = client.post("/prices", json=_submission(market="Olmayan Market"))
    assert resp.status_code == 400
    assert "tanımsız market" in resp.get_json()["error"]


def test_submit_rejects_unknown_city(client):
    assert client.post("/prices", json=_submission(city="Atlantis")).status_code == 400


def test_submit_accepts_national_market(client):
    """Kullanıcı ulusal zincirin şubesinde farklı bir etiket görmüş olabilir."""
    assert client.post("/prices", json=_submission(market="Migros")).status_code == 201


def test_submit_twice_same_day_updates_history(client):
    """Aynı ürüne aynı gün ikinci gönderim isteği düşürmemeli.

    `price_history` günde tek noktaya indekslendiğinde düz INSERT burada
    IntegrityError atıyordu — kullanıcı fiyatını düzeltmek isteyince 500 alıyordu.
    Son gönderim kazanır.
    """
    first = client.post("/prices", json=_submission(name="tekrarurunu", price="10,00"))
    second = client.post("/prices", json=_submission(name="tekrarurunu", price="12,00"))
    assert (first.status_code, second.status_code) == (201, 201)

    history = client.get("/history/Erenler/tekrarurunu").get_json()
    assert [p["price"] for p in history] == ["12.00"]


def test_submit_rate_limited(client):
    """Döngüye girmiş bir istemci veritabanını saniyeler içinde dolduramamalı."""
    validation.reset_rate_limit()
    for _ in range(validation.RATE_LIMIT_MAX):
        assert client.post("/prices", json=_submission()).status_code == 201

    resp = client.post("/prices", json=_submission())
    assert resp.status_code == 429
    validation.reset_rate_limit()


def test_implausible_crowd_price_hidden_from_search(client):
    """Büyüklük mertebesi kaçmış eski kayıt arama sonucuna sızmamalı.

    Doğrulama öncesi yazılmış satırlar veritabanında duruyor; süzme okuma
    anında yapıldığı için onlar da elenir.
    """
    national = client.get("/search/tokat/süt").get_json()
    ceiling = max(float(r["price"]) for r in national)

    # Rotayı atlayarak yazıyoruz: kural yokken kaydedilmiş bir satırı taklit eder.
    db.add_crowd_price("tokat", "Erenler", "süt", ceiling * 100, None)

    results = client.get("/search/tokat/süt").get_json()
    assert not any(r["from"] == "Erenler" for r in results)


def test_plausible_local_price_still_shown(client):
    """Yerel marketin ucuz olması elenme sebebi değil — uygulamanın amacı bu."""
    national = client.get("/search/tokat/makarna").get_json()
    floor = min(float(r["price"]) for r in national)

    db.add_crowd_price("tokat", "Mopaş", "makarna", round(floor * 0.5, 2), None)

    results = client.get("/search/tokat/makarna").get_json()
    assert any(r["from"] == "Mopaş" for r in results)


def test_crowd_price_survives_turkish_city_casing(client):
    """Şehir adı büyük harfli gelse de crowdsourced fiyat aramada görünmeli.

    `str.lower()` Türkçe'de "İstanbul" → "i" + U+0307 üretiyor ve `registry`nin
    `fold()` ile ürettiği "istanbul" anahtarıyla eşleşmiyordu; yerel market
    fiyatları sessizce kayboluyordu.
    """
    resp = client.post(
        "/prices",
        json={"city": "İstanbul", "market": "Onur Market",
              "name": "peynirtest", "price": "89,90 TL"},
    )
    assert resp.status_code == 201

    for spelling in ("İstanbul", "istanbul", "ISTANBUL"):
        data = client.get(f"/search/{spelling}/peynirtest").get_json()
        assert any(r["from"] == "Onur Market" for r in data), spelling


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


def test_history_empty_when_no_data(client):
    """Kayıt yoksa boş liste döner — eskiden burada uydurma seri üretiliyordu."""
    assert client.get("/history/Migros/hicboyleurunyok").get_json() == []


def test_history_is_recorded_from_search(client):
    """Arama, ulusal fiyatları geçmişe işler; grafik verisi böyle birikir."""
    results = client.get("/search/tokat/süt").get_json()
    sample = next(r for r in results if r["from"] == "Migros")

    data = client.get(f"/history/Migros/{sample['name']}").get_json()
    assert len(data) == 1
    assert {"price", "date"} <= data[0].keys()
    assert data[0]["price"] == sample["price"]


def test_history_matches_exact_product(client):
    """Genel bir kelime tek ürünün geçmişini döndürmemeli.

    `LIKE '%süt%'` kullanıldığında farklı ürünlerin fiyatları tek seriye
    karışıyordu; grafik anlamsız hale geliyordu.
    """
    client.get("/search/tokat/süt")
    assert client.get("/history/Migros/süt").get_json() == []


def test_history_one_point_per_day(client):
    """Aynı gün tekrar aranırsa geçmişe ikinci nokta düşmez.

    Önbellek kasıtlı olarak temizleniyor: aksi halde ikinci arama kaynağa hiç
    gitmez ve test, günlük tekilleştirmeyi değil yalnızca önbelleği doğrulardı.
    """
    results = client.get("/search/tokat/ekmek").get_json()
    sample = next(r for r in results if r["from"] == "Migros")

    before = client.get(f"/history/Migros/{sample['name']}").get_json()

    cache.clear()
    client.get("/search/tokat/ekmek")

    after = client.get(f"/history/Migros/{sample['name']}").get_json()
    assert len(after) == len(before) == 1


def test_price_normalization():
    assert Item.normalize_price("12,50 TL") == "12.50"
    assert Item.normalize_price("1.299,00") == "1299.00"
    assert Item.normalize_price("bozuk") == "0.00"
