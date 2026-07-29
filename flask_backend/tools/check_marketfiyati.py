#!/usr/bin/env python3
"""Ulusal fiyat kaynağını canlı doğrulama scripti.

Neden var: API şeması sürüm sürüm değişebiliyor ve `MarketFiyatiSource._parse`
alan adlarını toleranslı okuyor. Bir şey kırıldığında ilk buraya bak — ham
yanıtın anahtarlarını gösterir, böylece hangi alanın yeniden adlandırıldığını
tahmin etmeden görürsün.

Kullanım (flask_backend/ dizininden):

    python tools/check_marketfiyati.py süt
    python tools/check_marketfiyati.py "ayçiçek yağı" --city istanbul --raw
"""
from __future__ import annotations

import argparse
import json
import os
import sys

# Windows konsolu (cp1254) Türkçe/ok karakterlerinde patlıyor; çıktıyı UTF-8'e
# sabitle ki script kendisi teşhis aracıyken teşhis edilecek hâle gelmesin.
try:
    sys.stdout.reconfigure(encoding="utf-8")
except AttributeError:  # Python < 3.7
    pass

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

# Mock'u kesin kapat: bu scriptin amacı gerçek kaynağı test etmek.
os.environ["USE_MOCK"] = "false"
os.environ["MOCK_FALLBACK"] = "false"

import requests as _requests

from app import registry  # noqa: E402
from app.sources.marketfiyati import MarketFiyatiSource  # noqa: E402

_HEADERS = {
    "Accept": "application/json, text/plain, */*",
    "Accept-Language": "tr-TR,tr;q=0.9,en;q=0.8",
    "Content-Type": "application/json",
    "Origin": "https://marketfiyati.org.tr",
    "Referer": "https://marketfiyati.org.tr/",
    "Sec-Fetch-Site": "same-site",
    "Sec-Fetch-Mode": "cors",
    "Sec-Fetch-Dest": "empty",
    "User-Agent": (
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
        "AppleWebKit/537.36 (KHTML, like Gecko) "
        "Chrome/124.0.0.0 Safari/537.36"
    ),
}


def _warm_session() -> _requests.Session:
    """Ana sayfayı ziyaret edip çerezleri alır, sonra API'ye aynı oturumla gider."""
    session = _requests.Session()
    session.headers.update(_HEADERS)
    try:
        session.get("https://marketfiyati.org.tr/", timeout=10)
        print(f"Oturum çerezleri: {dict(session.cookies)}")
    except Exception as e:
        print(f"Ana sayfa ısınması başarısız (devam): {e}")
    return session


def main() -> int:
    parser = argparse.ArgumentParser(description="marketfiyati.org.tr kaynağını dener")
    parser.add_argument("query", help="Aranacak ürün, örn: makarna")
    parser.add_argument("--city", default="ankara", help="Şehir anahtarı (varsayılan: ankara)")
    parser.add_argument("--raw", action="store_true", help="Ham JSON yanıtın yapısını göster")
    args = parser.parse_args()

    latitude, longitude = registry.coords_for(args.city)

    print(f"→ '{args.query}' / {args.city} ({latitude}, {longitude})\n")
    print("1) Ana sayfaya bağlanılıyor (çerez alımı)...")
    session = _warm_session()

    print("2) API'ye istek atılıyor...")
    body = {
        "keywords": args.query,
        "latitude": latitude,
        "longitude": longitude,
        "distance": 15,
        "size": 24,
        "pages": 0,
    }
    try:
        r = session.post(
            "https://api.marketfiyati.org.tr/api/v2/search",
            json=body,
            timeout=15,
        )
        print(f"HTTP durumu: {r.status_code}")
        r.raise_for_status()
        payload = r.json()
    except Exception as exc:
        print(f"\nHATA: {exc}")
        print("\nOlası sebepler:")
        print("  - Cloudflare / bot koruması aktif (çerez + JS challenge gerekiyor)")
        print("  - API adresi değişmiş")
        print("  - Ağ/güvenlik duvarı engelliyor")
        return 1

    source = MarketFiyatiSource()

    products = payload.get("content") or payload.get("products") or []
    print(f"Yanıt üst düzey anahtarlar : {sorted(payload.keys())}")
    print(f"Dönen ürün sayısı          : {len(products)}")

    if products and isinstance(products[0], dict):
        print(f"Ürün alanları              : {sorted(products[0].keys())}")
        depots = (
            products[0].get("productDepotInfoList")
            or products[0].get("depots")
            or []
        )
        if depots and isinstance(depots[0], dict):
            print(f"Şube (depot) alanları      : {sorted(depots[0].keys())}")

    if args.raw and products:
        print("\n--- İlk ürünün ham hâli ---")
        print(json.dumps(products[0], ensure_ascii=False, indent=2)[:2000])

    items = source._parse(payload)
    print(f"\nAyrıştırılan kayıt sayısı  : {len(items)}")
    for item in items[:15]:
        print(f"  {item.source:<14} {item.price:>9} TL   {item.name[:60]}")

    # Son güncelleme bilgisi payload içinden okunur (ayrı istek atmaz). Servisin
    # kullandığı ayrıştırıcının aynısı — X-Data-Updated başlığına ne gideceğini
    # burada birebir görürsün.
    updated = source._latest_index_time(payload)
    print(f"\nKaynaktaki son güncelleme  : {updated or 'bilinmiyor'}")

    if not items:
        print("\nUYARI: hiç kayıt ayrıştırılamadı. Yukarıdaki alan adlarını")
        print("app/sources/marketfiyati.py içindeki _first(...) adaylarıyla karşılaştır.")
        return 2
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
