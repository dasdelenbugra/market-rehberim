"""Sonuçsuz aramaların kaydı.

Amaç: eş anlamlı sözlüğünü tahmin listesiyle değil gerçek kullanımla beslemek.
Kullanıcı hangi kelimeyi aradığında boş ekran gördüyse o kelime adaydır.
"""
from app import db


def test_gap_is_recorded_and_counted(tmp_db):
    db.record_search_gap("çöp poşeti", 0)
    db.record_search_gap("çöp poşeti", 0)
    db.record_search_gap("pırasa", 0)

    by_term = {row["term"]: row for row in db.search_gaps()}

    assert by_term["çöp poşeti"]["hits"] == 2
    assert by_term["pırasa"]["hits"] == 1
    assert by_term["çöp poşeti"]["results"] == 0


def test_same_term_counts_once_regardless_of_spelling(tmp_db):
    """"Çöp Poşeti" ile "çöp poşeti" aynı boşluktur; sayaç bölünmemeli."""
    db.record_search_gap("Çöp Poşeti", 0)
    db.record_search_gap("çöp  poşeti", 0)
    db.record_search_gap("ÇÖP POŞETİ", 0)

    rows = db.search_gaps()

    assert len(rows) == 1
    assert rows[0]["hits"] == 3


def test_most_searched_gap_comes_first(tmp_db):
    """Rapor sıklığa göre sıralanmalı — en çok istenen eksik önce görünsün."""
    for _ in range(5):
        db.record_search_gap("jilet", 0)
    db.record_search_gap("naftalin", 0)

    assert db.search_gaps()[0]["term"] == "jilet"


def test_blank_term_is_ignored(tmp_db):
    """Boş ya da yalnız noktalamadan oluşan girdi tabloyu kirletmemeli."""
    db.record_search_gap("   ", 0)
    db.record_search_gap("!!!", 0)

    assert db.search_gaps() == []


def test_long_term_is_truncated(tmp_db):
    """Aşırı uzun girdi olduğu gibi saklanmamalı."""
    db.record_search_gap("a" * 500, 0)

    assert len(db.search_gaps()[0]["term"]) <= 80


def test_search_records_a_gap_only_when_results_are_scarce(tmp_db, monkeypatch):
    """Dolu sonuç veren arama boşluk sayılmamalı.

    Aksi halde tablo her aramayla dolar ve gerçek boşluklar gürültüde kaybolur.
    """
    from app import services
    from app.models import Item

    monkeypatch.setattr(services, "_national_cached", lambda c, n: ([], None))
    monkeypatch.setattr(services.db, "latest_crowd_prices", lambda c, n: [])
    services.search("istanbul", "bulunmayanurun")
    assert [r["term"] for r in db.search_gaps()] == ["bulunmayanurun"]

    plenty = [Item(f"Ürün {i}", "10.00", "", "Migros") for i in range(9)]
    monkeypatch.setattr(services, "_national_cached", lambda c, n: (plenty, None))
    services.search("istanbul", "makarna")

    assert "makarna" not in [r["term"] for r in db.search_gaps()]
