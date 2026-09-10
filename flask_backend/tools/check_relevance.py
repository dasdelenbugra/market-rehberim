#!/usr/bin/env python3
"""Alaka sıralamasının canlı kaynağa karşı ölçümü.

Neden var: `tests/test_products.py` ayrıştırıcıyı sabit veriyle doğruluyor ama
asıl soru başka — *gerçek* sonuçlarda doğru ürünler ana listeye çıkıyor mu?
Kaynak katalogunu değiştirdiğinde ya da sıralama kuralına dokunduğumuzda bunu
gözle kontrol etmek güvenilir değil; ölçülebilir olması lazım.

Beklentiler `relevance_cases.json` içinde durur: her sorgu için ana listede
bulunması ve bulunmaması gereken ürün adı parçaları. Bir kural değişikliğinden
sonra bunu çalıştır — kaç beklentinin tuttuğunu ve neyin kaydığını söyler.

Kullanım (flask_backend/ dizininden):

    python tools/check_relevance.py                 # tüm sorgular, özet
    python tools/check_relevance.py --query muz     # tek sorgu, tam liste
    python tools/check_relevance.py --list          # sınıflandırmayı dök

Çıkış kodu: beklentiler tutuyorsa 0, tutmuyorsa 1 (CI'a bağlanabilir).
"""
from __future__ import annotations

import argparse
import json
import os
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:  # Python < 3.7
    pass

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Bu script gerçek kaynağı ölçer; mock veriyle çalışması anlamsız olurdu.
os.environ["USE_MOCK"] = "false"
os.environ["MOCK_FALLBACK"] = "false"

from app import create_app, products  # noqa: E402
from app.text import fold  # noqa: E402

# Ölçüm, istemcinin gerçekten çağırdığı rotadan geçer. Kaynağı doğrudan
# çağırmak daha basit olurdu ama `services` katmanındaki eş anlamlı çevirisini
# atlardı: "çöp poşeti" araması uygulamada çalışırken ölçümde sıfır görünüyordu.
# Araç, ürünün izlediği yoldan başka bir yolu ölçerse ölçüm değersizdir.
_CLIENT = create_app().test_client()

CASES_PATH = os.path.join(os.path.dirname(os.path.abspath(__file__)), "relevance_cases.json")
CITY = "istanbul"


def _contains(haystack: str, needle: str) -> bool:
    """Türkçe/büyük-küçük harf duyarsız parça araması.

    `fold` boşlukları da atıyor, bu yüzden "yerli muz" ile "Yerli Muz 1 Kg"
    eşleşir; ambalaj bilgisi değişse de beklenti tutmaya devam eder.
    """
    return fold(needle) in fold(haystack)


def _evaluate(case: dict) -> dict:
    """Tek sorguyu çalıştırır ve beklentileri kontrol eder."""
    query = case["query"]
    response = _CLIENT.get(f"/products/{CITY}/{query}")
    groups = response.get_json() or []

    head = [g["name"] for g in groups if g["relevance"] == products.RELEVANCE_HEAD]
    related = [g["name"] for g in groups if g["relevance"] != products.RELEVANCE_HEAD]

    failures = []
    for needle in case.get("head", []):
        # Kaynak o ürünü hiç döndürmediyse bu bir sıralama hatası değil; kapsam
        # sorunu. Ayrı raporlanır ki gerçek regresyonla karışmasın.
        if any(_contains(name, needle) for name in head):
            continue
        where = "İLGİLİ" if any(_contains(n, needle) for n in related) else "SONUÇ YOK"
        failures.append(("head", needle, where))

    for needle in case.get("notHead", []):
        if any(_contains(name, needle) for name in head):
            failures.append(("notHead", needle, "ANA"))

    return {
        "query": query,
        "groups": len(groups),
        "head": head,
        "related": related,
        "failures": failures,
    }


def main() -> int:
    parser = argparse.ArgumentParser(description="Alaka sıralamasını canlı kaynakta ölçer")
    parser.add_argument("--query", help="Yalnız bu sorguyu çalıştır")
    parser.add_argument("--list", action="store_true", help="Sınıflandırmayı tam dök")
    args = parser.parse_args()

    with open(CASES_PATH, encoding="utf-8") as handle:
        cases = json.load(handle)["cases"]
    if args.query:
        cases = [c for c in cases if c["query"] == args.query]
        if not cases:
            print(f"'{args.query}' relevance_cases.json içinde yok.")
            return 1

    total_head = total_related = 0
    checked = passed = 0
    broken: list[tuple[str, str, str, str]] = []

    for case in cases:
        result = _evaluate(case)
        total_head += len(result["head"])
        total_related += len(result["related"])
        expectations = len(case.get("head", [])) + len(case.get("notHead", []))
        checked += expectations
        passed += expectations - len(result["failures"])

        mark = "OK  " if not result["failures"] else "HATA"
        print(
            f"{mark} {result['query']:<16} {result['groups']:>3} grup"
            f" | ANA {len(result['head']):>3} | İLGİLİ {len(result['related']):>3}"
        )

        for kind, needle, where in result["failures"]:
            if kind == "head":
                print(f"       ✗ '{needle}' ana listede olmalıydı → {where}")
            else:
                print(f"       ✗ '{needle}' ana listede OLMAMALIYDI")
            broken.append((result["query"], kind, needle, where))

        if args.list:
            for name in result["head"]:
                print(f"         ANA     {name}")
            for name in result["related"]:
                print(f"         İLGİLİ  {name}")

    print("\n" + "-" * 62)
    print(f"Sınıflandırma : ANA {total_head} | İLGİLİ {total_related}")
    print(f"Beklenti      : {passed}/{checked} tuttu")

    if broken:
        print("\nKayan beklentiler:")
        for query, kind, needle, where in broken:
            print(f"  {query:<16} {kind:<8} '{needle}' → {where}")
        return 1

    print("Tüm beklentiler tutuyor.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
