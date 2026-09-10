"""Boş sonuçta "bunu mu demek istediniz" önerisi.

Bu modülün asıl sınavı doğru öneriyi bulmak değil, **yanlış öneri vermemek**:
"pırasa" arayana "pirinç" demek kullanıcıyı istemediği ürüne yollar. Boş ekran
dürüsttür, yanlış yönlendirme değil.
"""
import pytest

from app import cache, suggest


@pytest.fixture(autouse=True)
def vocabulary(monkeypatch):
    """Sabit bir kelime dağarcığı kurar.

    Gerçek dağarcık `price_history`den geliyor ve testler arasında değişir;
    öneri davranışını ölçmek için sabit bir korpus gerek.
    """
    cache.clear()
    names = [
        "Sek Çikolata 60 Gr",
        "Ülker Çikolata 70 Gr",
        "Pastavilla Makarna 500 Gr",
        "Eker Yoğurt 1 Kg",
        "Alo Toz Deterjan 4 Kg",
        "Ahir Beyaz Peynir 750 Gr",
        "Marmarabirlik Zeytin 400 Gr",
        "İçim Süt 1 Lt",
    ]
    monkeypatch.setattr(suggest.db, "distinct_product_names", lambda *a, **k: names)
    monkeypatch.setattr(suggest, "catalogue_terms", tuple)
    yield
    cache.clear()


@pytest.mark.parametrize(
    "typo,expected",
    [
        ("cikoolata", "çikolata"),
        ("makarnaa", "makarna"),
        ("yogurtt", "yoğurt"),
        ("peynirr", "peynir"),
        ("zeytiin", "zeytin"),
    ],
)
def test_typo_gets_a_suggestion(typo, expected):
    assert suggest.closest(typo) == expected


@pytest.mark.parametrize(
    "absent",
    ["pırasa", "süpürge", "naftalin", "ampul", "paspas", "fondöten"],
)
def test_absent_product_gets_no_suggestion(absent):
    """Markette olmayan ürün için susmalı — bu modülün en önemli davranışı."""
    assert suggest.closest(absent) is None


def test_known_word_is_not_corrected():
    """Katalogda olan kelime "düzeltilmemeli".

    Ürün o an bulunamamış olabilir (stok, şehir); bilinen bir kelimeyi başka
    bir şeye çevirmek kullanıcıyı yanıltırdı.
    """
    assert suggest.closest("çikolata") is None
    assert suggest.closest("makarna") is None


def test_suggestion_is_readable_turkish():
    """Öneri kullanıcıya gösteriliyor: ASCII'ye indirgenmiş hâli dönmemeli."""
    assert suggest.closest("cikoolata") == "çikolata"
    assert suggest.closest("yogurtt") == "yoğurt"


def test_multi_word_query_is_left_alone():
    """Çok kelimeli sorguda hangi kelimenin hatalı olduğu belirsiz.

    Yanlış kelimeyi düzeltmek sorguyu büsbütün bozar; kural ölçülmediği için
    sessiz kalınıyor.
    """
    assert suggest.closest("cikoolata paketi") is None


def test_very_short_query_is_left_alone():
    """Kısa kelimede bir harf fark anlamı tamamen değiştirir ("bal"/"dal")."""
    assert suggest.closest("bal") is None
    assert suggest.closest("çay") is None


def test_stopwords_are_not_suggested():
    """Ambalaj/sıfat kelimeleri dağarcığa girmemeli, öneri olarak dönmemeli."""
    known = suggest.vocabulary()

    assert "adet" not in known
    assert "paket" not in known


def test_endpoint_sends_suggestion_only_when_empty(client, monkeypatch):
    """Başlık yalnız sonuç boşken gelmeli; dolu listede öneri kafa karıştırır."""
    from app import services

    monkeypatch.setattr(services, "search", lambda city, name: [])
    monkeypatch.setattr(services, "national_updated_at", lambda city, name: None)

    empty = client.get("/products/istanbul/cikoolata")
    assert empty.get_json() == []
    assert empty.headers.get("X-Search-Suggestion") == "çikolata"

    # Katalogda bulunan bir kelimede öneri hiç üretilmemeli.
    known = client.get("/products/istanbul/çikolata")
    assert known.headers.get("X-Search-Suggestion") is None
