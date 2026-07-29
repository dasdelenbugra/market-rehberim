"""Türkçe metin normalizasyonu — karşılaştırma anahtarları için.

Tek bir yerde durmasının sebebi: `str.lower()` Türkçe'de güvenilir değil.
Python'da "İ".lower() birleşik bir karakter üretir ("i" + U+0307), bu da
"İSTANBUL".lower() == "istanbul" karşılaştırmasını sessizce False yapar.
Şehir anahtarı ve market adı eşleştirmesi bu tuzağa aynı şekilde düşüyordu.
"""
from __future__ import annotations

_TR_FOLD = str.maketrans(
    "İIıŞşĞğÜüÖöÇç",
    "IIiSsGgUuOoCc",
)


def fold(raw: str) -> str:
    """Karşılaştırma anahtarı üretir: 'Migros Ticaret A.Ş.' → 'migrosticaretas'.

    Türkçe harfler ASCII'ye indirgenir, küçük harfe çevrilir, harf/rakam
    dışındaki her şey atılır.
    """
    if not raw:
        return ""
    folded = raw.translate(_TR_FOLD).lower()
    return "".join(ch for ch in folded if ch.isalnum())
