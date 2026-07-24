"""TTL önbelleği ve arama üzerindeki etkisi."""
import os
import tempfile

os.environ["USE_MOCK"] = "true"
os.environ.setdefault(
    "DB_PATH", os.path.join(tempfile.gettempdir(), "mr_test_cache.sqlite")
)

import pytest

from app import cache, services


@pytest.fixture(autouse=True)
def clean_cache():
    cache.clear()
    yield
    cache.clear()


def test_ikinci_cagri_ureticiyi_calistirmaz():
    calls = []

    def producer():
        calls.append(1)
        return "değer"

    assert cache.get_or_set("k", ttl=60, producer=producer) == "değer"
    assert cache.get_or_set("k", ttl=60, producer=producer) == "değer"
    assert len(calls) == 1


def test_suresi_dolan_kayit_yeniden_uretilir():
    calls = []

    def producer():
        calls.append(1)
        return len(calls)

    # ttl=0 önbelleği tamamen atlar
    assert cache.get_or_set("k", ttl=0, producer=producer) == 1
    assert cache.get_or_set("k", ttl=0, producer=producer) == 2
    assert cache.size() == 0


def test_farkli_anahtarlar_karismaz():
    assert cache.get_or_set("a", 60, lambda: "A") == "A"
    assert cache.get_or_set("b", 60, lambda: "B") == "B"
    assert cache.get_or_set("a", 60, lambda: "değişti") == "A"


def test_ust_sinir_asilinca_bosaltir():
    for i in range(cache.MAX_ENTRIES + 10):
        cache.get_or_set(f"k{i}", 60, lambda i=i: i)
    assert cache.size() <= cache.MAX_ENTRIES


def test_arama_ulusal_kismi_onbellekten_okur(monkeypatch):
    calls = []
    original = services._fetch_national

    def spy(name):
        calls.append(name)
        return original(name)

    monkeypatch.setattr(services, "_fetch_national", spy)

    services.search("tokat", "süt")
    services.search("tokat", "süt")
    assert len(calls) == 1


def test_farkli_sehir_ayni_urun_ulusali_tekrar_cekmez():
    """Ulusal zincir fiyatları şehre göre değişmiyor; anahtar şehri içermemeli."""
    calls = []
    original = services._fetch_national

    def spy(name):
        calls.append(name)
        return original(name)

    services._fetch_national = spy
    try:
        services.search("tokat", "ekmek")
        services.search("istanbul", "ekmek")
        assert len(calls) == 1
    finally:
        services._fetch_national = original


def test_onbellekten_donen_liste_kirlenmiyor():
    """Yerel sonuçlar önbellekteki listeye eklenip birikmemeli."""
    first = services.search("tokat", "yumurta")
    second = services.search("tokat", "yumurta")
    assert len(first) == len(second)
