"""Ulusal kaynak ayrıştırıcısının testleri.

Ağa çıkmaz: kaynağın gerçek yanıt şekline benzeyen sabit veriyle çalışır.
Amaç, alan adı toleransının ve "aynı market için en ucuz şubeyi al" kuralının
sessizce bozulmamasını garanti etmek.
"""
from __future__ import annotations

import os

os.environ.setdefault("USE_MOCK", "false")
os.environ.setdefault("MOCK_FALLBACK", "false")

from app.sources.marketfiyati import (  # noqa: E402
    MarketFiyatiSource,
    _canonical_market,
    _parse_stamp,
)

SAMPLE = {
    "numberOfFound": 2,
    "content": [
        {
            "id": "p1",
            "title": "Sütaş Tam Yağlı Süt 1 L",
            "imageUrl": "https://example.org/sut.jpg",
            "productDepotInfoList": [
                {"marketAdi": "MIGROS", "price": 42.50, "indexTime": "2026-07-24T03:11:00"},
                # Aynı zincirin ikinci şubesi daha ucuz → bu kazanmalı.
                {"marketAdi": "Migros", "price": 39.90, "indexTime": "2026-07-25T03:10:00"},
                {"marketAdi": "A101", "price": 37.25, "indexTime": "2026-07-25T03:12:00"},
                {"marketAdi": "ŞOK MARKET", "price": 0, "indexTime": "2026-07-25T03:12:00"},
            ],
        },
        {
            "id": "p2",
            "title": "İçim Süt 1 L",
            # Alternatif alan adları: şema oynarsa da okunabilmeli.
            "image": "https://example.org/icim.jpg",
            "depots": [
                {"depotName": "BİM Birleşik Mağazalar", "unitPrice": "35,40 TL"},
            ],
        },
    ],
}


def test_parse_picks_cheapest_branch_per_market():
    items = MarketFiyatiSource()._parse(SAMPLE)
    by_market = {(it.source, it.name): it.price for it in items}

    assert by_market[("Migros", "Sütaş Tam Yağlı Süt 1 L")] == "39.90"
    assert by_market[("A101", "Sütaş Tam Yağlı Süt 1 L")] == "37.25"


def test_parse_reads_alternate_field_names():
    items = MarketFiyatiSource()._parse(SAMPLE)
    bim = [it for it in items if it.source == "BİM"]

    assert len(bim) == 1
    assert bim[0].price == "35.40"
    assert bim[0].image == "https://example.org/icim.jpg"


def test_parse_drops_zero_and_missing_prices():
    items = MarketFiyatiSource()._parse(SAMPLE)

    assert all(float(it.price) > 0 for it in items)
    assert not any(it.source == "ŞOK" for it in items)


def test_parse_sorts_ascending_by_price():
    prices = [float(it.price) for it in MarketFiyatiSource()._parse(SAMPLE)]

    assert prices == sorted(prices)


def test_parse_tolerates_garbage():
    """Kaynak boş/bozuk dönerse çökmemeli — servis ayakta kalmalı."""
    source = MarketFiyatiSource()

    assert source._parse({}) == []
    assert source._parse({"content": [None, "bozuk", {}]}) == []
    assert source._parse({"content": [{"title": "Ad var, şube yok"}]}) == []


def test_canonical_market_names():
    assert _canonical_market("MIGROS TICARET A.S.") == "Migros"
    assert _canonical_market("Sok Marketler") == "ŞOK"
    assert _canonical_market("CarrefourSA Hipermarket") == "CarrefourSA"
    assert _canonical_market("Türkiye Tarım Kredi Kooperatifleri") == "Tarım Kredi"
    # Tanınmayan ad kaybolmamalı, olduğu gibi geçmeli.
    assert _canonical_market("Yerel Market X") == "Yerel Market X"


def test_parse_reads_source_category():
    """Alaka sıralaması buna dayanıyor; kaynak alan adını değiştirirse burada patlar."""
    payload = {
        "content": [
            {
                "title": "Yerli Muz 1 Kg",
                "main_category": "Meyve",
                "productDepotInfoList": [{"marketAdi": "Migros", "price": 79.0}],
            },
            {
                "title": "Hero Baby Şeftali Muz 120 Gr",
                # Alternatif alan adı da okunabilmeli.
                "mainCategory": "Bebek Mamaları",
                "productDepotInfoList": [{"marketAdi": "A101", "price": 65.9}],
            },
        ]
    }
    by_name = {it.name: it.category for it in MarketFiyatiSource()._parse(payload)}

    assert by_name["Yerli Muz 1 Kg"] == "Meyve"
    assert by_name["Hero Baby Şeftali Muz 120 Gr"] == "Bebek Mamaları"


def test_category_does_not_leak_into_client_payload():
    """`category` sunucu içi bir sinyal; istemci sözleşmesi genişlemesin.

    Android `Item` şemasını sabit kabul ediyor. Tüketicisi olmayan alan
    eklemek sözleşmeyi sessizce büyütür.
    """
    from app.models import Item

    payload = Item("Yerli Muz 1 Kg", "79.00", "", "Migros", "Meyve").to_dict()

    assert "category" not in payload
    assert payload["from"] == "Migros"


def test_latest_index_time_picks_newest_as_iso():
    # SAMPLE'daki en yeni damga 2026-07-25T03:12:00; ISO metin olarak dönmeli.
    assert MarketFiyatiSource()._latest_index_time(SAMPLE) == "2026-07-25T03:12:00"


def test_latest_index_time_none_when_absent():
    payload = {"content": [{"title": "Damgasız", "depots": [{"marketAdi": "A101", "price": 5}]}]}
    assert MarketFiyatiSource()._latest_index_time(payload) is None


def test_parse_stamp_reads_both_formats_and_orders_by_date():
    # Sözlük sırasında '27.07.2026' > '05.08.2026' olurdu; tarih olarak tersi doğru.
    july = _parse_stamp("27.07.2026 08:25")
    august = _parse_stamp("05.08.2026 08:25")
    assert july is not None and august is not None
    assert august > july
    # ISO biçimi de tanınmalı.
    assert _parse_stamp("2026-07-25T03:10:00") is not None
    # Tanınmayan metin sessizce None.
    assert _parse_stamp("bilinmeyen") is None


def test_coords_for_known_and_unknown_city():
    from app import registry

    assert registry.coords_for("tokat") == (40.3167, 36.5544)
    assert registry.coords_for("İSTANBUL".lower()) == (41.0082, 28.9784)
    assert registry.coords_for("bilinmeyen-sehir") == registry.DEFAULT_COORDS
