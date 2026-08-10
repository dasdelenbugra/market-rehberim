"""Barkodla arama testleri.

Open Food Facts'e gerçek istek atılmaz: çözümleyici sahte bir kaynakla
değiştirilir. Testin doğrulaması gereken şey OFF'un kapsamı değil, barkod →
ürün adı → fiyat zincirinin ve hata durumlarının doğru kurulması.
"""
import pytest

from app import cache, services
from app.sources.openfoodfacts import BarcodeProduct, is_valid


class FakeBarcodeSource:
    """Sabit bir barkod tablosu; tablo dışındaki her kod bilinmiyor sayılır."""

    def __init__(self, table):
        self.table = table

    def resolve(self, barcode):
        return self.table.get(barcode.strip())


@pytest.fixture()
def fake_source(monkeypatch):
    table = {
        "8690766009205": BarcodeProduct(
            barcode="8690766009205", name="Çizi Peynirli Kraker", brand="Ülker"
        ),
        # Markette karşılığı olmayan ürün: çözümlenir ama fiyat bulunmaz.
        "1234567890123": BarcodeProduct(
            barcode="1234567890123", name="Hicboyleurunyok", brand=""
        ),
    }
    monkeypatch.setattr(services, "BARCODE_SOURCE", FakeBarcodeSource(table))
    # Çözümleme önbelleklidir; testler arası sızmasın.
    cache.clear()
    yield
    cache.clear()


def test_gecersiz_barkod_400(client, fake_source):
    """Biçim kontrolü kaynağa gitmeden önce: okuma hatası ağ isteğine dönüşmesin."""
    assert client.get("/barcode/tokat/abc").status_code == 400
    assert client.get("/barcode/tokat/123").status_code == 400


def test_bilinmeyen_barkod_404(client, fake_source):
    resp = client.get("/barcode/tokat/9999999999999")
    assert resp.status_code == 404
    assert "error" in resp.get_json()


def test_bilinen_barkod_urun_ve_fiyat_dondurur(client, fake_source):
    resp = client.get("/barcode/tokat/8690766009205")
    assert resp.status_code == 200
    body = resp.get_json()
    assert body["barcode"] == "8690766009205"
    assert body["product"] == "Ülker Çizi Peynirli Kraker"
    assert body["items"]
    assert {"name", "price", "from"} <= body["items"][0].keys()


def test_urun_taniniyor_ama_fiyat_yok_404_degil(client, fake_source, monkeypatch):
    """"Barkodu tanımadım" ile "fiyat bulamadım" ayrı durumlar.

    İkincisi 200 + boş `items` döner; kullanıcıya söylenecek şey farklı ve
    istemci bunları ayırt edebilmeli.

    Arama boşa zorlanıyor: testler mock modda çalışıyor ve mock kaynak her
    sorguya ürün üretiyor, yani "sonuç yok" durumu doğal yoldan oluşamıyor.
    """
    monkeypatch.setattr(services, "search", lambda city, name: [])

    resp = client.get("/barcode/tokat/1234567890123")
    assert resp.status_code == 200
    assert resp.get_json()["items"] == []


def test_barkod_bicim_kontrolu():
    assert is_valid("8690766009205")
    assert is_valid("18921102")  # EAN-8
    assert not is_valid("123")
    assert not is_valid("869076600920X")
    assert not is_valid("")


def test_sorgu_markayi_tekrarlamaz():
    """Ad zaten markayla başlıyorsa "Ülker Ülker Çikolata" üretilmemeli."""
    tekrarli = BarcodeProduct(barcode="1", name="Ülker Çikolata", brand="Ülker")
    assert tekrarli.query == "Ülker Çikolata"

    ayri = BarcodeProduct(barcode="2", name="Çizi Peynirli Kraker", brand="Ülker")
    assert ayri.query == "Ülker Çizi Peynirli Kraker"


def test_markasiz_urun_adi_kullanir():
    assert BarcodeProduct(barcode="3", name="Kuru İncir", brand="").query == "Kuru İncir"
