#!/usr/bin/env python3
"""Sonuçsuz aramaların raporu — eş anlamlı sözlüğünün besleyicisi.

`app/synonyms.py` ilk hâlinde tahmin listesiyle kuruldu: 325 yaygın terim elle
yazılıp canlı kaynağa soruldu. Bu yöntemin bariz sınırı, kullanıcının aklından
geçeni değil bizim aklımızdan geçeni taramasıdır.

Bu araç tersini yapar: uygulama sonuçsuz kalan aramaları sayıyor (bkz.
`db.record_search_gap`), burada da en sık aranıp en az sonuç verenler listelenir.
Sözlüğe eklenecek terim buradan seçilir — hangi kelimenin eksik olduğunu
kullanıcı zaten söylemiş oluyor.

Listede zaten çevrilen bir terim görürsen bu bir uyarıdır: çeviri hedefi de
sonuç vermiyor demektir, hedefi yeniden ölçmek gerekir.

Kullanım (flask_backend/ dizininden):

    python tools/search_gaps.py                # en sık 40 boşluk
    python tools/search_gaps.py --limit 100
    python tools/search_gaps.py --min-hits 3   # tek seferlik yazım hatalarını ele
"""
from __future__ import annotations

import argparse
import os
import sys

try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:  # Python < 3.7
    pass

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from app import db, synonyms  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Sonuçsuz aramaları raporlar")
    parser.add_argument("--limit", type=int, default=40, help="Kaç satır (varsayılan 40)")
    parser.add_argument(
        "--min-hits", type=int, default=1,
        help="Bu sayıdan az aranmış terimleri gösterme (yazım hatalarını eler)",
    )
    parser.add_argument(
        "--reset", action="store_true",
        help="Sayaçları sıfırla (sözlüğe ekleme yaptıktan sonra taze sinyal için)",
    )
    args = parser.parse_args()

    db.init_db()

    if args.reset:
        db.clear_search_gaps()
        print("Sayaçlar sıfırlandı.")
        return 0
    rows = [r for r in db.search_gaps(limit=args.limit) if r["hits"] >= args.min_hits]

    if not rows:
        print("Kayıtlı arama boşluğu yok.")
        print("Uygulama bir süre kullanıldıktan sonra tekrar bak; kayıt")
        print("LOG_SEARCH_GAPS ile açılıp kapanıyor (bkz. config.py).")
        return 0

    print(f"{'ARAMA':<34}{'kez':>5}{'sonuç':>7}  {'son görülme':<20} durum")
    print("-" * 88)

    for row in rows:
        term = row["term"]
        # Terim zaten çevriliyorsa hedef de boş dönüyor demektir: eklenecek yeni
        # bir eşleşme değil, gözden geçirilecek bir eşleşme.
        translated = synonyms.canonical(term)
        if translated != term:
            state = f"ZATEN ÇEVRİLİYOR -> '{translated}' (hedefi ölç)"
        else:
            state = "aday"
        print(
            f"{term[:32]:<34}{row['hits']:>5}{row['results']:>7}  "
            f"{row['last_seen']:<20} {state}"
        )

    print()
    print("Aday bir terimi eklemeden önce kataloğun karşılığını doğrula:")
    print("  1. Karşılık adayını canlı kaynağa sor, dönen ÜRÜN ADLARINA bak.")
    print("     Sonuç sayısı yeterli kanıt değil — kaynak bulanık eşleştiriyor")
    print("     ('mandal' 47 sonuç döndürür, hepsi 'Manda Sütlü Yoğurt').")
    print("  2. Ürün gerçekten aranan şeyse app/synonyms.py'ye ekle.")
    print("  3. Markette o ürün hiç yoksa EKLEME — boş sonuç, yanlış sonuçtan iyidir.")
    print("  4. tools/relevance_cases.json'a bir vaka ekleyip check_relevance.py çalıştır.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
