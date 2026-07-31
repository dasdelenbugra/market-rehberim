"""Birim fiyat çıkarımı.

Miktar, ürün adının içinde serbest metin olarak geldiği için ayrıştırma
tahminidir. Testlerin ağırlığı bu yüzden **yanlış pozitiflerde**: emin
olunamayan bir addan uydurma bir ₺/L üretmek, hiç göstermemekten kötü.
"""
import pytest

from app import units


@pytest.mark.parametrize(
    "name, expected",
    [
        ("Sek Süt 200 Ml", (0.2, "L")),
        ("İçim Süt 1 L", (1.0, "L")),
        ("Deterjan 1,5 Lt", (1.5, "L")),
        ("Makarna 500 G", (0.5, "kg")),
        ("Un 2,5 Kg", (2.5, "kg")),
        ("Krema 200gr", (0.2, "kg")),
    ],
)
def test_parse_quantity(name, expected):
    quantity, unit = units.parse_quantity(name)
    assert (round(quantity, 6), unit) == expected


def test_multipack_multiplies():
    assert units.parse_quantity("Pınar Süt 6 X 200 Ml") == (pytest.approx(1.2), "L")
    assert units.parse_quantity("Su 6x1,5 L") == (pytest.approx(9.0), "L")


def test_multipack_only_when_adjacent():
    """Addaki alakasız bir sayı çarpan sanılmamalı.

    "2 Li Paket" bir çoklu paket ifadesi değil; 500 G buna göre çarpılırsa
    birim fiyat yarıya düşer ve ürün olduğundan ucuz görünür.
    """
    assert units.parse_quantity("2 Li Paket Çay 500 G") == (pytest.approx(0.5), "kg")


@pytest.mark.parametrize(
    "name",
    [
        "Çay Bardağı",            # miktar yok
        "Kahve 2024 Özel",        # yıl, miktar değil
        "Bulaşık Süngeri 3 lü",   # adet — L/kg'a çevrilemez
        "Ampul 100 W",            # tanımsız birim
        "Islak Mendil 1 Litrelik",  # birim adının ardından harf var
        "",
    ],
)
def test_unparseable_returns_none(name):
    assert units.parse_quantity(name) is None


def test_absurd_quantity_rejected():
    """Miktar sanılan büyük sayı (ürün kodu, model) elenmeli."""
    assert units.parse_quantity("Kablo 5000 Kg") is None


def test_unit_price_computed():
    assert units.unit_price(22.50, "Sek Süt 200 Ml") == ("112.50", "L")
    assert units.unit_price(18.00, "Makarna 500 G") == ("36.00", "kg")


def test_unit_price_needs_positive_price():
    assert units.unit_price(0.0, "Sek Süt 200 Ml") is None


def test_item_exposes_unit_price():
    """Alanlar `to_dict` içinde türetilir; Item üreten her yer doldurmak zorunda değil."""
    from app.models import Item

    data = Item("Sek Süt 200 Ml", "22.50", "", "Migros").to_dict()
    assert data["unitPrice"] == "112.50"
    assert data["unit"] == "L"


def test_item_without_quantity_has_null_fields():
    from app.models import Item

    data = Item("Çay Bardağı", "40.00", "", "Migros").to_dict()
    assert data["unitPrice"] is None
    assert data["unit"] is None
