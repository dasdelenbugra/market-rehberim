"""Kullanıcı terimini kataloğun terimine çeviren sözlük.

Somut şikâyet: "çöp poşeti" araması boş ekran veriyordu. Kaynak katalogunda
yalnız "çöp torbası" var; ürün yok değil, adı farklı.
"""
import pytest

from app import products, synonyms
from app.models import Item


@pytest.mark.parametrize(
    "user_term,catalogue_term",
    [
        ("çöp poşeti", "çöp torbası"),
        ("jilet", "tıraş bıçağı"),
        ("ağartıcı", "çamaşır suyu"),
        ("hıyar", "salatalık"),
        ("meşrubat", "gazlı içecek"),
        ("rimel", "maskara"),
        ("tıkanıklık açıcı", "lavabo açıcı"),
        ("klozet temizleyici", "tuvalet temizleyici"),
        ("after shave", "tıraş sonrası"),
    ],
)
def test_known_terms_are_translated(user_term, catalogue_term):
    assert synonyms.canonical(user_term) == catalogue_term


@pytest.mark.parametrize(
    "absent_term",
    ["pırasa", "naftalin", "fondöten", "süpürge", "ampul", "çöp kovası", "sinek ilacı"],
)
def test_genuinely_absent_products_are_left_alone(absent_term):
    """Sıfır sonuç her zaman eş anlamlı sorunu değil.

    Bu terimlerin hepsi ölçümde boş dönüyor ama market ürünü gerçekten
    taşımıyor; en yakın kelimeye yönlendirmek kullanıcıyı istemediği ürüne
    götürürdü. Boş sonuç, yanlış sonuçtan iyidir.
    """
    assert synonyms.canonical(absent_term) == absent_term


def test_unknown_term_passes_through():
    """Sözlükte olmayan sorgu bozulmadan geçmeli."""
    assert synonyms.canonical("makarna") == "makarna"
    assert synonyms.canonical("") == ""


@pytest.mark.parametrize(
    "typed,expected",
    [
        ("büyük çöp poşeti", "büyük çöp torbası"),
        ("battal boy çöp poşeti", "battal boy çöp torbası"),
        ("lavabo tıkanıklık açıcı", "lavabo lavabo açıcı"),
        ("hıyar turşusu", "salatalık turşusu"),
    ],
)
def test_term_inside_a_longer_query_is_translated(typed, expected):
    """Terim sorgunun tamamı olmasa da çevrilmeli.

    "büyük çöp poşeti" ölçümde sıfır sonuç veriyordu; çevrildiğinde 22.
    "lavabo lavabo açıcı" bozuk görünüyor ama kaynak bulanık aradığı için
    13 sonuç döndürüyor — ölçüm, tekrarın zarar vermediğini gösterdi.
    """
    assert synonyms.canonical(typed) == expected


def test_longest_match_wins():
    """Uzun terim kısa anahtara bölünmemeli."""
    assert synonyms.canonical("bulaşık makinesi deterjanı") == "bulaşık makinesi tableti"


def test_only_the_known_run_is_replaced():
    """Sorgunun geri kalanı korunmalı; çeviri kelimeyi silmemeli."""
    assert synonyms.canonical("kalın çöp poşeti 50 adet") == "kalın çöp torbası 50 adet"


def test_matching_ignores_case_and_turkish_letters():
    """"Çöp Poşeti" ile "cop poseti" aynı sorgudur; eşleşme kaçmamalı."""
    assert synonyms.canonical("Çöp Poşeti") == "çöp torbası"
    assert synonyms.canonical("ÇÖP POŞETİ") == "çöp torbası"


def test_translation_is_idempotent():
    """Sorgu hem kaynağa giderken hem alaka hesaplanırken çevriliyor.

    Hedef terimlerin kendisi haritada anahtar olmamalı; olsaydı ikinci çeviri
    sorguyu bambaşka bir şeye taşırdı.
    """
    for user_term in ("çöp poşeti", "jilet", "meşrubat"):
        once = synonyms.canonical(user_term)
        assert synonyms.canonical(once) == once


def test_grouping_uses_the_translated_term():
    """Ürün adları kataloğun terimini taşır; alaka eşleşmesi çevrilmiş sorguyu görmeli.

    Çeviri `group` içinde uygulanmasaydı hiçbir ad "çöp poşeti" sorgusunun baş
    ismini karşılamaz ve gerçek ürünler "ilgili" bölümüne düşerdi.
    """
    items = [
        Item("Koroplast Küçük Boy Çöp Torbası 30 Adet", "45.00", "", "A101",
             "Mutfak Sarf Malzemeleri"),
        Item("Macromax Büzgülü Çöp Torbası 10 Adet", "39.00", "", "Migros",
             "Mutfak Sarf Malzemeleri"),
    ]

    groups = products.group(items, "çöp poşeti")

    assert groups, "çeviri uygulanmazsa grup üretilemezdi"
    assert all(g["relevance"] == products.RELEVANCE_HEAD for g in groups)


def test_leek_is_not_mapped_to_something_else():
    """Katalogda karşılığı olmayan terim uydurma bir eşleşmeye bağlanmamalı.

    "pırasa" ölçümde sıfır sonuç veriyor ama bu eş anlamlı sorunu değil — ürün
    gerçekten yok. Yakın bir sebzeye yönlendirmek kullanıcıyı yanıltırdı.
    """
    assert synonyms.canonical("pırasa") == "pırasa"
