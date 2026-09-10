"""Ürün gruplama ve alaka sıralaması.

Somut şikâyet: "muz" araması muzu, muzlu sütü ve muzlu gofreti tek listede
karıştırıyor, "EN UCUZ" rozeti 8 ₺'lik gofrete gidiyordu.
"""
import pytest

from app import products
from app.models import Item


def item(name, price, source="Migros", category=""):
    return Item(name, price, "", source, category)


# --- Alaka ------------------------------------------------------------------

@pytest.mark.parametrize(
    "name",
    ["Yerli Muz 1 Kg", "İthal Muz 1 Kg", "Muz 1 Kg", "Organik Yerli Muz"],
)
def test_head_noun_is_most_relevant(name):
    """Türkçe'de baş isim sonda: "Yerli Muz" muzdur."""
    assert products.relevance(name, "muz") == products.RELEVANCE_HEAD


@pytest.mark.parametrize(
    "name",
    [
        "Tarım Kredi Muz Aromalı Süt 200 Ml",
        "9 Kat Muz Kremalı Gofret 39 Gr",
        "Eti Hoşbeş Muz Kremalı Gofret 120 Gr",
    ],
)
def test_modifier_is_only_related(name):
    """"Muz Aromalı Süt" süttür — muz arayana ana sonuç olarak verilmemeli."""
    assert products.relevance(name, "muz") == products.RELEVANCE_RELATED


@pytest.mark.parametrize(
    "name",
    [
        "Fellas Yüksek Protein Bar Muz 45 Gr",
        "Algida Carte d'Or İsveç Karameli Muz 1.105 Lt",
    ],
)
def test_long_name_is_not_head_match(name):
    """Baş isim eşleşmesi tek başına yetmez; uzun ad aromayı işaret eder.

    Bu adların da başı "muz" ama ürün protein barı ve dondurma. Gerçek veride
    aranan ürünün adı kısa kalıyor ("Yerli Muz"), aroma olarak muz geçen
    ürünler belirgin biçimde uzuyor.
    """
    assert products.relevance(name, "muz") == products.RELEVANCE_RELATED


@pytest.mark.parametrize(
    "name",
    ["Activia Shot Çilek & Muz 80 Ml", "Carte D'or Classic Karamel/Muz 850 Ml"],
)
def test_flavour_list_is_not_head_match(name):
    """"Çilek & Muz" bir aroma sıralaması: bağlaçtan sonraki kelime ürün değil, tat."""
    assert products.relevance(name, "muz") == products.RELEVANCE_RELATED


def test_long_but_genuine_name_still_head_match():
    """Uzunluk sınırı gerçek ürünleri düşürmemeli — dört kelime hâlâ ana sonuç."""
    assert products.relevance("De Cecco Fettuccine Makarna 500 Gr", "makarna") == \
        products.RELEVANCE_HEAD


def test_elided_head_noun_still_fools_the_name_rule():
    """Ad tek başına yetmiyor: "Hero Baby Şeftali Muz" bebek maması ama sonu "Muz".

    Bu, ad sezgisinin bilinen sınırı ve öyle kalıyor — çözüm ada daha çok kural
    eklemek değil, kaynağın kategorisini kullanmak. Grup düzeyinde nasıl
    düzeldiği: `test_category_demotes_name_rule_false_positive`.
    """
    assert products.relevance("Hero Baby Şeftali Muz 120 Gr", "muz") == \
        products.RELEVANCE_HEAD


def test_suffix_tolerated():
    """Sondan eklemeli dil: "muz" sorgusu "Muzlu" kelimesini de karşılamalı."""
    assert products.relevance("Muzlu Süt 200 Ml", "muz") == products.RELEVANCE_RELATED
    assert products.relevance("Çilekli Süt 200 Ml", "süt") == products.RELEVANCE_HEAD


def test_long_suffix_not_matched():
    """Kısa sorgu her kelimeye uymamalı: "su" != "sucuk"."""
    assert products.relevance("Sucuk 250 Gr", "su") == products.RELEVANCE_RELATED


def test_quantity_ignored_when_finding_head():
    """"1 Kg" atılmazsa adın başı "kg" sanılırdı."""
    assert products._content_tokens("Yerli Muz 1 Kg") == ["yerli", "muz"]


# --- Kategori tohumlama -----------------------------------------------------

def _tier_of(groups, name):
    return next(g["relevance"] for g in groups if g["name"] == name)


def test_category_demotes_name_rule_false_positive():
    """Bebek maması ana listeden düşmeli: adı muzla bitiyor ama kategorisi Meyve değil.

    Ad kuralı ikisini de baş isim sayıyor (bkz.
    `test_elided_head_noun_still_fools_the_name_rule`); ayıran şey kategori.
    """
    groups = products.group(
        [
            item("Yerli Muz 1 Kg", "79.00", category="Meyve"),
            item("Hero Baby Şeftali Muz 120 Gr", "65.90", category="Bebek Mamaları"),
        ],
        "muz",
    )

    assert _tier_of(groups, "Yerli Muz 1 Kg") == products.RELEVANCE_HEAD
    assert _tier_of(groups, "Hero Baby Şeftali Muz 120 Gr") == products.RELEVANCE_RELATED
    # Ucuz olması onu üste taşımamalı: alaka fiyattan önce gelir.
    assert groups[0]["name"] == "Yerli Muz 1 Kg"


def test_category_promotes_what_the_name_rule_missed():
    """Baş ismi sonda olmayan gerçek ürünler kategori sayesinde geri gelir.

    "Yumurta M Boy 30 Adet" adının sonu "Boy"; ad kuralı bunu ilgili sayıyor.
    Kategorisi diğer yumurtalarla aynı olduğu için ana listeye çıkmalı.
    """
    groups = products.group(
        [
            item("Organik Yumurta 6 Adet", "65.00", category="Yumurta"),
            item("Yumurta M Boy 53-62 Gr 30 Adet", "145.00", category="Yumurta"),
        ],
        "yumurta",
    )

    assert products.relevance("Yumurta M Boy 53-62 Gr 30 Adet", "yumurta") == \
        products.RELEVANCE_RELATED  # ad kuralı tek başına kaçırıyor
    assert _tier_of(groups, "Yumurta M Boy 53-62 Gr 30 Adet") == products.RELEVANCE_HEAD


def test_shortest_name_seeds_the_target_category():
    """Hedef kategoriyi en kısa adlı aday belirler, en kalabalık olan değil.

    "muz" sonuçlarında Bebek Mamaları sayıca üstün; çoğunluğa baksaydık hedef
    yanlış çıkardı.
    """
    items = [
        item("Hero Baby Elma Muz 125 Gr", "51.95", category="Bebek Mamaları"),
        item("Hero Baby Şeftali Muz 120 Gr", "65.90", category="Bebek Mamaları"),
        item("Hero Baby Kayısı Muz 120 Gr", "66.90", category="Bebek Mamaları"),
        item("Yerli Muz 1 Kg", "79.00", category="Meyve"),
    ]

    assert products.target_category(items, "muz") == "Meyve"


def test_category_without_query_mention_is_not_promoted():
    """Hedef kategoride olmak yetmez; ad sorguyu da geçirmeli.

    Kaynak "muz" sorgusuna Meyve kategorisinden alakasız bir ürün döndürürse
    ana listeye sızmamalı.
    """
    groups = products.group(
        [
            item("Yerli Muz 1 Kg", "79.00", category="Meyve"),
            item("Elma 1 Kg", "45.00", category="Meyve"),
        ],
        "muz",
    )

    assert _tier_of(groups, "Elma 1 Kg") == products.RELEVANCE_RELATED


def test_missing_category_falls_back_to_name_rule():
    """Kategorisiz kayıt cezalandırılmamalı — crowdsourced yerel market verisi böyle.

    Yerel marketin fiyatı kategori bildirmiyor; hedefe uymadı diye elenseydi
    uygulamanın ayırt edici özelliği ana listeden tamamen kaybolurdu.
    """
    groups = products.group(
        [
            item("Yerli Muz 1 Kg", "79.00", category="Meyve"),
            item("Muz 1 Kg", "72.00", source="Erenler"),  # kategorisiz
        ],
        "muz",
    )

    assert _tier_of(groups, "Muz 1 Kg") == products.RELEVANCE_HEAD


def test_no_category_anywhere_keeps_old_behaviour():
    """Kaynak kategori vermezse sistem eski (yalnız ada bakan) davranışına döner."""
    items = [item("Yerli Muz 1 Kg", "79.00"), item("Muz Aromalı Süt 200 Ml", "13.90")]

    assert products.target_category(items, "muz") == ""
    groups = products.group(items, "muz")
    assert _tier_of(groups, "Yerli Muz 1 Kg") == products.RELEVANCE_HEAD
    assert _tier_of(groups, "Muz Aromalı Süt 200 Ml") == products.RELEVANCE_RELATED


# --- Gruplama ---------------------------------------------------------------

def test_same_product_grouped_across_markets():
    items = [
        item("Yerli Muz 1 Kg", "89.50", "A101"),
        item("Yerli Muz 1 Kg", "79.00", "BİM"),
        item("Yerli Muz 1 Kg", "94.90", "ŞOK"),
    ]
    groups = products.group(items, "muz")

    assert len(groups) == 1
    group = groups[0]
    assert group["marketCount"] == 3
    assert (group["bestPrice"], group["bestMarket"]) == ("79.00", "BİM")
    assert group["maxPrice"] == "94.90"
    # Teklifler ucuzdan pahalıya
    assert [o["price"] for o in group["offers"]] == ["79.00", "89.50", "94.90"]


def test_relevant_product_outranks_cheaper_unrelated_one():
    """Asıl hata buydu: 8 ₺'lik gofret 79 ₺'lik muzun üstünde çıkıyordu."""
    items = [
        item("9 Kat Muz Kremalı Gofret 39 Gr", "8.00", "A101"),
        item("Yerli Muz 1 Kg", "79.00", "BİM"),
    ]
    names = [g["name"] for g in products.group(items, "muz")]
    assert names == ["Yerli Muz 1 Kg", "9 Kat Muz Kremalı Gofret 39 Gr"]


def test_cheapest_first_within_same_relevance():
    items = [
        item("İthal Muz 1 Kg", "99.00", "BİM"),
        item("Yerli Muz 1 Kg", "79.00", "BİM"),
    ]
    assert [g["name"] for g in products.group(items, "muz")] == [
        "Yerli Muz 1 Kg",
        "İthal Muz 1 Kg",
    ]


def test_group_carries_unit_price():
    groups = products.group([item("Yerli Muz 1 Kg", "79.00")], "muz")
    assert (groups[0]["unitPrice"], groups[0]["unit"]) == ("79.00", "kg")


def test_image_falls_back_to_populated_one():
    """En ucuz satırın görseli boşsa grup görselsiz kalmamalı."""
    cheap = Item("Yerli Muz 1 Kg", "79.00", "", "BİM")
    pricey = Item("Yerli Muz 1 Kg", "89.50", "http://x/muz.png", "A101")
    assert products.group([cheap, pricey], "muz")[0]["image"] == "http://x/muz.png"


def test_endpoint_returns_groups(client):
    data = client.get("/products/tokat/süt").get_json()
    assert data and isinstance(data, list)
    assert {"name", "bestPrice", "bestMarket", "marketCount", "offers", "relevance"} \
        <= data[0].keys()
    # Alaka sırası bozulmamalı
    assert [g["relevance"] for g in data] == sorted(g["relevance"] for g in data)
